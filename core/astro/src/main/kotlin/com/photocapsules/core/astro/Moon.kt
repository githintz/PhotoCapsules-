package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.siderealTime
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class MoonPhaseName(val label: String) {
    NEW("New Moon"), WAXING_CRESCENT("Waxing crescent"), FIRST_QUARTER("First quarter"), WAXING_GIBBOUS("Waxing gibbous"),
    FULL("Full Moon"), WANING_GIBBOUS("Waning gibbous"), LAST_QUARTER("Last quarter"), WANING_CRESCENT("Waning crescent");

    companion object {
        /** Principal phases are named within ±12 h of the exact instant (≈ ±6.1° of phase angle). */
        private const val HALF_WINDOW = 360.0 / 29.530588 * 0.5
        fun forPhaseAngle(phase: Double): MoonPhaseName {
            val p = normalizeDegrees(phase)
            fun near(c: Double) = kotlin.math.abs(angleDiff(p, c)) <= HALF_WINDOW
            return when {
                near(0.0) -> NEW
                near(90.0) -> FIRST_QUARTER
                near(180.0) -> FULL
                near(270.0) -> LAST_QUARTER
                p < 90 -> WAXING_CRESCENT
                p < 180 -> WAXING_GIBBOUS
                p < 270 -> WANING_GIBBOUS
                else -> WANING_CRESCENT
            }
        }
    }
}

data class MoonInfo(
    /** Moon–Sun ecliptic elongation of date: 0 = new, 90 = first quarter, 180 = full, 270 = last quarter. */
    val phaseAngle: Double,
    /** Illuminated fraction of the disc, 0..1. */
    val illumination: Double,
    val phase: MoonPhaseName,
    val waxing: Boolean,
    /** Days since the previous new Moon. */
    val ageDays: Double,
    val position: BodyPosition,
    /** Position angle of the bright limb's midpoint, degrees east of celestial north (Meeus 48.5). */
    val brightLimbAngle: Double,
    /** Parallactic angle at the Moon (Meeus 14.1). */
    val parallacticAngle: Double,
) {
    /**
     * Rotation (degrees, clockwise on screen) of the bright limb direction measured from the observer's zenith;
     * 0 = lit side points straight up, 90 = lit side to the right. Used to draw the Moon as seen in the sky.
     */
    val brightLimbFromZenith: Double get() = normalizeDegrees(brightLimbAngle - parallacticAngle)
}

object MoonCalc {
    const val SYNODIC_MONTH = 29.530588853

    fun info(instant: Instant, where: GeoPoint): MoonInfo {
        val t = instant.toAstroTime()
        val pos = Sky.position(SkyBody.MOON, t, where)
        val sun = Sky.position(SkyBody.SUN, t, where)
        val phase = moonPhase(t)
        val illum = illumination(Body.Moon, t).phaseFraction
        // Previous new Moon: search forward from slightly before the expected time.
        val expectedAge = phase / 360.0 * SYNODIC_MONTH
        val newMoon = searchMoonPhase(0.0, t.addDays(-expectedAge - 2.0), 4.0)
            ?.let { if (it.ut > t.ut) searchMoonPhase(0.0, t.addDays(-expectedAge - 32.0), 4.0) else it }
        val age = newMoon?.let { t.ut - it.ut } ?: expectedAge
        val a0 = Math.toRadians(sun.raHours * 15); val d0 = Math.toRadians(sun.decDeg)
        val a = Math.toRadians(pos.raHours * 15); val d = Math.toRadians(pos.decDeg)
        val chi = Math.toDegrees(atan2(cos(d0) * sin(a0 - a), sin(d0) * cos(d) - cos(d0) * sin(d) * cos(a0 - a)))
        // Local hour angle of the Moon.
        val lst = siderealTime(t) * 15.0 + where.longitude
        val h = Math.toRadians(lst - pos.raHours * 15)
        val phi = Math.toRadians(where.latitude)
        val q = Math.toDegrees(atan2(sin(h), tan(phi) * cos(d) - sin(d) * cos(h)))
        return MoonInfo(
            phaseAngle = phase,
            illumination = illum,
            phase = MoonPhaseName.forPhaseAngle(phase),
            waxing = phase < 180.0,
            ageDays = age,
            position = pos,
            brightLimbAngle = normalizeDegrees(chi),
            parallacticAngle = q,
        )
    }
}
