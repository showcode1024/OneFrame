package io.github.showcode1024.oneframe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.showcode1024.oneframe.HomeState
import io.github.showcode1024.oneframe.MainViewModel
import io.github.showcode1024.oneframe.data.EmbySession
import io.github.showcode1024.oneframe.data.MediaVersion
import io.github.showcode1024.oneframe.data.PlaybackPolicy
import java.util.Locale

private val DetailOrange = Color(0xFFFF9200)
private val DetailBackground = Color(0xFF101216)
private val DetailSecondary = Color(0xFFB5B7BE)
private val DetailSeparator = Color(0xFF32343A)

/** A picture-first detail page. Every label and image comes from the selected Emby item. */
@Composable
fun CinematicDetailScreen(state: HomeState, model: MainViewModel, play: () -> Unit) {
    val item = state.detail ?: return
    val session = state.session ?: return
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val inset = if (wide) 42.dp else 20.dp
    val hasBackdrop = item.backdropTag.isNotBlank()
    val hasPoster = item.imageTag.isNotBlank()
    val selectedVersion = state.versions.firstOrNull { it.id == state.selectedVersionId }
    val metadata = buildList {
        if (item.rating.isNotBlank()) add("★ ${item.rating}")
        if (item.runtimeTicks > 0) {
            val minutes = item.runtimeTicks / PlaybackPolicy.TICKS_PER_SECOND / 60
            if (minutes > 0) add(if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟")
        }
        if (item.year.isNotBlank()) add(item.year)
        selectedVersion?.let { version ->
            if (version.height > 0) add(if (version.height >= 2160 || version.width >= 3840) "4K" else "${version.height}p")
        }
    }

    Box(Modifier.fillMaxSize().background(DetailBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(if (hasBackdrop || hasPoster) if (wide) 420.dp else 315.dp else if (wide) 225.dp else 190.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF35383E), Color(0xFF16181D), DetailBackground)))) {
                    if (hasBackdrop) DetailImage(model.api.backdropUrl(session, item), session, item.title, Modifier.fillMaxSize())
                    else if (hasPoster) DetailImage(model.api.imageUrl(session, item), session, item.title, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .80f)))))
                    if (wide) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .68f), Color.Transparent))))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = inset, end = inset, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, color = Color.White, fontSize = if (wide) 37.sp else 28.sp, lineHeight = if (wide) 43.sp else 33.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (metadata.isNotEmpty()) Text(metadata.joinToString("   ·   "), color = Color.White.copy(alpha = .78f),
                            fontSize = if (wide) 14.sp else 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.genres.isNotEmpty()) Text(item.genres.take(4).joinToString(" · "), color = Color.White.copy(alpha = .72f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                Column(Modifier.then(if (wide) Modifier.widthIn(max = 800.dp) else Modifier).fillMaxWidth()
                    .padding(horizontal = inset, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.then(if (wide) Modifier.widthIn(max = 480.dp) else Modifier).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = play, enabled = !state.loading && !state.versionsLoading && state.selectedVersionId != null,
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DetailOrange, contentColor = Color.White,
                                disabledContainerColor = DetailOrange.copy(alpha = .30f), disabledContentColor = Color.White.copy(alpha = .7f))) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(23.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (state.loading) "正在准备…" else if (item.positionTicks > 0) "继续播放" else "播放", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        Surface(onClick = { model.toggleFavorite(item) }, enabled = !state.favoriteBusy, shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF282A30), modifier = Modifier.size(52.dp).semantics {
                                contentDescription = if (state.favoriteBusy) "正在更新收藏" else if (item.favorite) "取消收藏" else "收藏"
                            }) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.favoriteBusy) CircularProgressIndicator(Modifier.size(19.dp), color = DetailOrange, strokeWidth = 2.dp)
                                else Icon(if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                                    tint = if (item.favorite) DetailOrange else Color.White, modifier = Modifier.size(23.dp))
                            }
                        }
                    }
                    if (item.positionTicks > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (item.runtimeTicks > 0) LinearProgressIndicator(
                                progress = { (item.positionTicks.toFloat() / item.runtimeTicks).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = DetailOrange, trackColor = Color.White.copy(alpha = .18f))
                            val seconds = item.positionTicks / PlaybackPolicy.TICKS_PER_SECOND
                            val time = if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
                                else "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
                            Text("上次观看至 $time", color = DetailSecondary, fontSize = 12.sp)
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    if (item.overview.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("简介", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(item.overview, color = Color(0xFFD3D4D7), fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                    HorizontalDivider(color = DetailSeparator)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("视频版本", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("选择要播放的原始文件", color = DetailSecondary, fontSize = 12.sp)
                        }
                        if (state.versionsLoading) CircularProgressIndicator(Modifier.size(20.dp), color = DetailOrange, strokeWidth = 2.dp)
                        else Text("${state.versions.size} 个版本", color = DetailSecondary, fontSize = 12.sp)
                    }
                    state.versionsError?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), color = DetailSecondary, fontSize = 13.sp)
                            TextButton(onClick = { model.openDetail(item) }) { Text("重试", color = DetailOrange) }
                        }
                    }
                }
            }
            items(state.versions, key = { it.id }) { version ->
                val selected = state.selectedVersionId == version.id
                Surface(onClick = { model.selectVersion(version.id) }, enabled = version.playable,
                    modifier = Modifier.then(if (wide) Modifier.widthIn(max = 800.dp) else Modifier).fillMaxWidth()
                        .padding(horizontal = inset, vertical = 4.dp).semantics { this.selected = selected },
                    shape = RoundedCornerShape(10.dp), color = if (selected) Color(0xFF3D2A17) else Color(0xFF24262B)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(version.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val summary = detailVersionSummary(version)
                            if (summary.isNotBlank()) Text(summary, color = DetailSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                            if (!version.playable) Text("服务器未提供原始文件直放", color = DetailSecondary, fontSize = 12.sp)
                        }
                        if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = DetailOrange, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        IconButton(onClick = model::closeDetail, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 4.dp)
            .background(Color.Black.copy(alpha = .35f), RoundedCornerShape(50))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
        }
    }
}

@Composable
private fun DetailImage(url: String, session: EmbySession, title: String, modifier: Modifier) {
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).addHeader("X-Emby-Token", session.token).build(),
        contentDescription = title, modifier = modifier, contentScale = ContentScale.Crop)
}

private fun detailVersionSummary(version: MediaVersion): String = buildList {
    if (version.height > 0) add(if (version.height >= 2160 || version.width >= 3840) "4K" else "${version.height}p")
    if (version.videoRange.isNotBlank() && !version.videoRange.equals("SDR", true)) add(version.videoRange)
    if (version.videoCodec.isNotBlank()) add(version.videoCodec.uppercase(Locale.ROOT))
    if (version.audioCodec.isNotBlank()) add(version.audioCodec.uppercase(Locale.ROOT))
    if (version.container.isNotBlank()) add(version.container.uppercase(Locale.ROOT))
    if (version.size > 0) add("%.1f GB".format(Locale.ROOT, version.size / 1_073_741_824.0))
}.joinToString(" · ")
