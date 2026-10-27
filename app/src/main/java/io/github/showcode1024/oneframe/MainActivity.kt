package io.github.showcode1024.oneframe

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.github.showcode1024.oneframe.data.LocalStore
import io.github.showcode1024.oneframe.data.RecentMedia
import io.github.showcode1024.oneframe.player.PlayerActivity
import io.github.showcode1024.oneframe.ui.HomeScreen
import io.github.showcode1024.oneframe.ui.OneFrameTheme
import io.github.showcode1024.oneframe.ui.UpdateDialog
import io.github.showcode1024.oneframe.update.UpdateViewModel
import io.github.showcode1024.oneframe.update.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val updater: UpdateViewModel by viewModels()
    private var returningFromEmby = false
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) installUpdate()
        else updater.showError("未授予安装权限；可以稍后在系统设置中允许 OneFrame 安装应用")
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val title = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } }.getOrNull() ?: "本地视频"
            val store = LocalStore(this)
            val position = store.recents().firstOrNull { it.uri == uri.toString() }?.position ?: 0.0
            store.remember(RecentMedia(uri.toString(), title, position))
            PlayerActivity.open(this, uri.toString(), title, position, true)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { OneFrameTheme { HomeScreen(model, updater,
            pickFile = { filePicker.launch(arrayOf("video/*", "application/octet-stream")) },
            playRecent = { PlayerActivity.open(this, it.uri, it.title, it.position, true) },
            playUrl = { PlayerActivity.open(this, it, "网络视频", 0.0, false) },
            playEmby = { model.playSelected { returningFromEmby = true; PlayerActivity.openEmby(this, it) } },
        ); UpdateDialog(updater, install = ::requestInstall, openReleases = ::openReleases) } }
    }
    private fun requestInstall() {
        if (updater.state.value.readyFile == null) return
        if (!packageManager.canRequestPackageInstalls()) {
            try { installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { updater.showError("无法打开安装权限设置，请到系统设置允许 OneFrame 安装应用") }
        } else installUpdate()
    }
    private fun installUpdate() {
        val state = updater.state.value
        val file = state.readyFile ?: return
        val release = state.release ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ApkVerifier.verify(this@MainActivity, file, release) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.updates", file)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("OneFrame update", uri) }
                startActivity(intent)
            } catch (_: Exception) { updater.showError("无法打开安装程序，请重试或前往 GitHub 下载") }
        }
    }
    private fun openReleases() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(io.github.showcode1024.oneframe.update.RELEASES_PAGE))) }
        catch (_: Exception) { updater.showError("无法打开浏览器，请稍后重试") }
    }
    override fun onResume() { super.onResume(); model.refreshRecents(); if (returningFromEmby) {
        returningFromEmby = false; model.refreshHome(); model.loadLibrary()
    } }
}
