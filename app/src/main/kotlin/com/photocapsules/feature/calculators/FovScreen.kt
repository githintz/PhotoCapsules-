package com.photocapsules.feature.calculators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.FieldOfView
import com.photocapsules.core.photo.FovDimension
import com.photocapsules.core.photo.FovResult
import com.photocapsules.core.photo.Orientation
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.Tabs
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import java.util.Locale

@Composable
fun FovScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val units = settingsState().units
    var f by rememberPersisted("fov_f", 24.0)
    var s by rememberPersisted("fov_s", 10.0)
    var o by rememberPersisted("fov_orient", 0.0)
    var tc by rememberPersisted("fov_tc", 1.0)
    AppState.handoffFocal?.let { f = it; AppState.handoffFocal = null }
    AppState.handoffDistance?.let { s = it; AppState.handoffDistance = null }
    val orientation = if (o == 0.0) Orientation.LANDSCAPE else Orientation.PORTRAIT
    var result: FovResult? = null
    val shown = androidx.compose.runtime.remember { arrayOfNulls<FovResult>(1) }

    AppScreen("Field of view", onBack, bottomBar = {
        BottomActions(
            "To DoF" to { AppState.handoffFocal = f; AppState.handoffDistance = s; go(Routes.DOF) },
            "AR" to {
                // Use exactly the result on screen (Classic or Inverse tab, teleconverter included).
                val r = shown[0] ?: FieldOfView.compute(PhotoCapsulesCamera.current(), f, s, orientation, tc)
                val focal35 = FieldOfView.focalForAngle(PhotoCapsulesCamera.current(), orientation, com.photocapsules.core.photo.FovDimension.HORIZONTAL, r.horizontalAngleDeg)
                AppState.ar = ArRequest(AppState.sky.value, focalMm = focal35, frameHAngleDeg = r.horizontalAngleDeg, frameVAngleDeg = r.verticalAngleDeg)
                go(Routes.ar(ArMode.FOV))
            },
        )
    }) { p ->
        ScrollColumn(p) {
            Tabs(listOf("Classic", "Inverse"), tab) { tab = it }
            lateinit var camera: Camera
            if (tab == 0) {
                Card {
                    camera = CameraRow("fov").also { PhotoCapsulesCamera.set(it) }; FixedFocal(camera) { f = it }
                    NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0, sub = "35 mm equiv. " + String.format(Locale.US, "%.0f", f * tc * camera.cropFactor) + " mm") { f = it }
                    DistanceRow("Subject distance", s, units) { s = it }
                    TeleconverterRow(tc) { tc = it }
                }
                SectionTitle("Orientation")
                PillRow(listOf("Landscape", "Portrait"), o.toInt()) { o = it.toDouble() }
                result = FieldOfView.compute(camera, f, s, orientation, tc)
            } else {
                var solve by rememberPersisted("ifov_solve", 0.0)
                var dim by rememberPersisted("ifov_dim", 0.0)
                var field by rememberPersisted("ifov_field", 20.0)
                SectionTitle("Calculate")
                PillRow(listOf("Focal length", "Subject distance"), solve.toInt()) { solve = it.toDouble() }
                Card {
                    camera = CameraRow("fov").also { PhotoCapsulesCamera.set(it) }; FixedFocal(camera) { f = it }
                    if (solve == 0.0) DistanceRow("Subject distance", s, units) { s = it } else NumberRow("Focal length", f, "mm", min = 1.0) { f = it }
                    DistanceRow("Desired field of view", field, units) { field = it }
                }
                SectionTitle("Field dimension")
                PillRow(listOf("Horizontal", "Vertical", "Diagonal"), dim.toInt()) { dim = it.toDouble() }
                SectionTitle("Orientation")
                PillRow(listOf("Landscape", "Portrait"), o.toInt()) { o = it.toDouble() }
                val which = FovDimension.entries[dim.toInt().coerceIn(0, 2)]
                if (solve == 0.0) {
                    val fs = FieldOfView.focalForField(camera, orientation, which, field, s)
                    Card { ValueRow("Focal length", String.format(Locale.US, "%.1f mm", fs), valueColor = Palette.Accent) }
                    result = FieldOfView.compute(camera, fs, s, orientation)
                } else {
                    val ds = FieldOfView.distanceForField(camera, orientation, which, field, f)
                    Card { ValueRow("Subject distance", Fmt.dist(ds, units), valueColor = Palette.Accent) }
                    result = FieldOfView.compute(camera, f, ds, orientation)
                }
            }
            shown[0] = result
            result?.let { r ->
                Card {
                    ValueRow("Horizontal angle of view", Fmt.deg(r.horizontalAngleDeg, 2))
                    ValueRow("Vertical angle of view", Fmt.deg(r.verticalAngleDeg, 2))
                    ValueRow("Diagonal angle of view", Fmt.deg(r.diagonalAngleDeg, 2))
                    ValueRow("Horizontal field", Fmt.dist(r.horizontalFieldM, units))
                    ValueRow("Vertical field", Fmt.dist(r.verticalFieldM, units))
                    ValueRow("Diagonal field", Fmt.dist(r.diagonalFieldM, units))
                }
                Card {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).padding(16.dp)) {
                        val ratio = (r.horizontalFieldM / r.verticalFieldM).toFloat()
                        val w = minOf(size.width, size.height * ratio); val h = w / ratio
                        val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
                        drawRect(Palette.SurfaceHigh, tl, Size(w, h))
                        drawRect(Palette.Accent, tl, Size(w, h), style = Stroke(3f))
                        // A 1.8 m person for scale
                        val ph = (1.8 / r.verticalFieldM * h).toFloat().coerceAtMost(h)
                        drawLine(Palette.Moon, Offset(size.width / 2, tl.y + h), Offset(size.width / 2, tl.y + h - ph), 6f)
                    }
                    InfoText("Blue bar: a 1.8 m tall person at the subject distance.")
                }
            }
        }
    }
}

/** Holds the camera last selected on the FoV screen for the AR hand-off. */
object PhotoCapsulesCamera {
    private var cam: Camera? = null
    fun set(c: Camera) { cam = c }
    fun current(): Camera = cam ?: com.photocapsules.PhotoCapsulesApp.instance.settings.camera()
}
