package io.github.showcode1024.oneframe.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import io.github.showcode1024.oneframe.data.PlaybackPolicy
import io.github.showcode1024.oneframe.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class MediaTrack(val id: Int, val type: String, val label: String, val selected: Boolean)
data class PlaybackState(
    val ready: Boolean = false, val paused: Boolean = true, val buffering: Boolean = true,
    val position: Double = 0.0, val duration: Double = 0.0,
    val decoder: String = "等待视频", val video: String = "", val audio: String = "",
    val profile: Int? = null, val colorMatrix: String = "", val output: String = "SDR / BT.709 · 100 nits 目标",
    val renderer: String = "等待渲染器", val fps: Double = 0.0, val dropped: Int = 0,
    val decoderDropped: Int = 0, val cacheSeconds: Double? = null, val waitingForCache: Boolean = false,
    val networkSource: Boolean = false, val decodePolicy: String = "", val hardwareApi: String = "",
    val fallbackReason: String = "",
    val tracks: List<MediaTrack> = emptyList(), val speed: Double = 1.0,
    val error: String? = null, val mpvVersion: String = "", val ffmpegVersion: String = "",
)

class MpvEngine(private val context: Context, private val software: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val statisticsThread = HandlerThread("OneFrame-statistics").apply { start() }
    private val statisticsHandler = Handler(statisticsThread.looper)
    private val nativeLock = Any()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()
    private var mpv: MPVLib? = null
    private var descriptor: ParcelFileDescriptor? = null
    @Volatile private var released = false
    private var closed = false
    private var colorFallbackRequested = false
    private var desiredPause = false
    var onLoaded: (() -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onInteraction: ((String) -> Unit)? = null

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit
        override fun event(eventId: Int) {
            handler.post {
                if (released) return@post
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> { poll(); onLoaded?.invoke() }
                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                        poll()
                        if (state.value.duration > 0 && state.value.position >= state.value.duration - 2) onEnded?.invoke()
                    }
                    MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> poll()
                }
            }
        }
    }

    fun start(surface: Surface, address: String, start: Double) {
        synchronized(nativeLock) {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (mpv != null || closed) return
            released = false
            colorFallbackRequested = false
            mutableState.value = PlaybackState(position = start,
                networkSource = address.startsWith("https://") || address.startsWith("http://"),
                decodePolicy = PlaybackPolicy.decoderPreference(software))
            try {
                val uri = Uri.parse(address)
                require(uri.scheme in setOf("content", "file", "https", "http")) { "不支持此视频地址类型" }
                require(uri.userInfo.isNullOrEmpty()) { "视频地址不能包含明文账号和密码" }
                val path = when (uri.scheme) {
                    "content" -> {
                        descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: error("无法读取视频，请重新选择文件")
                        "fd://${descriptor!!.fd}"
                    }
                    "file" -> uri.path ?: error("无效文件路径")
                    else -> address
                }
                val instance = MPVLib.create(context) ?: error("无法创建播放引擎")
                mpv = instance
                fun required(name: String, value: String) {
                    if (instance.setOptionString(name, value) < 0) error("播放引擎不支持 $name，无法启用要求的渲染路径")
                }
                required("config", "no")
                required("vo", "gpu-next")
                required("gpu-api", "opengl")
                required("gpu-context", "android")
                required("hwdec", PlaybackPolicy.decoderPreference(software))
                required("target-prim", "bt.709")
                required("target-trc", "bt.1886")
                required("target-peak", "100")
                required("target-colorspace-hint", "no")
                required("tone-mapping", "bt.2390")
                required("audio-channels", "stereo")
                required("ao", "audiotrack")
                required("tls-verify", "yes")
                required("idle", "yes")
                required("keep-open", "yes")
                required("cache", "yes")
                required("demuxer-max-bytes", "64MiB")
                required("demuxer-max-back-bytes", "16MiB")
                required("network-timeout", "20")
                required("input-default-bindings", "no")
                required("input-vo-keyboard", "no")
                instance.setOptionString("sub-fonts-dir", "/system/fonts")
                instance.setOptionString("sub-font", "sans-serif")
                instance.setOptionString("sub-font-size", "42")
                instance.setOptionString("osd-level", "0")
                instance.setOptionString("msg-level", "all=warn")
                if (start.isFinite() && start > 0) instance.setOptionString("start", start.toString())
                instance.attachSurface(surface)
                instance.addObserver(observer)
                instance.init()
                instance.command(arrayOf("loadfile", path))
                instance.setPropertyBoolean("pause", desiredPause)
                statisticsHandler.postDelayed(ticker, 350)
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(buffering = false,
                    error = when (error) {
                        is UnsatisfiedLinkError -> "当前设备架构无法加载原生播放引擎"
                        is SecurityException -> "视频访问权限已失效，请返回后重新选择文件"
                        else -> error.message?.takeUnless { it.contains(address) } ?: "播放器初始化失败"
                    })
                release()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!released) { poll(); if (!released) statisticsHandler.postDelayed(this, 1000) }
        }
    }

    private fun poll() {
        synchronized(nativeLock) {
            val instance = mpv ?: return
            if (released) return
            runCatching {
                val tracks = mutableListOf<MediaTrack>()
                var profile: Int? = null
                val count = instance.getPropertyInt("track-list/count")?.coerceIn(0, 64) ?: 0
                for (index in 0 until count) {
                    val prefix = "track-list/$index/"
                    val type = instance.getPropertyString(prefix + "type").orEmpty()
                    if (type == "video") profile = instance.getPropertyInt(prefix + "dolby-vision-profile")?.takeIf { it > 0 }
                    if (type == "audio" || type == "sub") {
                        val id = instance.getPropertyInt(prefix + "id") ?: continue
                        val codec = instance.getPropertyString(prefix + "codec").orEmpty()
                        val language = instance.getPropertyString(prefix + "lang").orEmpty()
                        val title = instance.getPropertyString(prefix + "title").orEmpty()
                        tracks += MediaTrack(id, type, listOf(title, language, codec).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "轨道 $id" },
                            instance.getPropertyBoolean(prefix + "selected") ?: false)
                    }
                }
                val hwdec = instance.getPropertyString("hwdec-current").orEmpty()
                val codec = instance.getPropertyString("video-codec").orEmpty()
                val audioCodec = if (tracks.any { it.type == "audio" }) instance.getPropertyString("audio-codec-name").orEmpty() else ""
                val old = state.value
                val width = instance.getPropertyInt("width") ?: 0
                val height = instance.getPropertyInt("height") ?: 0
                val renderer = instance.getPropertyString("current-vo").orEmpty()
                val idle = instance.getPropertyBoolean("idle-active") ?: false
                val matrix = instance.getPropertyString("video-params/colormatrix").orEmpty()
                if (!software && !colorFallbackRequested && profile == 5 && width > 0 && renderer == "gpu-next"
                    && hwdec.isNotBlank() && hwdec != "no" && matrix.isNotBlank() && matrix != "dolbyvision") {
                    colorFallbackRequested = true
                    instance.setPropertyString("hwdec", "no")
                }
                val waitingForCache = instance.getPropertyBoolean("paused-for-cache") == true
                val error = if (idle && !old.ready && old.error == null) "无法播放该视频，请检查文件、网络或图形驱动支持" else old.error
                mutableState.value = old.copy(
                    ready = width > 0 && renderer == "gpu-next",
                    position = instance.getPropertyDouble("time-pos")?.takeIf { it.isFinite() && it >= 0 } ?: old.position,
                    duration = instance.getPropertyDouble("duration")?.takeIf { it.isFinite() && it >= 0 } ?: old.duration,
                    paused = instance.getPropertyBoolean("pause") ?: true,
                    buffering = !idle && (width == 0 || waitingForCache),
                    waitingForCache = waitingForCache,
                    cacheSeconds = instance.getPropertyDouble("demuxer-cache-duration")?.takeIf { it.isFinite() && it >= 0 },
                    decoder = if (codec.isBlank()) "等待视频" else if (hwdec.isBlank() || hwdec == "no") "CPU 软件解码" else "系统解码 · $hwdec",
                    hardwareApi = hwdec,
                    fallbackReason = if (colorFallbackRequested) "已请求 CPU 回退，以保留 P5 色彩处理" else "",
                    video = if (width > 0) "$width × $height · $codec" else "等待视频信息",
                    audio = if (tracks.none { it.type == "audio" }) "无音轨" else PlaybackPolicy.audioSummary(audioCodec),
                    profile = profile, colorMatrix = matrix,
                    renderer = renderer.ifBlank { "等待渲染器" },
                    fps = instance.getPropertyDouble("estimated-vf-fps")?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                    dropped = instance.getPropertyInt("frame-drop-count") ?: 0,
                    decoderDropped = instance.getPropertyInt("decoder-frame-drop-count") ?: 0,
                    tracks = tracks, speed = instance.getPropertyDouble("speed") ?: 1.0,
                    mpvVersion = instance.getPropertyString("mpv-version").orEmpty(),
                    ffmpegVersion = instance.getPropertyString("ffmpeg-version").orEmpty(), error = error,
                )
            }.onFailure { mutableState.value = state.value.copy(error = "读取播放状态失败", buffering = false) }
        }
    }

    fun pause(value: Boolean) {
        synchronized(nativeLock) {
        desiredPause = value
        mpv?.setPropertyBoolean("pause", value)
        mutableState.value = state.value.copy(paused = value)
        }
        onInteraction?.invoke(if (value) "Pause" else "Unpause")
    }
    fun seek(seconds: Double) {
        if (!seconds.isFinite()) return
        synchronized(nativeLock) {
        mpv?.command(arrayOf("seek", seconds.coerceIn(0.0, state.value.duration.coerceAtLeast(0.0)).toString(), "absolute+exact"))
        mutableState.value = state.value.copy(position = seconds.coerceAtLeast(0.0))
        }
        onInteraction?.invoke("TimeUpdate")
    }
    fun speed(value: Double) {
        synchronized(nativeLock) { mpv?.setPropertyDouble("speed", value); mutableState.value = state.value.copy(speed = value) }
        onInteraction?.invoke("PlaybackRateChange")
    }
    fun track(type: String, id: Int) {
        synchronized(nativeLock) { mpv?.setPropertyString(if (type == "audio") "aid" else "sid", if (id < 0) "no" else id.toString()); poll() }
        onInteraction?.invoke(if (type == "audio") "AudioTrackChange" else "SubtitleTrackChange")
    }
    fun subtitleDelay(value: Double) {
        synchronized(nativeLock) { mpv?.setPropertyDouble("sub-delay", value) }
        onInteraction?.invoke("SubtitleOffsetChange")
    }

    fun diagnostics(): String = JSONObject().apply {
        val data = state.value
        put("app", "OneFrame ${BuildConfig.VERSION_NAME}"); put("mpv", data.mpvVersion); put("ffmpeg", data.ffmpegVersion)
        put("device", android.os.Build.MODEL); put("androidApi", android.os.Build.VERSION.SDK_INT)
        put("manufacturer", android.os.Build.MANUFACTURER); put("osRelease", android.os.Build.VERSION.RELEASE)
        put("capturedAtElapsedRealtimeMs", android.os.SystemClock.elapsedRealtime())
        put("playbackPositionSeconds", data.position); put("durationSeconds", data.duration)
        put("decoder", data.decoder); put("renderer", data.renderer); put("video", data.video)
        put("requestedDecodePolicy", data.decodePolicy); put("activeDecodeApi", data.hardwareApi)
        put("fallbackReason", data.fallbackReason)
        put("dolbyVisionProfile", data.profile ?: JSONObject.NULL); put("colorMatrix", data.colorMatrix)
        put("output", data.output); put("audio", data.audio); put("fps", data.fps); put("droppedFrames", data.dropped)
        put("decoderDroppedFrames", data.decoderDropped); put("bufferedSecondsEstimate", data.cacheSeconds ?: JSONObject.NULL)
        put("waitingForCache", data.waitingForCache); put("networkSource", data.networkSource)
        put("hevcDecoderCapabilities", DeviceCapabilities.hevcDecoders())
        put("fullFelReconstructionVerified", false); put("atmosObjectRendering", false)
    }.toString(2)

    fun release() {
        synchronized(nativeLock) {
            released = true
            statisticsHandler.removeCallbacks(ticker)
            mpv?.let { instance -> runCatching { instance.removeObserver(observer); instance.destroy() } }
            mpv = null
            runCatching { descriptor?.close() }; descriptor = null
        }
    }

    fun close() {
        synchronized(nativeLock) { closed = true; release() }
        statisticsThread.quitSafely()
    }
}
