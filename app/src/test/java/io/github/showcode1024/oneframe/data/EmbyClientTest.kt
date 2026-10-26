package io.github.showcode1024.oneframe.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class EmbyClientTest {
    private val server = MockWebServer().apply { start() }
    private val api = EmbyClient("unit-test-device")
    private val session get() = EmbySession(server.url("/reverse/emby").toString(), "user-id", "private-token", "Test")
    @After fun close() { server.shutdown() }

    @Test fun reverseProxyPathIsPreserved() {
        assertEquals("https://example.com/emby", EmbyClient.normalizeUrl(" https://example.com/emby/ "))
    }
    @Test fun serverUrlRejectsCredentialsAndQueries() {
        listOf("https://user:secret@example.com", "https://example.com?api_key=secret", "file:///etc/passwd", "https://example.com#token").forEach {
            assertThrows(EmbyException::class.java) { EmbyClient.normalizeUrl(it) }
        }
    }
    @Test fun loginEscapesPasswordAndUsesUserAuthentication() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"User":{"Id":"user-id","Name":"Test"},"AccessToken":"private-token"}"""))
        val password = "quote\" slash\\ 中文"
        assertEquals("private-token", api.login(session.baseUrl, "Test", password).token)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/reverse/emby/Users/AuthenticateByName", request.path)
        assertEquals(password, JSONObject(request.body.readUtf8()).getString("Pw"))
        assertNull(request.getHeader("X-Emby-Token"))
    }
    @Test fun authenticatedBrowseEncodesSearchAndIsolatesTokenInHeader() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
        api.items(session, "parent/with slash", "电影 & test")
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("private-token", request.getHeader("X-Emby-Token"))
        assertEquals("电影 & test", request.requestUrl!!.queryParameter("SearchTerm"))
        assertEquals("parent/with slash", request.requestUrl!!.queryParameter("ParentId"))
        assertFalse(request.path!!.contains("private-token"))
    }
    @Test fun loginDoesNotFollowRedirectsWithCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/untrusted")))
        val error = runCatching { api.login(session.baseUrl, "user", "password") }.exceptionOrNull()
        assertTrue(error is EmbyException && error.status == 302)
        assertEquals(1, server.requestCount)
    }
    @Test fun playbackKeepsItemAndMediaSourceIdentifiersSeparate() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"play-id","MediaSources":[{"Id":"source-id","SupportsDirectPlay":true}]}"""))
        val source = api.playback(session, LibraryItem("item-id", "Movie", "Movie", positionTicks = 125_000_000))
        assertEquals("source-id", source.mediaSourceId)
        assertEquals("item-id", source.itemId)
        assertEquals(12.5, source.startSeconds, .001)
        assertTrue(source.url.contains("/Videos/item-id/stream?"))
        assertTrue(source.url.contains("MediaSourceId=source-id"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(body.getBoolean("EnableTranscoding"))
        assertEquals(125_000_000, body.getLong("StartTimeTicks"))
    }
    @Test fun unavailableDirectPlayDoesNotSilentlyRequestTranscoding() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"source","SupportsDirectPlay":false,"TranscodingUrl":"/video.m3u8"}]}"""))
        val error = runCatching { api.playback(session, LibraryItem("id", "Movie", "Movie")) }.exceptionOrNull()
        assertTrue(error is EmbyException)
        assertEquals(1, server.requestCount)
    }
    @Test fun progressReportsRequiredSessionAndPosition() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.report(session, StreamSource("unused", "item", "source", "play", "Movie", 0.0), "Pause", 12.5, true)
        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/Sessions/Playing/Progress"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(125_000_000L, body.getLong("PositionTicks"))
        assertEquals("play", body.getString("PlaySessionId"))
        assertEquals("Pause", body.getString("EventName"))
        assertTrue(body.getBoolean("IsPaused"))
    }
}
