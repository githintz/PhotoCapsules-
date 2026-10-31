package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.ApsisKind
import io.github.cosinekitty.astronomy.KM_PER_AU
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.nextLunarApsis
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchLunarApsis
import io.github.cosinekitty.astronomy.searchMoonQuarter

import java.time.Instant

enum class PrincipalPhase(val label: String, val angle: Double) {
    NEW("New Moon", 0.0), FIRST_QUARTER("First quarter", 90.0), FULL("Full Moon", 180.0), LAST_QUARTER("Last quarter", 270.0)
}

data class PhaseEvent(val phase: PrincipalPhase, val time: Instant, val distanceKm: Double, val isSupermoon: Boolean, val isMicromoon: Boolean)
data class ApsisEvent(val perigee: Boolean, val time: Instant, val distanceKm: Double)
data class Seasons(val marchEquinox: Instant, val juneSolstice: Instant, val septemberEquinox: Instant, val decemberSolstice: Instant)

/** How a full/new Moon qualifies as a supermoon. */
sealed interface SupermoonRule {
    /** R. Nolle / timeanddate: within 90 % of the perigee of that orbit: d ≤ P + 0.1·(A − P). */
    data object NinetyPercentOfPerigee : SupermoonRule
    /** Fixed Earth–Moon centre distance threshold. */
    data class Distance(val km: Double) : SupermoonRule
}

object MoonCalendar {
    /** Common fixed-distance supermoon threshold (alternative rule). */
    const val SUPERMOON_KM = 360_000.0
    /** Micromoon threshold. */
    const val MICROMOON_KM = 405_000.0

    /** Rule used by [phases]; the app updates it from Settings. */
    @Volatile var supermoonRule: SupermoonRule = SupermoonRule.NinetyPercentOfPerigee

    /**
     * Whether a syzygy at [t] with Earth–Moon distance [distKm] is a supermoon under [rule].
     * For the 90 % rule the perigee closest in time and the apogee closest to that perigee define the orbit.
     */
    fun isSupermoon(t: Instant, distKm: Double, rule: SupermoonRule = supermoonRule): Boolean = when (rule) {
        is SupermoonRule.Distance -> distKm < rule.km
        SupermoonRule.NinetyPercentOfPerigee -> {
            val near = apsides(t.minusSeconds(32L * 86400), t.plusSeconds(32L * 86400))
            val perigee = near.filter { it.perigee }.minByOrNull { kotlin.math.abs(it.time.epochSecond - t.epochSecond) }
            val apogee = perigee?.let { p -> near.filter { !it.perigee }.minByOrNull { kotlin.math.abs(it.time.epochSecond - p.time.epochSecond) } }
            if (perigee == null || apogee == null) distKm < SUPERMOON_KM
            else distKm <= perigee.distanceKm + 0.1 * (apogee.distanceKm - perigee.distanceKm)
        }
    }

    fun moonDistanceKm(instant: Instant): Double = geoMoon(instant.toAstroTime()).length() * KM_PER_AU

    /** Principal phases in [start, end). Full/new Moons are flagged supermoon/micromoon by distance. */
    fun phases(start: Instant, end: Instant): List<PhaseEvent> {
        val out = ArrayList<PhaseEvent>()
        var mq = searchMoonQuarter(start.toAstroTime())
        while (mq.time.toInstant().isBefore(end)) {
            val inst = mq.time.toInstant()
            val dist = geoMoon(mq.time).length() * KM_PER_AU
            val phase = PrincipalPhase.entries[mq.quarter]
            val syzygy = phase == PrincipalPhase.FULL || phase == PrincipalPhase.NEW
            out.add(PhaseEvent(phase, inst, dist, syzygy && isSupermoon(inst, dist), syzygy && dist > MICROMOON_KM))
            mq = nextMoonQuarter(mq)
        }
        return out
    }

    fun nextPhase(from: Instant, phase: PrincipalPhase? = null): PhaseEvent {
        var t = from
        while (true) {
            val list = phases(t, t.plusSeconds(40L * 86400))
            val hit = list.firstOrNull { phase == null || it.phase == phase }
            if (hit != null) return hit
            t = t.plusSeconds(40L * 86400)
        }
    }

    fun previousPhase(from: Instant, phase: PrincipalPhase? = null): PhaseEvent {
        val list = phases(from.minusSeconds(40L * 86400), from.minusMillis(1))
        return list.lastOrNull { phase == null || it.phase == phase } ?: nextPhase(from.minusSeconds(80L * 86400), phase)
    }

    fun apsides(start: Instant, end: Instant): List<ApsisEvent> {
        val out = ArrayList<ApsisEvent>()
        var a = searchLunarApsis(start.toAstroTime())
        while (a.time.toInstant().isBefore(end)) {
            out.add(ApsisEvent(a.kind == ApsisKind.Pericenter, a.time.toInstant(), a.distanceKm))
            a = nextLunarApsis(a)
        }
        return out
    }

    fun supermoons(start: Instant, end: Instant): List<PhaseEvent> = phases(start, end).filter { it.phase == PrincipalPhase.FULL && it.isSupermoon }

    fun seasons(year: Int): Seasons {
        val s = io.github.cosinekitty.astronomy.seasons(year)
        return Seasons(s.marchEquinox.toInstant(), s.juneSolstice.toInstant(), s.septemberEquinox.toInstant(), s.decemberSolstice.toInstant())
    }
}
