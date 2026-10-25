package com.theveloper.pixelplay.data.equalizer

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

@Serializable
data class SubwooferCalibrationPoint(
    val frequencyHz: Int,
    val lastCleanDbfs: Float
)

@Serializable
data class SubwooferCalibrationProfile(
    val volumeStep: Int,
    val maxVolumeStep: Int,
    val outputRouteLabel: String = "Current output",
    val safetyMarginDb: Float = 4f,
    val points: List<SubwooferCalibrationPoint> = emptyList(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

object SubwooferCalibrationFrequencies {
    val centersHz: List<Int> = (0 until 16).map { index ->
        (20.0 * (300.0 / 20.0).pow(index / 15.0)).roundToInt()
    }
}
