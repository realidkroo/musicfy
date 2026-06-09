import re

file_path = "app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

imports_to_add = """
import androidx.compose.ui.text.font.FontWeight
import com.example.musicfy.ui.component.PlaylistGridItem
"""

# add it after `import androidx.compose.ui.Modifier`
content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\n" + imports_to_add)

with open(file_path, "w") as f:
    f.write(content)

print("Done fixing HomeScreen imports 2")
