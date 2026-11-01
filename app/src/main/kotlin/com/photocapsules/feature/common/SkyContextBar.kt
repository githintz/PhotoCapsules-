package com.photocapsules.feature.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.geo.Coordinates
import com.photocapsules.data.AppState
import com.photocapsules.data.GeocodeResult
import com.photocapsules.data.LocationService
import com.photocapsules.data.OnlineServices
import com.photocapsules.data.SkyContext
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Header showing location/date/time with prev/next day arrows and a settings dialog. */
@Composable
fun SkyContextBar() {
    val ctx by AppState.sky.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    val fmt = PhotoCapsulesApp.instance.settings.current.coordinateFormat
    androidx.compose.runtime.LaunchedEffect(ctx.point) { Fmt.setLocation(ctx.point.latitude, ctx.point.longitude, ctx.point.elevationM) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shift(ctx, -86400) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", tint = Palette.Accent) }
            Column(Modifier.weight(1f).clickable { dialog = true }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.date(ctx.instant, ctx.zone) + "  " + Fmt.time(ctx.instant, ctx.zone), color = Palette.Text, fontSize = 15.sp)
                Text(ctx.label + " · " + Coordinates.format(com.photocapsules.core.geo.LatLon(ctx.point.latitude, ctx.point.longitude), fmt),
                    color = Palette.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                Text(Fmt.zoneLabel(ctx.zone, ctx.instant) + if (ctx.live) " · live" else "", color = Palette.TextDim, fontSize = 11.sp)
            }
            IconButton(onClick = { shift(ctx, 86400) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", tint = Palette.Accent) }
        }
    }
    if (dialog) SkyContextDialog(ctx) { dialog = false }
}

/** Moves by whole calendar days in the context's time zone (keeps the local clock time across DST changes). */
private fun shift(ctx: SkyContext, seconds: Long) {
    val days = seconds / 86400
    AppState.sky.value = ctx.copy(instant = ctx.instant.atZone(ctx.zone).plusDays(days).toInstant(), live = false)
}

fun setSkyTime(instant: Instant) { AppState.sky.value = AppState.sky.value.copy(instant = instant, live = false) }

@Composable
fun SkyContextDialog(ctx: SkyContext, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // App-lifetime scope: these lookups finish after the dialog has been dismissed.
    val scope = PhotoCapsulesApp.instance.appScope
    var searching by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf(false) }
    var zones by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location & time") },
        text = {
            Column {
                Option("Now (current time)") {
                    AppState.sky.value = ctx.copy(instant = Instant.now(), live = ctx.label == "Current location"); onDismiss()
                }
                Option("Use my current location") {
                    scope.launch {
                        val loc = LocationService.current(context)
                        if (loc != null) AppState.sky.value = ctx.copy(point = GeoPoint(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0),
                            label = "Current location", zone = ZoneId.systemDefault(), live = true, instant = Instant.now())
                    }
                    onDismiss()
                }
                Option("Search a place…") { searching = true }
                Option("Enter coordinates…") { coords = true }
                Option("Change date…") {
                    val z = ctx.instant.atZone(ctx.zone)
                    DatePickerDialog(context, { _, y, m, d ->
                        AppState.sky.value = AppState.sky.value.copy(instant = z.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant(), live = false)
                    }, z.year, z.monthValue - 1, z.dayOfMonth).show()
                    onDismiss()
                }
                Option("Change time…") {
                    val z = ctx.instant.atZone(ctx.zone)
                    TimePickerDialog(context, { _, h, min ->
                        AppState.sky.value = AppState.sky.value.copy(instant = z.withHour(h).withMinute(min).toInstant(), live = false)
                    }, z.hour, z.minute, Fmt.use24h).show()
                    onDismiss()
                }
                Option("Time zone: ${ctx.zone.id}…") { zones = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (searching) PlaceSearchDialog({ searching = false }) { r -> setLocation(scope, ctx, r.latitude, r.longitude, r.name.substringBefore(',')); onDismiss() }
    if (coords) CoordinateDialog({ coords = false }) { lat, lon -> setLocation(scope, ctx, lat, lon, "Custom location"); onDismiss() }
    if (zones) ZonePickerDialog(ctx.zone, { zones = false }) { z -> AppState.sky.value = ctx.copy(zone = z, live = false); onDismiss() }
}

/** Applies a new location immediately, then refines time zone and elevation when the lookups return. */
private fun setLocation(scope: kotlinx.coroutines.CoroutineScope, ctx: SkyContext, lat: Double, lon: Double, label: String) {
    val point = GeoPoint(lat, lon, 0.0)
    AppState.sky.value = AppState.sky.value.copy(point = point, label = label, live = false)
    scope.launch {
        val tz = OnlineServices.timeZone(lat, lon)?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        val elev = OnlineServices.elevation(lat, lon)
        val cur = AppState.sky.value
        if (cur.point.latitude == lat && cur.point.longitude == lon)
            AppState.sky.value = cur.copy(point = GeoPoint(lat, lon, elev ?: 0.0), zone = tz ?: cur.zone)
    }
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(text, color = Palette.Accent, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
fun PlaceSearchDialog(onDismiss: () -> Unit, onPick: (GeocodeResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search place or address") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("e.g. Arches National Park") })
                TextButton(onClick = {
                    status = "Searching…"
                    scope.launch { results = OnlineServices.geocode(q); status = if (results.isEmpty()) "No results (internet required)" else "" }
                }) { Text("Search") }
                if (status.isNotEmpty()) Text(status, color = Palette.TextDim)
                LazyColumn(Modifier.height(260.dp)) {
                    items(results) { r ->
                        Text(r.name, color = Palette.Text, modifier = Modifier.fillMaxWidth().clickable { onPick(r); onDismiss() }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun CoordinateDialog(onDismiss: () -> Unit, onValue: (Double, Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = Coordinates.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Latitude, longitude") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("40.4168, -3.7038  or  40°25'0\"N 3°42'13\"W") }, isError = text.isNotBlank() && parsed == null)
                Text("Decimal degrees (DDD), degrees + decimal minutes (DMM) or DMS.", color = Palette.TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onValue(parsed!!.latitude, parsed.longitude); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ZonePickerDialog(current: ZoneId, onDismiss: () -> Unit, onPick: (ZoneId) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc/") || it == "UTC" }.sorted() }
    val list = remember(q) { all.filter { it.contains(q, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search…") })
                LazyColumn(Modifier.height(320.dp)) {
                    items(list) { z ->
                        Text(z, color = if (z == current.id) Palette.Accent else Palette.Text,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(ZoneId.of(z)); onDismiss() }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
