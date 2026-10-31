package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Time

/** A threshold crossing of a sampled function. */
internal data class Crossing(val time: Time, val threshold: Double, val rising: Boolean)

/**
 * Finds all crossings of [thresholds] by f(t) on [start, end] by sampling every [stepDays] and refining
 * by bisection to ~0.1 s.
 */
internal fun findCrossings(start: Time, end: Time, stepDays: Double, thresholds: List<Double>, f: (Time) -> Double): List<Crossing> {
    val out = ArrayList<Crossing>()
    val n = maxOf(1, Math.ceil((end.ut - start.ut) / stepDays).toInt())
    val times = DoubleArray(n + 1) { start.ut + (end.ut - start.ut) * it / n }
    val values = DoubleArray(n + 1) { f(Time(times[it])) }
    for (i in 0 until n) {
        for (th in thresholds) {
            val a = values[i] - th; val b = values[i + 1] - th
            if (a == 0.0 && i > 0) continue // counted at previous interval end
            if ((a < 0 && b >= 0) || (a >= 0 && b < 0)) {
                var lo = times[i]; var hi = times[i + 1]; var flo = a
                repeat(40) {
                    if (hi - lo < 1e-6) return@repeat
                    val mid = (lo + hi) / 2
                    val fm = f(Time(mid)) - th
                    if ((flo < 0) == (fm < 0)) { lo = mid; flo = fm } else hi = mid
                }
                out.add(Crossing(Time((lo + hi) / 2), th, b > a))
            }
        }
    }
    out.sortBy { it.time.ut }
    return out
}
