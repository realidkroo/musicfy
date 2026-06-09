import re

file_path = "app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace AccountPlaylists
account_playlists_old = """                        HomeSection.AccountPlaylists -> {
                            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
                                item(key = "account_playlists_title") {
                                    NavigationTitle(
                                        label = stringResource(R.string.your_youtube_playlists),
                                        title = accountName,
                                        thumbnail = {
                                            if (url != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(url)
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .diskCacheKey(url)
                                                        .crossfade(false)
                                                        .build(),
                                                    placeholder = painterResource(id = R.drawable.person),
                                                    error = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(ListThumbnailSize)
                                                )
                                            }
                                        },
                                        onClick = {
                                            navController.navigate("account")
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        items(
                                            items = accountPlaylists.distinctBy { it.id },
                                            key = { it.id },
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }"""

account_playlists_new = """                        HomeSection.AccountPlaylists -> {
                            if (!localPlaylists.isNullOrEmpty() || !accountPlaylists.isNullOrEmpty()) {
                                item(key = "account_playlists_title") {
                                    NavigationTitle(
                                        label = "Your Playlists",
                                        title = accountName,
                                        thumbnail = {
                                            if (url != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(url)
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .diskCacheKey(url)
                                                        .crossfade(false)
                                                        .build(),
                                                    placeholder = painterResource(id = R.drawable.person),
                                                    error = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(ListThumbnailSize)
                                                )
                                            }
                                        },
                                        onClick = {
                                            navController.navigate("account")
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier.animateItem(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        localPlaylists?.let { items ->
                                            items(
                                                items = items.distinctBy { it.id },
                                                key = { it.id },
                                            ) { item ->
                                                localGridItem(item)
                                            }
                                        }
                                        accountPlaylists?.let { items ->
                                            items(
                                                items = items.distinctBy { it.id },
                                                key = { it.id },
                                            ) { item ->
                                                ytGridItem(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }"""
content = content.replace(account_playlists_old, account_playlists_new)


# Replace Top Bar wrapper
# Need to find LazyColumn call and wrap it, and add the Top Bar Box.
topbar_old = """            LazyColumn(
                state = lazylistState,
                contentPadding = PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                )
            ) {"""

topbar_new = """            // Wrap LazyColumn to allow fixed Top Bar overlay
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazylistState,
                    contentPadding = PaddingValues(
                        bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                    )
                ) {"""
content = content.replace(topbar_old, topbar_new)

# Find the end of LazyColumn, which is before LaunchedEffect(quickPicks)
lazy_end_old = """    LaunchedEffect(quickPicks) {"""
lazy_end_new = """            // Fixed Top Bar Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(
                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        bottom = 16.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Musicfy",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } // End of Box wrapping LazyColumn
    LaunchedEffect(quickPicks) {"""
content = content.replace(lazy_end_old, lazy_end_new)


with open(file_path, "w") as f:
    f.write(content)
print("Done modifying HomeScreen TopBar and AccountPlaylists")
