package io.github.showcode1024.oneframe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.github.showcode1024.oneframe.BuildConfig

class EmbyException(message: String, val status: Int = 0) : IOException(message)

class EmbyClient(
    private val deviceId: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).build(),
) {
    companion object {
        fun normalizeUrl(value: String): String {
            val url = value.trim().toHttpUrlOrNull() ?: throw EmbyException("请输入完整的 http:// 或 https:// 服务器地址")
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
                throw EmbyException("服务器地址不能包含账号、查询参数或片段")
            return url.toString().trimEnd('/')
        }
    }

    private fun url(base: String, vararg segments: String): HttpUrl.Builder =
        normalizeUrl(base).toHttpUrlOrNull()!!.newBuilder().apply { segments.forEach { addPathSegment(it) } }

    private suspend fun request(url: HttpUrl, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Authorization", "Emby Client=\"OneFrame\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\"")
            .apply {
                token?.let { header("X-Emby-Token", it) }
                body?.let { post(it.toString().toRequestBody("application/json; charset=utf-8".toMediaType())) }
            }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw EmbyException(when (response.code) {
                401 -> "登录已失效或账号密码不正确，请重新连接"
                403 -> "该账号没有访问或播放权限"
                in 300..399 -> "服务器返回重定向，请填写重定向后的最终地址"
                else -> "服务器请求失败（${response.code}）"
            }, response.code)
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) JSONObject() else if (text.trimStart().startsWith("["))
                JSONObject().put("Items", JSONArray(text)) else JSONObject(text)
        }
    }

    suspend fun login(base: String, user: String, password: String): EmbySession {
        val normalized = normalizeUrl(base)
        if (user.isBlank()) throw EmbyException("请输入用户名")
        val json = request(url(normalized, "Users", "AuthenticateByName").build(), body =
            JSONObject().put("Username", user.trim()).put("Pw", password))
        return EmbySession(normalized, json.getJSONObject("User").getString("Id"), json.getString("AccessToken"),
            json.getJSONObject("User").optString("Name", user.trim()))
    }

    private fun parseItems(json: JSONObject): List<LibraryItem> {
        val values = json.optJSONArray("Items") ?: JSONArray()
        return List(values.length()) { i -> values.getJSONObject(i).let { item ->
            LibraryItem(item.getString("Id"), item.getString("Name"), item.optString("Type"),
                item.optString("ProductionYear", ""), item.optString("Overview"),
                item.optJSONObject("ImageTags")?.optString("Primary").orEmpty(),
                item.optBoolean("IsFolder", false), item.optJSONObject("UserData")?.optLong("PlaybackPositionTicks") ?: 0,
                item.optJSONArray("BackdropImageTags")?.optString(0).orEmpty(), item.optLong("RunTimeTicks"),
                item.optJSONArray("Genres")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
                item.optString("CommunityRating", ""))
        } }
    }

    suspend fun views(session: EmbySession): List<LibraryItem> = parseItems(
        request(url(session.baseUrl, "Users", session.userId, "Views").build(), session.token))

    suspend fun items(session: EmbySession, parent: String?, search: String = "", start: Int = 0): List<LibraryItem> {
        val builder = url(session.baseUrl, "Users", session.userId, "Items")
            .addQueryParameter("Fields", "Overview,PrimaryImageAspectRatio,Genres")
            .addQueryParameter("StartIndex", start.toString()).addQueryParameter("Limit", "100")
            .addQueryParameter("SortBy", "SortName").addQueryParameter("SortOrder", "Ascending")
        if (parent != null) builder.addQueryParameter("ParentId", parent)
        if (search.isNotBlank()) builder.addQueryParameter("SearchTerm", search).addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Movie,Series,Episode,Video")
        return parseItems(request(builder.build(), session.token))
    }

    fun imageUrl(session: EmbySession, item: LibraryItem): String = url(session.baseUrl, "Items", item.id, "Images", "Primary")
        .addQueryParameter("tag", item.imageTag).addQueryParameter("maxWidth", "480")
        .addQueryParameter("quality", "85").build().toString()

    fun backdropUrl(session: EmbySession, item: LibraryItem): String = url(session.baseUrl, "Items", item.id, "Images", "Backdrop", "0")
        .addQueryParameter("tag", item.backdropTag).addQueryParameter("maxWidth", "1280")
        .addQueryParameter("quality", "85").build().toString()

    suspend fun resumeItems(session: EmbySession): List<LibraryItem> = parseItems(request(
        url(session.baseUrl, "Users", session.userId, "Items", "Resume")
            .addQueryParameter("Limit", "20").addQueryParameter("Fields", "Overview,Genres")
            .addQueryParameter("IncludeItemTypes", "Movie,Series,Episode,Video")
            .build(), session.token))

    suspend fun latestItems(session: EmbySession): List<LibraryItem> = parseItems(request(
        url(session.baseUrl, "Users", session.userId, "Items", "Latest")
            .addQueryParameter("Limit", "20").addQueryParameter("Fields", "Overview,Genres")
            .addQueryParameter("IncludeItemTypes", "Movie,Series,Episode,Video")
            .build(), session.token))

    private fun versions(info: JSONObject): List<MediaVersion> {
        val sources = info.optJSONArray("MediaSources") ?: return emptyList()
        return (0 until sources.length()).mapNotNull { index ->
            val source = sources.optJSONObject(index) ?: return@mapNotNull null
            val id = source.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val streams = source.optJSONArray("MediaStreams") ?: JSONArray()
            val video = (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.firstOrNull { it.optString("Type").equals("Video", true) }
            val audio = (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.firstOrNull { it.optString("Type").equals("Audio", true) }
            MediaVersion(id, source.optString("Name").ifBlank { "版本 ${index + 1}" }, source.optString("Container"),
                source.optLong("Size"), video?.optInt("Width") ?: 0, video?.optInt("Height") ?: 0,
                video?.optString("Codec").orEmpty(), audio?.optString("Codec").orEmpty(),
                listOfNotNull(video?.optString("VideoRangeType"), video?.optString("VideoRange"), video?.optString("ExtendedVideoType"))
                    .firstOrNull { it.isNotBlank() && it != "Unknown" }.orEmpty(),
                (source.optBoolean("SupportsDirectPlay") || source.optBoolean("SupportsDirectStream")) && !source.optBoolean("RequiresOpening"),
                source.optLong("RunTimeTicks"))
        }.distinctBy { it.id }
    }

    suspend fun playbackOptions(session: EmbySession, item: LibraryItem): List<MediaVersion> =
        versions(playbackInfo(session, item, null, false))

    private suspend fun playbackInfo(session: EmbySession, item: LibraryItem, sourceId: String?, isPlayback: Boolean): JSONObject {
        val profile = JSONObject().put("Name", "OneFrame playback")
            .put("DirectPlayProfiles", JSONArray().put(JSONObject().put("Type", "Video")))
            .put("TranscodingProfiles", JSONArray())
            .put("SubtitleProfiles", JSONArray().put(JSONObject().put("Format", "srt").put("Method", "External"))
                .put(JSONObject().put("Format", "ass").put("Method", "Embed")))
        val body = JSONObject().put("UserId", session.userId).put("IsPlayback", isPlayback).put("DeviceProfile", profile)
                .put("StartTimeTicks", item.positionTicks).put("EnableDirectPlay", true)
                .put("EnableDirectStream", true).put("EnableTranscoding", false)
        if (sourceId != null) body.put("MediaSourceId", sourceId)
        return request(url(session.baseUrl, "Items", item.id, "PlaybackInfo").build(), session.token, body)
    }

    suspend fun playback(session: EmbySession, item: LibraryItem, selectedSourceId: String? = null): StreamSource {
        val info = playbackInfo(session, item, selectedSourceId, true)
        val choices = versions(info)
        val source = if (selectedSourceId == null) choices.firstOrNull { it.playable }
            else choices.firstOrNull { it.id == selectedSourceId && it.playable }
        source ?: throw EmbyException(if (selectedSourceId == null) "服务器没有提供可直放的媒体源；当前版本不自动转码"
            else "所选版本已不可用或无法直放，请刷新版本列表后重试")
        val sourceId = source.id
        val sessionId = info.optString("PlaySessionId").ifBlank { java.util.UUID.randomUUID().toString() }
        // The token is scoped to this URL; do not install it as a global native HTTP header.
        val stream = url(session.baseUrl, "Videos", item.id, "stream")
            .addQueryParameter("static", "true").addQueryParameter("MediaSourceId", sourceId)
            .addQueryParameter("PlaySessionId", sessionId).addQueryParameter("api_key", session.token).build()
        return StreamSource(stream.toString(), item.id, sourceId, sessionId, item.title,
            PlaybackPolicy.resumePosition(item.positionTicks.toDouble() / PlaybackPolicy.TICKS_PER_SECOND,
                source.runtimeTicks.toDouble() / PlaybackPolicy.TICKS_PER_SECOND), source.name)
    }

    suspend fun report(session: EmbySession, source: StreamSource, event: String, seconds: Double, paused: Boolean) {
        val path = when (event) { "Start" -> arrayOf("Sessions", "Playing")
            "Stop" -> arrayOf("Sessions", "Playing", "Stopped")
            else -> arrayOf("Sessions", "Playing", "Progress") }
        val body = JSONObject().put("ItemId", source.itemId).put("MediaSourceId", source.mediaSourceId)
            .put("PlaySessionId", source.playSessionId).put("PositionTicks", PlaybackPolicy.secondsToTicks(seconds))
            .put("IsPaused", paused).put("CanSeek", true).put("PlayMethod", "DirectStream")
        if (event != "Start" && event != "Stop") body.put("EventName", event)
        request(url(session.baseUrl, *path).build(), session.token, body)
    }

    suspend fun logout(session: EmbySession) {
        request(url(session.baseUrl, "Sessions", "Logout").build(), session.token, JSONObject())
    }
}
