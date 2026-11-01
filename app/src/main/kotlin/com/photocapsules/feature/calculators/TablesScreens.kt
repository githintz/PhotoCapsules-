package com.photocapsules.feature.calculators

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.DepthOfField
import com.photocapsules.core.photo.DofInput
import com.photocapsules.core.photo.DofTables
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.Stops
import com.photocapsules.core.photo.UnitSystem
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
import com.photocapsules.ui.theme.Palette
import java.util.Locale

private fun shortDist(m: Double, units: UnitSystem): String {
    if (m.isInfinite()) return "∞"
    val v = Fmt.toDisplay(m, units)
    return if (v >= 100) String.format(Locale.US, "%.0f", v) else if (v >= 10) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%.2f", v)
}

@Composable
private fun GridTable(rowHeader: String, colHeaders: List<String>, rows: List<Pair<String, List<String>>>, selected: Pair<Int, Int>?, onSelect: (Int, Int) -> Unit) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Cell(rowHeader, header = true)
            colHeaders.forEach { Cell(it, header = true) }
        }
        rows.forEachIndexed { ri, (label, cells) ->
            Row {
                Cell(label, header = true)
                cells.forEachIndexed { ci, c -> Cell(c, selected = selected == ri to ci) { onSelect(ri, ci) } }
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean = false, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text, fontSize = 12.sp, textAlign = TextAlign.Center,
        color = if (header) Palette.TextDim else if (selected) Color.Black else Palette.Text,
        modifier = Modifier.width(64.dp).background(if (selected) Palette.Accent else if (header) Palette.SurfaceHigh else Palette.Surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 8.dp),
    )
}

@Composable
fun DofTableScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val units = settingsState().units
    var f by rememberPersisted("doft_f", 35.0)
    var mode by rememberPersisted("doft_mode", 0.0)
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val apertures = Stops.apertures(settingsState().stopScale).filter { it in 1.4..22.0 }
    lateinit var camera: Camera
    val toastCtx = LocalContext.current
    AppScreen("DoF table", onBack, bottomBar = {
        BottomActions("AR" to {
            if (sel == null) Toast.makeText(toastCtx, "Tap a cell of the table first", Toast.LENGTH_SHORT).show()
            sel?.let { (ri, ci) ->
                val s = DofTables.defaultDistancesM[ri]; val n = apertures[ci]
                if (s * 1000 > f) {
                    val r = DepthOfField.compute(DofInput(f, n, s, camera.circleOfConfusionMm))
                    AppState.ar = ArRequest(AppState.sky.value, focalMm = f, aperture = n, distancesM = listOfNotNull("Near" to r.nearLimitM, "Focus" to s, if (r.farLimitM.isFinite()) "Far" to r.farLimitM else null, "Hyperfocal" to r.hyperfocalM))
                    go(Routes.ar(ArMode.DOF))
                } else Toast.makeText(toastCtx, "The subject is closer than the focal length", Toast.LENGTH_SHORT).show()
            }
        })
    }) { p ->
        ScrollColumn(p) {
            Card {
                camera = CameraRow("doft"); FixedFocal(camera) { f = it }
                NumberRow("Focal length", f, "mm", min = 1.0, max = 5000.0) { f = it }
            }
            PillRow(listOf("Total DoF", "Near – far limits"), mode.toInt()) { mode = it.toDouble() }
            val table = DofTables.dofTable(camera, f, apertures)
            val hyper = apertures.map { DepthOfField.hyperfocalM(f, it, camera.circleOfConfusionMm) }
            val rows = listOf("H" to hyper.map { shortDist(it, units) }) + DofTables.defaultDistancesM.mapIndexed { i, s ->
                shortDist(s, units) to table[i].map { r ->
                    if (r == null) "—" else if (mode == 0.0) shortDist(r.totalM, units) else shortDist(r.nearLimitM, units) + "–" + shortDist(r.farLimitM, units)
                }
            }
            GridTable("${Fmt.lenUnit(units)}  \\  f/", apertures.map { Format.trimZeros(it.toString()) }, rows, sel?.let { it.first + 1 to it.second }) { ri, ci ->
                if (ri > 0) sel = ri - 1 to ci
            }
            InfoText("First row (H): hyperfocal distance. Rows: subject distance (${Fmt.lenUnit(units)}). Tap a cell, then AR to see it on the ground.")
            sel?.let { (ri, ci) ->
                val s = DofTables.defaultDistancesM[ri]
                table[ri][ci]?.let { r -> DofDiagram(r, s) }
            }
        }
    }
}

@Composable
fun HyperfocalTableScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val units = settingsState().units
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val apertures = Stops.apertures(settingsState().stopScale).filter { it in 1.4..22.0 }
    lateinit var camera: Camera
    val toastCtx = LocalContext.current
    AppScreen("Hyperfocal table", onBack, bottomBar = {
        BottomActions("AR" to {
            if (sel == null) Toast.makeText(toastCtx, "Tap a cell of the table first", Toast.LENGTH_SHORT).show()
            sel?.let { (ri, ci) ->
                val h = DepthOfField.hyperfocalM(DofTables.defaultFocalsMm[ri], apertures[ci], camera.circleOfConfusionMm)
                AppState.ar = ArRequest(AppState.sky.value, distancesM = listOf("Near (H/2)" to h / 2, "Hyperfocal" to h))
                go(Routes.ar(ArMode.DOF))
            }
        })
    }) { p ->
        ScrollColumn(p) {
            Card { camera = CameraRow("hyper") }
            val t = DofTables.hyperfocalTable(camera, apertures)
            GridTable("mm \\ f/", apertures.map { Format.trimZeros(it.toString()) },
                DofTables.defaultFocalsMm.mapIndexed { i, fl -> Format.trimZeros(fl.toString()) to t[i].map { shortDist(it, units) } }, sel) { ri, ci -> sel = ri to ci }
            InfoText("Hyperfocal distances in ${Fmt.lenUnit(units)} (real focal length, CoC ${String.format(Locale.US, "%.3f", camera.circleOfConfusionMm)} mm). Focusing at H gives sharpness from H/2 to infinity. Tap a cell, then AR to see it on the ground.")
        }
    }
}
