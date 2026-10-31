package com.photocapsules.data

import android.content.Context
import com.photocapsules.core.geo.CoordinateFormat
import com.photocapsules.core.photo.Camera
import com.photocapsules.core.photo.CameraDatabase
import com.photocapsules.core.photo.StopScale
import com.photocapsules.core.photo.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

enum class MapType(val label: String) { STANDARD("Standard (OSM)"), TOPO("Topographic"), SATELLITE("Satellite") }
enum class AzimuthReference(val label: String) { TRUE_NORTH("True north"), MAGNETIC_NORTH("Magnetic north") }

data class Settings(
    val units: UnitSystem = UnitSystem.METRIC,
    val stopScale: StopScale = StopScale.THIRD,
    val cameraName: String = "Generic Full frame 24 MP",
    val customCameraJson: String? = null,
    val mapType: MapType = MapType.STANDARD,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DDD,
    val azimuthReference: AzimuthReference = AzimuthReference.TRUE_NORTH,
    val deviceHeightM: Double = 1.35,
    val useGyroscope: Boolean = true,
    val showCompassWarning: Boolean = true,
    val showMyLocation: Boolean = true,
    val onlineElevation: Boolean = true,
    val use24h: Boolean = true,
)

/** SharedPreferences-backed settings exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

    private fun read() = Settings(
        units = enumOf("units", UnitSystem.METRIC),
        stopScale = enumOf("stops", StopScale.THIRD),
        cameraName = prefs.getString("camera", null) ?: "Generic Full frame 24 MP",
        customCameraJson = prefs.getString("customCamera", null),
        mapType = enumOf("mapType", MapType.STANDARD),
        coordinateFormat = enumOf("coordFmt", CoordinateFormat.DDD),
        azimuthReference = enumOf("azRef", AzimuthReference.TRUE_NORTH),
        deviceHeightM = prefs.getFloat("deviceHeight", 1.35f).toDouble(),
        useGyroscope = prefs.getBoolean("gyro", true),
        showCompassWarning = prefs.getBoolean("compassWarn", true),
        showMyLocation = prefs.getBoolean("myLoc", true),
        onlineElevation = prefs.getBoolean("onlineElev", true),
        use24h = prefs.getBoolean("use24h", true),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("units", s.units.name).putString("stops", s.stopScale.name).putString("camera", s.cameraName)
            .putString("customCamera", s.customCameraJson).putString("mapType", s.mapType.name)
            .putString("coordFmt", s.coordinateFormat.name).putString("azRef", s.azimuthReference.name)
            .putFloat("deviceHeight", s.deviceHeightM.toFloat()).putBoolean("gyro", s.useGyroscope)
            .putBoolean("compassWarn", s.showCompassWarning).putBoolean("myLoc", s.showMyLocation)
            .putBoolean("onlineElev", s.onlineElevation).putBoolean("use24h", s.use24h)
            .apply()
        _state.value = s
    }

    /** All cameras: bundled database + the user's custom camera. */
    fun cameras(): List<Camera> = listOfNotNull(customCamera()) + CameraDatabase.all

    fun customCamera(): Camera? = current.customCameraJson?.let { runCatching { Json.decodeFromString(Camera.serializer(), it) }.getOrNull() }

    fun saveCustomCamera(c: Camera) = update { it.copy(customCameraJson = Json.encodeToString(Camera.serializer(), c), cameraName = c.name) }

    fun camera(): Camera = cameraByName(current.cameraName)

    fun cameraByName(name: String): Camera =
        customCamera()?.takeIf { it.name == name } ?: CameraDatabase.find(name) ?: CameraDatabase.all.first { it.model.startsWith("Full frame 24") }

    /** Per-screen key/value persistence for calculator inputs. */
    fun getString(key: String, def: String): String = prefs.getString("v_$key", def) ?: def
    fun putString(key: String, v: String) = prefs.edit().putString("v_$key", v).apply()
    fun getDouble(key: String, def: Double): Double = prefs.getString("d_$key", null)?.toDoubleOrNull() ?: def
    fun putDouble(key: String, v: Double) = prefs.edit().putString("d_$key", v.toString()).apply()
}
