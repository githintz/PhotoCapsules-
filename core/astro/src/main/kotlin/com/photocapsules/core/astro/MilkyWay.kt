package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.rotationEqjHor
import io.github.cosinekitty.astronomy.rotationGalEqj
import java.time.Instant

/** Milky Way / Galactic Center geometry. */
object MilkyWay {
    /** Sgr A* (IAU galactic centre), J2000: RA 17h45m40.04s, Dec −29°00′28.1″. */
    const val GC_RA_HOURS = 17.0 + 45.0 / 60.0 + 40.04 / 3600.0
    const val GC_DEC_DEG = -(29.0 + 0.0 / 60.0 + 28.1 / 3600.0)

    fun galacticCenter(instant: Instant, where: GeoPoint): HorizontalCoord = Sky.fixedPoint(GC_RA_HOURS, GC_DEC_DEG, instant, where)

    internal fun gcTrueAltitude(t: Time, where: GeoPoint) = Sky.fixedPoint(GC_RA_HOURS, GC_DEC_DEG, t, where, refraction = false).altitude

    /** Points of the galactic equator (b = 0) at the given time, in horizontal coordinates, every [stepDeg] of longitude. */
    fun arch(instant: Instant, where: GeoPoint, stepDeg: Double = 2.0): List<Pair<Double, HorizontalCoord>> {
        val t = instant.toAstroTime()
        val rot = rotationGalEqj() combine rotationEqjHor(t, where.toObserver())
        val out = ArrayList<Pair<Double, HorizontalCoord>>()
        var l = 0.0
        while (l < 360.0) {
            out.add(l to Sky.toHorizontal(rot.rotate(Sky.unitVector(l, 0.0, t)), refraction = true))
            l += stepDeg
        }
        return out
    }

    data class ArchInfo(
        /** Highest point of the galactic equator (centre of the visible arch). */
        val highestPoint: HorizontalCoord,
        val galacticLongitudeOfHighest: Double,
        /** Azimuths where the galactic equator crosses the horizon. */
        val horizonCrossings: List<Double>,
    )

    fun archInfo(instant: Instant, where: GeoPoint): ArchInfo {
        val pts = arch(instant, where, 0.5)
        val best = pts.maxBy { it.second.altitude }
        val crossings = ArrayList<Double>()
        for (i in pts.indices) {
            val a = pts[i].second; val b = pts[(i + 1) % pts.size].second
            if ((a.altitude < 0) != (b.altitude < 0)) {
                val f = a.altitude / (a.altitude - b.altitude)
                crossings.add(normalizeDegrees(a.azimuth + f * angleDiff(b.azimuth, a.azimuth)))
            }
        }
        return ArchInfo(best.second, best.first, crossings.sorted())
    }

    data class Visibility(val start: TimedEventLite, val end: TimedEventLite)
    data class TimedEventLite(val time: Instant, val azimuth: Double, val altitude: Double, val openEnded: Boolean)

    /**
     * Intervals within [start, end] when the Galactic Center is above the horizon while the Sun is below
     * −18° (astronomical night) — the PhotoPills "Galactic Center visibility" definition.
     */
    fun visibility(start: Instant, end: Instant, where: GeoPoint, sunLimit: Double = -18.0, gcMinAltitude: Double = 0.0): List<Visibility> {
        val t0 = start.toAstroTime(); val t1 = end.toAstroTime()
        // g(t) = min(gcAlt - gcMin, sunLimit - sunAlt) > 0 ⇔ visible.
        val g = { t: Time -> minOf(gcTrueAltitude(t, where) - gcMinAltitude, sunLimit - Sky.sunTrueAltitude(t, where)) }
        val crossings = findCrossings(t0, t1, 3.0 / 1440.0, listOf(0.0), g)
        val out = ArrayList<Visibility>()
        var inside = g(t0) > 0
        var s: Time? = if (inside) t0 else null
        var sOpen = inside
        for (c in crossings) {
            if (c.rising && !inside) { inside = true; s = c.time; sOpen = false }
            else if (!c.rising && inside) { out.add(Visibility(lite(s!!, where, sOpen), lite(c.time, where, false))); inside = false }
        }
        if (inside && s != null) out.add(Visibility(lite(s, where, sOpen), lite(t1, where, true)))
        return out
    }

    private fun lite(t: Time, where: GeoPoint, open: Boolean): TimedEventLite {
        val p = Sky.fixedPoint(GC_RA_HOURS, GC_DEC_DEG, t, where)
        return TimedEventLite(t.toInstant(), p.azimuth, p.altitude, open)
    }

    data class RiseSet(val time: Instant, val azimuth: Double, val rising: Boolean)

    /** Galactic Center rise/set events in [start, end] (apparent horizon: 34′ refraction). */
    fun riseSet(start: Instant, end: Instant, where: GeoPoint): List<RiseSet> =
        findCrossings(start.toAstroTime(), end.toAstroTime(), 5.0 / 1440.0, listOf(-34.0 / 60.0)) { gcTrueAltitude(it, where) }
            .map { c -> RiseSet(c.time.toInstant(), Sky.fixedPoint(GC_RA_HOURS, GC_DEC_DEG, c.time, where, refraction = false).azimuth, c.rising) }

    /** "Moon energy" bar of PhotoPills: 1 = no moonlight (new Moon or Moon down), 0 = full Moon up. */
    fun darkness(instant: Instant, where: GeoPoint): Double {
        val m = MoonCalc.info(instant, where)
        return if (m.position.altitude < 0) 1.0 else 1.0 - m.illumination
    }
}
