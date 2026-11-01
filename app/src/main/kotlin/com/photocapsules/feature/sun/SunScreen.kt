package com.photocapsules.feature.sun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.DayEvent
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.EventKind
import com.photocapsules.core.astro.LightType
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.MoonCalendar
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.astro.SunDay
import com.photocapsules.core.astro.Timeline
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
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
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
fun SunScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LiveClock()
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    AppScreen("Sun", onBack, bottomBar = {
        BottomActions(
            "AR" to { AppState.ar = ArRequest(AppState.sky.value); go(Routes.ar(ArMode.SUN_MOON)) },
            "Planner" to { AppState.pendingPlannerContext.value = AppState.sky.value; go(Routes.PLANNER) },
            "Share" to { com.photocapsules.feature.common.shareText(shareCtx, "Sun information", com.photocapsules.feature.common.daySummary(AppState.sky.value, "Sun information")) },
        )
    }) { p ->
        ScrollColumn(p) {
            SkyContextBar()
            Tabs(listOf("Info", "Calendar", "Seasons"), tab) { tab = it }
            when (tab) {
                0 -> SunInfo()
                1 -> SunCalendar()
                else -> SeasonsTab()
            }
        }
    }
}

@Composable
private fun SunInfo() {
    val ctx by AppState.sky.collectAsState()
    val units = settingsState().units
    val date = ctx.instant.atZone(ctx.zone).toLocalDate()
    val day by produceState<SunDay?>(null, date, ctx.zone, ctx.point, ctx.horizonHeightM) {
        value = withContext(Dispatchers.Default) { DayEvents.sun(date, ctx.zone, ctx.point, ctx.horizonHeightM) }
    }
    val events by produceState<List<DayEvent>>(emptyList(), date, ctx.zone, ctx.point) {
        value = withContext(Dispatchers.Default) { Timeline.events(date, ctx.zone, ctx.point, ctx.horizonHeightM) }
    }
    val pos = Sky.position(SkyBody.SUN, ctx.instant, ctx.point)
    val light = DayEvents.lightType(ctx.instant, ctx.point)
    val series = rememberDaySeries(ctx)

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp).pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, dx ->
                acc += dx
                if (kotlin.math.abs(acc) > 12f) { setSkyTime(AppState.sky.value.instant.plusSeconds((-acc / 12f * 300).toLong())); acc = 0f }
            }
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(110.dp).background(Palette.light(light).let { if (light == LightType.DAY) Palette.Sun else it }, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(light.label, color = Palette.Text, fontSize = 18.sp)
            Row {
                Text("↑ " + Fmt.timeAz(day?.sunrise?.time, day?.sunrise?.azimuth, ctx.zone), color = Palette.Sunrise)
                Spacer(Modifier.width(16.dp))
                Text("↓ " + Fmt.timeAz(day?.sunset?.time, day?.sunset?.azimuth, ctx.zone), color = Palette.Sunset)
            }
            if (day?.alwaysUp == true) Text("Midnight sun – the Sun does not set", color = Palette.Accent)
            if (day?.alwaysDown == true) Text("Polar night – the Sun does not rise", color = Palette.Accent)
            Text("Swipe to change time", color = Palette.TextDim, fontSize = 11.sp)
        }
    }
    SectionTitle("Events")
    Card { events.forEach { e -> EventRow(e, ctx.zone) } }
    SectionTitle("Sun elevation")
    Card { ElevationChart(series, ctx.instant, showMoon = false) }
    SectionTitle("Information")
    Card {
        val d = day
        val nextRise = DayEvents.nextRiseSet(SkyBody.SUN, true, ctx.instant, ctx.point, 2.0, ctx.horizonHeightM)
        val nextSet = DayEvents.nextRiseSet(SkyBody.SUN, false, ctx.instant, ctx.point, 2.0, ctx.horizonHeightM)
        // Up if the next set comes before the next rise (independent of refraction thresholds).
        when {
            nextSet == null && nextRise == null ->
                if (pos.trueAltitude > -0.833) ValueRow("Time to set", "Does not set (midnight sun)") else ValueRow("Time to rise", "Does not rise (polar night)")
            nextSet != null && (nextRise == null || nextSet.time < nextRise.time) -> ValueRow("Time to set", Format.duration((nextSet.time.epochSecond - ctx.instant.epochSecond).toDouble(), false))
            else -> ValueRow("Time to rise", Format.duration((nextRise!!.time.epochSecond - ctx.instant.epochSecond).toDouble(), false))
        }
        ValueRow("Daylight", d?.let { Format.duration(it.daylightSeconds, false) } ?: "—")
        ValueRow("Azimuth", Fmt.az(pos.azimuth, 2))
        ValueRow("Elevation", Fmt.deg(pos.altitude, 2))
        ValueRow("Angular diameter", String.format(Locale.US, "%.4f°", pos.angularDiameterDeg))
        ValueRow("Distance", Fmt.km(pos.distanceKm, units))
        ValueRow("Shadow ratio", Sky.shadowRatio(pos.altitude)?.let { String.format(Locale.US, "%.2f", it) } ?: "No shadow (Sun down)")
        ValueRow("Transit (solar noon)", d?.transit?.let { Fmt.time(it.time, ctx.zone) + " · el " + Fmt.deg(it.altitude) } ?: "—")
    }
}

@Composable
fun EventRow(e: DayEvent, zone: ZoneId) {
    val color = when (e.kind) {
        EventKind.SUNRISE -> Palette.Sunrise; EventKind.SUNSET -> Palette.Sunset
        EventKind.MOONRISE -> Palette.Moonrise; EventKind.MOONSET -> Palette.Moonset
        EventKind.GOLDEN_HOUR_START, EventKind.GOLDEN_HOUR_END -> Palette.Golden
        EventKind.BLUE_HOUR_START, EventKind.BLUE_HOUR_END -> Color(0xFF7D95FF)
        EventKind.GC_VISIBLE, EventKind.GC_HIDDEN -> Palette.MilkyWay
        else -> Palette.TextDim
    }
    Row(Modifier.fillMaxWidth().clickable { setSkyTime(e.time) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(Fmt.time(e.time, zone), color = color, modifier = Modifier.width(72.dp))
        Text(e.kind.label, color = Palette.Text, modifier = Modifier.weight(1f))
        e.azimuth?.let { Text(Fmt.az(it), color = Palette.TextDim) }
    }
}

@Composable
private fun SunCalendar() {
    val ctx by AppState.sky.collectAsState()
    val ym = YearMonth.from(ctx.instant.atZone(ctx.zone))
    val rows by produceState<List<List<String>>>(emptyList(), ym, ctx.point, ctx.zone) {
        value = withContext(Dispatchers.Default) {
            (1..ym.lengthOfMonth()).map { d ->
                val day = DayEvents.sun(ym.atDay(d), ctx.zone, ctx.point, ctx.horizonHeightM)
                listOf(d.toString(), Fmt.timeAz(day.sunrise?.time, day.sunrise?.azimuth, ctx.zone), Fmt.timeAz(day.sunset?.time, day.sunset?.azimuth, ctx.zone))
            }
        }
    }
    MonthHeader(ym)
    SimpleTable(listOf("Day", "Sunrise", "Sunset"), rows, highlightRow = ctx.instant.atZone(ctx.zone).dayOfMonth - 1) { i ->
        setSkyTime(ym.atDay(i + 1).atTime(12, 0).atZone(ctx.zone).toInstant())
    }
}

@Composable
fun MonthHeader(ym: YearMonth) {
    val ctx by AppState.sky.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(ctx.zone).minusMonths(1).toInstant()) }) { Text("‹ Prev") }
        Text(ym.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + ym.year, color = Palette.Text, modifier = Modifier.weight(1f), fontSize = 16.sp)
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(ctx.zone).plusMonths(1).toInstant()) }) { Text("Next ›") }
    }
}

@Composable
private fun SeasonsTab() {
    val ctx by AppState.sky.collectAsState()
    val year = ctx.instant.atZone(ctx.zone).year
    val s = MoonCalendar.seasons(year)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(ctx.zone).minusYears(1).toInstant()) }) { Text("‹ ${year - 1}") }
        Text("$year", color = Palette.Text, modifier = Modifier.weight(1f), fontSize = 16.sp)
        TextButton(onClick = { setSkyTime(ctx.instant.atZone(ctx.zone).plusYears(1).toInstant()) }) { Text("${year + 1} ›") }
    }
    val north = ctx.point.latitude >= 0
    val items = listOf(
        (if (north) "Spring equinox" else "Autumn equinox") to s.marchEquinox,
        (if (north) "Summer solstice" else "Winter solstice") to s.juneSolstice,
        (if (north) "Autumn equinox" else "Spring equinox") to s.septemberEquinox,
        (if (north) "Winter solstice" else "Summer solstice") to s.decemberSolstice,
    )
    Card { items.forEach { (label, t) -> ValueRow(label, Fmt.dateTime(t, ctx.zone), onClick = { setSkyTime(t) }) } }
}
