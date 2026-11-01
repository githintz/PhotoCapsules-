package com.photocapsules.feature.planner

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.astro.Eclipses
import com.photocapsules.core.astro.FindQuery
import com.photocapsules.core.astro.FindResult
import com.photocapsules.core.astro.FindSearch
import com.photocapsules.core.astro.LunarEclipse
import com.photocapsules.core.astro.MeteorShowers
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.astro.SolarEclipse
import com.photocapsules.core.astro.SolarEclipseMap
import com.photocapsules.core.data.LibraryRepository
import com.photocapsules.core.data.LibraryState
import com.photocapsules.core.data.PointOfInterest
import com.photocapsules.core.geo.Coordinates
import com.photocapsules.core.geo.LatLon
import com.photocapsules.core.photo.Orientation
import com.photocapsules.data.MapType
import com.photocapsules.feature.common.CoordinateDialog
import com.photocapsules.feature.common.PlaceSearchDialog
import com.photocapsules.feature.common.ZonePickerDialog
import com.photocapsules.feature.mystuff.addCalendarAlert
import com.photocapsules.feature.mystuff.openInMaps
import com.photocapsules.feature.mystuff.shareKmz
import com.photocapsules.ui.components.ChoiceDialog
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.MoonDisc
import com.photocapsules.ui.components.NumberDialog
import com.photocapsules.ui.components.PillRow
import com.photocapsules.ui.components.SimpleTable
import com.photocapsules.ui.components.TextInputDialog
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
private fun Opt(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(text, color = if (enabled) Palette.Accent else Palette.TextDim, modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp))
}

// ---------------- Find ----------------

@Composable
fun FindDialog(m: PlannerModel, mapCenter: () -> LatLon?, onPickWithAr: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val g = m.geodetics()
    val arTarget = remember { com.photocapsules.data.AppState.findTarget.value.also { com.photocapsules.data.AppState.findTarget.value = null } }
    var body by remember { mutableIntStateOf(m.findBody) }
    var rangeIdx by remember { mutableIntStateOf(m.findRangeIdx) }
    var start by remember { mutableStateOf(m.findStart ?: m.instant) }
    var az by remember { mutableDoubleStateOf(arTarget?.azimuth ?: m.findAz ?: g?.bearing ?: 270.0) }
    var azTol by remember { mutableDoubleStateOf(m.findAzTol) }
    var useAlt by remember { mutableStateOf(m.findUseAlt || arTarget != null) }
    var alt by remember { mutableDoubleStateOf(arTarget?.altitude ?: m.findAlt ?: g?.elevationAngle ?: 0.0) }
    var altTol by remember { mutableDoubleStateOf(m.findAltTol) }
    // Remember the inputs in the model so they survive closing the dialog (AR pointing, map checks).
    LaunchedEffect(rangeIdx, start, az, azTol, useAlt, alt, altTol) {
        m.findRangeIdx = rangeIdx; m.findStart = start; m.findAz = az; m.findAzTol = azTol
        m.findUseAlt = useAlt; m.findAlt = alt; m.findAltTol = altTol
    }
    var edit by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var results by remember { mutableStateOf<List<FindResult>?>(null) }
    var sortBy by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val ranges = listOf("1 week" to 7L, "1 month" to 31L, "6 months" to 183L, "1 year" to 366L, "2 years" to 731L, "5 years" to 1827L, "10 years" to 3653L)
    AlertDialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        title = { Text("Find") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PillRow(listOf("Sun", "Moon"), body) { body = it; m.findBody = it }
                ValueRow("Start date", Fmt.date(start, m.zone), onClick = {
                    val z = start.atZone(m.zone)
                    DatePickerDialog(ctx, { _, y, mo, d -> start = z.withYear(y).withMonth(mo + 1).withDayOfMonth(d).toLocalDate().atStartOfDay(m.zone).toInstant() }, z.year, z.monthValue - 1, z.dayOfMonth).show()
                })
                ValueRow("Date range", ranges[rangeIdx].first + " (to " + Fmt.date(start.plusSeconds(ranges[rangeIdx].second * 86400), m.zone) + ")", onClick = { edit = "range" })
                ValueRow("Azimuth", Fmt.az(az, 2) + String.format(Locale.US, " ± %.1f°", azTol), onClick = { edit = "az" })
                ValueRow("Azimuth tolerance", String.format(Locale.US, "± %.1f°", azTol), onClick = { edit = "azTol" })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Elevation", color = Palette.Text, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Switch(useAlt, { useAlt = it })
                }
                if (useAlt) {
                    ValueRow("Elevation", String.format(Locale.US, "%.2f° ± %.2f°", alt, altTol), onClick = { edit = "alt" })
                    ValueRow("Elevation tolerance", String.format(Locale.US, "± %.2f°", altTol), onClick = { edit = "altTol" })
                }
                if (g != null) Opt("Use Black Pin (az ${Fmt.az(g.bearing, 2)}, el ${String.format(Locale.US, "%+.2f°", g.elevationAngle)})") { az = g.bearing; alt = g.elevationAngle; useAlt = true }
                Opt("Use map centre as target") {
                    mapCenter()?.let { c ->
                        val inv = com.photocapsules.core.geo.Geodesy.inverse(m.red, c)
                        if (inv.distanceMeters > 1.0) {
                            az = inv.initialBearing
                            scope.launch {
                                val h = com.photocapsules.data.OnlineServices.elevation(c.latitude, c.longitude) ?: 0.0
                                alt = com.photocapsules.core.geo.Terrain.elevationAngle(inv.distanceMeters, m.redAltitude, h); useAlt = true
                            }
                        }
                    }
                }
                Opt("Point with AR (camera)") { job?.cancel(); onPickWithAr() }
                if (arTarget != null) Text("Target set from AR view", color = Palette.Green, fontSize = 12.sp)
                if (progress >= 0f && results == null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                results?.let { list ->
                    val capped = list.size >= FindQuery.DEFAULT_MAX_RESULTS
                    Text("${list.size} results" + (if (capped) " (limit reached: the search stopped on ${Fmt.date(list.last().time, m.zone)})" else "") +
                        (if (list.size > SHOWN_RESULTS) " · showing the first $SHOWN_RESULTS in this order" else ""),
                        color = if (capped) Palette.Sunset else Palette.TextDim, modifier = Modifier.padding(6.dp))
                    PillRow(listOf("Date", "Az", "El", "Phase"), sortBy) { sortBy = it }
                    val sorted = when (sortBy) { 1 -> list.sortedBy { it.azimuth }; 2 -> list.sortedBy { it.altitude }; 3 -> list.sortedByDescending { it.moonIllumination ?: 0.0 }; else -> list }
                    sorted.take(SHOWN_RESULTS).forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { m.instant = r.time; job?.cancel(); onDismiss() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.dateTime(r.time, m.zone), color = Palette.Text, fontSize = 13.sp)
                                Text("az " + Fmt.az(r.azimuth, 2) + String.format(Locale.US, " · el %.2f° · %s", r.altitude, r.lightType.label), color = Palette.TextDim, fontSize = 11.sp)
                            }
                            r.moonIllumination?.let { Text(Fmt.pct(it), color = Palette.Moon, fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                job?.cancel()
                results = null; progress = 0f
                val q = FindQuery(if (body == 0) SkyBody.SUN else SkyBody.MOON, m.observer, start, start.plusSeconds(ranges[rangeIdx].second * 86400), az, azTol, if (useAlt) alt else null, altTol)
                job = scope.launch {
                    // Each search checks its own coroutine, so a superseded search stops and never writes state.
                    val r = withContext(Dispatchers.Default) { FindSearch.search(q, cancelled = { !isActive }) { p -> if (isActive) progress = p.toFloat() } }
                    if (isActive) results = r
                }
            }) { Text("Search") }
        },
        dismissButton = { TextButton(onClick = { job?.cancel(); onDismiss() }) { Text("Close") } },
    )
    when (edit) {
        "range" -> ChoiceDialog("Date range", ranges, { it.first }, ranges[rangeIdx], { edit = null }) { rangeIdx = ranges.indexOf(it) }
        // Entered in the reference chosen in Settings (true or magnetic north); searched in true azimuth.
        "az" -> NumberDialog(if (Fmt.magneticAzimuths) "Azimuth (magnetic)" else "Azimuth (true north)", if (Fmt.magneticAzimuths) com.photocapsules.core.astro.normalizeDegrees(az - Fmt.declination) else az, "°", 0.0, 360.0, false, { edit = null }) {
            az = if (Fmt.magneticAzimuths) com.photocapsules.core.astro.normalizeDegrees(it + Fmt.declination) else it
        }
        "azTol" -> NumberDialog("Azimuth tolerance", azTol, "°", 0.0, 45.0, false, { edit = null }) { azTol = it }
        "alt" -> NumberDialog("Elevation", alt, "°", -5.0, 90.0, true, { edit = null }) { alt = it }
        "altTol" -> NumberDialog("Elevation tolerance", altTol, "°", 0.0, 30.0, false, { edit = null }) { altTol = it }
    }
}

// ---------------- Load / Save ----------------

@Composable
fun LoadDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf<String?>(null) }
    val repo = PhotoCapsulesApp.instance.library
    // The Photo Picker strips GPS EXIF, so use the document picker and (on Android 10+) request the original
    // file with ACCESS_MEDIA_LOCATION granted.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ll = readPhotoLatLong(ctx, uri)
            if (ll != null) { m.moveRed(LatLon(ll[0], LatLon.normalizeLongitude(ll[1]))); m.redCustomAltitude = null; m.centerRequest++ }
            else Toast.makeText(ctx, "This photo has no GPS location (or the location was removed when it was shared)", Toast.LENGTH_LONG).show()
        }
        onDismiss()
    }
    val mediaPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { photoPicker.launch(arrayOf("image/*")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load") },
        text = {
            Column {
                Opt("Search address / place") { sub = "search" }
                Opt("Plan") { sub = "plan" }
                Opt("Point of Interest") { sub = "poi" }
                Opt("Latitude / longitude") { sub = "coords" }
                Opt("Geotagged photo") {
                    val perms = buildList {
                        if (android.os.Build.VERSION.SDK_INT >= 29) add(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                        if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.READ_MEDIA_IMAGES) else add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    mediaPerms.launch(perms.toTypedArray())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    when (sub) {
        "search" -> PlaceSearchDialog({ sub = null }) { r -> m.moveRed(LatLon(r.latitude, r.longitude)); m.centerRequest++; onDismiss() }
        "coords" -> CoordinateDialog({ sub = null }) { lat, lon -> m.moveRed(LatLon(lat, lon)); m.centerRequest++; onDismiss() }
        "plan" -> {
            val plans = repo.load().plans.sortedByDescending { it.epochMillis }
            ChoiceDialog("Plans", plans, { it.name + " · " + Fmt.dateTime(Instant.ofEpochMilli(it.epochMillis), runCatching { ZoneId.of(it.zoneId) }.getOrDefault(m.zone)) }, null, { sub = null }) { p -> m.load(p); onDismiss() }
        }
        "poi" -> {
            val pois = repo.load().pois.sortedBy { it.name.lowercase() }
            ChoiceDialog("Points of Interest", pois, { it.name }, null, { sub = null }) { p -> m.moveRed(LatLon(p.latitude, p.longitude)); m.redCustomAltitude = p.altitudeM; m.centerRequest++; onDismiss() }
        }
    }
}

/** Reads latitude/longitude from a photo's EXIF, asking MediaStore for the unredacted original when possible. */
fun readPhotoLatLong(ctx: Context, uri: android.net.Uri): DoubleArray? {
    fun read(u: android.net.Uri) = runCatching { ctx.contentResolver.openInputStream(u)!!.use { ExifInterface(it).latLong } }.getOrNull()
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        // Document URIs from the media provider can be mapped to a MediaStore URI and opened as the original.
        val mediaUri = runCatching { android.provider.MediaStore.getMediaUri(ctx, uri) }.getOrNull() ?: uri.takeIf { it.authority == android.provider.MediaStore.AUTHORITY }
        if (mediaUri != null) runCatching { read(android.provider.MediaStore.setRequireOriginal(mediaUri)) }.getOrNull()?.let { return it }
    }
    return read(uri)
}

@Composable
fun SaveDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf<String?>(null) }
    val repo = PhotoCapsulesApp.instance.library
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save") },
        text = {
            Column {
                Opt("New plan") { sub = "plan" }
                Opt("Overwrite existing plan") { sub = "overwrite" }
                Opt("Point of Interest (Red Pin)") { sub = "poi" }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    when (sub) {
        "plan" -> TextInputDialog("Plan name", "Plan " + Fmt.dateShort(m.instant, m.zone), { sub = null }) { name ->
            repo.upsertPlan(m.toPlan(LibraryRepository.newId(), name)); Toast.makeText(ctx, "Plan saved", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        "overwrite" -> ChoiceDialog("Overwrite plan", repo.load().plans, { it.name }, null, { sub = null }) { p ->
            repo.upsertPlan(m.toPlan(p.id, p.name, p.notes).copy(createdMillis = p.createdMillis, photoUris = p.photoUris)); Toast.makeText(ctx, "Plan updated", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        "poi" -> TextInputDialog("Point of Interest name", "My location", { sub = null }) { name ->
            repo.upsertPoi(PointOfInterest(LibraryRepository.newId(), name, m.red.latitude, m.red.longitude, m.redCustomAltitude ?: m.redTerrain)); Toast.makeText(ctx, "Point of interest saved", Toast.LENGTH_SHORT).show(); onDismiss()
        }
    }
}

// ---------------- More ----------------

@Composable
fun MoreDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("More") },
        text = {
            Column {
                Opt("Date, time & time zone") { sub = "date" }
                Opt("Altitudes & offsets") { sub = "alt" }
                Opt("Horizon (height above horizon)") { sub = "horizon" }
                Opt("Share plan (KMZ)") { shareKmz(ctx, LibraryState(plans = listOf(m.toPlan(LibraryRepository.newId(), "PhotoCapsules plan"))), "PhotoCapsules plan") }
                Opt("Add alert to calendar") { addCalendarAlert(ctx, "Photo plan", m.instant, m.red.latitude, m.red.longitude) }
                Opt("Open Red Pin in Maps") { openInMaps(ctx, m.red.latitude, m.red.longitude, "Red Pin") }
                Opt("Copy coordinates") {
                    val txt = Coordinates.format(m.red, PhotoCapsulesApp.instance.settings.current.coordinateFormat)
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("coordinates", txt))
                    Toast.makeText(ctx, "Copied $txt", Toast.LENGTH_SHORT).show()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    when (sub) {
        "date" -> DateTimeDialog(m) { sub = null }
        "alt" -> AltitudesDialog(m) { sub = null }
        "horizon" -> HorizonDialog(m) { sub = null }
    }
}

@Composable
fun DateTimeDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var zones by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date & time") },
        text = {
            Column {
                ValueRow("Date", Fmt.date(m.instant, m.zone), onClick = {
                    val z = m.instant.atZone(m.zone)
                    DatePickerDialog(ctx, { _, y, mo, d -> m.instant = z.withYear(y).withMonth(mo + 1).withDayOfMonth(d).toInstant() }, z.year, z.monthValue - 1, z.dayOfMonth).show()
                })
                ValueRow("Time", Fmt.time(m.instant, m.zone), onClick = {
                    val z = m.instant.atZone(m.zone)
                    TimePickerDialog(ctx, { _, h, mi -> m.instant = z.withHour(h).withMinute(mi).withSecond(0).toInstant() }, z.hour, z.minute, Fmt.use24h).show()
                })
                ValueRow("Time zone", m.zone.id, onClick = { zones = true })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-detect time zone (internet)", color = Palette.Text, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Switch(m.zoneAuto, { m.zoneAuto = it })
                }
                Opt("Now") { m.instant = Instant.now() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    if (zones) ZonePickerDialog(m.zone, { zones = false }) { m.zone = it; m.zoneAuto = false }
}

@Composable
fun AltitudesDialog(m: PlannerModel, onDismiss: () -> Unit) {
    var edit by remember { mutableStateOf<String?>(null) }
    val u = PhotoCapsulesApp.instance.settings.current.units
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Altitudes") },
        text = {
            Column {
                ValueRow("Red Pin terrain", (m.redCustomAltitude ?: m.redTerrain)?.let { Fmt.len(it, u, 1) + if (m.redCustomAltitude != null) " (custom)" else "" } ?: "unknown", onClick = { edit = "redAlt" })
                ValueRow("Red Pin offset", Fmt.len(m.redOffset, u, 1), onClick = { edit = "redOff" })
                if (m.black != null) {
                    ValueRow("Black Pin terrain", Fmt.len(m.blackGround, u, 1) + if (m.blackCustomAltitude != null) " (custom)" else "", onClick = { edit = "blackAlt" })
                    ValueRow("Black Pin offset", Fmt.len(m.blackOffset, u, 1), onClick = { edit = "blackOff" })
                }
                Opt("Use automatic terrain altitudes") { m.redCustomAltitude = null; m.blackCustomAltitude = null }
                Text("Offsets add the height of a building or tripod. Custom altitudes reset when a pin moves more than 30 m.", color = Palette.TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    when (edit) {
        // Ranges keep altitude + offset within the ephemeris engine's valid range (≥ −500 m).
        "redAlt" -> NumberDialog("Red Pin altitude", Fmt.toDisplay(m.redCustomAltitude ?: m.redTerrain ?: 0.0, u), Fmt.lenUnit(u), Fmt.toDisplay(-430.0, u), Fmt.toDisplay(9000.0, u), true, { edit = null }) { m.redCustomAltitude = Fmt.fromDisplay(it, u) }
        "redOff" -> NumberDialog("Red Pin offset", Fmt.toDisplay(m.redOffset, u), Fmt.lenUnit(u), Fmt.toDisplay(-50.0, u), Fmt.toDisplay(10000.0, u), true, { edit = null }) { m.redOffset = Fmt.fromDisplay(it, u) }
        "blackAlt" -> NumberDialog("Black Pin altitude", Fmt.toDisplay(m.blackGround, u), Fmt.lenUnit(u), Fmt.toDisplay(-430.0, u), Fmt.toDisplay(9000.0, u), true, { edit = null }) { m.blackCustomAltitude = Fmt.fromDisplay(it, u) }
        "blackOff" -> NumberDialog("Black Pin offset", Fmt.toDisplay(m.blackOffset, u), Fmt.lenUnit(u), Fmt.toDisplay(-50.0, u), Fmt.toDisplay(10000.0, u), true, { edit = null }) { m.blackOffset = Fmt.fromDisplay(it, u) }
    }
}

@Composable
fun HorizonDialog(m: PlannerModel, onDismiss: () -> Unit) {
    var edit by remember { mutableStateOf<String?>(null) }
    val u = PhotoCapsulesApp.instance.settings.current.units
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horizon") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Adjust for height above horizon", color = Palette.Text, modifier = Modifier.weight(1f))
                    Switch(m.horizonEnabled, { on ->
                        m.horizonEnabled = on
                        if (on && m.horizonPin == null) m.horizonPin = com.photocapsules.core.geo.Geodesy.direct(m.red, 270.0, 20_000.0)
                    })
                }
                if (m.horizonEnabled) {
                    ValueRow("Height above horizon", Fmt.len(m.horizonHeightM, u) + if (m.horizonCustomHeight != null) " (custom)" else "", onClick = { edit = "height" })
                    if (m.horizonCustomHeight == null) ValueRow("Horizon Pin altitude", m.horizonPinTerrain?.let { Fmt.len(it, u) } ?: "unknown (0 m used)", onClick = { edit = "pinAlt" })
                    ValueRow("Horizon dip", String.format(Locale.US, "%.2f°", com.photocapsules.core.geo.Terrain.apparentHorizonDip(m.horizonHeightM)))
                    if (m.horizonCustomHeight != null) Opt("Compute from the Horizon Pin") { m.horizonCustomHeight = null }
                }
                Text("Drag the blue Horizon Pin to where you see the horizon (several km away), or tap a value to enter it. Rise/set times and azimuths are corrected for the dip of the horizon.", color = Palette.TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    when (edit) {
        "height" -> NumberDialog("Height above horizon", Fmt.toDisplay(m.horizonHeightM, u), Fmt.lenUnit(u), 0.0, Fmt.toDisplay(9000.0, u), false, { edit = null }) { m.horizonCustomHeight = Fmt.fromDisplay(it, u) }
        "pinAlt" -> NumberDialog("Horizon Pin altitude", Fmt.toDisplay(m.horizonPinTerrain ?: 0.0, u), Fmt.lenUnit(u), Fmt.toDisplay(-430.0, u), Fmt.toDisplay(9000.0, u), true, { edit = null }) { m.horizonPinTerrain = Fmt.fromDisplay(it, u) }
    }
}

// ---------------- Map settings / layers / tools ----------------

@Composable
fun MapSettingsDialog(m: PlannerModel, onDismiss: () -> Unit, onEclipsePick: () -> Unit, onMeteorPick: () -> Unit) {
    val store = PhotoCapsulesApp.instance.settings
    var sub by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PillRow(listOf("Camera mode", "Drone mode"), m.mode.ordinal) { m.mode = PlannerMode.entries[it] }
                ValueRow("Map type", store.current.mapType.label, onClick = { sub = "type" })
                ValueRow("Map tool", m.tool.label, onClick = { sub = "tool" })
                Text("LAYERS", color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                ValueRow("Sun", m.sunStyle.label, onClick = { sub = "sun" })
                Check("Show Sun size", m.showSunSize) { m.showSunSize = it }
                ValueRow("Moon", m.moonStyle.label, onClick = { sub = "moon" })
                Check("Show Moon size", m.showMoonSize) { m.showMoonSize = it }
                Check("Show real (geodesic) azimuth lines", m.realAzimuthLines) { m.realAzimuthLines = it }
                Check("Milky Way", m.milkyWay) { m.milkyWay = it }
                Check("Meteor shower", m.meteorLayer) { if (it && m.meteorCode == null) onMeteorPick() else m.meteorLayer = it }
                Check("Eclipse", m.eclipseLayer) { if (it && m.lunarEclipse == null && m.solarEclipse == null) onEclipsePick() else m.eclipseLayer = it }
                Check("Twilights", m.twilightLayer) { m.twilightLayer = it }
                Check("Shadow", m.shadowLayer) { m.shadowLayer = it }
                Opt("Reset map layers") { m.resetLayers() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    when (sub) {
        "type" -> ChoiceDialog("Map type", MapType.entries, { it.label }, store.current.mapType, { sub = null }) { t -> store.update { it.copy(mapType = t) } }
        "tool" -> ChoiceDialog("Map tool", MapTool.entries, { it.label }, m.tool, { sub = null }) { t ->
            m.tool = t
            if (t != MapTool.NONE && m.black != null && m.toolAzimuth == 0.0) m.azimuthAtBlackPin = true
        }
        "sun" -> ChoiceDialog("Sun layer", BodyLayerStyle.entries, { it.label }, m.sunStyle, { sub = null }) { m.sunStyle = it }
        "moon" -> ChoiceDialog("Moon layer", BodyLayerStyle.entries, { it.label }, m.moonStyle, { sub = null }) { m.moonStyle = it }
    }
}

@Composable
private fun Check(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Switch(value, onChange)
    }
}

/** Settings box for the FoV / DoF / Sun-Moon tools and drone mode. */
@Composable
fun ToolSettingsDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val units = PhotoCapsulesApp.instance.settings.current.units
    var edit by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m.mode == PlannerMode.DRONE) "Drone" else m.tool.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val cam = m.toolCamera()
                if (m.mode == PlannerMode.DRONE) ValueRow("Drone / camera", cam.name, onClick = { edit = "dronecam" }, sub = "Tap to choose the drone")
                else ValueRow("Camera", cam.name, sub = "Change in Settings → Default camera")
                if (m.tool == MapTool.SUN_MOON && m.mode == PlannerMode.CAMERA) {
                    PillRow(listOf("Sun", "Moon"), m.sunMoonToolBody) { m.sunMoonToolBody = it }
                    ValueRow("Desired Sun/Moon size", Fmt.dist(m.sunMoonSizeM, units), onClick = { edit = "smsize" })
                    Text("The grey circle around the Black Pin (or Red Pin) shows the shooting distance that gives that size.", color = Palette.TextDim, fontSize = 12.sp)
                } else {
                    val fixed = cam.fixedFocalMm
                    if (fixed != null) ValueRow("Focal length (fixed lens)", String.format(Locale.US, "%.1f mm (35 mm eq. %.0f)", fixed, fixed * cam.cropFactor))
                    else {
                        ValueRow("Focal length", String.format(Locale.US, "%.0f mm (35 mm eq. %.0f)", m.focalMm, m.focalMm * cam.cropFactor * m.teleconverter), onClick = { edit = "focal" })
                        ValueRow("Focal length as 35 mm equivalent", String.format(Locale.US, "%.0f mm", m.focalMm * cam.cropFactor), onClick = { edit = "focal35" })
                    }
                    ValueRow("Teleconverter", if (m.teleconverter == 1.0) "None" else "×" + m.teleconverter, onClick = { edit = "tc" })
                    Check("Lock focal length (map handles only rotate)", m.focalLocked) { m.focalLocked = it }
                    if (m.tool == MapTool.DOF) ValueRow("Aperture", com.photocapsules.core.photo.Format.aperture(m.aperture) + if (m.teleconverter != 1.0) " (effective " + com.photocapsules.core.photo.Format.aperture(m.aperture * m.teleconverter) + ")" else "", onClick = { edit = "aperture" })
                    if (m.mode == PlannerMode.CAMERA) {
                        ValueRow("Focus distance", if (m.focusAtBlackPin) "At Black Pin" else Fmt.dist(m.focusM, units), onClick = { edit = "focus" })
                        if (m.black != null) Check("Focus at Black Pin", m.focusAtBlackPin) { m.focusAtBlackPin = it }
                    } else {
                        ValueRow("Drone altitude", Fmt.dist(m.droneAltitudeM, units), onClick = { edit = "dalt" })
                        val fp = com.photocapsules.core.photo.DroneFrame.footprint(cam, m.toolFocalMm(), m.droneAltitudeM, m.orientation)
                        ValueRow("Ground area", Fmt.len(fp.widthM, units) + " × " + Fmt.len(fp.heightM, units))
                        ValueRow("Composition grid", m.droneGrid.label, onClick = { edit = "grid" })
                    }
                    PillRow(listOf("Landscape", "Portrait"), m.orientation.ordinal) { m.orientation = Orientation.entries[it] }
                    ValueRow("Azimuth", if (m.azimuthAtBlackPin) "Aligned with Black Pin" else Fmt.az(m.toolAzimuth), onClick = { edit = "az" })
                    if (m.black != null) Check("Align with Black Pin", m.azimuthAtBlackPin) { m.azimuthAtBlackPin = it }
                    Text("Tip: drag the black handles on the map to change direction (centre) and focal length (edge).", color = Palette.TextDim, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    when (edit) {
        "focal" -> NumberDialog("Focal length (real)", m.focalMm, "mm", 1.0, 5000.0, false, { edit = null }) { m.focalMm = it }
        "dronecam" -> com.photocapsules.ui.components.CameraPickerDialog(m.toolCamera(), { edit = null }) { c -> m.droneCameraName = c.name }
        "focal35" -> {
            val crop = m.toolCamera().cropFactor
            NumberDialog("35 mm equivalent focal length", m.focalMm * crop, "mm", 1.0, 20000.0, false, { edit = null }) { m.focalMm = it / crop }
        }
        "tc" -> ChoiceDialog("Teleconverter", listOf(1.0, 1.4, 1.7, 2.0), { if (it == 1.0) "None" else "×$it" }, m.teleconverter, { edit = null }) { m.teleconverter = it }
        "aperture" -> NumberDialog("Aperture", m.aperture, "f/", 0.5, 128.0, false, { edit = null }) { m.aperture = it }
        "focus" -> NumberDialog("Focus distance", Fmt.toDisplay(m.focusM, units), Fmt.lenUnit(units), 0.05, 1e6, false, { edit = null }) { m.focusM = Fmt.fromDisplay(it, units); m.focusAtBlackPin = false }
        "az" -> NumberDialog(if (Fmt.magneticAzimuths) "Azimuth (magnetic)" else "Azimuth (true north)", if (Fmt.magneticAzimuths) com.photocapsules.core.astro.normalizeDegrees(m.toolAzimuth - Fmt.declination) else m.toolAzimuth, "°", 0.0, 360.0, false, { edit = null }) {
            m.toolAzimuth = if (Fmt.magneticAzimuths) com.photocapsules.core.astro.normalizeDegrees(it + Fmt.declination) else it; m.azimuthAtBlackPin = false
        }
        "smsize" -> NumberDialog("Sun/Moon diameter", Fmt.toDisplay(m.sunMoonSizeM, units), Fmt.lenUnit(units), 0.01, 1e6, false, { edit = null }) { m.sunMoonSizeM = Fmt.fromDisplay(it, units) }
        "dalt" -> NumberDialog("Drone altitude", Fmt.toDisplay(m.droneAltitudeM, units), Fmt.lenUnit(units), 1.0, 10000.0, false, { edit = null }) { m.droneAltitudeM = Fmt.fromDisplay(it, units) }
        "grid" -> ChoiceDialog("Composition grid", CompositionGrid.entries, { it.label }, m.droneGrid, { edit = null }) { m.droneGrid = it }
    }
}

// ---------------- Eclipse & meteor pickers ----------------

@Composable
fun EclipsePickerDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val all by androidx.compose.runtime.produceState<List<Pair<Instant, Any>>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            val from = Instant.now().minusSeconds(365L * 86400)
            (Eclipses.lunarEclipses(from, 16).map { it.peak to it as Any } + Eclipses.solarEclipses(from, 16).map { it.peak to it as Any }).sortedBy { it.first }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eclipses") },
        text = {
            if (all.isEmpty()) Text("Loading eclipses…", color = Palette.TextDim)
            LazyColumn(Modifier.height(420.dp)) {
                items(all) { (t, e) ->
                    val label = when (e) { is LunarEclipse -> e.name + String.format(Locale.US, " (umbral %.2f)", e.umbralMagnitude); is SolarEclipse -> e.name; else -> "" }
                    Column(Modifier.fillMaxWidth().clickable {
                        m.solarMap = null
                        when (e) {
                            is LunarEclipse -> { m.lunarEclipse = e; m.solarEclipse = null }
                            is SolarEclipse -> {
                                m.solarEclipse = e; m.lunarEclipse = null
                                Toast.makeText(ctx, "Computing eclipse path…", Toast.LENGTH_SHORT).show()
                                // App-lifetime scope: the dialog is dismissed right away, its own scope would cancel the work.
                                PhotoCapsulesApp.instance.appScope.launch {
                                    val data = withContext(Dispatchers.Default) { SolarEclipseMap.forEclipse(e).compute(e) }
                                    if (m.solarEclipse == e) m.solarMap = data
                                }
                            }
                        }
                        m.eclipseLayer = true; m.instant = t; onDismiss()
                    }.padding(vertical = 8.dp)) {
                        Text(label, color = Palette.Text)
                        Text(Fmt.dateTime(t, ZoneId.of("UTC")) + " UT", color = Palette.TextDim, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { m.lunarEclipse = null; m.solarEclipse = null; m.solarMap = null; m.eclipseLayer = false; onDismiss() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun MeteorPickerDialog(m: PlannerModel, onDismiss: () -> Unit) {
    val year = m.instant.atZone(m.zone).year
    val list = remember(year) { (MeteorShowers.occurrences(year) + MeteorShowers.occurrences(year + 1)).filter { it.peak.isAfter(m.instant.minusSeconds(40L * 86400)) }.take(40) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meteor showers") },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(list) { o ->
                    Row(Modifier.fillMaxWidth().clickable { m.meteorCode = o.shower.code; m.meteorLayer = true; if (o.shower.hasPeak) m.instant = o.peak; onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.shower.name, color = Palette.Text)
                            Text((if (o.shower.hasPeak) "Peak " + Fmt.dateTime(o.peak, m.zone) else "No distinct maximum") + " · ZHR " + o.shower.zhr.toInt(), color = Palette.TextDim, fontSize = 12.sp)
                        }
                        MoonDisc(MoonCalc.info(o.peak, m.observer).phaseAngle, 24.dp, latitude = m.observer.latitude)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { m.meteorCode = null; m.meteorLayer = false; onDismiss() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val SHOWN_RESULTS = 500
