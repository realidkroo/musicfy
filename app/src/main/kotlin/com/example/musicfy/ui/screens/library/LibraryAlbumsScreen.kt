package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.AlbumSortType
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.LibraryAlbumGridItem
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albums by database.albums(AlbumSortType.CREATE_DATE, true).collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TopAppBar(
                    title = { Text("Albums") },
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }

            items(
                items = albums,
                key = { it.id }
            ) { album ->
                LibraryAlbumGridItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    album = album,
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
