// EqualizerScreen.kt
// First-ever UI for musicfy's existing custom 10-band biquad equalizer (the DSP itself,
// CustomEqualizerAudioProcessor, was already wired into every ExoPlayer instance in
// MusicService — it just had no settings screen, only a menu entry that pointed at an
// unregistered "equalizer" route). Standard vertical-slider-per-band layout.

package com.example.musicfy.ui.screens.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.viewmodels.EqualizerBandFrequencies
import com.example.musicfy.viewmodels.EqualizerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back_ios), contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::reset) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Enable equalizer",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EqualizerBandFrequencies.forEachIndexed { index, frequency ->
                    val gain = viewModel.bandGains[index]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = "${if (gain > 0) "+" else ""}${gain.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = gain,
                                onValueChange = { viewModel.setBandGain(index, it) },
                                valueRange = -12f..12f,
                                enabled = enabled,
                                modifier = Modifier.verticalSliderLayout()
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = frequencyLabel(frequency),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun frequencyLabel(hz: Double): String =
    if (hz >= 1000.0) "${(hz / 1000.0).let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }}k" else hz.toInt().toString()

/**
 * Turns a horizontal [Slider] into a vertical one: measure it as if the available width/
 * height were swapped, then rotate the result 270°. This is the standard Compose recipe for
 * a vertical slider since there's no built-in one.
 */
private fun Modifier.verticalSliderLayout(): Modifier = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin.Center
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
            )
        }
    }
