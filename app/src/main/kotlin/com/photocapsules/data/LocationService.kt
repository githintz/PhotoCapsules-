package com.photocapsules.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationService {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** Fresh fix (GPS or network) within [timeoutMs]; falls back to the last known location. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context, timeoutMs: Long = 15_000): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return lastKnown(ctx)
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                providers.forEach { runCatching { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) } }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return fix ?: lastKnown(ctx)
    }

    /** Magnetic declination (degrees, east positive) from the World Magnetic Model. */
    /**
     * Height above mean sea level for a fix, or 0.0 (= unknown) when it cannot be derived. GPS altitude is
     * the height above the WGS84 ellipsoid, which differs from sea level by the geoid undulation (±100 m);
     * Android 14+ converts it with the on-device geoid model, older versions leave it unknown.
     */
    suspend fun seaLevelAltitude(ctx: Context, loc: Location): Double = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && loc.hasAltitude()) {
            runCatching {
                val copy = Location(loc)
                android.location.altitude.AltitudeConverter().addMslAltitudeToLocation(ctx, copy)
                if (copy.hasMslAltitude()) copy.mslAltitudeMeters else 0.0
            }.getOrDefault(0.0)
        } else 0.0
    }

    fun magneticDeclination(lat: Double, lon: Double, altitudeM: Double, timeMillis: Long): Double =
        GeomagneticField(lat.toFloat(), lon.toFloat(), altitudeM.toFloat(), timeMillis).declination.toDouble()
}
