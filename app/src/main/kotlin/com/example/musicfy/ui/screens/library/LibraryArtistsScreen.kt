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
import com.example.musicfy.R
import com.example.musicfy.constants.ArtistSortType
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.LibraryArtistGridItem
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    val artists by database.artists(ArtistSortType.CREATE_DATE, true).collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TopAppBar(
                    title = { Text("Artists") },
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
                items = artists,
                key = { it.id }
            ) { artist ->
                LibraryArtistGridItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    artist = artist,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
