package com.kosd.log_inattendancesafeguard.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLocation(): Location {
        if (!hasLocationPermission()) throw SecurityException("Location permission not granted")

        val fresh = withTimeoutOrNull(8_000L) { requestFreshLocation() }
        if (fresh != null) return fresh

        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) continuation.resume(location)
                        else continuation.resumeWithException(Exception("Location unavailable"))
                    }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }

    private suspend fun requestFreshLocation(): Location =
        suspendCancellableCoroutine { continuation ->
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMaxUpdates(1)
                .setMinUpdateDistanceMeters(0f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    fusedLocationClient.removeLocationUpdates(this)
                    if (loc != null) continuation.resume(loc)
                    else continuation.resumeWithException(Exception("No location in result"))
                }
            }

            continuation.invokeOnCancellation { fusedLocationClient.removeLocationUpdates(callback) }

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            } else {
                continuation.resumeWithException(SecurityException("Location permission not granted"))
            }
        }

    fun isWithinRadius(
        currentLat: Double, currentLon: Double,
        targetLat: Double, targetLon: Double,
        radiusMeters: Double
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, results)
        return results[0] <= radiusMeters
    }
}
