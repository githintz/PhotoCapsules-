package com.photocapsules.core.photo

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

enum class StopScale(val divisions: Int) { FULL(1), HALF(2), THIRD(3) }

/** Standard marked values used on camera dials. */
object Stops {
    val apertureFull = listOf(0.95, 1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0, 45.0, 64.0)
    val apertureHalf = listOf(0.95, 1.0, 1.2, 1.4, 1.7, 2.0, 2.4, 2.8, 3.3, 4.0, 4.8, 5.6, 6.7, 8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0, 27.0, 32.0, 38.0, 45.0, 54.0, 64.0)
    val apertureThird = listOf(0.95, 1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0, 36.0, 40.0, 45.0, 51.0, 57.0, 64.0)

    fun apertures(scale: StopScale) = when (scale) { StopScale.FULL -> apertureFull; StopScale.HALF -> apertureHalf; StopScale.THIRD -> apertureThird }

    /** Shutter speeds in seconds (1/8000 s … 30 s) plus long exposures up to 1 h. */
    val shutterThird: List<Double> = listOf(
        1/8000.0, 1/6400.0, 1/5000.0, 1/4000.0, 1/3200.0, 1/2500.0, 1/2000.0, 1/1600.0, 1/1250.0, 1/1000.0, 1/800.0, 1/640.0,
        1/500.0, 1/400.0, 1/320.0, 1/250.0, 1/200.0, 1/160.0, 1/125.0, 1/100.0, 1/80.0, 1/60.0, 1/50.0, 1/40.0, 1/30.0, 1/25.0,
        1/20.0, 1/15.0, 1/13.0, 1/10.0, 1/8.0, 1/6.0, 1/5.0, 1/4.0, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0,
        5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 90.0, 120.0, 180.0, 240.0, 300.0, 480.0, 600.0,
        900.0, 1200.0, 1800.0, 2700.0, 3600.0)
    val shutterHalf: List<Double> = listOf(
        1/8000.0, 1/6000.0, 1/4000.0, 1/3000.0, 1/2000.0, 1/1500.0, 1/1000.0, 1/750.0, 1/500.0, 1/350.0, 1/250.0, 1/180.0,
        1/125.0, 1/90.0, 1/60.0, 1/45.0, 1/30.0, 1/20.0, 1/15.0, 1/10.0, 1/8.0, 1/6.0, 1/4.0, 0.3, 0.5, 0.7, 1.0, 1.5, 2.0, 3.0,
        4.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 180.0, 240.0, 300.0, 480.0, 600.0, 900.0, 1200.0, 1800.0, 2700.0, 3600.0)
    val shutterFull: List<Double> = listOf(
        1/8000.0, 1/4000.0, 1/2000.0, 1/1000.0, 1/500.0, 1/250.0, 1/125.0, 1/60.0, 1/30.0, 1/15.0, 1/8.0, 1/4.0, 0.5, 1.0,
        2.0, 4.0, 8.0, 15.0, 30.0, 60.0, 120.0, 240.0, 480.0, 960.0, 1920.0, 3600.0)
    fun shutters(scale: StopScale) = when (scale) { StopScale.FULL -> shutterFull; StopScale.HALF -> shutterHalf; StopScale.THIRD -> shutterThird }

    val isoThird = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500,
        3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200, 64000, 80000, 102400, 204800, 409600)
    val isoFull = listOf(25, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600, 51200, 102400, 204800, 409600)
    val isoHalf = listOf(25, 35, 50, 70, 100, 140, 200, 280, 400, 560, 800, 1100, 1600, 2200, 3200, 4500, 6400, 9000, 12800, 18000, 25600, 36000, 51200, 72000, 102400, 204800, 409600)
    fun isos(scale: StopScale) = when (scale) { StopScale.FULL -> isoFull; StopScale.HALF -> isoHalf; StopScale.THIRD -> isoThird }

    /** Nearest value in a list on a logarithmic scale. */
    fun <T : Number> nearest(list: List<T>, value: Double): T = list.minBy { abs(log2(it.toDouble() / value)) }
}

/** Neutral density filter expressed in stops (also its filter factor and optical density). */
data class NdFilter(val name: String, val stops: Double) {
    val factor: Double get() = 2.0.pow(stops)
    val opticalDensity: Double get() = stops * log10of2
    companion object {
        private const val log10of2 = 0.30102999566398120
        val NONE = NdFilter("None", 0.0)
        val common: List<NdFilter> = listOf(
            NONE,
            NdFilter("ND2 (0.3)", 1.0), NdFilter("ND4 (0.6)", 2.0), NdFilter("ND8 (0.9)", 3.0), NdFilter("ND16 (1.2)", 4.0),
            NdFilter("ND32 (1.5)", 5.0), NdFilter("ND64 (1.8)", 6.0), NdFilter("ND128 (2.1)", 7.0), NdFilter("ND256 (2.4)", 8.0),
            NdFilter("ND500 (2.7)", 9.0), NdFilter("ND1000 (3.0)", 10.0), NdFilter("ND2000 (3.3)", 11.0), NdFilter("ND4000 (3.6)", 12.0),
            NdFilter("ND8000 (3.9)", 13.0), NdFilter("ND16000 (4.2)", 14.0), NdFilter("ND32000 (4.5)", 15.0), NdFilter("ND64000 (4.8)", 16.0),
            NdFilter("ND100000 (5.0)", 16.6), NdFilter("ND1000000 (6.0)", 20.0),
        )
    }
}

data class ExposureSettings(val aperture: Double, val shutterSeconds: Double, val iso: Double) {
    init {
        require(aperture > 0 && shutterSeconds > 0 && iso > 0) { "Exposure settings must be positive" }
    }
    /** Exposure value normalized to ISO 100 (EV100): log2(N²/t) − log2(ISO/100). */
    val ev100: Double get() = log2(aperture * aperture / shutterSeconds) - log2(iso / 100.0)
    /** Exposure value at the set ISO (camera "EV" with no ISO normalization). */
    val ev: Double get() = log2(aperture * aperture / shutterSeconds)
}

enum class ExposureTarget { SHUTTER, APERTURE, ISO }

object ExposureCalculator {
    /**
     * Reciprocity-law equivalent exposure. Given a correctly exposed test shot and two of the three
     * final parameters (+ ND filter stops added in the final shot), returns the full final settings.
     */
    fun equivalent(
        test: ExposureSettings,
        target: ExposureTarget,
        finalAperture: Double,
        finalShutterSeconds: Double,
        finalIso: Double,
        ndStops: Double = 0.0,
    ): ExposureSettings {
        val ndFactor = 2.0.pow(ndStops)
        // Light reaching sensor ∝ t·ISO / (N²·ndFactor) must equal test's t·ISO/N².
        val k = test.shutterSeconds * test.iso / (test.aperture * test.aperture)
        return when (target) {
            ExposureTarget.SHUTTER -> ExposureSettings(finalAperture, k * finalAperture * finalAperture * ndFactor / finalIso, finalIso)
            ExposureTarget.APERTURE -> ExposureSettings(sqrt(finalShutterSeconds * finalIso / (k * ndFactor)), finalShutterSeconds, finalIso)
            ExposureTarget.ISO -> ExposureSettings(finalAperture, finalShutterSeconds, k * finalAperture * finalAperture * ndFactor / finalShutterSeconds)
        }
    }

    /** Shutter speed for a scene of [ev100] at the given aperture and ISO. */
    fun shutterForEv(ev100: Double, aperture: Double, iso: Double): Double =
        aperture * aperture / 2.0.pow(ev100 + log2(iso / 100.0))
}

/** Typical EV100 values for common lighting situations (from standard exposure tables). */
object ExposureValueTable {
    data class Entry(val ev100: Double, val situation: String)
    val entries = listOf(
        Entry(16.0, "Bright sun on snow or sand"),
        Entry(15.0, "Full Moon itself (lunar disk)"),
        Entry(14.0, "Gibbous Moon itself"),
        Entry(13.0, "Quarter Moon itself"),
        Entry(12.0, "Crescent Moon itself"),
        Entry(15.0, "Bright or hazy sun, distinct shadows (sunny 16)"),
        Entry(14.0, "Weak hazy sun, soft shadows"),
        Entry(13.0, "Cloudy bright, no shadows"),
        Entry(12.0, "Heavy overcast"),
        Entry(11.0, "Open shade / sunsets"),
        Entry(10.0, "Landscapes just after sunset"),
        Entry(9.0, "Neon and bright signs, floodlit stadiums"),
        Entry(8.0, "Brightly lit street scenes, Las Vegas"),
        Entry(7.0, "Bright interiors, night sports venues"),
        Entry(6.0, "Home interiors, brightly lit"),
        Entry(5.0, "Night street scenes, holiday lights"),
        Entry(4.0, "Floodlit buildings, candle-lit close-ups"),
        Entry(3.0, "Fireworks (bulb), distant city lights"),
        Entry(2.0, "Lightning (bulb), distant lit buildings"),
        Entry(1.0, "Distant skylines at night"),
        Entry(0.0, "Dim ambient interior light"),
        Entry(-1.0, "Night landscape under full Moon (bright)"),
        Entry(-2.0, "Night landscape under full Moon"),
        Entry(-3.0, "Moonlit snow scenes / landscape under quarter Moon"),
        Entry(-4.0, "Night landscape under crescent Moon"),
        Entry(-5.0, "Night landscape without Moon, city glow"),
        Entry(-6.0, "Night landscape without Moon (Milky Way)"),
        Entry(-7.0, "Milky Way, very dark sky"),
        Entry(-8.0, "Aurora (faint), deep-sky starfields"),
    )
}
