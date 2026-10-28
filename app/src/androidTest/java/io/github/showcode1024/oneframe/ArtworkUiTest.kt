package io.github.showcode1024.oneframe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.data.EmbySession
import io.github.showcode1024.oneframe.data.LibraryItem
import io.github.showcode1024.oneframe.data.LocalStore
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Visual smoke test uses a local fake Emby server and generated artwork; no sample content ships in the app. */
@RunWith(AndroidJUnit4::class)
class ArtworkUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun artworkAppearsOnHomeGridAndDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("prism", 0)
        val oldSession = prefs.getString("session", null)
        val oldSessions = prefs.getString("sessions", null)
        val oldUpdate = prefs.getBoolean("auto_update", true)
        prefs.edit().remove("session").remove("sessions").putBoolean("auto_update", false).commit()
        val imageRequests = AtomicInteger()
        val entries = JSONArray().apply { repeat(8) { index -> put(JSONObject().apply {
            put("Id", "movie$index"); put("Name", "示例影片 ${index + 1}"); put("Type", "Movie")
            put("ProductionYear", 2024 - index); put("Overview", "一段关于旅行、时间和重逢的故事。")
            put("ImageTags", JSONObject().put("Primary", "poster$index")); put("BackdropImageTags", JSONArray().put("backdrop$index"))
            put("Genres", JSONArray().put("剧情").put("冒险")); put("CommunityRating", 8.2)
            put("RunTimeTicks", 72000000000L); put("UserData", JSONObject().put("PlaybackPositionTicks", if (index == 0) 12000000000L else 0))
        }) } }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.contains("/Images/")) {
                        imageRequests.incrementAndGet()
                        val id = path.substringAfter("/Items/").substringBefore('/')
                        return MockResponse().setHeader("Content-Type", "image/png")
                            .setBody(Buffer().write(artwork(id, path.contains("/Backdrop/"))))
                    }
                    if (path.endsWith("/Views")) return json(JSONObject().put("Items", JSONArray().put(
                        JSONObject().put("Id", "library").put("Name", "电影库").put("Type", "CollectionFolder").put("IsFolder", true))))
                    if (path.endsWith("/Resume")) return json(JSONObject().put("Items", JSONArray().put(entries.getJSONObject(0))))
                    if (path.endsWith("/Items/Latest")) return json(JSONObject().put("Items", entries))
                    if (path.endsWith("/PlaybackInfo")) return json(JSONObject().put("PlaySessionId", "test")
                        .put("MediaSources", JSONArray().put(JSONObject().put("Id", "original").put("Name", "原始版本").put("SupportsDirectPlay", true))))
                    if (path.endsWith("/Items") && request.requestUrl!!.queryParameter("IsFavorite") == "true")
                        return json(JSONObject().put("Items", JSONArray()))
                    if (path.endsWith("/Items")) return json(JSONObject().put("Items", entries))
                    return MockResponse().setResponseCode(204)
                }
            }
            server.start()
            LocalStore(context).saveSession(EmbySession(server.url("/").toString().trimEnd('/'), "test", "test-token", "测试用户"))
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    compose.waitUntil(30_000) { imageRequests.get() >= 2 }
                    Thread.sleep(1500) // Let the image decoder and crossfade finish before visual capture.
                    compose.waitForIdle()
                    screenshot("artwork-home")
                    compose.onNodeWithContentDescription("搜索").performClick()
                    compose.onNodeWithText("搜索电影、剧集").performTextInput("示例")
                    compose.onNodeWithContentDescription("搜索").performClick()
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("示例影片 2").fetchSemanticsNodes().isNotEmpty() }
                    scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].search("") }
                    scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].openFolder(LibraryItem("library", "电影库", "CollectionFolder", folder = true)) }
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("示例影片 2").fetchSemanticsNodes().isNotEmpty() }
                    compose.waitUntil(20_000) { imageRequests.get() >= 5 }
                    Thread.sleep(1500)
                    compose.waitForIdle()
                    screenshot("artwork-grid")
                    compose.onNodeWithText("示例影片 2").performClick()
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("原始版本").fetchSemanticsNodes().isNotEmpty() }
                    Thread.sleep(1000)
                    compose.waitForIdle()
                    screenshot("artwork-detail")
                    assertTrue(imageRequests.get() >= 5)
                }
            } finally {
                prefs.edit().apply {
                    if (oldSession == null) remove("session") else putString("session", oldSession)
                    if (oldSessions == null) remove("sessions") else putString("sessions", oldSessions)
                    putBoolean("auto_update", oldUpdate)
                }.commit()
            }
        }
    }

    private fun json(value: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())

    private fun artwork(id: String, backdrop: Boolean): ByteArray {
        val index = id.filter { it.isDigit() }.toIntOrNull() ?: 0
        val colors = arrayOf(0xFF30536E.toInt(), 0xFF7D4B41.toInt(), 0xFF47675E.toInt(), 0xFF665674.toInt(), 0xFF967153.toInt())
        val width = if (backdrop) 960 else 480
        val height = if (backdrop) 540 else 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors[index % colors.size], Color.rgb(10, 15, 24), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null; paint.color = Color.argb(125, 255, 216, 161)
        canvas.drawCircle(width * .69f, height * .31f, height * .13f, paint)
        val silhouette = Path().apply { moveTo(0f, height * .84f); lineTo(width * .36f, height * .48f); lineTo(width * .56f, height * .72f)
            lineTo(width * .75f, height * .54f); lineTo(width.toFloat(), height * .78f); lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close() }
        paint.color = Color.argb(175, 13, 21, 30); canvas.drawPath(silhouette, paint)
        paint.color = Color.WHITE; paint.textSize = if (backdrop) 48f else 36f; paint.isFakeBoldText = true
        canvas.drawText("STORY ${index + 1}", width * .09f, height * .89f, paint)
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out); bitmap.recycle(); out.toByteArray() }
    }

    private fun screenshot(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
