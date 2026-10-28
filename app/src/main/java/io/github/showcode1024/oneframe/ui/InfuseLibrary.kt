package io.github.showcode1024.oneframe.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.showcode1024.oneframe.HomeState
import io.github.showcode1024.oneframe.MainViewModel
import io.github.showcode1024.oneframe.data.EmbySession
import io.github.showcode1024.oneframe.data.LibraryItem
import io.github.showcode1024.oneframe.data.RecentMedia

private fun LibraryItem.isDirectory() = folder || type in listOf("Series", "Season", "CollectionFolder", "BoxSet")
private val SecondaryText = Color(0xFFAAAEB7)
private val TileSurface = Color(0xFF24262C)

@Composable private fun Artwork(url: String, session: EmbySession, description: String, modifier: Modifier = Modifier) {
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).addHeader("X-Emby-Token", session.token).build(),
        description, modifier, contentScale = ContentScale.Crop)
}

@Composable internal fun InfuseEmbyHome(state: HomeState, model: MainViewModel) {
    val session = state.session ?: return
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 36.dp else 20.dp
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    val open: (LibraryItem) -> Unit = { if (it.isDirectory()) model.openFolder(it) else model.openDetail(it) }
    val candidates = state.resumeItems + state.latestItems
    val featured = candidates.firstOrNull { it.backdropTag.isNotBlank() }
        ?: candidates.firstOrNull { it.imageTag.isNotBlank() }
        ?: candidates.firstOrNull()

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
                Text("影片", Modifier.weight(1f), fontSize = 30.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        if (searchVisible) "关闭搜索" else "搜索", tint = SecondaryText)
                }
                IconButton(onClick = { model.loadLibrary(); model.refreshHome() }) {
                    Icon(Icons.Rounded.Refresh, "刷新片库", tint = SecondaryText)
                }
            }
        }
        if (searchVisible) item {
            SearchField(query, { query = it }, { model.search(query) }, Modifier.padding(horizontal = inset), "搜索电影、剧集")
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = inset), color = Mint) }
        if (featured != null) item {
            Box(Modifier.fillMaxWidth().padding(horizontal = inset).height(if (wide) 360.dp else 220.dp)
                .clip(RoundedCornerShape(if (wide) 16.dp else 12.dp))
                .background(Color(0xFF1D2028)).clickable { open(featured) }) {
                Icon(Icons.Rounded.Movie, null, Modifier.align(Alignment.Center).size(60.dp), tint = Color.White.copy(alpha = .16f))
                if (featured.backdropTag.isNotBlank()) Artwork(model.api.backdropUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                else if (featured.imageTag.isNotBlank()) Artwork(model.api.imageUrl(session, featured), session, featured.title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .06f), Color.Black.copy(alpha = .18f), Ink.copy(alpha = .98f)))))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (featured.positionTicks > 0) "继续观看" else "最近添加", color = Color.White.copy(alpha = .8f),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp)
                    Text(featured.title, fontSize = if (wide) 36.sp else 29.sp, lineHeight = if (wide) 40.sp else 34.sp,
                        letterSpacing = (-.6).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOf(featured.year, featured.genres.take(2).joinToString(" · "), featured.rating).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = .75f), fontSize = 12.sp, maxLines = 1)
                    if (featured.overview.isNotBlank() && wide) Text(featured.overview, color = Color.White.copy(alpha = .72f),
                        fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 550.dp))
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text(if (featured.isDirectory()) "查看详情" else if (featured.positionTicks > 0) "继续观看" else "查看影片",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (state.resumeItems.isNotEmpty()) item { ArtworkRow("继续观看", state.resumeItems, session, model, open) }
        if (state.favorites.isNotEmpty()) item { ArtworkRow("我的收藏", state.favorites, session, model, open,
            if (state.favorites.size >= 100) "最多显示前 100 项" else "") }
        state.favoritesError?.let { message -> item {
            Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = SecondaryText, fontSize = 12.sp)
                TextButton(onClick = model::refreshHome) { Text("重试") }
            }
        } }
        if (state.latestItems.isNotEmpty()) item { ArtworkRow("最近添加", state.latestItems, session, model, open) }
        item {
            Text("媒体库", Modifier.padding(horizontal = inset), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp)
        }
        if (!state.loading && state.items.isEmpty()) item { Text("服务器中没有可显示的媒体库", Modifier.padding(horizontal = inset), color = SecondaryText) }
        if (state.items.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.id }) { item ->
                    Box(Modifier.width(if (wide) 220.dp else 170.dp).height(if (wide) 126.dp else 105.dp)
                        .clip(RoundedCornerShape(9.dp)).background(TileSurface).clickable { open(item) }) {
                        if (item.backdropTag.isNotBlank()) Artwork(model.api.backdropUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        else if (item.imageTag.isNotBlank()) Artwork(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
                        Text(item.title, Modifier.align(Alignment.BottomStart).padding(12.dp), fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable private fun ArtworkRow(title: String, values: List<LibraryItem>, session: EmbySession, model: MainViewModel,
    open: (LibraryItem) -> Unit, note: String = "") {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 36.dp else 20.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp)
            if (note.isNotEmpty()) Text(note, color = SecondaryText, fontSize = 11.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(values, key = { it.id }) { item ->
                Column(Modifier.width(if (wide) 156.dp else 124.dp).clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Poster(item, session, model, Modifier.fillMaxWidth().aspectRatio(.68f))
                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.year.isNotBlank()) Text(item.year, color = SecondaryText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable private fun Poster(item: LibraryItem, session: EmbySession, model: MainViewModel, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(TileSurface), contentAlignment = Alignment.Center) {
        Icon(if (item.isDirectory()) Icons.Rounded.Folder else Icons.Rounded.Movie, null, Modifier.size(32.dp), tint = SecondaryText.copy(alpha = .55f))
        if (item.imageTag.isNotBlank()) Artwork(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
        if (item.positionTicks > 0 && item.runtimeTicks > 0) LinearProgressIndicator(
            progress = { (item.positionTicks.toFloat() / item.runtimeTicks).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Mint, trackColor = Color.Black.copy(alpha = .55f))
    }
}

@Composable private fun SearchField(query: String, change: (String) -> Unit, submit: () -> Unit, modifier: Modifier = Modifier, hint: String) {
    val keyboard = LocalSoftwareKeyboardController.current
    val search = { keyboard?.hide(); submit() }
    OutlinedTextField(query, change, modifier.fillMaxWidth(), placeholder = { Text(hint, fontSize = 14.sp, color = SecondaryText) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = search) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "搜索", tint = Mint) } },
        singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = TileSurface, focusedContainerColor = TileSurface,
            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
}

@Composable internal fun InfuseLibrary(items: List<LibraryItem>, title: String, search: String, busy: Boolean, more: Boolean, hasParent: Boolean,
    session: EmbySession, model: MainViewModel, open: (LibraryItem) -> Unit) {
    var query by remember(search) { mutableStateOf(search) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 36.dp else 20.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
            if (hasParent || search.isNotBlank()) IconButton(onClick = { if (search.isNotBlank()) model.search("") else model.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一级")
            }
            Text(title, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { model.loadLibrary() }) { Icon(Icons.Rounded.Refresh, "刷新片库", tint = SecondaryText) }
        }
        SearchField(query, { query = it }, { model.search(query) }, Modifier.padding(horizontal = inset, vertical = 12.dp), "搜索你的媒体库")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = inset), color = Mint)
        if (!busy && items.isEmpty()) Text("没有找到媒体内容", Modifier.padding(horizontal = inset, vertical = 32.dp), color = SecondaryText)
        LazyVerticalGrid(columns = GridCells.Adaptive(if (wide) 150.dp else 104.dp),
            contentPadding = PaddingValues(start = inset, end = inset, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.clickable { open(item) }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Poster(item, session, model, Modifier.fillMaxWidth().aspectRatio(.68f))
                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.year.isNotBlank()) Text(item.year, fontSize = 11.sp, color = SecondaryText)
                }
            }
            if (more) item { TextButton(onClick = { model.loadLibrary(true) }, enabled = !busy) { Text("加载更多") } }
        }
    }
}

@Composable internal fun InfuseWelcome(pickFile: () -> Unit, connect: () -> Unit, local: () -> Unit,
    recents: List<RecentMedia>, play: (RecentMedia) -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 36.dp else 20.dp
    LazyColumn(contentPadding = PaddingValues(start = inset, end = inset, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = if (wide) 48.dp else 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("欢迎来到 OneFrame", color = SecondaryText, fontSize = 13.sp)
                Text("属于你的私人影院", fontSize = if (wide) 38.sp else 30.sp, lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
                Text("连接媒体服务器，或打开设备上的视频。", color = SecondaryText, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WelcomeAction(Icons.Rounded.CloudQueue, "连接 Emby", "浏览你的电影和剧集", connect)
                WelcomeAction(Icons.Rounded.FolderOpen, "打开本地视频", "从设备中选择影片", pickFile)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近打开", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = local, contentPadding = PaddingValues(0.dp)) { Text("查看全部", fontSize = 12.sp) }
            }
        }
        if (recents.isEmpty()) item { Text("打开视频后，可以从这里继续观看。", color = SecondaryText, fontSize = 13.sp) }
        items(recents.take(3), key = { it.uri }) { item ->
            WelcomeAction(Icons.Rounded.PlayCircle, item.title, if (item.position >= 1) "继续观看" else "本地视频") { play(item) }
        }
    }
}

@Composable private fun WelcomeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, caption: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileSurface).clickable(onClick = click)
        .padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(25.dp), tint = Mint)
        Column(Modifier.weight(1f).padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, color = SecondaryText, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = SecondaryText)
    }
}
