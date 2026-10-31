package com.photocapsules.core.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeodesyTest {
    private val golden: JsonObject = Json.parseToJsonElement(
        javaClass.getResource("/geodesy_golden.json")!!.readText()
    ).jsonObject

    private fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.double
    private fun angDiff(a: Double, b: Double): Double { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }

    @Test fun inverseMatchesGeographiclib() {
        for (c in golden["inverse"]!!.jsonArray.map { it.jsonObject }) {
            val r = Geodesy.inverse(LatLon(c.d("lat1"), c.d("lon1")), LatLon(c.d("lat2"), c.d("lon2")))
            val s = c.d("s12")
            assertTrue(abs(r.distanceMeters - s) < 1e-3 + s * 1e-9, "distance $c -> ${r.distanceMeters}")
            if (s > 1.0) {
                assertTrue(angDiff(r.initialBearing, c.d("azi1")) < 1e-5, "azi1 $c -> ${r.initialBearing}")
                assertTrue(angDiff(r.finalBearing, c.d("azi2")) < 1e-5, "azi2 $c -> ${r.finalBearing}")
            }
        }
    }

    @Test fun randomNearAntipodalMatchesGeographiclib() {
        for (c in golden["antipodal"]!!.jsonArray.map { it.jsonObject }) {
            val r = Geodesy.inverse(LatLon(c.d("lat1"), c.d("lon1")), LatLon(c.d("lat2"), c.d("lon2")))
            assertEquals(c.d("s12"), r.distanceMeters, 0.05, "distance $c")
            assertTrue(angDiff(r.initialBearing, c.d("azi1")) < 1e-3, "azi1 $c -> ${r.initialBearing}")
        }
    }

    @Test fun directMatchesGeographiclib() {
        for (c in golden["direct"]!!.jsonArray.map { it.jsonObject }) {
            val p = Geodesy.direct(LatLon(c.d("lat1"), c.d("lon1")), c.d("azi1"), c.d("s12"))
            assertTrue(abs(p.latitude - c.d("lat2")) < 1e-8, "lat $c -> $p")
            assertTrue(angDiff(p.longitude, c.d("lon2")) < 1e-8, "lon $c -> $p")
        }
    }

    @Test fun nearlyAntipodalMatchesGeographiclib() {
        // Reference values: geographiclib 2.1 (Karney), WGS84
        val cases = listOf(
            listOf(0.0, 0.0, 0.5, 179.7, 19944127.420750458, 15.556882793490544, 164.44251389085494),
            listOf(10.0, 20.0, -10.2, -160.1, 19981121.597810656, 172.79575934236897, 7.208716058322592),
            listOf(-30.0, 40.0, 30.1, -140.3, 19986110.507817283, 27.38337665208571, 152.58678883775053),
            listOf(0.0, 0.0, 0.0, 179.6, 19989165.416035745, 41.537175375184965, 138.46282462481503),
            // Just short of the antipode (regression: spherical fallback must not win over Vincenty)
            listOf(0.0, 0.0, 0.0, 176.3, 19625626.226854134, 90.0, 90.0),
            listOf(0.0, 0.0, 1.0, 177.0, 19681743.75916397, 67.85001371621144, 112.12867988225902),
            listOf(20.0, 30.0, -19.0, -152.0, 19788486.692776967, 55.70677633306914, 124.80447768604908),
            listOf(0.0, 0.0, 0.0, 177.5, 19759209.615806058, 90.0, 90.0),
        )
        for (c in cases) {
            val r = Geodesy.inverse(LatLon(c[0], c[1]), LatLon(c[2], c[3]))
            assertEquals(c[4], r.distanceMeters, 0.05, "distance $c")
            assertTrue(angDiff(r.initialBearing, c[5]) < 1e-4, "azi1 $c -> ${r.initialBearing}")
            assertTrue(angDiff(r.finalBearing, c[6]) < 1e-3, "azi2 $c -> ${r.finalBearing}")
        }
        // Exactly antipodal on the equator: half meridian, over either pole
        val r = Geodesy.inverse(LatLon(0.0, 0.0), LatLon(0.0, 180.0))
        assertEquals(20003931.4586, r.distanceMeters, 0.05)
        assertTrue(angDiff(r.initialBearing, 0.0) < 1e-4 || angDiff(r.initialBearing, 180.0) < 1e-4, "${r.initialBearing}")
    }

    @Test fun samePointIsZero() {
        val r = Geodesy.inverse(LatLon(10.0, 20.0), LatLon(10.0, 20.0))
        assertEquals(0.0, r.distanceMeters)
    }

    @Test fun elevationAngleCurvature() {
        // Flat-earth angle for 100 m height over 1 km ≈ 5.71°; curvature negligible.
        // flat angle minus half the central angle (curvature) = 5.7106 - 0.0045
        assertEquals(5.7061, Terrain.elevationAngle(1000.0, 0.0, 100.0, 0.0), 1e-3)
        // A target at the same height 50 km away lies below the horizontal: drop d²/2R = 196 m (geometric).
        val geo = Terrain.elevationAngle(50_000.0, 0.0, 0.0, 0.0)
        assertEquals(-Math.toDegrees(50_000.0 / (2 * Geodesy.EARTH_MEAN_RADIUS)), geo, 1e-4)
        // Refraction reduces the apparent drop.
        assertTrue(Terrain.elevationAngle(50_000.0, 0.0, 0.0) > geo)
    }

    @Test fun horizonDip() {
        // Nautical almanac: dip at 10 m ≈ 5.6'
        assertEquals(5.57 / 60, Terrain.apparentHorizonDip(10.0), 0.002)
        assertEquals(0.0, Terrain.apparentHorizonDip(-5.0))
        // geometric dip at 1000 m ≈ 1.015°
        assertEquals(1.0149, Terrain.geometricHorizonDip(1000.0), 1e-3)
    }

    @Test fun sizeDistance() {
        // Moon 0.5° and 100 m subject → distance ≈ 11459 m (PhotoPills "rule of 100": ×~115)
        val d = Terrain.distanceForSize(0.5, 100.0)
        assertEquals(11459.1, d, 0.5)
        assertEquals(100.0, Terrain.linearSize(0.5, d), 1e-6)
        assertEquals(0.5, Terrain.angularSize(100.0, d), 1e-9)
    }

    @Test fun rayHeight() {
        // Body at 0° elevation, target 10 km away at sea level, observer at 0 m: ray rises above it by d²/2R_eff.
        val h = Terrain.rayHeightAboveTarget(10_000.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(10_000.0 * 10_000.0 / (2 * Geodesy.EARTH_MEAN_RADIUS), h, 0.05)
        // Body at 45° over 1 km flat ≈ 1000 m
        assertEquals(1000.0, Terrain.rayHeightAboveTarget(1000.0, 0.0, 0.0, 45.0, 0.0), 0.5)
    }

    @Test fun coordinateFormatting() {
        val p = LatLon(40.416775, -3.70379)
        assertEquals("40.416775°N 3.703790°W", Coordinates.format(p, CoordinateFormat.DDD))
        assertEquals("40°25'00.39\"N 3°42'13.64\"W", Coordinates.format(p, CoordinateFormat.DMS))
        assertEquals("40°25.007'N 3°42.227'W", Coordinates.format(p, CoordinateFormat.DMM))
        // Rounding must not produce 60 seconds.
        assertEquals("10°00'00.00\"N", Coordinates.formatComponent(9.9999999, true, CoordinateFormat.DMS))
    }

    @Test fun coordinateParsing() {
        fun close(a: LatLon?, lat: Double, lon: Double) {
            assertNotNull(a); assertEquals(lat, a.latitude, 1e-6); assertEquals(lon, a.longitude, 1e-6)
        }
        close(Coordinates.parse("40.4168, -3.7038"), 40.4168, -3.7038)
        close(Coordinates.parse("40.4168 -3.7038"), 40.4168, -3.7038)
        close(Coordinates.parse("40°25'0.39\"N 3°42'13.64\"W"), 40.416775, -3.703789)
        close(Coordinates.parse("N 40 25.007, W 3 42.227"), 40.416783, -3.703783)
        close(Coordinates.parse("33°51'54\"S; 151°12'34\"E"), -33.865, 151.209444)
        assertNull(Coordinates.parse("95, 10"))
        assertNull(Coordinates.parse("40 61 0 N, 3 0 0 W"))
        assertNull(Coordinates.parse("hello"))
        assertEquals(-3.5, Coordinates.parseComponent("3 30 W", false)!!, 1e-12)
        assertNull(Coordinates.parseComponent("10 N", false))
    }

    @Test fun longitudeNormalization() {
        assertEquals(-170.0, LatLon.normalizeLongitude(190.0), 1e-12)
        assertEquals(170.0, LatLon.normalizeLongitude(-190.0), 1e-12)
        assertEquals(-180.0, LatLon.normalizeLongitude(180.0), 1e-12)
    }
}
