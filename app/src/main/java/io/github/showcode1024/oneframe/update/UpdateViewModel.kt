package io.github.showcode1024.oneframe.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.showcode1024.oneframe.BuildConfig
import io.github.showcode1024.oneframe.data.LocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateState(
    val automatic: Boolean = true,
    val checking: Boolean = false,
    val release: UpdateRelease? = null,
    val dialog: Boolean = false,
    val message: String? = null,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val readyFile: File? = null,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val client = ReleaseClient()
    private val mutableState = MutableStateFlow(UpdateState(automatic = store.autoUpdate))
    val state = mutableState.asStateFlow()
    private var checkJob: Job? = null
    private var automaticCheckInFlight = false

    init {
        val age = System.currentTimeMillis() - store.lastUpdateCheck
        if (store.autoUpdate && (age < 0 || age >= 6 * 60 * 60 * 1000L)) check(manual = false)
    }

    fun automatic(value: Boolean) {
        store.setAutoUpdate(value)
        mutableState.update { it.copy(automatic = value) }
        if (!value && automaticCheckInFlight) {
            checkJob?.cancel(); automaticCheckInFlight = false
            mutableState.update { it.copy(checking = false) }
        }
        if (value) check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (state.value.checking || state.value.downloading) return
        automaticCheckInFlight = !manual
        checkJob = viewModelScope.launch {
            mutableState.update { it.copy(checking = true, message = null, dialog = manual || it.dialog) }
            try {
                val release = client.check(BuildConfig.VERSION_NAME)
                // An available update should be offered again on the next launch if postponed.
                store.setLastUpdateCheck(if (release == null) System.currentTimeMillis() else 0)
                mutableState.update {
                    it.copy(checking = false, release = release, readyFile = if (it.release == release) it.readyFile else null,
                        dialog = release != null || manual, message = if (manual && release == null) "当前已是最新版本" else null)
                }
                automaticCheckInFlight = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                automaticCheckInFlight = false
                mutableState.update { it.copy(checking = false, dialog = manual, message = if (manual) errorMessage(e, "无法检查更新，请检查网络后重试") else null) }
            }
        }
    }

    fun show() { mutableState.update { it.copy(dialog = true) } }
    fun dismiss() { mutableState.update { it.copy(dialog = false, message = null) } }
    fun showError(message: String) { mutableState.update { it.copy(dialog = true, message = message) } }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.downloading) return
        viewModelScope.launch {
            mutableState.update { it.copy(downloading = true, downloadedBytes = 0, readyFile = null, message = null) }
            val folder = File(getApplication<Application>().filesDir, "updates")
            val temporary = File(folder, "update-part.apk")
            try {
                client.download(release, temporary) { bytes, total -> mutableState.update {
                    if (it.downloadedBytes * 100 / total == bytes * 100 / total && bytes != total) it else it.copy(downloadedBytes = bytes)
                } }
                val finalFile = withContext(Dispatchers.IO) {
                    ApkVerifier.verify(getApplication(), temporary, release)
                    val file = File(folder, "update.apk")
                    if (file.exists() && !file.delete()) throw java.io.IOException("无法替换旧安装包")
                    if (!temporary.renameTo(file)) throw java.io.IOException("无法保存安装包")
                    file
                }
                mutableState.update { it.copy(downloading = false, readyFile = finalFile, message = "下载和校验完成，可安装更新") }
            } catch (e: CancellationException) { temporary.delete(); throw e }
            catch (e: Exception) {
                temporary.delete()
                mutableState.update { it.copy(downloading = false, readyFile = null, message = errorMessage(e, "下载或校验失败，请检查网络后重试")) }
            }
        }
    }

    private fun errorMessage(error: Exception, default: String): String = error.message
        ?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } && message.length < 150 } ?: default
}
