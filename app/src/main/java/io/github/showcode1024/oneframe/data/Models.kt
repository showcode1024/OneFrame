package io.github.showcode1024.oneframe.data

data class RecentMedia(val uri: String, val title: String, val position: Double = 0.0)
data class EmbySession(val baseUrl: String, val userId: String, val token: String, val userName: String)
data class LibraryItem(
    val id: String, val title: String, val type: String,
    val year: String = "", val overview: String = "", val imageTag: String = "",
    val folder: Boolean = false, val positionTicks: Long = 0,
    val backdropTag: String = "", val runtimeTicks: Long = 0,
    val genres: List<String> = emptyList(), val rating: String = "", val favorite: Boolean = false,
)
data class EmbySubtitle(val index: Int, val title: String, val language: String = "")
data class MediaVersion(
    val id: String, val name: String, val container: String, val size: Long,
    val width: Int, val height: Int, val videoCodec: String, val audioCodec: String,
    val videoRange: String, val playable: Boolean, val runtimeTicks: Long,
    val externalSubtitles: List<EmbySubtitle> = emptyList(),
)
data class StreamSource(
    val url: String, val itemId: String, val mediaSourceId: String,
    val playSessionId: String, val title: String, val startSeconds: Double,
    val versionName: String = "",
    val externalSubtitles: List<EmbySubtitle> = emptyList(),
)

object PlaybackPolicy {
    const val TICKS_PER_SECOND = 10_000_000L
    fun decoderPreference(forceSoftware: Boolean, experimentalHardware: Boolean = false): String = when {
        forceSoftware -> "no"
        experimentalHardware -> "mediacodec,mediacodec-copy,auto-copy"
        else -> "mediacodec-copy,auto-copy"
    }
    fun shouldFallbackP5(software: Boolean, experimentalHardware: Boolean, profile: Int?, width: Int,
        renderer: String, hwdec: String, matrix: String): Boolean =
        !software && !experimentalHardware && profile == 5 && width > 0 && renderer == "gpu-next" &&
            hwdec.isNotBlank() && hwdec != "no" && matrix.isNotBlank() && matrix != "dolbyvision"

    fun p5ColorStatus(profile: Int?, hwdec: String, matrix: String, experimentalHardware: Boolean): String = when {
        profile != 5 -> "不适用"
        hwdec.isBlank() -> "等待解码器信息"
        hwdec == "no" && matrix == "dolbyvision" -> "软解路径识别到杜比色彩数据；颜色尚未实测验证"
        hwdec == "no" -> "软件解码中，杜比色彩状态未确认"
        experimentalHardware && matrix != "dolbyvision" -> "实验系统解码未识别到杜比色彩数据，画面可能严重偏色"
        experimentalHardware -> "实验系统解码检测到杜比色彩标记，实际颜色仍需验证"
        else -> "杜比色彩状态未确认"
    }
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
