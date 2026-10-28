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
        Surface(color = Ink, contentColor = MaterialTheme.colorScheme.onBackground) { DetailScreen(state, model, playEmby) }
        return
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(containerColor = Ink, bottomBar = {
        if (!wide) NavigationBar(containerColor = Color(0xFF10141D), tonalElevation = 0.dp) {
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
        if (wide) NavigationRail(containerColor = Color(0xFF10141D), modifier = Modifier.fillMaxHeight()) {
            listOf("片库" to Icons.Rounded.VideoLibrary, "本地" to Icons.Rounded.FolderOpen, "设置" to Icons.Rounded.Tune)
                .forEachIndexed { index, (name, icon) -> NavigationRailItem(selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icon, name) }, label = { Text(name) },
                    colors = NavigationRailItemDefaults.colors(indicatorColor = Mint.copy(alpha = .12f), selectedIconColor = Mint, selectedTextColor = Mint,
                        unselectedIconColor = Muted, unselectedTextColor = Muted)) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF3EAAFF), Color(0xFF2464E8)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(23.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text("OneFrame", fontSize = 20.sp, letterSpacing = (-.4).sp, fontWeight = FontWeight.Bold)
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
                0 -> if (state.session == null) Welcome(pickFile, { login = true }, { tab = 1 }, state.recents, playRecent)
                else if (state.path.isEmpty() && state.search.isBlank()) EmbyHome(state, model)
                else Library(state.items, state.path.lastOrNull()?.title ?: "我的媒体库", state.search, state.loading, state.canLoadMore, state.path.isNotEmpty(),
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

private fun isFolder(item: LibraryItem) = item.folder || item.type in listOf("Series", "Season", "CollectionFolder", "BoxSet")

@Composable private fun EmbyHome(state: HomeState, model: MainViewModel) {
    val session = state.session ?: return
    var query by remember { mutableStateOf("") }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 32.dp else 20.dp
    val open: (LibraryItem) -> Unit = { if (isFolder(it)) model.openFolder(it) else model.openDetail(it) }
    val featured = (state.resumeItems + state.latestItems).firstOrNull { it.backdropTag.isNotBlank() }
        ?: (state.resumeItems + state.latestItems).firstOrNull()
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            Column(Modifier.padding(horizontal = inset), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("为你精选", fontSize = 27.sp, letterSpacing = (-.5).sp, fontWeight = FontWeight.Bold)
                        Text("你的私人影院", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    IconButton(onClick = { model.loadLibrary(); model.refreshHome() }) { Icon(Icons.Rounded.Refresh, "刷新片库", tint = Muted) }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索电影、剧集", color = Muted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                    trailingIcon = { if (query.isNotBlank()) Row {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索", tint = Muted) }
                        IconButton(onClick = { model.search(query) }) { Icon(Icons.Rounded.ArrowForward, "搜索", tint = Mint) }
                    } },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel,
                        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Mint.copy(alpha = .55f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search(query) }))
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = inset)) }
        if (featured != null) item {
            Box(Modifier.fillMaxWidth().padding(horizontal = inset).height(if (wide) 320.dp else 240.dp)
                .clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF273F63), Color(0xFF121B2C), Ink))).clickable { open(featured) }) {
                Icon(Icons.Rounded.Movie, null, Modifier.align(Alignment.TopEnd).padding(25.dp).size(45.dp), tint = Color.White.copy(alpha = .18f))
                if (featured.backdropTag.isNotBlank()) ServerImage(model.api.backdropUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                else if (featured.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .22f), Color(0xFF080C14).copy(alpha = .96f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Mint))
                        Spacer(Modifier.width(7.dp))
                        Text(if (featured.positionTicks > 0) "继续观看" else "最近添加", color = Color(0xFFDDEBFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(featured.title, fontSize = if (wide) 30.sp else 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val info = listOf(featured.year, featured.genres.take(2).joinToString(" / ")).filter { it.isNotBlank() }.joinToString(" · ")
                    if (info.isNotBlank()) Text(info, color = Color(0xFFCCD4E1), fontSize = 12.sp, maxLines = 1)
                    Surface(onClick = { open(featured) }, color = Color.White, contentColor = Color(0xFF101722), shape = RoundedCornerShape(9.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isFolder(featured)) Icons.Rounded.Info else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text(if (isFolder(featured)) "查看详情" else if (featured.positionTicks > 0) "继续观看" else "查看影片", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (state.resumeItems.isNotEmpty()) item { PosterRow("继续观看", "从上次离开的地方继续", state.resumeItems, session, model, open) }
        if (state.favorites.isNotEmpty()) item { PosterRow("我的收藏", if (state.favorites.size >= 100) "最多显示前 100 项" else "你喜欢的影片", state.favorites, session, model, open) }
        state.favoritesError?.let { message -> item {
            Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                TextButton(onClick = model::refreshHome) { Text("重试") }
            }
        } }
        if (state.latestItems.isNotEmpty()) item { PosterRow("最近添加", "新入库的内容", state.latestItems, session, model, open) }
        item {
            Column(Modifier.padding(horizontal = inset)) {
                Text("媒体库", fontSize = 20.sp, letterSpacing = (-.2).sp, fontWeight = FontWeight.Bold)
                Text("浏览你的收藏", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (!state.loading && state.items.isEmpty()) item { Text("服务器中没有可显示的媒体库", Modifier.padding(horizontal = inset), color = Muted) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.id }) { item ->
                    Box(Modifier.width(if (wide) 200.dp else 164.dp).height(105.dp).clip(RoundedCornerShape(14.dp)).background(Panel).clickable { open(item) }) {
                        if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x330C1420), Color(0xEE0B1018)))))
                        Icon(Icons.Rounded.VideoLibrary, null, Modifier.align(Alignment.TopStart).padding(14.dp).size(21.dp), tint = Color.White.copy(alpha = .88f))
                        Text(item.title, Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable private fun ServerImage(url: String, session: EmbySession, description: String, modifier: Modifier) {
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).addHeader("X-Emby-Token", session.token).build(),
        description, modifier, contentScale = ContentScale.Crop)
}

@Composable private fun PosterRow(title: String, subtitle: String, values: List<LibraryItem>, session: EmbySession, model: MainViewModel, open: (LibraryItem) -> Unit) {
    val inset = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(Modifier.padding(horizontal = inset)) {
            Text(title, fontSize = 20.sp, letterSpacing = (-.2).sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(values, key = { it.id }) { item ->
                Column(Modifier.width(132.dp).clip(RoundedCornerShape(12.dp)).clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(186.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Movie, null, Modifier.size(36.dp), tint = Muted.copy(alpha = .5f))
                        if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        if (item.positionTicks > 0 && item.runtimeTicks > 0) LinearProgressIndicator(
                            progress = { (item.positionTicks.toFloat() / item.runtimeTicks).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = Mint, trackColor = Color.White.copy(alpha = .25f))
                    }
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.year.ifBlank { if (isFolder(item)) "媒体目录" else "影片" }, color = Muted, fontSize = 11.sp)
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
    val inset = if (wide) 48.dp else 20.dp
    val hasArt = item.backdropTag.isNotBlank() || item.imageTag.isNotBlank()
    LazyColumn(Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (!hasArt) 105.dp else if (wide) 360.dp else 255.dp).background(Panel)) {
                if (item.backdropTag.isNotBlank()) ServerImage(model.api.backdropUrl(session, item), session, item.title, Modifier.fillMaxSize())
                else if (item.imageTag.isNotBlank()) ServerImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .42f), Color.Transparent, Ink))))
                IconButton(onClick = model::closeDetail, modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 6.dp)
                    .background(Color(0xB00B111B), RoundedCornerShape(50))) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = inset), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = if (wide) 34.sp else 28.sp, letterSpacing = (-.4).sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                        val metadata = listOf(item.year, item.runtimeTicks.takeIf { it > 0 }?.let { "${it / PlaybackPolicy.TICKS_PER_SECOND / 60} 分钟" }.orEmpty(),
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }.orEmpty(), item.genres.take(3).joinToString(" / ")).filter { it.isNotBlank() }.joinToString("  ·  ")
                        if (metadata.isNotBlank()) Text(metadata, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = play, enabled = !state.loading && !state.versionsLoading && state.selectedVersionId != null,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF07101C))) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(23.dp)); Spacer(Modifier.width(6.dp));
                        Text(if (state.loading) "正在准备…" else if (item.positionTicks > 0) "继续播放" else "播放", fontWeight = FontWeight.Bold)
                    }
                    Surface(onClick = { model.toggleFavorite(item) }, enabled = !state.favoriteBusy, shape = RoundedCornerShape(12.dp), color = Panel,
                        modifier = Modifier.size(52.dp).semantics { contentDescription = if (state.favoriteBusy) "正在更新收藏" else if (item.favorite) "取消收藏" else "收藏" }) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.favoriteBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                null, tint = if (item.favorite) Color(0xFFFF6681) else Color.White, modifier = Modifier.size(23.dp))
                        }
                    }
                }
                if (item.positionTicks > 0) Text("上次观看至 ${formatTime(item.positionTicks.toDouble() / PlaybackPolicy.TICKS_PER_SECOND)}", color = Muted, fontSize = 12.sp)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (item.overview.isNotBlank()) {
                    Text("简介", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                    Text(item.overview, color = Color(0xFFC5CFDD), fontSize = 14.sp, lineHeight = 23.sp)
                }
                HorizontalDivider(color = Line, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("视频版本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("选择要播放的原始文件", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state.versionsLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("${state.versions.size} 个版本", color = Muted, fontSize = 12.sp)
                }
                state.versionsError?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                        TextButton(onClick = { model.openDetail(item) }) { Text("重试") }
                    }
                }
            }
        }
        items(state.versions, key = { it.id }) { version ->
            val selected = state.selectedVersionId == version.id
            Surface(onClick = { model.selectVersion(version.id) }, enabled = version.playable,
                modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 5.dp).semantics { this.selected = selected },
                shape = RoundedCornerShape(14.dp), color = if (selected) Color(0xFF182B43) else Panel) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) Mint.copy(alpha = .18f) else Color(0xFF252E3C)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Movie, null, Modifier.size(19.dp), tint = if (selected) Mint else Muted)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(version.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val summary = versionSummary(version)
                        if (summary.isNotBlank()) Text(summary, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                        if (!version.playable) Text("服务器未提供原始文件直放", color = Muted, fontSize = 12.sp)
                    }
                    if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = Mint, modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable private fun Welcome(pickFile: () -> Unit, connect: () -> Unit, local: () -> Unit, recents: List<RecentMedia>, play: (RecentMedia) -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 32.dp else 20.dp
    LazyColumn(contentPadding = PaddingValues(horizontal = inset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("欢迎来到 OneFrame", color = Muted, fontSize = 13.sp)
                Text("属于你的私人影院", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(if (wide) 290.dp else 265.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF254C88), Color(0xFF182D51), Color(0xFF141B2B))))) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width * .79f, size.height * .38f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF478CFC).copy(alpha = .30f), Color.Transparent), center, size.height * .7f),
                        size.height * .7f, center)
                    drawCircle(Color.White.copy(alpha = .05f), size.height * .31f, center)
                    drawCircle(Color.White.copy(alpha = .10f), size.height * .31f, center, style = Stroke(1.dp.toPx()))
                    val icon = Path().apply { moveTo(center.x - size.height * .07f, center.y - size.height * .10f); lineTo(center.x + size.height * .10f, center.y); lineTo(center.x - size.height * .07f, center.y + size.height * .10f); close() }
                    drawPath(icon, Color.White.copy(alpha = .82f))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR MOVIES, YOUR WAY", color = Color(0xFFB4D3FF), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text("好故事，\n随时开始。", fontSize = 29.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = connect, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF12223A))) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(19.dp)); Spacer(Modifier.width(5.dp)); Text("连接 Emby", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(onClick = pickFile, shape = RoundedCornerShape(16.dp), color = Panel) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Mint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = Mint, modifier = Modifier.size(23.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("打开本地视频", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("从设备中选择影片", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "打开本地视频", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近打开", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = local, contentPadding = PaddingValues(0.dp)) { Text("查看全部", fontSize = 12.sp) }
            }
        }
        if (recents.isEmpty()) item { Text("打开视频后，可以从这里继续观看。", color = Muted, fontSize = 13.sp) }
        items(recents.take(3), key = { it.uri }) { RecentRow(it, { play(it) }, null) }
    }
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

@Composable private fun Library(items: List<LibraryItem>, title: String, search: String, busy: Boolean, more: Boolean, hasParent: Boolean,
    session: EmbySession, model: MainViewModel, open: (LibraryItem) -> Unit) {
    var query by remember(search) { mutableStateOf(search) }
    val inset = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
    Column(Modifier.fillMaxSize().padding(horizontal = inset)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasParent || search.isNotBlank()) IconButton(onClick = { if (search.isNotBlank()) model.search("") else model.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一级") }
            Text(title, Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { model.loadLibrary() }) { Icon(Icons.Rounded.Refresh, "刷新片库") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp), placeholder = { Text("搜索你的媒体库", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel,
                unfocusedBorderColor = Color.Transparent, focusedBorderColor = Mint.copy(alpha = .55f)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
            trailingIcon = { IconButton(onClick = { model.search(query) }) { Icon(Icons.Rounded.Search, "搜索") } })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint)
        if (!busy && items.isEmpty()) Text("没有找到媒体内容", Modifier.padding(vertical = 32.dp), color = Muted)
        LazyVerticalGrid(columns = GridCells.Adaptive(132.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                        Icon(if (item.folder) Icons.Rounded.Folder else Icons.Rounded.Movie, null, Modifier.size(40.dp), tint = Muted)
                        if (item.imageTag.isNotEmpty()) AsyncImage(ImageRequest.Builder(LocalContext.current)
                            .data(model.api.imageUrl(session, item)).addHeader("X-Emby-Token", session.token).build(), item.title,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (item.positionTicks > 0) Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = Ink.copy(alpha = .85f), shape = RoundedCornerShape(6.dp)) {
                            Text("继续观看", Modifier.padding(5.dp), color = Mint, fontSize = 10.sp)
                        }
                    }
                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.year.ifBlank { if (item.folder) "媒体目录" else "视频" }, fontSize = 11.sp, color = Muted)
                }
            }
            if (more) item { TextButton(onClick = { model.loadLibrary(true) }, enabled = !busy) { Text("加载更多") } }
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
