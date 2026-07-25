package com.theveloper.pixelplay.data.equalizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the DynamicBassProcessor - a custom audio processor for enhanced bass
 * Integrates with ExoPlayer's audio pipeline
 */
@Singleton
class DynamicBassManager @Inject constructor() {
    
    companion object {
        private const val TAG = "DynamicBassManager"
    }
    
    private var dynamicBassProcessor: DynamicBassProcessor? = DynamicBassProcessor()
    private var currentSampleRate: Int = 44100
    
    // State flows for UI binding
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _bassGain = MutableStateFlow(0f) // 0-1.0 or dB range
    val bassGain: StateFlow<Float> = _bassGain.asStateFlow()
    
    private val _filterXLow = MutableStateFlow(20f)
    val filterXLow: StateFlow<Float> = _filterXLow.asStateFlow()
    
    private val _filterXHigh = MutableStateFlow(200f)
    val filterXHigh: StateFlow<Float> = _filterXHigh.asStateFlow()
    
    private val _filterYLow = MutableStateFlow(20f)
    val filterYLow: StateFlow<Float> = _filterYLow.asStateFlow()
    
    private val _filterYHigh = MutableStateFlow(200f)
    val filterYHigh: StateFlow<Float> = _filterYHigh.asStateFlow()
    
    private val _sideGainX = MutableStateFlow(0f)
    val sideGainX: StateFlow<Float> = _sideGainX.asStateFlow()
    
    private val _sideGainY = MutableStateFlow(0f)
    val sideGainY: StateFlow<Float> = _sideGainY.asStateFlow()
    
    /**
     * Initialize the DynamicBassProcessor with the audio format from the player
     */
    fun initializeProcessor(sampleRate: Int) {
        currentSampleRate = sampleRate
        
        if (dynamicBassProcessor != null) {
            Timber.tag(TAG).d("DynamicBassProcessor already initialized")
            return
        }
        
        try {
            dynamicBassProcessor = DynamicBassProcessor(sampleRate).apply {
                setEnabled(_isEnabled.value)
                setBassGain(_bassGain.value)
                setFilterX(_filterXLow.value, _filterXHigh.value)
                setFilterY(_filterYLow.value, _filterYHigh.value)
                setSideGain(_sideGainX.value, _sideGainY.value)
            }
            Timber.tag(TAG).d("DynamicBassProcessor initialized with sample rate: $sampleRate Hz")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize DynamicBassProcessor")
        }
    }
    
    /**
     * Get the processor instance to add to ExoPlayer
     */
    fun getProcessor(): DynamicBassProcessor? = dynamicBassProcessor
    
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        dynamicBassProcessor?.setEnabled(enabled)
        Timber.tag(TAG).d("DynamicBass enabled: $enabled")
    }
    
    fun setBassGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        _bassGain.value = clamped
        dynamicBassProcessor?.setBassGain(clamped * 100f)
        Timber.tag(TAG).d("DynamicBass gain set to: $clamped")
    }
    
    fun setFilterXPassFrequency(low: Float, high: Float) {
        _filterXLow.value = low
        _filterXHigh.value = high
        dynamicBassProcessor?.setFilterX(low, high)
        Timber.tag(TAG).d("Filter X set to: $low - $high Hz")
    }
    
    fun setFilterYPassFrequency(low: Float, high: Float) {
        _filterYLow.value = low
        _filterYHigh.value = high
        dynamicBassProcessor?.setFilterY(low, high)
        Timber.tag(TAG).d("Filter Y set to: $low - $high Hz")
    }
    
    fun setSideGain(gx: Float, gy: Float) {
        _sideGainX.value = gx
        _sideGainY.value = gy
        dynamicBassProcessor?.setSideGain(gx, gy)
        Timber.tag(TAG).d("Side gain set to X: $gx, Y: $gy")
    }
    
    fun release() {
        dynamicBassProcessor = null
        Timber.tag(TAG).d("DynamicBassProcessor released")
    }
}