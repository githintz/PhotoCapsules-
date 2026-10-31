package com.photocapsules.core.photo

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** Spot Stars: maximum exposure before star trailing becomes visible. */
object SpotStars {
    enum class Accuracy { DEFAULT, ACCURATE }

    data class Result(val rule500Seconds: Double, val npfSeconds: Double?, val pixelPitchUm: Double?)

    /** 500 rule: t = 500 / (crop × f). */
    fun rule500(cropFactor: Double, focalMm: Double): Double = 500.0 / (cropFactor * focalMm)

    /**
     * NPF rule (F. Michaud, Société Astronomique du Havre).
     * Default = simplified rule t = (35·N + 30·p) / f, Accurate = full rule with k = 1:
     * t = (16.856·N + 0.0997·f + 13.713·p) / f. Both divided by cos(δ) for minimum declination δ.
     */
    fun npf(aperture: Double, focalMm: Double, pixelPitchUm: Double, declinationDeg: Double, accuracy: Accuracy): Double {
        val base = when (accuracy) {
            Accuracy.DEFAULT -> (35.0 * aperture + 30.0 * pixelPitchUm) / focalMm
            Accuracy.ACCURATE -> (16.856 * aperture + 0.0997 * focalMm + 13.713 * pixelPitchUm) / focalMm
        }
        val c = cos(Math.toRadians(declinationDeg.coerceIn(-89.99, 89.99)))
        return base / c
    }

    fun compute(camera: Camera, focalMm: Double, aperture: Double, declinationDeg: Double, accuracy: Accuracy): Result {
        val p = camera.pixelPitchUm
        return Result(rule500(camera.cropFactor, focalMm), p?.let { npf(aperture, focalMm, it, declinationDeg, accuracy) }, p)
    }
}

/** Star Trails: rotation angle ⇄ exposure time (sidereal rate). */
object StarTrails {
    const val SIDEREAL_DAY_SECONDS = 86164.0905
    fun angleForExposure(seconds: Double): Double = 360.0 * seconds / SIDEREAL_DAY_SECONDS
    fun exposureForAngle(angleDeg: Double): Double = angleDeg * SIDEREAL_DAY_SECONDS / 360.0
    /** Degrees per hour (≈ 15.041°/h). */
    val degreesPerHour: Double get() = 360.0 * 3600.0 / SIDEREAL_DAY_SECONDS
}

/** Time-lapse arithmetic. */
object TimeLapse {
    enum class Target { INTERVAL, CLIP_LENGTH, EVENT_DURATION }

    data class Result(
        val intervalSeconds: Double,
        val clipLengthSeconds: Double,
        val eventDurationSeconds: Double,
        val fps: Double,
        val photos: Long,
        val memoryMb: Double,
    )

    fun compute(
        target: Target,
        intervalSeconds: Double,
        clipLengthSeconds: Double,
        eventDurationSeconds: Double,
        fps: Double,
        imageSizeMb: Double,
    ): Result {
        require(fps > 0) { "fps must be positive" }
        val interval: Double; val clip: Double; val duration: Double
        when (target) {
            Target.INTERVAL -> { clip = clipLengthSeconds; duration = eventDurationSeconds; interval = duration / (clip * fps) }
            Target.CLIP_LENGTH -> { interval = intervalSeconds; duration = eventDurationSeconds; clip = duration / interval / fps }
            Target.EVENT_DURATION -> { interval = intervalSeconds; clip = clipLengthSeconds; duration = clip * fps * interval }
        }
        val photos = Math.round(clip * fps)
        return Result(interval, clip, duration, fps, photos, photos * imageSizeMb)
    }

    data class IntervalHint(val event: String, val interval: String)
    val intervalTable = listOf(
        IntervalHint("Fast-moving clouds", "1 – 3 s"),
        IntervalHint("Slow-moving clouds", "5 – 10 s"),
        IntervalHint("Sunrise / sunset", "2 – 5 s"),
        IntervalHint("Day-to-night transition (holy grail)", "5 – 15 s"),
        IntervalHint("Moving shadows", "15 – 30 s"),
        IntervalHint("Stars / Milky Way", "20 – 30 s"),
        IntervalHint("Star trails sequence", "exposure + 1 s"),
        IntervalHint("Fast traffic, crowds", "0.5 – 2 s"),
        IntervalHint("Slow people / city life", "2 – 5 s"),
        IntervalHint("Boats / ships", "2 – 10 s"),
        IntervalHint("Tides", "30 s – 1 min"),
        IntervalHint("Flowers blooming / plants growing", "5 – 30 min"),
        IntervalHint("Construction projects", "5 – 15 min"),
        IntervalHint("Seasons", "1 day"),
    )
}

/** Minimum subject distance to fit a subject in the frame (thin lens: u = f·(1 + S/s)). */
object SubjectDistance {
    data class Result(val distanceM: Double, val limitingDimension: FovDimension, val fieldWidthM: Double, val fieldHeightM: Double)

    fun compute(camera: Camera, focalMm: Double, subjectWidthM: Double, subjectHeightM: Double, orientation: Orientation, teleconverter: Double = 1.0): Result {
        val f = focalMm * teleconverter
        val (w, h) = camera.frameDimensions(orientation)
        val dW = f * (1 + subjectWidthM * 1000.0 / w) / 1000.0
        val dH = f * (1 + subjectHeightM * 1000.0 / h) / 1000.0
        val d = max(dW, dH)
        val m = f / (d * 1000.0 - f) // magnification
        return Result(d, if (dW >= dH) FovDimension.HORIZONTAL else FovDimension.VERTICAL, w / m / 1000.0, h / m / 1000.0)
    }
}

/** Focal length & aperture match between two sensors (equivalence): same AOV and DoF from the same position. */
object FocalLengthMatch {
    data class Side(val camera: Camera, val focalMm: Double, val aperture: Double, val fov: FovResult, val dof: DofResult)
    data class Result(val camera1: Side, val camera2: Side, val ratio: Double)

    fun compute(camera1: Camera, camera2: Camera, focalMm: Double, aperture: Double, distanceM: Double): Result {
        val ratio = camera2.diagonalMm / camera1.diagonalMm
        val f2 = focalMm * ratio
        val n2 = aperture * ratio
        fun side(c: Camera, f: Double, n: Double) = Side(
            c, f, n, FieldOfView.compute(c, f, distanceM, Orientation.LANDSCAPE),
            DepthOfField.compute(DofInput(f, n, distanceM, c.circleOfConfusionMm)),
        )
        return Result(side(camera1, focalMm, aperture), side(camera2, f2, n2), ratio)
    }
}

/** Depth-of-field and hyperfocal tables. */
object DofTables {
    val defaultDistancesM = listOf(0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 15.0, 20.0, 30.0, 50.0, 100.0)
    val defaultFocalsMm = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 35.0, 50.0, 70.0, 85.0, 100.0, 135.0, 200.0, 300.0, 400.0, 600.0)

    /** rows = distances, columns = apertures. Cells where focus ≤ focal length are null. */
    fun dofTable(camera: Camera, focalMm: Double, apertures: List<Double>, distancesM: List<Double> = defaultDistancesM): List<List<DofResult?>> =
        distancesM.map { s ->
            apertures.map { n ->
                if (s * 1000 <= focalMm) null else DepthOfField.compute(DofInput(focalMm, n, s, camera.circleOfConfusionMm))
            }
        }

    /** rows = focal lengths, columns = apertures; values = hyperfocal distance (m). */
    fun hyperfocalTable(camera: Camera, apertures: List<Double>, focalsMm: List<Double> = defaultFocalsMm): List<List<Double>> =
        focalsMm.map { f -> apertures.map { n -> DepthOfField.hyperfocalM(f, n, camera.circleOfConfusionMm) } }
}

/** Drone footprint (camera pointing straight down). */
object DroneFrame {
    data class Result(val widthM: Double, val heightM: Double, val areaM2: Double)
    fun footprint(camera: Camera, focalMm: Double, altitudeM: Double, orientation: Orientation): Result {
        val (w, h) = camera.frameDimensions(orientation)
        val fw = altitudeM * w / focalMm
        val fh = altitudeM * h / focalMm
        return Result(fw, fh, fw * fh)
    }
    /** Altitude giving a footprint of [widthM] (across the frame width). */
    fun altitudeForWidth(camera: Camera, focalMm: Double, widthM: Double, orientation: Orientation): Double {
        val (w, _) = camera.frameDimensions(orientation)
        return widthM * focalMm / w
    }
}

internal fun diag(w: Double, h: Double) = hypot(w, h)
