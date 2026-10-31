package com.photocapsules.core.geo

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

enum class CoordinateFormat { DDD, DMM, DMS }

/** Formatting and tolerant parsing of geographic coordinates. */
object Coordinates {

    fun format(p: LatLon, fmt: CoordinateFormat): String =
        "${formatComponent(p.latitude, true, fmt)} ${formatComponent(p.longitude, false, fmt)}"

    fun formatComponent(value: Double, isLatitude: Boolean, fmt: CoordinateFormat): String {
        val hemi = if (isLatitude) (if (value < 0) "S" else "N") else (if (value < 0) "W" else "E")
        val v = abs(value)
        return when (fmt) {
            CoordinateFormat.DDD -> String.format(java.util.Locale.US, "%.6f°%s", v, hemi)
            CoordinateFormat.DMM -> {
                // Round at the output precision first, then split, to avoid "59.9999'".
                val totalMinThousandths = (v * 60_000).roundToLong()
                val d = totalMinThousandths / 60_000
                val m = (totalMinThousandths % 60_000) / 1000.0
                String.format(java.util.Locale.US, "%d°%06.3f'%s", d, m, hemi)
            }
            CoordinateFormat.DMS -> {
                val totalCentiSec = (v * 360_000).roundToLong()
                val d = totalCentiSec / 360_000
                val m = (totalCentiSec % 360_000) / 6000
                val s = (totalCentiSec % 6000) / 100.0
                String.format(java.util.Locale.US, "%d°%02d'%05.2f\"%s", d, m, s, hemi)
            }
        }
    }

    private val numberRegex = Regex("""[-+]?\d+(?:[.,]\d+)?""")

    /**
     * Parses one coordinate component in DDD, DMM or DMS forms, e.g. "40.4168", "-3.7038",
     * "40°25'0.5\"N", "40 25.008 N", "W 3° 42' 13.7\"". Returns null when unparseable or out of range.
     */
    fun parseComponent(text: String, isLatitude: Boolean): Double? {
        val t = text.trim().uppercase()
        if (t.isEmpty()) return null
        val nums = numberRegex.findAll(t).map { it.value.replace(',', '.') }.toList()
        if (nums.isEmpty() || nums.size > 3) return null
        val hemiChars = if (isLatitude) "NS" else "EWO" // O = Oeste (Spanish/French)
        val letters = t.filter { it.isLetter() }
        if (letters.any { it !in hemiChars }) return null
        val negativeHemi = letters.any { it == 'S' || it == 'W' || it == 'O' }
        val first = nums[0].toDouble()
        val sign = if (first < 0 || nums[0].startsWith("-")) -1.0 else 1.0
        val d = abs(first)
        val m = nums.getOrNull(1)?.toDouble() ?: 0.0
        val s = nums.getOrNull(2)?.toDouble() ?: 0.0
        if (m < 0 || m >= 60 || s < 0 || s >= 60) return null
        if (nums.size > 1 && d != floor(d)) return null
        var value = sign * (d + m / 60.0 + s / 3600.0)
        if (negativeHemi) value = -abs(value)
        val limit = if (isLatitude) 90.0 else 180.0
        if (abs(value) > limit) return null
        return value
    }

    /** Parses "lat, lon" pairs in any supported format. */
    fun parse(text: String): LatLon? {
        val t = text.trim()
        // Split on comma/semicolon if present (but decimal commas would be ambiguous: require ", " or ";").
        val parts: List<String> = when {
            t.contains(';') -> t.split(';')
            Regex(""",\s""").containsMatchIn(t) -> t.split(Regex(""",\s+"""))
            t.count { it == ',' } == 1 && !t.contains('.') -> t.split(',')
            t.count { it == ',' } == 1 -> t.split(',')
            else -> splitByHemisphere(t) ?: t.split(Regex("""\s+""")).let { if (it.size == 2) it else return null }
        }
        if (parts.size != 2) return null
        val lat = parseComponent(parts[0], true) ?: return null
        val lon = parseComponent(parts[1], false) ?: return null
        return LatLon(lat, LatLon.normalizeLongitude(lon).let { if (lon == 180.0) -180.0 else it })
    }

    private fun splitByHemisphere(t: String): List<String>? {
        val u = t.uppercase()
        val idx = u.indexOfFirst { it == 'N' || it == 'S' }
        if (idx < 0) return null
        // Hemisphere letter may be a prefix ("N 40 25") or suffix ("40 25 N").
        return if (u.trim().startsWith("N") || u.trim().startsWith("S")) {
            val second = u.indexOfFirst { it == 'E' || it == 'W' || it == 'O' }
            if (second < 0) null else listOf(t.substring(0, second), t.substring(second))
        } else listOf(t.substring(0, idx + 1), t.substring(idx + 1))
    }
}
