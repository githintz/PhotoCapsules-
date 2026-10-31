package com.photocapsules.core.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class EventKind(val label: String) {
    ASTRONOMICAL_DAWN("Astronomical twilight begins"), NAUTICAL_DAWN("Nautical twilight begins"), BLUE_HOUR_START("Blue hour begins"),
    CIVIL_DAWN("Civil twilight begins"), GOLDEN_HOUR_START("Golden hour begins"), SUNRISE("Sunrise"), GOLDEN_HOUR_END("Golden hour ends"),
    BLUE_HOUR_END("Blue hour ends"), SOLAR_NOON("Solar noon"), SUNSET("Sunset"), CIVIL_DUSK("Civil twilight ends"),
    NAUTICAL_DUSK("Nautical twilight ends"), ASTRONOMICAL_DUSK("Astronomical twilight ends"), MOONRISE("Moonrise"), MOONSET("Moonset"),
    MOON_TRANSIT("Moon transit"), GC_VISIBLE("Galactic Center visible"), GC_HIDDEN("Galactic Center not visible"),
}

data class DayEvent(val kind: EventKind, val time: Instant, val azimuth: Double? = null)

/** Chronological list of all light/Sun/Moon/Milky Way events of a local day (Sun/Moon "Info" timeline). */
object Timeline {
    fun events(date: LocalDate, zone: ZoneId, where: GeoPoint, horizonHeightM: Double = 0.0): List<DayEvent> {
        val sun = DayEvents.sun(date, zone, where, horizonHeightM)
        val moon = DayEvents.moon(date, zone, where, horizonHeightM)
        val out = ArrayList<DayEvent>()
        sun.astronomicalDawn?.let { out.add(DayEvent(EventKind.ASTRONOMICAL_DAWN, it)) }
        sun.nauticalDawn?.let { out.add(DayEvent(EventKind.NAUTICAL_DAWN, it)) }
        sun.civilDawn?.let { out.add(DayEvent(EventKind.CIVIL_DAWN, it)) }
        sun.civilDusk?.let { out.add(DayEvent(EventKind.CIVIL_DUSK, it)) }
        sun.nauticalDusk?.let { out.add(DayEvent(EventKind.NAUTICAL_DUSK, it)) }
        sun.astronomicalDusk?.let { out.add(DayEvent(EventKind.ASTRONOMICAL_DUSK, it)) }
        sun.sunrise?.let { out.add(DayEvent(EventKind.SUNRISE, it.time, it.azimuth)) }
        sun.sunset?.let { out.add(DayEvent(EventKind.SUNSET, it.time, it.azimuth)) }
        sun.transit?.let { out.add(DayEvent(EventKind.SOLAR_NOON, it.time, it.azimuth)) }
        for (g in sun.goldenHours) {
            g.start?.let { out.add(DayEvent(EventKind.GOLDEN_HOUR_START, it)) }
            g.end?.let { out.add(DayEvent(EventKind.GOLDEN_HOUR_END, it)) }
        }
        for (b in sun.blueHours) {
            b.start?.let { out.add(DayEvent(EventKind.BLUE_HOUR_START, it)) }
            b.end?.let { out.add(DayEvent(EventKind.BLUE_HOUR_END, it)) }
        }
        moon.moonrise?.let { out.add(DayEvent(EventKind.MOONRISE, it.time, it.azimuth)) }
        moon.moonset?.let { out.add(DayEvent(EventKind.MOONSET, it.time, it.azimuth)) }
        moon.transit?.let { out.add(DayEvent(EventKind.MOON_TRANSIT, it.time, it.azimuth)) }
        val (s, e) = DayEvents.dayWindow(date, zone)
        for (v in MilkyWay.visibility(s, e, where)) {
            if (!v.start.openEnded) out.add(DayEvent(EventKind.GC_VISIBLE, v.start.time, v.start.azimuth))
            if (!v.end.openEnded) out.add(DayEvent(EventKind.GC_HIDDEN, v.end.time, v.end.azimuth))
        }
        return out.sortedBy { it.time }
    }

    /** Light type over a window, sampled every [stepMinutes] (for the coloured time bar). */
    fun lightBands(start: Instant, end: Instant, where: GeoPoint, stepMinutes: Int = 5): List<Pair<Instant, LightType>> {
        val out = ArrayList<Pair<Instant, LightType>>()
        var t = start
        while (t.isBefore(end)) { out.add(t to DayEvents.lightType(t, where)); t = t.plusSeconds(stepMinutes * 60L) }
        return out
    }
}
