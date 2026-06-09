import re

file_path = 'app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

layout_imports = {"Arrangement", "asPaddingValues", "Box", "BoxWithConstraints", "Column", "fillMaxSize", "fillMaxWidth", "height", "only", "padding", "PaddingValues", "Row", "size", "Spacer", "systemBars", "width", "WindowInsets", "WindowInsetsSides"}
shape_imports = {"CircleShape", "RoundedCornerShape"}
material3_imports = {"Button", "Card", "CardDefaults", "ContainedLoadingIndicator", "ExperimentalMaterial3Api", "ExperimentalMaterial3ExpressiveApi", "Icon", "IconButton", "MaterialTheme", "SnackbarHostState", "surfaceColorAtElevation", "Text"}
res_imports = {"painterResource", "stringResource"}

new_lines = []
for line in lines:
    m = re.match(r'^import ([A-Za-z0-9_]+)$', line.strip())
    if m:
        name = m.group(1)
        if name in layout_imports:
            new_lines.append(f"import androidx.compose.foundation.layout.{name}\n")
        elif name in shape_imports:
            new_lines.append(f"import androidx.compose.foundation.shape.{name}\n")
        elif name in material3_imports:
            new_lines.append(f"import androidx.compose.material3.{name}\n")
        elif name in res_imports:
            new_lines.append(f"import androidx.compose.ui.res.{name}\n")
        else:
            new_lines.append(line)
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
