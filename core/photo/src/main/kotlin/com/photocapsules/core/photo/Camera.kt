package com.photocapsules.core.photo

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Diagonal of the 35 mm "full frame" format (36 × 24 mm). */
const val FULL_FRAME_DIAGONAL_MM = 43.26661530556787

/**
 * A camera body (or generic sensor format).
 * @property cocMm optional explicit circle of confusion; by default diagonal/1442 rounded to 0.001 mm
 * (0.030 mm for full frame, 0.019/0.020 mm for APS-C), the convention used by PhotoPills and DOFMaster.
 */
@Serializable
data class Camera(
    val make: String,
    val model: String,
    val sensorWidthMm: Double,
    val sensorHeightMm: Double,
    val pixelsWide: Int? = null,
    val pixelsHigh: Int? = null,
    val megapixels: Double? = null,
    val cocMm: Double? = null,
    val fixedFocalMm: Double? = null,
) {
    val name: String get() = if (make.isBlank()) model else "$make $model"
    val diagonalMm: Double get() = hypot(sensorWidthMm, sensorHeightMm)
    val cropFactor: Double get() = FULL_FRAME_DIAGONAL_MM / diagonalMm
    val circleOfConfusionMm: Double get() = cocMm ?: defaultCoc(diagonalMm)

    /** Horizontal pixel count, derived from megapixels and aspect ratio when not given explicitly. */
    val horizontalPixels: Int?
        get() = pixelsWide ?: megapixels?.let { sqrt(it * 1e6 * sensorWidthMm / sensorHeightMm).roundToInt() }

    /** Pixel pitch in micrometres (sensor width / horizontal pixels). */
    val pixelPitchUm: Double?
        get() = horizontalPixels?.let { sensorWidthMm * 1000.0 / it }

    val effectiveMegapixels: Double?
        get() = megapixels ?: if (pixelsWide != null && pixelsHigh != null) pixelsWide.toDouble() * pixelsHigh / 1e6 else null

    companion object {
        fun defaultCoc(diagonalMm: Double): Double = Math.round(diagonalMm / 1442.0 * 1000.0) / 1000.0
        val FULL_FRAME_24MP = Camera("", "Full frame (36×24) 24 MP", 36.0, 24.0, 6000, 4000)
    }
}

enum class Orientation { LANDSCAPE, PORTRAIT }

/** Sensor dimensions for an orientation: (width, height) as seen in the frame. */
fun Camera.frameDimensions(orientation: Orientation): Pair<Double, Double> =
    if (orientation == Orientation.LANDSCAPE) sensorWidthMm to sensorHeightMm else sensorHeightMm to sensorWidthMm
