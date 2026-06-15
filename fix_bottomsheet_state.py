import sys

with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'r') as f:
    lines = f.readlines()

new_state_code = """    var horizontalOffset by androidx.compose.runtime.mutableFloatStateOf(0f)

    suspend fun animateHorizontalOffsetTo(targetValue: Float) {
        androidx.compose.animation.core.Animatable(horizontalOffset).animateTo(
            targetValue = targetValue,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
        ) {
            horizontalOffset = value
        }
    }
"""

for i, line in enumerate(lines):
    if "val progress by derivedStateOf {" in line:
        lines.insert(i, new_state_code)
        break

imports = [
    "import androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.gestures.detectDragGestures\n"
]
for imp in imports:
    if imp not in lines:
        lines.insert(10, imp)

with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'w') as f:
    f.writelines(lines)

print("Fixed BottomSheetState!")
