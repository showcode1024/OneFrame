package io.github.showcode1024.oneframe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.showcode1024.oneframe.HomeState
import io.github.showcode1024.oneframe.MainViewModel
import io.github.showcode1024.oneframe.update.UpdateViewModel
import io.github.showcode1024.oneframe.BuildConfig
import io.github.showcode1024.oneframe.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun HomeScreen(
    model: MainViewModel, updater: UpdateViewModel, pickFile: () -> Unit, playRecent: (RecentMedia) -> Unit,
    playUrl: (String) -> Unit, playEmby: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var login by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    BackHandler(state.detail != null) { model.closeDetail() }
    BackHandler(state.detail == null && tab == 0 && (state.path.isNotEmpty() || state.search.isNotBlank())) {
        if (state.search.isNotBlank()) model.search("") else model.back()
    }
    if (state.detail != null && state.session != null) {
        Surface(color = Ink, contentColor = MaterialTheme.colorScheme.onBackground) { CinematicDetailScreen(state, model, playEmby) }
        return
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(containerColor = Ink, bottomBar = {
        if (!wide) NavigationBar(containerColor = Color(0xFF191A1D), tonalElevation = 0.dp) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationBarItem(
                    selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                        selectedIconColor = Mint, selectedTextColor = Mint, unselectedIconColor = Muted, unselectedTextColor = Muted),
                ) }
        }
    }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        if (wide) NavigationRail(containerColor = Color(0xFF191A1D), modifier = Modifier.fillMaxHeight()) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationRailItem(selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationRailItemDefaults.colors(indicatorColor = Mint.copy(alpha = .12f), selectedIconColor = Mint, selectedTextColor = Mint,
                        unselectedIconColor = Muted, unselectedTextColor = Muted)) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 36.dp else 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(21.dp), tint = Color(0xFF151515))
                }
                Spacer(Modifier.width(10.dp))
                Text("OneFrame", fontSize = 17.sp, letterSpacing = (-.3).sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(onClick = { if (state.session == null) login = true else tab = 2 }, shape = RoundedCornerShape(50), color = Panel) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.session == null) Icons.Rounded.Add else Icons.Rounded.Person, null, Modifier.size(15.dp), tint = Mint)
                        Spacer(Modifier.width(5.dp))
                        Text(state.session?.userName ?: "连接", color = Color(0xFFDDE7F4), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            state.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = model::dismissError) { Icon(Icons.Rounded.Close, "关闭提示") }
                    }
                }
            }
            when (tab) {
                0 -> if (state.session == null) InfuseWelcome(pickFile, { login = true }, { tab = 1 }, state.recents, playRecent)
                else if (state.path.isEmpty() && state.search.isBlank()) InfuseEmbyHome(state, model)
                else InfuseLibrary(state.items, state.path.lastOrNull()?.title ?: "我的媒体库", state.search, state.loading, state.canLoadMore, state.path.isNotEmpty(),
                    state.session!!, model, { if (it.folder || it.type in listOf("Series", "Season", "CollectionFolder", "BoxSet")) model.openFolder(it) else model.openDetail(it) })
                1 -> LocalLibrary(state.recents, pickFile, { urlDialog = true }, playRecent, model::forget)
                2 -> Settings(state.softwareDecode, model::softwareDecode, state.experimentalP5Hardware, model::experimentalP5Hardware,
                    state.fastScaling, model::fastScaling, state.session, state.servers, { login = true }, model::switchServer, model::removeServer, updater)
            }
        }
        }
    }
    if (login) LoginDialog(state.loading, state.error, { login = false }, { address, user, password ->
        model.login(address, user, password) { login = false; tab = 0 }
    })
    if (urlDialog) UrlDialog({ urlDialog = false }) { urlDialog = false; playUrl(it) }
}

@Composable private fun LocalLibrary(recents: List<RecentMedia>, pick: () -> Unit, url: () -> Unit, play: (RecentMedia) -> Unit, forget: (String) -> Unit) {
    val inset = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
    LazyColumn(contentPadding = PaddingValues(horizontal = inset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("本地片库", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
            Text("设备中的影片与播放记录", color = Muted, fontSize = 13.sp)
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = pick, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null, Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("添加视频") }
            OutlinedButton(onClick = url, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Link, null, Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("播放链接") }
        } }
        item { HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 12.dp)) }
        if (recents.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Movie, null, Modifier.size(34.dp), tint = Mint) }
            Text("这里还没有视频", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("支持 MP4、MKV 等常见格式", color = Muted, fontSize = 12.sp)
        } }
        items(recents, key = { it.uri }) { item -> RecentRow(item, { play(item) }, { forget(item.uri) }) }
    }
}

@Composable private fun RecentRow(item: RecentMedia, play: () -> Unit, remove: (() -> Unit)?) {
    Surface(onClick = play, shape = RoundedCornerShape(16.dp), color = Panel) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Mint.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Mint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (item.position >= 1) "继续观看 · ${formatTime(item.position)}" else "本地视频", color = Muted, fontSize = 11.sp)
            }
            if (remove != null) IconButton(onClick = remove) { Icon(Icons.Rounded.Close, "移除记录", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable private fun Settings(software: Boolean, setSoftware: (Boolean) -> Unit, experimentalHardware: Boolean,
    setExperimentalHardware: (Boolean) -> Unit, fastScaling: Boolean, setFastScaling: (Boolean) -> Unit, session: EmbySession?, servers: List<EmbySession>,
    connect: () -> Unit, switch: (EmbySession) -> Unit, remove: (EmbySession) -> Unit, updater: UpdateViewModel) {
    var removing by remember { mutableStateOf<EmbySession?>(null) }
    val updateState by updater.state.collectAsStateWithLifecycle()
    val inset = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
    LazyColumn(contentPadding = PaddingValues(horizontal = inset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("设置", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
            Text("连接、播放和应用偏好", color = Muted, fontSize = 13.sp)
        } }
        item { SettingCard("软件更新") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("自动检查更新")
                    Text("启动时检查 GitHub Release，发现新版本后提醒。预览版会接收预览更新。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Switch(updateState.automatic, updater::automatic, modifier = Modifier.testTag("autoUpdate"))
            }
            Text("当前版本 ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            TextButton(onClick = { if (updateState.release != null) updater.show() else updater.check() },
                enabled = !updateState.checking, modifier = Modifier.testTag("checkUpdates")) {
                Text(if (updateState.checking) "正在检查…" else if (updateState.release != null) "查看更新" else "检查更新")
            }
        } }
        item { SettingCard("播放方式") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("强制 CPU 软件解码")
                    Text("默认关闭，优先尝试系统解码底层视频，杜比色彩映射仍由播放器完成。色彩异常时可开启 CPU 软解；高分辨率软解可能卡顿。修改后需重新打开视频。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Switch(software, setSoftware)
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("实验性系统直连解码")
                    Text("优先尝试系统直连，P5 不再自动回退软解。可能偏绿、偏紫或无法播放，颜色没有经过验证；其他视频也会尝试直连。关闭后恢复保色回退。需重新打开视频。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                    if (software) Text("请先关闭强制 CPU 软件解码。", color = Mint, fontSize = 12.sp)
                }
                Switch(experimentalHardware, setExperimentalHardware, modifier = Modifier.testTag("p5Experiment"), enabled = !software)
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("轻量画面缩放")
                    Text("降低画面缩放的 GPU 负载，保留杜比色彩映射；细节可能略有变化，对 CPU 解码瓶颈不一定有效。需重新打开视频。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Switch(fastScaling, setFastScaling, modifier = Modifier.testTag("fastScaling"))
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 18.dp))
            Text("画面输出", fontSize = 14.sp)
            Text("统一映射至 SDR / BT.709，适合没有杜比视界能力的屏幕。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 7.dp))
        } }
        item { SettingCard("Emby 服务器") {
            if (servers.isEmpty()) Text("尚未连接服务器", color = Muted, fontSize = 13.sp)
            servers.forEach { server ->
                Row(Modifier.fillMaxWidth().clickable { switch(server) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (server == session) Icons.Rounded.CheckCircle else Icons.Rounded.Dns, null, tint = if (server == session) Mint else Muted)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(server.userName + if (server == session) " · 当前" else "", fontSize = 14.sp)
                        Text(server.baseUrl, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { removing = server }) { Icon(Icons.Rounded.DeleteOutline, "移除服务器 ${server.userName}", tint = Muted) }
                }
            }
            TextButton(onClick = connect, contentPadding = PaddingValues(top = 14.dp)) { Text("添加服务器") }
        } }
        item { SettingCard("预览版能力") {
            Text("杜比视界的软件映射路径已接入，需通过真实片源与设备验收。Profile 7 FEL 完整重建尚未验证。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("音频输出为普通双声道 PCM，尚未实现 Atmos 对象解码与空间渲染。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
        } }
        item { Text("ONEFRAME  /  ${BuildConfig.VERSION_NAME}\n基于 libmpv · FFmpeg · libplacebo · libass", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 20.sp) }
    }
    removing?.let { server -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除服务器？") },
        text = { Text("将退出 ${server.userName}（${server.baseUrl}），并从此设备移除账号。") },
        confirmButton = { TextButton(onClick = { remove(server); removing = null }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}

@Composable private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, color = Color(0xFFE5EBF4), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp)); content()
    } }
}

@Composable private fun LoginDialog(busy: Boolean, error: String?, dismiss: () -> Unit, login: (String, String, String) -> Unit) {
    var address by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var visible by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("连接 Emby") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("使用你自己的服务器和账号登录", color = Muted, fontSize = 12.sp)
            OutlinedTextField(address, { address = it }, label = { Text("服务器地址") }, placeholder = { Text("https://emby.example.com") },
                singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            if (address.trim().startsWith("http://", true)) Text("HTTP 会明文传输账号与视频，请仅在可信网络使用。", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            OutlinedTextField(name, { name = it }, label = { Text("用户名") }, singleLine = true, enabled = !busy)
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, enabled = !busy,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "显示或隐藏密码") } })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { login(address, name, password) }, enabled = !busy && address.isNotBlank() && name.isNotBlank()) { Text(if (busy) "正在连接…" else "连接") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("取消") } })
}

@Composable private fun UrlDialog(dismiss: () -> Unit, play: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    val parsed = value.trim().toHttpUrlOrNull()
    val valid = parsed != null && parsed.username.isEmpty() && parsed.password.isEmpty()
    AlertDialog(onDismissRequest = dismiss, title = { Text("播放网络链接") }, text = {
        OutlinedTextField(value, { value = it }, label = { Text("HTTP / HTTPS 视频地址") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 4)
    }, confirmButton = { TextButton(onClick = { play(value.trim()) }, enabled = valid) { Text("播放") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

fun formatTime(seconds: Double): String {
    val total = if (seconds.isFinite()) seconds.toLong().coerceAtLeast(0) else 0
    val hour = total / 3600; val minute = total / 60 % 60; val second = total % 60
    return if (hour > 0) "%d:%02d:%02d".format(hour, minute, second) else "%02d:%02d".format(minute, second)
}
