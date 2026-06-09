import re

file_path = "app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add imports for PlaylistGridItem and FontWeight if they are missing
if "import com.example.musicfy.ui.component.PlaylistGridItem" not in content:
    content = content.replace("import com.example.musicfy.ui.component.GridItemPlaceHolder", "import com.example.musicfy.ui.component.GridItemPlaceHolder\nimport com.example.musicfy.ui.component.PlaylistGridItem")

if "import androidx.compose.ui.text.font.FontWeight" not in content:
    content = content.replace("import androidx.compose.ui.text.style.TextOverflow", "import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.text.font.FontWeight")

with open(file_path, "w") as f:
    f.write(content)

print("Done fixing HomeScreen imports")
