package io.github.showcode1024.oneframe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import io.github.showcode1024.oneframe.HomeState
import io.github.showcode1024.oneframe.MainViewModel
import io.github.showcode1024.oneframe.BuildConfig
import io.github.showcode1024.oneframe.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun HomeScreen(
    model: MainViewModel, pickFile: () -> Unit, playRecent: (RecentMedia) -> Unit,
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
        Surface(color = Ink, contentColor = MaterialTheme.colorScheme.onBackground) { DetailScreen(state, model, playEmby) }
        return
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(containerColor = Ink, bottomBar = {
        if (!wide) NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationBarItem(
                    selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Mint.copy(alpha = .12f)),
                ) }
        }
    }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        if (wide) NavigationRail(containerColor = Ink, modifier = Modifier.fillMaxHeight()) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationRailItem(selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationRailItemDefaults.colors(indicatorColor = Mint.copy(alpha = .12f))) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(32.dp), tint = Mint)
                Text("OneFrame", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Surface(onClick = { tab = 2 }, shape = RoundedCornerShape(50), color = Panel) {
                    Text(state.session?.userName ?: "连接服务器", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Mint, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                else if (state.path.isEmpty() && state.search.isBlank()) EmbyHome(state, model)
                else Library(state.items, state.path.lastOrNull()?.title ?: "我的媒体库", state.search, state.loading, state.canLoadMore, state.path.isNotEmpty(),
                    state.session!!, model, { if (it.folder || it.type in listOf("Series", "Season", "CollectionFolder", "BoxSet")) model.openFolder(it) else model.openDetail(it) })
                1 -> LocalLibrary(state.recents, pickFile, { urlDialog = true }, playRecent, model::forget)
                2 -> Settings(state.softwareDecode, model::softwareDecode, state.session, state.servers, { login = true }, model::switchServer, model::removeServer)
            }
        }
        }
    }
    if (login) LoginDialog(state.loading, state.error, { login = false }, { address, user, password ->
        model.login(address, user, password) { login = false; tab = 0 }
    })
    if (urlDialog) UrlDialog({ urlDialog = false }) { urlDialog = false; playUrl(it) }
}

private fun isFolder(item: LibraryItem) = item.folder || item.type in listOf("Series", "Season", "CollectionFolder", "BoxSet")

@Composable private fun EmbyHome(state: HomeState, model: MainViewModel) {
    val session = state.session ?: return
    var query by remember { mutableStateOf("") }
    val open: (LibraryItem) -> Unit = { if (isFolder(it)) model.openFolder(it) else model.openDetail(it) }
    val featured = (state.resumeItems + state.latestItems).firstOrNull { it.backdropTag.isNotBlank() }
        ?: (state.resumeItems + state.latestItems).firstOrNull()
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("欢迎回来，${session.userName}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("你的私人影院", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 18.dp),
                    placeholder = { Text("搜索电影、剧集") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
                    trailingIcon = { IconButton(onClick = { model.search(query) }) { Icon(Icons.Rounded.Search, "搜索") } })
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
        if (featured != null) item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(if (LocalConfiguration.current.screenWidthDp >= 600) 300.dp else 220.dp)
                .clip(RoundedCornerShape(20.dp)).background(Panel).clickable { open(featured) }) {
                if (featured.backdropTag.isNotBlank()) ServerImage(model.api.backdropUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                else if (featured.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .96f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(if (featured.positionTicks > 0) "继续观看" else "最近添加", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(featured.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (featured.year.isNotBlank()) Text(featured.year, color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
        if (state.resumeItems.isNotEmpty()) item { PosterRow("继续观看", state.resumeItems, session, model, open) }
        if (state.latestItems.isNotEmpty()) item { PosterRow("最近添加", state.latestItems, session, model, open) }
        item { Text("媒体库", Modifier.padding(horizontal = 24.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (!state.loading && state.items.isEmpty()) item { Text("服务器中没有可显示的媒体库", Modifier.padding(horizontal = 24.dp), color = Muted) }
        item { LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items, key = { it.id }) { item ->
                Surface(onClick = { open(item) }, shape = RoundedCornerShape(16.dp), color = Panel, modifier = Modifier.width(160.dp).height(100.dp)) {
                    Box { if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .93f)))))
                        Text(item.title, Modifier.align(Alignment.BottomStart).padding(12.dp), fontWeight = FontWeight.SemiBold, maxLines = 2) }
                }
            }
        } }
    }
}

@Composable private fun ServerImage(url: String, session: EmbySession, description: String, modifier: Modifier) {
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).addHeader("X-Emby-Token", session.token).build(),
        description, modifier, contentScale = ContentScale.Crop)
}

@Composable private fun PosterRow(title: String, values: List<LibraryItem>, session: EmbySession, model: MainViewModel, open: (LibraryItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 24.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(values, key = { it.id }) { item ->
                Column(Modifier.width(128.dp).clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(182.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Movie, null, tint = Muted)
                        if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        if (item.positionTicks > 0 && item.runtimeTicks > 0) LinearProgressIndicator(
                            progress = { (item.positionTicks.toFloat() / item.runtimeTicks).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Mint, trackColor = Color.White.copy(alpha = .25f))
                    }
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (item.year.isNotBlank()) Text(item.year, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun versionSummary(version: MediaVersion): String = buildList {
    if (version.height > 0) add(if (version.height >= 2160 || version.width >= 3840) "4K" else "${version.height}p")
    if (version.videoRange.isNotBlank() && !version.videoRange.equals("SDR", true)) add(version.videoRange)
    if (version.videoCodec.isNotBlank()) add(version.videoCodec.uppercase())
    if (version.audioCodec.isNotBlank()) add(version.audioCodec.uppercase())
    if (version.container.isNotBlank()) add(version.container.uppercase())
    if (version.size > 0) add("%.1f GB".format(version.size / 1_073_741_824.0))
}.joinToString(" · ")

@Composable private fun DetailScreen(state: HomeState, model: MainViewModel, play: () -> Unit) {
    val item = state.detail ?: return
    val session = state.session ?: return
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LazyColumn(Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (item.backdropTag.isBlank() && item.imageTag.isBlank()) 110.dp else if (wide) 380.dp else 270.dp).background(Panel)) {
                if (item.backdropTag.isNotBlank()) ServerImage(model.api.backdropUrl(session, item), session, item.title, Modifier.fillMaxSize())
                else if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, Ink))))
                IconButton(onClick = model::closeDetail, modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp).background(Ink.copy(alpha = .65f), RoundedCornerShape(50))) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (wide) 48.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(item.title, fontSize = if (wide) 34.sp else 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                val metadata = listOf(item.year, item.runtimeTicks.takeIf { it > 0 }?.let { "${it / PlaybackPolicy.TICKS_PER_SECOND / 60} 分钟" }.orEmpty(),
                    item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }.orEmpty(), item.genres.take(3).joinToString(" / ")).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (metadata.isNotBlank()) Text(metadata, color = Muted, fontSize = 13.sp)
                Button(onClick = play, enabled = !state.loading && !state.versionsLoading && state.selectedVersionId != null,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (state.loading) "正在准备…" else if (item.positionTicks > 0) "继续播放" else "播放", fontWeight = FontWeight.Bold)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (item.overview.isNotBlank()) Text(item.overview, color = Color(0xFFD0DAE7), fontSize = 14.sp, lineHeight = 22.sp)
                HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("视频版本", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (state.versionsLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("${state.versions.size} 个版本", color = Muted, fontSize = 12.sp)
                }
                Text("选择要播放的原始文件", color = Muted, fontSize = 12.sp)
                state.versionsError?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                        TextButton(onClick = { model.openDetail(item) }) { Text("重试") }
                    }
                }
            }
        }
        items(state.versions, key = { it.id }) { version ->
            Surface(onClick = { model.selectVersion(version.id) }, enabled = version.playable,
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (wide) 48.dp else 24.dp, vertical = 5.dp),
                shape = RoundedCornerShape(14.dp), color = if (state.selectedVersionId == version.id) Mint.copy(alpha = .15f) else Panel) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.selectedVersionId == version.id, onClick = null, enabled = version.playable)
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(version.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val summary = versionSummary(version)
                        if (summary.isNotBlank()) Text(summary, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        if (!version.playable) Text("服务器未提供原始文件直放", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun Welcome(pickFile: () -> Unit, connect: () -> Unit, local: () -> Unit, recents: List<RecentMedia>, play: (RecentMedia) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text("你的电影，自在观看。", color = Muted, fontSize = 13.sp) }
        item { Box(Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF203A60), Color(0xFF17273D), Color(0xFF111C2C))))) {
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
            if (hasParent || search.isNotBlank()) IconButton(onClick = { if (search.isNotBlank()) model.search("") else model.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一级") }
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

@Composable private fun Settings(software: Boolean, setSoftware: (Boolean) -> Unit, session: EmbySession?, servers: List<EmbySession>,
    connect: () -> Unit, switch: (EmbySession) -> Unit, remove: (EmbySession) -> Unit) {
    var removing by remember { mutableStateOf<EmbySession?>(null) }
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
