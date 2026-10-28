package io.github.showcode1024.oneframe.player

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.showcode1024.oneframe.data.PlaybackPolicy
import io.github.showcode1024.oneframe.data.EmbySubtitle
import io.github.showcode1024.oneframe.ui.Mint
import io.github.showcode1024.oneframe.ui.Muted
import io.github.showcode1024.oneframe.ui.formatTime
import kotlinx.coroutines.delay

@Composable fun PlayerScreen(title: String, engine: MpvEngine, versionName: String = "", surface: () -> SurfaceView,
    displayControls: PlayerDisplayControls, close: () -> Unit, rotate: () -> Unit, copyDiagnostics: () -> Unit,
    serverSubtitles: List<EmbySubtitle> = emptyList(), subtitleLoading: Boolean = false,
    pickSubtitle: () -> Unit = {}, loadServerSubtitle: (EmbySubtitle) -> Unit = {}) {
    val state by engine.state.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var subtitleDelay by remember { mutableFloatStateOf(0f) }
    var copied by remember { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var indicator by remember { mutableStateOf<AdjustmentIndicator?>(null) }
    var indicatorTick by remember { mutableIntStateOf(0) }
    var activeAdjustment by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    BackHandler(locked) { controls = true }
    LaunchedEffect(indicatorTick, activeAdjustment) {
        if (!activeAdjustment && indicator != null) { delay(1300); indicator = null }
    }
    LaunchedEffect(controls, state.paused, dialog, dragging, state.ready, locked, activeAdjustment) {
        if (controls && dialog == null && dragging == null && !activeAdjustment && (locked || !state.paused && state.ready)) {
            delay(if (locked) 3000 else 5000); controls = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { surface() }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().testTag("videoGestures")
            .pointerInput(locked, displayControls) {
                if (!locked) awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edge = 16.dp.toPx()
                    if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
                    val brightness = down.position.x < size.width / 2f
                    var overSlop = 0f
                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume(); overSlop = over
                    }
                    if (drag != null) {
                        val start = if (brightness) displayControls.brightness() else displayControls.volume()
                        var distance = overSlop
                        activeAdjustment = true
                        fun adjust() {
                            val requested = PlayerGesturePolicy.adjusted(start, distance, size.height, brightness)
                            val value = if (brightness) displayControls.brightness(requested) else displayControls.volume(requested)
                            indicator = AdjustmentIndicator(brightness, value, !brightness && displayControls.volumeFixed)
                            indicatorTick++
                        }
                        try {
                            adjust()
                            verticalDrag(drag.id) { change ->
                                distance += change.positionChange().y
                                change.consume()
                                adjust()
                            }
                        } finally { activeAdjustment = false }
                    }
                }
            }
            .pointerInput(engine, locked) {
                detectTapGestures(onTap = { if (locked) controls = true else controls = !controls }, onDoubleTap = {
                    if (locked) controls = true else {
                        engine.seek(engine.state.value.position + if (it.x < size.width / 2f) -10 else 10)
                        controls = true
                    }
                })
            })
        if (state.buffering && state.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(42.dp), color = Mint)
        if (state.experimentalHardware && state.profile == 5 && state.hardwareApi.isNotBlank() && state.hardwareApi != "no") {
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 65.dp), color = Color(0xD9904B00), shape = RoundedCornerShape(10.dp)) {
                Text("P5 实验系统解码：色彩未验证，可能偏色", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp)
            }
        }
        if (locked && controls) {
            FilledTonalIconButton(onClick = { locked = false; controls = true }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(54.dp).testTag("unlockControls"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = .68f), contentColor = Color.White)) {
                Icon(Icons.Rounded.Lock, "解锁播放控制")
            }
        }
        indicator?.takeIf { !locked }?.let { AdjustmentHud(it, Modifier.align(Alignment.Center)) }
        if (!locked && (controls || state.paused || !state.ready)) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Text(listOf(versionName, state.decoder).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 10.sp, color = Mint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                    IconButton(onClick = { locked = true; controls = true; dialog = null; indicator = null }, Modifier.testTag("lockControls")) { Icon(Icons.Rounded.LockOpen, "锁定播放控制", tint = Color.White) }
                    IconButton(onClick = { dialog = "diagnostics" }, Modifier.testTag("diagnostics")) { Icon(Icons.Rounded.Info, "播放信息", tint = Color.White) }
                }
            }
            if (!state.buffering && state.error == null) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                IconButton(onClick = { engine.seek(state.position - 10) }) { Icon(Icons.Rounded.Replay10, "后退 10 秒", Modifier.size(32.dp), tint = Color.White) }
                FilledIconButton(onClick = { engine.pause(!state.paused) }, modifier = Modifier.size(64.dp).testTag("playPause"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .15f), contentColor = Color.White)) {
                    Icon(if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (state.paused) "播放" else "暂停", Modifier.size(38.dp))
                }
                IconButton(onClick = { engine.seek(state.position + 10) }) { Icon(Icons.Rounded.Forward10, "前进 10 秒", Modifier.size(32.dp), tint = Color.White) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text(if (state.experimentalHardware && state.profile == 5 && state.hardwareApi.isNotBlank() && state.hardwareApi != "no")
                    "P5 实验系统解码 · 色彩未验证" else PlaybackPolicy.videoSummary(state.profile, state.colorMatrix), color = Mint, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Slider(value = dragging ?: state.position.toFloat().coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { engine.seek(it.toDouble()) }; dragging = null },
                    valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = state.duration > 0,
                    colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint), modifier = Modifier.testTag("seek"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(dragging?.toDouble() ?: state.position), color = Color.White, fontSize = 11.sp)
                    Text(formatTime(state.duration), color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { dialog = "speed" }) { Text("${state.speed}×", color = Color.White, fontSize = 12.sp) }
                    IconButton(onClick = { dialog = "tracks" }) { Icon(Icons.Rounded.Subtitles, "字幕与音轨", tint = Color.White) }
                    IconButton(onClick = { sliderValue = displayControls.brightness(); dialog = "brightness" }, Modifier.testTag("brightnessControl")) { Icon(Icons.Rounded.Brightness6, "调节亮度", tint = Color.White) }
                    IconButton(onClick = { sliderValue = displayControls.volume(); dialog = "volume" }, Modifier.testTag("volumeControl")) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "调节音量", tint = Color.White) }
                    IconButton(onClick = { dialog = "sizing" }, Modifier.testTag("videoSizing")) { Icon(Icons.Rounded.AspectRatio, "画面比例", tint = Color.White) }
                    IconButton(onClick = rotate) { Icon(Icons.Rounded.ScreenRotation, "切换横竖屏", tint = Color.White) }
                }
                Text("左侧上下滑动调亮度 · 右侧调音量", color = Color.White.copy(alpha = .66f), fontSize = 10.sp)
            }
        }
        if (!locked) state.error?.let { message ->
            AlertDialog(onDismissRequest = {}, title = { Text("暂时无法播放") }, text = { Text(message) },
                confirmButton = { TextButton(onClick = close) { Text("返回片库") } },
                dismissButton = { TextButton(onClick = { dialog = "diagnostics" }) { Text("查看诊断") } })
        }
    }
    when (if (locked) null else dialog) {
        "diagnostics" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("播放信息") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DiagnosticRow("视频", state.video)
                if (versionName.isNotBlank()) DiagnosticRow("Emby 版本", versionName)
                DiagnosticRow("解码", state.decoder)
                val decodeLabel = when {
                    state.decodePolicy == "no" -> "强制 CPU 软件解码"
                    state.experimentalHardware -> "实验性系统直连，P5 不自动保色回退"
                    else -> "优先系统解码，失败后回退 CPU"
                }
                DiagnosticRow("解码选择", decodeLabel)
                if (state.fallbackReason.isNotBlank()) { DiagnosticRow("回退原因", state.fallbackReason) }
                if (state.fallbackHardwareApi.isNotBlank()) {
                    DiagnosticRow("回退前解码 / 色彩标记", "${state.fallbackHardwareApi} / ${state.fallbackColorMatrix}")
                }
                if (state.profile == 5) { DiagnosticRow("P5 色彩状态", state.p5ColorStatus) }
                DiagnosticRow("渲染器", state.renderer)
                DiagnosticRow("画面缩放", if (state.fastScaling) "轻量双线性" else "默认")
                DiagnosticRow("杜比片源", state.profile?.let { "Profile $it" } ?: if (state.colorMatrix == "dolbyvision") "识别到杜比色彩数据" else "未检测到 Profile")
                DiagnosticRow("输出目标", state.output)
                DiagnosticRow("音频", state.audio)
                DiagnosticRow("源帧率", "%.2f fps".format(state.fps))
                DiagnosticRow("播放速度", "${state.speed}×")
                DiagnosticRow("呈现丢帧 / 解码丢帧", "${state.dropped} / ${state.decoderDropped}")
                if (state.networkSource) {
                    DiagnosticRow("网络缓冲估计", state.cacheSeconds?.let { "%.1f 秒".format(it) } ?: "当前不可用")
                    DiagnosticRow("缓冲状态", if (state.waitingForCache) "正在等待视频数据" else "未因缓冲暂停")
                }
                Text("这些信息用于确认实际播放路径，不代表杜比认证或画质验收。Atmos 对象渲染和 P7 FEL 完整重建尚未完成。", color = Muted, fontSize = 11.sp, lineHeight = 19.sp)
            }
        }, confirmButton = { TextButton(onClick = { copyDiagnostics(); copied = true }) { Text(if (copied) "已复制" else "复制诊断") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } })
        "speed" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("播放速度") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { listOf(.5, .75, 1.0, 1.25, 1.5, 2.0).forEach { speed ->
                TextButton(onClick = { engine.speed(speed); dialog = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("${speed}×${if (speed == state.speed) "  ✓" else ""}")
                }
            } }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "sizing" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("画面比例") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VideoSizing.entries.forEach { mode ->
                    TextButton(onClick = { engine.videoSizing(mode); dialog = null }, modifier = Modifier.fillMaxWidth().testTag("sizing-${mode.name}")) {
                        Text(mode.label + when (mode) { VideoSizing.FIT -> " · 完整画面"; VideoSizing.FILL -> " · 裁剪边缘"; VideoSizing.STRETCH -> " · 可能变形" } + if (state.videoSizing == mode) "  ✓" else "")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } })
        "brightness", "volume" -> {
            val brightness = dialog == "brightness"
            AlertDialog(onDismissRequest = { dialog = null }, title = { Text(if (brightness) "亮度" else "媒体音量") }, text = {
                Column {
                    Text(if (!brightness && displayControls.volumeFixed) "当前输出设备的音量由系统或外接设备控制" else "${(sliderValue * 100).toInt()}%", color = Muted)
                    Slider(value = sliderValue, onValueChange = { value ->
                        sliderValue = if (brightness) displayControls.brightness(value) else displayControls.volume(value)
                    }, valueRange = if (brightness) PlayerGesturePolicy.MIN_BRIGHTNESS..1f else 0f..1f,
                        enabled = brightness || !displayControls.volumeFixed)
                    Text(if (brightness) "仅改变当前播放窗口的亮度" else "调整设备媒体音量", color = Muted, fontSize = 12.sp)
                }
            }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("完成") } })
        }
        "tracks" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("音轨与字幕") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("音轨", color = Mint, fontSize = 12.sp)
                val audio = state.tracks.filter { it.type == "audio" }
                if (audio.isEmpty()) Text("没有可切换的音轨", color = Muted, fontSize = 12.sp)
                audio.forEach { track -> TextButton(onClick = { engine.track("audio", track.id) }) {
                    Text((if (track.selected) "✓  " else "") + track.label, maxLines = 2)
                } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("字幕", color = Mint, fontSize = 12.sp)
                TextButton(onClick = { engine.track("sub", -1) }) { Text("关闭字幕") }
                state.tracks.filter { it.type == "sub" }.forEach { track -> TextButton(onClick = { engine.track("sub", track.id) }) {
                    Text((if (track.selected) "✓  " else "") + track.label, maxLines = 2)
                } }
                TextButton(onClick = { dialog = null; pickSubtitle() }, enabled = !subtitleLoading, modifier = Modifier.testTag("addLocalSubtitle")) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("添加本地字幕")
                }
                if (serverSubtitles.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Emby 外挂字幕", color = Mint, fontSize = 12.sp)
                    if (subtitleLoading) Text("正在加载字幕…", color = Muted, fontSize = 12.sp)
                    serverSubtitles.forEach { subtitle ->
                        TextButton(onClick = { loadServerSubtitle(subtitle) }, enabled = !subtitleLoading,
                            modifier = Modifier.fillMaxWidth().testTag("serverSubtitle-${subtitle.index}")) {
                            Text(subtitle.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("服务器文本字幕以 SRT 加载；图片字幕暂不支持。", color = Muted, fontSize = 11.sp)
                }
                Text("字幕延迟：%.1f 秒".format(subtitleDelay), color = Muted, fontSize = 12.sp)
                Slider(subtitleDelay, { subtitleDelay = it; engine.subtitleDelay(it.toDouble()) }, valueRange = -5f..5f)
            }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("完成") } })
    }
}

private data class AdjustmentIndicator(val brightness: Boolean, val value: Float, val fixedVolume: Boolean)

@Composable private fun AdjustmentHud(indicator: AdjustmentIndicator, modifier: Modifier = Modifier) {
    Surface(modifier.testTag("adjustmentHud"), shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .78f), contentColor = Color.White) {
        Column(Modifier.width(180.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (indicator.brightness) Icons.Rounded.Brightness6 else if (indicator.value <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                null, Modifier.size(30.dp), tint = Color.White)
            Text(if (indicator.fixedVolume) "音量由外接设备控制" else (if (indicator.brightness) "亮度 " else "音量 ") + "${(indicator.value * 100).toInt()}%", fontSize = 13.sp)
            LinearProgressIndicator(progress = { indicator.value }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Mint, trackColor = Color.White.copy(alpha = .18f))
        }
    }
}

@Composable private fun DiagnosticRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value.ifBlank { "等待媒体信息" }, fontSize = 13.sp)
    }
}
