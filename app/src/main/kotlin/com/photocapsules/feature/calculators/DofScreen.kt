package com.photocapsules.feature.calculators

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import com.photocapsules.core.photo.CircleOfConfusion
import com.photocapsules.core.photo.DepthOfField
import com.photocapsules.core.photo.DofInput
import com.photocapsules.core.photo.DofResult
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.UnitSystem
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.ChoiceDialog
import com.photocapsules.ui.components.Divider
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.Tabs
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.util.Locale

@Composable
fun DofScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val units = settingsState().units
    var f by rememberPersisted("dof_f", 24.0)
    var n by rememberPersisted("dof_n", 8.0)
    var s by rememberPersisted("dof_s", 5.0)
    var tc by rememberPersisted("dof_tc", 1.0)
    AppState.handoffFocal?.let { f = it; AppState.handoffFocal = null }
    AppState.handoffAperture?.let { n = it; AppState.handoffAperture = null }
    AppState.handoffDistance?.let { s = it; AppState.handoffDistance = null }
    val holder = remember { arrayOfNulls<DofResult>(1) }

    AppScreen("Depth of field", onBack, bottomBar = {
        BottomActions(
            "To FoV" to { AppState.handoffFocal = f; AppState.handoffDistance = s; go(Routes.FOV) },
            "AR" to {
                val r = holder[0]
                if (r != null) {
                    AppState.ar = ArRequest(AppState.sky.value, focalMm = f, aperture = n, distancesM = listOfNotNull(
                        "Near" to r.nearLimitM, "Focus" to s, if (r.farLimitM.isFinite()) "Far" to r.farLimitM else null, "Hyperfocal" to r.hyperfocalM,
                    ))
                    go(Routes.ar(ArMode.DOF))
                }
            },
        )
    }) { p ->
        ScrollColumn(p) {
            Tabs(listOf("Classic", "Inverse", "Advanced"), tab) { tab = it }
            when (tab) {
                0 -> {
                    lateinit var camera: Camera
                    Card {
                        camera = CameraRow("dof")
                        NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0, sub = "35 mm equiv. " + Format.trimZeros(String.format(Locale.US, "%.1f", f * camera.cropFactor)) + " mm") { f = it }
                        ApertureRow(value = n) { n = it }
                        DistanceRow("Subject distance", s, units) { s = it }
                        TeleconverterRow(tc) { tc = it }
                    }
                    holder[0] = DofResults(camera, camera.circleOfConfusionMm, f, n, s, tc, units)
                }
                1 -> InverseDof(units, tc)
                else -> {
                    lateinit var camera: Camera
                    var pw by rememberPersisted("dof_pw", CircleOfConfusion.DEFAULT_PRINT_WIDTH_M)
                    var ph by rememberPersisted("dof_ph", CircleOfConfusion.DEFAULT_PRINT_HEIGHT_M)
                    var vd by rememberPersisted("dof_vd", CircleOfConfusion.DEFAULT_VIEWING_DISTANCE_M)
                    var ac by rememberPersisted("dof_acuity", 0.0)
                    var acOpen by remember { mutableStateOf(false) }
                    val acuity = CircleOfConfusion.Acuity.entries[ac.toInt().coerceIn(0, 3)]
                    Card {
                        camera = CameraRow("dof")
                        SectionTitle("Circle of confusion")
                        DistanceRow("Print width", pw, units) { pw = it }
                        DistanceRow("Print height", ph, units) { ph = it }
                        DistanceRow("Viewing distance", vd, units) { vd = it }
                        ValueRow("Visual acuity", acuity.label, onClick = { acOpen = true })
                    }
                    val coc = CircleOfConfusion.compute(camera.diagonalMm, pw, ph, vd, acuity)
                    Card {
                        ValueRow("Adjusted CoC", String.format(Locale.US, "%.4f mm", coc), valueColor = Palette.Accent)
                        ValueRow("Load default values", "8×10 in · 25 cm", onClick = {
                            pw = CircleOfConfusion.DEFAULT_PRINT_WIDTH_M; ph = CircleOfConfusion.DEFAULT_PRINT_HEIGHT_M; vd = CircleOfConfusion.DEFAULT_VIEWING_DISTANCE_M; ac = 0.0
                        })
                    }
                    Card {
                        NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0) { f = it }
                        ApertureRow(value = n) { n = it }
                        DistanceRow("Subject distance", s, units) { s = it }
                        TeleconverterRow(tc) { tc = it }
                    }
                    holder[0] = DofResults(camera, coc, f, n, s, tc, units)
                    if (acOpen) ChoiceDialog("Visual acuity", CircleOfConfusion.Acuity.entries, { it.label }, acuity, { acOpen = false }) { ac = it.ordinal.toDouble() }
                }
            }
        }
    }
}

@Composable
fun TeleconverterRow(tc: Double, onChange: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow("Teleconverter", if (tc == 1.0) "None" else "×" + Format.trimZeros(tc.toString()), onClick = { open = true })
    if (open) ChoiceDialog("Teleconverter", listOf(1.0, 1.4, 1.7, 2.0), { if (it == 1.0) "None" else "×$it" }, tc, { open = false }, onChange)
}

@Composable
private fun DofResults(camera: Camera, coc: Double, f: Double, n: Double, s: Double, tc: Double, units: UnitSystem): DofResult? {
    if (s * 1000 <= f * tc) {
        Card { ValueRow("Subject distance must be longer than the focal length", "") }
        return null
    }
    val r = DepthOfField.compute(DofInput(f, n, s, coc, tc))
    Card {
        ValueRow("Hyperfocal distance", Fmt.dist(r.hyperfocalM, units), valueColor = Palette.Moon)
        ValueRow("Hyperfocal near limit", Fmt.dist(r.hyperfocalNearLimitM, units))
        Divider()
        ValueRow("Near limit", Fmt.dist(r.nearLimitM, units))
        ValueRow("Far limit", Fmt.dist(r.farLimitM, units))
        ValueRow("Total depth of field", Fmt.dist(r.totalM, units), valueColor = Palette.Accent)
        ValueRow("In front of subject", Fmt.dist(r.inFrontM, units))
        ValueRow("Behind subject", Fmt.dist(r.behindM, units))
    }
    DofDiagram(r, s)
    if (tc != 1.0) InfoText("Teleconverter ×$tc: effective ${Format.trimZeros(String.format(Locale.US, "%.0f", f * tc))} mm ${Format.aperture(n * tc)}")
    return r
}

/** Distance bar: camera at left, near/far shaded, focus (red) and hyperfocal (blue) marks. */
@Composable
fun DofDiagram(r: DofResult, focus: Double) {
    Card {
        Canvas(Modifier.fillMaxWidth().height(70.dp).padding(12.dp)) {
            val maxD = maxOf(r.hyperfocalM * 1.3, if (r.farLimitM.isFinite()) r.farLimitM * 1.2 else 0.0, focus * 1.5)
            fun x(d: Double) = (size.width * (d / maxD)).toFloat().coerceIn(0f, size.width)
            val y = size.height / 2
            drawLine(Palette.Divider, Offset(0f, y), Offset(size.width, y), 4f)
            val far = if (r.farLimitM.isFinite()) x(r.farLimitM) else size.width
            drawRect(Palette.Green.copy(alpha = 0.5f), Offset(x(r.nearLimitM), y - 14), Size(far - x(r.nearLimitM), 28f))
            drawLine(Palette.Red, Offset(x(focus), y - 22), Offset(x(focus), y + 22), 5f)
            drawLine(Palette.Moon, Offset(x(r.hyperfocalM), y - 22), Offset(x(r.hyperfocalM), y + 22), 3f)
            drawCircle(Palette.Accent, 8f, Offset(0f, y), style = Stroke(3f))
        }
        Text("  ● camera   ▮ sharp zone   red: focus   blue: hyperfocal", color = Palette.TextDim, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
    }
}

@Composable
private fun InverseDof(units: UnitSystem, tcInit: Double) {
    var solve by rememberPersisted("idof_solve", 0.0) // 0 distance, 1 aperture, 2 focal
    var f by rememberPersisted("idof_f", 50.0)
    var n by rememberPersisted("idof_n", 8.0)
    var s by rememberPersisted("idof_s", 5.0)
    var target by rememberPersisted("idof_target", 2.0)
    var tc by rememberPersisted("idof_tc", tcInit)
    SectionTitle("Calculate")
    PillRow(listOf("Subject distance", "Aperture", "Focal length"), solve.toInt()) { solve = it.toDouble() }
    lateinit var camera: Camera
    Card {
        camera = CameraRow("dof")
        if (solve.toInt() != 2) NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0) { f = it }
        if (solve.toInt() != 1) ApertureRow(value = n) { n = it }
        if (solve.toInt() != 0) DistanceRow("Subject distance", s, units) { s = it }
        TeleconverterRow(tc) { tc = it }
        DistanceRow("Desired depth of field", target, units) { target = it }
    }
    val coc = camera.circleOfConfusionMm
    val solved: Triple<Double, Double, Double>? = when (solve.toInt()) {
        0 -> DepthOfField.distanceForTotalDof(f, n, coc, target, tc)?.let { Triple(f, n, it) }
        1 -> DepthOfField.apertureForTotalDof(f, s, coc, target, tc)?.let { Triple(f, it, s) }
        else -> DepthOfField.focalForTotalDof(n, s, coc, target, tc)?.let { Triple(it, n, s) }
    }
    if (solved == null) {
        Card { ValueRow("No solution for these settings", "—") }
        InfoText("The desired depth of field cannot be reached by changing only the selected setting.")
        return
    }
    Card {
        when (solve.toInt()) {
            0 -> ValueRow("Subject distance", Fmt.dist(solved.third, units), valueColor = Palette.Accent)
            1 -> ValueRow("Aperture", String.format(Locale.US, "f/%.1f", solved.second), valueColor = Palette.Accent)
            else -> ValueRow("Focal length", String.format(Locale.US, "%.1f mm", solved.first), valueColor = Palette.Accent)
        }
    }
    val r = DepthOfField.compute(DofInput(solved.first, solved.second, solved.third, coc, tc))
    Card {
        ValueRow("Hyperfocal distance", Fmt.dist(r.hyperfocalM, units))
        ValueRow("Near limit", Fmt.dist(r.nearLimitM, units))
        ValueRow("Far limit", Fmt.dist(r.farLimitM, units))
        ValueRow("Total depth of field", Fmt.dist(r.totalM, units))
        ValueRow("In front / behind", Fmt.dist(r.inFrontM, units) + " / " + Fmt.dist(r.behindM, units))
    }
    DofDiagram(r, solved.third)
}
