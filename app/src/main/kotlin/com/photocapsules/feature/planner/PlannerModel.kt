package com.photocapsules.feature.planner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.LunarEclipse
import com.photocapsules.core.astro.SolarEclipse
import com.photocapsules.core.astro.SolarEclipseMapData
import com.photocapsules.core.data.PinState
import com.photocapsules.core.data.Plan
import com.photocapsules.core.geo.Geodesy
import com.photocapsules.core.geo.LatLon
import com.photocapsules.core.geo.Terrain
import com.photocapsules.core.photo.Orientation
import java.time.Instant
import java.time.ZoneId

enum class BodyLayerStyle(val label: String) { OFF("Off"), STANDARD("Standard"), PATH("Path"), LIGHT("Light direction") }
enum class MapTool(val label: String) { NONE("None"), FOV("Field of view"), DOF("Depth of field"), SUN_MOON("Sun/Moon size") }
enum class PlannerMode { CAMERA, DRONE }
enum class CompositionGrid(val label: String) { NONE("None"), THIRDS("Rule of thirds"), GOLDEN("Golden ratio"), DIAGONAL("Diagonals"), TRIANGLE("Golden triangle"), CROSSHAIR("Crosshair"), SPIRAL("Golden spiral") }

/** Observable Planner state (single source of truth for map, panels and dialogs). */
class PlannerModel(start: GeoPoint, startInstant: Instant, startZone: ZoneId) {
    var red by mutableStateOf(LatLon(start.latitude, start.longitude))
    var redTerrain by mutableStateOf<Double?>(start.elevationM.takeIf { it != 0.0 })
    var redCustomAltitude by mutableStateOf<Double?>(null)
    var redOffset by mutableDoubleStateOf(0.0)
    var black by mutableStateOf<LatLon?>(null)
    var blackTerrain by mutableStateOf<Double?>(null)
    var blackCustomAltitude by mutableStateOf<Double?>(null)
    var blackOffset by mutableDoubleStateOf(0.0)

    var instant by mutableStateOf(startInstant)
    var zone by mutableStateOf(startZone)
    var zoneAuto by mutableStateOf(true)

    var horizonEnabled by mutableStateOf(false)
    var horizonPin by mutableStateOf<LatLon?>(null)
    var horizonPinTerrain by mutableStateOf<Double?>(null)
    /** Manually entered height above the horizon (overrides the Horizon Pin computation). */
    var horizonCustomHeight by mutableStateOf<Double?>(null)

    var sunStyle by mutableStateOf(BodyLayerStyle.STANDARD)
    var moonStyle by mutableStateOf(BodyLayerStyle.STANDARD)
    var showSunSize by mutableStateOf(false)
    var showMoonSize by mutableStateOf(false)
    var realAzimuthLines by mutableStateOf(false)
    var milkyWay by mutableStateOf(false)
    var twilightLayer by mutableStateOf(true)
    var shadowLayer by mutableStateOf(false)
    var meteorCode by mutableStateOf<String?>(null)
    var meteorLayer by mutableStateOf(false)
    var lunarEclipse by mutableStateOf<LunarEclipse?>(null)
    var solarEclipse by mutableStateOf<SolarEclipse?>(null)
    var solarMap by mutableStateOf<SolarEclipseMapData?>(null)
    var eclipseLayer by mutableStateOf(false)
    var extendLines by mutableStateOf(false)

    var tool by mutableStateOf(MapTool.NONE)
    var mode by mutableStateOf(PlannerMode.CAMERA)
    var focalMm by mutableDoubleStateOf(24.0)
    var aperture by mutableDoubleStateOf(8.0)
    var focusM by mutableDoubleStateOf(10.0)
    var focusAtBlackPin by mutableStateOf(false)
    var orientation by mutableStateOf(Orientation.LANDSCAPE)
    var toolAzimuth by mutableDoubleStateOf(0.0)
    var azimuthAtBlackPin by mutableStateOf(false)
    var sunMoonToolBody by mutableIntStateOf(1) // 0 sun, 1 moon
    var sunMoonSizeM by mutableDoubleStateOf(50.0)
    var droneAltitudeM by mutableDoubleStateOf(120.0)
    var droneGrid by mutableStateOf(CompositionGrid.THIRDS)
    /** Camera used in drone mode (null = the default camera from Settings). */
    var droneCameraName by mutableStateOf<String?>(null)

    /** Camera used by the map tools: the drone camera in drone mode, otherwise the default camera. */
    fun toolCamera(): com.photocapsules.core.photo.Camera {
        val store = com.photocapsules.PhotoCapsulesApp.instance.settings
        return if (mode == PlannerMode.DRONE) droneCameraName?.let { store.cameraByName(it) } ?: store.camera() else store.camera()
    }

    /** Real focal length used by the tools: a fixed-lens camera (drone, compact, phone) forces its own lens. */
    fun toolFocalMm(): Double = toolCamera().fixedFocalMm ?: focalMm

    var objectHeightM by mutableDoubleStateOf(10.0)
    var findBody by mutableIntStateOf(0)
    // Find dialog inputs, kept while the dialog is closed (e.g. while pointing a target with AR).
    var findRangeIdx by mutableIntStateOf(3)
    var findStart by mutableStateOf<Instant?>(null)
    var findAz by mutableStateOf<Double?>(null)
    var findAzTol by mutableDoubleStateOf(3.0)
    var findUseAlt by mutableStateOf(true)
    var findAlt by mutableStateOf<Double?>(null)
    var findAltTol by mutableDoubleStateOf(0.5)
    var focalLocked by mutableStateOf(false)
    var teleconverter by mutableDoubleStateOf(1.0)
    var expanded by mutableStateOf(false)
    var compassRotate by mutableStateOf(false)
    var movePinMode by mutableStateOf(false)
    var fineTimeScale by mutableStateOf(false)
    var centerRequest by mutableIntStateOf(0)

    /** Full state of a pin (position and altitudes) for undo/redo. */
    private data class PinSnap(val pos: LatLon, val custom: Double?, val terrain: Double?, val offset: Double)
    private data class PinsSnapshot(val red: PinSnap, val black: PinSnap?)
    private val undo = ArrayDeque<PinsSnapshot>()
    private val redo = ArrayDeque<PinsSnapshot>()

    private fun snapshot() = PinsSnapshot(
        PinSnap(red, redCustomAltitude, redTerrain, redOffset),
        black?.let { PinSnap(it, blackCustomAltitude, blackTerrain, blackOffset) },
    )

    private fun pushUndo() { undo.addLast(snapshot()); redo.clear(); if (undo.size > 50) undo.removeFirst() }

    /** Observer altitude: terrain (or custom) + offset. */
    val redAltitude: Double get() = (redCustomAltitude ?: redTerrain ?: 0.0) + redOffset
    val blackGround: Double get() = blackCustomAltitude ?: blackTerrain ?: 0.0
    val blackAltitude: Double get() = blackGround + blackOffset
    val observer: GeoPoint get() = GeoPoint(red.latitude, red.longitude, redAltitude)

    /** Height of the observer above the visible horizon point (More → Horizon). */
    val horizonHeightM: Double get() {
        if (!horizonEnabled) return 0.0
        horizonCustomHeight?.let { return it.coerceAtLeast(0.0) }
        horizonPin ?: return 0.0
        return (redAltitude - (horizonPinTerrain ?: 0.0)).coerceAtLeast(0.0)
    }

    fun moveRed(p: LatLon, record: Boolean = true) {
        if (record) pushUndo()
        // Custom altitudes reset when the pin moves more than 30 m (PhotoPills behaviour).
        if (Geodesy.haversine(red, p) > 30) { redCustomAltitude = null; redTerrain = null }
        relocateHorizon(red, p)
        red = p
    }

    fun moveBlack(p: LatLon?, record: Boolean = true) {
        if (record) pushUndo()
        val b = black
        if (p == null || b == null || Geodesy.haversine(b, p) > 30) { blackCustomAltitude = null; blackTerrain = null }
        black = p
    }

    fun undo() { undo.removeLastOrNull()?.let { redo.addLast(snapshot()); restore(it) } }
    fun redo() { redo.removeLastOrNull()?.let { undo.addLast(snapshot()); restore(it) } }

    /** Restores both pins exactly as they were (position, custom/terrain altitude and offset). */
    private fun restore(s: PinsSnapshot) {
        relocateHorizon(red, s.red.pos)
        red = s.red.pos; redCustomAltitude = s.red.custom; redTerrain = s.red.terrain; redOffset = s.red.offset
        black = s.black?.pos; blackCustomAltitude = s.black?.custom; blackTerrain = s.black?.terrain; blackOffset = s.black?.offset ?: 0.0
    }

    /**
     * Keeps the Horizon Pin at the same bearing and distance from the Red Pin when the Red Pin moves more
     * than 2 km (its terrain and any manual horizon height belong to the old place and are reset).
     */
    private fun relocateHorizon(from: LatLon, to: LatLon) {
        val hp = horizonPin ?: return
        if (Geodesy.haversine(from, to) <= 2000.0) return
        val inv = Geodesy.inverse(from, hp)
        horizonPin = Geodesy.direct(to, inv.initialBearing, inv.distanceMeters.coerceIn(500.0, 100_000.0))
        horizonPinTerrain = null; horizonCustomHeight = null
    }

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Swaps the pins with everything attached to them (altitudes and offsets). */
    fun swapPins() {
        val b = black ?: return
        pushUndo()
        relocateHorizon(red, b)
        val rt = redTerrain; val rc = redCustomAltitude; val ro = redOffset
        black = red; red = b
        redTerrain = blackTerrain; redCustomAltitude = blackCustomAltitude; redOffset = blackOffset
        blackTerrain = rt; blackCustomAltitude = rc; blackOffset = ro
    }

    data class Geodetics(val distanceM: Double, val bearing: Double, val altitudeDiffM: Double, val elevationAngle: Double)

    fun geodetics(): Geodetics? {
        val b = black ?: return null
        val inv = Geodesy.inverse(red, b)
        return Geodetics(inv.distanceMeters, inv.initialBearing, blackGround - (redCustomAltitude ?: redTerrain ?: 0.0),
            Terrain.elevationAngle(inv.distanceMeters, redAltitude, blackAltitude))
    }

    fun effectiveAzimuth(): Double = if (azimuthAtBlackPin) geodetics()?.bearing ?: toolAzimuth else toolAzimuth
    fun effectiveFocus(): Double = if (focusAtBlackPin) geodetics()?.distanceM ?: focusM else focusM

    fun toPlan(id: String, name: String, notes: String = ""): Plan = Plan(
        id = id, name = name,
        redPin = PinState(red.latitude, red.longitude, redCustomAltitude ?: redTerrain, redOffset),
        blackPin = black?.let { PinState(it.latitude, it.longitude, blackCustomAltitude ?: blackTerrain, blackOffset) },
        epochMillis = instant.toEpochMilli(), zoneId = zone.id, notes = notes,
    )

    fun load(plan: Plan) {
        // moveRed/moveBlack clear the terrain altitude only of a pin that really moves (> 30 m).
        moveRed(LatLon(plan.redPin.latitude, plan.redPin.longitude))
        // Saved altitudes are kept as custom altitudes so the online terrain lookup does not overwrite them.
        redCustomAltitude = plan.redPin.altitudeM; redOffset = plan.redPin.offsetM
        moveBlack(plan.blackPin?.let { LatLon(it.latitude, it.longitude) }, record = false)
        blackCustomAltitude = plan.blackPin?.altitudeM; blackOffset = plan.blackPin?.offsetM ?: 0.0
        instant = Instant.ofEpochMilli(plan.epochMillis)
        zone = runCatching { ZoneId.of(plan.zoneId) }.getOrDefault(zone); zoneAuto = false
        centerRequest++
    }

    fun resetLayers() {
        sunStyle = BodyLayerStyle.STANDARD; moonStyle = BodyLayerStyle.STANDARD; twilightLayer = true
        milkyWay = false; shadowLayer = false; meteorLayer = false; eclipseLayer = false; showSunSize = false; showMoonSize = false
        tool = MapTool.NONE
    }
}
