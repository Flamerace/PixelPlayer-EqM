package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
        val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")
        val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
        val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
        val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
        val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json")
        
        // DynamicBass keys
        val DYNAMIC_BASS_ENABLED = booleanPreferencesKey("dynamic_bass_enabled")
        val DYNAMIC_BASS_GAIN = floatPreferencesKey("dynamic_bass_gain")
        val DYNAMIC_BASS_FILTER_X_LOW = floatPreferencesKey("dynamic_bass_filter_x_low")
        val DYNAMIC_BASS_FILTER_X_HIGH = floatPreferencesKey("dynamic_bass_filter_x_high")
        val DYNAMIC_BASS_FILTER_Y_LOW = floatPreferencesKey("dynamic_bass_filter_y_low")
        val DYNAMIC_BASS_FILTER_Y_HIGH = floatPreferencesKey("dynamic_bass_filter_y_high")
        val DYNAMIC_BASS_SIDE_GAIN_X = floatPreferencesKey("dynamic_bass_side_gain_x")
        val DYNAMIC_BASS_SIDE_GAIN_Y = floatPreferencesKey("dynamic_bass_side_gain_y")

        // StereoExpand keys
        val STEREO_WIDENER_ENABLED = booleanPreferencesKey("stereo_widener_enabled")
        val STEREO_WIDTH = floatPreferencesKey("stereo_width")        // 0.0 .. 2.0 (0–200%)
        val STEREO_BASS_PROTECT = floatPreferencesKey("stereo_bass_protect") // Hz

        // SurroundSound keys
        val SURROUND_ENABLED = booleanPreferencesKey("surround_enabled")
        val HEAD_TRACKING_ENABLED = booleanPreferencesKey("head_tracking_enabled")
        val HEAD_TRACKING_SMOOTHING = floatPreferencesKey("head_tracking_smoothing") // 0.1..1.0

        val SURROUND_BASS_ANGLE = floatPreferencesKey("surround_bass_angle")
        val SURROUND_BASS_DISTANCE = floatPreferencesKey("surround_bass_distance")
        val SURROUND_MID_ANGLE = floatPreferencesKey("surround_mid_angle")
        val SURROUND_MID_DISTANCE = floatPreferencesKey("surround_mid_distance")
        val SURROUND_TREBLE_ANGLE = floatPreferencesKey("surround_treble_angle")
        val SURROUND_TREBLE_DISTANCE = floatPreferencesKey("surround_treble_distance")
        val SURROUND_CROSSOVER_BASS_MID = floatPreferencesKey("surround_crossover_bass_mid")
        val SURROUND_CROSSOVER_MID_TREBLE = floatPreferencesKey("surround_crossover_mid_treble")
    }

    val equalizerViewModeFlow: Flow<EqualizerViewMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.VIEW_MODE]
        if (modeString != null) {
            try {
                EqualizerViewMode.valueOf(modeString)
            } catch (_: Exception) {
                EqualizerViewMode.SLIDERS
            }
        } else {
            val isGraph = preferences[booleanPreferencesKey("is_graph_view")] ?: false
            if (isGraph) EqualizerViewMode.GRAPH else EqualizerViewMode.SLIDERS
        }
    }

    val equalizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_ENABLED] ?: false
    }

    val equalizerPresetFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_PRESET] ?: "flat"
    }

    val equalizerCustomBandsFlow: Flow<List<Int>> = dataStore.data.map { preferences ->
        val stored = preferences[Keys.EQUALIZER_CUSTOM_BANDS]
        if (stored != null) {
            try {
                val decoded = json.decodeFromString<List<Int>>(stored)
                when {
                    decoded.size >= 10 -> decoded.take(10)
                    decoded.isEmpty() -> List(10) { 0 }
                    else -> decoded + List(10 - decoded.size) { 0 }
                }
            } catch (_: Exception) {
                List(10) { 0 }
            }
        } else {
            List(10) { 0 }
        }
    }

    val bassBoostStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_STRENGTH] ?: 0
    }

    val virtualizerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_STRENGTH] ?: 0
    }

    val bassBoostEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_ENABLED] ?: false
    }

    val virtualizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_ENABLED] ?: false
    }

    val loudnessEnhancerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_ENHANCER_ENABLED] ?: false
    }

    val loudnessEnhancerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] ?: 0).coerceIn(0, 1000)
    }

    val bassBoostDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_DISMISSED] ?: false
    }

    val virtualizerDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_DISMISSED] ?: false
    }

    val loudnessDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_DISMISSED] ?: false
    }

    val customPresetsFlow: Flow<List<EqualizerPreset>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.CUSTOM_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<EqualizerPreset>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val pinnedPresetsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.PINNED_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<String>>(jsonString)
            } catch (_: Exception) {
                EqualizerPreset.ALL_PRESETS.map { it.name }
            }
        } else {
            EqualizerPreset.ALL_PRESETS.map { it.name }
        }
    }

    // DynamicBass flows
    val dynamicBassEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_ENABLED] ?: false
    }

    val dynamicBassBassGainFlow: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[Keys.DYNAMIC_BASS_GAIN] ?: 0.5f).coerceIn(0f, 1f)
    }

    val dynamicBassFilterXLowFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_FILTER_X_LOW] ?: 50f
    }

    val dynamicBassFilterXHighFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_FILTER_X_HIGH] ?: 250f
    }

    val dynamicBassFilterYLowFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_FILTER_Y_LOW] ?: 20f
    }

    val dynamicBassFilterYHighFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_FILTER_Y_HIGH] ?: 200f
    }

    val dynamicBassSideGainXFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_SIDE_GAIN_X] ?: 0f
    }

    val dynamicBassSideGainYFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_BASS_SIDE_GAIN_Y] ?: 0f
    }

    // StereoWidner flows
    val stereoWidenerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.STEREO_WIDENER_ENABLED] ?: false
    }

    // SurroundSound flows
    val surroundEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SURROUND_ENABLED] ?: false
    }

    suspend fun setEqualizerViewMode(mode: EqualizerViewMode) =
        dataStore.edit { preferences ->
            preferences[Keys.VIEW_MODE] = mode.name
        }

    suspend fun setEqualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_ENABLED] = enabled
        }

    suspend fun setEqualizerPreset(preset: String) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_PRESET] = preset
        }

    suspend fun setEqualizerCustomBands(bands: List<Int>) =
        dataStore.edit { preferences ->
            val normalized = when {
                bands.size >= 10 -> bands.take(10)
                bands.isEmpty() -> List(10) { 0 }
                else -> bands + List(10 - bands.size) { 0 }
            }
            preferences[Keys.EQUALIZER_CUSTOM_BANDS] = json.encodeToString(normalized)
        }

    suspend fun setBassBoostStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setVirtualizerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_ENABLED] = enabled
        }

    suspend fun setVirtualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_DISMISSED] = dismissed
        }

    suspend fun setVirtualizerDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_DISMISSED] = dismissed
        }

    suspend fun setLoudnessDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_DISMISSED] = dismissed
        }

    suspend fun setPinnedPresets(presetNames: List<String>) =
        dataStore.edit { preferences ->
            preferences[Keys.PINNED_PRESETS] = json.encodeToString(presetNames)
        }

    // DynamicBass setters
    suspend fun setDynamicBassEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_BASS_ENABLED] = enabled
        }

    suspend fun setDynamicBassBassGain(gain: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_BASS_GAIN] = gain.coerceIn(0f, 1f)
        }

    suspend fun setDynamicBassFilterX(low: Float, high: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_BASS_FILTER_X_LOW] = low.coerceIn(200f, 1500f)
            preferences[Keys.DYNAMIC_BASS_FILTER_X_HIGH] = high.coerceIn(2000f, 8000f)
        }

    suspend fun setDynamicBassFilterY(low: Float, high: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_BASS_FILTER_Y_LOW] = low.coerceIn(40f, 400f)
            preferences[Keys.DYNAMIC_BASS_FILTER_Y_HIGH] = high.coerceIn(10f, 200f)
        }

    suspend fun setDynamicBassSideGain(gx: Float, gy: Float) =
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_BASS_SIDE_GAIN_X] = gx.coerceIn(0f, 100f)
            preferences[Keys.DYNAMIC_BASS_SIDE_GAIN_Y] = gy.coerceIn(0f, 100f)
        }

    // StereoWidener setters
    suspend fun setStereoWidenerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.STEREO_WIDENER_ENABLED] = enabled
        }

    // SurroundSound setters
    suspend fun setSurroundEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.SURROUND_ENABLED] = enabled
        }

    suspend fun saveCustomPreset(preset: EqualizerPreset) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == preset.name }
        current.add(preset)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }

    suspend fun deleteCustomPreset(presetName: String) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == presetName }
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        if (pinned.remove(presetName)) {
            setPinnedPresets(pinned)
        }
    }

    suspend fun renameCustomPreset(oldName: String, newName: String) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index == -1) return

        current[index] = current[index].copy(name = newName, displayName = newName)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        val pinnedIndex = pinned.indexOf(oldName)
        if (pinnedIndex != -1) {
            pinned[pinnedIndex] = newName
            setPinnedPresets(pinned)
        }

        val activePreset = dataStore.data.first()[Keys.EQUALIZER_PRESET]
        if (activePreset == oldName) {
            dataStore.edit { preferences ->
                preferences[Keys.EQUALIZER_PRESET] = newName
            }
        }
    }

    suspend fun updateCustomPresetBands(presetName: String, bandLevels: List<Int>) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == presetName }
        if (index == -1) return

        current[index] = current[index].copy(bandLevels = bandLevels)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }
}
