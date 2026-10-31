package com.photocapsules.feature.mystuff

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.data.Kml
import com.photocapsules.core.data.LibraryRepository
import com.photocapsules.core.data.LibraryState
import com.photocapsules.core.data.Plan
import com.photocapsules.core.data.PointOfInterest
import com.photocapsules.core.geo.CoordinateFormat
import com.photocapsules.core.geo.Coordinates
import com.photocapsules.core.geo.LatLon
import com.photocapsules.core.photo.StopScale
import com.photocapsules.core.photo.UnitSystem
import com.photocapsules.data.AppState
import com.photocapsules.data.AzimuthReference
import com.photocapsules.data.MapType
import com.photocapsules.data.NearbyPlace
import com.photocapsules.data.OnlineServices
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.CameraPickerDialog
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.ChoiceDialog
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.NumberDialog
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.TextInputDialog
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

private val repo: LibraryRepository get() = PhotoCapsulesApp.instance.library

/** Shares a KMZ export through the system share sheet. */
fun shareKmz(ctx: android.content.Context, state: LibraryState, fileName: String) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".kmz")
    f.writeBytes(Kml.toKmz(state, fileName))
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).setType("application/vnd.google-earth.kmz").putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, fileName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share $fileName"))
}

fun openInMaps(ctx: android.content.Context, lat: Double, lon: Double, label: String) {
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { Toast.makeText(ctx, "No maps app installed", Toast.LENGTH_SHORT).show() }
}

fun addCalendarAlert(ctx: android.content.Context, title: String, start: Instant, lat: Double, lon: Double) {
    val i = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start.toEpochMilli() + 3600_000)
        .putExtra(android.provider.CalendarContract.Events.TITLE, title)
        .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, "$lat,$lon")
    runCatching { ctx.startActivity(i) }.onFailure { Toast.makeText(ctx, "No calendar app available", Toast.LENGTH_SHORT).show() }
}

@Composable
fun PlansScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(repo.load()) }
    var detail by remember { mutableStateOf<Plan?>(null) }
    var confirmDelete by remember { mutableStateOf<Plan?>(null) }
    val fmt = PhotoCapsulesApp.instance.settings.current.coordinateFormat
    AppScreen("Plans", onBack, actions = {
        TextButton(onClick = { shareKmz(ctx, LibraryState(plans = state.plans), "PhotoCapsules plans") }) { Text("Backup") }
    }) { p ->
        ScrollColumn(p) {
            if (state.plans.isEmpty()) InfoText("No plans yet. Use Planner → Save → Plan to add one.")
            val now = Instant.now()
            val sorted = state.plans.sortedWith(compareBy({ it.epochMillis < now.toEpochMilli() }, { kotlin.math.abs(it.epochMillis - now.toEpochMilli()) }))
            Card {
                sorted.forEach { plan ->
                    val zone = runCatching { ZoneId.of(plan.zoneId) }.getOrDefault(ZoneId.systemDefault())
                    val t = Instant.ofEpochMilli(plan.epochMillis)
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { detail = plan }, onLongClick = { confirmDelete = plan }).padding(14.dp)) {
                        Text(plan.name, color = if (t.isBefore(now)) Palette.TextDim else Palette.Text, fontSize = 16.sp)
                        Text(Fmt.dateTime(t, zone) + " · " + Coordinates.format(LatLon(plan.redPin.latitude, plan.redPin.longitude), fmt), color = Palette.TextDim, fontSize = 12.sp)
                    }
                }
            }
            InfoText("Tap a plan for details; long-press to delete.")
        }
    }
    detail?.let { plan ->
        PlanDetailDialog(plan, onDismiss = { detail = null; state = repo.load() }, onOpen = {
            AppState.pendingPlanId.value = plan.id; detail = null; go(Routes.PLANNER)
        })
    }
    confirmDelete?.let { plan ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete plan?") }, text = { Text(plan.name) },
            confirmButton = { TextButton(onClick = { repo.deletePlan(plan.id); state = repo.load(); confirmDelete = null }) { Text("Delete", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
    }
}

@Composable
private fun PlanDetailDialog(plan: Plan, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(plan) }
    var rename by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            // Keep access to the chosen photos across restarts.
            uris.forEach { u -> runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            current = current.copy(photoUris = (current.photoUris + uris.map { it.toString() }).distinct(), updatedMillis = System.currentTimeMillis())
            repo.upsertPlan(current)
        }
    }
    val zone = runCatching { ZoneId.of(current.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val t = Instant.ofEpochMilli(current.epochMillis)
    val where = GeoPoint(current.redPin.latitude, current.redPin.longitude, (current.redPin.altitudeM ?: 0.0) + current.redPin.offsetM)
    val sun = Sky.position(SkyBody.SUN, t, where)
    val moon = MoonCalc.info(t, where)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current.name) },
        text = {
            Column {
                ValueRow("Date", Fmt.dateTime(t, zone))
                ValueRow("Sun", "az ${Fmt.az(sun.azimuth)} · el ${Fmt.deg(sun.altitude)}")
                ValueRow("Moon", "az ${Fmt.az(moon.position.azimuth)} · el ${Fmt.deg(moon.position.altitude)} · ${Fmt.pct(moon.illumination)}")
                if (current.notes.isNotBlank()) Text(current.notes, color = Palette.TextDim, modifier = Modifier.padding(8.dp))
                Row {
                    TextButton(onClick = { rename = true }) { Text("Rename") }
                    TextButton(onClick = { notes = true }) { Text("Notes") }
                    TextButton(onClick = { shareKmz(ctx, LibraryState(plans = listOf(current)), current.name) }) { Text("Share") }
                }
                Row {
                    TextButton(onClick = { addCalendarAlert(ctx, current.name, t, where.latitude, where.longitude) }) { Text("Calendar") }
                    TextButton(onClick = { openInMaps(ctx, where.latitude, where.longitude, current.name) }) { Text("Maps") }
                    TextButton(onClick = { photos = true }) { Text("Photos (${current.photoUris.size})") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open in Planner") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (photos) AlertDialog(
        onDismissRequest = { photos = false }, title = { Text("Photos") },
        text = {
            Column {
                if (current.photoUris.isEmpty()) Text("No photos attached yet.", color = Palette.TextDim)
                current.photoUris.forEachIndexed { i, u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Photo ${i + 1}", color = Palette.Accent, modifier = Modifier.weight(1f).clickable {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                .onFailure { Toast.makeText(ctx, "Cannot open this photo", Toast.LENGTH_SHORT).show() }
                        }.padding(vertical = 8.dp))
                        TextButton(onClick = { current = current.copy(photoUris = current.photoUris - u); repo.upsertPlan(current) }) { Text("Remove") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text("Add photos") } },
        dismissButton = { TextButton(onClick = { photos = false }) { Text("Close") } },
    )
    if (rename) TextInputDialog("Plan name", current.name, { rename = false }) { n -> current = current.copy(name = n, updatedMillis = System.currentTimeMillis()); repo.upsertPlan(current) }
    if (notes) TextInputDialog("Notes", current.notes, { notes = false }, multiline = true) { n -> current = current.copy(notes = n, updatedMillis = System.currentTimeMillis()); repo.upsertPlan(current) }
}

@Composable
fun PoisScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(repo.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var nearby by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<PointOfInterest?>(null) }
    var rename by remember { mutableStateOf<PointOfInterest?>(null) }
    val sky by AppState.sky.collectAsState()
    val units = PhotoCapsulesApp.instance.settings.current.units
    AppScreen("Points of Interest", onBack, actions = {
        TextButton(onClick = { shareKmz(ctx, LibraryState(pois = state.pois), "PhotoCapsules POIs") }) { Text("Backup") }
    }) { p ->
        ScrollColumn(p) {
            com.photocapsules.ui.components.Tabs(listOf("My list", "Nearby (Wikipedia)"), tab) { tab = it }
            if (tab == 0) {
                if (state.pois.isEmpty()) InfoText("No points of interest. Use Planner → Save → Point of Interest.")
                Card {
                    state.pois.sortedBy { it.name.lowercase() }.forEach { poi ->
                        Column(Modifier.fillMaxWidth().combinedClickable(onClick = {
                            AppState.pendingPlannerLocation.value = GeoPoint(poi.latitude, poi.longitude, poi.altitudeM ?: 0.0); go(Routes.PLANNER)
                        }, onLongClick = { confirmDelete = poi }).padding(14.dp)) {
                            Text(poi.name, color = Palette.Text, fontSize = 16.sp)
                            Text(Coordinates.format(LatLon(poi.latitude, poi.longitude), PhotoCapsulesApp.instance.settings.current.coordinateFormat), color = Palette.TextDim, fontSize = 12.sp)
                        }
                    }
                }
                InfoText("Tap to open in the Planner; long-press for options.")
            } else {
                TextButton(onClick = {
                    status = "Searching around ${sky.label}…"
                    scope.launch {
                        nearby = OnlineServices.nearbyPlaces(sky.point.latitude, sky.point.longitude)
                        status = if (nearby.isEmpty()) "Nothing found (internet required)" else "${nearby.size} places within 10 km of ${sky.label}"
                    }
                }) { Text("Search near ${sky.label}") }
                if (status.isNotEmpty()) InfoText(status)
                Card {
                    nearby.forEach { pl ->
                        Row(Modifier.fillMaxWidth().clickable {
                            AppState.pendingPlannerLocation.value = GeoPoint(pl.latitude, pl.longitude); go(Routes.PLANNER)
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pl.title, color = Palette.Text)
                                Text(Fmt.dist(pl.distanceM, units), color = Palette.TextDim, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                repo.upsertPoi(PointOfInterest(LibraryRepository.newId(), pl.title, pl.latitude, pl.longitude, notes = "https://en.wikipedia.org/?curid=${pl.pageId}"))
                                state = repo.load(); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                            }) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { poi ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text(poi.name) }, text = { Text(poi.notes.ifBlank { "Point of interest" }) },
            confirmButton = { TextButton(onClick = { repo.deletePoi(poi.id); state = repo.load(); confirmDelete = null }) { Text("Delete", color = Palette.Red) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { rename = poi; confirmDelete = null }) { Text("Rename") }
                    TextButton(onClick = { openInMaps(ctx, poi.latitude, poi.longitude, poi.name) }) { Text("Maps") }
                    TextButton(onClick = { shareKmz(ctx, LibraryState(pois = listOf(poi)), poi.name) }) { Text("Share") }
                }
            })
    }
    rename?.let { poi -> TextInputDialog("Name", poi.name, { rename = null }) { n -> repo.upsertPoi(poi.copy(name = n)); state = repo.load() } }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(repo.load()) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { repo.merge(Kml.parse(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() })) } }
            Toast.makeText(ctx, r.fold({ "Imported ${it.first} plans, ${it.second} POIs" }, { "Import failed: ${it.message}" }), Toast.LENGTH_LONG).show()
            state = repo.load()
        }
    }
    AppScreen("Backup", onBack) { p ->
        ScrollColumn(p) {
            Card {
                ValueRow("Plans", state.plans.size.toString())
                ValueRow("Points of interest", state.pois.size.toString())
            }
            Card {
                ValueRow("Create backup (KMZ)", "Share…", onClick = { shareKmz(ctx, state, "PhotoCapsules backup") })
                ValueRow("Import KMZ / KML", "Choose file…", onClick = { importer.launch(arrayOf("*/*")) })
            }
            InfoText("Backups use the Google Earth KMZ format. Plans keep their date, time zone, pins and notes; placemarks from other apps are imported as points of interest. Duplicates are skipped.")
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = PhotoCapsulesApp.instance.settings
    val s by store.state.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    AppScreen("Settings", onBack) { p ->
        ScrollColumn(p) {
            Card {
                ValueRow("Units", if (s.units == UnitSystem.METRIC) "Metric" else "Imperial", onClick = { dialog = "units" })
                ValueRow("Aperture / shutter / ISO stops", when (s.stopScale) { StopScale.FULL -> "Full"; StopScale.HALF -> "Half"; StopScale.THIRD -> "Third" }, onClick = { dialog = "stops" })
                ValueRow("Default camera", s.cameraName, onClick = { dialog = "camera" })
                ValueRow("Map type", s.mapType.label, onClick = { dialog = "map" })
                ValueRow("Coordinate format", s.coordinateFormat.name, onClick = { dialog = "coord" })
                ValueRow("Azimuth reference", s.azimuthReference.label, onClick = { dialog = "az" })
                ValueRow("Time format", if (s.use24h) "24 h" else "12 h", onClick = { store.update { it.copy(use24h = !it.use24h) } })
                ValueRow("Supermoon definition", if (s.supermoonKm > 0) String.format(java.util.Locale.US, "Full Moon < %,.0f km", s.supermoonKm) else "Within 90 % of perigee", onClick = { dialog = "super" })
            }
            SectionTitle("Augmented reality")
            Card {
                ValueRow("Device usage height", Fmt.dist(s.deviceHeightM, s.units), onClick = { dialog = "height" })
                SwitchRow("Use gyroscope", s.useGyroscope) { v -> store.update { it.copy(useGyroscope = v) } }
                SwitchRow("Compass accuracy warning", s.showCompassWarning) { v -> store.update { it.copy(showCompassWarning = v) } }
            }
            SectionTitle("Map & data")
            Card {
                SwitchRow("Show my position on the map", s.showMyLocation) { v -> store.update { it.copy(showMyLocation = v) } }
                ValueRow("Elevation service", s.elevationProvider.label, onClick = { dialog = "elev" })
            }
            SectionTitle("About")
            Card { ValueRow("Version", com.photocapsules.BuildConfig.VERSION_NAME) }
        }
    }
    when (dialog) {
        "units" -> ChoiceDialog("Units", UnitSystem.entries, { if (it == UnitSystem.METRIC) "Metric" else "Imperial" }, s.units, { dialog = null }) { v -> store.update { it.copy(units = v) } }
        "stops" -> ChoiceDialog("Stops", StopScale.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, s.stopScale, { dialog = null }) { v -> store.update { it.copy(stopScale = v) } }
        "map" -> ChoiceDialog("Map type", MapType.entries, { it.label }, s.mapType, { dialog = null }) { v -> store.update { it.copy(mapType = v) } }
        "coord" -> ChoiceDialog("Coordinate format", CoordinateFormat.entries, { when (it) { CoordinateFormat.DDD -> "Decimal degrees (DDD)"; CoordinateFormat.DMM -> "Degrees decimal minutes (DMM)"; CoordinateFormat.DMS -> "Degrees minutes seconds (DMS)" } }, s.coordinateFormat, { dialog = null }) { v -> store.update { it.copy(coordinateFormat = v) } }
        "az" -> ChoiceDialog("Azimuth reference", AzimuthReference.entries, { it.label }, s.azimuthReference, { dialog = null }) { v -> store.update { it.copy(azimuthReference = v) } }
        "height" -> NumberDialog("Device height", Fmt.toDisplay(s.deviceHeightM, s.units), Fmt.lenUnit(s.units), 0.1, 10000.0, false, { dialog = null }) { v -> store.update { it.copy(deviceHeightM = Fmt.fromDisplay(v, s.units)) } }
        "elev" -> ChoiceDialog("Elevation service", com.photocapsules.data.ElevationProvider.entries, { it.label }, s.elevationProvider, { dialog = null }) { v -> store.update { it.copy(elevationProvider = v) } }
        "super" -> ChoiceDialog("Supermoon definition", listOf(0.0, 360_000.0, 361_524.0, 362_000.0), {
            if (it == 0.0) "Within 90 % of perigee (Nolle, timeanddate)" else String.format(java.util.Locale.US, "Full Moon closer than %,.0f km", it)
        }, s.supermoonKm, { dialog = null }) { v -> store.update { it.copy(supermoonKm = v) } }
        "camera" -> CameraPickerDialog(store.camera(), { dialog = null }) { c -> store.update { it.copy(cameraName = c.name) } }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
