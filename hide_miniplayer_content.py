import sys

with open('app/src/main/kotlin/com/example/musicfy/ui/player/MiniPlayer.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    # Add parameter to fun LegacyMiniPlayer
    if "private fun LegacyMiniPlayer(" in line:
        new_lines.append(line)
        new_lines.append("    isTransparent: Boolean = false,\n")
        continue

    # Update hazeEffect in NewMiniPlayer (approx line 230)
    if "if (hazeState != null) {" in line and "let {" in lines[i-1]:
        new_lines.append('                    if (hazeState != null && !isTransparent) {\n')
        continue

    # Hide background color in NewMiniPlayer
    if "backgroundColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer" in line:
        new_lines.append('                                    backgroundColor = if (isTransparent) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,\n')
        continue

    # Hide background in Box modifier in NewMiniPlayer
    if ".background(color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer)" in line:
        new_lines.append('                .background(color = if (isTransparent) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer)\n')
        continue
    
    # Hide border in NewMiniPlayer
    if ".border(" in line and "outlineColor.copy(alpha = 0.5f)" in line:
        new_lines.append('                .let { if (!isTransparent) it.border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(100.dp)) else it }\n')
        continue

    # Hide border in LegacyMiniPlayer
    if ".border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))" in line:
        new_lines.append('                .let { if (!isTransparent) it.border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else it }\n')
        continue
    
    # Hide Album Art in NewMiniPlayer (approx line 300)
    if "Spacer(modifier = Modifier.width(8.dp))" in line and "contentAlignment = Alignment.Center" in lines[i+2]:
        new_lines.append(line)
        new_lines.append('                if (!isTransparent) {\n')
        continue

    # Close Album Art in NewMiniPlayer
    if "errorColor = errorColor" in line and "}" in lines[i+1] and "Spacer" in lines[i+3]:
        new_lines.append(line)
        new_lines.append('                    }\n')
        new_lines.append('                } else {\n')
        new_lines.append('                    Spacer(modifier = Modifier.size(48.dp))\n')
        new_lines.append('                }\n')
        continue

    # Hide Song Info in NewMiniPlayer
    if "// Song info - isolated composable" in line:
        new_lines.append(line)
        new_lines.append('                if (!isTransparent) {\n')
        continue

    if "modifier = Modifier.weight(1f)" in line and "NewMiniPlayerSongInfo(" in lines[i-4]:
        new_lines.append(line)
        new_lines.append('                    )\n')
        new_lines.append('                } else {\n')
        new_lines.append('                    Spacer(modifier = Modifier.weight(1f))\n')
        new_lines.append('                }\n')
        skip = True
        continue
        
    if skip and ")" in line:
        skip = False
        continue

    # Legacy MiniPlayer Album Art
    if "Spacer(modifier = Modifier.width(6.dp))" in line and "contentAlignment = Alignment.Center" in lines[i+2]:
        new_lines.append(line)
        new_lines.append('                if (!isTransparent) {\n')
        continue

    # Close Legacy MiniPlayer Album Art
    if "errorColor = errorColor" in line and "}" in lines[i+1] and "Column(" in lines[i+3]:
        new_lines.append(line)
        new_lines.append('                    }\n')
        new_lines.append('                } else {\n')
        new_lines.append('                    Spacer(modifier = Modifier.size(48.dp))\n')
        new_lines.append('                }\n')
        continue

    # Hide Song Info in LegacyMiniPlayer
    if "Column(" in line and "modifier = Modifier.weight(1f)" in lines[i+1] and "basicMarquee(" in lines[i+4]:
        new_lines.append('                if (!isTransparent) {\n')
        new_lines.append(line)
        continue

    # Close Legacy MiniPlayer Song Info
    if "color = MaterialTheme.colorScheme.onSurfaceVariant," in line and "style = typography.bodyMedium" in lines[i+1] and "maxLines = 1" in lines[i+2]:
        new_lines.append(line)
        new_lines.append('                            )\n')
        new_lines.append('                        }\n')
        new_lines.append('                    }\n')
        new_lines.append('                } else {\n')
        new_lines.append('                    Spacer(modifier = Modifier.weight(1f))\n')
        new_lines.append('                }\n')
        skip = True
        continue
        
    if skip and "}" in line and "PlayerButtons(" in lines[i+1]:
        skip = False
        continue

    new_lines.append(line)

with open('app/src/main/kotlin/com/example/musicfy/ui/player/MiniPlayer.kt', 'w') as f:
    f.writelines(new_lines)

print("Updated MiniPlayer.kt content!")
