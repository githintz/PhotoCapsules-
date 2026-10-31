package com.photocapsules.data

import com.photocapsules.core.astro.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId

/** Location + time context shared by the Sun, Moon, Meteor, Night AR pills (default: here and now). */
data class SkyContext(
    val point: GeoPoint,
    val label: String,
    val instant: Instant,
    val zone: ZoneId,
    /** True when following the device's location and the current time. */
    val live: Boolean = true,
    val horizonHeightM: Double = 0.0,
)

/** Parameters handed to an AR screen. */
data class ArRequest(
    val context: SkyContext,
    val meteorShowerCode: String? = null,
    val focalMm: Double = 24.0,
    val aperture: Double = 2.8,
    val distancesM: List<Pair<String, Double>> = emptyList(),
    /** Angles of view of a camera frame to outline in AR (FoV AR). */
    val frameHAngleDeg: Double? = null,
    val frameVAngleDeg: Double? = null,
)

object AppState {
    val defaultPoint = GeoPoint(40.4168, -3.7038, 667.0) // Madrid until a location fix arrives
    val sky = MutableStateFlow(SkyContext(defaultPoint, "Madrid (default)", Instant.now(), ZoneId.systemDefault()))
    var ar: ArRequest? = null
    /** Plan to open in the Planner (set by My Stuff → Plans). */
    val pendingPlanId = MutableStateFlow<String?>(null)
    /** Location to open in the Planner (from a POI). */
    val pendingPlannerLocation = MutableStateFlow<GeoPoint?>(null)
    /** Calculator hand-offs ("To DoF", "To FoV"). */
    var handoffFocal: Double? = null
    var handoffAperture: Double? = null
    var handoffDistance: Double? = null
    /** Timer preset seconds from a calculator. */
    var timerPresetSeconds: Double? = null
    /** Declination measured in the Spot Stars AR. */
    val measuredDeclination = MutableStateFlow<Double?>(null)
}
