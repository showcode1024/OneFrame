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
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.showcode1024.oneframe.data.*
import io.github.showcode1024.oneframe.ui.OneFrameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

class PlayerActivity : ComponentActivity() {
    companion object {
        fun open(context: Context, uri: String, title: String, start: Double, local: Boolean) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("uri", uri)
                .putExtra("title", title).putExtra("start", start).putExtra("local", local))
        }
        fun openEmby(context: Context, source: StreamSource) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("uri", source.url)
                .putExtra("title", source.title).putExtra("start", source.startSeconds)
                .putExtra("itemId", source.itemId).putExtra("mediaSourceId", source.mediaSourceId)
                .putExtra("playSessionId", source.playSessionId).putExtra("versionName", source.versionName))
        }
    }
    private lateinit var engine: MpvEngine
    private lateinit var store: LocalStore
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var address = ""
    private var title = ""
    private var local = false
    private var position = 0.0
    private var reporter: EmbyReporter? = null
    private var relay: StreamRelay? = null
    private var focusHeld = false
    private var resumeAfterFocus = false
    val playbackSnapshot: PlaybackState get() = engine.state.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        address = intent.getStringExtra("uri") ?: intent.data?.toString().orEmpty()
        title = intent.getStringExtra("title") ?: "视频播放"
        local = intent.getBooleanExtra("local", false)
        position = savedInstanceState?.getDouble("position") ?: intent.getDoubleExtra("start", 0.0)
        engine = MpvEngine(this, store.softwareDecode, store.experimentalP5Hardware, store.fastScaling)
        if (intent.hasExtra("itemId")) store.session()?.let { session ->
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
            surface = { surfaceView() }, close = { finish() },
            rotate = { requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
            copyDiagnostics = {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OneFrame diagnostics", engine.diagnostics()))
            },
        ) } }
    }

    private fun surfaceView(): SurfaceView = SurfaceView(this).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                engine.start(holder.surface, address, position)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
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
    }
    override fun onStop() {
        savePosition(); engine.pause(true)
        audioManager.abandonAudioFocusRequest(focus); focusHeld = false; resumeAfterFocus = false
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) { savePosition(); outState.putDouble("position", position); super.onSaveInstanceState(outState) }
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
