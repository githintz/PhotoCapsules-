package com.photocapsules.feature.meteors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.MeteorShowers
import com.photocapsules.core.astro.MilkyWay
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.ShowerOccurrence
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.feature.common.LiveClock
import com.photocapsules.feature.common.SkyContextBar
import com.photocapsules.feature.common.setSkyTime
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.MoonDisc
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.SimpleTable
import com.photocapsules.ui.components.Tabs
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Night window for a date: local noon → next local noon. */
private fun night(date: java.time.LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atTime(12, 0).atZone(zone).toInstant() to date.plusDays(1).atTime(12, 0).atZone(zone).toInstant()

/** Seconds of astronomical night without the Moon above the horizon in [start, end]. */
fun moonlessDarkSeconds(start: Instant, end: Instant, where: GeoPoint): Double {
    var t = start; var dark = 0.0
    while (t.isBefore(end)) {
        val sun = Sky.position(SkyBody.SUN, t, where).trueAltitude
        if (sun < -18 && Sky.position(SkyBody.MOON, t, where).altitude < 0) dark += 300.0
        t = t.plusSeconds(300)
    }
    return dark
}

@Composable
fun MeteorScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LiveClock()
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    AppScreen("Meteor Showers", onBack, bottomBar = {
        BottomActions(
            "AR" to { AppState.ar = ArRequest(AppState.sky.value, meteorShowerCode = selected); go(Routes.ar(ArMode.NIGHT)) },
            "Planner" to { AppState.pendingPlannerContext.value = AppState.sky.value; go(Routes.PLANNER) },
            "Share" to {
                val c = AppState.sky.value
                val body = MeteorShowers.active(c.instant).joinToString("\n", prefix = "Active meteor showers – " + Fmt.date(c.instant, c.zone) + "\n") { o ->
                    o.shower.name + ": peak " + Fmt.dateTime(o.peak, c.zone) + ", ZHR " + o.shower.zhr.toInt()
                }
                com.photocapsules.feature.common.shareText(shareCtx, "Meteor showers", body)
            },
        )
    }) { p ->
        ScrollColumn(p) {
            SkyContextBar()
            Tabs(listOf("Info", "Calendar"), tab) { tab = it }
            if (tab == 0) MeteorInfo(selected) { selected = it } else MeteorCalendar { selected = it; tab = 0 }
        }
    }
}

@Composable
private fun EnergyBar(fraction: Double) {
    Box(Modifier.width(60.dp).height(8.dp).background(Palette.SurfaceHigh, RoundedCornerShape(4.dp))) {
        Box(Modifier.width((60 * fraction.coerceIn(0.0, 1.0)).dp).height(8.dp).background(Palette.Accent, RoundedCornerShape(4.dp)))
    }
}

/** Quality score: peak ZHR (log-scaled) × fraction of the peak night free of moonlight. */
private fun quality(occ: ShowerOccurrence, illum: Double): Double {
    val z = kotlin.math.ln(1 + occ.shower.zhr) / kotlin.math.ln(151.0)
    return (z * (1 - 0.8 * illum)).coerceIn(0.0, 1.0)
}

@Composable
private fun ShowerRow(occ: ShowerOccurrence, zone: ZoneId, where: GeoPoint, onClick: () -> Unit) {
    val moon = remember(occ.peak, where) { MoonCalc.info(occ.peak, where) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(occ.shower.name + if (occ.shower.daytime) " (daytime)" else "", color = Palette.Text)
            Text("${Fmt.dateShort(occ.activityStart.atStartOfDay(zone).toInstant(), zone)} – ${Fmt.dateShort(occ.activityEnd.atStartOfDay(zone).toInstant(), zone)} · peak ${Fmt.dateShort(occ.peak, zone)} ${Fmt.time(occ.peak, zone)}",
                color = Palette.TextDim, fontSize = 11.sp)
            Text("ZHR ${occ.shower.zhr.toInt()}${if (occ.shower.variable) " (variable)" else ""} · ${occ.shower.velocityKmS.toInt()} km/s · class ${listOf("I", "II", "III", "IV")[(occ.shower.showerClass - 1).coerceIn(0, 3)]}",
                color = Palette.TextDim, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MoonDisc(moon.phaseAngle, 24.dp, latitude = where.latitude)
            EnergyBar(quality(occ, moon.illumination))
        }
    }
}

@Composable
private fun MeteorInfo(selected: String?, onSelect: (String) -> Unit) {
    val ctx by AppState.sky.collectAsState()
    val zone = ctx.zone; val where = ctx.point
    val date = ctx.instant.atZone(zone).toLocalDate()
    val (ns, ne) = night(date, zone)
    val active = remember(date) { MeteorShowers.active(ctx.instant) }
    val upcoming = remember(date) {
        (date.year..date.year + 1).flatMap { MeteorShowers.occurrences(it) }.filter { it.peak.isAfter(ctx.instant) && it.shower.showerClass == 1 }.take(6)
    }
    val total by produceState(0.0, ctx.instant, where) { value = withContext(Dispatchers.Default) { MeteorShowers.totalHourlyRate(ctx.instant, where) } }
    data class NightInfo(val sunset: Instant?, val sunrise: Instant?, val moonrise: Instant?, val moonset: Instant?, val gc: List<MilkyWay.Visibility>)
    val nightInfo by produceState<NightInfo?>(null, date, where, zone) {
        value = withContext(Dispatchers.Default) {
            val md = DayEvents.moon(date, zone, where)
            NightInfo(DayEvents.sun(date, zone, where).sunset?.time, DayEvents.sun(date.plusDays(1), zone, where).sunrise?.time,
                md.moonrise?.time, md.moonset?.time, MilkyWay.visibility(ns, ne, where))
        }
    }
    val positions by produceState<List<List<String>>>(emptyList(), ctx.instant, where, active) {
        value = withContext(Dispatchers.Default) {
            val mp = Sky.position(SkyBody.MOON, ctx.instant, where)
            listOf(listOf("Moon", Fmt.az(mp.azimuth), Fmt.deg(mp.altitude), "")) + active.map { o ->
                val r = MeteorShowers.radiantPosition(o, ctx.instant, where)
                listOf(o.shower.code, Fmt.az(r.azimuth), Fmt.deg(r.altitude), String.format(Locale.US, "%.1f", MeteorShowers.hourlyRate(o, ctx.instant, where)))
            }
        }
    }
    // Peak hourly rate of the night (sampled every 15 min)
    val nightCurve by produceState<List<Triple<Instant, Double, Pair<Double, Double>>>>(emptyList(), date, where, selected) {
        value = withContext(Dispatchers.Default) {
            // Nearest occurrence (previous, this or next year) to the selected date.
            val occ = selected?.let { c -> MeteorShowers.byCode(c)?.let { s -> listOf(-1, 0, 1).map { MeteorShowers.occurrence(s, date.year + it) }.minBy { kotlin.math.abs(it.peak.epochSecond - ctx.instant.epochSecond) } } }
            val out = ArrayList<Triple<Instant, Double, Pair<Double, Double>>>()
            var t = ns
            while (t.isBefore(ne)) {
                val rate = MeteorShowers.totalHourlyRate(t, where)
                val rad = occ?.let { MeteorShowers.radiantPosition(it, t, where).altitude } ?: Double.NaN
                out.add(Triple(t, rate, rad to Sky.position(SkyBody.MOON, t, where).altitude))
                t = t.plusSeconds(900)
            }
            out
        }
    }
    val dark by produceState(0.0, date, where) { value = withContext(Dispatchers.Default) { moonlessDarkSeconds(ns, ne, where) } }
    val moon = MoonCalc.info(ctx.instant, where)
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.0f meteors/h", total), color = Palette.Accent, fontSize = 30.sp)
            val peak = nightCurve.maxByOrNull { it.second }
            if (peak != null && peak.second > 0) Text("Tonight's peak: ${String.format(Locale.US, "%.0f", peak.second)}/h at ${Fmt.time(peak.first, zone)}", color = Palette.Text,
                modifier = Modifier.clickable { setSkyTime(peak.first) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonDisc(moon.phaseAngle, 28.dp, latitude = where.latitude)
                Text("  ${moon.phase.label} ${Fmt.pct(moon.illumination)} · ${com.photocapsules.core.photo.Format.duration(dark, false)} dark without Moon", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
    }
    SectionTitle("Major shower peaks")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        upcoming.forEach { o ->
            Column(Modifier.background(Palette.Surface, RoundedCornerShape(10.dp)).clickable { onSelect(o.shower.code); setSkyTime(o.peak) }.padding(10.dp)) {
                Text(o.shower.name, color = Palette.Text, fontSize = 13.sp)
                Text(Fmt.dateShort(o.peak, zone) + " " + Fmt.time(o.peak, zone), color = Palette.TextDim, fontSize = 11.sp)
                Text("ZHR ${o.shower.zhr.toInt()}", color = Palette.Accent, fontSize = 11.sp)
            }
        }
    }
    SectionTitle("Active showers")
    Card {
        if (active.isEmpty()) ValueRow("No active showers", "")
        active.forEach { o -> ShowerRow(o, zone, where) { onSelect(o.shower.code); setSkyTime(o.peak) } }
    }
    SectionTitle("Activity tonight")
    Card {
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(10.dp)) {
            if (nightCurve.isEmpty()) return@Canvas
            val maxRate = (nightCurve.maxOf { it.second }).coerceAtLeast(1.0)
            val w = size.width; val h = size.height; val n = nightCurve.size
            nightCurve.forEachIndexed { i, (_, r, alts) ->
                val x = w * i / n
                drawLine(Palette.TextDim.copy(alpha = 0.6f), Offset(x, h), Offset(x, h - (r / maxRate * h).toFloat()), w / n)
            }
            fun y(a: Double) = (h - (a.coerceIn(0.0, 90.0) / 90.0 * h)).toFloat()
            for (i in 1 until n) {
                val a = nightCurve[i - 1].third; val b = nightCurve[i].third
                if (!a.first.isNaN() && !b.first.isNaN() && a.first > 0 && b.first > 0) drawLine(Palette.Accent, Offset(w * (i - 1) / n, y(a.first)), Offset(w * i / n, y(b.first)), 3f)
                if (a.second > 0 && b.second > 0) drawLine(Palette.Moon, Offset(w * (i - 1) / n, y(a.second)), Offset(w * i / n, y(b.second)), 3f)
            }
            val cx = ((ctx.instant.epochSecond - ns.epochSecond).toFloat() / (ne.epochSecond - ns.epochSecond) * w)
            if (cx in 0f..w) drawLine(Palette.Text, Offset(cx, 0f), Offset(cx, h), 2f)
        }
        InfoText("Grey: meteors/h (all active showers) · Yellow: radiant elevation of the selected shower · Blue: Moon elevation. Noon to noon.")
    }
    SectionTitle("Sun, Moon & Milky Way")
    Card {
        val ni = nightInfo
        ValueRow("Sunset / sunrise", Fmt.time(ni?.sunset, zone) + " / " + Fmt.time(ni?.sunrise, zone))
        ValueRow("Moonrise / moonset", Fmt.time(ni?.moonrise, zone) + " / " + Fmt.time(ni?.moonset, zone))
        val gc = ni?.gc.orEmpty()
        ValueRow("Galactic Center visible", if (ni == null) "…" else if (gc.isEmpty()) "Not visible" else gc.joinToString { Fmt.time(it.start.time, zone) + "–" + Fmt.time(it.end.time, zone) })
    }
    SectionTitle("Positions now")
    SimpleTable(listOf("", "Az", "El", "Met/h"), positions)
    InfoText("Hourly rate = ZHR × sin(radiant elevation), following each shower's activity profile (IMO data). Actual rates also depend on sky darkness.")
}

@Composable
private fun MeteorCalendar(onSelect: (String) -> Unit) {
    val ctx by AppState.sky.collectAsState()
    val zone = ctx.zone
    var year by rememberSaveable { mutableIntStateOf(ctx.instant.atZone(zone).year) }
    val list = remember(year) { MeteorShowers.occurrences(year) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { year-- }) { Text("‹ ${year - 1}") }
        Text("$year", color = Palette.Text, modifier = Modifier.weight(1f), fontSize = 16.sp)
        TextButton(onClick = { year++ }) { Text("${year + 1} ›") }
    }
    for (cls in 1..4) {
        SectionTitle("Class " + listOf("I", "II", "III", "IV")[cls - 1] + " – " + listOf("strong (> 10/h)", "moderate (2–10/h)", "variable / outbursts", "weak (< 2/h)")[cls - 1])
        Card { list.filter { it.shower.showerClass == cls }.forEach { o -> ShowerRow(o, zone, ctx.point) { onSelect(o.shower.code); setSkyTime(o.peak) } } }
    }
}
