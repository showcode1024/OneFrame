package io.github.showcode1024.oneframe

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.player.MpvEngine
import io.github.showcode1024.oneframe.player.SubtitleFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlayerSurfaceRecreationTest {
    @Test fun speedAndSubtitleSelectionSurviveSurfaceRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("cat /data/local/tmp/oneframe-samples/dolby-p5.mp4")).use { it.readBytes() }
        assertTrue("Copy the Dolby sample with tools/test-device.ps1", bytes.size > 1024)
        val video = File(context.cacheDir, "surface-recreation-p5.mp4").apply { writeBytes(bytes) }
        val subtitle = File(context.cacheDir, "surface-recreation-test.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:10,000\nTest subtitle\n")
        }
        val engine = MpvEngine(context, software = true)
        val readers = mutableListOf<ImageReader>()
        fun newSurface(): ImageReader = ImageReader.newInstance(640, 360, PixelFormat.RGBA_8888, 3).also { reader ->
            reader.setOnImageAvailableListener({ runCatching { it.acquireLatestImage()?.close() } }, Handler(Looper.getMainLooper()))
            readers.add(reader)
        }
        fun start() {
            val reader = newSurface()
            instrumentation.runOnMainSync { engine.start(reader.surface, video.toURI().toString(), 0.0) }
            waitFor("playback ready") { engine.state.value.error != null || engine.state.value.ready }
            assertNull(engine.state.value.error)
        }
        fun restart() {
            instrumentation.runOnMainSync { engine.release() }
            readers.last().close()
            start()
        }
        try {
            start()
            engine.speed(1.5)
            val added = CountDownLatch(1)
            var error: String? = null
            engine.addSubtitle(SubtitleFile(subtitle, "恢复测试字幕")) { result -> error = result; added.countDown() }
            assertTrue("Subtitle load timed out", added.await(15, TimeUnit.SECONDS))
            assertNull(error)
            waitFor("external subtitle selected") {
                engine.state.value.tracks.any { it.type == "sub" && it.selected && it.label.contains("恢复测试字幕") }
            }
            restart()
            waitFor("speed and selected subtitle restored") {
                val state = engine.state.value
                state.speed == 1.5 && state.tracks.any { it.type == "sub" && it.selected && it.label.contains("恢复测试字幕") }
            }
            engine.track("sub", -1)
            waitFor("subtitles disabled") { engine.state.value.tracks.none { it.type == "sub" && it.selected } }
            restart()
            waitFor("subtitle reloaded without selecting it") {
                val state = engine.state.value
                state.speed == 1.5 && state.tracks.any { it.type == "sub" && it.label.contains("恢复测试字幕") } &&
                    state.tracks.none { it.type == "sub" && it.selected }
            }
        } finally {
            instrumentation.runOnMainSync { engine.close() }
            readers.forEach { runCatching { it.close() } }
            video.delete()
            subtitle.delete()
        }
    }

    private fun waitFor(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for $description")
    }
}
