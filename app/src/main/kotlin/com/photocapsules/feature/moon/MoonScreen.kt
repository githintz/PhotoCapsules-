package com.photocapsules.feature.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.ApsisEvent
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.MoonCalendar
import com.photocapsules.core.astro.MoonDay
import com.photocapsules.core.astro.PhaseEvent
import com.photocapsules.core.astro.PrincipalPhase
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.astro.Timeline
import com.photocapsules.core.astro.DayEvent
import com.photocapsules.core.photo.Format
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.feature.calculators.settingsState
import com.photocapsules.feature.common.ElevationChart
import com.photocapsules.feature.common.LiveClock
import com.photocapsules.feature.common.SkyContextBar
import com.photocapsules.feature.common.rememberDaySeries
import com.photocapsules.feature.common.setSkyTime
import com.photocapsules.feature.sun.EventRow
import com.photocapsules.feature.sun.MonthHeader
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
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Composable
fun MoonScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LiveClock()
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    AppScreen("Moon", onBack, bottomBar = {
        BottomActions(
            "AR" to { AppState.ar = ArRequest(AppState.sky.value); go(Routes.ar(ArMode.SUN_MOON)) },
            "Planner" to { AppState.pendingPlannerContext.value = AppState.sky.value; go(Routes.PLANNER) },
            "Share" to { com.photocapsules.feature.common.shareText(shareCtx, "Moon information", com.photocapsules.feature.common.daySummary(AppState.sky.value, "Moon information")) },
        )
    }) { p ->
        ScrollColumn(p) {
            SkyContextBar()
            Tabs(listOf("Info", "Calendar", "Distances"), tab) { tab = it }
            when (tab) {
                0 -> MoonInfo()
                1 -> MoonMonth()
                else -> Distances()
            }
        }
    }
}

@Composable
private fun MoonInfo() {
    val ctx by AppState.sky.collectAsState()
    val units = settingsState().units
    val date = ctx.instant.atZone(ctx.zone).toLocalDate()
    val info = MoonCalc.info(ctx.instant, ctx.point)
    val sun = Sky.position(SkyBody.SUN, ctx.instant, ctx.point)
    val day by produceState<MoonDay?>(null, date, ctx.zone, ctx.point) { value = withContext(Dispatchers.Default) { DayEvents.moon(date, ctx.zone, ctx.point, ctx.horizonHeightM) } }
    val events by produceState<List<DayEvent>>(emptyList(), date, ctx.zone, ctx.point) { value = withContext(Dispatchers.Default) { Timeline.events(date, ctx.zone, ctx.point, ctx.horizonHeightM) } }
    val next by produceState<List<PhaseEvent>>(emptyList(), ctx.instant.epochSecond / 3600) {
        value = withContext(Dispatchers.Default) { MoonCalendar.phases(ctx.instant, ctx.instant.plusSeconds(40L * 86400)).take(5) }
    }
    val series = rememberDaySeries(ctx)
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp).pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, dx ->
                acc += dx
                if (kotlin.math.abs(acc) > 12f) { setSkyTime(AppState.sky.value.instant.plusSeconds((-acc / 12f * 300).toLong())); acc = 0f }
            }
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            val bg = if (info.position.altitude > 0) Palette.light(DayEvents.lightType(ctx.instant, ctx.point)) else Palette.Night
            MoonDisc(info.phaseAngle, 120.dp, rotation = info.brightLimbFromZenith, background = bg)
            Spacer(Modifier.size(8.dp))
            Text("${info.phase.label} · ${Fmt.pct(info.illumination)}", color = Palette.Text, fontSize = 18.sp)
            Row {
                Text("↑ " + Fmt.timeAz(day?.moonrise?.time, day?.moonrise?.azimuth, ctx.zone), color = Palette.Moonrise)
                Spacer(Modifier.width(16.dp))
                Text("↓ " + Fmt.timeAz(day?.moonset?.time, day?.moonset?.azimuth, ctx.zone), color = Palette.Moonset)
            }
            if (info.position.altitude < 0) Text("Moon below the horizon", color = Palette.TextDim, fontSize = 12.sp)
        }
    }
    SectionTitle("Next main phases")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        next.forEach { ph ->
            Column(Modifier.background(Palette.Surface, RoundedCornerShape(10.dp)).clickable { setSkyTime(ph.time) }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MoonDisc(ph.phase.angle, 40.dp, latitude = ctx.point.latitude)
                Text(ph.phase.label, color = Palette.Text, fontSize = 12.sp)
                Text(Fmt.dateShort(ph.time, ctx.zone) + " " + Fmt.time(ph.time, ctx.zone), color = Palette.TextDim, fontSize = 11.sp)
                if (ph.isSupermoon) Text("Supermoon", color = Palette.Accent, fontSize = 11.sp)
            }
        }
    }
    SectionTitle("Position")
    SimpleTable(listOf("", "Azimuth", "Elevation"), listOf(
        listOf("Moon", Fmt.az(info.position.azimuth, 2), Fmt.deg(info.position.altitude, 2)),
        listOf("Sun", Fmt.az(sun.azimuth, 2), Fmt.deg(sun.altitude, 2)),
    ))
    InfoText("Light: " + DayEvents.lightType(ctx.instant, ctx.point).label)
    SectionTitle("Events")
    Card {
        events.forEach { e ->
            EventRow(e, ctx.zone)
            if (e.kind.name == "MOONRISE" || e.kind.name == "MOONSET") {
                val s = Sky.position(SkyBody.SUN, e.time, ctx.point)
                Text("      Sun elevation ${Fmt.deg(s.altitude)} · ${DayEvents.lightType(e.time, ctx.point).label}", color = Palette.TextDim, fontSize = 11.sp)
            }
        }
    }
    SectionTitle("Moon & Sun elevation")
    Card { ElevationChart(series, ctx.instant, showMoon = true) }
    SectionTitle("Information")
    Card {
        val nextRise = DayEvents.nextRiseSet(SkyBody.MOON, true, ctx.instant, ctx.point, 2.0, ctx.horizonHeightM)
        val nextSet = DayEvents.nextRiseSet(SkyBody.MOON, false, ctx.instant, ctx.point, 2.0, ctx.horizonHeightM)
        when {
            nextSet == null && nextRise == null ->
                if (info.position.altitude > 0) ValueRow("Time to set", "Does not set in the next 48 h") else ValueRow("Time to rise", "Does not rise in the next 48 h")
            nextSet != null && (nextRise == null || nextSet.time < nextRise.time) -> ValueRow("Time to set", Format.duration((nextSet.time.epochSecond - ctx.instant.epochSecond).toDouble(), false))
            else -> ValueRow("Time to rise", Format.duration((nextRise!!.time.epochSecond - ctx.instant.epochSecond).toDouble(), false))
        }
        ValueRow("Angular diameter", String.format(Locale.US, "%.4f°", info.position.angularDiameterDeg))
        ValueRow("Age", String.format(Locale.US, "%.1f days", info.ageDays))
        ValueRow("Distance", Fmt.km(info.position.distanceKm, units))
        ValueRow("Illumination", Fmt.pct(info.illumination))
        ValueRow("Shadow ratio", Sky.shadowRatio(info.position.altitude)?.let { String.format(Locale.US, "%.2f", it) } ?: "Moon down")
        ValueRow("Transit", day?.transit?.let { Fmt.time(it.time, ctx.zone) + " · el " + Fmt.deg(it.altitude) } ?: "—")
    }
}

@Composable
private fun MoonMonth() {
    val ctx by AppState.sky.collectAsState()
    val ym = YearMonth.from(ctx.instant.atZone(ctx.zone))
    val zone = ctx.zone
    val data by produceState<Pair<List<Double>, List<PhaseEvent>>?>(null, ym, zone) {
        value = withContext(Dispatchers.Default) {
            val angles = (1..ym.lengthOfMonth()).map { d -> MoonCalc.info(ym.atDay(d).atTime(21, 0).atZone(zone).toInstant(), ctx.point).phaseAngle }
            val s = ym.atDay(1).atStartOfDay(zone).toInstant(); val e = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            angles to MoonCalendar.phases(s, e)
        }
    }
    MonthHeader(ym)
    val today = java.time.LocalDate.now(zone)
    val first = ym.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val cells = List(first) { null } + (1..ym.lengthOfMonth()).toList()
    Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = Palette.TextDim, textAlign = TextAlign.Center) } }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(0.8f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        val date = ym.atDay(d)
                        val phaseHere = data?.second?.firstOrNull { it.time.atZone(zone).toLocalDate() == date }
                        val border = when {
                            phaseHere?.isSupermoon == true -> Palette.Accent
                            date == today -> Palette.Moon
                            else -> null
                        }
                        Column(Modifier.let { m -> if (border != null) m.border(2.dp, border, CircleShape) else m }.clickable {
                            setSkyTime(date.atTime(21, 0).atZone(zone).toInstant())
                        }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            data?.first?.getOrNull(d - 1)?.let { MoonDisc(it, 26.dp, latitude = ctx.point.latitude) }
                            Text("$d", color = if (phaseHere != null) Palette.Accent else Palette.Text, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    SectionTitle("Phases this month")
    Card { data?.second?.forEach { ph -> ValueRow(ph.phase.label + if (ph.isSupermoon) " (Supermoon)" else "", Fmt.dateTime(ph.time, zone), onClick = { setSkyTime(ph.time) }) } }
    InfoText("Yellow circle: Supermoon (definition in Settings). Blue circle: today.")
}

@Composable
private fun Distances() {
    val ctx by AppState.sky.collectAsState()
    val units = settingsState().units
    val year = ctx.instant.atZone(ctx.zone).year
    val zone = ctx.zone
    val data by produceState<Pair<List<ApsisEvent>, List<PhaseEvent>>?>(null, year, zone) {
        value = withContext(Dispatchers.Default) {
            val s = java.time.LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
            val e = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
            MoonCalendar.apsides(s, e) to MoonCalendar.supermoons(s, e)
        }
    }
    // Moon phase/orientation and light type for each apsis, computed off the main thread.
    val apsisInfo by produceState<Map<java.time.Instant, Pair<com.photocapsules.core.astro.MoonInfo, String>>>(emptyMap(), data, ctx.point) {
        val list = data?.first ?: return@produceState
        value = withContext(Dispatchers.Default) { list.associate { a -> a.time to (MoonCalc.info(a.time, ctx.point) to DayEvents.lightType(a.time, ctx.point).label) } }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(zone).minusYears(1).toInstant()) }) { Text("‹ ${year - 1}") }
        Text("$year", color = Palette.Text, modifier = Modifier.weight(1f), fontSize = 16.sp)
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(zone).plusYears(1).toInstant()) }) { Text("${year + 1} ›") }
    }
    SectionTitle("Supermoons")
    Card {
        val sm = data?.second.orEmpty()
        if (sm.isEmpty()) ValueRow("None this year", "")
        sm.forEach { ValueRow(Fmt.dateTime(it.time, zone), Fmt.km(it.distanceKm, units), onClick = { setSkyTime(it.time) }) }
    }
    SectionTitle("Perigees & apogees")
    Card {
        data?.first?.forEach { a ->
            val (info, lightLabel) = apsisInfo[a.time] ?: return@forEach
            Row(Modifier.fillMaxWidth().clickable { setSkyTime(a.time) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (a.perigee) "Perigee" else "Apogee") + " · " + Fmt.dateTime(a.time, zone), color = if (a.perigee) Palette.Accent else Palette.Text)
                    Text(Fmt.km(a.distanceKm, units) + " · " + lightLabel, color = Palette.TextDim, fontSize = 12.sp)
                }
                MoonDisc(info.phaseAngle, 32.dp, rotation = info.brightLimbFromZenith)
            }
        }
    }
}
