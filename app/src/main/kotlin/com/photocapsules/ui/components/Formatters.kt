package com.photocapsules.ui.components

import com.photocapsules.core.photo.Format
import com.photocapsules.core.photo.UnitSystem
import com.photocapsules.core.photo.Units
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val t24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val t12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val tSec = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val date = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
    private val dateShort = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.US)

    var use24h = true
    /** Show azimuths relative to magnetic north (Settings → Azimuth reference). */
    @Volatile var magneticAzimuths = false
    /** Magnetic declination at the location currently on screen (set by screens; east positive). */
    @Volatile var declination = 0.0

    /** Sets [declination] for a location (World Magnetic Model). */
    fun setLocation(lat: Double, lon: Double, altitudeM: Double = 0.0) {
        declination = runCatching { com.photocapsules.data.LocationService.magneticDeclination(lat, lon, altitudeM, System.currentTimeMillis()) }.getOrDefault(0.0)
    }

    /** Azimuth for display: true north, or magnetic (suffix "M") per settings. */
    fun az(trueAz: Double?, decimals: Int = 1): String {
        if (trueAz == null) return "—"
        if (!magneticAzimuths) return String.format(Locale.US, "%.${decimals}f°", trueAz)
        var m = (trueAz - declination) % 360.0; if (m < 0) m += 360.0
        return String.format(Locale.US, "%.${decimals}f°M", m)
    }

    fun time(i: Instant?, zone: ZoneId): String = i?.atZone(zone)?.format(if (use24h) t24 else t12) ?: "—"
    fun timeSec(i: Instant?, zone: ZoneId): String = i?.atZone(zone)?.format(tSec) ?: "—"
    fun date(i: Instant, zone: ZoneId): String = i.atZone(zone).format(date)
    fun dateShort(i: Instant, zone: ZoneId): String = i.atZone(zone).format(dateShort)
    fun dateTime(i: Instant, zone: ZoneId): String = i.atZone(zone).format(dateTime)
    fun deg(d: Double?, decimals: Int = 1): String = d?.let { String.format(Locale.US, "%.${decimals}f°", it) } ?: "—"
    fun timeAz(i: Instant?, az: Double?, zone: ZoneId): String = if (i == null) "—" else "${time(i, zone)} @ ${az(az)}"
    fun dist(m: Double, units: UnitSystem, decimals: Int = 2) = Format.distance(m, units, decimals)
    fun km(km: Double, units: UnitSystem): String =
        if (units == UnitSystem.METRIC) String.format(Locale.US, "%,.0f km", km) else String.format(Locale.US, "%,.0f mi", km * 0.621371)
    fun pct(f: Double) = String.format(Locale.US, "%.0f%%", f * 100)
    fun num(v: Double, decimals: Int = 2) = String.format(Locale.US, "%.${decimals}f", v)

    /** Convert a length shown in the user's units to metres and back. */
    fun toDisplay(m: Double, units: UnitSystem) = if (units == UnitSystem.METRIC) m else Units.mToFt(m)
    fun fromDisplay(v: Double, units: UnitSystem) = if (units == UnitSystem.METRIC) v else Units.ftToM(v)
    fun lenUnit(units: UnitSystem) = if (units == UnitSystem.METRIC) "m" else "ft"
    /** Short length (heights/altitudes) in m or ft without km/mi switching; optional sign. */
    fun len(m: Double, units: UnitSystem, decimals: Int = 0, signed: Boolean = false): String =
        String.format(Locale.US, "%${if (signed) "+" else ""}.${decimals}f %s", toDisplay(m, units), lenUnit(units))
    /** Current unit system from settings (for places without access to composition state). */
    val units: UnitSystem get() = com.photocapsules.PhotoCapsulesApp.instance.settings.current.units

    fun zoneLabel(zone: ZoneId, at: Instant): String {
        val off = zone.rules.getOffset(at)
        return "${zone.id} (UTC${if (off.totalSeconds == 0) "" else off.id})"
    }
}
