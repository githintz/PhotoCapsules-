package com.photocapsules.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.photo.Camera
import com.photocapsules.ui.theme.Palette
import java.util.Locale

/** Searchable camera list plus "custom camera" entry. */
@Composable
fun CameraPickerDialog(current: Camera, onDismiss: () -> Unit, onSelect: (Camera) -> Unit) {
    val store = PhotoCapsulesApp.instance.settings
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        CustomCameraDialog(onDismiss = { custom = false }) { c -> store.saveCustomCamera(c); onSelect(c); onDismiss() }
        return
    }
    val all = remember { store.cameras() }
    val list = remember(query) {
        val terms = query.lowercase(Locale.US).split(' ').filter { it.isNotBlank() }
        all.filter { c -> terms.all { c.name.lowercase(Locale.US).contains(it) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search…") }, modifier = Modifier.fillMaxWidth())
                Text("+ Custom camera / sensor", color = Palette.Accent, modifier = Modifier.fillMaxWidth().clickable { custom = true }.padding(vertical = 10.dp))
                LazyColumn(Modifier.height(360.dp)) {
                    items(list) { c ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(c); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(c.name, color = if (c.name == current.name) Palette.Accent else Palette.Text)
                            Text(
                                String.format(Locale.US, "%.1f×%.1f mm · crop %.2f · CoC %.3f mm%s", c.sensorWidthMm, c.sensorHeightMm, c.cropFactor, c.circleOfConfusionMm,
                                    c.effectiveMegapixels?.let { String.format(Locale.US, " · %.1f MP", it) } ?: ""),
                                color = Palette.TextDim, fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun CustomCameraDialog(onDismiss: () -> Unit, onSave: (Camera) -> Unit) {
    var name by remember { mutableStateOf("My camera") }
    var w by remember { mutableStateOf("36") }
    var h by remember { mutableStateOf("24") }
    var px by remember { mutableStateOf("6000") }
    var coc by remember { mutableStateOf("") }
    val wv = w.replace(',', '.').toDoubleOrNull(); val hv = h.replace(',', '.').toDoubleOrNull()
    val pxv = px.toIntOrNull(); val cocv = coc.replace(',', '.').toDoubleOrNull()
    val valid = wv != null && hv != null && wv > 0 && hv > 0 && (px.isBlank() || (pxv != null && pxv > 0)) && (coc.isBlank() || (cocv != null && cocv > 0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom camera") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row {
                    OutlinedTextField(w, { w = it }, label = { Text("Width mm") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(h, { h = it }, label = { Text("Height mm") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(px, { px = it }, label = { Text("Horizontal pixels (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(coc, { coc = it }, label = { Text("CoC mm (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val (ww, hh) = if (wv!! >= hv!!) wv to hv else hv to wv
                onSave(Camera("Custom", name.ifBlank { "My camera" }, ww, hh, pixelsWide = pxv, cocMm = cocv))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
