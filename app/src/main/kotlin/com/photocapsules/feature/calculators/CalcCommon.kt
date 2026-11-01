package com.photocapsules.feature.calculators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.Stops
import com.photocapsules.core.photo.UnitSystem
import com.photocapsules.data.Settings
import com.photocapsules.ui.components.CameraPickerDialog
import com.photocapsules.ui.components.ChoiceDialog
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.NumberDialog
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.components.trimNumber

/** A Double persisted under [key] in settings (calculator inputs survive restarts). */
@Composable
fun rememberPersisted(key: String, default: Double): MutableState<Double> {
    val store = PhotoCapsulesApp.instance.settings
    val state = remember { mutableStateOf(store.getDouble(key, default)) }
    return object : MutableState<Double> by state {
        override var value: Double
            get() = state.value
            set(v) { state.value = v; store.putDouble(key, v) }
    }
}

@Composable
fun rememberPersistedString(key: String, default: String): MutableState<String> {
    val store = PhotoCapsulesApp.instance.settings
    val state = remember { mutableStateOf(store.getString(key, default)) }
    return object : MutableState<String> by state {
        override var value: String
            get() = state.value
            set(v) { state.value = v; store.putString(key, v) }
    }
}

@Composable
fun settingsState(): Settings = PhotoCapsulesApp.instance.settings.state.collectAsState().value

/** Camera row bound to a persisted camera name (per calculator; defaults to the settings camera). */
@Composable
fun CameraRow(key: String, label: String = "Camera"): Camera {
    val store = PhotoCapsulesApp.instance.settings
    var name by rememberPersistedString("cam_$key", store.current.cameraName)
    val camera = remember(name) { store.cameraByName(name) }
    var open by remember { mutableStateOf(false) }
    ValueRow(label, camera.name, onClick = { open = true }, sub = String.format(java.util.Locale.US, "Crop %.2f · CoC %.3f mm", camera.cropFactor, camera.circleOfConfusionMm))
    if (open) CameraPickerDialog(camera, { open = false }) { name = it.name }
    return camera
}

/** When a fixed-lens camera (compact, phone, drone) is selected, adopt its focal length. */
@Composable
fun FixedFocal(camera: Camera, apply: (Double) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(camera.name) { camera.fixedFocalMm?.let(apply) }
}

@Composable
fun NumberRow(label: String, value: Double, unit: String, display: String = trimNumber(value) + if (unit.isNotEmpty()) " $unit" else "",
              min: Double = 0.0, max: Double = Double.MAX_VALUE, allowNegative: Boolean = false, sub: String? = null, onChange: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow(label, display, onClick = { open = true }, sub = sub)
    if (open) NumberDialog(label, value, unit, min, max, allowNegative, { open = false }, onChange)
}

/** Distance row: value stored in metres, edited in the user's unit system. */
@Composable
fun DistanceRow(label: String, meters: Double, units: UnitSystem, min: Double = 0.001, onChange: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow(label, Fmt.dist(meters, units), onClick = { open = true })
    if (open) NumberDialog(label, Fmt.toDisplay(meters, units), Fmt.lenUnit(units), Fmt.toDisplay(min, units), Double.MAX_VALUE, false, { open = false }) {
        onChange(Fmt.fromDisplay(it, units))
    }
}

@Composable
fun ApertureRow(label: String = "Aperture", value: Double, onChange: (Double) -> Unit) {
    val s = settingsState()
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    ValueRow(label, Format.aperture(value), onClick = { open = true })
    if (open) ChoiceDialog(label, listOf(-1.0) + Stops.apertures(s.stopScale), { if (it < 0) "Custom…" else Format.aperture(it) }, value, { open = false }) {
        if (it < 0) custom = true else onChange(it)
    }
    if (custom) NumberDialog(label, value, "f/", 0.5, 256.0, false, { custom = false }, onChange)
}

@Composable
fun ShutterRow(label: String = "Shutter speed", value: Double, onChange: (Double) -> Unit) {
    val s = settingsState()
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    ValueRow(label, Format.shutter(value), onClick = { open = true })
    if (open) ChoiceDialog(label, listOf(-1.0) + Stops.shutters(s.stopScale), { if (it < 0) "Custom (seconds)…" else Format.shutter(it) }, value, { open = false }) {
        if (it < 0) custom = true else onChange(it)
    }
    if (custom) NumberDialog(label, value, "s", 1e-5, 1e6, false, { custom = false }, onChange)
}

@Composable
fun IsoRow(label: String = "ISO", value: Double, onChange: (Double) -> Unit) {
    val s = settingsState()
    var open by remember { mutableStateOf(false) }
    ValueRow(label, "ISO ${value.toLong()}", onClick = { open = true })
    if (open) ChoiceDialog(label, Stops.isos(s.stopScale).map { it.toDouble() }, { "ISO ${it.toLong()}" }, value, { open = false }, onChange)
}
