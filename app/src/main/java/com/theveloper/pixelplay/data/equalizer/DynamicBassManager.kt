/*package com.theveloper.pixelplay.data.equalizer

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

    // Created eagerly (not lazily on first format) so it's already present
    // when DualPlayerEngine builds the audio sink's processor chain.
    // Its internal engine is (re)configured for the real sample rate
    // automatically via onConfigure() once the format is known.
    private var dynamicBassProcessor: DynamicBassProcessor? = DynamicBassProcessor()

    // State flows for UI binding
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bassGain = MutableStateFlow(0f) // 0-1.0 fraction (UI-facing)
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

    fun initializeProcessor(sampleRate: Int) {
        Timber.tag(TAG).d("initializeProcessor($sampleRate Hz) called - no-op, handled via onConfigure()")
    }

    /**
     * Get the processor instance to add to ExoPlayer's audio sink.
     * Always non-null once this manager is constructed.
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
        // Engine expects a 0-100 percent value; UI/state store a 0-1 fraction.
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
}*/


package com.theveloper.pixelplay.data.equalizer

import android.content.Context
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
class DynamicBassManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DynamicBassManager"
    }

    // Created eagerly (not lazily on first format) so it's already present
    // when DualPlayerEngine builds the audio sink's processor chain.
    // Its internal engine is (re)configured for the real sample rate
    // automatically via onConfigure() once the format is known.
    private var dynamicBassProcessor: DynamicBassProcessor? = DynamicBassProcessor()

    private var headTracker: HeadOrientationTracker? = null
    
    private var headTrackingSmoothing = 0.85f
    
    // State flows for UI binding
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bassGain = MutableStateFlow(0.45f) // 0-1.0 fraction (UI-facing) — matches reference dBassGain=45
    val bassGain: StateFlow<Float> = _bassGain.asStateFlow()

    private val _filterXLow = MutableStateFlow(1200f)
    val filterXLow: StateFlow<Float> = _filterXLow.asStateFlow()

    private val _filterXHigh = MutableStateFlow(6200f)
    val filterXHigh: StateFlow<Float> = _filterXHigh.asStateFlow()

    private val _filterYLow = MutableStateFlow(40f)
    val filterYLow: StateFlow<Float> = _filterYLow.asStateFlow()

    private val _filterYHigh = MutableStateFlow(80f)
    val filterYHigh: StateFlow<Float> = _filterYHigh.asStateFlow()

    private val _sideGainX = MutableStateFlow(0f)
    val sideGainX: StateFlow<Float> = _sideGainX.asStateFlow()

    private val _sideGainY = MutableStateFlow(30f)
    val sideGainY: StateFlow<Float> = _sideGainY.asStateFlow()

    init {
        // Push the tuned defaults into the processor immediately — otherwise they
        // only apply once the UI happens to call each setter.
        dynamicBassProcessor?.setBassGain(_bassGain.value * 100f)
        dynamicBassProcessor?.setFilterX(_filterXLow.value, _filterXHigh.value)
        dynamicBassProcessor?.setFilterY(_filterYLow.value, _filterYHigh.value)
        dynamicBassProcessor?.setSideGain(_sideGainX.value, _sideGainY.value)
    }

    // Called by MusicService to start tracking
    fun startHeadTracking(onYaw: (Float) -> Unit) {
        stopHeadTracking()
        headTracker = HeadOrientationTracker(context) { yaw ->
            onYaw(yaw)
            // also forward to the processor if needed
            dynamicBassProcessor?.setHeadYaw(yaw)
        }.apply {
            smoothing = headTrackingSmoothing
            start()
        }
    }

    fun stopHeadTracking() {
        headTracker?.stop()
        headTracker = null
    }

    fun setHeadTrackingSmoothing(factor: Float) {
        headTrackingSmoothing = factor.coerceIn(0.1f, 1.0f)
        headTracker?.smoothing = headTrackingSmoothing
    }
    
    /**
     * Kept for compatibility with existing callers (e.g. DualPlayerEngine's
     * onAudioInputFormatChanged listener). No longer does the actual setup —
     * the processor is created eagerly above, and DynamicBassProcessor now
     * reconfigures its own engine for the real sample rate via onConfigure()
     * when the audio sink negotiates the format. Safe to keep calling this;
     * it's just a log line now.
     */
    fun initializeProcessor(sampleRate: Int) {
        Timber.tag(TAG).d("initializeProcessor($sampleRate Hz) called - no-op, handled via onConfigure()")
    }

    /**
     * Get the processor instance to add to ExoPlayer's audio sink.
     * Always non-null once this manager is constructed.
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
        // Engine expects a 0-100 percent value; UI/state store a 0-1 fraction.
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

