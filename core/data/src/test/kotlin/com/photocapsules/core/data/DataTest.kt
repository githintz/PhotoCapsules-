package com.photocapsules.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataTest {
    private val plan = Plan("p1", "Moon over <Tower> & bridge", PinState(40.4, -3.7, 650.0, 2.0), PinState(40.5, -3.6, 900.0, 30.0),
        1_790_000_000_000, "Europe/Madrid", notes = "Bring 600mm")
    private val poi = PointOfInterest("q1", "Arch", 39.95, 4.13, 12.0, "Natural arch")

    @Test fun kmlRoundTrip() {
        val s = LibraryState(listOf(plan), listOf(poi))
        val back = Kml.parse(Kml.toKmz(s))
        assertEquals(plan, back.plans[0]) // lossless, including created/updated dates
        assertEquals(poi, back.pois[0]) // lossless, including creation date
        val withPhotos = poi.copy(id = "q2", photoUris = listOf("content://x/1"))
        assertEquals(withPhotos, Kml.parse(Kml.toKmz(LibraryState(pois = listOf(withPhotos)))).pois.single())
        val back2 = Kml.parse(Kml.toKml(s).toByteArray())
        assertEquals(1, back2.plans.size); assertEquals(1, back2.pois.size)
    }

    @Test fun foreignKmlImportsAsPoi() {
        val kml = """<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>
            <Placemark><name>Somewhere</name><Point><coordinates>-73.98,40.75,0</coordinates></Point></Placemark>
            <Placemark><name>Bad</name><Point><coordinates>500,40</coordinates></Point></Placemark>
            </Document></kml>"""
        val s = Kml.parse(kml.toByteArray())
        assertEquals(1, s.pois.size); assertEquals(40.75, s.pois[0].latitude)
    }

    @Test fun invalidXmlCharactersAndZeroAltitudeSurviveRoundTrip() {
        val s = LibraryState(pois = listOf(PointOfInterest("z", "Bad\u0001name\uFFFE ok", 10.0, 20.0, 0.0), PointOfInterest("n", "No alt", 1.0, 2.0, null)))
        val back = Kml.parse(Kml.toKmz(s))
        assertEquals("Badname ok", back.pois[0].name)
        assertEquals(0.0, back.pois[0].altitudeM)
        assertEquals(null, back.pois[1].altitudeM)
    }

    @Test fun nonFiniteAndPrefixedAndSmallNumbers() {
        val kml = """<?xml version="1.0"?><kml:kml xmlns:kml="http://www.opengis.net/kml/2.2"><kml:Document>
            <kml:Placemark><kml:name>Prefixed</kml:name><kml:Point><kml:coordinates>-0.0001,51.4779,NaN</kml:coordinates></kml:Point></kml:Placemark>
            </kml:Document></kml:kml>"""
        val s = Kml.parse(kml.toByteArray())
        assertEquals(1, s.pois.size); assertEquals(null, s.pois[0].altitudeM); assertEquals(-0.0001, s.pois[0].longitude)
        val plan = Plan("x", "p", PinState(0.00001, -0.0002, 1e-4, 0.0), PinState(95.0, 0.0), 0L, "UTC")
        val text = Kml.toKml(LibraryState(plans = listOf(plan)))
        assertTrue(!text.contains("E-"), text)
        assertTrue(text.contains("-0.0002,0.00001,0.0001"), text)
        // Out-of-range Black Pin is dropped instead of crashing later
        assertEquals(null, Kml.parse(text.toByteArray()).plans.single().blackPin)
    }

    @Test fun absurdDatesAndZonesAreRejectedOrFixed() {
        val bad = Plan("b", "bad", PinState(1.0, 2.0), null, Long.MAX_VALUE, "Not/AZone")
        val good = Plan("g", "good", PinState(1.0, 2.0), null, 1_790_000_000_000, "Mars/Olympus", photoUris = listOf("content://a/1", "content://a/2"))
        val back = Kml.parse(Kml.toKmz(LibraryState(plans = listOf(bad, good))))
        assertEquals(listOf("good"), back.plans.map { it.name })
        assertEquals("UTC", back.plans.single().zoneId)
        assertEquals(listOf("content://a/1", "content://a/2"), back.plans.single().photoUris)
    }

    @Test fun lineStringUsesFirstTupleOnly() {
        val kml = """<kml><Document><Placemark><name>Track</name><LineString><coordinates>1,2 3,4 5,6</coordinates></LineString></Placemark>
            <Placemark><name>P3</name><Point><coordinates> 7,8,9 </coordinates></Point></Placemark></Document></kml>"""
        val s = Kml.parse(kml.toByteArray())
        assertEquals(1.0, s.pois[0].longitude); assertEquals(2.0, s.pois[0].latitude); assertEquals(null, s.pois[0].altitudeM)
        assertEquals(9.0, s.pois[1].altitudeM)
    }

    @Test fun xxeIsRejected() {
        val evil = """<?xml version="1.0"?><!DOCTYPE kml [<!ENTITY x SYSTEM "file:///etc/passwd">]><kml><Document><Placemark><name>&x;</name><Point><coordinates>0,0</coordinates></Point></Placemark></Document></kml>"""
        val r = runCatching { Kml.parse(evil.toByteArray()) }
        assertTrue(r.isFailure || r.getOrThrow().pois.none { it.name.contains("root") })
    }

    @Test fun jsonRepository() {
        val dir = createTempDir(); val f = File(dir, "lib.json")
        val repo = JsonFileLibraryRepository(f)
        assertEquals(LibraryState(), repo.load())
        repo.upsertPlan(plan); repo.upsertPoi(poi)
        assertEquals(1, JsonFileLibraryRepository(f).load().plans.size)
        repo.upsertPlan(plan.copy(name = "renamed"))
        assertEquals("renamed", repo.load().plans.single().name)
        assertEquals(0 to 0, repo.merge(LibraryState(listOf(plan), listOf(poi))))
        assertEquals(1 to 0, repo.merge(LibraryState(listOf(plan.copy(id = "p2", name = "new")), emptyList())))
        repo.deletePlan("p1"); repo.deletePoi("q1")
        assertEquals(1, repo.load().plans.size); assertEquals(0, repo.load().pois.size)
        f.writeText("{garbage")
        assertEquals(LibraryState(), repo.load())
        assertTrue(File(dir, "lib.json.corrupt").exists())
        dir.deleteRecursively()
    }
}
