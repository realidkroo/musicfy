import sys

with open('app/src/main/kotlin/com/example/musicfy/ui/player/MiniPlayer.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "isTransparent = isTransparent," in line:
        pass
    else:
        new_lines.append(line)

with open('app/src/main/kotlin/com/example/musicfy/ui/player/MiniPlayer.kt', 'w') as f:
    f.writelines(new_lines)
