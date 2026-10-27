package io.github.showcode1024.oneframe.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue

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

    @Test fun liveGithubReleaseCheckWhenExplicitlyEnabled() = runBlocking {
        assumeTrue(System.getenv("ONEFRAME_LIVE_UPDATE_TEST") == "1")
        val update = ReleaseClient().check("0.1.3-alpha")!!
        assertTrue(update.apkUrl.startsWith("https://github.com/showcode1024/OneFrame/releases/download/"))
        assertTrue(update.size > 0)
    }
}
