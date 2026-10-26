package io.github.showcode1024.oneframe.data

import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Keeps remote credentials out of the native player's verbose logs and cross-origin redirects. */
class StreamRelay(
    upstream: String,
    private val token: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
) : Closeable {
    private val origin = upstream.toHttpUrl()
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    private val path = "/${UUID.randomUUID()}/stream"
    val url = "http://127.0.0.1:${server.localPort}$path"
    private val workers = Executors.newFixedThreadPool(3)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    @Volatile private var closed = false
    private val acceptor = Thread({
        while (!closed) runCatching {
            val socket = server.accept()
            sockets.add(socket)
            workers.submit { socket.use { serve(it) }; sockets.remove(socket) }
        }.onFailure { if (!closed) close() }
    }, "oneframe-stream-relay").apply { isDaemon = true; start() }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
    private fun withoutToken(url: HttpUrl): HttpUrl = if (token != null && url.queryParameter("api_key") == token)
        url.newBuilder().removeAllQueryParameters("api_key").build() else url

    private fun open(method: String, range: String?, ifRange: String?): Response {
        var target = withoutToken(origin)
        repeat(6) {
            val request = Request.Builder().url(target).method(method, null).header("Accept-Encoding", "identity")
                .apply {
                    if (token != null && sameOrigin(origin, target)) header("X-Emby-Token", token)
                    range?.let { header("Range", it) }; ifRange?.let { header("If-Range", it) }
                }.build()
            val call = client.newCall(request)
            calls.add(call)
            val response = try { call.execute() } finally { calls.remove(call) }
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val next = response.header("Location")?.let { target.resolve(it) }
            response.close()
            if (next == null || target.isHttps && !next.isHttps) throw java.io.IOException("Unsafe stream redirect")
            target = withoutToken(next)
        }
        throw java.io.IOException("Too many stream redirects")
    }

    private fun serve(socket: Socket) {
        runCatching {
            socket.soTimeout = 15_000
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            fun line(): String? {
                val result = StringBuilder()
                while (result.length < 8192) {
                    val value = input.read()
                    if (value < 0) return if (result.isEmpty()) null else result.toString()
                    if (value == 10) return result.toString().trimEnd('\r')
                    result.append(value.toChar())
                }
                throw java.io.IOException("Request header too long")
            }
            val request = line()?.split(' ') ?: return
            if (request.size != 3 || request[1] != path) {
                socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return
            }
            if (request[0] !in setOf("GET", "HEAD")) {
                socket.getOutputStream().write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return
            }
            var range: String? = null; var ifRange: String? = null
            var ended = false
            for (index in 0 until 64) {
                val value = line() ?: break
                if (value.isEmpty()) { ended = true; break }
                when (value.substringBefore(':').lowercase()) {
                    "range" -> range = value.substringAfter(':').trim()
                    "if-range" -> ifRange = value.substringAfter(':').trim()
                }
            }
            if (!ended) throw java.io.IOException("Too many request headers")
            open(request[0], range, ifRange).use { response ->
                val output = socket.getOutputStream().buffered()
                val headers = StringBuilder("HTTP/1.1 ${response.code} Stream\r\nConnection: close\r\n")
                for (name in listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges")) {
                    response.header(name)?.let { headers.append("$name: $it\r\n") }
                }
                headers.append("\r\n")
                output.write(headers.toString().toByteArray(Charsets.ISO_8859_1)); output.flush()
                if (request[0] == "GET") response.body?.byteStream()?.copyTo(output, 32 * 1024)
                output.flush()
            }
        }.onFailure {
            runCatching { socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        calls.forEach { it.cancel() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
