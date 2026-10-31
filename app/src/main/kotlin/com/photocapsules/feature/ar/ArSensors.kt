package com.photocapsules.feature.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.atan

/** Device orientation from the rotation-vector sensor (device → world ENU, magnetic north). */
class OrientationTracker(context: Context, private val useGyro: Boolean) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val rotation = FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }
    @Volatile var hasData = false; private set
    @Volatile var compassAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH; private set
    var onUpdate: (() -> Unit)? = null
    private val accel = FloatArray(3); private val mag = FloatArray(3)
    private var haveAccel = false; private var haveMag = false
    private val smoothed = FloatArray(9)
    private var first = true

    val available: Boolean get() = sensorFor() != null || (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)

    private fun sensorFor(): Sensor? =
        if (useGyro) sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        else sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    fun start() {
        val s = sensorFor()
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        else {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        // Magnetometer always registered: needed for the fallback and for accuracy reporting.
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9)
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(r, e.values)
            Sensor.TYPE_ACCELEROMETER -> { System.arraycopy(e.values, 0, accel, 0, 3); haveAccel = true; if (!fuse(r)) return }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(e.values, 0, mag, 0, 3); haveMag = true
                if (sensorFor() != null || !fuse(r)) return
            }
            else -> return
        }
        // Light low-pass filter on the matrix to reduce jitter (re-orthonormalisation not needed for small α).
        val a = if (first) 1f else 0.25f
        for (i in 0 until 9) smoothed[i] = smoothed[i] + a * (r[i] - smoothed[i])
        first = false
        System.arraycopy(smoothed, 0, rotation, 0, 9)
        hasData = true
        onUpdate?.invoke()
    }

    private fun fuse(out: FloatArray): Boolean = haveAccel && haveMag && SensorManager.getRotationMatrix(out, null, accel, mag)

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) compassAccuracy = accuracy
    }
}

/** Vertical (long-side) field of view of the back camera in portrait, degrees. */
fun backCameraVerticalFov(context: Context): Double {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: return 60.0
        val ch = cm.getCameraCharacteristics(id)
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 60.0
        val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return 60.0
        val longSide = maxOf(size.width, size.height)
        Math.toDegrees(2 * atan(longSide / (2.0 * f))).takeIf { it in 20.0..130.0 } ?: 60.0
    } catch (e: Exception) { 60.0 }
}
