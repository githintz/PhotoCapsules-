package com.photocapsules.core.photo

/**
 * Thin-lens depth of field (the standard formulas used by PhotoPills/DOFMaster).
 * All distances in metres in the public API; lens focal length in millimetres.
 */
data class DofInput(
    val focalMm: Double,
    val aperture: Double,
    val focusDistanceM: Double,
    val cocMm: Double,
    val teleconverter: Double = 1.0,
) {
    val effectiveFocalMm get() = focalMm * teleconverter
    val effectiveAperture get() = aperture * teleconverter
}

data class DofResult(
    val hyperfocalM: Double,
    /** Near limit of DoF when focused at the hyperfocal distance (= H/2). */
    val hyperfocalNearLimitM: Double,
    val nearLimitM: Double,
    /** Far limit (∞ when focused at/after the hyperfocal distance). */
    val farLimitM: Double,
    val totalM: Double,
    val inFrontM: Double,
    val behindM: Double,
) {
    val isFarInfinite: Boolean get() = farLimitM.isInfinite()
}

object DepthOfField {
    /** Hyperfocal distance in metres: H = f²/(N·c) + f. */
    fun hyperfocalM(focalMm: Double, aperture: Double, cocMm: Double): Double =
        (focalMm * focalMm / (aperture * cocMm) + focalMm) / 1000.0

    fun compute(input: DofInput): DofResult {
        val f = input.effectiveFocalMm
        val n = input.effectiveAperture
        require(f > 0 && n > 0 && input.cocMm > 0) { "Invalid DoF input" }
        val h = f * f / (n * input.cocMm) + f // mm
        val s = input.focusDistanceM * 1000.0
        require(s > f) { "Focus distance must exceed focal length" }
        val near = s * (h - f) / (h + s - 2 * f)
        val far = if (s >= h) Double.POSITIVE_INFINITY else s * (h - f) / (h - s)
        val nearM = near / 1000.0
        val farM = far / 1000.0
        return DofResult(
            hyperfocalM = h / 1000.0,
            hyperfocalNearLimitM = h / 2000.0,
            nearLimitM = nearM,
            farLimitM = farM,
            totalM = farM - nearM,
            inFrontM = input.focusDistanceM - nearM,
            behindM = farM - input.focusDistanceM,
        )
    }

    // ---------- Inverse ----------

    /** Focus distance (m) giving the [targetTotalM] total depth of field, or null if unreachable (target ≥ ∞ region). */
    fun distanceForTotalDof(focalMm: Double, aperture: Double, cocMm: Double, targetTotalM: Double, teleconverter: Double = 1.0): Double? {
        val f = focalMm * teleconverter
        val hM = hyperfocalM(f, aperture * teleconverter, cocMm)
        var lo = f / 1000.0 * 1.0001
        var hi = hM * (1 - 1e-12)
        val total = { s: Double -> compute(DofInput(focalMm, aperture, s, cocMm, teleconverter)).totalM }
        if (targetTotalM <= total(lo)) return null
        if (targetTotalM >= total(hi)) return null
        repeat(200) {
            val mid = (lo + hi) / 2
            if (total(mid) < targetTotalM) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** Aperture (f-number, before teleconverter) giving [targetTotalM] at [focusM], or null if unreachable. */
    fun apertureForTotalDof(focalMm: Double, focusM: Double, cocMm: Double, targetTotalM: Double, teleconverter: Double = 1.0): Double? {
        val total = { n: Double -> compute(DofInput(focalMm, n, focusM, cocMm, teleconverter)).totalM }
        var lo = 0.5; var hi = 256.0
        if (focusM * 1000 <= focalMm * teleconverter) return null
        if (targetTotalM <= total(lo) || targetTotalM >= total(hi) && total(hi).isFinite()) return null
        repeat(200) {
            val mid = Math.sqrt(lo * hi)
            val t = total(mid)
            if (t < targetTotalM) lo = mid else hi = mid
        }
        return Math.sqrt(lo * hi)
    }

    /** Focal length (mm, before teleconverter) giving [targetTotalM] at [focusM], or null if unreachable. */
    fun focalForTotalDof(aperture: Double, focusM: Double, cocMm: Double, targetTotalM: Double, teleconverter: Double = 1.0): Double? {
        val total = { f: Double -> compute(DofInput(f, aperture, focusM, cocMm, teleconverter)).totalM }
        var lo = 1.0
        var hi = minOf(4000.0, focusM * 1000.0 / teleconverter * 0.999)
        if (hi <= lo) return null
        // DoF decreases as focal length increases.
        val tLo = total(lo); val tHi = total(hi)
        if (!(targetTotalM < tLo || tLo.isInfinite()) || targetTotalM <= tHi) return null
        repeat(200) {
            val mid = Math.sqrt(lo * hi)
            if (total(mid) > targetTotalM) lo = mid else hi = mid
        }
        return Math.sqrt(lo * hi)
    }
}

/**
 * Circle of confusion from print size, viewing distance and visual acuity ("Advanced DoF").
 * Default values (8×10 in print, 25 cm viewing, manufacturer standard acuity) reproduce diagonal/1442.
 */
object CircleOfConfusion {
    enum class Acuity(val label: String, val blurAt25cmMm: Double) {
        MANUFACTURER_STANDARD("Manufacturer standard", 325.2691 / 1442.0),
        NORMAL("Normal (5 lp/mm)", 0.2),
        GOOD("Good (6 lp/mm)", 1.0 / 6.0),
        EXCELLENT("Excellent (8 lp/mm)", 0.125),
    }
    const val DEFAULT_PRINT_WIDTH_M = 0.254   // 10 in
    const val DEFAULT_PRINT_HEIGHT_M = 0.2032 // 8 in
    const val DEFAULT_VIEWING_DISTANCE_M = 0.25

    fun compute(
        sensorDiagonalMm: Double,
        printWidthM: Double = DEFAULT_PRINT_WIDTH_M,
        printHeightM: Double = DEFAULT_PRINT_HEIGHT_M,
        viewingDistanceM: Double = DEFAULT_VIEWING_DISTANCE_M,
        acuity: Acuity = Acuity.MANUFACTURER_STANDARD,
    ): Double {
        val printDiagMm = Math.hypot(printWidthM, printHeightM) * 1000.0
        val blurOnPrint = acuity.blurAt25cmMm * (viewingDistanceM / 0.25)
        return blurOnPrint * sensorDiagonalMm / printDiagMm
    }
}
