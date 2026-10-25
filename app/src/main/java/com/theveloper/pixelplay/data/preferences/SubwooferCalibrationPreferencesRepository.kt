package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationPoint
import com.theveloper.pixelplay.data.equalizer.SubwooferCalibrationProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubwooferCalibrationPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private companion object {
        val PROFILES = stringPreferencesKey("subwoofer_calibration_profiles_json")
    }

    val profilesFlow: Flow<List<SubwooferCalibrationProfile>> = dataStore.data.map { preferences ->
        preferences[PROFILES]?.let { encoded ->
            runCatching { json.decodeFromString<List<SubwooferCalibrationProfile>>(encoded) }
                .getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveMeasurement(
        volumeStep: Int,
        maxVolumeStep: Int,
        outputRouteLabel: String,
        point: SubwooferCalibrationPoint
    ): SubwooferCalibrationProfile {
        var savedProfile: SubwooferCalibrationProfile? = null
        dataStore.edit { preferences ->
            val profiles = preferences[PROFILES]?.let { encoded ->
                runCatching { json.decodeFromString<List<SubwooferCalibrationProfile>>(encoded) }
                    .getOrDefault(emptyList())
            } ?: emptyList()

            val existing = profiles.firstOrNull {
                it.volumeStep == volumeStep &&
                    it.maxVolumeStep == maxVolumeStep &&
                    it.outputRouteLabel == outputRouteLabel
            }
            val points = (existing?.points.orEmpty()
                .filterNot { it.frequencyHz == point.frequencyHz } + point)
                .sortedBy { it.frequencyHz }
            val updated = SubwooferCalibrationProfile(
                volumeStep = volumeStep,
                maxVolumeStep = maxVolumeStep,
                outputRouteLabel = outputRouteLabel,
                safetyMarginDb = existing?.safetyMarginDb ?: 4f,
                points = points,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            savedProfile = updated
            preferences[PROFILES] = json.encodeToString(
                profiles.filterNot {
                    it.volumeStep == volumeStep &&
                        it.maxVolumeStep == maxVolumeStep &&
                        it.outputRouteLabel == outputRouteLabel
                } + updated
            )
        }
        return checkNotNull(savedProfile)
    }
}
