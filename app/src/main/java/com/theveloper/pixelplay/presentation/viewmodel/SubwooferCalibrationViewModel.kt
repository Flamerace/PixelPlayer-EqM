package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.equalizer.CalibrationTonePlayer
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationFrequencies
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationManager
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationPoint
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationProfile
import com.theveloper.pixelplay.data.preferences.SubwooferCalibrationPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubwooferCalibrationUiState(
    val volumeStep: Int = 0,
    val maxVolumeStep: Int = 0,
    val frequencyIndex: Int = 0,
    val currentLevelDbfs: Float = CalibrationTonePlayer.START_LEVEL_DBFS,
    val isPlaying: Boolean = false,
    val hasStopped: Boolean = false,
    val outputRouteLabel: String = "Current output",
    val measurements: List<SubwooferCalibrationPoint> = emptyList()
) {
    val currentFrequencyHz: Int?
        get() = SubwooferCalibrationFrequencies.centersHz.getOrNull(frequencyIndex)
    val isComplete: Boolean
        get() = frequencyIndex >= SubwooferCalibrationFrequencies.centersHz.size
}

@HiltViewModel
class SubwooferCalibrationViewModel @Inject constructor(
    private val repository: SubwooferCalibrationPreferencesRepository,
    private val calibrationManager: SubwooferCalibrationManager,
    private val playerEngine: DualPlayerEngine,
    @ApplicationContext context: Context
) : ViewModel() {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tonePlayer = CalibrationTonePlayer()
    private val _uiState = MutableStateFlow(readInitialState())
    val uiState: StateFlow<SubwooferCalibrationUiState> = _uiState.asStateFlow()
    private var toneJob: Job? = null

    init {
        observeProfiles()
    }

    private fun readInitialState(): SubwooferCalibrationUiState {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return SubwooferCalibrationUiState(
            volumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolumeStep = max
        )
    }

    fun refreshVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        _uiState.update {
            it.copy(
                volumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolumeStep = max
            )
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            repository.profilesFlow.collect { profiles ->
                val state = _uiState.value
                val matching = profiles
                    .filter { it.volumeStep == state.volumeStep && it.maxVolumeStep == state.maxVolumeStep }
                    .maxByOrNull { it.updatedAtEpochMs }
                calibrationManager.setActiveProfile(matching)
                val points = matching?.points.orEmpty()
                val firstMissing = SubwooferCalibrationFrequencies.centersHz.indexOfFirst { hz ->
                    points.none { it.frequencyHz == hz }
                }.let { if (it < 0) SubwooferCalibrationFrequencies.centersHz.size else it }
                _uiState.update {
                    it.copy(
                        measurements = points,
                        frequencyIndex = if (it.frequencyIndex < points.size) it.frequencyIndex else firstMissing
                    )
                }
            }
        }
    }

    fun startTone() {
        if (toneJob?.isActive == true) return
        val frequency = _uiState.value.currentFrequencyHz ?: return
        toneJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentLevelDbfs = CalibrationTonePlayer.START_LEVEL_DBFS,
                    isPlaying = true,
                    hasStopped = false,
                    outputRouteLabel = "Current output"
                )
            }
            try {
                tonePlayer.playRamp(frequency, playerEngine.getAudioSessionId()) { level ->
                    _uiState.update {
                        it.copy(
                            currentLevelDbfs = level,
                            outputRouteLabel = tonePlayer.lastOutputRouteLabel
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isPlaying = false, hasStopped = true) }
            }
        }
    }

    fun stopTone() {
        tonePlayer.stop()
        toneJob?.cancel()
        _uiState.update { it.copy(isPlaying = false, hasStopped = true) }
    }

    fun saveCurrentPoint() {
        val state = _uiState.value
        val frequency = state.currentFrequencyHz ?: return
        if (!state.hasStopped) return
        viewModelScope.launch {
            val updated = repository.saveMeasurement(
                volumeStep = state.volumeStep,
                maxVolumeStep = state.maxVolumeStep,
                outputRouteLabel = tonePlayer.lastOutputRouteLabel,
                point = SubwooferCalibrationPoint(
                    frequencyHz = frequency,
                    lastCleanDbfs = state.currentLevelDbfs
                )
            )
            calibrationManager.setActiveProfile(updated)
            val nextIndex = (state.frequencyIndex + 1).coerceAtMost(
                SubwooferCalibrationFrequencies.centersHz.size
            )
            _uiState.update {
                it.copy(
                    frequencyIndex = nextIndex,
                    currentLevelDbfs = CalibrationTonePlayer.START_LEVEL_DBFS,
                    hasStopped = false,
                    measurements = updated.points,
                    outputRouteLabel = updated.outputRouteLabel
                )
            }
        }
    }

    fun skipFrequency() {
        stopTone()
        _uiState.update {
            it.copy(
                frequencyIndex = (it.frequencyIndex + 1).coerceAtMost(
                    SubwooferCalibrationFrequencies.centersHz.size
                ),
                currentLevelDbfs = CalibrationTonePlayer.START_LEVEL_DBFS,
                hasStopped = false
            )
        }
    }

    override fun onCleared() {
        tonePlayer.stop()
        toneJob?.cancel()
        super.onCleared()
    }
}
