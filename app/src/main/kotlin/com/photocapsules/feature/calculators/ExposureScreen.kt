package com.photocapsules.feature.calculators

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.photocapsules.core.photo.ExposureCalculator
import com.photocapsules.core.photo.ExposureSettings
import com.photocapsules.core.photo.ExposureTarget
import com.photocapsules.core.photo.ExposureValueTable
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.NdFilter
import com.photocapsules.core.photo.Stops
import com.photocapsules.data.AppState
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.BigResult
import com.photocapsules.ui.components.BottomActions
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.ChoiceDialog
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.SimpleTable
import com.photocapsules.ui.components.ValueRow
import java.util.Locale

@Composable
fun ExposureScreen(onBack: () -> Unit, go: (String) -> Unit) {
    var target by rememberPersisted("exp_target", 0.0)
    var tN by rememberPersisted("exp_tN", 2.8); var tT by rememberPersisted("exp_tT", 30.0); var tIso by rememberPersisted("exp_tIso", 6400.0)
    var fN by rememberPersisted("exp_fN", 4.0); var fT by rememberPersisted("exp_fT", 60.0); var fIso by rememberPersisted("exp_fIso", 400.0)
    var nd by rememberPersisted("exp_nd", 0.0)
    var showEv by remember { mutableStateOf(false) }
    var ndOpen by remember { mutableStateOf(false) }
    val tgt = ExposureTarget.entries[target.toInt().coerceIn(0, 2)]
    val test = ExposureSettings(tN, tT, tIso)
    val result = ExposureCalculator.equivalent(test, tgt, fN, fT, fIso, nd)

    AppScreen("Exposure", onBack, bottomBar = {
        BottomActions(
            "EV table" to { showEv = true },
            "Timer" to { AppState.timerPresetSeconds = result.shutterSeconds; go(Routes.TIMER) },
        )
    }) { p ->
        ScrollColumn(p) {
            SectionTitle("Calculate")
            PillRow(listOf("Shutter speed", "Aperture", "ISO"), tgt.ordinal) { target = it.toDouble() }
            SectionTitle("Test settings (correctly exposed shot)")
            Card {
                ApertureRow(value = tN) { tN = it }
                ShutterRow(value = tT) { tT = it }
                IsoRow(value = tIso) { tIso = it }
                ValueRow("Exposure value (EV₁₀₀)", String.format(Locale.US, "%.2f", test.ev100))
            }
            SectionTitle("Equivalent settings")
            Card {
                if (tgt != ExposureTarget.APERTURE) ApertureRow(value = fN) { fN = it }
                if (tgt != ExposureTarget.SHUTTER) ShutterRow(value = fT) { fT = it }
                if (tgt != ExposureTarget.ISO) IsoRow(value = fIso) { fIso = it }
                ValueRow("ND filter", NdFilter.common.minBy { kotlin.math.abs(it.stops - nd) }.let { if (nd == 0.0) "None" else "${it.name} · ${Format.trimZeros(String.format(Locale.US, "%.1f", nd))} stops" }, onClick = { ndOpen = true })
            }
            Card {
                when (tgt) {
                    ExposureTarget.SHUTTER -> {
                        BigResult("Shutter speed", Format.shutter(result.shutterSeconds))
                        ValueRow("Exact", String.format(Locale.US, "%.3f s", result.shutterSeconds))
                        ValueRow("Nearest camera setting", Format.shutter(Stops.nearest(Stops.shutters(settingsState().stopScale), result.shutterSeconds)))
                    }
                    ExposureTarget.APERTURE -> {
                        BigResult("Aperture", Format.aperture(result.aperture))
                        ValueRow("Exact", String.format(Locale.US, "f/%.2f", result.aperture))
                        ValueRow("Nearest camera setting", Format.aperture(Stops.nearest(Stops.apertures(settingsState().stopScale), result.aperture)))
                    }
                    ExposureTarget.ISO -> {
                        BigResult("ISO", "ISO " + result.iso.toLong())
                        ValueRow("Nearest camera setting", "ISO " + Stops.nearest(Stops.isos(settingsState().stopScale), result.iso))
                    }
                }
                ValueRow("Camera EV₁₀₀ (behind filter)", String.format(Locale.US, "%.2f", result.ev100))
            }
            InfoText("Reciprocity law: equivalent exposures keep N²/(t·ISO) × filter factor constant. Film reciprocity failure is not modelled.")
        }
    }
    if (ndOpen) ChoiceDialog("ND filter", NdFilter.common, { if (it.stops == 0.0) "None" else "${it.name} – ${Format.trimZeros(String.format(Locale.US, "%.1f", it.stops))} stops" }, null, { ndOpen = false }) { nd = it.stops }
    if (showEv) AlertDialog(
        onDismissRequest = { showEv = false },
        title = { Text("Exposure values (ISO 100)") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                SimpleTable(listOf("EV", "Situation"), ExposureValueTable.entries.map { listOf(Format.trimZeros(it.ev100.toString()), it.situation) }) { i ->
                    // Tap: set the test shot to f/test at the chosen EV (keep aperture and ISO, solve shutter)
                    tT = ExposureCalculator.shutterForEv(ExposureValueTable.entries[i].ev100, tN, tIso)
                    showEv = false
                }
            }
        },
        confirmButton = { TextButton(onClick = { showEv = false }) { Text("Close") } },
    )
}
