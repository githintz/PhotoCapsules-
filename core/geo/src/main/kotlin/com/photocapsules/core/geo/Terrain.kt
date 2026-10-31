package com.photocapsules.core.geo

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Line-of-sight geometry between two points on the Earth, accounting for Earth curvature and
 * standard terrestrial refraction (coefficient k ≈ 0.13).
 */
object Terrain {
    const val STANDARD_REFRACTION_K = 0.13

    /**
     * Apparent elevation angle (degrees) of a target at [distanceMeters] (along the surface) whose height
     * above sea level is [targetHeight], seen from an observer at [observerHeight].
     * With [refractionK] = 0 this is the pure geometric angle on a sphere.
     */
    fun elevationAngle(
        distanceMeters: Double,
        observerHeight: Double,
        targetHeight: Double,
        refractionK: Double = STANDARD_REFRACTION_K,
    ): Double {
        if (distanceMeters <= 0.0) return if (targetHeight >= observerHeight) 90.0 else -90.0
        // Effective radius model: refraction bends rays with curvature k/R → treat Earth radius as R/(1-k).
        val r = Geodesy.EARTH_MEAN_RADIUS / (1.0 - refractionK)
        val theta = distanceMeters / r // central angle
        val r1 = r + observerHeight
        val r2 = r + targetHeight
        // Observer at (0, r1); target at (r2 sinθ, r2 cosθ). Elevation is angle above local horizontal.
        val dx = r2 * kotlin.math.sin(theta)
        val dy = r2 * kotlin.math.cos(theta) - r1
        return atan2(dy, dx) * 180.0 / PI
    }

    /** Geometric dip of the sea horizon (degrees) for an observer [heightMeters] above it, no refraction. */
    fun geometricHorizonDip(heightMeters: Double): Double {
        if (heightMeters <= 0) return 0.0
        val r = Geodesy.EARTH_MEAN_RADIUS
        return acos(r / (r + heightMeters)) * 180.0 / PI
    }

    /** Apparent dip including standard refraction (navigation formula 1.76′·√h[m]). */
    fun apparentHorizonDip(heightMeters: Double): Double =
        if (heightMeters <= 0) 0.0 else 1.76 * sqrt(heightMeters) / 60.0

    /** Distance (m) to the geometric horizon. */
    fun distanceToHorizon(heightMeters: Double, refractionK: Double = 0.0): Double {
        if (heightMeters <= 0) return 0.0
        val r = Geodesy.EARTH_MEAN_RADIUS / (1 - refractionK)
        return sqrt(2 * r * heightMeters + heightMeters * heightMeters)
    }

    /** Linear size (m) subtended by [angularDiameterDeg] at [distanceMeters]. */
    fun linearSize(angularDiameterDeg: Double, distanceMeters: Double): Double =
        2 * distanceMeters * tan(angularDiameterDeg * PI / 360.0)

    /** Distance (m) at which an object of [sizeMeters] subtends [angularDiameterDeg]. */
    fun distanceForSize(angularDiameterDeg: Double, sizeMeters: Double): Double =
        sizeMeters / (2 * tan(angularDiameterDeg * PI / 360.0))

    /** Angular size (deg) of an object of [sizeMeters] at [distanceMeters]. */
    fun angularSize(sizeMeters: Double, distanceMeters: Double): Double =
        2 * atan(sizeMeters / (2 * distanceMeters)) * 180.0 / PI

    /**
     * Height above the Black-Pin ground of a ray from the observer at elevation [bodyElevationDeg]
     * when it passes over a point at [distanceMeters], including curvature + refraction.
     * Returns meters above the target ground (negative = body is below the terrain there).
     */
    fun rayHeightAboveTarget(
        distanceMeters: Double,
        observerHeight: Double,
        targetGroundHeight: Double,
        bodyElevationDeg: Double,
        refractionK: Double = STANDARD_REFRACTION_K,
    ): Double {
        val r = Geodesy.EARTH_MEAN_RADIUS / (1.0 - refractionK)
        val theta = distanceMeters / r
        val r1 = r + observerHeight
        val e = bodyElevationDeg * PI / 180.0
        // Ray from (0, r1) with direction (cos e, sin e); intersect with radial line at angle theta.
        // Radial line: points t*(sin θ, cos θ). Solve r1 + s sin e = t cos θ, s cos e = t sin θ.
        val denom = kotlin.math.cos(theta) * kotlin.math.cos(e) - kotlin.math.sin(theta) * kotlin.math.sin(e)
        if (denom <= 0) return Double.POSITIVE_INFINITY // ray never comes back over that point (goes to space)
        val t = r1 * kotlin.math.cos(e) / denom
        return t - (r + targetGroundHeight)
    }
}
