package com.photocapsules.feature.polar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.PolarScope
import com.photocapsules.core.astro.PolarScopeView
import com.photocapsules.data.AppState
import com.photocapsules.feature.calculators.rememberPersisted
import com.photocapsules.feature.common.SkyContextBar
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.Help
import com.photocapsules.ui.components.InfoDot
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Colours of the reticle: the normal palette, or all red to protect night vision. */
private data class ScopeColors(val background: Color, val line: Color, val dim: Color, val star: Color, val text: Color)

private val normalColors = ScopeColors(Color(0xFF0B0E24), Color(0xFFE8E8F0), Color(0xFF5A6080), Palette.Accent, Palette.Text)
private val redColors = ScopeColors(Color(0xFF120000), Color(0xFFD03030), Color(0xFF6A1818), Color(0xFFFF5040), Color(0xFFE04040))

/**
 * Polar scope: where Polaris (north) or σ Octantis (south) sits on the reticle circle right now,
 * to polar-align an equatorial mount or star tracker.
 */
@Composable
fun PolarScopeScreen(onBack: () -> Unit) {
    val ctx by AppState.sky.collectAsState()
    // 0 = inverting polar scope (the usual case), 1 = naked eye / erect image.
    var viewMode by rememberPersisted("polar_view", 0.0)
    // 0 = automatic from the latitude, 1 = north, 2 = south.
    var hemisphere by rememberPersisted("polar_hemi", 0.0)
    var red by rememberPersisted("polar_red", 0.0)

    // Live mode follows the clock every second (the star moves 1° on the dial every 4 minutes).
    com.photocapsules.feature.common.LiveClock()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(ctx.live) { while (ctx.live) { now = Instant.now(); delay(1000) } }
    val instant = if (ctx.live) now else ctx.instant

    val northern = when (hemisphere.toInt()) { 1 -> true; 2 -> false; else -> ctx.point.latitude >= 0 }
    val view = PolarScope.compute(instant, ctx.point, northern)
    val angle = if (viewMode == 0.0) view.scopeAngleDeg else view.nakedEyeAngleDeg
    val colors = if (red != 0.0) redColors else normalColors
    val textColor by animateColorAsState(colors.text, label = "polarText")

    AppScreen("Polar scope", onBack) { p ->
        ScrollColumn(p) {
            SkyContextBar()
            Reticle(view, angle, inverted = viewMode == 0.0, colors = colors)
            Card {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${view.starName} position on the dial", color = Palette.TextDim, fontSize = 13.sp)
                        InfoDot("Position on the dial", Help.row("Position on the dial").orEmpty())
                    }
                    Text(PolarScope.clockLabel(angle), color = textColor, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(if (viewMode == 0.0) "as seen in an inverting polar scope" else "as seen with the naked eye", color = Palette.TextDim, fontSize = 12.sp)
                }
            }
            SectionTitle("View", "Match what you see in the eyepiece")
            PillRow(listOf("Polar scope (inverted)", "Naked eye"), viewMode.toInt()) { viewMode = it.toDouble() }
            SectionTitle("Hemisphere", "Polaris in the north, σ Octantis in the south")
            PillRow(listOf("Auto", "North", "South"), hemisphere.toInt()) { hemisphere = it.toDouble() }
            Card {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Night vision (red)", color = Palette.Text, fontSize = 15.sp)
                    InfoDot("Night vision (red)", Help.row("Night vision (red)").orEmpty())
                    Spacer(Modifier.weight(1f))
                    Switch(red != 0.0, { red = if (it) 1.0 else 0.0 })
                }
            }
            SectionTitle("Details")
            Card {
                ValueRow("Hour angle", PolarScope.hoursLabel(view.hourAngleHours))
                ValueRow("Local sidereal time", PolarScope.hoursLabel(view.siderealTimeHours))
                ValueRow("Distance from the pole", String.format(Locale.US, "%.1f′ (%.3f°)", view.poleDistanceDeg * 60, view.poleDistanceDeg))
                ValueRow("Next upper transit", Fmt.time(view.nextUpperTransit, ctx.zone))
                ValueRow("Star coordinates (of date)", String.format(Locale.US, "RA %s · Dec %+.3f°", PolarScope.hoursLabel(view.raHours), view.decDeg))
            }
            InfoText(
                "Level the mount, aim its polar axis at the pole, then turn the altitude and azimuth bolts until ${view.starName} sits on the reticle circle " +
                    "at the position shown. The distance from the pole is computed for the date, so it stays right as the pole drifts over the years. " +
                    "Atmospheric refraction is not applied; it lifts the pole by about 1′ at latitude 40° and more near the equator."
            )
        }
    }
}

@Composable
private fun Reticle(view: PolarScopeView, angleDeg: Double, inverted: Boolean, colors: ScopeColors) {
    val bg by animateColorAsState(colors.background, label = "reticleBg")
    val line by animateColorAsState(colors.line, label = "reticleLine")
    val dim by animateColorAsState(colors.dim, label = "reticleDim")
    val starColor by animateColorAsState(colors.star, label = "reticleStar")
    // The star glides to its new place when the view or hemisphere changes (shortest way round the dial).
    val target = remember { floatArrayOf(angleDeg.toFloat()) } // unwrapped angle, so 350° → 10° turns 20°, not 340°
    target[0] += ((angleDeg.toFloat() - target[0]) % 360f + 540f) % 360f - 180f
    val shown by animateFloatAsState(target[0], tween(500), label = "reticleAngle")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulseValue",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(bg)) {
            val c = Offset(size.width / 2, size.height / 2)
            val outer = size.minDimension * 0.40f
            val circle = outer * 0.62f // the reticle circle the star must sit on
            fun at(deg: Float, r: Float): Offset {
                val a = Math.toRadians(deg.toDouble())
                return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
            }
            // Dial: a tick every 10 clock minutes (5°), longer on the half hours and hours.
            drawCircle(dim, outer, c, style = Stroke(2f))
            for (i in 0 until 72) {
                val len = when { i % 6 == 0 -> 0.12f; i % 3 == 0 -> 0.08f; else -> 0.04f }
                drawLine(if (i % 6 == 0) line else dim, at(i * 5f, outer), at(i * 5f, outer * (1 - len)), if (i % 6 == 0) 3f else 1.5f)
            }
            for (h in 1..12) label(h.toString(), at(h * 30f, outer * 1.13f), size.minDimension * 0.045f, line)
            // Crosshair through the pole and the circle for the star.
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
            drawLine(dim, at(0f, outer * 0.86f), at(180f, outer * 0.86f), 1.5f, pathEffect = dash)
            drawLine(dim, at(90f, outer * 0.86f), at(270f, outer * 0.86f), 1.5f, pathEffect = dash)
            drawCircle(line, circle, c, style = Stroke(3f))
            drawCircle(line, 4f, c)
            label(if (view.northern) "NCP" else "SCP", c + Offset(0f, size.minDimension * 0.05f), size.minDimension * 0.032f, dim)
            // The star, with a line from the pole like the hour hand of a clock.
            val star = at(shown, circle)
            drawLine(starColor.copy(alpha = 0.55f), c, star, 2.5f)
            drawCircle(starColor.copy(alpha = 0.25f * pulse), size.minDimension * 0.045f * pulse, star)
            drawCircle(starColor, size.minDimension * 0.017f, star)
            label(view.starName, at(shown, circle * 0.70f), size.minDimension * 0.036f, starColor)
        }
        Text(
            if (inverted) "Reticle as seen through the polar scope (image upside down)" else "Sky as seen facing the pole",
            color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun DrawScope.label(text: String, at: Offset, sizePx: Float, color: Color) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        textSize = sizePx
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y + sizePx * 0.35f, paint)
}
