package com.photocapsules.core.astro

import java.time.Instant

/** Curves for the Night AR view, all in horizontal coordinates (azimuth, apparent altitude). */
object NightSky {
    /** Polaris (α UMi), J2000. */
    const val POLARIS_RA_HOURS = 2.0 + 31.0 / 60.0 + 49.09 / 3600.0
    const val POLARIS_DEC = 89.0 + 15.0 / 60.0 + 50.8 / 3600.0
    /** σ Octantis (southern pole star), J2000. */
    const val SIGMA_OCT_RA_HOURS = 21.0 + 8.0 / 60.0 + 46.86 / 3600.0
    const val SIGMA_OCT_DEC = -(88.0 + 57.0 / 60.0 + 23.4 / 3600.0)

    fun northCelestialPole(instant: Instant, where: GeoPoint) = Sky.ofDatePoint(0.0, 90.0, instant.toAstroTime(), where, refraction = true)
    fun southCelestialPole(instant: Instant, where: GeoPoint) = Sky.ofDatePoint(0.0, -90.0, instant.toAstroTime(), where, refraction = true)
    fun polaris(instant: Instant, where: GeoPoint) = Sky.fixedPoint(POLARIS_RA_HOURS, POLARIS_DEC, instant, where)
    fun sigmaOctantis(instant: Instant, where: GeoPoint) = Sky.fixedPoint(SIGMA_OCT_RA_HOURS, SIGMA_OCT_DEC, instant, where)

    /** Line of constant declination (of date): celestial equator for 0°, star-trail circles otherwise. */
    fun declinationCircle(decDeg: Double, instant: Instant, where: GeoPoint, stepDeg: Double = 3.0): List<HorizontalCoord> {
        val t = instant.toAstroTime()
        val out = ArrayList<HorizontalCoord>()
        var ra = 0.0
        while (ra <= 360.0) { out.add(Sky.ofDatePoint(ra, decDeg, t, where, refraction = true)); ra += stepDeg }
        return out
    }

    /** Line of constant right ascension (of date) from dec −90 to +90. */
    fun hourCircle(raDeg: Double, instant: Instant, where: GeoPoint, stepDeg: Double = 3.0): List<HorizontalCoord> {
        val t = instant.toAstroTime()
        val out = ArrayList<HorizontalCoord>()
        var dec = -90.0
        while (dec <= 90.0) { out.add(Sky.ofDatePoint(raDeg, dec, t, where, refraction = true)); dec += stepDeg }
        return out
    }

    /**
     * Arc traced by a star at the given horizontal position during [exposureSeconds] (star-trail pattern).
     * Returns points from start to end (direction of motion).
     */
    fun starTrailArc(start: HorizontalCoord, instant: Instant, where: GeoPoint, exposureSeconds: Double, segments: Int = 12): List<HorizontalCoord> {
        val t = instant.toAstroTime()
        // Convert the horizontal direction to equatorial of date via the inverse rotation.
        val rot = io.github.cosinekitty.astronomy.rotationHorEqd(t, where.toObserver())
        val az = Math.toRadians(start.azimuth); val alt = Math.toRadians(start.altitude)
        // HOR frame: x north, y west, z up.
        val v = io.github.cosinekitty.astronomy.Vector(kotlin.math.cos(alt) * kotlin.math.cos(az), -kotlin.math.cos(alt) * kotlin.math.sin(az), kotlin.math.sin(alt), t)
        val eqd = rot.rotate(v)
        val ra = Math.toDegrees(kotlin.math.atan2(eqd.y, eqd.x))
        val dec = Math.toDegrees(kotlin.math.asin(eqd.z.coerceIn(-1.0, 1.0)))
        return (0..segments).map { i ->
            val dt = exposureSeconds * i / segments
            val ti = Instant.ofEpochMilli(instant.toEpochMilli() + (dt * 1000).toLong()).toAstroTime()
            Sky.ofDatePoint(ra, dec, ti, where, refraction = false)
        }
    }

    /** Declination of the sky point at the given horizontal direction (used by Spot Stars AR). */
    fun declinationAt(direction: HorizontalCoord, instant: Instant, where: GeoPoint): Double {
        val t = instant.toAstroTime()
        val rot = io.github.cosinekitty.astronomy.rotationHorEqd(t, where.toObserver())
        val az = Math.toRadians(direction.azimuth); val alt = Math.toRadians(direction.altitude)
        val v = io.github.cosinekitty.astronomy.Vector(kotlin.math.cos(alt) * kotlin.math.cos(az), -kotlin.math.cos(alt) * kotlin.math.sin(az), kotlin.math.sin(alt), t)
        return Math.toDegrees(kotlin.math.asin(rot.rotate(v).z.coerceIn(-1.0, 1.0)))
    }
}

/**
 * Pinhole projection of horizontal directions onto a camera image, given the device rotation matrix
 * (Android convention: row-major 3×3 R mapping device coordinates to world East-North-Up).
 * The back camera looks along device −Z; screen x = device +X (right), screen y = device +Y (up).
 */
object ArProjection {
    /** World ENU unit vector for azimuth/altitude. */
    fun enu(azDeg: Double, altDeg: Double): DoubleArray {
        val az = Math.toRadians(azDeg); val alt = Math.toRadians(altDeg)
        return doubleArrayOf(kotlin.math.cos(alt) * kotlin.math.sin(az), kotlin.math.cos(alt) * kotlin.math.cos(az), kotlin.math.sin(alt))
    }

    /**
     * Projects [azDeg]/[altDeg] to pixel coordinates (origin at image centre, y down) for a camera with
     * horizontal field of view [hFovDeg] on an image [widthPx]×[heightPx]. Returns null if behind the camera.
     */
    fun project(r: FloatArray, azDeg: Double, altDeg: Double, hFovDeg: Double, widthPx: Double, heightPx: Double): Pair<Double, Double>? {
        val w = enu(azDeg, altDeg)
        // device = Rᵀ · world
        val dx = r[0] * w[0] + r[3] * w[1] + r[6] * w[2]
        val dy = r[1] * w[0] + r[4] * w[1] + r[7] * w[2]
        val dz = r[2] * w[0] + r[5] * w[1] + r[8] * w[2]
        if (dz >= -1e-6) return null
        val f = (widthPx / 2) / kotlin.math.tan(Math.toRadians(hFovDeg) / 2)
        val x = f * dx / -dz
        val y = f * dy / -dz
        return x to -y
    }

    /** Direction (az, alt) the camera centre is pointing at. */
    fun cameraDirection(r: FloatArray): HorizontalCoord {
        // device −Z in world = −(third column of R)
        val e = -r[2].toDouble(); val n = -r[5].toDouble(); val u = -r[8].toDouble()
        val az = normalizeDegrees(Math.toDegrees(kotlin.math.atan2(e, n)))
        val alt = Math.toDegrees(kotlin.math.asin(u.coerceIn(-1.0, 1.0)))
        return HorizontalCoord(az, alt)
    }
}
