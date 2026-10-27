package io.github.showcode1024.oneframe.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest

class ReleaseClientTest {
    private fun release(version: String, prerelease: Boolean = true, digest: String = "sha256:" + "a".repeat(64)): JSONObject {
        val tag = "v$version"
        val asset = JSONObject().put("name", "OneFrame-$version.apk").put("state", "uploaded").put("size", 123)
            .put("digest", digest).put("browser_download_url", "$RELEASES_PAGE/download/$tag/OneFrame-$version.apk")
        return JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", prerelease)
            .put("body", "更新说明").put("assets", JSONArray().put(asset))
    }

    @Test fun comparesSemverIncludingPrereleaseOrdering() {
        fun v(value: String) = ReleaseVersion.parse(value)!!
        assertTrue(v("0.1.10-alpha") > v("0.1.9"))
        assertTrue(v("1.0.0") > v("1.0.0-rc.9"))
        assertTrue(v("1.0.0-alpha.10") > v("1.0.0-alpha.2"))
        assertTrue(v("1.0.0-alpha.1") < v("1.0.0-alpha.beta"))
        assertNull(ReleaseVersion.parse("1.0.0-alpha.01"))
        assertNull(ReleaseVersion.parse("release-foo"))
    }

    @Test fun previewInstallsSeePreviewUpdatesButStableInstallsDoNot() {
        val data = JSONArray().put(release("0.1.6-alpha")).put(release("0.1.5", prerelease = false))
            .put(release("0.1.8-alpha").put("draft", true)).put(release("0.1.4-alpha"))
        assertEquals("0.1.6-alpha", ReleaseParser.select(data, "0.1.5-alpha")!!.version)
        assertEquals("0.1.5", ReleaseParser.select(data, "0.1.4")!!.version)
        assertNull(ReleaseParser.select(data, "0.1.5"))
    }

    @Test fun rejectsMissingChecksumOrUnexpectedDownloadHost() {
        assertThrows(java.io.IOException::class.java) { ReleaseParser.select(JSONArray().put(release("0.1.6-alpha", digest = "")), "0.1.5-alpha") }
        val bad = release("0.1.6-alpha")
        bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.org/OneFrame.apk")
        assertThrows(java.io.IOException::class.java) { ReleaseParser.select(JSONArray().put(bad), "0.1.5-alpha") }
    }

    @Test fun rejectsWrongPackageVersionOrSigningIdentity() {
        val cert = setOf(ApkIdentityPolicy.PROJECT_CERT_SHA256)
        fun verify(packageName: String = "dev.prism.player", version: String = "0.1.6-alpha", code: Long = 7,
            archiveSigners: Set<String> = cert, installedSigners: Set<String> = cert) =
            ApkIdentityPolicy.verify("dev.prism.player", 6, installedSigners, "0.1.6-alpha", packageName, version, code, archiveSigners)
        verify()
        assertThrows(java.io.IOException::class.java) { verify(packageName = "other.app") }
        assertThrows(java.io.IOException::class.java) { verify(version = "0.1.5-alpha") }
        assertThrows(java.io.IOException::class.java) { verify(code = 6) }
        assertThrows(java.io.IOException::class.java) { verify(archiveSigners = setOf("wrong")) }
        assertThrows(java.io.IOException::class.java) { verify(installedSigners = setOf("debug-key")) }
    }

    @Test fun checksGithubListAndReportsRateLimit() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(JSONArray().put(release("0.1.6-alpha")).toString()))
            val client = ReleaseClient(apiUrl = server.url("/releases").toString())
            assertEquals("0.1.6-alpha", client.check("0.1.5-alpha")!!.version)
            server.enqueue(MockResponse().setResponseCode(403))
            assertTrue(runCatching { client.check("0.1.5-alpha") }.exceptionOrNull()?.message!!.contains("受限"))
        }
    }

    @Test fun downloadVerifiesSizeAndHashAndCleansIncompleteFile() = runBlocking {
        val payload = "APK fixture".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        MockWebServer().use { server ->
            server.start()
            val client = ReleaseClient(downloadClient = OkHttpClient.Builder().followRedirects(false).build(), trustedDownload = { it.host == server.hostName })
            val update = UpdateRelease("v0.1.6-alpha", "0.1.6-alpha", "", server.url("/file").toString(), payload.size.toLong(), sha)
            val file = File.createTempFile("oneframe-update-test", ".apk")
            try {
                server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
                assertEquals(file, client.download(update, file) { _, _ -> })
                assertArrayEquals(payload, file.readBytes())
                server.enqueue(MockResponse().setBody("wrong data!"))
                assertTrue(runCatching { client.download(update, file) { _, _ -> } }.isFailure)
                assertFalse(file.exists())
                server.enqueue(MockResponse().setBody("short"))
                assertTrue(runCatching { client.download(update, file) { _, _ -> } }.isFailure)
                assertFalse(file.exists())
            } finally { file.delete() }
        }
    }

    @Test fun productionDownloaderRejectsNonHttpsUrls() = runBlocking {
        val file = File.createTempFile("oneframe-update-test", ".apk")
        val update = UpdateRelease("v0.1.6-alpha", "0.1.6-alpha", "", "http://127.0.0.1:9/file", 1, "a".repeat(64))
        assertTrue(runCatching { ReleaseClient().download(update, file) { _, _ -> } }.isFailure)
        assertFalse(file.exists())
    }

    @Test fun downloaderChecksRedirectDestinationBeforeFollowingIt() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://untrusted.example/file"))
            val client = ReleaseClient(downloadClient = OkHttpClient.Builder().followRedirects(false).build(), trustedDownload = { it.host == server.hostName })
            val file = File.createTempFile("oneframe-update-test", ".apk")
            val update = UpdateRelease("v0.1.6-alpha", "0.1.6-alpha", "", server.url("/file").toString(), 1, "a".repeat(64))
            val error = runCatching { client.download(update, file) { _, _ -> } }.exceptionOrNull()
            assertTrue(error is java.io.IOException && error.message!!.contains("不受信任"))
            assertEquals(1, server.requestCount)
            assertFalse(file.exists())
        }
    }

    @Test fun liveGithubReleaseDownloadsAndVerifiesWhenExplicitlyEnabled() = runBlocking {
        assumeTrue(System.getenv("ONEFRAME_LIVE_UPDATE_TEST") == "1")
        val client = ReleaseClient()
        val update = client.check("0.1.3-alpha")!!
        val file = File.createTempFile("oneframe-live-release", ".apk")
        try {
            client.download(update, file) { _, _ -> }
            assertEquals(update.size, file.length())
        } finally { file.delete() }
    }
}
