package io.github.showcode1024.oneframe.data

data class RecentMedia(val uri: String, val title: String, val position: Double = 0.0)
data class EmbySession(val baseUrl: String, val userId: String, val token: String, val userName: String)
data class LibraryItem(
    val id: String, val title: String, val type: String,
    val year: String = "", val overview: String = "", val imageTag: String = "",
    val folder: Boolean = false, val positionTicks: Long = 0,
)
data class StreamSource(
    val url: String, val itemId: String, val mediaSourceId: String,
    val playSessionId: String, val title: String, val startSeconds: Double,
)

object PlaybackPolicy {
    const val TICKS_PER_SECOND = 10_000_000L
    fun decoderPreference(forceSoftware: Boolean): String = if (forceSoftware) "no" else "mediacodec-copy,auto-copy"
    fun secondsToTicks(seconds: Double): Long =
        if (!seconds.isFinite() || seconds < 0) 0 else
            (seconds.coerceAtMost(Long.MAX_VALUE.toDouble() / TICKS_PER_SECOND) * TICKS_PER_SECOND).toLong()

    fun resumePosition(position: Double, duration: Double): Double = when {
        !position.isFinite() || position < 0 -> 0.0
        duration > 0 && position >= duration - 3 -> 0.0
        else -> position
    }

    fun videoSummary(profile: Int?, colorMatrix: String): String = when {
        profile == 7 -> "杜比视界 P7 · SDR 映射；FEL 完整重建未验证"
        profile != null && profile > 0 -> "杜比视界 P$profile · SDR 映射路径"
        colorMatrix == "dolbyvision" -> "杜比视界 · SDR 映射路径"
        else -> "SDR 输出 · HDR 片源自动映射"
    }

    // A source's Atmos tag never proves object decoding or spatial rendering.
    fun audioSummary(codec: String): String = when {
        codec.contains("truehd", true) || codec.contains("eac3", true) || codec.contains("ac3", true) ->
            "FFmpeg 解码 → 双声道；Atmos 对象渲染未实现"
        else -> "双声道 PCM 输出"
    }
}
