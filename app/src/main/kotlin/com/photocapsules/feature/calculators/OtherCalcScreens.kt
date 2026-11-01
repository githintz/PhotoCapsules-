package com.photocapsules.feature.calculators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.FocalLengthMatch
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.Orientation
import com.photocapsules.core.photo.SubjectDistance
import com.photocapsules.core.photo.TimeLapse
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BigResult
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.SimpleTable
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import java.util.Locale

@Composable
fun TimeLapseScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var target by rememberPersisted("tl_target", 1.0)
    var interval by rememberPersisted("tl_interval", 5.0)
    var clip by rememberPersisted("tl_clip", 10.0)
    var duration by rememberPersisted("tl_duration", 3600.0)
    var fps by rememberPersisted("tl_fps", 25.0)
    var mb by rememberPersisted("tl_mb", 25.0)
    var table by remember { mutableStateOf(false) }
    val t = TimeLapse.Target.entries[target.toInt().coerceIn(0, 2)]
    val r = TimeLapse.compute(t, interval, clip, duration, fps, mb)
    AppScreen("Time lapse", onBack, bottomBar = {
        BottomActions("Interval table" to { table = true }, "Timer" to { AppState.timerPresetSeconds = r.eventDurationSeconds; go(Routes.TIMER) })
    }) { p ->
        ScrollColumn(p) {
            SectionTitle("Calculate")
            PillRow(listOf("Interval", "Clip length", "Event duration"), t.ordinal) { target = it.toDouble() }
            Card {
                if (t != TimeLapse.Target.INTERVAL) NumberRow("Shooting interval", interval, "s", min = 0.01, max = 86400.0 * 7) { interval = it }
                if (t != TimeLapse.Target.CLIP_LENGTH) NumberRow("Clip length", clip, "s", display = Format.duration(clip), min = 0.04, max = 86400.0) { clip = it }
                if (t != TimeLapse.Target.EVENT_DURATION) NumberRow("Event duration", duration / 60.0, "min", display = Format.duration(duration), min = 0.01, max = 525600.0) { duration = it * 60.0 }
                NumberRow("Frames per second", fps, "fps", min = 1.0, max = 240.0) { fps = it }
                NumberRow("Image size", mb, "MB", min = 0.01, max = 1000.0) { mb = it }
            }
            Card {
                when (t) {
                    TimeLapse.Target.INTERVAL -> BigResult("Shooting interval", String.format(Locale.US, "%.2f s", r.intervalSeconds))
                    TimeLapse.Target.CLIP_LENGTH -> BigResult("Clip length", Format.duration(r.clipLengthSeconds))
                    TimeLapse.Target.EVENT_DURATION -> BigResult("Event duration", Format.duration(r.eventDurationSeconds))
                }
                ValueRow("Number of photos", r.photos.toString())
                ValueRow("Total memory", if (r.memoryMb >= 1024) String.format(Locale.US, "%.2f GB", r.memoryMb / 1024) else String.format(Locale.US, "%.0f MB", r.memoryMb))
            }
            InfoText("Photos = clip length × fps. Interval = event duration ÷ photos.")
        }
    }
    if (table) AlertDialog(
        onDismissRequest = { table = false }, title = { Text("Shooting intervals") },
        text = { SimpleTable(listOf("Event", "Interval"), TimeLapse.intervalTable.map { listOf(it.event, it.interval) }) },
        confirmButton = { TextButton(onClick = { table = false }) { Text("Close") } },
    )
}

@Composable
fun SubjectDistanceScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val units = settingsState().units
    var f by rememberPersisted("sd_f", 50.0)
    var w by rememberPersisted("sd_w", 0.6)
    var h by rememberPersisted("sd_h", 1.8)
    var o by rememberPersisted("sd_o", 1.0)
    lateinit var camera: Camera
    var result: SubjectDistance.Result? = null
    AppScreen("Subject distance", onBack, bottomBar = {
        BottomActions(
            "To DoF" to { result?.let { AppState.handoffFocal = f; AppState.handoffDistance = it.distanceM }; go(Routes.DOF) },
            "To FoV" to { result?.let { AppState.handoffFocal = f; AppState.handoffDistance = it.distanceM }; go(Routes.FOV) },
            "AR" to { result?.let { AppState.ar = ArRequest(AppState.sky.value, distancesM = listOf("Subject" to it.distanceM)); go(Routes.ar(ArMode.DOF)) } },
        )
    }) { p ->
        ScrollColumn(p) {
            Card {
                camera = CameraRow("sd"); FixedFocal(camera) { f = it }
                NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0) { f = it }
                DistanceRow("Subject width", w, units) { w = it }
                DistanceRow("Subject height", h, units) { h = it }
            }
            SectionTitle("Orientation")
            PillRow(listOf("Landscape", "Portrait"), o.toInt()) { o = it.toDouble() }
            val r = SubjectDistance.compute(camera, f, w, h, if (o == 0.0) Orientation.LANDSCAPE else Orientation.PORTRAIT)
            result = r
            Card {
                BigResult("Minimum distance", Fmt.dist(r.distanceM, units))
                ValueRow("Frame at that distance", Fmt.dist(r.fieldWidthM, units) + " × " + Fmt.dist(r.fieldHeightM, units))
                ValueRow("Limited by", if (r.limitingDimension.name == "HORIZONTAL") "Subject width" else "Subject height")
            }
            Card {
                Canvas(Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) {
                    val fw = r.fieldWidthM.toFloat(); val fh = r.fieldHeightM.toFloat()
                    val scale = minOf(size.width / fw, size.height / fh)
                    val tl = Offset((size.width - fw * scale) / 2, (size.height - fh * scale) / 2)
                    drawRect(Palette.Accent, tl, Size(fw * scale, fh * scale), style = Stroke(3f))
                    val sw = (w * scale).toFloat(); val sh = (h * scale).toFloat()
                    drawRect(Palette.Moon.copy(alpha = 0.6f), Offset(size.width / 2 - sw / 2, tl.y + fh * scale - sh), Size(sw, sh))
                }
            }
        }
    }
}

@Composable
fun FocalMatchScreen(onBack: () -> Unit) {
    val units = settingsState().units
    var f by rememberPersisted("fm_f", 50.0)
    var n by rememberPersisted("fm_n", 2.8)
    var s by rememberPersisted("fm_s", 5.0)
    lateinit var c1: Camera
    lateinit var c2: Camera
    AppScreen("Focal length match", onBack) { p ->
        ScrollColumn(p) {
            Card {
                c1 = CameraRow("fm1", "Camera 1"); FixedFocal(c1) { f = it }
                c2 = CameraRow("fm2", "Camera 2")
            }
            SectionTitle("Camera 1 settings")
            Card {
                NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0) { f = it }
                ApertureRow(value = n) { n = it }
                DistanceRow("Subject distance", s, units) { s = it }
            }
            if (s * 1000 <= maxOf(f, f * c2.diagonalMm / c1.diagonalMm)) { InfoText("Subject distance must exceed both focal lengths."); return@ScrollColumn }
            val r = FocalLengthMatch.compute(c1, c2, f, n, s)
            Card {
                ValueRow("Camera 2 focal length", String.format(Locale.US, "%.1f mm", r.camera2.focalMm), valueColor = Palette.Accent)
                ValueRow("Camera 2 aperture", String.format(Locale.US, "f/%.1f", r.camera2.aperture), valueColor = Palette.Accent)
            }
            SimpleTable(
                listOf("", "Camera 1", "Camera 2"),
                listOf(
                    listOf("Focal", Format.trimZeros(String.format(Locale.US, "%.1f", r.camera1.focalMm)) + " mm", String.format(Locale.US, "%.1f mm", r.camera2.focalMm)),
                    listOf("Aperture", String.format(Locale.US, "f/%.1f", r.camera1.aperture), String.format(Locale.US, "f/%.1f", r.camera2.aperture)),
                    listOf("Diag. AOV", Fmt.deg(r.camera1.fov.diagonalAngleDeg), Fmt.deg(r.camera2.fov.diagonalAngleDeg)),
                    listOf("Near", Fmt.dist(r.camera1.dof.nearLimitM, units), Fmt.dist(r.camera2.dof.nearLimitM, units)),
                    listOf("Far", Fmt.dist(r.camera1.dof.farLimitM, units), Fmt.dist(r.camera2.dof.farLimitM, units)),
                    listOf("Total DoF", Fmt.dist(r.camera1.dof.totalM, units), Fmt.dist(r.camera2.dof.totalM, units)),
                    listOf("Hyperfocal", Fmt.dist(r.camera1.dof.hyperfocalM, units), Fmt.dist(r.camera2.dof.hyperfocalM, units)),
                ),
            )
            InfoText("Equivalence: f₂ = f₁·d₂/d₁ and N₂ = N₁·d₂/d₁ (d = sensor diagonal) give the same angle of view and depth of field from the same position.")
        }
    }
}
