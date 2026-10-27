package io.github.showcode1024.oneframe.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val MAX_APK_SIZE = 300L * 1024 * 1024
const val RELEASES_PAGE = "https://github.com/showcode1024/OneFrame/releases"

data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val prerelease: List<String>) : Comparable<ReleaseVersion> {
    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("^(?:v)?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(value) ?: return null
            val (major, minor, patch, pre) = match.destructured
            val numbers = listOf(major, minor, patch).map { it.toLongOrNull() ?: return null }
            val identifiers = if (pre.isEmpty()) emptyList() else pre.split('.')
            if (identifiers.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
            return ReleaseVersion(numbers[0], numbers[1], numbers[2], identifiers)
        }
    }
    override fun compareTo(other: ReleaseVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) return when {
            prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
            prerelease.isEmpty() -> 1
            else -> -1
        }
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[i]; val b = other.prerelease[i]
            val an = a.all(Char::isDigit); val bn = b.all(Char::isDigit)
            val comparison = when {
                an && bn -> if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
                an -> -1
                bn -> 1
                else -> a.compareTo(b)
            }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }
}

data class UpdateRelease(val tag: String, val version: String, val notes: String, val apkUrl: String, val size: Long, val sha256: String)

object ApkIdentityPolicy {
    const val PROJECT_CERT_SHA256 = "ae3e199d610ac48d0e19cfa6b75d435645c8d45f7e481c1297a2bc6bd225ae9f"
    fun verify(packageName: String, installedCode: Long, installedSigners: Set<String>, releaseVersion: String,
        archivePackage: String, archiveVersion: String?, archiveCode: Long, archiveSigners: Set<String>) {
        if (archivePackage != packageName || archiveVersion != releaseVersion) throw IOException("安装包的应用标识或版本不匹配")
        if (archiveCode <= installedCode) throw IOException("安装包版本不高于当前版本")
        if (archiveSigners != setOf(PROJECT_CERT_SHA256)) throw IOException("安装包签名与 OneFrame 发布签名不匹配")
        if (installedSigners != setOf(PROJECT_CERT_SHA256)) throw IOException("当前安装包签名与正式发布包不同，无法直接覆盖升级")
    }
}

object ReleaseParser {
    fun select(releases: JSONArray, current: String): UpdateRelease? {
        val installed = ReleaseVersion.parse(current) ?: throw IOException("当前版本号无法识别")
        val entries = (0 until releases.length()).mapNotNull { index ->
            val release = releases.optJSONObject(index) ?: return@mapNotNull null
            val tag = release.optString("tag_name")
            val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
            if (!tag.startsWith("v") || release.optBoolean("draft") || version <= installed ||
                (installed.prerelease.isEmpty() && (release.optBoolean("prerelease") || version.prerelease.isNotEmpty()))) return@mapNotNull null
            version to release
        }
        val selected = entries.maxByOrNull { it.first }?.second ?: return null
        val tag = selected.getString("tag_name")
        val version = tag.removePrefix("v")
        val expectedName = "OneFrame-$version.apk"
        val assets = selected.optJSONArray("assets") ?: JSONArray()
        val asset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull {
            it.optString("name") == expectedName && it.optString("state") == "uploaded"
        } ?: throw IOException("新版安装包尚未发布，请稍后重试")
        val digest = asset.optString("digest").removePrefix("sha256:")
        val size = asset.optLong("size")
        if (!Regex("[0-9a-fA-F]{64}").matches(digest) || !asset.optString("digest").startsWith("sha256:") || size !in 1..MAX_APK_SIZE)
            throw IOException("新版安装包缺少有效的校验信息")
        val expectedUrl = "https://github.com/showcode1024/OneFrame/releases/download/".toHttpUrl().newBuilder()
            .addPathSegment(tag).addPathSegment(expectedName).build().toString()
        if (asset.optString("browser_download_url") != expectedUrl) throw IOException("新版安装包地址不符合预期")
        return UpdateRelease(tag, version, selected.optString("body").take(4000), expectedUrl, size, digest.lowercase())
    }
}

class ReleaseClient(
    private val apiClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).followRedirects(false).build(),
    private val apiUrl: String = "https://api.github.com/repos/showcode1024/OneFrame/releases?per_page=100",
    private val downloadClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val trustedDownload: (HttpUrl) -> Boolean = { url ->
        url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    },
) {
    suspend fun check(current: String): UpdateRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(apiUrl).header("Accept", "application/vnd.github+json")
            .header("User-Agent", "OneFrame-Android").build()
        apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(if (response.code == 403 || response.code == 429) "GitHub 检查次数受限，请稍后重试" else "检查更新失败（${response.code}）")
            val body = response.body ?: throw IOException("更新信息为空")
            val source = body.source()
            source.request(2L * 1024 * 1024 + 1)
            if (source.buffer.size > 2L * 1024 * 1024) throw IOException("更新信息过大")
            ReleaseParser.select(JSONArray(source.readUtf8()), current)
        }
    }

    suspend fun download(release: UpdateRelease, temporary: File, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        temporary.parentFile?.mkdirs()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            var currentUrl = release.apkUrl.toHttpUrl()
            var redirects = 0
            while (true) {
                if (!trustedDownload(currentUrl)) throw IOException("安装包下载地址不受信任")
                val request = Request.Builder().url(currentUrl).header("User-Agent", "OneFrame-Android").build()
                val response = downloadClient.newCall(request).execute()
                if (response.isRedirect) {
                    val next = response.header("Location")?.let { currentUrl.resolve(it) }
                    response.close()
                    if (next == null || ++redirects > 5) throw IOException("安装包下载重定向异常")
                    currentUrl = next
                    continue
                }
                response.use {
                if (!response.isSuccessful) throw IOException("下载失败（${response.code}）")
                val body = response.body ?: throw IOException("安装包为空")
                temporary.outputStream().buffered().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            if (count > release.size || count > MAX_APK_SIZE) throw IOException("安装包大小异常")
                            digest.update(buffer, 0, n); out.write(buffer, 0, n); progress(count, release.size)
                        }
                    }
                }
                }
                break
            }
            if (count != release.size || digest.digest().joinToString("") { "%02x".format(it) } != release.sha256)
                throw IOException("安装包校验失败，请重试")
            temporary
        } catch (error: Exception) { temporary.delete(); throw error }
    }
}
