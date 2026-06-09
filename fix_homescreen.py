import re

file_path = "app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Remove the incorrectly placed top bar
bad_top_bar = """            // Fixed Top Bar Overlay
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

content = content.replace(bad_top_bar, "    LaunchedEffect(quickPicks) {")

# 2. Insert the top bar correctly at the end of LazyColumn
lazy_end_pattern = r"""                item\(key = "bottom_spacer"\) \{
                    Spacer\(modifier = Modifier\.height\(30\.dp\)\)
                \}
            \}

            HideOnScrollFAB\("""

good_top_bar = """                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            } // End of LazyColumn

            // Fixed Top Bar Overlay
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

            HideOnScrollFAB("""

content = re.sub(lazy_end_pattern, good_top_bar, content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)

print("Done fixing HomeScreen")
