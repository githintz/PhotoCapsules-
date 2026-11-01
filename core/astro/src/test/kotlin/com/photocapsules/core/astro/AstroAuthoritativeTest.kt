package com.photocapsules.core.astro

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Spot checks against USNO, NASA (F. Espenak) and IMO published values. */
class AstroAuthoritativeTest {
    private val a = Golden.load("astro_authoritative.json")
    private val deltaT = 69.5 // s, 2025–2030

    private fun hm(date: LocalDate, s: String) = LocalDateTime.of(date, LocalTime.parse(s)).toInstant(ZoneOffset.UTC)

    @Test fun usnoRiseSetTwilight() {
        for (r in a["usno_rstt"]!!.jsonArray.map { it.o() }) {
            val where = GeoPoint(r.d("lat"), r.d("lon"), 0.0)
            val date = LocalDate.parse(r.s("date"))
            val sun = DayEvents.sun(date, ZoneOffset.UTC, where)
            val moon = DayEvents.moon(date, ZoneOffset.UTC, where)
            fun check(label: String, ours: Instant?) {
                assertNotNull(ours, label)
                assertTrue(minutesBetween(ours, hm(date, r.s(label)!!)) <= 1.0, "$label ours=$ours usno=${r.s(label)}")
            }
            check("sunrise", sun.sunrise?.time); check("sunset", sun.sunset?.time); check("sunTransit", sun.transit?.time)
            check("civilBegin", sun.civilDawn); check("civilEnd", sun.civilDusk)
            check("moonrise", moon.moonrise?.time); check("moonset", moon.moonset?.time); check("moonTransit", moon.transit?.time)
        }
    }

    @Test fun usnoMoonPhases2026() {
        val ours = MoonCalendar.phases(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))
        val ref = a["usno_phases_2026"]!!.jsonArray.map { it.jsonArray }
        assertEquals(ref.size, ours.size)
        for ((r, o) in ref.zip(ours)) {
            assertEquals(r[1].jsonPrimitive.content, o.phase.name)
            // USNO publishes times rounded to the nearest minute.
            assertTrue(minutesBetween(parseUtc(r[0].jsonPrimitive.content), o.time) <= 0.6, "$r vs ${o.time}")
        }
    }

    @Test fun usnoSeasons2026() {
        val s = MoonCalendar.seasons(2026)
        val r = a["usno_seasons_2026"]!!.o()
        for ((k, v) in listOf("march" to s.marchEquinox, "june" to s.juneSolstice, "september" to s.septemberEquinox, "december" to s.decemberSolstice))
            assertTrue(minutesBetween(parseUtc(r.s(k)!!), v) <= 1.5, "$k usno=${r.s(k)} ours=$v")
    }

    @Test fun nasaLunarEclipses() {
        val ours = Eclipses.lunarEclipses(Instant.parse("2025-01-01T00:00:00Z"), 14)
        val ref = a["nasa_lunar"]!!.jsonArray.map { it.o() }
        assertEquals(ref.size, ours.size)
        for ((r, o) in ref.zip(ours)) {
            val ut = parseUtc(r.s("td")!!).minusMillis((deltaT * 1000).toLong())
            assertTrue(minutesBetween(ut, o.peak) < 2.0, "peak $r vs ${o.peak}")
            assertEquals(r.s("type"), o.type.name, "type $r")
            assertEquals(r.d("umbral"), o.umbralMagnitude, 0.02, "umbral magnitude $r")
        }
        val mar = ours.first { it.peak.toString().startsWith("2026-03-03") }
        val u1 = mar.contacts.first { it.code == "U1" }.time; val u4 = mar.contacts.first { it.code == "U4" }.time
        val u2 = mar.contacts.first { it.code == "U2" }.time; val u3 = mar.contacts.first { it.code == "U3" }.time
        assertEquals(207.0, minutesBetween(u1, u4), 3.0)
        assertEquals(58.0, minutesBetween(u2, u3), 3.0)
    }

    @Test fun nasaSolarEclipses() {
        val ours = Eclipses.solarEclipses(Instant.parse("2025-01-01T00:00:00Z"), 14)
        val ref = a["nasa_solar"]!!.jsonArray.map { it.o() }
        for ((r, o) in ref.zip(ours)) {
            val ut = parseUtc(r.s("td")!!).minusMillis((deltaT * 1000).toLong())
            assertTrue(minutesBetween(ut, o.peak) < 2.0, "peak $r vs ${o.peak}")
            assertEquals(r.s("type"), o.type.name, "type $r")
            if (r.containsKey("geLat")) {
                assertEquals(r.d("geLat"), o.greatestEclipse!!.latitude, 0.3)
                assertEquals(r.d("geLon"), o.greatestEclipse!!.longitude, 0.3)
            }
        }
    }

    @Test fun nasaPathOfTotality2026() {
        val ecl = Eclipses.solarEclipses(Instant.parse("2026-08-01T00:00:00Z"), 1).single()
        val map = SolarEclipseMap.forEclipse(ecl)
        for (r in a["nasa_path_2026aug12"]!!.jsonArray.map { it.o() }) {
            // Central line point: magnitude should be at its local maximum (> 1); limits ≈ 1.0.
            val c = map.maxMagnitude(r.d("cLat"), r.d("cLon"))
            // Partial-phase formula (rS+rM−sep)/2rS peaks at (1+rM/rS)/2 ≈ 1.0195 on the central line.
            assertTrue(c > 1.015, "central magnitude $c at $r")
            val n = map.maxMagnitude(r.d("nLat"), r.d("nLon"))
            val s = map.maxMagnitude(r.d("sLat"), r.d("sLon"))
            assertEquals(1.0, n, 0.004, "north limit magnitude at $r")
            assertEquals(1.0, s, 0.004, "south limit magnitude at $r")
        }
        // Local circumstances on the central line: magnitude = ratio of apparent diameters (NASA: 1.039).
        val onLine = Eclipses.solarLocal(ecl, GeoPoint(65.1717, -25.205))!!
        assertEquals(EclipseType.TOTAL, onLine.type)
        assertEquals(1.039, onLine.magnitude, 0.004)
        val data = map.compute(ecl, levels = listOf(0.0, 0.5))
        assertTrue(data.centralLine.size > 20)
        assertTrue(data.umbraNorthLimit.size > 10 && data.umbraSouthLimit.size > 10)
        // Spain is crossed by the path: some central-line point is within the Iberian peninsula box.
        assertTrue(data.centralLine.any { it.latitude in 38.0..44.0 && it.longitude in -9.0..3.5 })
        assertTrue(data.partialContours.getValue(0.0).size > 40)
    }

    @Test fun imoShowerPeaks2026() {
        for (p in a["imo_peaks_2026"]!!.jsonArray.map { it.jsonArray }) {
            val sh = MeteorShowers.byCode(p[0].jsonPrimitive.content)!!
            val occ = MeteorShowers.occurrence(sh, 2026)
            val date = occ.peak.atZone(ZoneOffset.UTC).toLocalDate()
            val ref = LocalDate.parse(p[1].jsonPrimitive.content)
            assertTrue(kotlin.math.abs(date.toEpochDay() - ref.toEpochDay()) <= 1, "${sh.code} ours=$date imo=$ref")
        }
    }

    @Test fun usnoIllumination() {
        // USNO fracillum (Madrid 2026-10-31, 65 %) — Moon waning gibbous during that UT day.
        val i = MoonCalc.info(Instant.parse("2026-10-31T12:00:00Z"), GeoPoint(40.4168, -3.7038))
        assertEquals(0.65, i.illumination, 0.04)
        assertEquals(MoonPhaseName.WANING_GIBBOUS, i.phase)
    }
}
