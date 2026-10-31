package com.photocapsules.core.astro

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EclipseKind
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.KM_PER_AU
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.earthShadow
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.globalSolarEclipsesAfter
import io.github.cosinekitty.astronomy.lunarEclipsesAfter
import io.github.cosinekitty.astronomy.searchLocalSolarEclipse
import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class EclipseType(val label: String) { PENUMBRAL("Penumbral"), PARTIAL("Partial"), ANNULAR("Annular"), TOTAL("Total") }

private fun EclipseKind.toType() = when (this) {
    EclipseKind.Penumbral -> EclipseType.PENUMBRAL
    EclipseKind.Partial -> EclipseType.PARTIAL
    EclipseKind.Annular -> EclipseType.ANNULAR
    EclipseKind.Total -> EclipseType.TOTAL
}

data class LatLonPoint(val latitude: Double, val longitude: Double)

data class LunarContact(val label: String, val code: String, val time: Instant, val subLunarPoint: LatLonPoint)

data class LunarEclipse(
    val type: EclipseType,
    val peak: Instant,
    val umbralMagnitude: Double,
    val penumbralMagnitude: Double,
    val obscuration: Double,
    /** P1, U1, U2, Greatest, U3, U4, P4 (only those that exist for the eclipse type). */
    val contacts: List<LunarContact>,
) {
    val name: String get() = "${type.label} lunar eclipse"
}

data class LocalLunarContact(val contact: LunarContact, val moonAltitude: Double, val moonAzimuth: Double) {
    val visible: Boolean get() = moonAltitude > -0.5
}

data class SolarEclipse(
    val type: EclipseType,
    val peak: Instant,
    /** Point of greatest eclipse (total/annular only). */
    val greatestEclipse: LatLonPoint?,
    val obscurationAtGreatest: Double?,
    /** Distance of the shadow axis from Earth's centre at greatest eclipse, km. */
    val axisDistanceKm: Double,
) {
    val name: String get() = "${type.label} solar eclipse"
}

data class LocalSolarPhase(val label: String, val code: String, val time: Instant, val sunAltitude: Double, val sunAzimuth: Double)

data class LocalSolarEclipse(
    /** Type seen from the location (partial/annular/total), or null if not seen at all. */
    val type: EclipseType,
    val magnitude: Double,
    val obscuration: Double,
    /** C1, C2, Max, C3, C4. */
    val phases: List<LocalSolarPhase>,
) {
    val visibleAboveHorizon: Boolean get() = phases.any { it.sunAltitude > -0.5 }
}

object Eclipses {
    private const val MOON_RADIUS_KM = 1737.4

    private fun subLunar(t: Time): LatLonPoint {
        val o = geoMoon(t).toObserver(EquatorEpoch.J2000)
        return LatLonPoint(o.latitude, o.longitude)
    }

    fun lunarEclipses(from: Instant, count: Int): List<LunarEclipse> =
        lunarEclipsesAfter(from.toAstroTime()).take(count).map { e ->
            val shadow = earthShadow(e.peak)
            val umb = (shadow.k + MOON_RADIUS_KM - shadow.r) / (2 * MOON_RADIUS_KM)
            val pen = (shadow.p + MOON_RADIUS_KM - shadow.r) / (2 * MOON_RADIUS_KM)
            val c = ArrayList<LunarContact>()
            fun add(label: String, code: String, minutes: Double) {
                val t = e.peak.addDays(minutes / 1440.0)
                c.add(LunarContact(label, code, t.toInstant(), subLunar(t)))
            }
            add("Penumbral begins", "P1", -e.sdPenum)
            if (e.sdPartial > 0) add("Partial begins", "U1", -e.sdPartial)
            if (e.sdTotal > 0) add("Total begins", "U2", -e.sdTotal)
            add("Greatest eclipse", "Max", 0.0)
            if (e.sdTotal > 0) add("Total ends", "U3", e.sdTotal)
            if (e.sdPartial > 0) add("Partial ends", "U4", e.sdPartial)
            add("Penumbral ends", "P4", e.sdPenum)
            LunarEclipse(e.kind.toType(), e.peak.toInstant(), umb, pen, e.obscuration, c)
        }.toList()

    fun lunarLocal(eclipse: LunarEclipse, where: GeoPoint): List<LocalLunarContact> =
        eclipse.contacts.map {
            val p = Sky.position(SkyBody.MOON, it.time, where)
            LocalLunarContact(it, p.altitude, p.azimuth)
        }

    fun solarEclipses(from: Instant, count: Int): List<SolarEclipse> =
        globalSolarEclipsesAfter(from.toAstroTime()).take(count).map { e ->
            val central = e.kind == EclipseKind.Total || e.kind == EclipseKind.Annular
            SolarEclipse(
                e.kind.toType(), e.peak.toInstant(),
                if (central) LatLonPoint(e.latitude, e.longitude) else null,
                if (central) e.obscuration else null,
                e.distance,
            )
        }.toList()

    /**
     * Local circumstances of the solar eclipse around [eclipse] at [where], or null if no eclipse
     * is seen there (the observer is outside the penumbra).
     */
    fun solarLocal(eclipse: SolarEclipse, where: GeoPoint): LocalSolarEclipse? {
        val start = eclipse.peak.minusSeconds(86400L).toAstroTime()
        val local = searchLocalSolarEclipse(start, where.toObserver())
        if (kotlin.math.abs(local.peak.time.ut - eclipse.peak.toAstroTime().ut) > 0.5) return null
        val mag = topocentricMagnitude(local.peak.time, where)
        val phases = ArrayList<LocalSolarPhase>()
        fun add(label: String, code: String, t: Time) {
            val p = Sky.position(SkyBody.SUN, t, where)
            phases.add(LocalSolarPhase(label, code, t.toInstant(), p.altitude, p.azimuth))
        }
        add("Partial begins", "C1", local.partialBegin.time)
        local.totalBegin?.let { add(if (local.kind == EclipseKind.Annular) "Annular begins" else "Total begins", "C2", it.time) }
        add("Maximum", "Max", local.peak.time)
        local.totalEnd?.let { add(if (local.kind == EclipseKind.Annular) "Annular ends" else "Total ends", "C3", it.time) }
        add("Partial ends", "C4", local.partialEnd.time)
        return LocalSolarEclipse(local.kind.toType(), mag, local.obscuration, phases)
    }

    /** Magnitude (fraction of the Sun's diameter covered; Moon/Sun diameter ratio during central phases) at [where], time [t]. */
    internal fun topocentricMagnitude(t: Time, where: GeoPoint): Double {
        val obs = where.toObserver()
        val s = equator(Body.Sun, t, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val m = equator(Body.Moon, t, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val rs = asin(695700.0 / (s.dist * KM_PER_AU))
        val rm = asin(MOON_RADIUS_KM / (m.dist * KM_PER_AU))
        val sv = s.vec; val mv = m.vec
        val sep = acos(((sv.x * mv.x + sv.y * mv.y + sv.z * mv.z) / (sv.length() * mv.length())).coerceIn(-1.0, 1.0))
        // Central phase (Espenak convention): magnitude = ratio of apparent diameters.
        if (sep <= kotlin.math.abs(rm - rs)) return rm / rs
        return ((rs + rm - sep) / (2 * rs)).coerceAtLeast(0.0)
    }

    /** Great circle where the Moon (or any body) is on the horizon, given its sub-point. */
    fun horizonCircle(subPoint: LatLonPoint, segments: Int = 180): List<LatLonPoint> {
        val lat0 = Math.toRadians(subPoint.latitude); val lon0 = Math.toRadians(subPoint.longitude)
        val d = Math.PI / 2
        return (0..segments).map { i ->
            val brg = 2 * Math.PI * i / segments
            val lat = asin(sin(lat0) * cos(d) + cos(lat0) * sin(d) * cos(brg))
            val lon = lon0 + kotlin.math.atan2(sin(brg) * sin(d) * cos(lat0), cos(d) - sin(lat0) * sin(lat))
            LatLonPoint(Math.toDegrees(lat), normalizeLon(Math.toDegrees(lon)))
        }
    }

    internal fun normalizeLon(l: Double): Double { var x = (l + 180.0) % 360.0; if (x < 0) x += 360.0; return x - 180.0 }
    internal fun sq(x: Double) = x * x
    internal fun hyp(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)
}
