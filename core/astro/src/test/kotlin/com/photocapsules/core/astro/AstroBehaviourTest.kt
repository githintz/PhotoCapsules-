package com.photocapsules.core.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behavioural and edge-case tests. */
class AstroBehaviourTest {
    private val madrid = GeoPoint(40.4168, -3.7038, 667.0)
    private val madridZone = ZoneId.of("Europe/Madrid")

    @Test fun dayOrderingAndLightIntervals() {
        val d = DayEvents.sun(LocalDate.of(2026, 10, 31), madridZone, madrid)
        val rise = d.sunrise!!.time; val set = d.sunset!!.time; val noon = d.transit!!.time
        assertTrue(d.astronomicalDawn!! < d.nauticalDawn!! && d.nauticalDawn!! < d.civilDawn!! && d.civilDawn!! < rise)
        assertTrue(rise < noon && noon < set && set < d.civilDusk!! && d.civilDusk!! < d.nauticalDusk!! && d.nauticalDusk!! < d.astronomicalDusk!!)
        assertEquals(2, d.goldenHours.size); assertEquals(2, d.blueHours.size)
        // Morning blue hour ends where golden hour begins (−4°), and golden hour contains sunrise.
        assertEquals(d.blueHours[0].end, d.goldenHours[0].start)
        assertTrue(d.goldenHours[0].start!! < rise && rise < d.goldenHours[0].end!!)
        assertTrue(d.blueHours[0].start!! == d.civilDawn) // −6°
        val daylight = (set.toEpochMilli() - rise.toEpochMilli()) / 1000.0
        assertEquals(daylight, d.daylightSeconds, 1.0)
        assertTrue(d.sunrise!!.azimuth in 100.0..115.0 && d.sunset!!.azimuth in 245.0..260.0)
    }

    @Test fun polarDayAndNight() {
        val svalbard = GeoPoint(78.2232, 15.6267, 10.0)
        val june = DayEvents.sun(LocalDate.of(2026, 6, 21), ZoneOffset.UTC, svalbard)
        assertTrue(june.alwaysUp); assertNull(june.sunrise); assertNull(june.sunset)
        assertEquals(86400.0, june.daylightSeconds, 1.0)
        assertTrue(june.goldenHours.isEmpty() || june.goldenHours.all { it.start != null || it.end != null })
        val dec = DayEvents.sun(LocalDate.of(2026, 12, 21), ZoneOffset.UTC, svalbard)
        assertTrue(dec.alwaysDown); assertEquals(0.0, dec.daylightSeconds)
        // Polar night at 78°N in December: Sun stays below −12° → nautical dawn exists? (min alt ≈ −34°, max ≈ −11.5°)
        assertNull(dec.civilDawn)
    }

    @Test fun dstTransitionDays() {
        // 2026-03-29: Europe/Madrid day lasts 23 h; 2026-10-25: 25 h. Events must still be found.
        for (date in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            val d = DayEvents.sun(date, madridZone, madrid)
            assertNotNull(d.sunrise); assertNotNull(d.sunset)
            val (s, e) = DayEvents.dayWindow(date, madridZone)
            assertTrue(d.sunrise!!.time >= s && d.sunset!!.time < e)
        }
        val (s1, e1) = DayEvents.dayWindow(LocalDate.of(2026, 3, 29), madridZone)
        assertEquals(23 * 3600L, (e1.epochSecond - s1.epochSecond))
    }

    @Test fun horizonHeightMakesSunriseEarlier() {
        val date = LocalDate.of(2026, 6, 1)
        val flat = DayEvents.sun(date, madridZone, madrid)
        val high = DayEvents.sun(date, madridZone, madrid, horizonHeightM = 1000.0)
        val dt = (flat.sunrise!!.time.epochSecond - high.sunrise!!.time.epochSecond) / 60.0
        // Dip ≈ 1.0° at 1000 m → ~6–8 minutes earlier at 40°N in June.
        assertTrue(dt in 4.0..10.0, "dt=$dt")
        assertTrue(high.sunset!!.time > flat.sunset!!.time)
    }

    @Test fun moonPhaseNamesAndOrientation() {
        val full = MoonCalendar.nextPhase(Instant.parse("2026-10-20T00:00:00Z"), PrincipalPhase.FULL)
        assertEquals("2026-10-26", full.time.toString().substring(0, 10))
        val info = MoonCalc.info(full.time, madrid)
        assertEquals(MoonPhaseName.FULL, info.phase)
        assertTrue(info.illumination > 0.99)
        assertTrue(info.ageDays in 14.0..16.0, "age ${info.ageDays}")
        val fq = MoonCalc.info(MoonCalendar.nextPhase(Instant.parse("2026-10-11T00:00:00Z"), PrincipalPhase.FIRST_QUARTER).time, madrid)
        assertEquals(MoonPhaseName.FIRST_QUARTER, fq.phase)
        assertTrue(fq.waxing)
        // Waxing Moon: bright limb towards the Sun (west, PA ≈ 270° ± 70°)
        assertTrue(abs(angleDiff(fq.brightLimbAngle, 270.0)) < 70, "chi=${fq.brightLimbAngle}")
        assertEquals(MoonPhaseName.WAXING_CRESCENT, MoonPhaseName.forPhaseAngle(45.0))
        assertEquals(MoonPhaseName.NEW, MoonPhaseName.forPhaseAngle(359.0))
        assertEquals(MoonPhaseName.WANING_CRESCENT, MoonPhaseName.forPhaseAngle(340.0))
    }

    @Test fun supermoons2026() {
        // Nolle 90 %-of-perigee rule: EarthSky / AccuWeather list 3 Jan, 24 Nov and 24 Dec 2026.
        val list = MoonCalendar.supermoons(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))
        val dates = list.map { it.time.toString().substring(0, 10) }
        assertEquals(listOf("2026-01-03", "2026-11-24", "2026-12-24"), dates)
        // Fixed 360,000 km rule only keeps 24 Dec.
        val fixed = MoonCalendar.phases(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))
            .filter { it.phase == PrincipalPhase.FULL && MoonCalendar.isSupermoon(it.time, it.distanceKm, SupermoonRule.Distance(360_000.0)) }
        assertEquals(listOf("2026-12-24"), fixed.map { it.time.toString().substring(0, 10) })
    }

    @Test fun moonBrightLimbPointsTowardsTheSunOnScreen() {
        // The lit limb must point along the great circle from the Moon to the Sun, measured clockwise from the zenith
        // on the observer's view (azimuth increases to the right when facing the Moon).
        for ((t, where) in listOf(
            Instant.parse("2026-01-21T18:00:00Z") to madrid,
            Instant.parse("2026-03-01T03:00:00Z") to GeoPoint(-33.87, 151.21),
            Instant.parse("2026-10-30T06:00:00Z") to GeoPoint(64.1, -21.9),
            Instant.parse("2026-07-20T20:00:00Z") to GeoPoint(0.0, 0.0),
        )) {
            val m = MoonCalc.info(t, where)
            val s = Sky.position(SkyBody.SUN, t, where)
            val a1 = Math.toRadians(m.position.trueAltitude); val a2 = Math.toRadians(s.trueAltitude)
            val dAz = Math.toRadians(s.azimuth - m.position.azimuth)
            val expected = normalizeDegrees(Math.toDegrees(kotlin.math.atan2(kotlin.math.sin(dAz) * kotlin.math.cos(a2),
                kotlin.math.cos(a1) * kotlin.math.sin(a2) - kotlin.math.sin(a1) * kotlin.math.cos(a2) * kotlin.math.cos(dAz))))
            assertTrue(abs(angleDiff(m.brightLimbFromZenith, expected)) < 1.0, "$t $where expected $expected got ${m.brightLimbFromZenith}")
        }
    }

    @Test fun belowSeaLevelObserverDoesNotCrash() {
        val deadSea = GeoPoint(31.5, 35.5, -430.0)
        assertNotNull(DayEvents.sun(LocalDate.of(2026, 6, 1), ZoneId.of("Asia/Jerusalem"), deadSea).sunrise)
        // Absurd altitudes (bad import / offsets) are clamped, not fatal.
        assertNotNull(DayEvents.sun(LocalDate.of(2026, 6, 1), ZoneOffset.UTC, GeoPoint(40.0, -3.0, -2000.0)).sunrise)
        assertNotNull(DayEvents.moon(LocalDate.of(2026, 6, 1), ZoneOffset.UTC, GeoPoint(40.0, -3.0, -2000.0)))
    }

    @Test fun nonFiniteElevationsAreSanitised() {
        val d = LocalDate.of(2026, 6, 1)
        assertNotNull(DayEvents.sun(d, ZoneOffset.UTC, GeoPoint(40.0, 0.0, Double.NaN)).sunrise)
        assertNotNull(DayEvents.sun(d, ZoneOffset.UTC, GeoPoint(40.0, 0.0, 10.0), horizonHeightM = Double.NaN).sunrise)
        assertNotNull(DayEvents.moon(d, ZoneOffset.UTC, GeoPoint(40.0, 0.0, Double.POSITIVE_INFINITY)))
    }

    @Test fun riseSetIndependentOfGroundElevation() {
        // USNO/PhotoPills convention: fixed 34′ refraction regardless of the observer's ground altitude.
        val low = DayEvents.sun(LocalDate.of(2026, 6, 1), ZoneOffset.UTC, GeoPoint(-0.18, -78.47, 0.0)).sunrise!!.time
        val high = DayEvents.sun(LocalDate.of(2026, 6, 1), ZoneOffset.UTC, GeoPoint(-0.18, -78.47, 2850.0)).sunrise!!.time
        assertTrue(minutesBetween(low, high) < 0.05, "${minutesBetween(low, high)}")
    }

    @Test fun showerActivityWindowsAreSaneEveryYear() {
        for (year in 2020..2035) for (sh in MeteorShowers.all) {
            val o = MeteorShowers.occurrence(sh, year)
            val peak = o.peak.atZone(ZoneOffset.UTC).toLocalDate()
            val span = o.activityEnd.toEpochDay() - o.activityStart.toEpochDay()
            assertTrue(span in 0..90, "${sh.code} $year span $span: ${o.activityStart}..${o.activityEnd}")
            assertTrue(!peak.isBefore(o.activityStart) && !peak.isAfter(o.activityEnd), "${sh.code} $year peak $peak outside ${o.activityStart}..${o.activityEnd}")
        }
        // June Bootids are not active in February
        assertTrue(MeteorShowers.active(Instant.parse("2028-02-29T00:00:00Z")).none { it.shower.code == "JBO" })
        assertTrue(MeteorShowers.active(Instant.parse("2028-06-25T00:00:00Z")).any { it.shower.code == "JBO" })
    }

    @Test fun comaeBerenicidsIncludesDlmRegion() {
        assertNull(MeteorShowers.byCode("DLM"))
        val com = MeteorShowers.occurrence(MeteorShowers.byCode("COM")!!, 2026)
        assertEquals(LocalDate.of(2027, 1, 31), com.activityEnd) // IMO 2026: detectable until Jan 31
        // Flat profile: same ZHR on 5 Jan as at the nominal maximum
        assertEquals(3.0, MeteorShowers.zhrAt(com, Instant.parse("2027-01-05T00:00:00Z")), 1e-9)
    }

    @Test fun galacticCenterVisibility() {
        // Madrid, new Moon week of 2026-07-14: GC visible in the evening after astronomical dusk.
        val where = GeoPoint(40.4168, -3.7038, 667.0)
        val start = Instant.parse("2026-07-14T12:00:00Z"); val end = Instant.parse("2026-07-15T12:00:00Z")
        val v = MilkyWay.visibility(start, end, where)
        assertEquals(1, v.size)
        val w = v.single()
        // Starts at astronomical dusk (~21:40 UT) and ends at GC set (~01:30 UT)
        val dusk = DayEvents.sun(LocalDate.of(2026, 7, 14), ZoneOffset.UTC, where).astronomicalDusk!!
        assertTrue(minutesBetween(w.start.time, dusk) < 1.0)
        assertTrue(abs(w.end.altitude) < 0.6 || w.end.time == DayEvents.sun(LocalDate.of(2026, 7, 15), ZoneOffset.UTC, where).astronomicalDawn)
        assertTrue(w.start.azimuth in 130.0..200.0, "${w.start.azimuth}")
        // In December the GC is not visible at night from Madrid
        assertTrue(MilkyWay.visibility(Instant.parse("2026-12-20T12:00:00Z"), Instant.parse("2026-12-21T12:00:00Z"), where).isEmpty())
        val arch = MilkyWay.archInfo(Instant.parse("2026-07-14T23:00:00Z"), where)
        assertEquals(2, arch.horizonCrossings.size)
        assertTrue(arch.highestPoint.altitude > 0)
    }

    @Test fun meteorShowersBehaviour() {
        val per = MeteorShowers.occurrence(MeteorShowers.byCode("PER")!!, 2026)
        assertEquals(LocalDate.of(2026, 7, 17), per.activityStart)
        assertEquals(LocalDate.of(2026, 8, 24), per.activityEnd)
        assertEquals(100.0, MeteorShowers.zhrAt(per, per.peak), 0.5)
        assertEquals(0.0, MeteorShowers.zhrAt(per, Instant.parse("2026-09-15T00:00:00Z")))
        val qua = MeteorShowers.occurrence(MeteorShowers.byCode("QUA")!!, 2026)
        assertEquals(LocalDate.of(2025, 12, 28), qua.activityStart)
        // Radiant of the Perseids is circumpolar-ish from 50°N; rate high before dawn at peak.
        val where = GeoPoint(50.0, 10.0)
        val predawn = per.peak.atZone(ZoneOffset.UTC).toLocalDate().atTime(2, 0).toInstant(ZoneOffset.UTC)
        val hr = MeteorShowers.hourlyRate(per, predawn, where)
        assertTrue(hr in 40.0..100.0, "hr=$hr")
        // IMO data corrections: LMI peaks around 24 Oct (λ 211°); Phoenicid radiant 8°/−27°
        val lmi = MeteorShowers.occurrence(MeteorShowers.byCode("LMI")!!, 2026)
        assertEquals(LocalDate.of(2026, 10, 24), lmi.peak.atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 27), lmi.activityEnd)
        assertEquals(LocalDate.of(2026, 12, 2), MeteorShowers.occurrence(MeteorShowers.byCode("PHO")!!, 2026).peak.atZone(ZoneOffset.UTC).toLocalDate())
        val active = MeteorShowers.active(Instant.parse("2026-08-12T00:00:00Z")).map { it.shower.code }
        assertTrue("PER" in active && "SDA" in active && "CAP" in active)
        assertTrue("GEM" !in active)
    }

    @Test fun findSunsetAzimuth() {
        // Find when the Sun sets at azimuth 299.3° ±1 at elevation 0±0.5 within 2026 from Madrid (≈ June solstice).
        val q = FindQuery(SkyBody.SUN, madrid, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), 299.3, 1.0, 0.0, 0.5)
        val r = FindSearch.search(q)
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { angDist(it.azimuth, 299.3) <= 1.0001 && abs(it.altitude) <= 0.5001 })
        assertTrue(r.all { it.time.atZone(ZoneOffset.UTC).monthValue in 5..7 }, "${r.map { it.time }}")
        // Moon search returns illumination
        val m = FindSearch.search(FindQuery(SkyBody.MOON, madrid, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"), 90.0, 3.0, 10.0, 1.0))
        assertTrue(m.isNotEmpty()); assertTrue(m.all { it.moonIllumination != null })
        // Impossible position (Sun at azimuth 0 / alt 60 from Madrid) → no results
        assertTrue(FindSearch.search(FindQuery(SkyBody.SUN, madrid, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 0.0, 2.0, 60.0, 1.0)).isEmpty())
    }

    @Test fun nightSkyAndProjection() {
        val t = Instant.parse("2026-07-14T23:00:00Z")
        val ncp = NightSky.northCelestialPole(t, madrid)
        assertEquals(madrid.latitude, ncp.altitude, 0.1) // + refraction ~0.02°
        assertTrue(angDist(ncp.azimuth, 0.0) < 0.5)
        val pol = NightSky.polaris(t, madrid)
        assertTrue(abs(pol.altitude - madrid.latitude) < 1.0)
        val eq = NightSky.declinationCircle(0.0, t, madrid)
        assertTrue(eq.maxOf { it.altitude } in 49.0..50.0) // 90 − 40.4
        val arc = NightSky.starTrailArc(HorizontalCoord(180.0, 30.0), t, madrid, 3600.0)
        assertEquals(15.04, angularSep(arc.first(), arc.last()) / kotlin.math.cos(Math.toRadians(NightSky.declinationAt(HorizontalCoord(180.0, 30.0), t, madrid))), 0.3)
        // Identity rotation (device flat, screen up): camera looks down (−Z) → altitude −90°.
        val ident = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertEquals(-90.0, ArProjection.cameraDirection(ident).altitude, 1e-6)
        // Device upright facing north: device Y = up, device −Z = north. R columns: X→E, Y→U, Z→−N
        val upright = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        val dir = ArProjection.cameraDirection(upright)
        assertEquals(0.0, dir.altitude, 1e-6); assertTrue(angDist(dir.azimuth, 0.0) < 1e-6)
        val center = ArProjection.project(upright, 0.0, 0.0, 60.0, 1000.0, 800.0)!!
        assertEquals(0.0, center.first, 1e-6); assertEquals(0.0, center.second, 1e-6)
        val up10 = ArProjection.project(upright, 0.0, 10.0, 60.0, 1000.0, 800.0)!!
        assertTrue(up10.second < 0) // above centre is negative y (screen coordinates)
        val east = ArProjection.project(upright, 10.0, 0.0, 60.0, 1000.0, 800.0)!!
        assertTrue(east.first > 0)
        assertNull(ArProjection.project(upright, 180.0, 0.0, 60.0, 1000.0, 800.0))
    }

    private fun angularSep(a: HorizontalCoord, b: HorizontalCoord): Double {
        val x = ArProjection.enu(a.azimuth, a.altitude); val y = ArProjection.enu(b.azimuth, b.altitude)
        return Math.toDegrees(kotlin.math.acos((x[0] * y[0] + x[1] * y[1] + x[2] * y[2]).coerceIn(-1.0, 1.0)))
    }

    @Test fun belowHorizonAltitudesHaveNoFictitiousRefraction() {
        assertEquals(-18.36, Sky.apparentAltitude(-18.36), 1e-12)
        assertEquals(-5.85, Sky.apparentAltitude(-5.85), 1e-12)
        // Continuous at −1° and standard ~0.57° refraction at the horizon
        assertEquals(Sky.apparentAltitude(-1.0), Sky.apparentAltitude(-1.0 - 1e-9), 1e-6)
        assertEquals(0.5, Sky.apparentAltitude(0.0), 0.1)
        // Twilight positions shown match the light type thresholds
        val t = Instant.parse("2026-10-31T19:45:00Z")
        val p = Sky.position(SkyBody.SUN, t, madrid)
        assertTrue(p.trueAltitude < -10 && abs(p.altitude - p.trueAltitude) < 1e-9)
    }

    @Test fun lightTypes() {
        assertEquals(LightType.DAY, LightType.forSunAltitude(10.0))
        assertEquals(LightType.GOLDEN_HOUR, LightType.forSunAltitude(0.0))
        assertEquals(LightType.GOLDEN_HOUR, LightType.forSunAltitude(-4.0))
        assertEquals(LightType.BLUE_HOUR, LightType.forSunAltitude(-5.0))
        assertEquals(LightType.NAUTICAL_TWILIGHT, LightType.forSunAltitude(-8.0))
        assertEquals(LightType.ASTRONOMICAL_TWILIGHT, LightType.forSunAltitude(-15.0))
        assertEquals(LightType.NIGHT, LightType.forSunAltitude(-20.0))
        assertEquals(0.0, Sky.shadowRatio(90.0)!!, 1e-12)
        assertEquals(1.0, Sky.shadowRatio(45.0)!!, 1e-12)
        assertNull(Sky.shadowRatio(-1.0))
    }

    @Test fun lunarEclipseLocalVisibility() {
        val e = Eclipses.lunarEclipses(Instant.parse("2026-01-01T00:00:00Z"), 1).single() // 2026-03-03 total
        assertEquals(EclipseType.TOTAL, e.type)
        assertEquals(7, e.contacts.size)
        // Visible from Honolulu/Pacific; not from Madrid (Moon below horizon at mid-eclipse ~11:34 UT)
        assertTrue(Eclipses.lunarLocal(e, GeoPoint(21.3, -157.86)).first { it.contact.code == "Max" }.visible)
        assertFalse(Eclipses.lunarLocal(e, madrid).first { it.contact.code == "Max" }.visible)
        // Sub-lunar point at Max: Moon at zenith there, ~90° altitude
        val sub = e.contacts.first { it.code == "Max" }.subLunarPoint
        assertTrue(Sky.position(SkyBody.MOON, e.contacts.first { it.code == "Max" }.time, GeoPoint(sub.latitude, sub.longitude)).trueAltitude > 88.5)
        assertEquals(181, Eclipses.horizonCircle(sub).size)
    }

    @Test fun localSolarEclipseSpain2026() {
        val e = Eclipses.solarEclipses(Instant.parse("2026-08-01T00:00:00Z"), 1).single()
        // Oviedo (inside the path) sees totality; Madrid (outside, near the southern limit) a deep partial
        val oviedo = Eclipses.solarLocal(e, GeoPoint(43.36, -5.85, 230.0))!!
        assertEquals(EclipseType.TOTAL, oviedo.type)
        assertTrue(oviedo.magnitude >= 1.0)
        val madridL = Eclipses.solarLocal(e, madrid)!!
        assertTrue(madridL.magnitude > 0.95, "${madridL.magnitude}")
        assertTrue(madridL.phases.first().time < madridL.phases.last().time)
        // Sydney: not visible
        val syd = Eclipses.solarLocal(e, GeoPoint(-33.87, 151.21))
        assertTrue(syd == null || !syd.visibleAboveHorizon)
    }

    @Test fun timelineIsSorted() {
        val ev = Timeline.events(LocalDate.of(2026, 7, 14), madridZone, madrid)
        assertTrue(ev.zipWithNext().all { (a, b) -> a.time <= b.time })
        assertTrue(ev.any { it.kind == EventKind.SUNRISE } && ev.any { it.kind == EventKind.GC_VISIBLE })
    }
}
