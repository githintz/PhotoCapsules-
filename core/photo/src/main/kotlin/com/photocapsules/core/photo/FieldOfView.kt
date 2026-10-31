package com.photocapsules.core.photo

import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.tan

data class FovResult(
    val horizontalAngleDeg: Double,
    val verticalAngleDeg: Double,
    val diagonalAngleDeg: Double,
    /** Field dimensions at the focus distance, metres. */
    val horizontalFieldM: Double,
    val verticalFieldM: Double,
    val diagonalFieldM: Double,
)

enum class FovDimension { HORIZONTAL, VERTICAL, DIAGONAL }

/** Rectilinear angle and field of view. */
object FieldOfView {
    fun angleDeg(sensorDimMm: Double, focalMm: Double): Double = Math.toDegrees(2 * atan(sensorDimMm / (2 * focalMm)))

    /** Field at distance [distanceM]: 2·d·tan(AOV/2) = d·sensor/f. */
    fun fieldM(sensorDimMm: Double, focalMm: Double, distanceM: Double): Double = distanceM * sensorDimMm / focalMm

    fun compute(camera: Camera, focalMm: Double, distanceM: Double, orientation: Orientation, teleconverter: Double = 1.0): FovResult {
        val f = focalMm * teleconverter
        val (w, h) = camera.frameDimensions(orientation)
        val d = hypot(w, h)
        return FovResult(angleDeg(w, f), angleDeg(h, f), angleDeg(d, f), fieldM(w, f, distanceM), fieldM(h, f, distanceM), fieldM(d, f, distanceM))
    }

    private fun dim(camera: Camera, orientation: Orientation, which: FovDimension): Double {
        val (w, h) = camera.frameDimensions(orientation)
        return when (which) { FovDimension.HORIZONTAL -> w; FovDimension.VERTICAL -> h; FovDimension.DIAGONAL -> hypot(w, h) }
    }

    /** Inverse: focal length (mm, real) giving [fieldM] along [which] at [distanceM]. */
    fun focalForField(camera: Camera, orientation: Orientation, which: FovDimension, fieldM: Double, distanceM: Double, teleconverter: Double = 1.0): Double =
        dim(camera, orientation, which) * distanceM / fieldM / teleconverter

    /** Inverse: distance (m) giving [fieldM] along [which] with [focalMm]. */
    fun distanceForField(camera: Camera, orientation: Orientation, which: FovDimension, fieldM: Double, focalMm: Double, teleconverter: Double = 1.0): Double =
        fieldM * focalMm * teleconverter / dim(camera, orientation, which)

    /** Inverse: focal length (mm) giving the angle of view [angleDeg] along [which]. */
    fun focalForAngle(camera: Camera, orientation: Orientation, which: FovDimension, angleDeg: Double): Double =
        dim(camera, orientation, which) / (2 * tan(Math.toRadians(angleDeg) / 2))

    fun equivalentFocal35(camera: Camera, focalMm: Double): Double = focalMm * camera.cropFactor
}
