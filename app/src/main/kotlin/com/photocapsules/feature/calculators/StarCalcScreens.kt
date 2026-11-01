package com.photocapsules.feature.calculators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.SpotStars
import com.photocapsules.core.photo.StarTrails
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BigResult
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import java.util.Locale
import kotlin.random.Random

@Composable
fun SpotStarsScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var f by rememberPersisted("ss_f", 14.0)
    var n by rememberPersisted("ss_n", 2.8)
    var dec by rememberPersisted("ss_dec", 0.0)
    var acc by rememberPersisted("ss_acc", 0.0)
    val measured by AppState.measuredDeclination.collectAsState()
    LaunchedEffect(measured) { measured?.let { dec = Math.round(it * 10) / 10.0; AppState.measuredDeclination.value = null } }
    lateinit var camera: Camera
    var npfValue = 0.0
    AppScreen("Spot Stars", onBack, bottomBar = {
        BottomActions(
            "AR" to { AppState.ar = ArRequest(AppState.sky.value, focalMm = f, aperture = n, cameraName = camera.name); go(Routes.ar(ArMode.SPOT_STARS)) },
            "Timer" to { AppState.timerPresetSeconds = npfValue; go(Routes.TIMER) },
        )
    }) { p ->
        ScrollColumn(p) {
            Card {
                camera = CameraRow("ss"); FixedFocal(camera) { f = it }
                NumberRow("Focal length", f, "mm", min = 1.0, max = 2000.0) { f = it }
                ApertureRow(value = n) { n = it }
                NumberRow("Declination", dec, "°", display = String.format(Locale.US, "%.1f°", dec), min = -90.0, max = 90.0, allowNegative = true,
                    sub = "Minimum |declination| in the frame (0° = worst case)") { dec = it }
            }
            SectionTitle("Accuracy")
            PillRow(listOf("Default", "Accurate"), acc.toInt()) { acc = it.toDouble() }
            val r = SpotStars.compute(camera, f, n, dec, if (acc == 0.0) SpotStars.Accuracy.DEFAULT else SpotStars.Accuracy.ACCURATE)
            npfValue = r.npfSeconds ?: r.rule500Seconds
            Card {
                if (r.npfSeconds != null) BigResult("NPF rule", String.format(Locale.US, "%.1f s", r.npfSeconds)) else ValueRow("NPF rule", "needs pixel count")
                ValueRow("500 rule", String.format(Locale.US, "%.1f s", r.rule500Seconds))
                r.pixelPitchUm?.let { ValueRow("Pixel pitch", String.format(Locale.US, "%.2f µm", it)) }
                ValueRow("Crop factor", String.format(Locale.US, "%.2f", camera.cropFactor))
            }
            InfoText("NPF rule (F. Michaud): Default uses the simplified rule t = (35·N + 30·p)/f; Accurate uses the full rule t = (16.856·N + 0.0997·f + 13.713·p)/f for pinpoint stars, both divided by cos(declination). Use the NPF value as a starting point and take a test shot.")
        }
    }
}

@Composable
fun StarTrailsScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var mode by rememberPersisted("st_mode", 0.0)
    var exposure by rememberPersisted("st_exp", 3600.0)
    var angle by rememberPersisted("st_angle", 30.0)
    var hemi by rememberPersisted("st_hemi", if (AppState.sky.value.point.latitude >= 0) 0.0 else 1.0)
    val seconds = if (mode == 0.0) exposure else StarTrails.exposureForAngle(angle)
    val deg = if (mode == 0.0) StarTrails.angleForExposure(exposure) else angle
    AppScreen("Star Trails", onBack, bottomBar = { BottomActions("Timer" to { AppState.timerPresetSeconds = seconds; go(Routes.TIMER) }) }) { p ->
        ScrollColumn(p) {
            SectionTitle("Calculate")
            PillRow(listOf("Rotation angle", "Exposure time"), mode.toInt()) { mode = it.toDouble() }
            Card {
                if (mode == 0.0) NumberRow("Exposure time", exposure / 60.0, "min", display = Format.duration(exposure), min = 0.01, max = 1440.0) { exposure = it * 60.0 }
                else NumberRow("Rotation angle", angle, "°", min = 0.1, max = 360.0) { angle = it }
            }
            SectionTitle("Hemisphere")
            PillRow(listOf("North", "South"), hemi.toInt()) { hemi = it.toDouble() }
            Card {
                ValueRow("Rotation angle", String.format(Locale.US, "%.2f°", deg), valueColor = Palette.Accent)
                ValueRow("Exposure time", Format.duration(seconds), valueColor = Palette.Accent)
                ValueRow("Rotation rate", String.format(Locale.US, "%.3f°/h (sidereal)", StarTrails.degreesPerHour))
            }
            Card {
                val north = hemi == 0.0
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)) {
                    drawRect(Color(0xFF070A1C), Offset.Zero, size)
                    val c = Offset(size.width / 2, size.height / 2)
                    val rnd = Random(7)
                    repeat(140) {
                        val rad = rnd.nextFloat() * size.minDimension * 0.7f + 6f
                        val start = rnd.nextFloat() * 360f
                        val sweep = deg.toFloat().coerceAtMost(360f)
                        val bright = 0.35f + rnd.nextFloat() * 0.65f
                        // Stars circle counter-clockwise around Polaris (north) and clockwise around the south pole.
                        drawArc(Color.White.copy(alpha = bright), if (north) start else start - sweep, sweep, false,
                            Offset(c.x - rad, c.y - rad), Size(2 * rad, 2 * rad), style = Stroke(1.5f + 1.5f * bright))
                    }
                    drawCircle(Palette.Accent, 4f, c)
                }
                InfoText(if (hemi == 0.0) "Simulation around the north celestial pole (Polaris)." else "Simulation around the south celestial pole (σ Octantis).")
            }
        }
    }
}
