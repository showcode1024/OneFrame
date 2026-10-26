package io.github.showcode1024.oneframe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.showcode1024.oneframe.MainViewModel
import io.github.showcode1024.oneframe.BuildConfig
import io.github.showcode1024.oneframe.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun HomeScreen(
    model: MainViewModel, pickFile: () -> Unit, playRecent: (RecentMedia) -> Unit,
    playUrl: (String) -> Unit, playEmby: (LibraryItem) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var login by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LibraryItem?>(null) }
    BackHandler(tab == 0 && state.path.isNotEmpty()) { model.back() }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationBarItem(
                    selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Mint.copy(alpha = .12f)),
                ) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(32.dp), tint = Mint)
                Text("OneFrame", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = Panel) {
                    Text("预览版 ${BuildConfig.VERSION_NAME.substringBefore('-')}", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Mint, fontSize = 11.sp)
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
                0 -> if (state.session == null) Welcome(pickFile, { login = true }, { tab = 1 }, state.recents, playRecent)
                else Library(state.items, state.path.lastOrNull()?.title ?: "我的媒体库", state.search, state.loading, state.canLoadMore, state.path.isNotEmpty(),
                    state.session!!, model, { if (it.folder || it.type in listOf("Series", "Season", "CollectionFolder", "BoxSet")) model.openFolder(it) else selected = it })
                1 -> LocalLibrary(state.recents, pickFile, { urlDialog = true }, playRecent, model::forget)
                2 -> Settings(state.softwareDecode, model::softwareDecode, state.session, { login = true }, model::logout)
            }
        }
    }
    if (login) LoginDialog(state.loading, state.error, { login = false }, { address, user, password ->
        model.login(address, user, password) { login = false; tab = 0 }
    })
    if (urlDialog) UrlDialog({ urlDialog = false }) { urlDialog = false; playUrl(it) }
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.year.isNotBlank()) Text(item.year, color = Mint)
            Text(item.overview.ifBlank { "暂无简介" }, maxLines = 10, overflow = TextOverflow.Ellipsis)
            if (item.positionTicks > 0) Text("将从上次观看的位置继续", color = Mint)
        } }, confirmButton = { TextButton(onClick = { selected = null; playEmby(item) }) { Text("开始播放") } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text("返回") } }) }
}

@Composable private fun Welcome(pickFile: () -> Unit, connect: () -> Unit, local: () -> Unit, recents: List<RecentMedia>, play: (RecentMedia) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text("你的电影，自在观看。", color = Muted, fontSize = 13.sp) }
        item { Box(Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF203B3A), Color(0xFF15262F), Color(0xFF142028))))) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * .82f; val y = size.height * .42f; val r = size.height * .29f
                drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = .15f), Color.Transparent), Offset(x, y), r * 1.7f), r * 1.7f, Offset(x, y))
                for (i in 0..3) {
                    val shift = i * 15f
                    val path = Path().apply { moveTo(x - r * .65f + shift, y - r); lineTo(x + r * .7f + shift, y); lineTo(x - r * .65f + shift, y + r); close() }
                    drawPath(path, Mint.copy(alpha = .35f - i * .07f), style = Stroke(2f))
                }
            }
            Column(Modifier.padding(24.dp).align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("PRESS PLAY.\nSTAY A LITTLE.", color = Mint, fontSize = 10.sp, letterSpacing = 3.sp, lineHeight = 18.sp)
                Text("从一部\n好电影开始", fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 42.sp)
                Button(onClick = pickFile, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("打开本地视频") }
            }
        } }
        item { Surface(onClick = connect, shape = RoundedCornerShape(18.dp), color = Panel) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dns, null, tint = Mint, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("连接你的 Emby", fontWeight = FontWeight.Medium)
                    Text("让私人影院，随身而行", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "连接")
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("最近打开", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = local, contentPadding = PaddingValues(0.dp)) { Text("查看全部", fontSize = 12.sp) }
        } }
        if (recents.isEmpty()) item { Text("打开一个视频，下一次从这里继续。", color = Muted, fontSize = 13.sp) }
        items(recents.take(3), key = { it.uri }) { RecentRow(it, { play(it) }, null) }
    }
}

@Composable private fun LocalLibrary(recents: List<RecentMedia>, pick: () -> Unit, url: () -> Unit, play: (RecentMedia) -> Unit, forget: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("本地片库", fontSize = 28.sp, fontWeight = FontWeight.Medium) }
        item { Text("选择设备上的视频，或打开一个播放链接。", color = Muted, fontSize = 13.sp) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = pick, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Add, null); Text("添加视频") }
            OutlinedButton(onClick = url, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Link, null); Text("播放链接") }
        } }
        item { HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 12.dp)) }
        if (recents.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Movie, null, Modifier.size(56.dp), tint = Muted)
            Text("这里还没有视频", fontSize = 19.sp)
            Text("支持 MP4、MKV 等常见格式", color = Muted, fontSize = 12.sp)
        } }
        items(recents, key = { it.uri }) { item -> RecentRow(item, { play(item) }, { forget(item.uri) }) }
    }
}

@Composable private fun RecentRow(item: RecentMedia, play: () -> Unit, remove: (() -> Unit)?) {
    Surface(onClick = play, shape = RoundedCornerShape(16.dp), color = Panel) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Mint.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Mint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(if (item.position >= 1) "继续观看 · ${formatTime(item.position)}" else "本地视频", color = Muted, fontSize = 11.sp)
            }
            if (remove != null) IconButton(onClick = remove) { Icon(Icons.Rounded.Close, "移除记录", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable private fun Library(items: List<LibraryItem>, title: String, search: String, busy: Boolean, more: Boolean, hasParent: Boolean,
    session: EmbySession, model: MainViewModel, open: (LibraryItem) -> Unit) {
    var query by remember(search) { mutableStateOf(search) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasParent) IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一级") }
            Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { model.loadLibrary() }) { Icon(Icons.Rounded.Refresh, "刷新片库") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp), placeholder = { Text("搜索你的媒体库") },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
            trailingIcon = { IconButton(onClick = { model.search(query) }) { Icon(Icons.Rounded.Search, "搜索") } })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint)
        if (!busy && items.isEmpty()) Text("没有找到媒体内容", Modifier.padding(vertical = 32.dp), color = Muted)
        LazyVerticalGrid(columns = GridCells.Adaptive(132.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(if (item.folder) Icons.Rounded.Folder else Icons.Rounded.Movie, null, Modifier.size(40.dp), tint = Muted)
                        if (item.imageTag.isNotEmpty()) AsyncImage(ImageRequest.Builder(LocalContext.current)
                            .data(model.api.imageUrl(session, item)).addHeader("X-Emby-Token", session.token).build(), item.title,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (item.positionTicks > 0) Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = Ink.copy(alpha = .85f), shape = RoundedCornerShape(6.dp)) {
                            Text("继续观看", Modifier.padding(5.dp), color = Mint, fontSize = 10.sp)
                        }
                    }
                    Text(item.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.year.ifBlank { if (item.folder) "媒体目录" else "视频" }, fontSize = 11.sp, color = Muted)
                }
            }
            if (more) item { TextButton(onClick = { model.loadLibrary(true) }, enabled = !busy) { Text("加载更多") } }
        }
    }
}

@Composable private fun Settings(software: Boolean, setSoftware: (Boolean) -> Unit, session: EmbySession?, connect: () -> Unit, logout: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Text("播放，随你设定", fontSize = 26.sp, fontWeight = FontWeight.Medium) }
        item { SettingCard("播放方式") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("强制 CPU 软件解码")
                    Text("默认关闭，优先尝试系统解码底层视频，杜比色彩映射仍由播放器完成。色彩异常时可开启 CPU 软解；高分辨率软解可能卡顿。修改后需重新打开视频。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Switch(software, setSoftware)
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 18.dp))
            Text("画面输出", fontSize = 14.sp)
            Text("统一映射至 SDR / BT.709，适合没有杜比视界能力的屏幕。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 7.dp))
        } }
        item { SettingCard("Emby 服务器") {
            Text(session?.userName ?: "尚未连接")
            Text(session?.baseUrl ?: "连接后即可浏览和播放私人媒体库", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = if (session == null) connect else logout, contentPadding = PaddingValues(top = 14.dp)) {
                Text(if (session == null) "添加服务器" else "退出当前账号")
            }
        } }
        item { SettingCard("预览版能力") {
            Text("杜比视界的软件映射路径已接入，需通过真实片源与设备验收。Profile 7 FEL 完整重建尚未验证。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("音频输出为普通双声道 PCM，尚未实现 Atmos 对象解码与空间渲染。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
        } }
        item { Text("ONEFRAME  /  ${BuildConfig.VERSION_NAME}\n基于 libmpv · FFmpeg · libplacebo · libass", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 20.sp) }
    }
}

@Composable private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, color = Mint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 18.dp)); content()
    } }
}

@Composable private fun LoginDialog(busy: Boolean, error: String?, dismiss: () -> Unit, login: (String, String, String) -> Unit) {
    var address by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var visible by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("连接 Emby") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
