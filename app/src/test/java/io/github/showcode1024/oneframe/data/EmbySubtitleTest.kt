package io.github.showcode1024.oneframe.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class EmbySubtitleTest {
    @Test fun selectedMediaSourceOffersOnlyItsExternalTextSubtitles() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = EmbyClient("fixture")
            val session = EmbySession(server.url("/proxy/emby").toString(), "user", "private-token", "测试")
            server.enqueue(MockResponse().setBody("""{"PlaySessionId":"play","MediaSources":[{"Id":"other","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","Index":1,"IsExternal":true,"IsTextSubtitleStream":true}]},{"Id":"chosen","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","Index":7,"IsExternal":true,"IsTextSubtitleStream":true,"DisplayTitle":"简体中文","Language":"chi"},{"Type":"Subtitle","Index":8,"IsExternal":false,"IsTextSubtitleStream":true},{"Type":"Subtitle","Index":9,"IsExternal":true,"IsTextSubtitleStream":false}]}]}"""))
            val source = client.playback(session, LibraryItem("movie", "电影", "Movie"), "chosen")
            assertEquals(listOf(7), source.externalSubtitles.map { it.index })
            assertEquals("简体中文", source.externalSubtitles.single().title)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("1\n00:00:00,000 --> 00:00:01,000\n你好\n"))
            assertTrue(client.subtitle(session, "movie", "chosen", 7).toString(Charsets.UTF_8).contains("你好"))
            val request = server.takeRequest()
            assertEquals("/proxy/emby/Videos/movie/chosen/Subtitles/7/Stream.srt", request.path)
            assertEquals("private-token", request.getHeader("X-Emby-Token"))
            assertFalse(request.path!!.contains("private-token"))
        }
    }

    @Test fun subtitleRedirectNeverReceivesTheEmbyToken() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = EmbyClient("fixture")
            val session = EmbySession(server.url("/").toString(), "user", "private-token", "测试")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
            assertTrue(runCatching { client.subtitle(session, "item", "source", 0) }.exceptionOrNull() is EmbyException)
            assertEquals(1, server.requestCount)
        }
    }
}
