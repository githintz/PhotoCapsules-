package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.KM_PER_AU
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.rotationEqjEqd
import io.github.cosinekitty.astronomy.siderealTime
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic geometry of a solar eclipse for map display: central line, umbral/antumbral limits and
 * partial-magnitude contours (0 = penumbral limit, 0.2 … 0.8), computed from Sun/Moon ephemerides by
 * maximizing the topocentric magnitude over time at each location.
 */
data class SolarEclipseMapData(
    val centralLine: List<LatLonPoint>,
    val umbraNorthLimit: List<LatLonPoint>,
    val umbraSouthLimit: List<LatLonPoint>,
    /** level → closed-ish polyline (ordered by bearing around the greatest-eclipse point). */
    val partialContours: Map<Double, List<LatLonPoint>>,
    val greatestEclipse: LatLonPoint,
)

class SolarEclipseMap private constructor(private val samples: List<Sample>) {
    private class Sample(val ut: Double, val sx: Double, val sy: Double, val sz: Double, val mx: Double, val my: Double, val mz: Double)

    companion object {
        private const val A = 6378.1366
        private const val F = 1.0 / 298.25642
        private const val B = A * (1 - F)
        private const val E2 = F * (2 - F)
        private const val SUN_R = 695700.0
        private const val MOON_R = 1737.4
        val PARTIAL_LEVELS = listOf(0.0, 0.2, 0.4, 0.6, 0.8)

        /** Builds Earth-fixed Sun/Moon samples spanning ±[halfSpanHours] around the eclipse peak. */
        fun forEclipse(eclipse: SolarEclipse, halfSpanHours: Double = 4.0, stepMinutes: Double = 1.0): SolarEclipseMap {
            val peak = eclipse.peak.toAstroTime().ut
            val n = (2 * halfSpanHours * 60 / stepMinutes).toInt()
            val list = (0..n).map { i ->
                val t = Time(peak - halfSpanHours / 24.0 + i * stepMinutes / 1440.0)
                val rot = rotationEqjEqd(t)
                val s = rot.rotate(geoVector(Body.Sun, t, Aberration.Corrected))
                val m = rot.rotate(geoMoon(t))
                val gast = Math.toRadians(siderealTime(t) * 15.0)
                val c = cos(gast); val sn = sin(gast)
                fun ex(x: Double, y: Double) = (x * c + y * sn) * KM_PER_AU
                fun ey(x: Double, y: Double) = (-x * sn + y * c) * KM_PER_AU
                Sample(t.ut, ex(s.x, s.y), ey(s.x, s.y), s.z * KM_PER_AU, ex(m.x, m.y), ey(m.x, m.y), m.z * KM_PER_AU)
            }
            return SolarEclipseMap(list)
        }

        internal fun ecef(lat: Double, lon: Double): DoubleArray {
            val p = Math.toRadians(lat); val l = Math.toRadians(lon)
            val n = A / sqrt(1 - E2 * sin(p) * sin(p))
            return doubleArrayOf(n * cos(p) * cos(l), n * cos(p) * sin(l), n * (1 - E2) * sin(p))
        }
    }

    /** Topocentric magnitude at the sample, or −1 if the Sun is below the horizon there. */
    private fun magnitudeAt(s: Sample, o: DoubleArray, up: DoubleArray): Double {
        val sx = s.sx - o[0]; val sy = s.sy - o[1]; val sz = s.sz - o[2]
        val mx = s.mx - o[0]; val my = s.my - o[1]; val mz = s.mz - o[2]
        val ds = sqrt(sx * sx + sy * sy + sz * sz); val dm = sqrt(mx * mx + my * my + mz * mz)
        val sunUp = (sx * up[0] + sy * up[1] + sz * up[2]) / ds
        if (sunUp < -0.0145) return -1.0 // Sun more than ~0.83° below the horizon
        val rs = asin(SUN_R / ds); val rm = asin(MOON_R / dm)
        val cosSep = ((sx * mx + sy * my + sz * mz) / (ds * dm)).coerceIn(-1.0, 1.0)
        val sep = kotlin.math.acos(cosSep)
        return (rs + rm - sep) / (2 * rs)
    }

    private fun up(lat: Double, lon: Double): DoubleArray {
        val p = Math.toRadians(lat); val l = Math.toRadians(lon)
        return doubleArrayOf(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    }

    /** Maximum magnitude over the eclipse at a location (≤ 0 = no eclipse visible with the Sun up). */
    fun maxMagnitude(lat: Double, lon: Double): Double {
        val o = ecef(lat, lon); val u = up(lat, lon)
        var best = -1.0
        for (s in samples) { val m = magnitudeAt(s, o, u); if (m > best) best = m }
        return best
    }

    /** max over samples near [ut] of (|rM − rS| − separation) sign-adjusted; > 0 means inside umbra/antumbra. */
    private fun centralMargin(lat: Double, lon: Double, utCenter: Double, total: Boolean): Double {
        val o = ecef(lat, lon); val u = up(lat, lon)
        var best = -10.0
        for (s in samples) {
            if (kotlin.math.abs(s.ut - utCenter) > 30.0 / 1440.0) continue
            val sx = s.sx - o[0]; val sy = s.sy - o[1]; val sz = s.sz - o[2]
            val mx = s.mx - o[0]; val my = s.my - o[1]; val mz = s.mz - o[2]
            val ds = sqrt(sx * sx + sy * sy + sz * sz); val dm = sqrt(mx * mx + my * my + mz * mz)
            if ((sx * u[0] + sy * u[1] + sz * u[2]) / ds < -0.0145) continue
            val rs = asin(SUN_R / ds); val rm = asin(MOON_R / dm)
            val sep = kotlin.math.acos(((sx * mx + sy * my + sz * mz) / (ds * dm)).coerceIn(-1.0, 1.0))
            val margin = (if (total) rm - rs else rs - rm) - sep
            if (margin > best) best = margin
        }
        return best
    }

    /** Point where the shadow axis meets the ellipsoid at sample [s], or null if it misses Earth. */
    private fun axisPoint(s: Sample): Pair<LatLonPoint, Boolean>? {
        var dx = s.mx - s.sx; var dy = s.my - s.sy; var dz = s.mz - s.sz
        val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
        // Scale z to make the ellipsoid a sphere of radius A.
        val k = A / B
        val px = s.mx; val py = s.my; val pz = s.mz * k
        val qx = dx; val qy = dy; val qz = dz * k
        val a = qx * qx + qy * qy + qz * qz
        val b = 2 * (px * qx + py * qy + pz * qz)
        val c = px * px + py * py + pz * pz - A * A
        val disc = b * b - 4 * a * c
        if (disc < 0) return null
        val t = (-b - sqrt(disc)) / (2 * a)
        if (t < 0) return null
        val x = s.mx + t * dx; val y = s.my + t * dy; val z = s.mz + t * dz
        val lon = Math.toDegrees(atan2(y, x))
        val lat = Math.toDegrees(atan(z / (sqrt(x * x + y * y) * (1 - E2))))
        // Total if the umbra cone vertex lies beyond the surface point (Moon–surface distance < umbra length).
        val distMoonToSurface = t
        val umbraLength = MOON_R * dl / (SUN_R - MOON_R)
        return LatLonPoint(lat, lon) to (umbraLength > distMoonToSurface)
    }

    private fun destination(p: LatLonPoint, bearingDeg: Double, distKm: Double): LatLonPoint {
        val r = 6371.0; val d = distKm / r
        val lat1 = Math.toRadians(p.latitude); val lon1 = Math.toRadians(p.longitude); val b = Math.toRadians(bearingDeg)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLonPoint(Math.toDegrees(lat2), Eclipses.normalizeLon(Math.toDegrees(lon2)))
    }

    private fun bearing(a: LatLonPoint, b: LatLonPoint): Double {
        val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude); val dl = Math.toRadians(b.longitude - a.longitude)
        return Math.toDegrees(atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)))
    }

    /** Outermost distance along [bearing] from [origin] where f ≥ 0, scanning inward from [maxKm]; null if none. */
    private fun outermost(origin: LatLonPoint, bearing: Double, maxKm: Double, stepKm: Double, f: (LatLonPoint) -> Double): LatLonPoint? {
        var d = maxKm
        var prevOut = d
        while (d >= 0) {
            if (f(destination(origin, bearing, d)) >= 0) {
                if (d == maxKm) return destination(origin, bearing, d)
                var lo = d; var hi = prevOut
                repeat(25) { val mid = (lo + hi) / 2; if (f(destination(origin, bearing, mid)) >= 0) lo = mid else hi = mid }
                return destination(origin, bearing, (lo + hi) / 2)
            }
            prevOut = d
            d -= stepKm
        }
        return null
    }

    fun compute(eclipse: SolarEclipse, levels: List<Double> = PARTIAL_LEVELS, centralStepMinutes: Int = 3): SolarEclipseMapData {
        val central = ArrayList<Triple<Sample, LatLonPoint, Boolean>>()
        for ((i, s) in samples.withIndex()) {
            if (i % centralStepMinutes != 0) continue
            axisPoint(s)?.let { (p, total) -> central.add(Triple(s, p, total)) }
        }
        val north = ArrayList<LatLonPoint>(); val south = ArrayList<LatLonPoint>()
        for (i in central.indices) {
            val (s, p, total) = central[i]
            val prev = central.getOrNull(i - 1)?.second ?: p
            val next = central.getOrNull(i + 1)?.second ?: p
            if (prev == next) continue
            val dir = bearing(prev, next)
            for ((side, list) in listOf(-90.0 to north, 90.0 to south)) {
                val b = dir + side
                outermost(p, b, 1500.0, 20.0) { q -> centralMargin(q.latitude, q.longitude, s.ut, total) }?.let { list.add(it) }
            }
        }
        // North list built with bearing dir−90 — that is to the left of the direction of motion. Path moves
        // generally eastward, so left = north. Swap if the average latitude says otherwise.
        val (nl, sl) = if (north.isNotEmpty() && south.isNotEmpty() && north.map { it.latitude }.average() < south.map { it.latitude }.average()) south to north else north to south

        val ge = eclipse.greatestEclipse ?: run {
            val best = samples.minBy { s ->
                val dx = s.mx - s.sx; val dy = s.my - s.sy; val dz = s.mz - s.sz
                val dl = sqrt(dx * dx + dy * dy + dz * dz)
                val t = -(s.mx * dx + s.my * dy + s.mz * dz) / (dl * dl)
                val cx = s.mx + t * dx; val cy = s.my + t * dy; val cz = s.mz + t * dz
                cx * cx + cy * cy + cz * cz
            }
            val dx = best.mx - best.sx; val dy = best.my - best.sy; val dz = best.mz - best.sz
            val dl = sqrt(dx * dx + dy * dy + dz * dz)
            val t = -(best.mx * dx + best.my * dy + best.mz * dz) / (dl * dl)
            val cx = best.mx + t * dx; val cy = best.my + t * dy; val cz = best.mz + t * dz
            LatLonPoint(Math.toDegrees(atan2(cz, sqrt(cx * cx + cy * cy))), Math.toDegrees(atan2(cy, cx)))
        }
        val contours = levels.associateWith { level ->
            (0 until 72).mapNotNull { k ->
                outermost(ge, k * 5.0, 9000.0, 150.0) { q -> maxMagnitude(q.latitude, q.longitude) - maxOf(level, 1e-6) }
            }
        }
        return SolarEclipseMapData(central.map { it.second }, nl, sl, contours, ge)
    }
}
