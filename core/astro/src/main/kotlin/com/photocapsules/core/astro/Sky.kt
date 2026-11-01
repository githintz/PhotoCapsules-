package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.Vector
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.rotationEqdHor
import io.github.cosinekitty.astronomy.rotationEqjHor
import io.github.cosinekitty.astronomy.KM_PER_AU
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Positions of the Sun, Moon and fixed points of the sky. */
object Sky {
    const val SUN_RADIUS_KM = 695700.0
    const val MOON_RADIUS_KM = 1737.4

    internal fun body(b: SkyBody) = if (b == SkyBody.SUN) Body.Sun else Body.Moon

    fun position(body: SkyBody, instant: Instant, where: GeoPoint): BodyPosition = position(body, instant.toAstroTime(), where)

    internal fun position(body: SkyBody, time: Time, where: GeoPoint): BodyPosition {
        val obs = where.toObserver()
        val eq = equator(body(body), time, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val horTrue = horizon(time, obs, eq.ra, eq.dec, Refraction.None)
        val distKm = eq.dist * KM_PER_AU
        val r = if (body == SkyBody.SUN) SUN_RADIUS_KM else MOON_RADIUS_KM
        return BodyPosition(
            azimuth = horTrue.azimuth,
            altitude = apparentAltitude(horTrue.altitude),
            trueAltitude = horTrue.altitude,
            raHours = eq.ra,
            decDeg = eq.dec,
            distanceKm = distKm,
            angularDiameterDeg = Math.toDegrees(2 * asin(r / distKm)),
        )
    }

    /** Geometric altitude of the Sun's centre (fast path for samplers). */
    internal fun sunTrueAltitude(time: Time, where: GeoPoint): Double {
        val obs = where.toObserver()
        val eq = equator(Body.Sun, time, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        return horizon(time, obs, eq.ra, eq.dec, Refraction.None).altitude
    }

    /** Horizontal coordinates (az, apparent alt) of a fixed J2000 point (star, radiant, galactic centre). */
    fun fixedPoint(raHoursJ2000: Double, decDegJ2000: Double, instant: Instant, where: GeoPoint, refraction: Boolean = true): HorizontalCoord =
        fixedPoint(raHoursJ2000, decDegJ2000, instant.toAstroTime(), where, refraction)

    internal fun fixedPoint(raHoursJ2000: Double, decDegJ2000: Double, time: Time, where: GeoPoint, refraction: Boolean = true): HorizontalCoord {
        val v = unitVector(raHoursJ2000 * 15.0, decDegJ2000, time)
        val hor = rotationEqjHor(time, where.toObserver()).rotate(v)
        return toHorizontal(hor, refraction)
    }

    /** Horizontal coordinates of a point given in equatorial coordinates *of date* (grid lines, poles). */
    internal fun ofDatePoint(raDeg: Double, decDeg: Double, time: Time, where: GeoPoint, refraction: Boolean = false): HorizontalCoord {
        val hor = rotationEqdHor(time, where.toObserver()).rotate(unitVector(raDeg, decDeg, time))
        return toHorizontal(hor, refraction)
    }

    internal fun unitVector(lonDeg: Double, latDeg: Double, time: Time): Vector {
        val l = Math.toRadians(lonDeg); val b = Math.toRadians(latDeg)
        return Vector(cos(b) * cos(l), cos(b) * sin(l), sin(b), time)
    }

    internal fun toHorizontal(hor: Vector, refraction: Boolean): HorizontalCoord {
        val s = hor.toHorizontal(Refraction.None)
        return HorizontalCoord(s.lon, if (refraction) apparentAltitude(s.lat) else s.lat)
    }

    /**
     * Apparent altitude: standard refraction (Astronomy Engine "Normal" model) above −1°; below that the
     * refraction tapers linearly to zero at −5° (the engine would otherwise add a constant ~0.5° to every
     * negative altitude, e.g. showing −17.8° for a Sun that is geometrically 18.4° below the horizon).
     */
    fun apparentAltitude(trueAltitude: Double): Double {
        if (trueAltitude >= -1.0) return trueAltitude + io.github.cosinekitty.astronomy.refractionAngle(Refraction.Normal, trueAltitude)
        val r1 = io.github.cosinekitty.astronomy.refractionAngle(Refraction.Normal, -1.0)
        return trueAltitude + r1 * ((trueAltitude + 5.0) / 4.0).coerceIn(0.0, 1.0)
    }

    /** Ratio shadow length / object height for a light source at [altitudeDeg]; null when at/below the horizon. */
    fun shadowRatio(altitudeDeg: Double): Double? =
        if (altitudeDeg <= 0.0) null else 1.0 / kotlin.math.tan(Math.toRadians(altitudeDeg))
}

data class HorizontalCoord(val azimuth: Double, val altitude: Double)
