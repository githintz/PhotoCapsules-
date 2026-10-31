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
        assertEquals(plan.copy(createdMillis = back.plans[0].createdMillis, updatedMillis = back.plans[0].updatedMillis), back.plans[0])
        assertEquals(poi.copy(createdMillis = back.pois[0].createdMillis), back.pois[0])
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
