package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.equalizer.DynamicBassManager
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.preferences.EqualizerViewMode
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json // Added import

data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevels: List<Int> = List(10) { 0 },
    val editingPresetName: String? = null,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0f, // Changed to Float
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0f, // Changed to Float
    val loudnessEnhancerEnabled: Boolean = false,
    val loudnessEnhancerStrength: Float = 0f, // Changed to Float
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true,
    val isLoudnessEnhancerSupported: Boolean = true,
    val viewMode: EqualizerViewMode = EqualizerViewMode.SLIDERS,
    val isBassBoostDismissed: Boolean = false,
    val isVirtualizerDismissed: Boolean = false,
    val isLoudnessDismissed: Boolean = false,
    val customPresets: List<EqualizerPreset> = emptyList(), // Added
    val pinnedPresetsNames: List<String> = emptyList(), // Added
    // DynamicBass state
    val dynamicBassEnabled: Boolean = false,
    val dynamicBassBassGain: Float = 0.5f,
    val dynamicBassFilterXLow: Float = 50f,
    val dynamicBassFilterXHigh: Float = 250f,
    val dynamicBassFilterYLow: Float = 20f,
    val dynamicBassFilterYHigh: Float = 200f,
    val dynamicBassSideGainX: Float = 0f,
    val dynamicBassSideGainY: Float = 0f,
    // StereoExpand state
    val stereoWidenerEnabled: Boolean = false,
    val stereoWidth: Float = 1.0f,          // 1.0 = 100%
    val stereoBassProtectFreq: Float = 200f,
    // SurroundSound state
    val surroundEnabled: Boolean = false,
    val headTrackingEnabled: Boolean = false,
    val headTrackingSmoothing: Float = 0.85f,
    val surroundBassAngle: Float = 15f,
    val surroundBassDistance: Float = 1.5f,
    val surroundMidAngle: Float = 25f,
    val surroundMidDistance: Float = 1.5f,
    val surroundTrebleAngle: Float = 30f,
    val surroundTrebleDistance: Float = 1.5f,
    val surroundCrossoverBassMid: Float = 250f,
    val surroundCrossoverMidTreble: Float = 4000f,
) {
    // Computed property for accessible presets (Pinned)
    val accessiblePresets: List<EqualizerPreset>
        get() {
            // Map pinned names to actual Presets (Default or Custom)
            return pinnedPresetsNames.mapNotNull { name ->
                // First check custom presets
                customPresets.find { it.name == name }
                    ?: EqualizerPreset.fromName(name) // Then standard defaults
            }
        }
        
    // Computed property for All Available Presets (for Edit Sheet)
    val allAvailablePresets: List<EqualizerPreset>
        get() = EqualizerPreset.ALL_PRESETS + customPresets
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val dynamicBassManager: DynamicBassManager,
    private val dualPlayerEngine: DualPlayerEngine,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "EqualizerViewModel"
        private const val SLIDER_PERSIST_DEBOUNCE_MS = 150L
        private val json = Json { ignoreUnknownKeys = true } // Assuming Json is needed
    }

    private val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    
    // UI-only state for view mode
    // UI-only state for view mode - Now persisted
    // val isGraphView: StateFlow<Boolean> = _isGraphView.asStateFlow() // Removed local state

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private val _systemVolume = MutableStateFlow(0f)
    val systemVolume: StateFlow<Float> = _systemVolume.asStateFlow()

    val headYaw: StateFlow<Float> = dynamicBassManager.headYaw

    val dynamicBassLevel: StateFlow<Float> = flow {
        while (true) {
            emit(dynamicBassManager.getBassActivityLevel())
            delay(33) // ~30fps poll of one Volatile Float; only runs while a collector exists
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 0f)
    
    private var persistBandLevelsJob: Job? = null
    private var persistBassBoostJob: Job? = null
    private var persistVirtualizerJob: Job? = null
    private var persistLoudnessJob: Job? = null
    private var persistDynamicBassJob: Job? = null
    
    init {
        initializeEqualizer()
        observeEqualizerState()
        loadSystemVolume()
    }
    
    private fun loadSystemVolume() {
        try {
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            _systemVolume.value = if (max > 0) current.toFloat() / max.toFloat() else 0.5f
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load system volume")
        }
    }

    fun setSystemVolume(percent: Float) {
        viewModelScope.launch {
            try {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (percent * max).roundToInt().coerceIn(0, max)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0) // flag 0 to not show system UI
                _systemVolume.value = percent
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to set system volume")
            }
        }
    }
    
    private fun initializeEqualizer() {
        viewModelScope.launch {
            Timber.tag(TAG).d("Initializing equalizer...")
            
            if (!equalizerManager.isAttached) {
                val enabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
                val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
                val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
                val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
                val bassBoost = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
                val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
                val virtualizer = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
                val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
                val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()
                
                equalizerManager.restoreState(
                    enabled, presetName, customBands, 
                    bassBoostEnabled, bassBoost, 
                    virtualizerEnabled, virtualizer,
                    loudnessEnabled, loudnessStrength
                )
                
                val initialSessionId = dualPlayerEngine.getAudioSessionId()
                if (initialSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(initialSessionId)
                }
            } else {
                Timber.tag(TAG).d("Equalizer already attached by service, skipping restore.")
            }
            
            // Initialize DynamicBass
            val sampleRate = dualPlayerEngine.currentAudioFormatSnapshot()?.sampleRate ?: 44100
            dynamicBassManager.initializeProcessor(sampleRate)
            
            // Update UI state with device capabilities
            _uiState.value = _uiState.value.copy(
                isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
            )

            dualPlayerEngine.activeAudioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    Timber.tag(TAG).d("Audio Session ID changed to $sessionId.")
                    _uiState.value = _uiState.value.copy(
                        isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                        isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                        isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
                    )
                }
            }
        }
    }
    
    private fun observeEqualizerState() {
        viewModelScope.launch {
            // Combine flows for UI State
            combine(
                equalizerPreferencesRepository.equalizerEnabledFlow,
                equalizerPreferencesRepository.equalizerPresetFlow,
                equalizerPreferencesRepository.equalizerCustomBandsFlow,
                equalizerPreferencesRepository.bassBoostEnabledFlow,
                equalizerPreferencesRepository.bassBoostStrengthFlow,
                equalizerPreferencesRepository.virtualizerEnabledFlow,
                equalizerPreferencesRepository.virtualizerStrengthFlow,
                equalizerPreferencesRepository.loudnessEnhancerEnabledFlow,
                equalizerPreferencesRepository.loudnessEnhancerStrengthFlow,
                equalizerPreferencesRepository.bassBoostDismissedFlow,
                equalizerPreferencesRepository.virtualizerDismissedFlow,
                equalizerPreferencesRepository.loudnessDismissedFlow,
                equalizerPreferencesRepository.equalizerViewModeFlow,
                equalizerPreferencesRepository.customPresetsFlow, // Added
                equalizerPreferencesRepository.pinnedPresetsFlow, // Added
                equalizerPreferencesRepository.dynamicBassEnabledFlow,
                equalizerPreferencesRepository.dynamicBassBassGainFlow,
                equalizerPreferencesRepository.dynamicBassFilterXLowFlow,
                equalizerPreferencesRepository.dynamicBassFilterXHighFlow,
                equalizerPreferencesRepository.dynamicBassFilterYLowFlow,
                equalizerPreferencesRepository.dynamicBassFilterYHighFlow,
                equalizerPreferencesRepository.dynamicBassSideGainXFlow,
                equalizerPreferencesRepository.dynamicBassSideGainYFlow,
                equalizerPreferencesRepository.stereoWidenerEnabledFlow,
                equalizerPreferencesRepository.surroundEnabledFlow,
                equalizerPreferencesRepository.stereoWidthFlow,
                equalizerPreferencesRepository.stereoBassProtectFreqFlow,
                equalizerPreferencesRepository.headTrackingEnabledFlow,
                equalizerPreferencesRepository.headTrackingSmoothingFlow,
                equalizerPreferencesRepository.surroundBassAngleFlow,
                equalizerPreferencesRepository.surroundBassDistanceFlow,
                equalizerPreferencesRepository.surroundMidAngleFlow,
                equalizerPreferencesRepository.surroundMidDistanceFlow,
                equalizerPreferencesRepository.surroundTrebleAngleFlow,
                equalizerPreferencesRepository.surroundTrebleDistanceFlow,
                equalizerPreferencesRepository.surroundCrossoverBassMidFlow,
                equalizerPreferencesRepository.surroundCrossoverMidTrebleFlow
            ) { values -> // Too many args for standard destructuring, use array/list access
                 val enabled = values[0] as Boolean
                 val presetName = values[1] as String
                 val customBands = (values[2] as? List<*>)
                     ?.mapNotNull { (it as? Number)?.toInt() }
                     ?: emptyList()
                 val bbEnabled = values[3] as Boolean
                 val bbStrength = values[4] as Int
                 val vEnabled = values[5] as Boolean
                 val vStrength = values[6] as Int
                 val lEnabled = values[7] as Boolean
                 val lStrength = values[8] as Int
                 val bbDismissed = values[9] as Boolean
                 val vDismissed = values[10] as Boolean
                 val lDismissed = values[11] as Boolean
                 val viewMode = values[12] as EqualizerViewMode
                 val customPresets = (values[13] as? List<*>)?.filterIsInstance<EqualizerPreset>() ?: emptyList()
                 val pinnedPresets = (values[14] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                 // DynamicBass values
                 val dbEnabled = values[15] as Boolean
                 val dbGain = values[16] as Float
                 val dbFilterXLow = values[17] as Float
                 val dbFilterXHigh = values[18] as Float
                 val dbFilterYLow = values[19] as Float
                 val dbFilterYHigh = values[20] as Float
                 val dbSideGainX = values[21] as Float
                 val dbSideGainY = values[22] as Float
                 // StereoExpand values
                 val seEnabled = values[23] as Boolean
                 val stWidth = values[25] as Float
                 val stBassProtect = values[26] as Float
                 // SurroundSound values
                 val ssEnabled = values[24] as Boolean
                 val htEnabled = values[27] as Boolean
                 val htSmoothing = values[28] as Float
                 val srBassAngle = values[29] as Float
                 val srBassDistance = values[30] as Float
                 val srMidAngle = values[31] as Float
                 val srMidDistance = values[32] as Float
                 val srTrebleAngle = values[33] as Float
                 val srTrebleDistance = values[34] as Float
                 val srCrossBassMid = values[35] as Float
                 val srCrossMidTreble = values[36] as Float

                val currentPreset = if (presetName == "custom") {
                    EqualizerPreset.custom(customBands)
                } else {
                     // Check custom presets first
                     customPresets.find { it.name == presetName }
                        ?: EqualizerPreset.fromName(presetName)
                }

                EqualizerUiState(
                    isEnabled = enabled,
                    currentPreset = currentPreset,
                    bandLevels = if (currentPreset.name == "custom") customBands else currentPreset.bandLevels,
                    editingPresetName = _uiState.value.editingPresetName,
                    bassBoostEnabled = bbEnabled,
                    bassBoostStrength = bbStrength.toFloat(), // Raw 0-1000
                    virtualizerEnabled = vEnabled,
                    virtualizerStrength = vStrength.toFloat(), // Raw 0-1000
                    loudnessEnhancerEnabled = lEnabled,
                    loudnessEnhancerStrength = lStrength.toFloat(), // Raw 0-1000
                    isBassBoostDismissed = bbDismissed,
                    isVirtualizerDismissed = vDismissed,
                    isLoudnessDismissed = lDismissed,
                    viewMode = viewMode,
                    // New State
                    customPresets = customPresets,
                    pinnedPresetsNames = pinnedPresets,
                    // Capabilities (Keep existing values)
                    isBassBoostSupported = _uiState.value.isBassBoostSupported,
                    isVirtualizerSupported = _uiState.value.isVirtualizerSupported,
                    isLoudnessEnhancerSupported = _uiState.value.isLoudnessEnhancerSupported,
                    // DynamicBass state
                    dynamicBassEnabled = dbEnabled,
                    dynamicBassBassGain = dbGain,
                    dynamicBassFilterXLow = dbFilterXLow,
                    dynamicBassFilterXHigh = dbFilterXHigh,
                    dynamicBassFilterYLow = dbFilterYLow,
                    dynamicBassFilterYHigh = dbFilterYHigh,
                    dynamicBassSideGainX = dbSideGainX,
                    dynamicBassSideGainY = dbSideGainY,
                    // StereoExpand state
                    stereoWidenerEnabled = seEnabled,
                    stereoWidth = stWidth,
                    stereoBassProtectFreq = stBassProtect,
                    // SurroundSound state
                    surroundEnabled = ssEnabled,
                    headTrackingEnabled = htEnabled,
                    headTrackingSmoothing = htSmoothing,
                    surroundBassAngle = srBassAngle,
                    surroundBassDistance = srBassDistance,
                    surroundMidAngle = srMidAngle,
                    surroundMidDistance = srMidDistance,
                    surroundTrebleAngle = srTrebleAngle,
                    surroundTrebleDistance = srTrebleDistance,
                    surroundCrossoverBassMid = srCrossBassMid,
                    surroundCrossoverMidTreble = srCrossMidTreble
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun cycleViewMode() {
        viewModelScope.launch {
            val currentMode = _uiState.value.viewMode
            val nextMode = when (currentMode) {
                EqualizerViewMode.SLIDERS -> EqualizerViewMode.GRAPH
                EqualizerViewMode.GRAPH -> EqualizerViewMode.HYBRID
                EqualizerViewMode.HYBRID -> EqualizerViewMode.SLIDERS
            }
            equalizerPreferencesRepository.setEqualizerViewMode(nextMode)
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        _uiState.update { current ->
            current.copy(isEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setEqualizerEnabled(enabled)
        }
    }

    fun toggleEqualizer() {
        setEnabled(!_uiState.value.isEnabled)
    }
    
    fun selectPreset(preset: EqualizerPreset) {
        persistBandLevelsJob?.cancel()
        equalizerManager.applyPreset(preset)
        _uiState.update { current ->
            current.copy(
                currentPreset = preset,
                bandLevels = preset.bandLevels,
                editingPresetName = null
            )
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.setEqualizerPreset(preset.name)
            if (!preset.isCustom) {
                equalizerPreferencesRepository.setEqualizerCustomBands(preset.bandLevels)
            }
        }
    }
    
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in _uiState.value.bandLevels.indices) return
        val clampedLevel = level.coerceIn(-15, 15)

        equalizerManager.setBandLevel(bandIndex, clampedLevel)
        val updatedBands = equalizerManager.bandLevels.value
        _uiState.update { current ->
            val editingName = current.editingPresetName
                ?: current.currentPreset.name.takeIf { current.currentPreset.isCustom && it != "custom" }
            current.copy(
                currentPreset = EqualizerPreset.custom(updatedBands),
                bandLevels = updatedBands,
                editingPresetName = editingName
            )
        }

        persistBandLevelsJob?.cancel()
        persistBandLevelsJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setEqualizerCustomBands(updatedBands)
            equalizerPreferencesRepository.setEqualizerPreset("custom")
        }
    }
    
    fun saveCurrentAsCustomPreset(name: String) {
        viewModelScope.launch {
            // Create preset from current custom bands
            val bands = equalizerManager.bandLevels.value
            val preset = EqualizerPreset(name, name, bands, true)
            equalizerPreferencesRepository.saveCustomPreset(preset)
            
            // Also pin it automatically
            togglePinPreset(name)
            
            // Select it
            selectPreset(preset)
        }
    }
    
    fun deleteCustomPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            equalizerPreferencesRepository.deleteCustomPreset(preset.name)
            // If deleting current, revert to Flat
            if (_uiState.value.currentPreset.name == preset.name) {
                selectPreset(EqualizerPreset.FLAT)
            }
        }
    }
    
    fun renameCustomPreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            equalizerPreferencesRepository.renameCustomPreset(oldName, newName)
        }
    }
    
    fun updateCustomPresetBands(presetName: String) {
        viewModelScope.launch {
            val bands = equalizerManager.bandLevels.value
            equalizerPreferencesRepository.updateCustomPresetBands(presetName, bands)
            selectPreset(EqualizerPreset(presetName, presetName, bands, true))
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        equalizerManager.setBassBoostEnabled(enabled)
        _uiState.update { current ->
            current.copy(bassBoostEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setBassBoostEnabled(enabled)
        }
    }
    
    fun setBassBoostStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setBassBoostStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(bassBoostStrength = clampedStrength.toFloat())
        }

        persistBassBoostJob?.cancel()
        persistBassBoostJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setBassBoostStrength(clampedStrength)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        equalizerManager.setVirtualizerEnabled(enabled)
        _uiState.update { current ->
            current.copy(virtualizerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setVirtualizerEnabled(enabled)
        }
    }
    
    fun setVirtualizerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setVirtualizerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(virtualizerStrength = clampedStrength.toFloat())
        }

        persistVirtualizerJob?.cancel()
        persistVirtualizerJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setVirtualizerStrength(clampedStrength)
        }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        equalizerManager.setLoudnessEnhancerEnabled(enabled)
        _uiState.update { current ->
            current.copy(loudnessEnhancerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setLoudnessEnhancerEnabled(enabled)
        }
    }

    fun setLoudnessEnhancerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setLoudnessEnhancerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(loudnessEnhancerStrength = clampedStrength.toFloat())
        }

        persistLoudnessJob?.cancel()
        persistLoudnessJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setLoudnessEnhancerStrength(clampedStrength)
        }
    }

    fun setBassBoostDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setBassBoostDismissed(dismissed)
        }
    }

    fun setVirtualizerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setVirtualizerDismissed(dismissed)
        }
    }

    fun setLoudnessDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setLoudnessDismissed(dismissed)
        }
    }
    
    fun updatePinnedPresetsOrder(newOrder: List<String>) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setPinnedPresets(newOrder)
        }
    }
    
    fun resetPinnedPresetsToDefault() {
        viewModelScope.launch {
            // Reset to default order: all standard presets visible, in original order
            val defaultOrder = EqualizerPreset.ALL_PRESETS.map { it.name }
            equalizerPreferencesRepository.setPinnedPresets(defaultOrder)
        }
    }
    
    fun togglePinPreset(presetName: String) {
        viewModelScope.launch {
            val currentPinned = _uiState.value.pinnedPresetsNames.toMutableList()
            if (currentPinned.contains(presetName)) {
                currentPinned.remove(presetName)
            } else {
                currentPinned.add(presetName)
            }
            equalizerPreferencesRepository.setPinnedPresets(currentPinned)
        }
    }

    // DynamicBass control methods
    fun setDynamicBassEnabled(enabled: Boolean) {
        dynamicBassManager.setEnabled(enabled)
        _uiState.update { current ->
            current.copy(dynamicBassEnabled = enabled)
        }
        viewModelScope.launch {
            val sampleRate = dualPlayerEngine.currentAudioFormatSnapshot()?.sampleRate ?: 44100
            dynamicBassManager.initializeProcessor(sampleRate)
            equalizerPreferencesRepository.setDynamicBassEnabled(enabled)
        }
    }

    fun setDynamicBassBassGain(gain: Float) {
        val clampedGain = gain.coerceIn(0f, 1f)
        dynamicBassManager.setBassGain(clampedGain)
        _uiState.update { current ->
            current.copy(dynamicBassBassGain = clampedGain)
        }

        persistDynamicBassJob?.cancel()
        persistDynamicBassJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setDynamicBassBassGain(clampedGain)
        }
    }

    fun setDynamicBassFilterX(low: Float, high: Float) {
        dynamicBassManager.setFilterXPassFrequency(low, high)
        _uiState.update { current ->
            current.copy(dynamicBassFilterXLow = low, dynamicBassFilterXHigh = high)
        }

        persistDynamicBassJob?.cancel()
        persistDynamicBassJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setDynamicBassFilterX(low, high)
        }
    }

    fun setDynamicBassFilterY(low: Float, high: Float) {
        dynamicBassManager.setFilterYPassFrequency(low, high)
        _uiState.update { current ->
            current.copy(dynamicBassFilterYLow = low, dynamicBassFilterYHigh = high)
        }

        persistDynamicBassJob?.cancel()
        persistDynamicBassJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setDynamicBassFilterY(low, high)
        }
    }

    fun setDynamicBassSideGain(gx: Float, gy: Float) {
        dynamicBassManager.setSideGain(gx, gy)
        _uiState.update { current ->
            current.copy(dynamicBassSideGainX = gx, dynamicBassSideGainY = gy)
        }

        persistDynamicBassJob?.cancel()
        persistDynamicBassJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setDynamicBassSideGain(gx, gy)
        }
    }

    // StereoExpand control methods

    fun setStereoWidenerEnabled(enabled: Boolean) {
        dynamicBassManager.setStereoEnabled(enabled)
        _uiState.update { current ->
            current.copy(stereoWidenerEnabled = enabled)
        }
        viewModelScope.launch {
            val sampleRate = dualPlayerEngine.currentAudioFormatSnapshot()?.sampleRate ?: 44100
            dynamicBassManager.initializeProcessor(sampleRate)
            equalizerPreferencesRepository.setStereoWidenerEnabled(enabled)
        }
    }

    fun setStereoWidth(width: Float) {
        val clamped = width.coerceIn(0f, 2f)
        dynamicBassManager.setStereoWidth(clamped * 100f) // processor's API is percent-based
        _uiState.update { it.copy(stereoWidth = clamped) }
        viewModelScope.launch { equalizerPreferencesRepository.setStereoWidth(clamped) }
    }

    fun setStereoBassProtectFreq(freqHz: Float) {
        dynamicBassManager.setStereoBassProtectFrequency(freqHz)
        _uiState.update { it.copy(stereoBassProtectFreq = freqHz) }
        viewModelScope.launch { equalizerPreferencesRepository.setStereoBassProtectFreq(freqHz) }
    }

    // SurroundSound control method

    fun setSurroundEnabled(enabled: Boolean) {
        dynamicBassManager.setSurroundEnabled(enabled)
        _uiState.update { current ->
            current.copy(surroundEnabled = enabled)
        }
        viewModelScope.launch {
            val sampleRate = dualPlayerEngine.currentAudioFormatSnapshot()?.sampleRate ?: 44100
            dynamicBassManager.initializeProcessor(sampleRate)
            equalizerPreferencesRepository.setSurroundEnabled(enabled)
        }
    }

    fun setHeadTrackingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(headTrackingEnabled = enabled) }
        if (enabled) dynamicBassManager.startHeadTracking { } else dynamicBassManager.stopHeadTracking()
        viewModelScope.launch { equalizerPreferencesRepository.setHeadTrackingEnabled(enabled) }
    }

    fun setHeadTrackingSmoothing(factor: Float) {
        val clamped = factor.coerceIn(0.1f, 1f)
        dynamicBassManager.setHeadTrackingSmoothing(clamped)
        _uiState.update { it.copy(headTrackingSmoothing = clamped) }
        viewModelScope.launch { equalizerPreferencesRepository.setHeadTrackingSmoothing(clamped) }
    }

    fun setSurroundBassPlacement(angleDegrees: Float, distanceMeters: Float) {
        dynamicBassManager.setSurroundBassPlacement(angleDegrees, distanceMeters)
        _uiState.update { it.copy(surroundBassAngle = angleDegrees, surroundBassDistance = distanceMeters) }
        viewModelScope.launch { equalizerPreferencesRepository.setSurroundBassPlacement(angleDegrees, distanceMeters) }
    }

    fun setSurroundMidPlacement(angleDegrees: Float, distanceMeters: Float) {
        dynamicBassManager.setSurroundMidPlacement(angleDegrees, distanceMeters)
        _uiState.update { it.copy(surroundMidAngle = angleDegrees, surroundMidDistance = distanceMeters) }
        viewModelScope.launch { equalizerPreferencesRepository.setSurroundMidPlacement(angleDegrees, distanceMeters) }
    }

    fun setSurroundTreblePlacement(angleDegrees: Float, distanceMeters: Float) {
        dynamicBassManager.setSurroundTreblePlacement(angleDegrees, distanceMeters)
        _uiState.update { it.copy(surroundTrebleAngle = angleDegrees, surroundTrebleDistance = distanceMeters) }
        viewModelScope.launch { equalizerPreferencesRepository.setSurroundTreblePlacement(angleDegrees, distanceMeters) }
    }

    fun setSurroundCrossovers(bassMidHz: Float, midTrebleHz: Float) {
        dynamicBassManager.setSurroundCrossovers(bassMidHz, midTrebleHz)
        _uiState.update { it.copy(surroundCrossoverBassMid = bassMidHz, surroundCrossoverMidTreble = midTrebleHz) }
        viewModelScope.launch { equalizerPreferencesRepository.setSurroundCrossovers(bassMidHz, midTrebleHz) }
    }
    
    
    /**
     * Reattaches the equalizer to a new audio session.
     * Call this when the player swaps during crossfade.
     */
    fun reattachToPlayer() {
        viewModelScope.launch {
            val audioSessionId = dualPlayerEngine.getAudioSessionId()
            Timber.tag(TAG).d("Reattaching equalizer to new audio session: $audioSessionId")
            equalizerManager.attachToAudioSessionIfNeeded(audioSessionId)
        }
    }

    private fun persistLatestStateAsync() {
        val latest = _uiState.value
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                equalizerPreferencesRepository.setEqualizerEnabled(latest.isEnabled)
                equalizerPreferencesRepository.setEqualizerPreset(latest.currentPreset.name)
                equalizerPreferencesRepository.setEqualizerCustomBands(equalizerManager.bandLevels.value)
                equalizerPreferencesRepository.setBassBoostEnabled(latest.bassBoostEnabled)
                equalizerPreferencesRepository.setBassBoostStrength(latest.bassBoostStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setVirtualizerEnabled(latest.virtualizerEnabled)
                equalizerPreferencesRepository.setVirtualizerStrength(latest.virtualizerStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setLoudnessEnhancerEnabled(latest.loudnessEnhancerEnabled)
                equalizerPreferencesRepository.setLoudnessEnhancerStrength(latest.loudnessEnhancerStrength.toInt().coerceIn(0, 1000))
                // DynamicBass persistence
                equalizerPreferencesRepository.setDynamicBassEnabled(latest.dynamicBassEnabled)
                equalizerPreferencesRepository.setDynamicBassBassGain(latest.dynamicBassBassGain)
                equalizerPreferencesRepository.setDynamicBassFilterX(latest.dynamicBassFilterXLow, latest.dynamicBassFilterXHigh)
                equalizerPreferencesRepository.setDynamicBassFilterY(latest.dynamicBassFilterYLow, latest.dynamicBassFilterYHigh)
                equalizerPreferencesRepository.setDynamicBassSideGain(latest.dynamicBassSideGainX, latest.dynamicBassSideGainY)
                // StereoExpand presistance
                equalizerPreferencesRepository.setStereoWidenerEnabled(latest.stereoWidenerEnabled)
                equalizerPreferencesRepository.setSurroundEnabled(latest.surroundEnabled)
                equalizerPreferencesRepository.setStereoWidth(latest.stereoWidth)
                equalizerPreferencesRepository.setStereoBassProtectFreq(latest.stereoBassProtectFreq)                
                equalizerPreferencesRepository.setHeadTrackingEnabled(latest.headTrackingEnabled)
                equalizerPreferencesRepository.setHeadTrackingSmoothing(latest.headTrackingSmoothing)
                equalizerPreferencesRepository.setSurroundBassPlacement(latest.surroundBassAngle, latest.surroundBassDistance)
                equalizerPreferencesRepository.setSurroundMidPlacement(latest.surroundMidAngle, latest.surroundMidDistance)
                equalizerPreferencesRepository.setSurroundTreblePlacement(latest.surroundTrebleAngle, latest.surroundTrebleDistance)
                equalizerPreferencesRepository.setSurroundCrossovers(latest.surroundCrossoverBassMid, latest.surroundCrossoverMidTreble)
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to flush equalizer state during onCleared")
            }
        }
    }
    
    override fun onCleared() {
        persistBandLevelsJob?.cancel()
        persistBassBoostJob?.cancel()
        persistVirtualizerJob?.cancel()
        persistLoudnessJob?.cancel()
        persistDynamicBassJob?.cancel()
        persistLatestStateAsync()
        super.onCleared()
        // Don't release equalizer here - it should persist across screen navigation
        Timber.tag(TAG).d("ViewModel cleared")
    }
}
