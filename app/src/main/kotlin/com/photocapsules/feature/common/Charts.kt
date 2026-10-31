package com.photocapsules.feature.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.LightType
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.data.AppState
import com.photocapsules.data.SkyContext
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DaySeries(val start: Instant, val end: Instant, val sun: List<Double>, val moon: List<Double>, val light: List<LightType>)

suspend fun computeDaySeries(date: LocalDate, zone: ZoneId, where: GeoPoint, stepMin: Int = 10): DaySeries = withContext(Dispatchers.Default) {
    val (s, e) = DayEvents.dayWindow(date, zone)
    val sun = DayEvents.path(SkyBody.SUN, s, e, where, stepMin.toDouble())
    val moon = DayEvents.path(SkyBody.MOON, s, e, where, stepMin.toDouble())
    DaySeries(s, e, sun.map { it.second.altitude }, moon.map { it.second.altitude },
        sun.map { LightType.forSunAltitude(it.second.trueAltitude) })
}

/** Elevation chart of the Sun (and Moon) over a local day with light-type background and a time cursor. */
@Composable
fun ElevationChart(series: DaySeries?, cursor: Instant, showMoon: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(150.dp).padding(8.dp)) {
        if (series == null || series.sun.isEmpty()) return@Canvas
        val n = series.sun.size
        val w = size.width; val h = size.height
        val colW = w / n
        series.light.forEachIndexed { i, t -> drawRect(Palette.light(t), Offset(i * colW, 0f), Size(colW + 1, h)) }
        fun y(alt: Double) = (h / 2 - (alt / 90.0) * (h / 2)).toFloat()
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(w, h / 2), 1.5f)
        fun line(vals: List<Double>, c: Color) {
            for (i in 1 until vals.size) drawLine(c, Offset((i - 1) * colW, y(vals[i - 1])), Offset(i * colW, y(vals[i])), 4f)
        }
        line(series.sun, Palette.Sun)
        if (showMoon) line(series.moon, Palette.Moon)
        val total = (series.end.toEpochMilli() - series.start.toEpochMilli()).toFloat()
        val cx = ((cursor.toEpochMilli() - series.start.toEpochMilli()) / total * w).coerceIn(0f, w)
        drawLine(Color.White, Offset(cx, 0f), Offset(cx, h), 2f)
    }
}

/** Keeps the shared sky context's time current while "live". */
@Composable
fun LiveClock() {
    val ctx by AppState.sky.collectAsState()
    LaunchedEffect(ctx.live) {
        while (ctx.live) {
            delay(30_000)
            val c = AppState.sky.value
            if (c.live) AppState.sky.value = c.copy(instant = Instant.now())
        }
    }
}

@Composable
fun rememberDaySeries(ctx: SkyContext): DaySeries? {
    val date = ctx.instant.atZone(ctx.zone).toLocalDate()
    val s by produceState<DaySeries?>(null, date, ctx.zone, ctx.point) { value = computeDaySeries(date, ctx.zone, ctx.point) }
    return s
}
