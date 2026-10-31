package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.rotationEqjEcl
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sin

/**
 * Meteor shower data: IMO Working List of Visual Meteor Showers (IMO 2026 Meteor Shower Calendar, Table 5).
 * Solar longitudes are J2000.0. Radiant drift in degrees/day (typical IMO values). [profileB] = activity
 * profile slope (Jenniskens 1994): ZHR(λ) = ZHRmax·10^(−B·|λ−λmax|).
 */
data class MeteorShower(
    val code: String,
    val name: String,
    val activityStart: MonthDay,
    val activityEnd: MonthDay,
    val peakSolarLongitude: Double,
    val raDeg: Double,
    val decDeg: Double,
    val velocityKmS: Double,
    val populationIndex: Double,
    val zhr: Double,
    val variable: Boolean = false,
    val showerClass: Int,
    val driftRa: Double = 0.0,
    val driftDec: Double = 0.0,
    val profileB: Double = 0.2,
    val daytime: Boolean = false,
)

data class ShowerOccurrence(
    val shower: MeteorShower,
    val activityStart: LocalDate,
    val activityEnd: LocalDate,
    val peak: Instant,
)

object MeteorShowers {
    private fun md(m: Int, d: Int) = MonthDay.of(m, d)

    val all: List<MeteorShower> = listOf(
        MeteorShower("QUA", "Quadrantids", md(12, 28), md(1, 12), 283.15, 230.0, 49.0, 41.0, 2.1, 80.0, showerClass = 1, driftRa = 0.8, driftDec = -0.2, profileB = 2.5),
        MeteorShower("GUM", "γ-Ursae Minorids", md(1, 10), md(1, 22), 298.0, 228.0, 67.0, 31.0, 3.0, 3.0, showerClass = 4),
        MeteorShower("ACE", "α-Centaurids", md(1, 31), md(2, 20), 319.4, 211.0, -58.0, 58.0, 2.0, 6.0, showerClass = 2, driftRa = 1.0, driftDec = -0.3),
        MeteorShower("LYR", "April Lyrids", md(4, 14), md(4, 30), 32.32, 271.0, 34.0, 49.0, 2.1, 18.0, showerClass = 1, driftRa = 1.1, profileB = 0.22),
        MeteorShower("PPU", "π-Puppids", md(4, 15), md(4, 28), 33.5, 110.0, -45.0, 18.0, 2.0, 5.0, variable = true, showerClass = 3, driftRa = 0.5),
        MeteorShower("ETA", "η-Aquariids", md(4, 19), md(5, 28), 45.5, 338.0, -1.0, 66.0, 2.4, 50.0, showerClass = 1, driftRa = 0.9, driftDec = 0.4, profileB = 0.08),
        MeteorShower("ELY", "η-Lyrids", md(5, 3), md(5, 14), 50.0, 291.0, 43.0, 43.0, 3.0, 3.0, showerClass = 4),
        MeteorShower("ARI", "Daytime Arietids", md(5, 14), md(6, 24), 76.7, 43.0, 24.0, 38.0, 2.8, 30.0, showerClass = 2, driftRa = 1.0, daytime = true, profileB = 0.1),
        MeteorShower("JBO", "June Bootids", md(6, 22), md(7, 2), 90.3, 221.0, 48.0, 18.0, 2.2, 5.0, variable = true, showerClass = 3),
        MeteorShower("JPE", "July Pegasids", md(7, 1), md(7, 20), 108.0, 347.0, 11.0, 63.0, 2.0, 3.0, showerClass = 4, driftRa = 0.8, driftDec = 0.2),
        MeteorShower("GDR", "July γ-Draconids", md(7, 25), md(7, 31), 125.13, 280.0, 51.0, 27.0, 3.0, 5.0, showerClass = 4),
        MeteorShower("SDA", "Southern δ-Aquariids", md(7, 12), md(8, 23), 128.0, 340.0, -16.0, 41.0, 2.5, 25.0, showerClass = 1, driftRa = 0.8, driftDec = 0.2, profileB = 0.091),
        MeteorShower("CAP", "α-Capricornids", md(7, 13), md(8, 15), 128.0, 307.0, -10.0, 23.0, 2.5, 5.0, showerClass = 2, driftRa = 0.9, driftDec = 0.3, profileB = 0.1),
        MeteorShower("ERI", "η-Eridanids", md(7, 31), md(8, 19), 135.0, 41.0, -11.0, 64.0, 3.0, 3.0, showerClass = 4, driftRa = 0.9),
        MeteorShower("PER", "Perseids", md(7, 17), md(8, 24), 140.0, 48.0, 58.0, 59.0, 2.2, 100.0, showerClass = 1, driftRa = 1.35, driftDec = 0.12, profileB = 0.2),
        MeteorShower("KCG", "κ-Cygnids", md(8, 3), md(8, 28), 144.0, 286.0, 59.0, 23.0, 3.0, 3.0, showerClass = 4, driftRa = 0.3, driftDec = 0.1),
        MeteorShower("AUR", "Aurigids", md(8, 28), md(9, 5), 158.6, 91.0, 39.0, 66.0, 2.5, 6.0, showerClass = 2, driftRa = 1.1, profileB = 0.5),
        MeteorShower("SPE", "September ε-Perseids", md(9, 5), md(9, 21), 166.7, 48.0, 40.0, 64.0, 2.5, 8.0, showerClass = 2, driftRa = 1.1, driftDec = 0.1),
        MeteorShower("SLY", "September Lyncids", md(9, 10), md(10, 8), 170.0, 113.0, 56.0, 60.0, 3.0, 3.0, showerClass = 4),
        MeteorShower("DSX", "Daytime Sextantids", md(9, 20), md(10, 6), 188.0, 156.0, -2.0, 32.0, 2.5, 5.0, showerClass = 4, daytime = true, driftRa = 0.7, driftDec = -0.4),
        MeteorShower("OCT", "October Camelopardalids", md(10, 5), md(10, 6), 192.58, 164.0, 79.0, 47.0, 2.5, 5.0, variable = true, showerClass = 3, profileB = 1.0),
        MeteorShower("DRA", "Draconids", md(10, 6), md(10, 10), 195.4, 263.0, 56.0, 20.0, 2.6, 5.0, variable = true, showerClass = 3, profileB = 1.0),
        MeteorShower("EGE", "ε-Geminids", md(10, 14), md(10, 27), 205.0, 102.0, 27.0, 70.0, 3.0, 3.0, showerClass = 4, driftRa = 0.8),
        MeteorShower("ORI", "Orionids", md(10, 2), md(11, 7), 208.0, 95.0, 16.0, 66.0, 2.5, 20.0, showerClass = 1, driftRa = 0.7, driftDec = 0.1, profileB = 0.12),
        MeteorShower("EOR", "ε-Orionids", md(10, 2), md(11, 2), 208.0, 92.0, 13.0, 66.0, 2.5, 3.0, showerClass = 4, driftRa = 0.7),
        MeteorShower("STA", "Southern Taurids", md(9, 20), md(11, 20), 223.0, 52.0, 15.0, 27.0, 2.3, 5.0, showerClass = 2, driftRa = 0.8, driftDec = 0.3, profileB = 0.03),
        MeteorShower("NTA", "Northern Taurids", md(10, 20), md(12, 10), 230.0, 58.0, 22.0, 29.0, 2.3, 5.0, showerClass = 2, driftRa = 0.9, driftDec = 0.2, profileB = 0.03),
        MeteorShower("LEO", "Leonids", md(11, 6), md(11, 30), 235.27, 152.0, 22.0, 71.0, 2.5, 15.0, showerClass = 1, driftRa = 0.7, driftDec = -0.4, profileB = 0.4),
        MeteorShower("LMI", "Leonis Minorids", md(10, 19), md(12, 5), 238.0, 162.0, 37.0, 62.0, 3.2, 2.0, showerClass = 4, driftRa = 0.9, driftDec = -0.4),
        MeteorShower("AMO", "α-Monocerotids", md(11, 15), md(11, 25), 239.5, 118.0, 1.0, 60.0, 2.4, 5.0, variable = true, showerClass = 3, profileB = 1.0),
        MeteorShower("NOR", "November Orionids", md(11, 14), md(12, 6), 246.0, 88.0, 15.0, 44.0, 2.5, 3.0, showerClass = 4, driftRa = 0.9),
        MeteorShower("PHO", "Phoenicids", md(11, 28), md(12, 9), 250.5, 18.0, -47.0, 18.0, 2.8, 5.0, variable = true, showerClass = 3),
        MeteorShower("PUP", "Puppid-Velids", md(12, 1), md(12, 15), 255.0, 123.0, -45.0, 44.0, 2.9, 10.0, showerClass = 2),
        MeteorShower("MON", "Monocerotids", md(11, 27), md(12, 17), 257.0, 100.0, 8.0, 41.0, 3.0, 3.0, showerClass = 4, driftRa = 0.8),
        MeteorShower("HYD", "σ-Hydrids", md(12, 3), md(12, 20), 257.0, 125.0, 2.0, 58.0, 3.0, 7.0, showerClass = 2, driftRa = 0.7, driftDec = -0.2),
        MeteorShower("GEM", "Geminids", md(12, 4), md(12, 20), 262.2, 112.0, 33.0, 35.0, 2.6, 150.0, showerClass = 1, driftRa = 1.0, driftDec = -0.1, profileB = 0.39),
        MeteorShower("URS", "Ursids", md(12, 17), md(12, 26), 270.7, 217.0, 76.0, 27.0, 2.9, 10.0, showerClass = 1, profileB = 0.9),
        // IMO 2026: "summarise all meteors from the (extended) COM/DLM-region under COM" – no significant activity variation.
        // Drift fitted to IMO 2026 Table 6 (COM: Jan 0 172°/+25° … Jan 30 198°/+16°); detectable until Jan 31.
        MeteorShower("COM", "Comae Berenicids (incl. Dec. Leonis Minorids)", md(12, 4), md(1, 31), 271.0, 164.0, 29.0, 65.0, 3.0, 3.0, showerClass = 4, driftRa = 0.9, driftDec = -0.35, profileB = 0.0),
    )

    fun byCode(code: String) = all.firstOrNull { it.code == code }

    /** Apparent geocentric solar longitude referred to the mean equinox/ecliptic of J2000.0 (IMO convention), degrees. */
    internal fun solarLongitudeJ2000(t: Time): Double {
        val sun = geoVector(Body.Sun, t, Aberration.Corrected)
        val ecl = rotationEqjEcl().rotate(sun)
        return normalizeDegrees(Math.toDegrees(atan2(ecl.y, ecl.x)))
    }

    fun solarLongitudeJ2000(instant: Instant): Double = solarLongitudeJ2000(instant.toAstroTime())

    /** Instant when the J2000 solar longitude equals [lambda] during calendar [year] (UTC). */
    fun instantOfSolarLongitude(lambda: Double, year: Int): Instant {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toAstroTime()
        // The Sun moves ~0.9856°/day; λ on Jan 1 ≈ 280°.
        val l0 = solarLongitudeJ2000(start)
        var t = start.ut + normalizeDegrees(lambda - l0) / 0.9856
        repeat(8) {
            val d = angleDiff(lambda, solarLongitudeJ2000(Time(t)))
            t += d / 0.9856
        }
        return Time(t).toInstant()
    }

    /** Occurrence of [shower] whose peak falls in [year]. */
    fun occurrence(shower: MeteorShower, year: Int): ShowerOccurrence {
        val peak = instantOfSolarLongitude(shower.peakSolarLongitude, year)
        val peakDate = peak.atZone(ZoneOffset.UTC).toLocalDate()
        fun around(md: MonthDay, before: Boolean): LocalDate {
            // Choose the year that places the date on the correct side of the peak, within ±200 days.
            val candidates = listOf(year - 1, year, year + 1).map { md.atYear(it) }
            return if (before) candidates.filter { !it.isAfter(peakDate) }.maxOrNull()!!
            else candidates.filter { !it.isBefore(peakDate) }.minOrNull()!!
        }
        return ShowerOccurrence(shower, around(shower.activityStart, true), around(shower.activityEnd, false), peak)
    }

    fun occurrences(year: Int): List<ShowerOccurrence> = all.map { occurrence(it, year) }.sortedBy { it.peak }

    /** Showers active at [instant] (by activity dates). */
    fun active(instant: Instant): List<ShowerOccurrence> {
        val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
        return (date.year - 1..date.year + 1).flatMap { y -> all.map { occurrence(it, y) } }
            .filter { !date.isBefore(it.activityStart) && !date.isAfter(it.activityEnd) }
            .distinctBy { it.shower.code }
            .sortedBy { it.peak }
    }

    /** ZHR at the given time following the activity profile; 0 outside the activity period. */
    fun zhrAt(occ: ShowerOccurrence, instant: Instant): Double {
        val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
        if (date.isBefore(occ.activityStart) || date.isAfter(occ.activityEnd)) return 0.0
        val dl = kotlin.math.abs(angleDiff(solarLongitudeJ2000(instant), occ.shower.peakSolarLongitude))
        return occ.shower.zhr * 10.0.pow(-occ.shower.profileB * dl)
    }

    /** Radiant J2000 position at [instant], applying daily drift from the peak. */
    fun radiant(occ: ShowerOccurrence, instant: Instant): Pair<Double, Double> {
        val days = (instant.toEpochMilli() - occ.peak.toEpochMilli()) / 86_400_000.0
        val ra = normalizeDegrees(occ.shower.raDeg + occ.shower.driftRa * days)
        val dec = (occ.shower.decDeg + occ.shower.driftDec * days).coerceIn(-90.0, 90.0)
        return ra to dec
    }

    fun radiantPosition(occ: ShowerOccurrence, instant: Instant, where: GeoPoint): HorizontalCoord {
        val (ra, dec) = radiant(occ, instant)
        return Sky.fixedPoint(ra / 15.0, dec, instant, where)
    }

    /**
     * Expected visual hourly rate for an observer: HR = ZHR·sin(h_radiant)·r^(LM−6.5).
     * Zero when the radiant is below the horizon or it is daytime (Sun above −12° for visual observing
     * is still counted; PhotoPills shows rates by radiant elevation).
     */
    fun hourlyRate(occ: ShowerOccurrence, instant: Instant, where: GeoPoint, limitingMagnitude: Double = 6.5): Double {
        val zhr = zhrAt(occ, instant)
        if (zhr <= 0) return 0.0
        val h = radiantPosition(occ, instant, where).altitude
        if (h <= 0) return 0.0
        return zhr * sin(Math.toRadians(h)) * occ.shower.populationIndex.pow(limitingMagnitude - 6.5)
    }

    /** Sum of hourly rates of all active showers. */
    fun totalHourlyRate(instant: Instant, where: GeoPoint): Double = active(instant).sumOf { hourlyRate(it, instant, where) }
}
