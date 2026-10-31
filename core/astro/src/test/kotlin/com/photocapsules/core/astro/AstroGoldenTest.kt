package com.photocapsules.core.astro

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Agreement with PyEphem (independent VSOP87/ELP2000 implementation). */
class AstroGoldenTest {
    private val g = Golden.load("astro_golden_pyephem.json")

    @Test fun sunAndMoonPositions() {
        var n = 0
        for (c in g["positions"]!!.jsonArray.map { it.o() }) {
            val where = c.geo(); val t = parseUtc(c.s("utc")!!)
            val sun = Sky.position(SkyBody.SUN, t, where)
            val moon = Sky.position(SkyBody.MOON, t, where)
            assertEquals(c.d("sunAlt"), sun.trueAltitude, 0.01, "sun alt $c")
            if (abs(c.d("sunAlt")) < 85) assertTrue(angDist(c.d("sunAz"), sun.azimuth) < 0.02, "sun az $c -> ${sun.azimuth}")
            assertEquals(c.d("moonAlt"), moon.trueAltitude, 0.02, "moon alt $c")
            if (abs(c.d("moonAlt")) < 85) assertTrue(angDist(c.d("moonAz"), moon.azimuth) < 0.04, "moon az $c -> ${moon.azimuth}")
            assertEquals(c.d("moonDistKm"), moon.distanceKm, 15.0, "moon topocentric distance $c")
            assertEquals(c.d("sunDistKm"), sun.distanceKm, c.d("sunDistKm") * 2e-5, "sun distance $c")
            val info = MoonCalc.info(t, where)
            assertEquals(c.d("moonIllum"), info.illumination, 0.003, "illumination $c")
            n++
        }
        assertTrue(n > 300)
    }

    @Test fun riseSetTimes() {
        var compared = 0
        for (c in g["risesets"]!!.jsonArray.map { it.o() }) {
            val where = c.geo(); val from = parseUtc(c.s("fromUtc")!!)
            val polar = abs(where.latitude) > 65
            for ((body, prefix) in listOf(SkyBody.SUN to "sun", SkyBody.MOON to "moon")) {
                for ((rise, k) in listOf(true to "Rise", false to "Set")) {
                    val ref = c.s(prefix + k)?.let { parseUtc(it) }
                    val ours = DayEvents.nextRiseSet(body, rise, from, where, limitDays = 2.0)
                    if (ref == null) continue // PyEphem reports circumpolar for the coming day
                    if (ours == null) { assertTrue(polar, "missing $prefix$k $c"); continue }
                    val tol = if (polar) 20.0 else if (body == SkyBody.MOON) 1.5 else 1.0
                    assertTrue(minutesBetween(ref, ours.time) < tol, "$prefix$k $c ours=${ours.time} ref=$ref")
                    compared++
                }
            }
        }
        assertTrue(compared > 300, "compared=$compared")
    }

    @Test fun twilightTimes() {
        var compared = 0
        for (c in g["twilights"]!!.jsonArray.map { it.o() }) {
            val where = c.geo(); val from = parseUtc(c.s("fromUtc")!!)
            val day = DayEvents.sun(from.atZone(ZoneOffset.UTC).toLocalDate(), ZoneOffset.UTC, where)
            val polar = abs(where.latitude) > 65
            val pairs = listOf(
                "tw6Dawn" to day.civilDawn, "tw6Dusk" to day.civilDusk, "tw12Dawn" to day.nauticalDawn,
                "tw12Dusk" to day.nauticalDusk, "tw18Dawn" to day.astronomicalDawn, "tw18Dusk" to day.astronomicalDusk,
            )
            for ((k, ours) in pairs) {
                val ref = c.s(k)?.let { parseUtc(it) } ?: continue
                if (ref.isAfter(from.plusSeconds(86400))) continue // falls on the next UTC day
                if (ours == null) { assertTrue(polar, "missing $k $c"); continue }
                assertTrue(minutesBetween(ref, ours) < (if (polar) 10.0 else 1.0), "$k $c ours=$ours ref=$ref")
                compared++
            }
        }
        assertTrue(compared > 250, "compared=$compared")
    }

    @Test fun moonPhases() {
        val ours = MoonCalendar.phases(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"))
        val ref = g["phases"]!!.jsonArray.map { it.o() }.filter { parseUtc(it.s("utc")!!) < Instant.parse("2028-01-01T00:00:00Z") }
        assertEquals(ref.size, ours.size)
        for ((r, o) in ref.zip(ours)) {
            assertEquals(r.s("phase"), o.phase.name)
            assertTrue(minutesBetween(parseUtc(r.s("utc")!!), o.time) < 1.0, "$r vs ${o.time}")
        }
    }

    @Test fun seasons() {
        for (r in g["seasons"]!!.jsonArray.map { it.o() }) {
            val s = MoonCalendar.seasons(r.d("year").toInt())
            assertTrue(minutesBetween(parseUtc(r.s("march")!!), s.marchEquinox) < 1.0)
            assertTrue(minutesBetween(parseUtc(r.s("june")!!), s.juneSolstice) < 1.0)
            assertTrue(minutesBetween(parseUtc(r.s("september")!!), s.septemberEquinox) < 1.0)
            assertTrue(minutesBetween(parseUtc(r.s("december")!!), s.decemberSolstice) < 1.0)
        }
    }

    @Test fun lunarApsides() {
        val ref = g["apsides"]!!.jsonArray.map { it.o() }
        val ours = MoonCalendar.apsides(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))
        for (r in ref) {
            val t = parseUtc(r.s("utc")!!)
            val m = ours.minBy { minutesBetween(it.time, t) }
            assertEquals(r.s("perigee").toBoolean(), m.perigee)
            assertTrue(minutesBetween(m.time, t) < 30, "apsis $r vs ${m.time}")
            assertEquals(r.d("distKm"), m.distanceKm, 20.0)
        }
    }

    @Test fun fixedStarsGalacticCenterPolaris() {
        for (r in g["fixed"]!!.jsonArray.map { it.o() }) {
            val where = r.geo(); val t = parseUtc(r.s("utc")!!)
            val gc = Sky.fixedPoint(MilkyWay.GC_RA_HOURS, MilkyWay.GC_DEC_DEG, t, where, refraction = false)
            assertEquals(r.d("gcAlt"), gc.altitude, 0.01, "GC alt $r")
            if (kotlin.math.abs(r.d("gcAlt")) < 75) assertTrue(angDist(r.d("gcAz"), gc.azimuth) < 0.02, "GC az $r")
            val p = Sky.fixedPoint(NightSky.POLARIS_RA_HOURS, NightSky.POLARIS_DEC, t, where, refraction = false)
            assertEquals(r.d("polAlt"), p.altitude, 0.01, "Polaris alt $r")
        }
    }

    @Test fun galacticCenterRiseSetAltitudeIsHorizon() {
        val where = GeoPoint(40.4168, -3.7038, 667.0)
        val ev = MilkyWay.riseSet(Instant.parse("2026-07-14T00:00:00Z"), Instant.parse("2026-07-16T00:00:00Z"), where)
        assertTrue(ev.size >= 3, "$ev")
        for (e in ev) {
            assertEquals(0.0, Sky.fixedPoint(MilkyWay.GC_RA_HOURS, MilkyWay.GC_DEC_DEG, e.time, where).altitude, 0.05)
            // GC (dec −29°) rises in the SE and sets in the SW from 40°N
            assertTrue(if (e.rising) e.azimuth in 120.0..150.0 else e.azimuth in 210.0..240.0, "$e")
        }
    }

    @Test fun solarLongitudeJ2000() {
        for (r in g["solarLongitude"]!!.jsonArray.map { it.o() }) {
            val ours = MeteorShowers.solarLongitudeJ2000(parseUtc(r.s("utc")!!))
            // PyEphem value is astrometric (no aberration, −20.5″); allow 0.01°.
            assertTrue(angDist(r.d("lambda"), ours) < 0.01, "λ $r -> $ours")
        }
    }
}
