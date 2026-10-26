package io.github.showcode1024.oneframe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.showcode1024.oneframe.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val session: EmbySession? = null,
    val recents: List<RecentMedia> = emptyList(),
    val softwareDecode: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val path: List<LibraryItem> = emptyList(),
    val search: String = "",
    val canLoadMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    val api = EmbyClient(store.deviceId)
    private val mutableState = MutableStateFlow(HomeState(store.session(), store.recents(), store.softwareDecode))
    val state = mutableState.asStateFlow()
    private var libraryJob: Job? = null

    init { if (state.value.session != null) loadLibrary() }
    fun refreshRecents() { mutableState.update { it.copy(recents = store.recents()) } }
    fun softwareDecode(enabled: Boolean) {
        store.setSoftwareDecode(enabled)
        mutableState.update { it.copy(softwareDecode = enabled) }
    }
    fun forget(uri: String) { store.forget(uri); refreshRecents() }
    fun dismissError() { mutableState.update { it.copy(error = null) } }

    fun login(base: String, user: String, password: String, connected: () -> Unit) {
        if (state.value.loading) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val session = api.login(base, user, password)
                store.saveSession(session)
                mutableState.update { it.copy(session = session, path = emptyList(), search = "", loading = false) }
                connected()
                loadLibrary()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    fun logout() {
        libraryJob?.cancel()
        val old = state.value.session
        store.clearSession()
        mutableState.update { it.copy(session = null, items = emptyList(), path = emptyList(), search = "", loading = false) }
        if (old != null) viewModelScope.launch { runCatching { api.logout(old) } }
    }

    fun openFolder(item: LibraryItem) {
        mutableState.update { it.copy(path = it.path + item, search = "") }
        loadLibrary()
    }
    fun back() {
        mutableState.update { it.copy(path = it.path.dropLast(1), search = "") }
        loadLibrary()
    }
    fun search(query: String) {
        mutableState.update { it.copy(search = query.trim()) }
        loadLibrary()
    }
    fun loadLibrary(more: Boolean = false) {
        libraryJob?.cancel()
        val snapshot = state.value
        val session = snapshot.session ?: return
        libraryJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val views = snapshot.path.isEmpty() && snapshot.search.isBlank()
                val values = if (views) api.views(session) else api.items(session,
                    snapshot.path.lastOrNull()?.id, snapshot.search, if (more) snapshot.items.size else 0)
                mutableState.update { it.copy(items = if (more) snapshot.items + values else values,
                    loading = false, canLoadMore = !views && values.size == 100) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    fun play(item: LibraryItem, ready: (StreamSource) -> Unit) {
        if (state.value.loading) return
        val session = state.value.session ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val source = api.playback(session, item)
                mutableState.update { it.copy(loading = false) }
                ready(source)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    private fun fail(error: Exception) {
        if (error is EmbyException && error.status == 401) {
            store.clearSession()
            mutableState.update { it.copy(session = null, items = emptyList(), path = emptyList()) }
        }
        val message = when (error) {
            is EmbyException -> error.message
            is java.net.UnknownHostException -> "找不到服务器，请检查地址和网络"
            is java.net.SocketTimeoutException -> "服务器响应超时，请稍后重试"
            is javax.net.ssl.SSLException -> "HTTPS 证书验证失败，请检查服务器证书"
            else -> "连接失败，请检查服务器地址、网络和服务状态"
        }
        mutableState.update { it.copy(loading = false, error = message) }
    }
}
