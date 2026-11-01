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
        // No duplicates
        assertTrue(found.zipWithNext().all { (a, b) -> b.time.epochSecond - a.time.epochSecond > 60 }, "duplicates in ${found.map { it.time }}")
    }

    @Test fun tropicalTurningPointHasNoDuplicates() = check(
        FindQuery(SkyBody.SUN, GeoPoint(-5.0, 39.0, 0.0), Instant.parse("2026-12-10T00:00:00Z"), Instant.parse("2026-12-20T00:00:00Z"), 245.0, 5.0, null, 0.0), 30)

    @Test fun matchAtTheEndsOfTheRangeIsFound() {
        val madrid = GeoPoint(40.4168, -3.7038, 667.0)
        val t = Instant.parse("2026-06-05T16:16:14Z")
        for ((s, e) in listOf(t.minusSeconds(86400) to t.minusSeconds(180), t.plusSeconds(180) to t.plusSeconds(86400))) {
            val r = FindSearch.search(FindQuery(SkyBody.SUN, madrid, s, e, 270.0, 2.0, null, 0.0))
            assertTrue(r.any { kotlin.math.abs(it.time.epochSecond - t.epochSecond) < 3600 }, "range $s..$e -> ${r.map { it.time }}")
            assertTrue(r.all { !it.time.isBefore(s) && !it.time.isAfter(e) })
        }
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

    // Audit #7: zero azimuth tolerance must still report exact crossings.
    @Test fun zeroToleranceNearZenithIsFound() {
        val q = FindQuery(SkyBody.SUN, GeoPoint(0.3404, 83.894, 0.0), Instant.parse("2026-03-19T05:00:00Z"), Instant.parse("2026-03-19T08:00:00Z"), 224.857, 0.0, 88.772, 0.5)
        val r = FindSearch.search(q)
        assertTrue(r.any { abs(it.time.epochSecond - Instant.parse("2026-03-19T06:35:41Z").epochSecond) < 5 }, "${r.map { it.time }}")
    }

    @Test fun zeroToleranceFindsEveryCrossing() {
        var total = 0
        for ((body, where) in listOf(SkyBody.SUN to GeoPoint(35.0, 10.0, 0.0), SkyBody.MOON to GeoPoint(-33.9, 151.2, 0.0), SkyBody.MOON to GeoPoint(52.0, -1.0, 0.0))) {
            for (az in listOf(95.0, 180.0, 250.0)) {
                val s = Instant.parse("2026-05-01T00:00:00Z"); val e = Instant.parse("2026-05-08T00:00:00Z")
                // Brute force: sign changes of Δaz (not ±180 wraps) with the body above −1°, at 30 s steps.
                var crossings = 0
                var t = s; var prev = angleDiff(Sky.position(body, t, where).azimuth, az)
                while (t.isBefore(e)) {
                    val n = t.plusSeconds(30); val p = Sky.position(body, n, where); val d = angleDiff(p.azimuth, az)
                    if ((prev <= 0 && d > 0 || prev >= 0 && d < 0) && abs(prev - d) < 90 && p.altitude > -1.0) crossings++
                    prev = d; t = n
                }
                val found = FindSearch.search(FindQuery(body, where, s, e, az, 0.0, null, 0.0))
                assertTrue(abs(found.size - crossings) <= 1, "$body $where az=$az brute=$crossings found=${found.size}")
                total += crossings
                assertTrue(found.all { abs(angleDiff(it.azimuth, az)) < 1e-3 })
            }
        }
        assertTrue(total > 30, "total=$total")
    }

    // Audit #7: two separate passes less than 3 h apart must both be reported.
    @Test fun twoPassesCloseTogetherAreBothReported() = check(
        FindQuery(SkyBody.SUN, GeoPoint(20.446, 116.476, 0.0), Instant.parse("2026-07-13T02:18:00Z"), Instant.parse("2026-07-14T18:56:00Z"), 281.335, 2.0, null, 0.0), 20)

    // A long pass (wide tolerance) still yields a single result per pass.
    @Test fun widePassGivesOneResultPerDay() {
        val q = FindQuery(SkyBody.SUN, GeoPoint(70.0, 20.0, 0.0), Instant.parse("2026-06-10T00:00:00Z"), Instant.parse("2026-06-15T00:00:00Z"), 180.0, 60.0, null, 0.0)
        val r = FindSearch.search(q)
        assertTrue(r.size in 5..6, "${r.map { it.time }}")
    }
}
