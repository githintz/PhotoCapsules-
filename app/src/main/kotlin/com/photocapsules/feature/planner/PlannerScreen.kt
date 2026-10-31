package com.photocapsules.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.Eclipses
import com.photocapsules.core.astro.HorizontalCoord
import com.photocapsules.core.astro.MeteorShowers
import com.photocapsules.core.astro.MilkyWay
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.core.astro.Sky
import com.photocapsules.core.astro.SkyBody
import com.photocapsules.core.astro.Timeline
import com.photocapsules.core.geo.Geodesy
import com.photocapsules.core.geo.LatLon
import com.photocapsules.core.geo.Terrain
import com.photocapsules.core.photo.DepthOfField
import com.photocapsules.core.photo.DofInput
import com.photocapsules.core.photo.DroneFrame
import com.photocapsules.core.photo.FieldOfView
import com.photocapsules.core.photo.Orientation
import com.photocapsules.data.AppState
import com.photocapsules.data.ArRequest
import com.photocapsules.data.LocationService
import com.photocapsules.data.OnlineServices
import com.photocapsules.data.SkyContext
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.feature.calculators.settingsState
import com.photocapsules.ui.Routes
import com.photocapsules.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private data class DayKey(val date: java.time.LocalDate, val zone: ZoneId, val lat: Double, val lon: Double, val alt: Double, val horizon: Double)
private data class DayData(val panel: PanelData, val sunPath: List<Pair<java.time.Instant, com.photocapsules.core.astro.BodyPosition>>,
                           val moonPath: List<Pair<java.time.Instant, com.photocapsules.core.astro.BodyPosition>>, val events: List<java.time.Instant>)

/** The Planner model survives navigation (like PhotoPills keeps the planner state). */
private var savedModel: PlannerModel? = null

@Composable
fun PlannerScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = settingsState()
    val m = remember { savedModel ?: AppState.sky.value.let { PlannerModel(it.point, it.instant, it.zone) }.also { savedModel = it } }
    var holder by remember { mutableStateOf<PlannerMapHolder?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var toolbar by remember { mutableStateOf(false) }

    // Pending plan / location hand-offs from My Stuff
    val pendingPlan by AppState.pendingPlanId.collectAsState()
    LaunchedEffect(pendingPlan) {
        pendingPlan?.let { id -> PhotoCapsulesApp.instance.library.load().plans.firstOrNull { it.id == id }?.let { m.load(it) }; AppState.pendingPlanId.value = null }
    }
    // Returning from the AR target picker reopens Find with the picked direction.
    val findTarget by AppState.findTarget.collectAsState()
    LaunchedEffect(findTarget) { if (findTarget != null) dialog = "find" }
    val pendingCtx by AppState.pendingPlannerContext.collectAsState()
    LaunchedEffect(pendingCtx) {
        pendingCtx?.let { c ->
            m.moveRed(LatLon(c.point.latitude, c.point.longitude)); m.instant = c.instant; m.zone = c.zone
            m.zoneAuto = c.live; m.centerRequest++
            AppState.pendingPlannerContext.value = null
        }
    }
    val pendingLoc by AppState.pendingPlannerLocation.collectAsState()
    LaunchedEffect(pendingLoc) {
        pendingLoc?.let { p -> m.moveRed(LatLon(p.latitude, p.longitude)); if (p.elevationM != 0.0) m.redCustomAltitude = p.elevationM; m.centerRequest++; AppState.pendingPlannerLocation.value = null }
    }

    // Terrain elevation + time zone lookups (debounced) when pins move.
    LaunchedEffect(m.red, settings.onlineElevation) {
        delay(400)
        if (settings.onlineElevation && m.redCustomAltitude == null) OnlineServices.elevation(m.red.latitude, m.red.longitude)?.let { m.redTerrain = it }
        if (m.zoneAuto) OnlineServices.timeZone(m.red.latitude, m.red.longitude)?.let { id -> runCatching { ZoneId.of(id) }.getOrNull()?.let { m.zone = it } }
    }
    LaunchedEffect(m.black, settings.onlineElevation) {
        val b = m.black ?: return@LaunchedEffect
        delay(400)
        if (settings.onlineElevation && m.blackCustomAltitude == null) OnlineServices.elevation(b.latitude, b.longitude)?.let { m.blackTerrain = it }
    }
    LaunchedEffect(m.horizonPin, m.horizonEnabled) {
        val hp = m.horizonPin ?: return@LaunchedEffect
        if (!m.horizonEnabled) return@LaunchedEffect
        delay(400)
        OnlineServices.elevation(hp.latitude, hp.longitude)?.let { m.horizonPinTerrain = it }
    }

    LaunchedEffect(m.red) { com.photocapsules.ui.components.Fmt.setLocation(m.red.latitude, m.red.longitude) }

    pendingBlackToggle = {
        if (m.black == null) m.moveBlack(holder?.mapCenter()?.let { c -> if (Geodesy.haversine(c, m.red) < 5) Geodesy.direct(m.red, 45.0, 500.0) else c } ?: Geodesy.direct(m.red, 45.0, 500.0))
        else m.moveBlack(null)
    }

    // ---- Day data (background) ----
    val obs = m.observer
    val key = DayKey(m.instant.atZone(m.zone).toLocalDate(), m.zone, obs.latitude, obs.longitude, obs.elevationM, m.horizonHeightM)
    val dayData by produceState<DayData?>(null, key, m.lunarEclipse, m.solarEclipse) {
        value = withContext(Dispatchers.Default) {
            val (s, e) = DayEvents.dayWindow(key.date, key.zone)
            val sd = DayEvents.sun(key.date, key.zone, obs, key.horizon)
            val md = DayEvents.moon(key.date, key.zone, obs, key.horizon)
            val night = key.date.atTime(12, 0).atZone(key.zone).toInstant()
            val gc = MilkyWay.visibility(night.minusSeconds(86400), night.plusSeconds(86400), obs).filter { it.end.time > s && it.start.time < e }
            val local = m.solarEclipse?.let { Eclipses.solarLocal(it, obs) }
            val events = Timeline.events(key.date, key.zone, obs, key.horizon).map { it.time } +
                Timeline.events(key.date.plusDays(1), key.zone, obs, key.horizon).map { it.time } + Timeline.events(key.date.minusDays(1), key.zone, obs, key.horizon).map { it.time }
            DayData(PanelData(sd, md, gc, local), DayEvents.path(SkyBody.SUN, s, e, obs, 10.0), DayEvents.path(SkyBody.MOON, s, e, obs, 10.0), events.sorted())
        }
    }

    // ---- Instant data (synchronous; fast) ----
    val sun = Sky.position(SkyBody.SUN, m.instant, obs)
    val moon = MoonCalc.info(m.instant, obs)
    val gc = MilkyWay.galacticCenter(m.instant, obs)
    // Slow-changing geometry is cached by minute / by day so scrubbing the time bar stays fluid.
    val minute = m.instant.epochSecond / 60
    val arch = remember(m.milkyWay, minute, obs) { if (m.milkyWay) MilkyWay.arch(m.instant, obs, 3.0).map { it.second } else emptyList() }
    val archInfo = remember(m.milkyWay, minute, obs) { if (m.milkyWay) MilkyWay.archInfo(m.instant, obs) else null }
    val year = m.instant.atZone(m.zone).year
    val occ = remember(m.meteorCode, year, m.instant.epochSecond / 86400) {
        m.meteorCode?.let { c -> MeteorShowers.byCode(c)?.let { sh -> listOf(-1, 0, 1).map { MeteorShowers.occurrence(sh, year + it) }.minBy { kotlin.math.abs(it.peak.epochSecond - m.instant.epochSecond) } } }
    }
    val radiant = occ?.let { MeteorShowers.radiantPosition(it, m.instant, obs) }
    val radiantPath = remember(m.meteorLayer, occ, m.instant.epochSecond / 1800, obs) {
        if (m.meteorLayer && occ != null) (0..48).map { i -> MeteorShowers.radiantPosition(occ, m.instant.minusSeconds(43200).plusSeconds(i * 1800L), obs) } else emptyList()
    }
    val shadowSource = if (sun.altitude > 0) sun else if (moon.position.altitude > 0) moon.position else null
    val shadowLen = shadowSource?.let { s -> Sky.shadowRatio(s.altitude)?.let { it * m.objectHeightM } }
    val cam = PhotoCapsulesApp.instance.settings.camera()
    val (frameW, frameH) = if (m.orientation == Orientation.LANDSCAPE) cam.sensorWidthMm to cam.sensorHeightMm else cam.sensorHeightMm to cam.sensorWidthMm
    val hAngle = FieldOfView.angleDeg(frameW, m.focalMm * m.teleconverter)
    val focus = m.effectiveFocus()
    val dof = if (m.tool == MapTool.DOF && focus * 1000 > m.focalMm * m.teleconverter) DepthOfField.compute(DofInput(m.focalMm, m.aperture, focus, cam.circleOfConfusionMm, m.teleconverter)) else null
    val smBody = if (m.sunMoonToolBody == 0) sun else moon.position
    val g = m.geodetics()
    val frame = PlannerFrame(
        red = m.red, black = m.black, blackElevationAngle = g?.elevationAngle, blackBearing = g?.bearing, zone = m.zone,
        sun = sun, moon = moon,
        sunRiseAz = dayData?.panel?.sunDay?.sunrise?.azimuth, sunSetAz = dayData?.panel?.sunDay?.sunset?.azimuth,
        moonRiseAz = dayData?.panel?.moonDay?.moonrise?.azimuth, moonSetAz = dayData?.panel?.moonDay?.moonset?.azimuth,
        sunPath = dayData?.sunPath.orEmpty(), moonPath = dayData?.moonPath.orEmpty(),
        light = DayEvents.lightType(m.instant, obs), gc = gc, arch = arch, archCrossings = archInfo?.horizonCrossings.orEmpty(),
        gcVisibleAz = dayData?.panel?.gcVisibility?.firstOrNull()?.start?.azimuth, gcHiddenAz = dayData?.panel?.gcVisibility?.firstOrNull()?.end?.azimuth,
        radiant = radiant, radiantPath = radiantPath,
        shadowLengthM = shadowLen, shadowAzimuth = shadowSource?.let { com.photocapsules.core.astro.normalizeDegrees(it.azimuth + 180) },
        solarMap = m.solarMap, lunarEclipse = m.lunarEclipse,
        toolAzimuth = m.effectiveAzimuth(), toolHAngle = hAngle, toolFocusM = focus, toolDof = dof,
        sunMoonToolRadiusM = if (m.tool == MapTool.SUN_MOON) Terrain.distanceForSize(smBody.angularDiameterDeg, m.sunMoonSizeM) else null,
        droneFootprint = if (m.mode == PlannerMode.DRONE) DroneFrame.footprint(cam, m.focalMm, m.droneAltitudeM, m.orientation).let { it.widthM to it.heightM } else null,
        droneGrid = m.droneGrid, model = m,
    )

    val pager = rememberPagerState(initialPage = 2) { PANEL_COUNT }
    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Planner", color = Palette.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (m.tool != MapTool.NONE || m.mode == PlannerMode.DRONE) TextButton(onClick = { dialog = "tool" }) { Text("Tool ⚙") }
        }
        if (!m.expanded) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(136.dp)) { page ->
                PlannerPanel(page, m, frame, dayData?.panel ?: PanelData(null, null, emptyList(), null), settings.units,
                    onEclipsePick = { dialog = "eclipse" }, onMeteorPick = { dialog = "meteor" })
            }
            Row(Modifier.fillMaxWidth().padding(2.dp), horizontalArrangement = Arrangement.Center) {
                repeat(PANEL_COUNT) { i -> Box(Modifier.padding(2.dp).size(6.dp).background(if (i == pager.currentPage) Palette.Accent else Palette.Divider, CircleShape)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PlannerMapView(m, frame, Modifier.fillMaxSize()) { holder = it }
            if (m.movePinMode) {
                Box(Modifier.align(Alignment.Center).size(40.dp)) {
                    Text("✛", color = Palette.Red, fontSize = 36.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
            // Map buttons (bottom right)
            Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalAlignment = Alignment.End) {
                if (toolbar) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).background(Color(0xCC1E1E22), RoundedCornerShape(20.dp)).padding(4.dp)) {
                        MapButton(Icons.Filled.MyLocation, "GPS") {
                            scope.launch { LocationService.current(ctx)?.let { l -> m.moveRed(LatLon(l.latitude, l.longitude)); m.centerRequest++ } }
                        }
                        MapButton(Icons.Filled.OpenWith, if (m.movePinMode) "Drop pin" else "Move pin", active = m.movePinMode) {
                            if (m.movePinMode) holder?.let { m.moveRed(it.mapCenter()) }
                            m.movePinMode = !m.movePinMode
                        }
                        MapButton(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = m.canUndo) { m.undo(); m.centerRequest++ }
                        MapButton(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = m.canRedo) { m.redo(); m.centerRequest++ }
                        MapButton(Icons.Filled.Fullscreen, "Expand", active = m.expanded) { m.expanded = !m.expanded }
                        MapButton(Icons.Filled.SwapHoriz, "Swap pins", enabled = m.black != null) { m.swapPins() }
                        MapButton(Icons.Filled.Straighten, "Extend lines", active = m.extendLines) { m.extendLines = !m.extendLines }
                        MapButton(Icons.Filled.Map, "Map type") {
                            val st = PhotoCapsulesApp.instance.settings
                            st.update { it.copy(mapType = com.photocapsules.data.MapType.entries[(it.mapType.ordinal + 1) % com.photocapsules.data.MapType.entries.size]) }
                        }
                        MapButton(Icons.Filled.Explore, "Compass", active = m.compassRotate) { m.compassRotate = !m.compassRotate }
                    }
                }
                Row {
                    SmallFab(Icons.Filled.Layers) { dialog = "settings" }
                    SmallFab(if (toolbar) Icons.Filled.Close else Icons.Filled.Add) { toolbar = !toolbar }
                }
            }
        }
        TimeBar(m.instant, m.zone, obs, m.fineTimeScale, dayData?.events.orEmpty(),
            onChange = { m.instant = it }, onCenterTap = { dialog = "date" }, onToggleFine = { m.fineTimeScale = !m.fineTimeScale })
        Row(Modifier.fillMaxWidth().background(Palette.Surface), horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomItem("Find") { dialog = "find" }
            BottomItem("AR") { AppState.ar = ArRequest(skyContext(m)); go(Routes.ar(ArMode.SUN_MOON)) }
            BottomItem("Night AR") { AppState.ar = ArRequest(skyContext(m), meteorShowerCode = m.meteorCode); go(Routes.ar(ArMode.NIGHT)) }
            BottomItem("Load") { dialog = "load" }
            BottomItem("Save") { dialog = "save" }
            BottomItem("More") { dialog = "more" }
        }
    }
    when (dialog) {
        "find" -> FindDialog(m, mapCenter = { holder?.mapCenter() }, onPickWithAr = {
            dialog = null
            AppState.ar = ArRequest(skyContext(m)); go(Routes.ar(ArMode.FIND_TARGET))
        }) { dialog = null }
        "load" -> LoadDialog(m) { dialog = null }
        "save" -> SaveDialog(m) { dialog = null }
        "more" -> MoreDialog(m) { dialog = null }
        "date" -> DateTimeDialog(m) { dialog = null }
        "settings" -> MapSettingsDialog(m, { dialog = null }, onEclipsePick = { dialog = "eclipse" }, onMeteorPick = { dialog = "meteor" })
        "tool" -> ToolSettingsDialog(m) { dialog = null }
        "eclipse" -> EclipsePickerDialog(m) { dialog = null }
        "meteor" -> MeteorPickerDialog(m) { dialog = null }
    }
}

private fun skyContext(m: PlannerModel) = SkyContext(m.observer, "Red Pin", m.instant, m.zone, live = false, horizonHeightM = m.horizonHeightM)

@Composable
private fun MapButton(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (!enabled) Palette.Divider else if (active) Palette.Accent else Color.White) }
        Text(label, color = Palette.TextDim, fontSize = 9.sp)
    }
}

@Composable
private fun SmallFab(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.padding(4.dp).size(48.dp).background(Color(0xEE1E1E22), CircleShape), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { Icon(icon, null, tint = Palette.Accent) }
    }
}

@Composable
private fun BottomItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = Palette.Accent, fontSize = 13.sp) }
}
