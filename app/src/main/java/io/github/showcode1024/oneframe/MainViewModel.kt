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
    val resumeItems: List<LibraryItem> = emptyList(),
    val latestItems: List<LibraryItem> = emptyList(),
    val detail: LibraryItem? = null,
    val versions: List<MediaVersion> = emptyList(),
    val selectedVersionId: String? = null,
    val versionsLoading: Boolean = false,
    val versionsError: String? = null,
    val servers: List<EmbySession> = emptyList(),
    val experimentalP5Hardware: Boolean = false,
    val fastScaling: Boolean = false,
    val favoriteBusy: Boolean = false,
    val favorites: List<LibraryItem> = emptyList(),
    val favoritesError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    val api = EmbyClient(store.deviceId)
    private val mutableState = MutableStateFlow(HomeState(store.session(), store.recents(), store.softwareDecode,
        servers = store.sessions(), experimentalP5Hardware = store.experimentalP5Hardware, fastScaling = store.fastScaling))
    val state = mutableState.asStateFlow()
    private var libraryJob: Job? = null
    private var versionsJob: Job? = null
    private var homeJob: Job? = null
    private var playbackJob: Job? = null
    private var favoriteJob: Job? = null
    private var favoriteRevision = 0L
    private val favoriteChanges = mutableMapOf<String, Pair<Long, Boolean>>()

    private fun clearFavoriteRequests() {
        favoriteJob?.cancel(); favoriteJob = null; favoriteRevision = 0; favoriteChanges.clear()
    }
    private fun preserveRecentFavorites(items: List<LibraryItem>, startedAt: Long): List<LibraryItem> = items.map { item ->
        favoriteChanges[item.id]?.takeIf { it.first > startedAt }?.let { item.copy(favorite = it.second) } ?: item
    }

    init { if (state.value.session != null) { loadLibrary(); refreshHome() } }
    fun refreshRecents() { mutableState.update { it.copy(recents = store.recents()) } }
    fun softwareDecode(enabled: Boolean) {
        store.setSoftwareDecode(enabled)
        mutableState.update { it.copy(softwareDecode = enabled) }
    }
    fun experimentalP5Hardware(enabled: Boolean) {
        store.setExperimentalP5Hardware(enabled); mutableState.update { it.copy(experimentalP5Hardware = enabled) }
    }
    fun fastScaling(enabled: Boolean) {
        store.setFastScaling(enabled); mutableState.update { it.copy(fastScaling = enabled) }
    }
    fun forget(uri: String) { store.forget(uri); refreshRecents() }
    fun dismissError() { mutableState.update { it.copy(error = null) } }

    fun login(base: String, user: String, password: String, connected: () -> Unit) {
        if (state.value.loading) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val session = api.login(base, user, password)
                libraryJob?.cancel(); versionsJob?.cancel(); homeJob?.cancel(); playbackJob?.cancel(); clearFavoriteRequests()
                store.saveSession(session)
                mutableState.update { it.copy(session = session, servers = store.sessions(), path = emptyList(), search = "", loading = false,
                    items = emptyList(), resumeItems = emptyList(), latestItems = emptyList(), detail = null, favoriteBusy = false,
                    favorites = emptyList(), favoritesError = null) }
                connected()
                loadLibrary()
                refreshHome()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error, invalidateSession = false) }
        }
    }

    fun switchServer(session: EmbySession) {
        if (session == state.value.session) return
        libraryJob?.cancel()
        versionsJob?.cancel()
        homeJob?.cancel()
        playbackJob?.cancel()
        clearFavoriteRequests()
        store.saveSession(session)
        mutableState.update { it.copy(session = session, servers = store.sessions(), items = emptyList(), path = emptyList(), search = "", loading = false,
            error = null, detail = null, versions = emptyList(), resumeItems = emptyList(), latestItems = emptyList(), favoriteBusy = false,
            favorites = emptyList(), favoritesError = null) }
        loadLibrary(); refreshHome()
    }

    fun removeServer(server: EmbySession) {
        val wasActive = state.value.session == server
        if (wasActive) { libraryJob?.cancel(); versionsJob?.cancel(); homeJob?.cancel(); playbackJob?.cancel(); clearFavoriteRequests() }
        val active = store.removeSession(server)
        mutableState.update { if (wasActive) it.copy(session = active, servers = store.sessions(), items = emptyList(), path = emptyList(), search = "", loading = false,
            error = null, detail = null, versions = emptyList(), resumeItems = emptyList(), latestItems = emptyList(), favoriteBusy = false,
            favorites = emptyList(), favoritesError = null)
            else it.copy(servers = store.sessions()) }
        if (wasActive && active != null) { loadLibrary(); refreshHome() }
        viewModelScope.launch { runCatching { api.logout(server) } }
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
        val startedAt = favoriteRevision
        libraryJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val views = snapshot.path.isEmpty() && snapshot.search.isBlank()
                val values = if (views) api.views(session) else api.items(session,
                    snapshot.path.lastOrNull()?.id, snapshot.search, if (more) snapshot.items.size else 0)
                val currentValues = preserveRecentFavorites(values, startedAt)
                mutableState.update { it.copy(items = if (more) it.items + currentValues else currentValues,
                    loading = false, canLoadMore = !views && values.size == 100) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    fun refreshHome() {
        val session = state.value.session ?: return
        val startedAt = favoriteRevision
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val resume = try { api.resumeItems(session) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val latest = try { api.latestItems(session) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val favorites = try { api.favorites(session) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val currentResume = preserveRecentFavorites(resume, startedAt)
            val currentLatest = preserveRecentFavorites(latest, startedAt)
            if (state.value.session == session) mutableState.update { current ->
                val fetchedFavorites = favorites?.let { preserveRecentFavorites(it, startedAt).filter { item -> item.favorite } }
                val pendingAdds = if (fetchedFavorites != null) current.favorites.filter { item ->
                    favoriteChanges[item.id]?.let { it.first > startedAt && it.second } == true && fetchedFavorites.none { it.id == item.id }
                } else emptyList()
                current.copy(resumeItems = currentResume, latestItems = currentLatest,
                    favorites = if (fetchedFavorites == null) current.favorites else pendingAdds + fetchedFavorites,
                    favoritesError = if (favorites == null) "收藏加载失败，请检查网络后刷新" else null,
                    detail = current.detail?.let { detail -> (currentResume + currentLatest).firstOrNull { it.id == detail.id } ?: detail })
            }
        }
    }

    fun openDetail(item: LibraryItem) {
        versionsJob?.cancel()
        val session = state.value.session ?: return
        mutableState.update { it.copy(detail = item, versions = emptyList(), selectedVersionId = null, versionsLoading = true, versionsError = null) }
        versionsJob = viewModelScope.launch {
            try {
                val versions = api.playbackOptions(session, item)
                if (state.value.detail?.id == item.id && state.value.session == session) mutableState.update {
                    it.copy(versions = versions, selectedVersionId = versions.firstOrNull { v -> v.playable }?.id, versionsLoading = false,
                        versionsError = if (versions.isEmpty()) "服务器没有返回视频版本" else null)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.detail?.id == item.id) mutableState.update { it.copy(versionsLoading = false, versionsError = "无法获取版本，请检查网络后重试") }
            }
        }
    }
    fun closeDetail() { versionsJob?.cancel(); playbackJob?.cancel(); mutableState.update { it.copy(detail = null, versionsLoading = false, loading = false, error = null) } }
    fun selectVersion(id: String) { mutableState.update { it.copy(selectedVersionId = id) } }

    fun toggleFavorite(item: LibraryItem) {
        val snapshot = state.value
        val session = snapshot.session ?: return
        if (snapshot.favoriteBusy) return
        val current = snapshot.detail?.takeIf { it.id == item.id }
            ?: (snapshot.items + snapshot.resumeItems + snapshot.latestItems + snapshot.favorites).firstOrNull { it.id == item.id } ?: item
        val requested = !current.favorite
        mutableState.update { it.copy(favoriteBusy = true, error = null) }
        favoriteJob = viewModelScope.launch {
            try {
                val confirmed = api.setFavorite(session, item.id, requested)
                if (state.value.session != session) return@launch
                favoriteChanges[item.id] = ++favoriteRevision to confirmed
                fun update(value: LibraryItem) = if (value.id == item.id) value.copy(favorite = confirmed) else value
                mutableState.update { currentState -> currentState.copy(favoriteBusy = false,
                    detail = currentState.detail?.let(::update), items = currentState.items.map(::update),
                    resumeItems = currentState.resumeItems.map(::update), latestItems = currentState.latestItems.map(::update),
                    path = currentState.path.map(::update), favorites = if (confirmed)
                        (listOf(current.copy(favorite = true)) + currentState.favorites.filterNot { it.id == item.id })
                        else currentState.favorites.filterNot { it.id == item.id }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (state.value.session == session) { mutableState.update { it.copy(favoriteBusy = false) }; fail(e, clearLoading = false) }
            }
        }
    }

    fun playSelected(ready: (StreamSource) -> Unit) {
        if (state.value.loading) return
        val session = state.value.session ?: return
        val item = state.value.detail ?: return
        val selected = state.value.selectedVersionId ?: return
        playbackJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val source = api.playback(session, item, selected)
                mutableState.update { it.copy(loading = false) }
                ready(source)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { fail(error) }
        }
    }

    private fun fail(error: Exception, invalidateSession: Boolean = true, clearLoading: Boolean = true) {
        if (invalidateSession && error is EmbyException && error.status == 401) {
            clearFavoriteRequests()
            val active = state.value.session?.let { store.removeSession(it) }
            mutableState.update { it.copy(session = active, servers = store.sessions(), items = emptyList(), path = emptyList(), detail = null,
                favoriteBusy = false, favorites = emptyList(), favoritesError = null, resumeItems = emptyList(), latestItems = emptyList()) }
            if (active != null) { loadLibrary(); refreshHome() }
        }
        val message = when (error) {
            is EmbyException -> error.message
            is java.net.UnknownHostException -> "找不到服务器，请检查地址和网络"
            is java.net.SocketTimeoutException -> "服务器响应超时，请稍后重试"
            is javax.net.ssl.SSLException -> "HTTPS 证书验证失败，请检查服务器证书"
            else -> "连接失败，请检查服务器地址、网络和服务状态"
        }
        mutableState.update { it.copy(loading = if (clearLoading) false else it.loading, error = message) }
    }
}
