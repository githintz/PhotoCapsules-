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

object MoonCalendar {
    /** Supermoon threshold on Earth–Moon centre distance (timeanddate.com convention). */
    const val SUPERMOON_KM = 360_000.0
    /** Micromoon threshold. */
    const val MICROMOON_KM = 405_000.0

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
            out.add(PhaseEvent(phase, inst, dist, syzygy && dist < SUPERMOON_KM, syzygy && dist > MICROMOON_KM))
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
