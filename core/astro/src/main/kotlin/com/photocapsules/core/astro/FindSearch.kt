package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Time
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sqrt

data class FindQuery(
    val body: SkyBody,
    val where: GeoPoint,
    val start: Instant,
    val end: Instant,
    val azimuth: Double,
    val azimuthTolerance: Double,
    /** Desired apparent altitude, or null to ignore altitude ("at azimuth" mode; body must be above −1°). */
    val altitude: Double?,
    val altitudeTolerance: Double,
    val maxResults: Int = 500,
)

data class FindResult(
    val time: Instant,
    val azimuth: Double,
    val altitude: Double,
    val lightType: LightType,
    /** Moon illumination (0..1); null when searching the Sun. */
    val moonIllumination: Double?,
)

/**
 * "Find" tool: dates and times when the Sun/Moon is at a given azimuth (± tolerance) and elevation (± tolerance).
 * Scans azimuth crossings at a coarse step, then within each window where |Δaz| ≤ tolerance minimizes the
 * normalized error by golden-section search.
 */
object FindSearch {
    private const val STEP_DAYS = 15.0 / 1440.0

    fun search(q: FindQuery, cancelled: () -> Boolean = { false }, progress: (Double) -> Unit = {}): List<FindResult> {
        require(q.azimuthTolerance >= 0 && q.altitudeTolerance >= 0)
        val t0 = q.start.toAstroTime().ut; val t1 = q.end.toAstroTime().ut
        if (t1 <= t0) return emptyList()
        val azTol = maxOf(q.azimuthTolerance, 1e-6)
        val altTol = maxOf(q.altitudeTolerance, 1e-6)
        val results = ArrayList<FindResult>()
        fun pos(ut: Double) = Sky.position(q.body, Time(ut), q.where)
        fun dAz(ut: Double) = angleDiff(pos(ut).azimuth, q.azimuth)
        fun cost(ut: Double): Double {
            val p = pos(ut)
            val a = angleDiff(p.azimuth, q.azimuth) / azTol
            val e = if (q.altitude == null) 0.0 else (p.altitude - q.altitude) / altTol
            return a * a + e * e
        }
        var prevUt = t0
        var prev = dAz(t0)
        var prevPrev = Double.NaN
        var ut = t0
        var lastResultUt = -1e9
        while (ut < t1 && results.size < q.maxResults) {
            if (cancelled()) break
            val next = minOf(ut + STEP_DAYS, t1)
            val cur = dAz(next)
            // Candidate: sign change (true crossing, not a ±180 wrap) or a local minimum of |Δaz| within tolerance.
            val crossing = (prev <= 0 && cur > 0 || prev >= 0 && cur < 0) && abs(prev - cur) < 90
            val localMin = !prevPrev.isNaN() && abs(prev) <= azTol && abs(prev) <= abs(prevPrev) && abs(prev) <= abs(cur)
            if (crossing || localMin) {
                val center = if (crossing) bisect(prevUt, next) { dAz(it) } else prevUt
                if (center - lastResultUt > 2.0 / 24.0) {
                    // Window where |Δaz| ≤ tol around the centre (search ±3 h).
                    val lo = edge(center, center - 0.125) { abs(dAz(it)) <= azTol }
                    val hi = edge(center, center + 0.125) { abs(dAz(it)) <= azTol }
                    val best = golden(lo, hi, ::cost)
                    val p = pos(best)
                    val okAz = abs(angleDiff(p.azimuth, q.azimuth)) <= azTol + 1e-9
                    val okAlt = if (q.altitude == null) p.altitude > -1.0 else abs(p.altitude - q.altitude) <= altTol + 1e-9
                    if (okAz && okAlt && best >= t0 && best <= t1) {
                        val inst = Time(best).toInstant()
                        results.add(
                            FindResult(
                                inst, p.azimuth, p.altitude, DayEvents.lightType(inst, q.where),
                                if (q.body == SkyBody.MOON) MoonCalc.info(inst, q.where).illumination else null,
                            )
                        )
                        lastResultUt = best
                    }
                }
            }
            prevPrev = prev
            prev = cur; prevUt = next; ut = next
            progress((ut - t0) / (t1 - t0))
        }
        return results
    }

    private fun bisect(a0: Double, b0: Double, f: (Double) -> Double): Double {
        var a = a0; var b = b0; var fa = f(a)
        repeat(30) {
            val m = (a + b) / 2; val fm = f(m)
            if ((fa <= 0) == (fm <= 0)) { a = m; fa = fm } else b = m
        }
        return (a + b) / 2
    }

    /** Farthest point from [inside] towards [limit] where [pred] still holds (assumes a single transition). */
    private fun edge(inside: Double, limit: Double, pred: (Double) -> Boolean): Double {
        if (pred(limit)) return limit
        var a = inside; var b = limit
        repeat(25) { val m = (a + b) / 2; if (pred(m)) a = m else b = m }
        return a
    }

    private fun golden(a0: Double, b0: Double, f: (Double) -> Double): Double {
        if (b0 - a0 < 1e-7) return (a0 + b0) / 2
        val g = (sqrt(5.0) - 1) / 2
        var a = a0; var b = b0
        var c = b - g * (b - a); var d = a + g * (b - a)
        var fc = f(c); var fd = f(d)
        repeat(40) {
            if (fc < fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = f(c) }
            else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = f(d) }
        }
        val m = (a + b) / 2
        // Endpoints may be better if the minimum is at the window edge.
        return listOf(a0, b0, m).minBy(f)
    }
}
