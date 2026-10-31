package com.photocapsules.feature.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.LightType
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private data class BarData(val start: Instant, val end: Instant, val light: List<LightType>, val sun: List<Double>, val moon: List<Double>)

/**
 * Colourful time bar: light type band over a 24 h window (or 1 h in fine mode) centred on the current time,
 * with Sun (yellow) and Moon (blue) elevation curves. Drag to scrub; tap sides to jump to the previous/next
 * event; double-tap the centre for "now"; tap the centre for the date dialog; long-press for fine scale.
 */
@Composable
fun TimeBar(
    instant: Instant, zone: ZoneId, where: GeoPoint, fine: Boolean, events: List<Instant>,
    onChange: (Instant) -> Unit, onCenterTap: () -> Unit, onToggleFine: () -> Unit,
) {
    val spanSec = if (fine) 3600L else 86400L
    // Recompute the band only when the window moves by more than 1/48 of its span.
    val bucket = instant.epochSecond / (spanSec / 48)
    val data by produceState<BarData?>(null, bucket, where, fine) {
        value = withContext(Dispatchers.Default) {
            val s = Instant.ofEpochSecond(bucket * (spanSec / 48) - spanSec / 2)
            val e = s.plusSeconds(spanSec + spanSec / 24)
            val n = 120
            val times = (0..n).map { s.plusSeconds((e.epochSecond - s.epochSecond) * it / n) }
            val sun = times.map { Sky.position(SkyBody.SUN, it, where) }
            BarData(s, e, sun.map { LightType.forSunAltitude(it.trueAltitude) }, sun.map { it.altitude }, times.map { Sky.position(SkyBody.MOON, it, where).altitude })
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().height(54.dp)
                .pointerInput(instant, fine, events) {
                    detectTapGestures(
                        onTap = { o ->
                            val third = size.width / 3f
                            when {
                                o.x < third -> events.filter { it < instant.minusSeconds(30) }.maxOrNull()?.let(onChange)
                                o.x > 2 * third -> events.filter { it > instant.plusSeconds(30) }.minOrNull()?.let(onChange)
                                else -> onCenterTap()
                            }
                        },
                        onDoubleTap = { onChange(Instant.now()) },
                        onLongPress = { onToggleFine() },
                    )
                }
                .pointerInput(fine) {
                    detectHorizontalDragGestures { _, dx ->
                        val secPerPx = spanSec.toDouble() / size.width
                        onChange(Instant.ofEpochMilli(instantHolder.toEpochMilli() - (dx * secPerPx * 1000).toLong()))
                    }
                },
        ) {
            instantHolder = instant
            val d = data ?: return@Canvas
            val w = size.width; val h = size.height
            val total = (d.end.epochSecond - d.start.epochSecond).toFloat()
            val center = instant.epochSecond
            val winStart = center - spanSec / 2
            fun x(t: Long) = (t - winStart).toFloat() / spanSec * w
            val n = d.light.size - 1
            for (i in 0 until n) {
                val t0 = d.start.epochSecond + (total * i / n).toLong(); val t1 = d.start.epochSecond + (total * (i + 1) / n).toLong()
                val x0 = x(t0); val x1 = x(t1)
                if (x1 < 0 || x0 > w) continue
                drawRect(Palette.light(d.light[i]), Offset(x0, 0f), Size(x1 - x0 + 1, h))
            }
            fun y(a: Double) = (h / 2 - a / 90.0 * h / 2).toFloat()
            for (i in 1..n) {
                val t0 = d.start.epochSecond + (total * (i - 1) / n).toLong(); val t1 = d.start.epochSecond + (total * i / n).toLong()
                drawLine(Palette.Sun, Offset(x(t0), y(d.sun[i - 1])), Offset(x(t1), y(d.sun[i])), 3f)
                drawLine(Palette.Moon, Offset(x(t0), y(d.moon[i - 1])), Offset(x(t1), y(d.moon[i])), 3f)
            }
            drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, h / 2), Offset(w, h / 2), 1f)
            drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, h), 4f)
        }
        Text(Fmt.date(instant, zone) + "   " + Fmt.timeSec(instant, zone) + "   " + Fmt.zoneLabel(zone, instant) + if (fine) "   (1 h)" else "",
            color = Palette.Text, fontSize = 12.sp)
    }
}

private var instantHolder: Instant = Instant.now()
