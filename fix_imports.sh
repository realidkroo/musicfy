#!/bin/bash
sed -i '' '37i\
import androidx.compose.ui.draw.drawWithCache\
import androidx.compose.ui.draw.drawWithContent\
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas\
' app/src/main/kotlin/com/example/musicfy/ui/screens/HomeScreen.kt
