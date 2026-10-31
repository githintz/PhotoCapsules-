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

    var objectHeightM by mutableDoubleStateOf(10.0)
    var findBody by mutableIntStateOf(0)
    var focalLocked by mutableStateOf(false)
    var teleconverter by mutableDoubleStateOf(1.0)
    var expanded by mutableStateOf(false)
    var compassRotate by mutableStateOf(false)
    var movePinMode by mutableStateOf(false)
    var fineTimeScale by mutableStateOf(false)
    var centerRequest by mutableIntStateOf(0)

    private val undo = ArrayDeque<Pair<LatLon, LatLon?>>()
    private val redo = ArrayDeque<Pair<LatLon, LatLon?>>()

    /** Observer altitude: terrain (or custom) + offset. */
    val redAltitude: Double get() = (redCustomAltitude ?: redTerrain ?: 0.0) + redOffset
    val blackGround: Double get() = blackCustomAltitude ?: blackTerrain ?: 0.0
    val blackAltitude: Double get() = blackGround + blackOffset
    val observer: GeoPoint get() = GeoPoint(red.latitude, red.longitude, redAltitude)

    /** Height of the observer above the visible horizon point (More → Horizon). */
    val horizonHeightM: Double get() {
        if (!horizonEnabled) return 0.0
        val hp = horizonPin ?: return 0.0
        return (redAltitude - (horizonPinTerrain ?: 0.0)).coerceAtLeast(0.0)
    }

    fun moveRed(p: LatLon, record: Boolean = true) {
        if (record) { undo.addLast(red to black); redo.clear(); if (undo.size > 50) undo.removeFirst() }
        // Custom altitudes reset when the pin moves more than 30 m (PhotoPills behaviour).
        if (Geodesy.haversine(red, p) > 30) { redCustomAltitude = null; redTerrain = null }
        red = p
    }

    fun moveBlack(p: LatLon?, record: Boolean = true) {
        if (record) { undo.addLast(red to black); redo.clear() }
        val b = black
        if (p == null || b == null || Geodesy.haversine(b, p) > 30) { blackCustomAltitude = null; blackTerrain = null }
        black = p
    }

    fun undo() { undo.removeLastOrNull()?.let { redo.addLast(red to black); red = it.first; black = it.second; redTerrain = null; blackTerrain = null } }
    fun redo() { redo.removeLastOrNull()?.let { undo.addLast(red to black); red = it.first; black = it.second; redTerrain = null; blackTerrain = null } }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun swapPins() {
        val b = black ?: return
        undo.addLast(red to black)
        val rt = redTerrain; val bt = blackTerrain
        black = red; red = b; redTerrain = bt; blackTerrain = rt
        redCustomAltitude = null; blackCustomAltitude = null
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
        moveRed(LatLon(plan.redPin.latitude, plan.redPin.longitude))
        // Saved altitudes are kept as custom altitudes so the online terrain lookup does not overwrite them.
        redTerrain = null; redCustomAltitude = plan.redPin.altitudeM; redOffset = plan.redPin.offsetM
        black = plan.blackPin?.let { LatLon(it.latitude, it.longitude) }
        blackTerrain = null; blackCustomAltitude = plan.blackPin?.altitudeM; blackOffset = plan.blackPin?.offsetM ?: 0.0
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
