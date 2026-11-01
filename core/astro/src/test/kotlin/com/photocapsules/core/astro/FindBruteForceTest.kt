package com.photocapsules.core.astro

import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Find must report every window that an exhaustive scan finds (audit #4 regressions). */
class FindBruteForceTest {
    private fun bruteWindows(q: FindQuery, stepSec: Long): List<Pair<Instant, Instant>> {
        val out = ArrayList<Pair<Instant, Instant>>()
        var t = q.start; var runStart: Instant? = null; var last = t
        while (!t.isAfter(q.end)) {
            val p = Sky.position(q.body, t, q.where)
            val ok = abs(angleDiff(p.azimuth, q.azimuth)) <= q.azimuthTolerance &&
                (if (q.altitude == null) p.altitude > -1.0 else abs(p.altitude - q.altitude) <= q.altitudeTolerance)
            if (ok && runStart == null) runStart = t
            if (!ok && runStart != null) { out.add(runStart to last); runStart = null }
            last = t; t = t.plusSeconds(stepSec)
        }
        runStart?.let { out.add(it to last) }
        return out
    }

    private fun check(q: FindQuery, stepSec: Long) {
        val brute = bruteWindows(q, stepSec)
        val found = FindSearch.search(q)
        for ((a, b) in brute) {
            val hit = found.any { r -> !r.time.isBefore(a.minusSeconds(stepSec + 60)) && !r.time.isAfter(b.plusSeconds(stepSec + 60)) }
            assertTrue(hit, "window $a..$b not reported; found=${found.map { it.time }}")
        }
        for (r in found) {
            assertTrue(abs(angleDiff(r.azimuth, q.azimuth)) <= q.azimuthTolerance + 1e-6, "$r")
            if (q.altitude != null) assertTrue(abs(r.altitude - q.altitude) <= q.altitudeTolerance + 1e-6, "$r") else assertTrue(r.altitude > -1.0, "$r")
        }
        assertTrue(brute.isNotEmpty())
    }

    @Test fun moonAzimuthOnlyIncludesRisesAndSets() = check(
        FindQuery(SkyBody.MOON, GeoPoint(40.0, -105.0, 1600.0), Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2027-03-01T00:00:00Z"), 300.0, 2.0, null, 0.0), 60)

    @Test fun moonAzimuthAndElevationBox() = check(
        FindQuery(SkyBody.MOON, GeoPoint(40.4168, -3.7038, 667.0), Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2027-11-01T00:00:00Z"), 200.0, 3.0, 45.0, 3.0), 90)

    @Test fun sunNearZenithAtQuito() = check(
        FindQuery(SkyBody.SUN, GeoPoint(-0.18, -78.47, 2850.0), Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"), 180.0, 2.0, 88.0, 2.0), 20)

    @Test fun moonNearZenithAt10N() = check(
        FindQuery(SkyBody.MOON, GeoPoint(10.0, 0.0, 0.0), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), 180.0, 3.0, 85.0, 5.0), 30)

    @Test fun sunNarrowAzimuthNearEquinox() = check(
        FindQuery(SkyBody.SUN, GeoPoint(0.0, 0.0, 0.0), Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"), 90.0, 0.2, 60.0, 0.5), 30)
}
