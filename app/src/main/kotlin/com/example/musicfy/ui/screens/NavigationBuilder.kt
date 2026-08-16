// NavigationBuilder.kt

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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.musicfy.ui.component.LocalNavAnimatedContentScope
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
import com.example.musicfy.ui.screens.search.GenreScreen
import com.example.musicfy.ui.screens.search.OnlineSearchResult
import com.example.musicfy.ui.screens.search.SearchScreen
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.ui.screens.settings.SettingsScreen
import com.example.musicfy.ui.screens.library.LibraryAlbumsScreen
import com.example.musicfy.ui.screens.SectionDetailScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    composable(
        route = Screens.Home.route,

        popEnterTransition = { fadeIn(tween(150)) },
    ) {

        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
        }
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

        val homeViewModel: HomeViewModel = hiltViewModel(navController.getBackStackEntry(Screens.Home.route))
        SectionDetailScreen(
            navController = navController,
            sectionId = sectionId,
            homeViewModel = homeViewModel
        )
    }

    composable("history") {
        HistoryScreen(navController = navController)
    }

    composable("artist_list_detail") {

        val homeViewModel: HomeViewModel = hiltViewModel(navController.getBackStackEntry(Screens.Home.route))
        ArtistListDetailScreen(
            navController = navController,
            homeViewModel = homeViewModel
        )
    }

    composable(
        route = "coming_soon/{sectionTitle}",
        arguments = listOf(
            navArgument("sectionTitle") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionTitle = backStackEntry.arguments?.getString("sectionTitle") ?: return@composable
        ComingSoonScreen(navController = navController, sectionTitle = sectionTitle)
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

    composable(
        route = "library",
        popEnterTransition = { fadeIn(tween(150)) },
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            com.example.musicfy.ui.screens.library.LibraryHomeScreen(
                navController = navController,
                pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme },
            )
        }
    }

    composable("library/songs") {
        com.example.musicfy.ui.screens.library.LibrarySongsScreen(navController = navController)
    }

    composable("library/playlists") {
        com.example.musicfy.ui.screens.library.LibraryPlaylistsScreen(navController = navController)
    }

    composable("library/added") {
        com.example.musicfy.ui.screens.library.LibraryRecentlyAddedScreen(navController = navController)
    }

    composable("library/downloaded") {
        com.example.musicfy.ui.screens.library.LibraryDownloadedScreen(navController = navController)
    }

    composable(Screens.Settings.route) {
        SettingsScreen(navController = navController)
    }

    composable("advanced_audio_settings") {
        com.example.musicfy.ui.screens.settings.AdvancedAudioSettingsScreen(navController = navController)
    }

    composable("appearance_settings") {
        com.example.musicfy.ui.screens.settings.AppearanceSettingsScreen(navController = navController)
    }

    composable("player_customize") {
        com.example.musicfy.ui.player.customize.PlayerCustomizeSettingsScreen(navController = navController)
    }

    composable("playback_settings") {
        com.example.musicfy.ui.screens.settings.PlaybackSettingsScreen(navController = navController)
    }

    composable("experimental_settings") {
        com.example.musicfy.ui.screens.settings.ExperimentalSettingsScreen(navController = navController)
    }

    composable("equalizer") {
        com.example.musicfy.ui.screens.equalizer.EqualizerScreen(navController = navController)
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

        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AlbumScreen(navController, scrollBehavior)
        }
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
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            OnlinePlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
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
        route = "genre/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
        GenreScreen(
            navController = navController,
            pureBlack = remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            },
        )
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
        com.example.musicfy.ui.screens.library.LibraryAlbumsScreen(navController = navController)
    }

    composable("library/artists") {
        com.example.musicfy.ui.screens.library.LibraryArtistsScreen(navController = navController)
    }
}

internal data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val embeddedArtwork: ByteArray?,
)

internal fun extractAudioMetadata(
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

