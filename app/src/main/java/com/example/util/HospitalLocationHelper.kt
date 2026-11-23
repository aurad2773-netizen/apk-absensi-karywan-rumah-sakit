package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GpsReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val distanceFromHospitalMeters: Int
)

object HospitalLocationHelper {
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(
        context: Context,
        hospitalLat: Double,
        hospitalLng: Double
    ): GpsReading? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        val location: Location? = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { current ->
                    if (current != null) {
                        if (cont.isActive) cont.resume(current)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (cont.isActive) cont.resume(last)
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            if (cont.isActive) cont.resume(last)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
        }

        return location?.let { loc ->
            val distance = calculateDistanceMeters(
                loc.latitude,
                loc.longitude,
                hospitalLat,
                hospitalLng
            )
            GpsReading(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = loc.accuracy,
                distanceFromHospitalMeters = distance
            )
        }
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Int {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].roundToInt()
    }
}

object DateTimeUtils {
    private val indonesiaLocale = Locale("id", "ID")
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fullDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", indonesiaLocale)
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", indonesiaLocale)
    private val dayNameFormat = SimpleDateFormat("EEE", indonesiaLocale)
    private val dayNumberFormat = SimpleDateFormat("dd", indonesiaLocale)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val timeWithSecondsFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", indonesiaLocale)

    fun todayDateKey(): String = dateKeyFormat.format(Date())

    fun dateKeyForOffset(daysOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        return dateKeyFormat.format(cal.time)
    }

    fun formatFullDate(millis: Long = System.currentTimeMillis()): String =
        fullDateFormat.format(Date(millis))

    fun formatShortDateFromKey(dateKey: String): String {
        return try {
            val parsed = dateKeyFormat.parse(dateKey)
            if (parsed != null) shortDateFormat.format(parsed) else dateKey
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatDayChip(dateKey: String): Pair<String, String> {
        return try {
            val parsed = dateKeyFormat.parse(dateKey)
            if (parsed != null) {
                dayNameFormat.format(parsed) to dayNumberFormat.format(parsed)
            } else {
                "-" to dateKey.takeLast(2)
            }
        } catch (_: Exception) {
            "-" to dateKey.takeLast(2)
        }
    }

    fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    fun formatTimeWithSeconds(millis: Long): String = timeWithSecondsFormat.format(Date(millis))

    fun formatMonthYear(millis: Long = System.currentTimeMillis()): String =
        monthYearFormat.format(Date(millis))

    fun currentMonthPrefix(): String = todayDateKey().substring(0, 7)

    fun formatDuration(startMillis: Long, endMillis: Long?): String {
        if (endMillis == null || endMillis <= startMillis) return "Berlangsung"
        val totalMinutes = ((endMillis - startMillis) / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}j ${minutes}m"
    }
}
