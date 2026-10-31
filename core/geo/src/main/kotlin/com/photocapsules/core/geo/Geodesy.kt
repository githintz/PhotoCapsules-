package com.photocapsules.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class GeodesicInverse(
    /** Ellipsoidal distance in meters. */
    val distanceMeters: Double,
    /** Initial bearing (forward azimuth) at point 1, degrees clockwise from true north, [0, 360). */
    val initialBearing: Double,
    /** Bearing at point 2, degrees [0, 360). */
    val finalBearing: Double,
)

/**
 * WGS84 geodesy using Vincenty's formulae (accurate to ~0.5 mm), with a robust fallback for
 * nearly antipodal points where Vincenty fails to converge.
 */
object Geodesy {
    const val WGS84_A = 6378137.0
    const val WGS84_F = 1.0 / 298.257223563
    const val WGS84_B = WGS84_A * (1 - WGS84_F)
    /** Mean Earth radius (IUGG), meters. */
    const val EARTH_MEAN_RADIUS = 6371008.8

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm360(d: Double): Double { var x = d % 360.0; if (x < 0) x += 360.0; return if (x >= 360.0) 0.0 else x }

    fun inverse(p1: LatLon, p2: LatLon): GeodesicInverse {
        val v = vincentyInverse(p1, p2)
        // Near the antipode Vincenty may fail or converge to a non-shortest geodesic: also solve numerically
        // and keep the shorter result.
        if (haversine(p1, p2) > 19_500_000.0) {
            val n = antipodalInverse(p1, p2)
            if (n.distanceMeters < v.distanceMeters - 1e-3) return n
        }
        return v
    }

    private fun vincentyInverse(p1: LatLon, p2: LatLon): GeodesicInverse {
        val a = WGS84_A; val b = WGS84_B; val f = WGS84_F
        val phi1 = rad(p1.latitude); val phi2 = rad(p2.latitude)
        val l = rad(LatLon.normalizeLongitude(p2.longitude - p1.longitude))
        if (abs(p1.latitude - p2.latitude) < 1e-12 && abs(l) < 1e-14) return GeodesicInverse(0.0, 0.0, 0.0)
        val u1 = atan((1 - f) * tan(phi1)); val u2 = atan((1 - f) * tan(phi2))
        val sinU1 = sin(u1); val cosU1 = cos(u1); val sinU2 = sin(u2); val cosU2 = cos(u2)
        var lambda = l
        var iter = 0
        var sinSigma: Double; var cosSigma: Double; var sigma: Double
        var sinAlpha: Double; var cos2Alpha: Double; var cos2SigmaM: Double
        var sinLambda: Double; var cosLambda: Double
        while (true) {
            sinLambda = sin(lambda); cosLambda = cos(lambda)
            val t1 = cosU2 * sinLambda
            val t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            sinSigma = sqrt(t1 * t1 + t2 * t2)
            if (sinSigma == 0.0) return GeodesicInverse(0.0, 0.0, 0.0)
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cos2Alpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cos2Alpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cos2Alpha else 0.0
            val c = f / 16 * cos2Alpha * (4 + f * (4 - 3 * cos2Alpha))
            val lambdaPrev = lambda
            lambda = l + (1 - c) * f * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - lambdaPrev) < 1e-12) break
            if (++iter > 200 || abs(lambda) > PI) return antipodalInverse(p1, p2)
        }
        val uSq = cos2Alpha * (a * a - b * b) / (b * b)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
            bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val s = b * bigA * (sigma - deltaSigma)
        val az1 = atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
        val az2 = atan2(cosU1 * sinLambda, -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda)
        return GeodesicInverse(s, norm360(deg(az1)), norm360(deg(az2)))
    }

    /** Destination point given start, initial bearing (deg) and distance (m) on WGS84 (Vincenty direct). */
    fun direct(start: LatLon, bearingDeg: Double, distanceMeters: Double): LatLon {
        if (distanceMeters == 0.0) return start
        val a = WGS84_A; val b = WGS84_B; val f = WGS84_F
        val alpha1 = rad(bearingDeg)
        val sinAlpha1 = sin(alpha1); val cosAlpha1 = cos(alpha1)
        val tanU1 = (1 - f) * tan(rad(start.latitude))
        val cosU1 = 1 / sqrt(1 + tanU1 * tanU1); val sinU1 = tanU1 * cosU1
        val sigma1 = atan2(tanU1, cosAlpha1)
        val sinAlpha = cosU1 * sinAlpha1
        val cosSqAlpha = 1 - sinAlpha * sinAlpha
        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        var sigma = distanceMeters / (b * bigA)
        var cos2SigmaM: Double; var sinSigma: Double; var cosSigma: Double
        var iter = 0
        while (true) {
            cos2SigmaM = cos(2 * sigma1 + sigma)
            sinSigma = sin(sigma); cosSigma = cos(sigma)
            val deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
            val prev = sigma
            sigma = distanceMeters / (b * bigA) + deltaSigma
            if (abs(sigma - prev) < 1e-12 || ++iter > 200) break
        }
        cos2SigmaM = cos(2 * sigma1 + sigma); sinSigma = sin(sigma); cosSigma = cos(sigma)
        val tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1
        val phi2 = atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1, (1 - f) * sqrt(sinAlpha * sinAlpha + tmp * tmp))
        val lambda = atan2(sinSigma * sinAlpha1, cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1)
        val c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
        val l = lambda - (1 - c) * f * sinAlpha * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        val lat = deg(phi2).coerceIn(-90.0, 90.0)
        return LatLon(lat, LatLon.normalizeLongitude(start.longitude + deg(l)))
    }

    /** Points along the geodesic from [start] in direction [bearingDeg] for [distanceMeters] ("real azimuth line"). */
    fun geodesicLine(start: LatLon, bearingDeg: Double, distanceMeters: Double, segments: Int = 64): List<LatLon> =
        (0..segments).map { direct(start, bearingDeg, distanceMeters * it / segments) }

    /**
     * Inverse problem for nearly antipodal points (where Vincenty's iteration does not converge), solved
     * numerically with the direct problem: find the azimuth whose geodesic passes through p2, and the
     * distance along it. Accurate to millimetres.
     */
    internal fun antipodalInverse(p1: LatLon, p2: LatLon): GeodesicInverse {
        fun miss(az: Double, s: Double) = haversine(direct(p1, az, s), p2)
        fun bestS(az: Double): Pair<Double, Double> {
            // Golden-section search for the closest approach along this azimuth near half the meridian length.
            var a = 1.97e7; var b = 2.0040e7
            val g = (sqrt(5.0) - 1) / 2
            var c = b - g * (b - a); var d = a + g * (b - a)
            var fc = miss(az, c); var fd = miss(az, d)
            repeat(60) {
                if (fc < fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = miss(az, c) }
                else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = miss(az, d) }
            }
            val s = (a + b) / 2
            return s to miss(az, s)
        }
        // Several geodesics can reach a nearly antipodal point: scan azimuths, refine every local minimum of the
        // miss distance, keep those that actually hit p2, and choose the shortest.
        val step = 1.0
        val scan = (0 until 360).map { i -> val a = i * step; Triple(a, bestS(a).first, bestS(a).second) }
        val g = (sqrt(5.0) - 1) / 2
        val candidates = ArrayList<Pair<Double, Double>>() // (azimuth, distance)
        for (i in scan.indices) {
            val prev = scan[(i + scan.size - 1) % scan.size].third; val cur = scan[i].third; val next = scan[(i + 1) % scan.size].third
            if (cur > prev || cur > next) continue
            var lo = scan[i].first - step; var hi = scan[i].first + step
            var c = hi - g * (hi - lo); var d = lo + g * (hi - lo)
            var fc = bestS(c).second; var fd = bestS(d).second
            repeat(50) {
                if (fc < fd) { hi = d; d = c; fd = fc; c = hi - g * (hi - lo); fc = bestS(c).second }
                else { lo = c; c = d; fc = fd; d = lo + g * (hi - lo); fd = bestS(d).second }
            }
            val az = norm360((lo + hi) / 2)
            val (sDist, miss) = bestS(az)
            if (miss < 0.5) candidates.add(az to sDist)
        }
        val best = candidates.minByOrNull { it.second } ?: return sphericalFallback(p1, p2)
        val az1 = best.first
        val s = best.second
        // Final azimuth: ellipsoidal bearing at p2 of the short geodesic arriving from 10 km back along the path.
        val before = direct(p1, az1, s - 10_000.0)
        val az2 = vincentyInverse(before, p2).finalBearing
        return GeodesicInverse(s, az1, az2)
    }

    /** Bearing between two very close points (used for the final azimuth). */
    private fun inverseShort(a: LatLon, b: LatLon): Double {
        val phi1 = rad(a.latitude); val phi2 = rad(b.latitude); val dl = rad(b.longitude - a.longitude)
        return norm360(deg(atan2(sin(dl) * cos(phi2), cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dl))))
    }

    private fun sphericalFallback(p1: LatLon, p2: LatLon): GeodesicInverse {
        val phi1 = rad(p1.latitude); val phi2 = rad(p2.latitude)
        val dl = rad(p2.longitude - p1.longitude)
        val c = (sin(phi1) * sin(phi2) + cos(phi1) * cos(phi2) * cos(dl)).coerceIn(-1.0, 1.0)
        val d = acos(c) * EARTH_MEAN_RADIUS
        val az1 = atan2(sin(dl) * cos(phi2), cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dl))
        val az2 = atan2(sin(dl) * cos(phi1), -cos(phi2) * sin(phi1) + sin(phi2) * cos(phi1) * cos(dl))
        return GeodesicInverse(d, norm360(deg(az1)), norm360(deg(az2)))
    }

    /** Spherical haversine distance in meters (mean radius) – used for quick checks. */
    fun haversine(p1: LatLon, p2: LatLon): Double {
        val dphi = rad(p2.latitude - p1.latitude); val dl = rad(p2.longitude - p1.longitude)
        val h = sin(dphi / 2) * sin(dphi / 2) + cos(rad(p1.latitude)) * cos(rad(p2.latitude)) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_MEAN_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
