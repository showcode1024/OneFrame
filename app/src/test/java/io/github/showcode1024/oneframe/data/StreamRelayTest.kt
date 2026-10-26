package io.github.showcode1024.oneframe.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class StreamRelayTest {
    private val client = OkHttpClient()
    @Test fun credentialsStayUpstreamAndByteRangeIsPreserved() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setBody("part").setHeader("Content-Range", "bytes 10-13/100"))
            StreamRelay(server.url("/video?api_key=secret").toString(), "secret").use { relay ->
                assertFalse(relay.url.contains("secret"))
                client.newCall(Request.Builder().url(relay.url).header("Range", "bytes=10-13").build()).execute().use {
                    assertEquals(206, it.code); assertEquals("part", it.body!!.string())
                    assertEquals("bytes 10-13/100", it.header("Content-Range"))
                }
                val request = server.takeRequest()
                assertEquals("secret", request.getHeader("X-Emby-Token"))
                assertEquals("bytes=10-13", request.getHeader("Range"))
                assertEquals("/video", request.path)
            }
        }
    }
    @Test fun crossOriginRedirectDoesNotReceiveEmbyToken() {
        MockWebServer().use { origin -> MockWebServer().use { cdn ->
            cdn.enqueue(MockResponse().setBody("video"))
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cdn.url("/cdn")))
            StreamRelay(origin.url("/video?api_key=secret").toString(), "secret").use { relay ->
                client.newCall(Request.Builder().url(relay.url).build()).execute().use { assertEquals("video", it.body!!.string()) }
                assertEquals("secret", origin.takeRequest().getHeader("X-Emby-Token"))
                assertNull(cdn.takeRequest().getHeader("X-Emby-Token"))
            }
        } }
    }
    @Test fun unknownLocalPathCannotProxyRemoteContent() {
        MockWebServer().use { origin ->
            StreamRelay(origin.url("/video").toString()).use { relay ->
                val wrong = relay.url.substringBeforeLast('/') + "/wrong"
                client.newCall(Request.Builder().url(wrong).build()).execute().use { assertEquals(403, it.code) }
                assertEquals(0, origin.requestCount)
            }
        }
    }
}
