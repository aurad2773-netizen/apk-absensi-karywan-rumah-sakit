package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medpresensi_prefs")

data class HospitalLocationConfig(
    val hospitalName: String = "RSUD Medika Nusantara",
    val latitude: Double = -6.1944,
    val longitude: Double = 106.8229,
    val radiusMeters: Float = 250f
)

class SessionPreferences(private val context: Context) {
    private object Keys {
        val LOGGED_IN_EMPLOYEE_ID = longPreferencesKey("logged_in_employee_id")
        val HOSPITAL_NAME = stringPreferencesKey("hospital_name")
        val HOSPITAL_LAT = doublePreferencesKey("hospital_lat")
        val HOSPITAL_LNG = doublePreferencesKey("hospital_lng")
        val GEOFENCE_RADIUS = floatPreferencesKey("geofence_radius")
    }

    val loggedInEmployeeId: Flow<Long?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.LOGGED_IN_EMPLOYEE_ID] ?: -1L
        if (id > 0L) id else null
    }

    val hospitalConfig: Flow<HospitalLocationConfig> = context.dataStore.data.map { prefs ->
        HospitalLocationConfig(
            hospitalName = prefs[Keys.HOSPITAL_NAME] ?: "RSUD Medika Nusantara",
            latitude = prefs[Keys.HOSPITAL_LAT] ?: -6.1944,
            longitude = prefs[Keys.HOSPITAL_LNG] ?: 106.8229,
            radiusMeters = prefs[Keys.GEOFENCE_RADIUS] ?: 250f
        )
    }

    suspend fun setLoggedInEmployeeId(employeeId: Long?) {
        context.dataStore.edit { prefs ->
            if (employeeId == null || employeeId <= 0L) {
                prefs.remove(Keys.LOGGED_IN_EMPLOYEE_ID)
            } else {
                prefs[Keys.LOGGED_IN_EMPLOYEE_ID] = employeeId
            }
        }
    }

    suspend fun updateHospitalLocation(
        hospitalName: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOSPITAL_NAME] = hospitalName.trim().ifEmpty { "RSUD Medika Nusantara" }
            prefs[Keys.HOSPITAL_LAT] = latitude
            prefs[Keys.HOSPITAL_LNG] = longitude
            prefs[Keys.GEOFENCE_RADIUS] = radiusMeters.coerceIn(50f, 2000f)
        }
    }
}
