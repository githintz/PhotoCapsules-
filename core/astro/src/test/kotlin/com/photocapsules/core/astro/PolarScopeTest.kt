package com.photocapsules.core.astro

import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolarScopeTest {
    private val madrid = GeoPoint(40.4168, -3.7038, 667.0)
    private val sydney = GeoPoint(-33.8688, 151.2093, 20.0)
    private val t0 = Instant.parse("2026-11-13T21:00:00Z")

    /** Direction of the star from the pole, clockwise from straight up, derived from horizontal coordinates only. */
    private fun angleFromHorizon(where: GeoPoint, t: Instant, north: Boolean): Double {
        val star = if (north) Sky.fixedPoint(NightSky.POLARIS_RA_HOURS, NightSky.POLARIS_DEC, t, where, refraction = false)
        else Sky.fixedPoint(NightSky.SIGMA_OCT_RA_HOURS, NightSky.SIGMA_OCT_DEC, t, where, refraction = false)
        val pole = Sky.ofDatePoint(0.0, if (north) 90.0 else -90.0, t.toAstroTime(), where, refraction = false)
        val right = angleDiff(star.azimuth, pole.azimuth) * cos(Math.toRadians(pole.altitude)) // azimuth grows to the right
        val up = star.altitude - pole.altitude
        return normalizeDegrees(Math.toDegrees(atan2(right, up)))
    }

    @Test fun nakedEyeAngleMatchesHorizontalGeometry() {
        for (hours in 0 until 48 step 5) {
            val t = t0.plusSeconds(hours * 3600L)
            for ((where, north) in listOf(madrid to true, sydney to false)) {
                val v = PolarScope.compute(t, where)
                assertEquals(north, v.northern)
                val expected = angleFromHorizon(where, t, north)
                assertTrue(abs(angleDiff(v.nakedEyeAngleDeg, expected)) < 0.6, "$where $t: ${v.nakedEyeAngleDeg} vs $expected")
                assertEquals(0.0, angleDiff(v.scopeAngleDeg, v.nakedEyeAngleDeg + 180.0), 1e-9)
            }
        }
    }

    @Test fun upperTransitIsCulmination() {
        for ((where, north) in listOf(madrid to true, sydney to false)) {
            val v = PolarScope.compute(t0, where)
            val tr = v.nextUpperTransit
            assertTrue(tr.isAfter(t0.minusSeconds(1)) && tr.isBefore(t0.plusSeconds(86400)))
            val at = PolarScope.compute(tr, where)
            val ha = if (at.hourAngleHours > 12) at.hourAngleHours - 24 else at.hourAngleHours
            assertTrue(abs(ha) < 1.0 / 3600, "hour angle at transit $ha h")
            fun alt(i: Instant) = (if (north) Sky.fixedPoint(NightSky.POLARIS_RA_HOURS, NightSky.POLARIS_DEC, i, where, refraction = false)
            else Sky.fixedPoint(NightSky.SIGMA_OCT_RA_HOURS, NightSky.SIGMA_OCT_DEC, i, where, refraction = false)).altitude
            assertTrue(alt(tr) > alt(tr.minusSeconds(600)) && alt(tr) > alt(tr.plusSeconds(600)), "not a culmination")
            // Straight above the pole: 6 o'clock in an inverting scope.
            assertEquals("6:00", PolarScope.clockLabel(at.scopeAngleDeg))
        }
    }

    @Test fun poleDistances() {
        // Polaris is ~37′ from the pole in the 2020s (closest approach ~27′ around 2100); σ Oct ~1.1–1.2°.
        val n = PolarScope.compute(t0, madrid)
        assertTrue(n.poleDistanceDeg in 0.60..0.66, "Polaris ${n.poleDistanceDeg}")
        assertTrue(PolarScope.compute(Instant.parse("2100-01-01T00:00:00Z"), madrid).poleDistanceDeg in 0.44..0.47)
        val s = PolarScope.compute(t0, sydney)
        assertTrue(s.poleDistanceDeg in 1.0..1.3, "sigma Oct ${s.poleDistanceDeg}")
        assertEquals("Polaris", n.starName)
    }

    @Test fun labels() {
        assertEquals("12:00", PolarScope.clockLabel(0.0))
        assertEquals("3:00", PolarScope.clockLabel(90.0))
        assertEquals("12:00", PolarScope.clockLabel(359.9))
        assertEquals("4:37", PolarScope.clockLabel(138.5))
        assertEquals("06h 30m", PolarScope.hoursLabel(6.5))
        assertEquals("00h 00m", PolarScope.hoursLabel(23.9999))
    }
}
