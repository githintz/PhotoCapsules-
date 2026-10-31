package com.photocapsules.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photocapsules.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.min

/**
 * Moon disc with terminator. [phaseAngle] 0 = new, 180 = full (waxing < 180).
 * [brightLimbFromZenith] rotates the lit side (0 = lit side up, clockwise). For a simple "calendar" view pass
 * 90 for waxing (lit right) — handled automatically when [rotation] is null.
 */
@Composable
fun MoonDisc(phaseAngle: Double, size: Dp = 56.dp, rotation: Double? = null, background: Color = Color.Transparent, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawMoon(phaseAngle, rotation, background) }
}

fun DrawScope.drawMoon(phaseAngle: Double, rotation: Double?, background: Color = Color.Transparent, center: Offset = this.center, radius: Float = min(size.width, size.height) / 2 * 0.92f) {
    if (background != Color.Transparent) drawCircle(background, radius / 0.92f, center)
    val dark = Color(0xFF2B2F3A)
    val lit = Color(0xFFF2F0E6)
    drawCircle(dark, radius, center)
    val p = ((phaseAngle % 360) + 360) % 360
    val waxing = p < 180
    // Lit side on the right (before rotation) for waxing in the default northern view.
    val rot = (rotation ?: if (waxing) 90.0 else 270.0).toFloat() - 90f
    // k = cos(phase): terminator ellipse half-width factor
    val k = cos(Math.toRadians(p)).toFloat()
    rotate(rot, center) {
        val path = Path()
        // Lit half: right semicircle (from top to bottom through right)
        path.moveTo(center.x, center.y - radius)
        path.arcTo(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius), -90f, 180f, false)
        // Terminator: half-ellipse back to top. Width = radius * k (k>0 crescent: ellipse bulges right).
        val ew = radius * k
        val steps = 40
        for (i in 0..steps) {
            val t = Math.PI * i / steps // 0..π from bottom to top
            val x = center.x + ew * kotlin.math.sin(t).toFloat()
            val y = center.y + radius * kotlin.math.cos(t).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, lit)
    }
}

/** Polar sky plot (zenith in the centre, horizon on the edge) with a path and current position. */
@Composable
fun SkyPlot(
    paths: List<Pair<List<Pair<Double, Double>>, Color>>,
    markers: List<Triple<Double, Double, Color>>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2 * 0.95f
        val c = center
        for (alt in listOf(0, 30, 60)) drawCircle(Palette.Divider, r * (90 - alt) / 90f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        fun pt(az: Double, alt: Double): Offset {
            val rr = r * ((90 - alt.coerceIn(-5.0, 90.0)) / 90.0).toFloat()
            val a = Math.toRadians(az)
            return Offset(c.x + rr * kotlin.math.sin(a).toFloat(), c.y - rr * kotlin.math.cos(a).toFloat())
        }
        for ((pts, color) in paths) {
            var prev: Offset? = null
            for ((az, alt) in pts) {
                val o = pt(az, alt)
                if (alt >= 0 && prev != null) drawLine(color, prev, o, 3f)
                prev = if (alt >= 0) o else null
            }
        }
        for ((az, alt, color) in markers) if (alt >= 0) drawCircle(color, 8f, pt(az, alt))
    }
}
