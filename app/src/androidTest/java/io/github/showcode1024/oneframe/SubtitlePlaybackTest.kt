package io.github.showcode1024.oneframe

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.data.LocalStore
import io.github.showcode1024.oneframe.player.MpvEngine
import io.github.showcode1024.oneframe.player.PlayerActivity
import io.github.showcode1024.oneframe.player.PlaybackState
import io.github.showcode1024.oneframe.player.SubtitleFile
import io.github.showcode1024.oneframe.player.SubtitleFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SubtitlePlaybackTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun externalSrtBecomesSelectedTrackAndItsCacheIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("prism", 0)
        val previous = prefs.getBoolean("software", false)
        LocalStore(context).setSoftwareDecode(true)
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat /data/local/tmp/oneframe-samples/dolby-p5.mp4")).use { it.readBytes() }
        assertTrue("Copy Dolby sample first", bytes.size > 1024)
        val movie = File(context.cacheDir, "subtitle-test-movie.mp4").apply { writeBytes(bytes) }
        val subtitle = File(context.cacheDir, "subtitle-test.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:02,000\n字幕测试\n", Charsets.UTF_8)
        }
        try {
            val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", movie.toURI().toString())
                .putExtra("title", "字幕测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                var snapshot = PlaybackState()
                compose.waitUntil(45_000) {
                    scenario.onActivity { snapshot = it.playbackSnapshot }
                    snapshot.error != null || snapshot.ready && snapshot.position > .05
                }
                assertNull(snapshot.error)
                val result = AtomicReference<String?>("waiting")
                scenario.onActivity { activity ->
                    val field = PlayerActivity::class.java.getDeclaredField("engine").apply { isAccessible = true }
                    val engine = field.get(activity) as MpvEngine
                    engine.pause(true)
                    engine.addSubtitle(SubtitleFile(subtitle, "本地测试字幕")) { result.set(it) }
                }
                compose.waitUntil(15_000) { result.get() != "waiting" }
                assertNull(result.get())
                compose.waitUntil(10_000) {
                    scenario.onActivity { snapshot = it.playbackSnapshot }
                    snapshot.tracks.any { it.type == "sub" && it.selected && it.label.contains("本地测试字幕") }
                }
            }
            assertFalse("Temporary subtitle must be removed when player closes", subtitle.exists())
        } finally { movie.delete(); subtitle.delete(); LocalStore(context).setSoftwareDecode(previous) }
    }

    @Test fun serverTextSubtitleUsesPrivateCacheAndRejectsEmptyOrOversizedData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = SubtitleFiles.importServer(context, "1\n00:00:00,000 --> 00:00:01,000\n你好\n".toByteArray(), "中文字幕")
        try {
            assertTrue(saved.file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
            assertTrue(saved.file.readText().contains("你好"))
        } finally { saved.file.delete() }
        assertTrue(runCatching { SubtitleFiles.importServer(context, byteArrayOf(), "empty") }.isFailure)
        assertTrue(runCatching { SubtitleFiles.importServer(context, ByteArray(SubtitleFiles.MAX_BYTES + 1), "large") }.isFailure)
    }
}
