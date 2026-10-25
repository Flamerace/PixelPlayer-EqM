package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationFrequencies
import com.theveloper.pixelplay.presentation.viewmodel.SubwooferCalibrationViewModel
import java.util.Locale

@Composable
fun SubwooferCalibrationScreen(
    navController: NavController,
    viewModel: SubwooferCalibrationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshVolume() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopTone() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
        Text("Subwoofer calibration", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Set your media volume and output device first, then keep them fixed during this calibration. " +
                "Turn off Bass Boost, Dynamic Bass, Loudness Enhancer, and EQ boosts before measuring."
        )
        Text(
            "The tone starts quietly and rises in 1 dB steps. Stop as soon as you hear the rattle; " +
                "the saved correction keeps a 4 dB margin."
        )
        Text(
            "Media volume step: " + state.volumeStep + " / " + state.maxVolumeStep
        )
        Text("Output: " + state.outputRouteLabel)

        val frequency = state.currentFrequencyHz
        if (frequency == null) {
            Text(
                "Calibration complete. The saved low-frequency profile is active at this media-volume step.",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            val position = state.frequencyIndex + 1
            Text(
                "Frequency " + position + " of " +
                    SubwooferCalibrationFrequencies.centersHz.size + ": " + frequency + " Hz",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Test level: " + String.format(Locale.getDefault(), "%.0f dBFS", state.currentLevelDbfs)
            )

            if (state.isPlaying) {
                Button(onClick = { viewModel.stopTone() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop tone")
                }
            } else {
                Button(onClick = { viewModel.startTone() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Play and ramp tone")
                }
            }

            Button(
                onClick = { viewModel.saveCurrentPoint() },
                enabled = state.hasStopped,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save last clean level and continue")
            }
            OutlinedButton(
                onClick = { viewModel.skipFrequency() },
                enabled = !state.isPlaying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip frequency")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Saved measurements", style = MaterialTheme.typography.titleMedium)
        if (state.measurements.isEmpty()) {
            Text("No measurements saved for this volume step yet.")
        } else {
            state.measurements.forEach { point ->
                Text(
                    point.frequencyHz.toString() + " Hz — " +
                        String.format(Locale.getDefault(), "%.0f dBFS", point.lastCleanDbfs)
                )
            }
        }
    }
}
