package io.github.showcode1024.oneframe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.showcode1024.oneframe.data.LocalStore
import io.github.showcode1024.oneframe.data.RecentMedia
import io.github.showcode1024.oneframe.player.PlayerActivity
import io.github.showcode1024.oneframe.ui.HomeScreen
import io.github.showcode1024.oneframe.ui.OneFrameTheme
import io.github.showcode1024.oneframe.ui.UpdateDialog
import io.github.showcode1024.oneframe.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val updater: UpdateViewModel by viewModels()
    private var returningFromEmby = false
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
        ); UpdateDialog(updater, download = ::downloadUpdate, openReleases = ::openReleases) } }
    }
    private fun downloadUpdate() {
        val release = updater.state.value.release ?: return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))) }
        catch (_: Exception) { updater.showError("无法打开浏览器，请稍后重试") }
    }
    private fun openReleases() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(io.github.showcode1024.oneframe.update.RELEASES_PAGE))) }
        catch (_: Exception) { updater.showError("无法打开浏览器，请稍后重试") }
    }
    override fun onResume() { super.onResume(); model.refreshRecents(); if (returningFromEmby) {
        returningFromEmby = false; model.refreshHome(); model.loadLibrary()
    } }
}
