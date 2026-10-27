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
import io.github.showcode1024.oneframe.update.UpdateState

@Composable fun UpdateDialog(model: UpdateViewModel, download: () -> Unit, openReleases: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    UpdateDialogContent(state, dismiss = model::dismiss, retry = { model.check() }, download = download, openReleases = openReleases)
}

@Composable internal fun UpdateDialogContent(state: UpdateState, dismiss: () -> Unit, retry: () -> Unit, download: () -> Unit, openReleases: () -> Unit) {
    if (!state.dialog) return
    val release = state.release
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (release != null) "发现新版本 ${release.version}" else "软件更新") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.checking) { Text("正在检查 GitHub Release…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (release != null) {
                    Text("安装包 %.1f MB".format(release.size / 1_048_576.0), color = Muted, fontSize = 12.sp)
                    if (release.notes.isNotBlank()) {
                        Text("版本说明", fontSize = 13.sp)
                        val notes = release.notes.lineSequence().map { line ->
                            line.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                                .let { if (it.startsWith("- ")) "• " + it.removePrefix("- ") else it }
                        }.joinToString("\n")
                        Box(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                            Text(notes, fontSize = 13.sp, lineHeight = 20.sp, color = Muted)
                        }
                    }
                    Text("将在浏览器中下载；下载完成后，请在浏览器或下载管理器中打开安装包并确认更新。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) }
                if (!state.checking && (release == null || state.message != null)) {
                    TextButton(onClick = openReleases) { Text("前往 GitHub 发布页") }
                }
            }
        },
        confirmButton = {
            if (release != null) TextButton(onClick = download, enabled = !state.checking) { Text("浏览器下载") }
            else if (!state.checking && state.message != "当前已是最新版本") TextButton(onClick = retry) { Text("重试") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}
