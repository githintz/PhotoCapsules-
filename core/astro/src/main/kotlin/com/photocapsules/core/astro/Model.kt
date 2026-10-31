package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Time
import java.time.Instant

/** Observer location. [elevationM] = height of the ground above sea level (+ any offset). */
data class GeoPoint(val latitude: Double, val longitude: Double, val elevationM: Double = 0.0) {
    init {
        require(latitude in -90.0..90.0) { "latitude $latitude" }
        require(longitude.isFinite()) { "longitude" }
    }
    internal fun toObserver() = io.github.cosinekitty.astronomy.Observer(latitude, normLon(longitude), safeElevation)

    /** Elevation sanitised for the ephemeris engine: non-finite → 0, clamped to its valid range. */
    private val safeElevation: Double get() = if (elevationM.isFinite()) elevationM.coerceIn(-500.0, 100_000.0) else 0.0

    /**
     * Observer used for rise/set searches. USNO/PhotoPills convention: fixed 34′ refraction and no dependence
     * on the ground's elevation, so the ground is placed at sea level; [horizonHeightM] (height above the
     * visible horizon) is applied separately as horizon dip.
     */
    internal fun riseSetObserver(horizonHeightM: Double) =
        io.github.cosinekitty.astronomy.Observer(latitude, normLon(longitude), if (horizonHeightM.isFinite()) horizonHeightM.coerceIn(0.0, 100_000.0) else 0.0)
    private fun normLon(l: Double): Double { var x = (l + 180.0) % 360.0; if (x < 0) x += 360.0; return x - 180.0 }
}

enum class SkyBody { SUN, MOON }

/** Topocentric horizontal position. Altitudes in degrees; azimuth from true north clockwise. */
data class BodyPosition(
    val azimuth: Double,
    /** Apparent altitude including standard atmospheric refraction. */
    val altitude: Double,
    /** Geometric (airless) altitude of the centre. */
    val trueAltitude: Double,
    /** Topocentric right ascension of date, hours. */
    val raHours: Double,
    /** Topocentric declination of date, degrees. */
    val decDeg: Double,
    val distanceKm: Double,
    val angularDiameterDeg: Double,
)

/** Natural light classification by Sun altitude (PhotoPills convention). */
enum class LightType(val label: String) {
    DAY("Daytime"),
    GOLDEN_HOUR("Golden hour"),
    BLUE_HOUR("Blue hour"),
    NAUTICAL_TWILIGHT("Nautical twilight"),
    ASTRONOMICAL_TWILIGHT("Astronomical twilight"),
    NIGHT("Night");

    companion object {
        const val GOLDEN_UPPER = 6.0
        const val GOLDEN_LOWER = -4.0
        const val BLUE_LOWER = -6.0
        const val NAUTICAL_LOWER = -12.0
        const val ASTRONOMICAL_LOWER = -18.0

        /** Classify by geometric altitude of the Sun's centre. */
        fun forSunAltitude(alt: Double): LightType = when {
            alt >= GOLDEN_UPPER -> DAY
            alt >= GOLDEN_LOWER -> GOLDEN_HOUR
            alt >= BLUE_LOWER -> BLUE_HOUR
            alt >= NAUTICAL_LOWER -> NAUTICAL_TWILIGHT
            alt >= ASTRONOMICAL_LOWER -> ASTRONOMICAL_TWILIGHT
            else -> NIGHT
        }
    }
}

internal fun Instant.toAstroTime(): Time = Time.fromMillisecondsSince1970(toEpochMilli())
internal fun Time.toInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())

fun normalizeDegrees(d: Double): Double { var x = d % 360.0; if (x < 0) x += 360.0; return if (x >= 360.0) 0.0 else x }
/** Signed difference a−b wrapped to (−180, 180]. */
fun angleDiff(a: Double, b: Double): Double { var d = (a - b) % 360.0; if (d > 180) d -= 360.0; if (d <= -180) d += 360.0; return d }
