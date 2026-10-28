package io.github.showcode1024.oneframe

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.showcode1024.oneframe.data.EmbySession
import io.github.showcode1024.oneframe.data.LibraryItem
import io.github.showcode1024.oneframe.data.LocalStore
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FavoritesIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("prism", 0)
    private var oldSession: String? = null
    private var oldSessions: String? = null
    private var oldAutoUpdate = true

    @Before fun isolate() {
        oldSession = prefs.getString("session", null); oldSessions = prefs.getString("sessions", null)
        oldAutoUpdate = prefs.getBoolean("auto_update", true)
        prefs.edit().remove("session").remove("sessions").putBoolean("auto_update", false).commit()
    }
    @After fun restore() {
        prefs.edit().apply {
            if (oldSession == null) remove("session") else putString("session", oldSession)
            if (oldSessions == null) remove("sessions") else putString("sessions", oldSessions)
            putBoolean("auto_update", oldAutoUpdate)
        }.commit()
    }

    @Test fun staleLibraryResponseDoesNotUndoConfirmedFavorite() {
        val viewsStarted = CountDownLatch(1)
        val releaseViews = CountDownLatch(1)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when {
                        request.path!!.endsWith("/Views") -> {
                            viewsStarted.countDown()
                            releaseViews.await(20, TimeUnit.SECONDS)
                            MockResponse().setBody("""{"Items":[{"Id":"movie","Name":"Movie","Type":"Movie","UserData":{"IsFavorite":false}}]}""")
                        }
                        request.path!!.contains("/FavoriteItems/") -> MockResponse().setBody("""{"IsFavorite":true}""")
                        request.requestUrl!!.encodedPath.endsWith("/Items/Latest") -> MockResponse().setBody("[]")
                        else -> MockResponse().setBody("""{"Items":[]}""")
                    }
                }
            }
            server.start()
            LocalStore(context).saveSession(EmbySession(server.url("/").toString().trimEnd('/'), "u", "token", "User"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                assertTrue(viewsStarted.await(15, TimeUnit.SECONDS))
                try {
                    scenario.onActivity { model.toggleFavorite(LibraryItem("movie", "Movie", "Movie")) }
                    compose.waitUntil(20_000) { !model.state.value.favoriteBusy }
                    assertNull(model.state.value.error)
                    releaseViews.countDown()
                    compose.waitUntil(20_000) { model.state.value.items.isNotEmpty() }
                    assertTrue(model.state.value.items.first().favorite)
                } finally { releaseViews.countDown() }
            }
        }
    }

    @Test fun changingServerWhileFavoriteRequestIsPendingDoesNotLeakState() {
        val favoriteStarted = CountDownLatch(1)
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.contains("/FavoriteItems/") -> {
                        favoriteStarted.countDown()
                        MockResponse().setBody("""{"IsFavorite":true}""").setBodyDelay(2, TimeUnit.SECONDS)
                    }
                    request.requestUrl!!.encodedPath.endsWith("/Items/Latest") -> MockResponse().setBody("[]")
                    else -> MockResponse().setBody("""{"Items":[]}""")
                }
            }
            second.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.requestUrl!!.encodedPath.endsWith("/Items/Latest"))
                    MockResponse().setBody("[]") else MockResponse().setBody("""{"Items":[]}""")
            }
            first.start(); second.start()
            val a = EmbySession(first.url("/").toString().trimEnd('/'), "a", "a-token", "A")
            val b = EmbySession(second.url("/").toString().trimEnd('/'), "b", "b-token", "B")
            LocalStore(context).apply { saveSession(a); saveSession(b); saveSession(a) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                scenario.onActivity { model.toggleFavorite(LibraryItem("movie", "Movie", "Movie")) }
                assertTrue(favoriteStarted.await(15, TimeUnit.SECONDS))
                scenario.onActivity { model.switchServer(b) }
                compose.waitUntil(20_000) { model.state.value.session == b && !model.state.value.favoriteBusy && !model.state.value.loading }
                Thread.sleep(2500)
                assertEquals(b, model.state.value.session)
                assertFalse(model.state.value.favoriteBusy)
                assertTrue(model.state.value.items.isEmpty())
                assertNull(model.state.value.error)
            }
        } }
    }

    @Test fun staleFavoritesResponseCannotRemoveNewlyAddedItem() {
        val favoritesStarted = CountDownLatch(1)
        val releaseFavorites = CountDownLatch(1)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.requestUrl?.queryParameter("IsFavorite") == "true" -> {
                        favoritesStarted.countDown()
                        releaseFavorites.await(20, TimeUnit.SECONDS)
                        MockResponse().setBody("""{"Items":[]}""")
                    }
                    request.path!!.contains("/FavoriteItems/") -> MockResponse().setBody("""{"IsFavorite":true}""")
                    request.requestUrl!!.encodedPath.endsWith("/Items/Latest") -> MockResponse().setBody("""[{"Id":"marker","Name":"Marker","Type":"Movie"}]""")
                    else -> MockResponse().setBody("""{"Items":[]}""")
                }
            }
            server.start()
            LocalStore(context).saveSession(EmbySession(server.url("/").toString().trimEnd('/'), "u", "token", "User"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                assertTrue(favoritesStarted.await(15, TimeUnit.SECONDS))
                try {
                    scenario.onActivity { model.toggleFavorite(LibraryItem("movie", "Movie", "Movie")) }
                    compose.waitUntil(20_000) { !model.state.value.favoriteBusy && model.state.value.favorites.isNotEmpty() }
                    releaseFavorites.countDown()
                    compose.waitUntil(20_000) { model.state.value.latestItems.any { it.id == "marker" } }
                    assertTrue(model.state.value.favorites.any { it.id == "movie" && it.favorite })
                } finally { releaseFavorites.countDown() }
            }
        }
    }
}
