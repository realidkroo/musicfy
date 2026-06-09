import re

file_path = "app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

speed_dial_old_pattern = r"""                        HomeSection\.SpeedDial -> \{
                            speedDialItems\.takeIf \{ it\.isNotEmpty\(\) \}\?\.let \{ items ->.*?HomeSection\.QuickPicks -> \{"""

speed_dial_new = """                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "speed_dial_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.speed_dial),
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier.animateItem(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = items,
                                            key = { it.id }
                                        ) { item ->
                                            Box(modifier = Modifier.width(160.dp)) {
                                                ytGridItem(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.QuickPicks -> {"""

content = re.sub(speed_dial_old_pattern, speed_dial_new, content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)

print("Done replacing SpeedDial section")
