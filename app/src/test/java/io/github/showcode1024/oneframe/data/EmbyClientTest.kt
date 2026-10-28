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
    @Test fun parsesFavoriteFromServerUserDataAndDefaultsToFalse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"one","Name":"One","UserData":{"IsFavorite":true}},{"Id":"two","Name":"Two"}]}"""))
        val items = api.items(session, "parent")
        assertTrue(items[0].favorite)
        assertFalse(items[1].favorite)
    }
    @Test fun favoriteCollectionIsRequestedFromEmbyWithUserAuthentication() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"movie","Name":"Movie","Type":"Movie"}]}"""))
        val items = api.favorites(session)
        assertEquals(1, items.size)
        assertTrue(items.single().favorite)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(request.path!!.startsWith("/reverse/emby/Users/user-id/Items?"))
        assertEquals("true", request.requestUrl!!.queryParameter("IsFavorite"))
        assertEquals("true", request.requestUrl!!.queryParameter("Recursive"))
        assertEquals("Movie,Series,Episode,Video", request.requestUrl!!.queryParameter("IncludeItemTypes"))
        assertEquals("private-token", request.getHeader("X-Emby-Token"))
        assertFalse(request.path!!.contains("private-token"))
    }
    @Test fun favoriteUsesCorrectMethodsAndReturnsServerState() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"IsFavorite":true}"""))
        assertTrue(api.setFavorite(session, "item/with slash", true))
        val add = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", add.method)
        assertEquals("/reverse/emby/Users/user-id/FavoriteItems/item%2Fwith%20slash", add.path)
        assertEquals("private-token", add.getHeader("X-Emby-Token"))
        assertFalse(add.path!!.contains("private-token"))
        server.enqueue(MockResponse().setBody("""{"IsFavorite":false}"""))
        assertFalse(api.setFavorite(session, "item/with slash", false))
        val remove = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("DELETE", remove.method)
        assertEquals(add.path, remove.path)
        assertEquals("private-token", remove.getHeader("X-Emby-Token"))
        server.enqueue(MockResponse().setBody("""{"IsFavorite":false}"""))
        assertFalse(api.setFavorite(session, "item", true))
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(runCatching { api.setFavorite(session, "item", true) }.exceptionOrNull() is EmbyException)
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
        assertEquals("DirectStream", body.getString("PlayMethod"))
    }

    @Test fun listsVersionsAndPlaysTheExplicitlySelectedSource() = runBlocking {
        val versions = """{"MediaSources":[{"Id":"first","Name":"1080p","SupportsDirectPlay":true},{"Id":"second","Name":"4K Dolby","Container":"mkv","Size":2147483648,"SupportsDirectStream":true,"MediaStreams":[{"Type":"Video","Width":3840,"Height":2160,"Codec":"hevc","VideoRangeType":"DOVI"},{"Type":"Audio","Codec":"truehd"}]}]}"""
        server.enqueue(MockResponse().setBody(versions))
        server.enqueue(MockResponse().setBody(versions.replace("{\"MediaSources\"", "{\"PlaySessionId\":\"fresh-session\",\"MediaSources\"")))
        val item = LibraryItem("movie", "Movie", "Movie")
        val available = api.playbackOptions(session, item)
        assertEquals(listOf("first", "second"), available.map { it.id })
        assertEquals(2160, available[1].height)
        assertEquals("DOVI", available[1].videoRange)
        assertTrue(available[1].playable)
        val source = api.playback(session, item, "second")
        assertEquals("second", source.mediaSourceId)
        assertEquals("fresh-session", source.playSessionId)
        assertTrue(source.url.contains("MediaSourceId=second"))
        val listing = JSONObject(server.takeRequest().body.readUtf8())
        val selected = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(listing.getBoolean("IsPlayback"))
        assertTrue(selected.getBoolean("IsPlayback"))
        assertEquals("second", selected.getString("MediaSourceId"))
        assertFalse(selected.getBoolean("EnableTranscoding"))
    }

    @Test fun missingOrUnsupportedSelectedVersionNeverFallsBack() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"other","SupportsDirectPlay":true}]}"""))
        val missing = runCatching { api.playback(session, LibraryItem("item", "Movie", "Movie"), "chosen") }.exceptionOrNull()
        assertTrue(missing is EmbyException)
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"chosen","SupportsDirectPlay":true,"RequiresOpening":true}]}"""))
        val unavailable = runCatching { api.playback(session, LibraryItem("item", "Movie", "Movie"), "chosen") }.exceptionOrNull()
        assertTrue(unavailable is EmbyException)
    }

    @Test fun listingRetainsUnavailableVersionsWithoutInvalidIds() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"Id":"a","SupportsDirectPlay":false},{"Id":"b","SupportsDirectStream":true},{"Name":"invalid"},{"Id":"b","SupportsDirectPlay":true}]}"""))
        val versions = api.playbackOptions(session, LibraryItem("item", "Movie", "Movie"))
        assertEquals(listOf("a", "b"), versions.map { it.id })
        assertFalse(versions[0].playable)
        assertTrue(versions[1].playable)
    }
}
