package io.github.showcode1024.oneframe.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

enum class VideoSizing(val label: String) { FIT("适应"), FILL("填充"), STRETCH("拉伸") }

/** Pure calculation keeps the finger's full travel, rather than rounding every motion. */
internal object PlayerGesturePolicy {
    const val MIN_BRIGHTNESS = .03f
    fun adjusted(start: Float, movementPixels: Float, heightPixels: Int, brightness: Boolean): Float {
        val low = if (brightness) MIN_BRIGHTNESS else 0f
        if (heightPixels <= 0 || !movementPixels.isFinite()) return start.coerceIn(low, 1f)
        return (start - movementPixels / heightPixels * 1.5f).coerceIn(low, 1f)
    }
}

/** Brightness affects only this window. Volume affects Android's media stream. */
class PlayerDisplayControls(private val activity: Activity) {
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val volumeFixed: Boolean get() = audio.isVolumeFixed

    fun brightness(): Float {
        val override = activity.window.attributes.screenBrightness
        if (override >= 0f) return override.coerceIn(PlayerGesturePolicy.MIN_BRIGHTNESS, 1f)
        return runCatching { Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
            .getOrDefault(.5f).coerceIn(PlayerGesturePolicy.MIN_BRIGHTNESS, 1f)
    }
    fun brightness(value: Float): Float {
        if (!value.isFinite()) return brightness()
        val actual = value.coerceIn(PlayerGesturePolicy.MIN_BRIGHTNESS, 1f)
        val attributes = activity.window.attributes
        attributes.screenBrightness = actual
        activity.window.attributes = attributes
        return actual
    }
    private fun minimumVolume(): Int = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
    fun volume(): Float = runCatching {
        val min = minimumVolume(); val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= min) 0f else ((audio.getStreamVolume(AudioManager.STREAM_MUSIC) - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }.getOrDefault(0f)
    fun volume(value: Float): Float {
        if (!value.isFinite() || volumeFixed) return volume()
        runCatching {
            val min = minimumVolume(); val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > min) audio.setStreamVolume(AudioManager.STREAM_MUSIC, (min + value.coerceIn(0f, 1f) * (max - min)).roundToInt(), 0)
        }
        return volume()
    }
}
