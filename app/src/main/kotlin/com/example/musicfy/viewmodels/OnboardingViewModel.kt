// OnboardingViewModel.kt

package com.example.musicfy.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.ProfileCardNumberKey
import com.example.musicfy.constants.ProfileJoinedEpochDayKey
import com.example.musicfy.constants.SelectedYtmPlaylistsKey
import com.example.musicfy.constants.YtPullHistoryKey
import com.example.musicfy.constants.YtReportPlaysKey
import com.example.musicfy.constants.YtSyncLikedSongsKey
import com.example.musicfy.constants.YtSyncNewPlaylistsKey
import com.example.musicfy.constants.YtmSyncKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.importer.MusicImportService
import com.example.musicfy.importer.ParsedImport
import com.example.musicfy.importer.YouTubeSyncManager
import com.example.musicfy.importer.account.AccountImportSession
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.importer.parseImportCsv
import com.example.musicfy.ui.screens.setup.onboarding.BlurCapability
import com.example.musicfy.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.random.Random

/** What the "Import & sync" options page sets. */
data class YouTubeSyncChoices(
    val syncOnLaunch: Boolean = true,
    val newPlaylistsToYouTube: Boolean = true,
    val reportPlays: Boolean = true,
    val copyHistory: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val importService: MusicImportService,
    private val youTubeSync: YouTubeSyncManager,
) : ViewModel() {

    var session by mutableStateOf<AccountImportSession?>(null)
        private set

    /** Music came in some way (an account or a backup); Home builds itself then, so artist picking is skipped. */
    var broughtMusicIn by mutableStateOf(false)
        private set

    /** Set on the Hello page from its frame timings. */
    var blur by mutableStateOf<BlurCapability.Result?>(null)
        private set

    val cardNumber: String = (10_000_000_000L + Random.nextLong(89_999_999_999L)).toString()
    val joinedText: String = "joined at " + LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    private val _artistSuggestions = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artistSuggestions: StateFlow<List<ArtistItem>> = _artistSuggestions.asStateFlow()
    private val _artistResults = MutableStateFlow<List<ArtistItem>?>(null)
    val artistResults: StateFlow<List<ArtistItem>?> = _artistResults.asStateFlow()
    private var searchJob: Job? = null
    private var suggestionsLoaded = false

    /** Onboarding can be run again from Settings; start each run clean. */
    fun reset() {
        session?.cancel()
        session = null
        broughtMusicIn = false
        blur = null
        _artistResults.value = null
    }

    // ---- providers ----

    fun startProvider(service: AccountService) {
        session?.cancel()
        session = AccountImportSession(service, context, viewModelScope).also { it.start() }
    }

    fun closeProvider() {
        session?.cancel()
        session = null
    }

    /** Whatever was read from the account goes to the background importer; onboarding moves on. */
    fun startImport(parsed: ParsedImport) {
        if (importService.startImport(parsed)) broughtMusicIn = true
    }

    /** "Import & sync": remember what to sync and how, then run the first sync in the background. */
    fun startYouTubeSync(selectedIds: Set<String>, likedId: String, choices: YouTubeSyncChoices) {
        broughtMusicIn = true
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                // nothing picked means everything, which is also what sync did before picking existed
                prefs[SelectedYtmPlaylistsKey] = selectedIds.filterNot { it == likedId }.joinToString(",")
                prefs[YtSyncLikedSongsKey] = selectedIds.isEmpty() || likedId in selectedIds
                prefs[YtmSyncKey] = choices.syncOnLaunch
                prefs[YtSyncNewPlaylistsKey] = choices.newPlaylistsToYouTube
                prefs[YtReportPlaysKey] = choices.reportPlays
                prefs[YtPullHistoryKey] = choices.copyHistory
            }
            // its own scope: keeps going after onboarding closes
            youTubeSync.syncNow()
        }
    }

    /** A Musicfy (or any playlist) CSV; null on success, otherwise what went wrong. */
    suspend fun importBackup(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return@withContext "Couldn't open that file."
            val parsed = parseImportCsv(text, fallbackPlaylistName = "Imported playlist")
            if (parsed.totalSongs == 0 && parsed.totalPlaylists == 0) return@withContext "No songs in that file."
            if (!importService.startImport(parsed)) return@withContext "Another import is still running."
            broughtMusicIn = true
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Backup import failed")
            "Couldn't read that file. Make sure it's a .csv backup."
        }
    }

    // ---- profile ----

    /** The signed-in account's name, to start the profile from. */
    fun accountName(): String? = session?.library?.accountName?.takeIf { it.isNotBlank() }

    /** Downloads the account's picture to a local file the profile can keep. */
    suspend fun accountPhoto(): Uri? = withContext(Dispatchers.IO) {
        val url = session?.library?.accountPhotoUrl ?: return@withContext null
        // YouTube serves tiny avatars unless asked for a size
        val sized = url.replace(Regex("=s\\d+(-[^&]*)?$"), "=s512")
        try {
            val connection = (URL(sized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            connection.inputStream.use { input ->
                val file = File(context.cacheDir, "onboarding_account_photo.jpg")
                file.outputStream().use { input.copyTo(it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Timber.w(e, "Account photo download failed")
            null
        }
    }

    fun saveCard() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ProfileCardNumberKey] = cardNumber
                prefs[ProfileJoinedEpochDayKey] = LocalDate.now().toEpochDay()
            }
        }
    }

    // ---- blur ----

    fun onBlurProbe(probe: BlurCapability.Probe) {
        blur = BlurCapability.decide(context, probe)
    }

    // ---- artists ----

    fun loadArtistSuggestions() {
        if (suggestionsLoaded) return
        suggestionsLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            val fromCharts = YouTube.getChartsPage().getOrNull()?.sections.orEmpty()
                .flatMap { it.items }
                .filterIsInstance<ArtistItem>()
            // the charts alone are often a dozen names; a spread of genres fills the page out
            val fromGenres = coroutineScope {
                SuggestionQueries.map { query ->
                    async {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty()
                            .filterIsInstance<ArtistItem>()
                            .take(6)
                    }
                }.awaitAll()
            }
            // interleaved, so the first screen isn't all one genre
            val mixed = buildList {
                addAll(fromCharts)
                val longest = fromGenres.maxOfOrNull { it.size } ?: 0
                for (i in 0 until longest) fromGenres.forEach { list -> list.getOrNull(i)?.let(::add) }
            }
            val artists = mixed.distinctBy { it.id }.filter { !it.thumbnail.isNullOrBlank() }.take(MaxSuggestions)
            _artistSuggestions.value = artists
            if (artists.size < MinSuggestions) suggestionsLoaded = false
        }
    }

    fun searchArtists(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _artistResults.value = null
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _artistResults.value = YouTube.search(q, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty()
                .filterIsInstance<ArtistItem>()
                .distinctBy { it.id }
                .take(24)
        }
    }

    /** Picked artists join the library; Home leans on them until there's listening history. */
    fun saveArtists(artists: List<ArtistItem>) {
        if (artists.isEmpty()) return
        val now = LocalDateTime.now()
        database.query {
            artists.forEachIndexed { index, artist ->
                insert(
                    ArtistEntity(
                        id = artist.id,
                        name = artist.title,
                        thumbnailUrl = artist.thumbnail,
                        channelId = artist.channelId,
                        // spaced so the library keeps the order they were picked in
                        bookmarkedAt = now.minusSeconds(index.toLong()),
                    )
                )
            }
        }
    }

    private companion object {
        const val MinSuggestions = 30
        const val MaxSuggestions = 48
        val SuggestionQueries = listOf(
            "pop", "hip hop", "r&b", "rock", "indie", "k-pop", "edm", "latin", "jazz", "j-pop", "country", "afrobeats",
        )
    }

    override fun onCleared() {
        session?.cancel()
    }
}
