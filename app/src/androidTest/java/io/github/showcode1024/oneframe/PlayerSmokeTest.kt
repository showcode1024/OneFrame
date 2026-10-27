package io.github.showcode1024.oneframe

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.data.LocalStore
import io.github.showcode1024.oneframe.data.EmbySession
import io.github.showcode1024.oneframe.update.ApkVerifier
import io.github.showcode1024.oneframe.update.UpdateRelease
import androidx.core.content.FileProvider
import io.github.showcode1024.oneframe.player.PlaybackState
import io.github.showcode1024.oneframe.player.PlayerActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.io.File
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class PlayerSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var originalSession: String? = null
    private var originalSessions: String? = null
    private var originalSoftware = false
    private var originalExperimentalHardware = false
    private var originalFastScaling = false
    private var originalAutoUpdate = true
    private var originalLastUpdateCheck = 0L
    @Before fun isolatePreferences() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("prism", 0)
        originalSession = prefs.getString("session", null)
        originalSessions = prefs.getString("sessions", null)
        originalSoftware = prefs.getBoolean("software", false)
        originalExperimentalHardware = prefs.getBoolean("p5_hardware_experiment", false)
        originalFastScaling = prefs.getBoolean("fast_scaling", false)
        originalAutoUpdate = prefs.getBoolean("auto_update", true)
        originalLastUpdateCheck = prefs.getLong("last_update_check", 0)
        prefs.edit().remove("session").remove("sessions").commit()
        prefs.edit().putBoolean("p5_hardware_experiment", false).putBoolean("fast_scaling", false).putBoolean("auto_update", false).commit()
    }
    @After fun restorePreferences() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("prism", 0)
        prefs.edit().apply {
            if (originalSession == null) remove("session") else putString("session", originalSession)
            if (originalSessions == null) remove("sessions") else putString("sessions", originalSessions)
            putBoolean("software", originalSoftware)
            putBoolean("p5_hardware_experiment", originalExperimentalHardware)
            putBoolean("fast_scaling", originalFastScaling)
            putBoolean("auto_update", originalAutoUpdate)
            putLong("last_update_check", originalLastUpdateCheck)
        }.commit()
    }

    @Test fun launchHomeAndOpenLocalLibrary() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("OneFrame").assertIsDisplayed()
            compose.onNodeWithText("从一部\n好电影开始").assertIsDisplayed()
            screenshot("home-phone")
            compose.onNodeWithText("本地", useUnmergedTree = true).performClick()
            compose.onNodeWithText("本地片库").assertIsDisplayed()
            compose.onNodeWithText("播放链接").performClick()
            compose.onNodeWithText("播放网络链接").assertIsDisplayed()
        }
    }

    @Test fun profileFiveUsesSoftwareDecodeAndLibplacebo() = verifyDolby("dolby-p5", 5, true)
    @Test fun profileEightOneUsesSoftwareDecodeAndLibplacebo() = verifyDolby("dolby-p81", 8, false)
    @Test fun profileEightFourUsesSoftwareDecodeAndLibplacebo() = verifyDolby("dolby-p84", 8, false)

    @Test fun playbackOptionsAreOptInAndPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalStore(context).setSoftwareDecode(false)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("p5Experiment").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("fastScaling").performScrollTo().assertIsOff().performClick().assertIsOn()
            assertTrue(LocalStore(context).experimentalP5Hardware)
            assertTrue(LocalStore(context).fastScaling)
        }
    }

    @Test fun updateSettingCanBeDisabledAndReenabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("autoUpdate").performScrollTo().assertIsOff()
            compose.onNodeWithText("当前版本 ${io.github.showcode1024.oneframe.BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("autoUpdate").performClick().assertIsOn()
            assertTrue(LocalStore(context).autoUpdate)
            compose.onNodeWithTag("autoUpdate").performClick().assertIsOff()
            assertFalse(LocalStore(context).autoUpdate)
        }
    }

    @Test fun updaterRejectsInstalledVersionAndProvidesPrivateApkUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val version = io.github.showcode1024.oneframe.BuildConfig.VERSION_NAME
        val release = UpdateRelease("v$version", version, "", "", 1, "a".repeat(64))
        val installedApk = File(context.applicationInfo.sourceDir)
        val error = runCatching { ApkVerifier.verify(context, installedApk, release) }.exceptionOrNull()
        assertTrue("An installed or older version must never be offered as an update", error is java.io.IOException && error.message!!.contains("不高于"))
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val file = File(dir, "provider-test.apk").apply { writeText("fixture") }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            assertEquals("content", uri.scheme)
            assertEquals("fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        } finally { file.delete() }
    }

    @Test fun p5FastScalingKeepsSoftwareColorPathAndReportsSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sample = File(context.cacheDir, "dolby-p5-fast.mp4").apply { writeBytes(sampleBytes("dolby-p5")) }
        LocalStore(context).apply { setSoftwareDecode(true); setFastScaling(true); setExperimentalP5Hardware(true) }
        val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", sample.toURI().toString())
            .putExtra("title", "P5 快速缩放测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            var snapshot = PlaybackState()
            compose.waitUntil(45_000) {
                scenario.onActivity { snapshot = it.playbackSnapshot }
                snapshot.error != null || snapshot.ready && snapshot.position > .05
            }
            assertNull(snapshot.error)
            assertEquals("no", snapshot.decodePolicy)
            assertFalse(snapshot.experimentalHardware)
            assertTrue(snapshot.fastScaling)
            assertEquals("CPU 软件解码", snapshot.decoder)
            assertTrue(snapshot.profile == 5 || snapshot.colorMatrix == "dolbyvision")
            assertEquals("dolbyvision", snapshot.colorMatrix)
            compose.onNodeWithTag("diagnostics").performClick()
            compose.onNodeWithText("轻量双线性").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun p5ExperimentalModeReportsActualDecoderWithoutClaimingColorAccuracy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sample = File(context.cacheDir, "dolby-p5-experiment.mp4").apply { writeBytes(sampleBytes("dolby-p5")) }
        LocalStore(context).apply { setSoftwareDecode(false); setExperimentalP5Hardware(true) }
        val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", sample.toURI().toString())
            .putExtra("title", "P5 硬解实验测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            var snapshot = PlaybackState()
            compose.waitUntil(45_000) {
                scenario.onActivity { snapshot = it.playbackSnapshot }
                snapshot.error != null || snapshot.ready && snapshot.position > .05
            }
            assertNull(snapshot.error)
            assertTrue(snapshot.experimentalHardware)
            assertEquals("mediacodec,mediacodec-copy,auto-copy", snapshot.decodePolicy)
            assertTrue(snapshot.fallbackReason.isBlank())
            if (snapshot.profile == 5) assertTrue(snapshot.p5ColorStatus.contains("未") || snapshot.p5ColorStatus.contains("偏色"))
        }
    }

    @Test fun networkPlaybackUsesAutomaticPolicyAndReportsActualDecodePath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = sampleBytes("dolby-p5")
        LocalStore(context).setSoftwareDecode(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")?.removePrefix("bytes=")
                    val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                    val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                    if (start !in bytes.indices || end < start) return MockResponse().setResponseCode(416)
                    return MockResponse().setResponseCode(if (range == null) 200 else 206)
                        .setHeader("Content-Type", "video/mp4").setHeader("Accept-Ranges", "bytes")
                        .apply { if (range != null) setHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                        .setBody(Buffer().write(bytes, start, end - start + 1))
                }
            }
            server.start()
            val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", server.url("/sample.mp4").toString())
                .putExtra("title", "网络播放 / 自动解码测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                var snapshot = PlaybackState()
                compose.waitUntil(45_000) {
                    scenario.onActivity { snapshot = it.playbackSnapshot }
                    snapshot.error != null || snapshot.ready && snapshot.position > .05
                }
                assertNull(snapshot.error)
                assertTrue(snapshot.ready)
                assertEquals("gpu-next", snapshot.renderer)
                assertEquals("mediacodec-copy,auto-copy", snapshot.decodePolicy)
                assertTrue(snapshot.networkSource)
                assertTrue(snapshot.profile == 5 || snapshot.colorMatrix == "dolbyvision")
                // This AVD may have only software system codecs; do not assert hardware acceleration.
                assertTrue(snapshot.decoder == "CPU 软件解码" || snapshot.decoder.startsWith("系统解码"))
            }
        }
    }

    @Test fun savedServersCanBeSwitchedAndRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("prism", 0)
        prefs.edit().remove("session").remove("sessions").commit()
        val store = LocalStore(context)
        val first = EmbySession("https://first.example", "u1", "secret-one", "First")
        val second = EmbySession("https://second.example", "u2", "secret-two", "Second")
        try {
            store.saveSession(first)
            // An existing installation has only the old encrypted active-session record.
            prefs.edit().remove("sessions").commit()
            assertEquals(listOf(first), store.sessions())
            store.saveSession(second)
            assertEquals(listOf(first, second), store.sessions())
            assertEquals(second, store.session())
            assertFalse(prefs.getString("sessions", "")!!.contains("secret-one"))
            store.saveSession(first)
            assertEquals(first, store.session())
            assertEquals(second, store.removeSession(first))
            assertEquals(second, LocalStore(context).session())
            assertEquals(listOf(second), store.sessions())
        } finally { prefs.edit().remove("session").remove("sessions").commit() }
    }

    @Test fun embyVersionSelectionPlaysSelectedFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("prism", 0)
        prefs.edit().remove("session").remove("sessions").commit()
        val bytes = sampleBytes("dolby-p5")
        val requests = Collections.synchronizedList(mutableListOf<String>())
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith("/PlaybackInfo")) {
                        requests.add(request.body.readUtf8())
                        return MockResponse().setBody("""{"PlaySessionId":"play-selected","MediaSources":[{"Id":"first","Name":"1080p 测试版","SupportsDirectPlay":true},{"Id":"second","Name":"4K 测试版","SupportsDirectStream":true,"MediaStreams":[{"Type":"Video","Width":3840,"Height":2160,"Codec":"hevc"}]}]}""")
                    }
                    if (path.endsWith("/stream")) {
                        requests.add("stream:" + request.requestUrl!!.queryParameter("MediaSourceId"))
                        val range = request.getHeader("Range")?.removePrefix("bytes=")
                        val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                        val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                        if (start !in bytes.indices || end < start) return MockResponse().setResponseCode(416)
                        return MockResponse().setResponseCode(if (range == null) 200 else 206)
                            .setHeader("Content-Type", "video/mp4").setHeader("Accept-Ranges", "bytes")
                            .apply { if (range != null) setHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                            .setBody(Buffer().write(bytes, start, end - start + 1))
                    }
                    if (path.endsWith("/Items/Latest")) return MockResponse().setBody("""[{"Id":"movie","Name":"示例电影","Type":"Movie","ProductionYear":2024}]""")
                    if (path.endsWith("/Views") || path.endsWith("/Items/Resume")) return MockResponse().setBody("""{"Items":[]}""")
                    return MockResponse().setResponseCode(204)
                }
            }
            server.start()
            LocalStore(context).saveSession(EmbySession(server.url("/").toString().trimEnd('/'), "test-user", "test-token", "测试账号"))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("示例电影").fetchSemanticsNodes().isNotEmpty() }
                    compose.onAllNodesWithText("示例电影")[0].performClick()
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("4K 测试版").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("4K 测试版").performScrollTo().performClick()
                    compose.waitForIdle()
                    screenshot("emby-detail-phone")
                    compose.onNodeWithText("播放", useUnmergedTree = true).performScrollTo().performClick()
                    compose.waitUntil(45_000) { requests.any { it == "stream:second" } }
                    assertTrue(requests.any { it.startsWith("{") && JSONObject(it).optBoolean("IsPlayback") && JSONObject(it).optString("MediaSourceId") == "second" })
                    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                }
            } finally { prefs.edit().remove("session").remove("sessions").commit() }
        }
    }

    private fun verifyDolby(name: String, expectedProfile: Int, checkControls: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = sampleBytes(name)
        val sample = File(context.cacheDir, "$name.mp4").apply { writeBytes(bytes) }
        LocalStore(context).setSoftwareDecode(true)
        val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", sample.toURI().toString())
            .putExtra("title", "$name / 官方测试片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            var snapshot = PlaybackState()
            compose.waitUntil(45_000) {
                scenario.onActivity { snapshot = it.playbackSnapshot }
                snapshot.error != null || snapshot.ready && snapshot.position > .05
            }
            assertNull(snapshot.error)
            assertTrue(snapshot.ready)
            assertEquals("gpu-next", snapshot.renderer)
            assertEquals("CPU 软件解码", snapshot.decoder)
            assertTrue("Dolby source metadata was not detected", snapshot.profile == expectedProfile || expectedProfile == 5 && snapshot.colorMatrix == "dolbyvision")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("CPU 软件解码").fetchSemanticsNodes().isNotEmpty() }
            screenshot("player-$name")
            if (!checkControls) return@use
            compose.onNodeWithTag("diagnostics").performClick()
            compose.onNodeWithText("播放信息").assertIsDisplayed()
            compose.onNodeWithText("gpu-next").assertIsDisplayed()
            compose.onNodeWithText("SDR / BT.709 · 100 nits 目标").performScrollTo().assertIsDisplayed()
            screenshot("diagnostics-$name")
            compose.onNodeWithText("复制诊断").performClick()
            compose.onNodeWithText("已复制").assertIsDisplayed()
        }
    }

    private fun sampleBytes(name: String): ByteArray {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat /data/local/tmp/oneframe-samples/$name.mp4")).use { it.readBytes() }
        assertTrue("Copy the official Dolby sample with tools/test-device.ps1 first", bytes.size > 1024)
        return bytes
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
