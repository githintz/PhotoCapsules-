package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchHourAngle
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TimedEvent(val time: Instant, val azimuth: Double, val altitude: Double)

/** A time interval; either end may be open (null) when it lies outside the day window. */
data class Interval(val start: Instant?, val end: Instant?)

data class SunDay(
    val date: LocalDate,
    val zone: ZoneId,
    val sunrise: TimedEvent?,
    val sunset: TimedEvent?,
    val transit: TimedEvent?,
    val civilDawn: Instant?, val civilDusk: Instant?,
    val nauticalDawn: Instant?, val nauticalDusk: Instant?,
    val astronomicalDawn: Instant?, val astronomicalDusk: Instant?,
    /** Golden hours (Sun −4°…+6°) intersecting the day, in chronological order. */
    val goldenHours: List<Interval>,
    /** Blue hours (Sun −6°…−4°) intersecting the day. */
    val blueHours: List<Interval>,
    /** Sun above the rise/set altitude the whole day (midnight sun). */
    val alwaysUp: Boolean,
    /** Sun below the horizon the whole day (polar night). */
    val alwaysDown: Boolean,
    /** Total time between sunrise and sunset within the day, seconds. */
    val daylightSeconds: Double,
)

data class MoonDay(
    val date: LocalDate,
    val zone: ZoneId,
    val moonrise: TimedEvent?,
    val moonset: TimedEvent?,
    val transit: TimedEvent?,
    val alwaysUp: Boolean,
    val alwaysDown: Boolean,
)

/** Daily Sun and Moon events for a local calendar date. */
object DayEvents {

    /** [start, end) instants of a local calendar day (handles 23/25 h DST days). */
    fun dayWindow(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

    private fun riseSetEvents(body: Body, where: GeoPoint, start: Time, end: Time, direction: Direction, horizonHeightM: Double): List<Time> {
        val out = ArrayList<Time>()
        var t = start
        while (t.ut < end.ut) {
            val limit = end.ut - t.ut
            val found = searchRiseSet(body, where.riseSetObserver(horizonHeightM), direction, t, limit, horizonHeightM.coerceAtLeast(0.0)) ?: break
            if (found.ut >= end.ut) break
            out.add(found)
            t = found.addDays(1.0 / 1440.0)
        }
        return out
    }

    private fun event(body: SkyBody, t: Time, where: GeoPoint): TimedEvent {
        val p = Sky.position(body, t, where)
        return TimedEvent(t.toInstant(), p.azimuth, p.altitude)
    }

    private fun transit(body: Body, sky: SkyBody, where: GeoPoint, start: Time, end: Time): TimedEvent? {
        val info = searchHourAngle(body, where.toObserver(), 0.0, start)
        return if (info.time.ut < end.ut) event(sky, info.time, where) else null
    }

    /**
     * @param horizonHeightM observer height above the visible horizon (Planner "Horizon" option);
     * it lowers the apparent horizon by the dip angle for rise/set computations.
     */
    fun sun(date: LocalDate, zone: ZoneId, where: GeoPoint, horizonHeightM: Double = 0.0): SunDay {
        val (s, e) = dayWindow(date, zone)
        val start = s.toAstroTime(); val end = e.toAstroTime()
        val rises = riseSetEvents(Body.Sun, where, start, end, Direction.Rise, horizonHeightM)
        val sets = riseSetEvents(Body.Sun, where, start, end, Direction.Set, horizonHeightM)

        val thresholds = listOf(LightType.GOLDEN_UPPER, LightType.GOLDEN_LOWER, LightType.BLUE_LOWER, LightType.NAUTICAL_LOWER, LightType.ASTRONOMICAL_LOWER)
        val crossings = findCrossings(start, end, 2.0 / 1440.0, thresholds) { Sky.sunTrueAltitude(it, where) }
        fun first(th: Double, rising: Boolean) = crossings.firstOrNull { it.threshold == th && it.rising == rising }?.time?.toInstant()

        val altAt = { t: Time -> Sky.sunTrueAltitude(t, where) }
        val golden = intervalsWithin(start, end, crossings, LightType.GOLDEN_LOWER, LightType.GOLDEN_UPPER, altAt)
        val blue = intervalsWithin(start, end, crossings, LightType.BLUE_LOWER, LightType.GOLDEN_LOWER, altAt)

        val noRiseSet = rises.isEmpty() && sets.isEmpty()
        val midAlt = Sky.position(SkyBody.SUN, Time((start.ut + end.ut) / 2), where).altitude
        val alwaysUp = noRiseSet && midAlt > 0
        val alwaysDown = noRiseSet && midAlt <= 0

        // Daylight: integrate up-intervals bounded by rise/set events.
        val events = (rises.map { it.ut to true } + sets.map { it.ut to false }).sortedBy { it.first }
        var daylight = 0.0
        var up = Sky.position(SkyBody.SUN, start, where).altitude > -0.833 && !(alwaysDown)
        if (events.isNotEmpty()) up = !events.first().second
        var last = start.ut
        for ((t, isRise) in events) {
            if (up && !isRise) daylight += t - last
            up = isRise; last = t
        }
        if (up) daylight += end.ut - last
        if (alwaysUp) daylight = end.ut - start.ut
        if (alwaysDown) daylight = 0.0

        return SunDay(
            date, zone,
            sunrise = rises.firstOrNull()?.let { event(SkyBody.SUN, it, where) },
            sunset = sets.lastOrNull()?.let { event(SkyBody.SUN, it, where) },
            transit = transit(Body.Sun, SkyBody.SUN, where, start, end),
            civilDawn = first(-6.0, true), civilDusk = first(-6.0, false),
            nauticalDawn = first(-12.0, true), nauticalDusk = first(-12.0, false),
            astronomicalDawn = first(-18.0, true), astronomicalDusk = first(-18.0, false),
            goldenHours = golden, blueHours = blue,
            alwaysUp = alwaysUp, alwaysDown = alwaysDown,
            daylightSeconds = daylight * 86400.0,
        )
    }

    /** Intervals within [start,end] where lower ≤ altitude < upper. */
    private fun intervalsWithin(start: Time, end: Time, crossings: List<Crossing>, lower: Double, upper: Double, alt: (Time) -> Double): List<Interval> {
        val relevant = crossings.filter { it.threshold == lower || it.threshold == upper }
        val out = ArrayList<Interval>()
        val a0 = alt(start)
        var inside = a0 >= lower && a0 < upper
        var openStart: Instant? = null
        var started = inside
        for (c in relevant) {
            val enters = (c.threshold == lower && c.rising) || (c.threshold == upper && !c.rising)
            if (enters && !inside) { inside = true; openStart = c.time.toInstant(); started = false }
            else if (!enters && inside) {
                out.add(Interval(if (started) null else openStart, c.time.toInstant()))
                inside = false; started = false; openStart = null
            }
        }
        if (inside) out.add(Interval(if (started) null else openStart, null))
        return out
    }

    fun moon(date: LocalDate, zone: ZoneId, where: GeoPoint, horizonHeightM: Double = 0.0): MoonDay {
        val (s, e) = dayWindow(date, zone)
        val start = s.toAstroTime(); val end = e.toAstroTime()
        val rises = riseSetEvents(Body.Moon, where, start, end, Direction.Rise, horizonHeightM)
        val sets = riseSetEvents(Body.Moon, where, start, end, Direction.Set, horizonHeightM)
        val none = rises.isEmpty() && sets.isEmpty()
        val midAlt = Sky.position(SkyBody.MOON, Time((start.ut + end.ut) / 2), where).altitude
        return MoonDay(
            date, zone,
            moonrise = rises.firstOrNull()?.let { event(SkyBody.MOON, it, where) },
            moonset = sets.firstOrNull()?.let { event(SkyBody.MOON, it, where) },
            transit = transit(Body.Moon, SkyBody.MOON, where, start, end),
            alwaysUp = none && midAlt > 0,
            alwaysDown = none && midAlt <= 0,
        )
    }

    /** Next sunrise/sunset/moonrise/moonset after [from] (searching up to [limitDays]). */
    fun nextRiseSet(body: SkyBody, rise: Boolean, from: Instant, where: GeoPoint, limitDays: Double = 2.0, horizonHeightM: Double = 0.0): TimedEvent? {
        val t = searchRiseSet(Sky.body(body), where.riseSetObserver(horizonHeightM), if (rise) Direction.Rise else Direction.Set, from.toAstroTime(), limitDays, horizonHeightM.coerceAtLeast(0.0))
        return t?.let { event(body, it, where) }
    }

    /** Previous rise/set before [from]. */
    fun previousRiseSet(body: SkyBody, rise: Boolean, from: Instant, where: GeoPoint, limitDays: Double = 2.0, horizonHeightM: Double = 0.0): TimedEvent? {
        val t = searchRiseSet(Sky.body(body), where.riseSetObserver(horizonHeightM), if (rise) Direction.Rise else Direction.Set, from.toAstroTime(), -limitDays, horizonHeightM.coerceAtLeast(0.0))
        return t?.let { event(body, it, where) }
    }

    /** Samples a body's position across [start, end] every [stepMinutes]. */
    fun path(body: SkyBody, start: Instant, end: Instant, where: GeoPoint, stepMinutes: Double = 10.0): List<Pair<Instant, BodyPosition>> {
        val out = ArrayList<Pair<Instant, BodyPosition>>()
        var t = start
        val step = (stepMinutes * 60_000).toLong()
        while (!t.isAfter(end)) { out.add(t to Sky.position(body, t, where)); t = t.plusMillis(step) }
        return out
    }

    fun lightType(instant: Instant, where: GeoPoint): LightType =
        LightType.forSunAltitude(Sky.sunTrueAltitude(instant.toAstroTime(), where))
}
