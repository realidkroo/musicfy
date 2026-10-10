// AccountImportSession.kt

package com.example.musicfy.importer.account

import android.content.Context
import com.example.musicfy.constants.AccountNameKey
import com.example.musicfy.extensions.isUserLoggedIn
import com.example.musicfy.importer.ImportSource
import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.ParsedImport
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get
import com.music.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

sealed interface AccountImportState {
    /** The service's sign-in page is showing. */
    data object SigningIn : AccountImportState
    /** YouTube Music uses the app's account, which isn't signed in yet. */
    data object NeedsYouTubeSignIn : AccountImportState
    data class Loading(val label: String) : AccountImportState
    data class Choosing(
        val accountName: String?,
        val playlists: List<RemotePlaylist>,
        val selected: Set<String>,
    ) : AccountImportState
    data class Reading(val label: String, val done: Int, val total: Int) : AccountImportState
    data class Failed(val message: String, val signedOut: Boolean) : AccountImportState
}

/**
 * One sign-in → list → pick → read pass against one service, shared by the Import settings page
 * and onboarding. Runs on the [scope] it's given (a ViewModel's), so it stops with its screen.
 */
class AccountImportSession(
    val service: AccountService,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val connector: AccountConnector? = when (service) {
        AccountService.SPOTIFY -> SpotifyConnector
        AccountService.APPLE_MUSIC -> AppleMusicConnector
        AccountService.TIDAL -> TidalConnector
        AccountService.YOUTUBE_MUSIC -> null
    }

    private val _state = MutableStateFlow<AccountImportState>(AccountImportState.Loading("Getting ready…"))
    val state: StateFlow<AccountImportState> = _state.asStateFlow()

    /** What the user picked, read and ready to import. */
    private val _ready = MutableStateFlow<ParsedImport?>(null)
    val ready: StateFlow<ParsedImport?> = _ready.asStateFlow()

    /** A one-off note for the list page (e.g. some playlists couldn't be read). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val credentials = CapturedCredentials()
    private val connectSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile var library: AccountLibrary? = null
        private set
    private var connectJob: Job? = null
    private var workJob: Job? = null
    private var lastChoosing: AccountImportState.Choosing? = null

    fun start() {
        if (service == AccountService.YOUTUBE_MUSIC) refreshYouTube() else startSignIn()
    }

    // ---- sign-in ----

    private fun startSignIn() {
        library = null
        lastChoosing = null
        _state.value = AccountImportState.SigningIn
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            for (signal in connectSignal) {
                if (library != null) break
                try {
                    val connected = connector?.connect(credentials) ?: continue
                    library = connected
                    loadPlaylists()
                    break
                } catch (e: CancellationException) {
                    // only a real cancellation ends sign-in; a stray one from a request mustn't
                    // leave the page waiting with nothing listening
                    ensureActive()
                    Timber.w(e, "${service.label} connect attempt was cut short")
                } catch (e: AccountRetryLater) {
                    delay(e.seconds * 1000)
                    connectSignal.trySend(Unit)
                } catch (e: Exception) {
                    // offline for a moment, or the service hiccuped: the next captured request retries
                    Timber.w(e, "${service.label} connect attempt failed")
                }
            }
        }
    }

    /** From the web page's hook or request headers; may arrive on any thread. */
    fun onHeader(name: String, value: String, url: String) {
        if (library != null) return
        credentials.addHeader(name, value, url)
        connectSignal.trySend(Unit)
    }

    /** Apple: tokens read straight from MusicKit in the page, as JSON {dev, user}. */
    fun onApplePageProbe(raw: String?) {
        if (library != null || raw.isNullOrBlank() || raw == "null") return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        json.optString("dev").takeIf { it.length > 20 && it != "null" }?.let {
            credentials.addHeader("authorization", "Bearer $it", "https://amp-api.music.apple.com/")
        }
        json.optString("user").takeIf { it.length > 20 && it != "null" }?.let {
            credentials.addHeader("media-user-token", it, "https://amp-api.music.apple.com/")
        }
        connectSignal.trySend(Unit)
    }

    /** Apple also keeps the user token as a cookie on music.apple.com. */
    fun onCookies(cookieHeader: String?) {
        if (library != null || cookieHeader.isNullOrBlank()) return
        cookieHeader.split(';').forEach { pair ->
            if (pair.substringBefore('=').trim().equals("media-user-token", ignoreCase = true)) {
                credentials.addHeader("media-user-token", pair.substringAfter('=').trim(), "https://music.apple.com/")
                connectSignal.trySend(Unit)
            }
        }
    }

    /** Forget this session (the caller clears the web cookies) and sign in again. */
    fun signInAgain() {
        workJob?.cancel()
        credentials.clear()
        library = null
        start()
    }

    fun refreshYouTube() {
        if (service != AccountService.YOUTUBE_MUSIC) return
        scope.launch(Dispatchers.IO) {
            if (!context.isUserLoggedIn()) {
                _state.value = AccountImportState.NeedsYouTubeSignIn
                return@launch
            }
            if (library == null) {
                val info = YouTube.accountInfo().getOrNull()
                library = YouTubeMusicLibrary(
                    accountName = info?.name ?: context.dataStore.get(AccountNameKey, "").ifBlank { null },
                    accountPhotoUrl = info?.thumbnailUrl,
                )
            }
            loadPlaylists()
        }
    }

    // ---- choosing ----

    fun retry() {
        if (library != null) scope.launch(Dispatchers.IO) { loadPlaylists() } else signInAgain()
    }

    private suspend fun loadPlaylists() {
        val lib = library ?: return
        _state.value = AccountImportState.Loading("Reading your ${service.label} library…")
        try {
            val playlists = lib.playlists()
            val choosing = AccountImportState.Choosing(lib.accountName, playlists, emptySet())
            lastChoosing = choosing
            _state.value = choosing
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Timber.w(e, "${service.label} library read was cut short")
            _state.value = AccountImportState.Failed("Couldn't read your ${service.label} library.", signedOut = false)
        } catch (e: AccountImportException) {
            if (e.signedOut) library = null
            _state.value = AccountImportState.Failed(e.message ?: "Couldn't read your library.", e.signedOut)
        } catch (e: Exception) {
            Timber.e(e, "${service.label} library read failed")
            _state.value = AccountImportState.Failed("Couldn't read your ${service.label} library.", signedOut = false)
        }
    }

    fun toggle(id: String) = updateChoosing { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    fun selectAll() = updateChoosing { it.copy(selected = it.playlists.map(RemotePlaylist::id).toSet()) }

    fun selectNone() = updateChoosing { it.copy(selected = emptySet()) }

    private fun updateChoosing(change: (AccountImportState.Choosing) -> AccountImportState.Choosing) {
        _state.update { current ->
            if (current is AccountImportState.Choosing) change(current).also { lastChoosing = it } else current
        }
    }

    // ---- reading what was picked ----

    fun readSelected() {
        val choosing = _state.value as? AccountImportState.Choosing ?: return
        val lib = library ?: return
        val chosen = choosing.playlists.filter { it.id in choosing.selected }
        if (chosen.isEmpty()) return

        workJob?.cancel()
        workJob = scope.launch(Dispatchers.IO) {
            val liked = mutableListOf<ImportedTrack>()
            val librarySongs = mutableListOf<ImportedTrack>()
            val playlists = linkedMapOf<String, List<ImportedTrack>>()
            val covers = mutableMapOf<String, String>()
            val failed = mutableListOf<String>()
            var signedOut = false

            for ((index, playlist) in chosen.withIndex()) {
                _state.value = AccountImportState.Reading("Reading \"${playlist.name}\"", index, chosen.size)
                try {
                    val tracks = lib.tracks(playlist)
                    when {
                        playlist.isLiked -> liked += tracks
                        playlist.isLibrary -> librarySongs += tracks
                        else -> {
                            val name = uniqueName(playlist.name, playlists.keys)
                            playlists[name] = tracks
                            playlist.coverUrl?.let { covers[name] = it }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AccountImportException) {
                    failed += playlist.name
                    if (e.signedOut) {
                        signedOut = true
                        break
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Reading ${playlist.name} failed")
                    failed += playlist.name
                }
            }

            if (signedOut) {
                library = null
                _state.value = AccountImportState.Failed("${service.label} signed you out while reading. Sign in again.", signedOut = true)
                return@launch
            }
            _state.value = lastChoosing ?: choosing
            if (liked.isEmpty() && librarySongs.isEmpty() && playlists.isEmpty()) {
                _notice.value = "Couldn't read ${failed.joinToString(", ").ifEmpty { "those playlists" }}."
                return@launch
            }
            _ready.value = ParsedImport(
                likedSongs = liked,
                librarySongs = librarySongs,
                playlists = playlists,
                warnings = if (failed.isEmpty()) emptyList() else listOf("Couldn't read: ${failed.joinToString(", ")}. The rest imports fine."),
                provider = ImportSource.fromKey(service.route),
                playlistCovers = covers,
            )
        }
    }

    fun cancelReading() {
        workJob?.cancel()
        lastChoosing?.let { _state.value = it }
    }

    fun consumeReady() {
        _ready.value = null
    }

    /** Stops anything in flight; used when onboarding switches to another service. */
    fun cancel() {
        connectJob?.cancel()
        workJob?.cancel()
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }
}
