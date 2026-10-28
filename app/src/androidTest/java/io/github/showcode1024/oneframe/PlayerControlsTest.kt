package io.github.showcode1024.oneframe

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.data.LocalStore
import io.github.showcode1024.oneframe.player.PlaybackState
import io.github.showcode1024.oneframe.player.PlayerActivity
import io.github.showcode1024.oneframe.player.VideoSizing
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerControlsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun verticalGesturesAndLockRespectWindowAndMediaVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val oldVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val oldSoftware = LocalStore(context).softwareDecode
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("cat /data/local/tmp/oneframe-samples/dolby-p5.mp4")).use { it.readBytes() }
        assertTrue("Copy the Dolby sample with tools/test-device.ps1", bytes.size > 1024)
        val sample = File(context.cacheDir, "player-controls-p5.mp4").apply { writeBytes(bytes) }
        try {
            LocalStore(context).setSoftwareDecode(true)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            assumeTrue(!audio.isVolumeFixed && max >= 2)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
            val intent = Intent(context, PlayerActivity::class.java).putExtra("uri", sample.toURI().toString())
                .putExtra("title", "手势测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                var snapshot = PlaybackState()
                compose.waitUntil(45_000) {
                    scenario.onActivity { snapshot = it.playbackSnapshot }
                    snapshot.error != null || snapshot.ready && snapshot.position > .05
                }
                assertNull(snapshot.error)
                scenario.onActivity { activity ->
                    val attributes = activity.window.attributes
                    attributes.screenBrightness = .5f
                    activity.window.attributes = attributes
                }
                swipeUp(.15f)
                var brightness = 0f
                scenario.onActivity { brightness = it.window.attributes.screenBrightness }
                assertTrue("Left swipe up should increase window brightness", brightness > .5f)
                swipeUp(.85f)
                assertTrue("Right swipe up should increase media volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC) > max / 2)
                if (compose.onAllNodesWithTag("lockControls").fetchSemanticsNodes().isEmpty()) {
                    compose.onNodeWithTag("videoGestures").performTouchInput { click(percentOffset(.15f, .4f)) }
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("lockControls").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("lockControls").performClick()
                val lockedBrightness = brightness
                swipeUp(.15f)
                scenario.onActivity { brightness = it.window.attributes.screenBrightness }
                assertEquals("Locked gestures must not adjust brightness", lockedBrightness, brightness, .001f)
                compose.onNodeWithTag("videoGestures").performTouchInput { click(percentOffset(.15f, .4f)) }
                compose.onNodeWithTag("unlockControls").assertIsDisplayed().performClick()
                compose.onNodeWithTag("videoSizing").performScrollTo().performClick()
                compose.onNodeWithTag("sizing-FILL").performClick()
                compose.waitUntil(8_000) {
                    scenario.onActivity { snapshot = it.playbackSnapshot }
                    snapshot.videoSizing == VideoSizing.FILL && snapshot.appliedPanscan?.let { it > .99 } == true
                }
            }
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0)
            LocalStore(context).setSoftwareDecode(oldSoftware)
            sample.delete()
        }
    }

    private fun swipeUp(x: Float) {
        compose.onNodeWithTag("videoGestures").performTouchInput {
            down(percentOffset(x, .65f))
            moveTo(percentOffset(x, .35f), delayMillis = 300)
            up()
        }
    }
}
