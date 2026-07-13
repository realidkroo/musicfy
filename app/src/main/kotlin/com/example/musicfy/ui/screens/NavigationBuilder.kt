// NavigationBuilder.kt
// the file functioned as navigation builder

package com.example.musicfy.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.musicfy.db.entities.FormatEntity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.example.musicfy.R
import com.example.musicfy.LocalDatabase
import com.example.musicfy.constants.DarkModeKey
import com.example.musicfy.constants.PlaylistSortType
import com.example.musicfy.constants.PureBlackKey
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.SongArtistMap
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.ui.component.NavigationTitle
import com.example.musicfy.ui.screens.artist.ArtistAlbumsScreen
import com.example.musicfy.ui.screens.artist.ArtistItemsScreen
import com.example.musicfy.ui.screens.artist.ArtistScreen
import com.example.musicfy.ui.screens.artist.ArtistSongsScreen
import com.example.musicfy.ui.screens.playlist.AutoPlaylistScreen
import com.example.musicfy.ui.screens.playlist.CachePlaylistScreen
import com.example.musicfy.ui.screens.playlist.LocalPlaylistScreen
import com.example.musicfy.ui.screens.playlist.OnlinePlaylistScreen
import com.example.musicfy.ui.screens.playlist.TopPlaylistScreen
import com.example.musicfy.ui.screens.search.OnlineSearchResult
import com.example.musicfy.ui.screens.search.SearchScreen
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.ui.screens.settings.SettingsScreen
import com.example.musicfy.ui.screens.library.LibraryAlbumsScreen
import com.example.musicfy.ui.screens.SectionDetailScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import com.example.musicfy.viewmodels.HomeViewModel
import com.example.musicfy.ui.screens.library.LibraryArtistsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID


@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState
) {
    composable(Screens.Home.route) {
        HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
    }

    composable(
        route = "section_detail/{sectionId}",
        arguments = listOf(
            navArgument("sectionId") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionId = backStackEntry.arguments?.getString("sectionId") ?: return@composable
        val homeViewModel: HomeViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
        SectionDetailScreen(
            navController = navController,
            sectionId = sectionId,
            homeViewModel = homeViewModel
        )
    }


    composable(Screens.Search.route) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }
        SearchScreen(
            navController = navController,
            pureBlack = pureBlack
        )
    }

    composable("library") {
        LibraryTabScreen(navController = navController)
    }

    composable(Screens.Settings.route) {
        SettingsScreen(navController = navController)
    }

    composable("advanced_audio_settings") {
        com.example.musicfy.ui.screens.settings.AdvancedAudioSettingsScreen(navController = navController)
    }

    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }

    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }

    composable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    composable("library/albums") {
        LibraryAlbumsScreen(navController, scrollBehavior)
    }

    composable("library/artists") {
        LibraryArtistsScreen(navController, scrollBehavior)
    }
}

@Composable
private fun LibraryTabScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val songs by database.allSongs().collectAsState(initial = emptyList())
    val playlists by database.playlists(PlaylistSortType.SONG_COUNT, descending = true)
        .collectAsState(initial = emptyList())

    var importProgress by remember { mutableFloatStateOf(-1f) } // -1 = not importing
    var isImporting by remember { mutableStateOf(false) }

    val automaticPlaylists = remember(songs) {
        listOf(
            LibraryAutoPlaylist(
                title = "Liked Songs",
                subtitle = "${songs.count { it.song.liked }} songs",
                playTime = songs.filter { it.song.liked }.sumOf { it.song.totalPlayTime },
                route = "auto_playlist/liked"
            ),
            LibraryAutoPlaylist(
                title = "Downloaded",
                subtitle = "${songs.count { it.song.isDownloaded }} songs",
                playTime = songs.filter { it.song.isDownloaded }.sumOf { it.song.totalPlayTime },
                route = "auto_playlist/downloaded"
            ),
            LibraryAutoPlaylist(
                title = "Local Songs",
                subtitle = "${songs.count { it.song.isLocal }} songs",
                playTime = songs.filter { it.song.isLocal }.sumOf { it.song.totalPlayTime },
                route = "auto_playlist/local"
            ),
            LibraryAutoPlaylist(
                title = "Uploaded",
                subtitle = "${songs.count { it.song.isUploaded }} songs",
                playTime = songs.filter { it.song.isUploaded }.sumOf { it.song.totalPlayTime },
                route = "auto_playlist/uploaded"
            )
        ).sortedByDescending { it.playTime }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            isImporting = true
            importProgress = 0f
            coroutineScope.launch {
                var successCount = 0
                var failCount = 0
                val total = uris.size
                for ((index, uri) in uris.withIndex()) {
                    try {
                        // Take persistable permission so we can play it later
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        // Extract metadata (includes artwork + mime type)
                        val metadata = withContext(Dispatchers.IO) {
                            extractAudioMetadata(context, uri)
                        }

                        // Extract and save embedded artwork during import
                        val songId = "LOCAL_${UUID.randomUUID()}"
                        val thumbnailUrl = withContext(Dispatchers.IO) {
                            metadata.embeddedArtwork?.let { artworkData ->
                                try {
                                    val artworkFile = java.io.File(
                                        context.cacheDir,
                                        "artwork_${songId.hashCode()}.jpg"
                                    )
                                    artworkFile.writeBytes(artworkData)
                                    android.net.Uri.fromFile(artworkFile).toString()
                                } catch (_: Exception) { null }
                            }
                        }

                        // Insert into database
                        withContext(Dispatchers.IO) {
                            database.transaction {
                                insert(
                                    SongEntity(
                                        id = songId,
                                        title = metadata.title,
                                        duration = metadata.durationSeconds,
                                        thumbnailUrl = thumbnailUrl,
                                        albumName = metadata.album,
                                        isLocal = true,
                                        inLibrary = LocalDateTime.now(),
                                        localUri = uri.toString(),
                                    )
                                )

                                // Insert artist
                                val artistName = metadata.artist
                                val artistId = artistByName(artistName)?.id
                                    ?: ArtistEntity.generateArtistId()
                                insert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = artistName,
                                        isLocal = true,
                                    )
                                )
                                insert(
                                    SongArtistMap(
                                        songId = songId,
                                        artistId = artistId,
                                        position = 0,
                                    )
                                )

                                // Create FormatEntity immediately so format badge shows
                                val mimeType = metadata.mimeType ?: "audio/mpeg"
                                val codecs = when {
                                    mimeType.contains("flac") -> "flac"
                                    mimeType.contains("opus") || mimeType.contains("ogg") -> "opus"
                                    mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "mp4a.40.2"
                                    mimeType.contains("wav") -> "pcm"
                                    else -> "mp3"
                                }
                                upsert(
                                    FormatEntity(
                                        id = songId,
                                        itag = -1,
                                        mimeType = mimeType,
                                        codecs = codecs,
                                        bitrate = metadata.bitrate ?: 0,
                                        sampleRate = metadata.sampleRate,
                                        contentLength = 0L,
                                        loudnessDb = null,
                                        perceptualLoudnessDb = null,
                                        playbackUrl = null
                                    )
                                )
                            }
                        }
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                    importProgress = (index + 1).toFloat() / total
                }
                isImporting = false
                importProgress = -1f

                val message = if (failCount == 0) {
                    "Imported $successCount song${if (successCount != 1) "s" else ""}"
                } else {
                    "Imported $successCount, failed $failCount"
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues()
        ) {
            item(key = "library_title") {
                NavigationTitle(title = stringResource(R.string.your_library))
            }
            item(key = "library_import") {
                LibraryImportCard(
                    totalSongs = songs.size,
                    totalPlaylists = playlists.size,
                    isImporting = isImporting,
                    importProgress = importProgress,
                    onClick = {
                        if (!isImporting) {
                            launcher.launch(
                                arrayOf(
                                    "audio/*",
                                    "audio/mpeg",
                                    "audio/mp4",
                                    "audio/flac",
                                    "audio/ogg",
                                    "audio/wav",
                                    "audio/x-wav",
                                    "audio/aac"
                                )
                            )
                        }
                    }
                )
            }
            item(key = "library_categories_title") {
                Text(
                    text = "Categories",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            item(key = "library_categories") {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    LibraryCategoryRow("Liked Songs") { navController.navigate("auto_playlist/liked") }
                    LibraryCategoryRow("Downloaded") { navController.navigate("auto_playlist/downloaded") }
                    LibraryCategoryRow("Albums") { navController.navigate("library/albums") }
                    LibraryCategoryRow("Local") { navController.navigate("auto_playlist/local") }
                    LibraryCategoryRow("Artist") { navController.navigate("library/artists") }
                }
            }
            item(key = "library_playlists_title") {
                Text(
                    text = "Playlists",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            items(
                items = automaticPlaylists,
                key = { "library_auto_${it.title}" }
            ) { playlist ->
                LibraryPlaylistRow(
                    title = playlist.title,
                    subtitle = playlist.subtitle,
                    onClick = { navController.navigate(playlist.route) }
                )
            }
            items(
                items = playlists,
                key = { "library_playlist_${it.id}" }
            ) { playlist ->
                LibraryPlaylistRow(
                    title = playlist.title,
                    subtitle = "${playlist.songCount} songs",
                    onClick = { navController.navigate("local_playlist/${playlist.id}") }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private data class LibraryAutoPlaylist(
    val title: String,
    val subtitle: String,
    val playTime: Long,
    val route: String,
)

@Composable
private fun LibraryImportCard(
    totalSongs: Int,
    totalPlaylists: Int,
    isImporting: Boolean,
    importProgress: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.add_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isImporting) "Importing songs..." else "Add more song here!",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Total songs - $totalSongs | Total Playlist - $totalPlaylists",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isImporting && importProgress >= 0f) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { importProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(importProgress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val embeddedArtwork: ByteArray?,
)

private fun extractAudioMetadata(
    context: android.content.Context,
    uri: Uri,
): LocalAudioMetadata {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown"
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            ?: "Unknown Artist"
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            ?: context.contentResolver.getType(uri)
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull()
        val sampleRate = try {
            // METADATA_KEY_SAMPLERATE = 38, available on API 31+
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                retriever.extractMetadata(38)?.toIntOrNull()
            } else null
        } catch (_: Exception) { null }
        val embeddedArtwork = try { retriever.embeddedPicture } catch (_: Exception) { null }
        LocalAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = (durationMs / 1000).toInt(),
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            embeddedArtwork = embeddedArtwork,
        )
    } catch (e: Exception) {
        // Fallback: use filename as title
        LocalAudioMetadata(
            title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown",
            artist = "Unknown Artist",
            album = null,
            durationSeconds = 0,
            mimeType = context.contentResolver.getType(uri),
            bitrate = null,
            sampleRate = null,
            embeddedArtwork = null,
        )
    } finally {
        retriever.release()
    }
}

@Composable
private fun LibraryCategoryRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                shape = RoundedCornerShape(0.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f))
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryPlaylistRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                painter = painterResource(R.drawable.playlist_play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
