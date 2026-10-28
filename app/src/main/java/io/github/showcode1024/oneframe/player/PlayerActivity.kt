package io.github.showcode1024.oneframe.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as SystemPlaybackState
import android.os.Bundle
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.showcode1024.oneframe.data.*
import io.github.showcode1024.oneframe.ui.OneFrameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {
    companion object {
        fun open(context: Context, uri: String, title: String, start: Double, local: Boolean) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("uri", uri)
                .putExtra("title", title).putExtra("start", start).putExtra("local", local))
        }
        fun openEmby(context: Context, source: StreamSource) {
            val subtitles = JSONArray().apply { source.externalSubtitles.take(100).forEach {
                put(JSONObject().put("index", it.index).put("title", it.title).put("language", it.language))
            } }
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("uri", source.url)
                .putExtra("title", source.title).putExtra("start", source.startSeconds)
                .putExtra("itemId", source.itemId).putExtra("mediaSourceId", source.mediaSourceId)
                .putExtra("playSessionId", source.playSessionId).putExtra("versionName", source.versionName)
                .putExtra("serverSubtitles", subtitles.toString()))
        }
    }
    private lateinit var engine: MpvEngine
    private lateinit var store: LocalStore
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var displayControls: PlayerDisplayControls
    private lateinit var focus: AudioFocusRequest
    private var address = ""
    private var title = ""
    private var local = false
    private var position = 0.0
    private var reporter: EmbyReporter? = null
    private var relay: StreamRelay? = null
    private var focusHeld = false
    private var resumeAfterFocus = false
    private var subtitleLoading by mutableStateOf(false)
    private var subtitlePickerWasPlaying = false
    private var resumeAfterSubtitlePicker = false
    private var embySession: EmbySession? = null
    private var serverSubtitles: List<EmbySubtitle> = emptyList()
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        resumeAfterSubtitlePicker = subtitlePickerWasPlaying
        subtitlePickerWasPlaying = false
        resumeSubtitlePlaybackIfNeeded()
        if (uri != null) loadSubtitle { SubtitleFiles.importLocal(this, uri) }
    }
    val playbackSnapshot: PlaybackState get() = engine.state.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        address = intent.getStringExtra("uri") ?: intent.data?.toString().orEmpty()
        title = intent.getStringExtra("title") ?: "视频播放"
        local = intent.getBooleanExtra("local", false)
        position = savedInstanceState?.getDouble("position") ?: intent.getDoubleExtra("start", 0.0)
        engine = MpvEngine(this, store.softwareDecode, store.experimentalP5Hardware, store.fastScaling)
        savedInstanceState?.getString("videoSizing")?.let { name ->
            runCatching { VideoSizing.valueOf(name) }.getOrNull()?.let { engine.videoSizing(it) }
        }
        displayControls = PlayerDisplayControls(this)
        if (savedInstanceState?.containsKey("windowBrightness") == true) {
            savedInstanceState.getFloat("windowBrightness").takeIf { it >= 0f }?.let { displayControls.brightness(it) }
        }
        if (intent.hasExtra("itemId")) store.session()?.let { session ->
            embySession = session
            serverSubtitles = runCatching { val array = JSONArray(intent.getStringExtra("serverSubtitles") ?: "[]")
                (0 until minOf(array.length(), 100)).mapNotNull { index -> array.optJSONObject(index)?.let { value ->
                    val trackIndex = value.optInt("index", -1)
                    if (trackIndex < 0) null else EmbySubtitle(trackIndex, value.optString("title", "字幕").take(100), value.optString("language").take(30))
                } }
            }.getOrDefault(emptyList())
            reporter = EmbyReporter(EmbyClient(store.deviceId), session, StreamSource(address,
                intent.getStringExtra("itemId")!!, intent.getStringExtra("mediaSourceId")!!,
                intent.getStringExtra("playSessionId")!!, title, position))
        }
        if (address.startsWith("https://") || address.startsWith("http://")) {
            runCatching {
                relay = StreamRelay(address, if (intent.hasExtra("itemId")) store.session()?.token else null)
                address = relay!!.url
            }.onFailure { address = "" }
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setupAudio()
        volumeControlStream = AudioManager.STREAM_MUSIC
        setupSession()
        engine.onLoaded = { reporter?.send("Start", engine.state.value) }
        engine.onEnded = { reporter?.send("Stop", engine.state.value); savePosition(completed = true) }
        engine.onInteraction = { event -> reporter?.send(event, engine.state.value); updateSystemSession() }
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                savePosition()
                if (engine.state.value.ready) reporter?.send("TimeUpdate", engine.state.value)
                updateSystemSession()
            }
        }
        setContent { OneFrameTheme { PlayerScreen(title, engine, versionName = intent.getStringExtra("versionName").orEmpty(),
            surface = { surfaceView() }, displayControls = displayControls, close = { finish() },
            serverSubtitles = serverSubtitles, subtitleLoading = subtitleLoading,
            pickSubtitle = { subtitlePickerWasPlaying = !engine.state.value.paused
                subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream", "*/*")) },
            loadServerSubtitle = ::loadServerSubtitle,
            rotate = { requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
            copyDiagnostics = {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OneFrame diagnostics", engine.diagnostics()))
            },
        ) } }
    }

    private fun loadSubtitle(acquire: suspend () -> SubtitleFile) {
        if (subtitleLoading) return
        subtitleLoading = true
        lifecycleScope.launch {
            try {
                val subtitle = acquire()
                engine.addSubtitle(subtitle) { error ->
                    subtitleLoading = false
                    if (!isFinishing && !isDestroyed) Toast.makeText(this@PlayerActivity, error ?: "字幕已添加", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                subtitleLoading = false
                val message = e.message?.takeIf { it.any { ch -> ch in '\u4e00'..'\u9fff' } && it.length < 120 } ?: "无法读取字幕，请重试"
                Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadServerSubtitle(subtitle: EmbySubtitle) {
        val session = embySession ?: return
        val itemId = intent.getStringExtra("itemId") ?: return
        val sourceId = intent.getStringExtra("mediaSourceId") ?: return
        if (serverSubtitles.none { it.index == subtitle.index }) return
        loadSubtitle {
            val bytes = EmbyClient(store.deviceId).subtitle(session, itemId, sourceId, subtitle.index)
            SubtitleFiles.importServer(this, bytes, "Emby · ${subtitle.title}")
        }
    }

    private fun resumeSubtitlePlaybackIfNeeded() {
        if (resumeAfterSubtitlePicker && focusHeld) { resumeAfterSubtitlePicker = false; engine.pause(false) }
    }

    private fun surfaceView(): SurfaceView = SurfaceView(this).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                engine.start(holder.surface, address, position)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { engine.surfaceSize(width, height) }
            override fun surfaceDestroyed(holder: SurfaceHolder) { savePosition(); engine.release() }
        })
    }

    private fun setupAudio() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeAfterFocus = !engine.state.value.paused && change != AudioManager.AUDIOFOCUS_LOSS
                        engine.pause(true)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) { engine.pause(false); resumeAfterFocus = false }
                }
            }.build()
    }

    private fun setupSession() {
        mediaSession = MediaSession(this, "OneFrame").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { engine.pause(false) }
                override fun onPause() { engine.pause(true) }
                override fun onSeekTo(pos: Long) { engine.seek(pos / 1000.0) }
                override fun onStop() { engine.pause(true) }
            })
            setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
            isActive = true
        }
    }

    private fun updateSystemSession() {
        val state = engine.state.value
        mediaSession.setPlaybackState(SystemPlaybackState.Builder().setActions(
            SystemPlaybackState.ACTION_PLAY or SystemPlaybackState.ACTION_PAUSE or SystemPlaybackState.ACTION_PLAY_PAUSE or SystemPlaybackState.ACTION_SEEK_TO)
            .setState(if (state.paused) SystemPlaybackState.STATE_PAUSED else SystemPlaybackState.STATE_PLAYING,
                (state.position * 1000).toLong(), state.speed.toFloat()).build())
    }

    private fun savePosition(completed: Boolean = false) {
        val state = engine.state.value
        position = if (completed) 0.0 else PlaybackPolicy.resumePosition(state.position, state.duration)
        if (local) store.remember(RecentMedia(address, title, position))
    }

    override fun onResume() {
        super.onResume()
        focusHeld = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) engine.pause(true)
        else resumeSubtitlePlaybackIfNeeded()
    }
    override fun onStop() {
        savePosition(); engine.pause(true)
        audioManager.abandonAudioFocusRequest(focus); focusHeld = false; resumeAfterFocus = false
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        savePosition(); outState.putDouble("position", position)
        outState.putFloat("windowBrightness", window.attributes.screenBrightness)
        outState.putString("videoSizing", engine.state.value.videoSizing.name)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        reporter?.close(engine.state.value)
        engine.close(); relay?.close(); mediaSession.release()
        audioManager.abandonAudioFocusRequest(focus)
        super.onDestroy()
    }
}

private class EmbyReporter(private val api: EmbyClient, private val session: EmbySession, private val source: StreamSource) {
    private val executor = Executors.newSingleThreadExecutor()
    private var started = false
    private var stopped = false
    fun send(event: String, state: PlaybackState) {
        if (executor.isShutdown) return
        executor.submit {
            if (stopped || event == "Start" && started) return@submit
            if (event != "Start" && !started) return@submit
            runCatching { runBlocking { api.report(session, source, event, state.position, state.paused) } }
                .onSuccess { if (event == "Start") started = true; if (event == "Stop") stopped = true }
        }
    }
    fun close(state: PlaybackState) { send("Stop", state); executor.shutdown() }
}
