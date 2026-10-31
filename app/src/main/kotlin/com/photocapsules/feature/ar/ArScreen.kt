package com.photocapsules.feature.ar

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.astro.ArProjection
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.HorizontalCoord
import com.photocapsules.core.astro.MeteorShowers
import com.photocapsules.core.astro.MilkyWay
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.NightSky
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.astro.normalizeDegrees
import com.photocapsules.core.photo.SpotStars
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.data.LocationService
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.drawMoon
import com.photocapsules.ui.theme.Palette
import java.time.Instant
import java.util.Locale
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class ArMode(val title: String) { SUN_MOON("Sun & Moon AR"), NIGHT("Night AR"), DOF("DoF AR"), FOV("FoV AR"), SPOT_STARS("Spot Stars AR"), FIND_TARGET("Point at the target") }

/** Pixel projector for one frame: true horizontal coords → screen pixels. */
private class Projector(val r: FloatArray, val w: Float, val h: Float, val fPx: Double, val declination: Double, val azOffset: Double, val altOffset: Double) {
    /** World ENU (magnetic frame) direction for a true azimuth/altitude, with calibration offsets. */
    fun world(az: Double, alt: Double): DoubleArray = ArProjection.enu(az - declination - azOffset, alt - altOffset)
    fun projectWorld(v: DoubleArray): Offset? {
        val dx = r[0] * v[0] + r[3] * v[1] + r[6] * v[2]
        val dy = r[1] * v[0] + r[4] * v[1] + r[7] * v[2]
        val dz = r[2] * v[0] + r[5] * v[1] + r[8] * v[2]
        if (dz >= -0.02) return null
        return Offset((w / 2 + fPx * dx / -dz).toFloat(), (h / 2 - fPx * dy / -dz).toFloat())
    }
    fun project(az: Double, alt: Double): Offset? = projectWorld(world(az, alt))
    /** True direction at the screen centre. */
    fun center(): HorizontalCoord {
        val c = ArProjection.cameraDirection(r)
        return HorizontalCoord(normalizeDegrees(c.azimuth + declination + azOffset), c.altitude + altOffset)
    }
    /** True direction through a screen pixel. */
    fun unproject(x: Float, y: Float): HorizontalCoord {
        val d = doubleArrayOf((x - w / 2) / fPx, -(y - h / 2) / fPx, -1.0)
        val n = sqrt(d[0] * d[0] + d[1] * d[1] + 1)
        val ex = (r[0] * d[0] + r[1] * d[1] + r[2] * d[2]) / n
        val ny = (r[3] * d[0] + r[4] * d[1] + r[5] * d[2]) / n
        val uz = (r[6] * d[0] + r[7] * d[1] + r[8] * d[2]) / n
        val az = Math.toDegrees(kotlin.math.atan2(ex, ny))
        val alt = Math.toDegrees(kotlin.math.asin(uz.coerceIn(-1.0, 1.0)))
        return HorizontalCoord(normalizeDegrees(az + declination + azOffset), alt + altOffset)
    }
}

@Composable
fun ArScreen(mode: ArMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = PhotoCapsulesApp.instance.settings.current
    val request = remember { (AppState.ar ?: ArRequest(AppState.sky.value)).also { AppState.ar = null } }
    val where: GeoPoint = request.context.point
    var instant by remember { mutableStateOf(if (request.context.live) Instant.now() else request.context.instant) }
    val zone = request.context.zone
    var tick by remember { mutableIntStateOf(0) }
    val tracker = remember { OrientationTracker(context, settings.useGyroscope) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val vFov = remember { backCameraVerticalFov(context) }
    val declination = remember { LocationService.magneticDeclination(where.latitude, where.longitude, where.elevationM, System.currentTimeMillis()) }
    val store = PhotoCapsulesApp.instance.settings
    var azOffset by remember { mutableStateOf(store.getDouble("ar_azoff", 0.0)) }
    var altOffset by remember { mutableStateOf(store.getDouble("ar_altoff", 0.0)) }
    var calibrating by remember { mutableStateOf(false) }
    var lockedFrame by remember { mutableStateOf<List<DoubleArray>?>(null) }
    val projHolder = remember { arrayOfNulls<Projector>(1) }
    val needsSky = mode == ArMode.SUN_MOON || mode == ArMode.NIGHT || mode == ArMode.SPOT_STARS || mode == ArMode.FIND_TARGET
    // Sky data is recomputed in the background when the displayed minute changes; frames only project it.
    val arSky by androidx.compose.runtime.produceState<ArSky?>(null, instant.epochSecond / 60, needsSky) {
        if (needsSky) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            computeArSky(where, instant, zone, mode == ArMode.NIGHT || mode == ArMode.SPOT_STARS, request.meteorShowerCode)
        }
    }

    DisposableEffect(Unit) {
        val act = context as? Activity
        val prev = act?.requestedOrientation
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        tracker.onUpdate = { tick++ }
        tracker.start()
        if (!hasCamera) camPerm.launch(Manifest.permission.CAMERA)
        onDispose {
            tracker.stop(); tracker.onUpdate = null
            if (prev != null) act.requestedOrientation = prev
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) CameraPreview()
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { instant = Instant.now() },
                        onTap = { o ->
                            when {
                                mode == ArMode.FOV -> {
                                    // Lock/unlock the frame in the world.
                                    val p = projHolder[0]
                                    lockedFrame = if (lockedFrame != null || p == null) null else frameCorners(p, request)
                                }
                                o.x < size.width * 0.18f -> instant = instant.minusSeconds(86400)
                                o.x > size.width * 0.82f -> instant = instant.plusSeconds(86400)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, dx ->
                        acc += dx
                        if (kotlin.math.abs(acc) > 8f) { instant = instant.plusSeconds((-acc / 8f * 120).toLong()); acc = 0f }
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            val w = size.width; val h = size.height
            val tanV = tan(Math.toRadians(vFov) / 2)
            val fPx = maxOf((h / 2) / tanV, (w / 2) / (tanV * 0.75))
            val proj = Projector(tracker.rotation.copyOf(), w, h, fPx, declination, azOffset, altOffset)
            projHolder[0] = proj
            drawGrid(proj)
            val sky = arSky
            when (mode) {
                ArMode.SUN_MOON, ArMode.FIND_TARGET -> sky?.let { drawSunMoon(proj, it, zone) }
                ArMode.NIGHT -> sky?.let { drawNight(proj, it, zone) }
                ArMode.DOF -> drawGround(proj, request, settings.deviceHeightM, settings.units)
                ArMode.FOV -> drawFrame(proj, request, lockedFrame)
                ArMode.SPOT_STARS -> drawSpotStars(proj, sky, where, instant, request)
            }
            // Centre crosshair
            drawLine(Color.White, Offset(w / 2 - 20, h / 2), Offset(w / 2 + 20, h / 2), 2f)
            drawLine(Color.White, Offset(w / 2, h / 2 - 20), Offset(w / 2, h / 2 + 20), 2f)
        }
        // Top bar
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(mode.title, color = Color.White, fontSize = 15.sp)
                    Text(Fmt.date(instant, zone) + " " + Fmt.time(instant, zone), color = Palette.Accent, fontSize = 13.sp)
                }
                TextButton(onClick = { instant = Instant.now() }) { Text("Now", color = Color.White) }
            }
            @Suppress("UNUSED_EXPRESSION") tick
            projHolder[0]?.let { p ->
                val c = p.center()
                Text(String.format(Locale.US, "Az %.1f° · El %.1f° · decl. %+.1f°", c.azimuth, c.altitude, declination), color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (!tracker.available) Text("This device has no compass: AR is not available.", color = Palette.Red)
            if (settings.showCompassWarning && tracker.compassAccuracy < android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
                Text("Compass needs calibration: move the phone in a figure-8.", color = Palette.Accent, fontSize = 12.sp)
            if (!hasCamera) Text("Camera permission denied – showing overlay only.", color = Palette.TextDim, fontSize = 12.sp)
        }
        // Bottom bar
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(Color(0x99000000)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (!calibrating) {
                TextButton(onClick = { calibrating = true }) { Text("Calibrate", color = Color.White) }
                if (mode == ArMode.SPOT_STARS) Button(onClick = {
                    projHolder[0]?.let { p -> AppState.measuredDeclination.value = minAbsDeclination(p, where, instant) }
                    onBack()
                }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black)) { Text("Use declination") }
                if (mode == ArMode.FIND_TARGET) Button(onClick = {
                    projHolder[0]?.let { p -> AppState.findTarget.value = p.center() }
                    onBack()
                }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black)) { Text("Use this direction") }
                if (mode == ArMode.FOV) Text(if (lockedFrame == null) "Tap to lock frame" else "Tap to unlock", color = Color.White, fontSize = 12.sp)
                else if (mode != ArMode.DOF) Text("Swipe: time · tap edges: ±1 day", color = Color.White, fontSize = 11.sp)
            } else {
                Text("Point the crosshair at the real", color = Color.White, fontSize = 11.sp)
                TextButton(onClick = {
                    projHolder[0]?.let { p ->
                        val raw = p.center().let { HorizontalCoord(normalizeDegrees(it.azimuth - azOffset), it.altitude - altOffset) }
                        val target = Sky.position(SkyBody.SUN, instant, where)
                        azOffset = com.photocapsules.core.astro.angleDiff(target.azimuth, raw.azimuth); altOffset = target.altitude - raw.altitude
                        store.putDouble("ar_azoff", azOffset); store.putDouble("ar_altoff", altOffset)
                    }
                    calibrating = false
                }) { Text("Sun", color = Palette.Sun) }
                TextButton(onClick = {
                    projHolder[0]?.let { p ->
                        val raw = p.center().let { HorizontalCoord(normalizeDegrees(it.azimuth - azOffset), it.altitude - altOffset) }
                        val target = Sky.position(SkyBody.MOON, instant, where)
                        azOffset = com.photocapsules.core.astro.angleDiff(target.azimuth, raw.azimuth); altOffset = target.altitude - raw.altitude
                        store.putDouble("ar_azoff", azOffset); store.putDouble("ar_altoff", altOffset)
                    }
                    calibrating = false
                }) { Text("Moon", color = Palette.Moon) }
                TextButton(onClick = { azOffset = 0.0; altOffset = 0.0; store.putDouble("ar_azoff", 0.0); store.putDouble("ar_altoff", 0.0); calibrating = false }) { Text("Reset", color = Color.White) }
            }
        }
    }
}

@Composable
private fun CameraPreview() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        view
    }, modifier = Modifier.fillMaxSize())
}

// ---------------- drawing helpers ----------------

private fun DrawScope.text(s: String, at: Offset, color: Color = Color.White, sizePx: Float = 30f) {
    val paint = android.graphics.Paint().apply { this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()); textSize = sizePx; isAntiAlias = true; setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK) }
    drawContext.canvas.nativeCanvas.drawText(s, at.x, at.y, paint)
}

private fun DrawScope.polyline(p: Projector, pts: List<HorizontalCoord>, color: Color, width: Float = 3f, dashed: Boolean = false) {
    var prev: Offset? = null
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
    for (c in pts) {
        val o = p.project(c.azimuth, c.altitude)
        if (o != null && prev != null && (o - prev).getDistance() < size.width) drawLine(color, prev, o, width, pathEffect = effect)
        prev = o
    }
}

private fun DrawScope.drawGrid(p: Projector) {
    // Horizon
    polyline(p, (0..360 step 2).map { HorizontalCoord(it.toDouble(), 0.0) }, Color(0xFF59D98E), 4f)
    // Altitude circles every 10°
    for (alt in listOf(-20, -10, 10, 20, 30, 40, 50, 60, 70, 80)) polyline(p, (0..360 step 3).map { HorizontalCoord(it.toDouble(), alt.toDouble()) }, Color.White.copy(alpha = 0.25f), 1.5f)
    // Azimuth lines every 15°
    for (az in 0 until 360 step 15) {
        polyline(p, (0..90 step 5).map { HorizontalCoord(az.toDouble(), it.toDouble()) }, Color.White.copy(alpha = if (az % 90 == 0) 0.5f else 0.2f), 1.5f)
        p.project(az.toDouble(), 1.0)?.let { o ->
            val label = when (az) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$az°" }
            text(label, o + Offset(4f, -6f), if (az % 90 == 0) Palette.Accent else Color.White, if (az % 90 == 0) 38f else 26f)
        }
    }
    for (alt in listOf(10, 20, 30, 40, 50, 60, 70, 80)) {
        val c = p.center()
        p.project(c.azimuth, alt.toDouble())?.let { text("$alt°", it + Offset(6f, 0f), Color.White.copy(alpha = 0.7f), 22f) }
    }
}

/** Everything the AR overlays need for one minute of time, computed off the main thread. */
class ArSky(
    val sunPath: List<Pair<Instant, com.photocapsules.core.astro.BodyPosition>>,
    val moonPath: List<Pair<Instant, com.photocapsules.core.astro.BodyPosition>>,
    val sunRiseSet: List<Double>, val moonRiseSet: List<Double>,
    val sun: com.photocapsules.core.astro.BodyPosition,
    val moon: com.photocapsules.core.astro.MoonInfo,
    val decCircles: List<Pair<Int, List<HorizontalCoord>>>, val hourCircles: List<List<HorizontalCoord>>,
    val equator: List<HorizontalCoord>, val arch: List<HorizontalCoord>, val gc: HorizontalCoord,
    val ncp: HorizontalCoord, val scp: HorizontalCoord, val polaris: HorizontalCoord,
    val trails: List<List<HorizontalCoord>>,
    val radiants: List<Triple<String, Boolean, HorizontalCoord>>, val radiantPath: List<HorizontalCoord>,
)

fun computeArSky(where: GeoPoint, instant: Instant, zone: java.time.ZoneId, night: Boolean, showerCode: String?): ArSky {
    val date = instant.atZone(zone).toLocalDate()
    val (s, e) = DayEvents.dayWindow(date, zone)
    val sd = DayEvents.sun(date, zone, where); val md = DayEvents.moon(date, zone, where)
    val sunPath = DayEvents.path(SkyBody.SUN, s, e, where, 10.0)
    val moonPath = DayEvents.path(SkyBody.MOON, s, e, where, 10.0)
    var dec = emptyList<Pair<Int, List<HorizontalCoord>>>(); var hours = emptyList<List<HorizontalCoord>>()
    var eq = emptyList<HorizontalCoord>(); var arch = emptyList<HorizontalCoord>(); var trails = emptyList<List<HorizontalCoord>>()
    var radiants = emptyList<Triple<String, Boolean, HorizontalCoord>>(); var radPath = emptyList<HorizontalCoord>()
    if (night) {
        dec = (-80..80 step 10).map { it to NightSky.declinationCircle(it.toDouble(), instant, where, 4.0) }
        hours = (0 until 24).map { NightSky.hourCircle(it * 15.0, instant, where, 4.0) }
        eq = NightSky.declinationCircle(0.0, instant, where, 2.0)
        arch = MilkyWay.arch(instant, where, 2.0).map { it.second }
        // Star-trail pattern: 1-hour arcs on a fixed grid covering the whole sky.
        trails = (0 until 360 step 30).flatMap { az -> listOf(10, 30, 50, 70).map { alt -> NightSky.starTrailArc(HorizontalCoord(az.toDouble(), alt.toDouble()), instant, where, 3600.0, 10) } }
        val active = MeteorShowers.active(instant)
        radiants = active.map { o -> Triple(o.shower.name, o.shower.code == showerCode, MeteorShowers.radiantPosition(o, instant, where)) }
        active.firstOrNull { it.shower.code == showerCode }?.let { occ ->
            radPath = (0..96).map { i -> s.plusSeconds((e.epochSecond - s.epochSecond) * i / 96) }.map { MeteorShowers.radiantPosition(occ, it, where) }
        }
    }
    return ArSky(sunPath, moonPath, listOfNotNull(sd.sunrise?.azimuth, sd.sunset?.azimuth), listOfNotNull(md.moonrise?.azimuth, md.moonset?.azimuth),
        Sky.position(SkyBody.SUN, instant, where), MoonCalc.info(instant, where), dec, hours, eq, arch, MilkyWay.galacticCenter(instant, where),
        NightSky.northCelestialPole(instant, where), NightSky.southCelestialPole(instant, where), NightSky.polaris(instant, where), trails, radiants, radPath)
}

private fun DrawScope.drawBodyPath(p: Projector, path: List<Pair<Instant, com.photocapsules.core.astro.BodyPosition>>, riseSet: List<Double>, zone: java.time.ZoneId, color: Color) {
    polyline(p, path.map { HorizontalCoord(it.second.azimuth, it.second.altitude) }, color, 4f)
    path.filter { it.first.atZone(zone).minute == 0 }.forEach { (t, pos) ->
        if (pos.altitude > -5) p.project(pos.azimuth, pos.altitude)?.let {
            drawCircle(color, 6f, it); text(t.atZone(zone).hour.toString() + "h", it + Offset(8f, -8f), color, 24f)
        }
    }
    riseSet.forEachIndexed { i, az -> polyline(p, listOf(HorizontalCoord(az, -10.0), HorizontalCoord(az, 5.0)), if (i == 0) color else color.copy(alpha = 0.6f), 6f) }
}

private fun DrawScope.drawSunMoon(p: Projector, sky: ArSky, zone: java.time.ZoneId) {
    drawBodyPath(p, sky.sunPath, sky.sunRiseSet, zone, Palette.Sun)
    drawBodyPath(p, sky.moonPath, sky.moonRiseSet, zone, Palette.Moon)
    p.project(sky.sun.azimuth, sky.sun.altitude)?.let { drawCircle(Palette.Sun, 26f, it); text("Sun", it + Offset(30f, 0f), Palette.Sun) }
    val m = sky.moon
    p.project(m.position.azimuth, m.position.altitude)?.let { o ->
        drawMoon(m.phaseAngle, m.brightLimbFromZenith, Color.Transparent, o, 26f)
        text("Moon ${Fmt.pct(m.illumination)}", o + Offset(30f, 0f), Palette.Moon)
    }
}

private fun DrawScope.drawNight(p: Projector, sky: ArSky, zone: java.time.ZoneId) {
    for ((dec, c) in sky.decCircles) polyline(p, c, Color(0x553F6FD8), if (dec == 0) 3f else 1.2f)
    for (c in sky.hourCircles) polyline(p, c, Color(0x333F6FD8), 1.2f)
    polyline(p, sky.equator, Color(0xFF6FA8FF), 3f)
    polyline(p, sky.arch, Palette.MilkyWay.copy(alpha = 0.9f), 10f)
    p.project(sky.gc.azimuth, sky.gc.altitude)?.let { drawCircle(Palette.Red, 16f, it); text("Galactic Center", it + Offset(20f, 0f), Palette.Red) }
    p.project(sky.ncp.azimuth, sky.ncp.altitude)?.let { drawCircle(Color.White, 10f, it, style = Stroke(3f)); text("NCP", it + Offset(14f, 0f)) }
    p.project(sky.scp.azimuth, sky.scp.altitude)?.let { drawCircle(Color.White, 10f, it, style = Stroke(3f)); text("SCP", it + Offset(14f, 0f)) }
    p.project(sky.polaris.azimuth, sky.polaris.altitude)?.let { drawCircle(Color.White, 6f, it); text("Polaris", it + Offset(10f, 16f), sizePx = 24f) }
    for (arc in sky.trails) {
        polyline(p, arc, Color(0xAA9FDBFF), 2.5f)
        arc.lastOrNull()?.let { end -> p.project(end.azimuth, end.altitude)?.let { drawCircle(Color(0xFF9FDBFF), 5f, it) } }
    }
    drawBodyPath(p, sky.moonPath, sky.moonRiseSet, zone, Palette.Moon)
    val m = sky.moon
    p.project(m.position.azimuth, m.position.altitude)?.let { drawMoon(m.phaseAngle, m.brightLimbFromZenith, Color.Transparent, it, 24f) }
    for ((name, selected, rp) in sky.radiants) p.project(rp.azimuth, rp.altitude)?.let {
        drawCircle(if (selected) Palette.Accent else Color(0xFFEC407A), if (selected) 16f else 9f, it, style = Stroke(4f))
        text(name, it + Offset(18f, 0f), if (selected) Palette.Accent else Color(0xFFEC407A), if (selected) 30f else 22f)
    }
    if (sky.radiantPath.isNotEmpty()) polyline(p, sky.radiantPath, Palette.Accent, 3f, dashed = true)
}

/** Ground lines at the given distances (device held at [deviceHeight] m). */
private fun DrawScope.drawGround(p: Projector, req: ArRequest, deviceHeight: Double, units: com.photocapsules.core.photo.UnitSystem) {
    val c = p.center()
    // Ruler: faint lines every 0.5 m (metric) / 1 ft up to the largest distance
    val maxD = (req.distancesM.maxOfOrNull { it.second } ?: 10.0).coerceAtMost(200.0)
    val step = if (units == com.photocapsules.core.photo.UnitSystem.METRIC) 0.5 else 0.3048
    var d = step
    while (d <= maxD * 1.2) {
        val alt = -Math.toDegrees(atan(deviceHeight / d))
        polyline(p, (-30..30 step 5).map { HorizontalCoord(normalizeDegrees(c.azimuth + it), alt) }, Color.White.copy(alpha = 0.18f), 1f)
        d += step
    }
    val colors = mapOf("Near" to Color(0xFFB0B0B0), "Focus" to Palette.Red, "Far" to Color(0xFFB0B0B0), "Hyperfocal" to Palette.Moon, "Subject" to Palette.Red, "Near (H/2)" to Color(0xFFB0B0B0))
    for ((label, dist) in req.distancesM) {
        if (!dist.isFinite() || dist <= 0) continue
        val alt = -Math.toDegrees(atan(deviceHeight / dist))
        val color = colors[label] ?: Palette.Accent
        polyline(p, (-35..35 step 5).map { HorizontalCoord(normalizeDegrees(c.azimuth + it), alt) }, color, 5f)
        p.project(c.azimuth + 8, alt)?.let { text("$label ${Fmt.dist(dist, units)}", it + Offset(0f, -10f), color) }
    }
    p.project(c.azimuth, 0.0)?.let { text("Device height ${Fmt.dist(deviceHeight, units)} (Settings)", Offset(20f, size.height - 220f), Color.White, 24f) }
}

private fun frameCorners(p: Projector, req: ArRequest): List<DoubleArray> {
    val th = tan(Math.toRadians(req.frameHAngleDeg ?: 60.0) / 2); val tv = tan(Math.toRadians(req.frameVAngleDeg ?: 40.0) / 2)
    return listOf(-th to tv, th to tv, th to -tv, -th to -tv).map { (x, y) ->
        val n = sqrt(x * x + y * y + 1)
        val d = doubleArrayOf(x / n, y / n, -1 / n)
        // world = R · device
        doubleArrayOf(
            p.r[0] * d[0] + p.r[1] * d[1] + p.r[2] * d[2],
            p.r[3] * d[0] + p.r[4] * d[1] + p.r[5] * d[2],
            p.r[6] * d[0] + p.r[7] * d[1] + p.r[8] * d[2],
        )
    }
}

private fun DrawScope.drawFrame(p: Projector, req: ArRequest, locked: List<DoubleArray>?) {
    val corners = (locked ?: frameCorners(p, req)).map { p.projectWorld(it) }
    if (corners.all { it != null }) {
        for (i in 0 until 4) drawLine(Palette.Accent, corners[i]!!, corners[(i + 1) % 4]!!, 5f)
    }
    text(String.format(Locale.US, "%.0f mm · %.1f° × %.1f°", req.focalMm, req.frameHAngleDeg ?: 0.0, req.frameVAngleDeg ?: 0.0), Offset(20f, size.height - 220f), Palette.Accent)
}

/** Minimum |declination| among the centre and the corners of a landscape and portrait frame. */
private fun minAbsDeclination(p: Projector, where: GeoPoint, instant: Instant): Double {
    val pts = listOf(p.w / 2 to p.h / 2, 0f to 0f, p.w to 0f, 0f to p.h, p.w to p.h)
    return pts.map { (x, y) -> NightSky.declinationAt(p.unproject(x, y), instant, where) }.minBy { kotlin.math.abs(it) }
}

private fun DrawScope.drawSpotStars(p: Projector, sky: ArSky?, where: GeoPoint, instant: Instant, req: ArRequest) {
    sky?.let { drawNight(p, it, java.time.ZoneId.systemDefault()) }
    val cam = PhotoCapsulesApp.instance.settings.camera()
    val dec = minAbsDeclination(p, where, instant)
    val pitch = cam.pixelPitchUm
    val t = pitch?.let { SpotStars.npf(req.aperture, req.focalMm, it, dec, SpotStars.Accuracy.DEFAULT) }
    val txt = String.format(Locale.US, "Min. declination %.1f°  ·  NPF %s  ·  500 rule %.1f s", dec, t?.let { String.format(Locale.US, "%.1f s", it) } ?: "—", SpotStars.rule500(cam.cropFactor, req.focalMm))
    text(txt, Offset(20f, size.height - 220f), Palette.Accent, 30f)
}
