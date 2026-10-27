package io.github.showcode1024.oneframe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.showcode1024.oneframe.update.UpdateViewModel

@Composable fun UpdateDialog(model: UpdateViewModel, install: () -> Unit, openReleases: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    if (!state.dialog) return
    val release = state.release
    AlertDialog(onDismissRequest = model::dismiss, title = { Text(if (release != null) "发现新版本 ${release.version}" else "软件更新") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.checking) { Text("正在检查 GitHub Release…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (release != null) {
                    Text("安装包 %.1f MB".format(release.size / 1_048_576.0), color = Muted, fontSize = 12.sp)
                    if (release.notes.isNotBlank()) Text(release.notes, fontSize = 13.sp, lineHeight = 20.sp)
                    if (state.downloading) {
                        val fraction = (state.downloadedBytes.toFloat() / release.size).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("正在下载 ${"%.0f".format(fraction * 100)}%，请保持应用打开", color = Muted, fontSize = 12.sp)
                    } else if (state.readyFile != null) Text("安装时需要在 Android 系统界面确认。", color = Muted, fontSize = 12.sp)
                }
                state.message?.let { Text(it, color = if (state.readyFile != null) Mint else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) }
                if (!state.checking && (release == null || state.message != null && state.readyFile == null)) {
                    TextButton(onClick = openReleases) { Text("前往 GitHub 发布页") }
                }
            }
        },
        confirmButton = {
            if (release != null) TextButton(onClick = if (state.readyFile != null) install else model::download,
                enabled = !state.downloading && !state.checking) { Text(if (state.readyFile != null) "安装更新" else "下载更新") }
            else if (!state.checking && state.message != "当前已是最新版本") TextButton(onClick = { model.check() }) { Text("重试") }
        },
        dismissButton = { TextButton(onClick = model::dismiss) { Text(if (state.downloading) "收起" else "关闭") } })
}
