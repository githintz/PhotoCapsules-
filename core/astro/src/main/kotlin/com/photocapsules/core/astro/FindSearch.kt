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
    val maxResults: Int = DEFAULT_MAX_RESULTS,
) {
    companion object { const val DEFAULT_MAX_RESULTS = 5000 }
}

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
 * Scans azimuth crossings (and near-misses) at a coarse step; each window where |Δaz| ≤ tolerance is then
 * scanned for times meeting BOTH tolerances (box constraint), and the best such time is reported.
 */
object FindSearch {
    private const val STEP_DAYS = 15.0 / 1440.0
    private const val EDGE_STEP_DAYS = 4.0 / 1440.0

    fun search(q: FindQuery, cancelled: () -> Boolean = { false }, progress: (Double) -> Unit = {}): List<FindResult> {
        require(q.azimuthTolerance >= 0 && q.altitudeTolerance >= 0)
        val t0 = q.start.toAstroTime().ut; val t1 = q.end.toAstroTime().ut
        if (t1 <= t0) return emptyList()
        // A zero tolerance means "exact": use 0.0001° (0.36″), far below what the app can display, so windows never collapse.
        val azTol = maxOf(q.azimuthTolerance, 1e-4)
        val altTol = maxOf(q.altitudeTolerance, 1e-4)
        val results = ArrayList<FindResult>()
        fun pos(ut: Double) = Sky.position(q.body, Time(ut), q.where)
        fun dAz(ut: Double) = angleDiff(pos(ut).azimuth, q.azimuth)
        // Chebyshev (box) error: ≤ 1 means both constraints are met. Altitude-only mode requires alt > −1°.
        fun boxError(p: BodyPosition): Double {
            val a = abs(angleDiff(p.azimuth, q.azimuth)) / azTol
            val e = if (q.altitude == null) (if (p.altitude > -1.0) 0.0 else 1.0 + (-1.0 - p.altitude)) else abs(p.altitude - q.altitude) / altTol
            return maxOf(a, e)
        }
        var prevUt = t0
        var prev = dAz(t0)
        var prevPrevUt = Double.NaN
        var prevPrev = Double.NaN
        var ut = t0
        var lastResultUt = -1e9
        var lastResultErr = Double.MAX_VALUE
        var scannedUntil = -1e9
        // True while the azimuth has stayed inside the tolerance since the end of the last window
        // (a pass longer than the ±3 h window): further windows then refine that pass instead of adding results.
        var passOpen = false
        // Whether the currently open pass already produced a result (only then may later windows replace it).
        var passHasResult = false
        fun processWindow(center: Double) {
            if (center <= scannedUntil) return
            val inside = { t: Double -> abs(dAz(t)) <= azTol }
            // Window where |Δaz| ≤ tol around the centre (search ±3 h), then scan it for feasible times.
            // Clamp to the requested range and to what earlier windows already scanned (no duplicates).
            val loEdge = edge(center, center - 0.125, inside)
            val hiEdge = edge(center, center + 0.125, inside)
            val lo = maxOf(loEdge, t0, scannedUntil)
            val hi = minOf(hiEdge, t1)
            val continuation = passOpen && passHasResult
            if (!passOpen) passHasResult = false
            passOpen = hiEdge >= center + 0.125 && hi < t1
            scannedUntil = maxOf(scannedUntil, hi)
            if (hi < lo) return
            val n = if (hi > lo) maxOf(8, minOf(400, ((hi - lo) / (20.0 / 86400.0)).toInt())) else 1
            var bestUt = center.coerceIn(lo, hi); var bestErr = boxError(pos(bestUt))
            for (i in 0..n) {
                val tt = lo + (hi - lo) * i / n
                val err = boxError(pos(tt))
                if (err < bestErr) { bestErr = err; bestUt = tt }
            }
            // Local refinement around the best sample.
            if (hi > lo) {
                val stepW = (hi - lo) / n
                val refined = goldenMin(maxOf(lo, bestUt - stepW), minOf(hi, bestUt + stepW)) { boxError(pos(it)) }
                val refinedErr = boxError(pos(refined))
                if (refinedErr <= bestErr) { bestUt = refined; bestErr = refinedErr }
            }
            if (bestErr > 1.0 + 1e-9 || bestUt < t0 || bestUt > t1) return
            val p = pos(bestUt)
            val inst = Time(bestUt).toInstant()
            val result = FindResult(
                inst, p.azimuth, p.altitude, DayEvents.lightType(inst, q.where),
                if (q.body == SkyBody.MOON) MoonCalc.info(inst, q.where).illumination else null,
            )
            if (continuation && results.isNotEmpty()) {
                // Same long pass as the previous result: keep the better of the two.
                if (bestErr < lastResultErr) { results[results.size - 1] = result; lastResultUt = bestUt; lastResultErr = bestErr }
            } else if (bestUt - lastResultUt > 60.0 / 86400.0) {
                results.add(result); lastResultUt = bestUt; lastResultErr = bestErr
                passHasResult = true
            }
        }

        // The range may start while the body is already inside the azimuth tolerance.
        if (abs(prev) <= azTol) processWindow(t0)
        while (ut < t1 && results.size < q.maxResults) {
            if (cancelled()) break
            val next = minOf(ut + STEP_DAYS, t1)
            val cur = dAz(next)
            if (abs(cur) > azTol) { passOpen = false; passHasResult = false }
            // Candidates: a true crossing of the target azimuth (not a ±180 wrap) or a local minimum of |Δaz|
            // (azimuth hovering near the target without crossing it).
            var crossing = (prev <= 0 && cur > 0 || prev >= 0 && cur < 0) && abs(prev - cur) < 90
            var crossingCenter = Double.NaN
            // Near the zenith the azimuth can swing > 90° between coarse samples: subdivide to find a real crossing.
            if (!crossing && abs(prev - cur) >= 90 && maxOf(pos(prevUt).altitude, pos(next).altitude) > 45.0) {
                val n = 60
                var a = prevUt; var da = prev
                for (k in 1..n) {
                    val b = prevUt + (next - prevUt) * k / n
                    val db = dAz(b)
                    if ((da <= 0 && db > 0 || da >= 0 && db < 0) && abs(da - db) < 90) {
                        crossing = true; crossingCenter = bisect(a, b) { dAz(it) }; break
                    }
                    a = b; da = db
                }
            }
            val localMin = !prevPrev.isNaN() && abs(prev) <= abs(prevPrev) && abs(prev) <= abs(cur) && abs(prev) < azTol + 2.0
            var center = Double.NaN
            if (crossing) center = if (!crossingCenter.isNaN()) crossingCenter else bisect(prevUt, next) { dAz(it) }
            else if (localMin) {
                val m = goldenMin(prevPrevUt, next) { abs(dAz(it)) }
                if (abs(dAz(m)) <= azTol) center = m
            }
            // Azimuth lingering inside the tolerance without crossing or a minimum (e.g. the Sun rising
            // vertically at the equator on an equinox).
            if (center.isNaN() && abs(cur) <= azTol) center = next
            if (!center.isNaN()) processWindow(center)
            prevPrev = prev; prevPrevUt = prevUt
            prev = cur; prevUt = next; ut = next
            progress((ut - t0) / (t1 - t0))
        }
        return results
    }

    private fun goldenMin(a0: Double, b0: Double, f: (Double) -> Double): Double {
        var a = a0; var b = b0
        val g = (sqrt(5.0) - 1) / 2
        var c = b - g * (b - a); var d = a + g * (b - a)
        var fc = f(c); var fd = f(d)
        repeat(40) {
            if (fc < fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = f(c) }
            else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = f(d) }
        }
        return (a + b) / 2
    }

    private fun bisect(a0: Double, b0: Double, f: (Double) -> Double): Double {
        var a = a0; var b = b0; var fa = f(a)
        repeat(42) {
            val m = (a + b) / 2; val fm = f(m)
            if ((fa <= 0) == (fm <= 0)) { a = m; fa = fm } else b = m
        }
        return (a + b) / 2
    }

    /**
     * Farthest point from [inside] towards [limit] up to which [pred] holds continuously: steps outward
     * (so a second, separate pass inside the ±limit span is not swallowed), then bisects the transition.
     */
    private fun edge(inside: Double, limit: Double, pred: (Double) -> Boolean): Double {
        val span = limit - inside
        val steps = maxOf(1, kotlin.math.ceil(abs(span) / EDGE_STEP_DAYS).toInt())
        var a = inside
        for (k in 1..steps) {
            val b = if (k == steps) limit else inside + span * k / steps
            if (!pred(b)) {
                var lo = a; var hi = b
                repeat(45) { val m = (lo + hi) / 2; if (pred(m)) lo = m else hi = m }
                return lo
            }
            a = b
        }
        return limit
    }

}
