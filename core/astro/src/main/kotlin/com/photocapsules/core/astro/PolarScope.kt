package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.rotationEqjEqd
import io.github.cosinekitty.astronomy.siderealTime
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToLong

/** Where the pole star sits around the celestial pole, as needed to polar-align an equatorial mount. */
data class PolarScopeView(
    /** True for the north celestial pole (Polaris), false for the south (σ Octantis). */
    val northern: Boolean,
    val starName: String,
    /** Local hour angle of the pole star, hours in [0, 24): 0 = upper transit (star straight above the pole). */
    val hourAngleHours: Double,
    /** Local apparent sidereal time, hours in [0, 24). */
    val siderealTimeHours: Double,
    /** Angular distance of the star from the pole of date, degrees (the radius of the reticle circle). */
    val poleDistanceDeg: Double,
    /** Right ascension (hours) and declination (degrees) of the star, equinox of date. */
    val raHours: Double,
    val decDeg: Double,
    /** Direction of the star from the pole as seen with the naked eye, degrees clockwise from straight up. */
    val nakedEyeAngleDeg: Double,
    /** Next time the star is straight above the pole (hour angle 0). */
    val nextUpperTransit: Instant,
) {
    /** Direction in an inverting polar scope (image rotated by 180°), degrees clockwise from the top of the reticle. */
    val scopeAngleDeg: Double get() = normalizeDegrees(nakedEyeAngleDeg + 180.0)
}

/**
 * Polar scope reticle: position of Polaris (north) or σ Octantis (south) around the celestial pole.
 * Coordinates are precessed and nutated to the date, so the pole distance follows the slow drift of the pole.
 */
object PolarScope {
    private const val SIDEREAL_RATIO = 0.9972695663 // mean solar days per sidereal day

    fun compute(instant: Instant, where: GeoPoint, northern: Boolean = where.latitude >= 0): PolarScopeView {
        val t = instant.toAstroTime()
        val (ra, dec) = ofDate(northern, t)
        val lst = siderealHours(t, where.longitude)
        val ha = norm24(lst - ra)
        // Facing the pole, the sky turns anticlockwise in the north and clockwise in the south.
        val naked = normalizeDegrees(if (northern) -ha * 15.0 else ha * 15.0)
        // Next upper transit: the hour angle advances 24 h per sidereal day; refine once for precession/nutation.
        var transit = t.ut + norm24(24.0 - ha) / 24.0 * SIDEREAL_RATIO
        repeat(2) {
            val tt = Time(transit)
            val h = norm24(siderealHours(tt, where.longitude) - ofDate(northern, tt).first)
            transit += (if (h > 12) h - 24 else h) * -1.0 / 24.0 * SIDEREAL_RATIO
        }
        return PolarScopeView(
            northern, if (northern) "Polaris" else "σ Octantis", ha, lst,
            if (northern) 90.0 - dec else 90.0 + dec, ra, dec, naked, Time(transit).toInstant(),
        )
    }

    /** Reading on a 12-hour clock dial for a direction given in degrees clockwise from the top, e.g. "4:37". */
    fun clockLabel(angleDeg: Double): String {
        val minutes = (normalizeDegrees(angleDeg) * 2.0).roundToLong() % 720 // 360° = 720 clock minutes
        val h = (minutes / 60).let { if (it == 0L) 12 else it }
        return "%d:%02d".format(h, minutes % 60)
    }

    /** "06h 12m" for an angle in hours. */
    fun hoursLabel(hours: Double): String {
        val m = (norm24(hours) * 60.0).roundToLong() % 1440
        return "%02dh %02dm".format(m / 60, m % 60)
    }

    private fun siderealHours(t: Time, longitude: Double) = norm24(siderealTime(t) + longitude / 15.0)

    /** Star position (RA hours, Dec degrees) referred to the true equator and equinox of date. */
    private fun ofDate(northern: Boolean, t: Time): Pair<Double, Double> {
        val raJ = if (northern) NightSky.POLARIS_RA_HOURS else NightSky.SIGMA_OCT_RA_HOURS
        val decJ = if (northern) NightSky.POLARIS_DEC else NightSky.SIGMA_OCT_DEC
        val v = rotationEqjEqd(t).rotate(Sky.unitVector(raJ * 15.0, decJ, t))
        return norm24(Math.toDegrees(atan2(v.y, v.x)) / 15.0) to Math.toDegrees(asin(v.z.coerceIn(-1.0, 1.0)))
    }

    private fun norm24(h: Double): Double { var x = h % 24.0; if (x < 0) x += 24.0; return if (x >= 24.0) 0.0 else x }
}
