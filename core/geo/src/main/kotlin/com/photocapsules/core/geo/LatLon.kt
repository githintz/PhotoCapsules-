package com.photocapsules.core.geo

import kotlin.math.abs

/** A WGS84 geographic position in decimal degrees. Longitude is normalized to [-180, 180). */
data class LatLon(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && longitude.isFinite()) { "Non-finite coordinate" }
        require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
    }

    fun normalized(): LatLon = LatLon(latitude, normalizeLongitude(longitude))

    companion object {
        fun normalizeLongitude(lon: Double): Double {
            var l = (lon + 180.0) % 360.0
            if (l < 0) l += 360.0
            return l - 180.0
        }
    }
}

internal fun Double.approxEquals(other: Double, eps: Double = 1e-12) = abs(this - other) <= eps
