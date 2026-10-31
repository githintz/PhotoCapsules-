package com.photocapsules.core.photo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class UnitSystem { METRIC, IMPERIAL }

object Units {
    const val FEET_PER_METER = 3.280839895013123
    fun mToFt(m: Double) = m * FEET_PER_METER
    fun ftToM(ft: Double) = ft / FEET_PER_METER
}

/** Human-readable formatting shared by the app. */
object Format {
    /** Shutter speed: "1/250", "0.3\"", "2\"", "1m 30s", "1h 02m". */
    fun shutter(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0) return "—"
        return when {
            seconds < 0.3 -> "1/" + formatReciprocal(1.0 / seconds)
            seconds < 10 -> trimZeros(String.format(Locale.US, "%.1f", seconds)) + "\""
            seconds < 60 -> "${seconds.roundToLong()}\""
            else -> duration(seconds)
        }
    }

    private fun formatReciprocal(r: Double): String =
        if (r >= 10) r.roundToLong().toString() else trimZeros(String.format(Locale.US, "%.1f", r))

    /** Durations: "45s", "3m 05s", "2h 07m 12s", "3d 4h". */
    fun duration(seconds: Double, withSeconds: Boolean = true): String {
        if (!seconds.isFinite()) return "∞"
        val neg = seconds < 0
        var s = abs(seconds).roundToLong()
        val d = s / 86400; s %= 86400
        val h = s / 3600; s %= 3600
        val m = s / 60; val sec = s % 60
        val out = when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> if (withSeconds) String.format(Locale.US, "%dh %02dm %02ds", h, m, sec) else String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> if (withSeconds) String.format(Locale.US, "%dm %02ds", m, sec) else "${m}m"
            else -> "${sec}s"
        }
        return if (neg) "-$out" else out
    }

    fun aperture(n: Double): String = "f/" + trimZeros(String.format(Locale.US, if (n < 1) "%.2f" else if (n < 10) "%.1f" else "%.0f", n))

    fun distance(m: Double, units: UnitSystem, decimals: Int = 2): String {
        if (m.isInfinite()) return "∞"
        if (m.isNaN()) return "—"
        return when (units) {
            UnitSystem.METRIC -> if (abs(m) >= 1000) String.format(Locale.US, "%.2f km", m / 1000) else String.format(Locale.US, "%.${decimals}f m", m)
            UnitSystem.IMPERIAL -> {
                val ft = Units.mToFt(m)
                if (abs(ft) >= 5280) String.format(Locale.US, "%.2f mi", ft / 5280) else String.format(Locale.US, "%.${decimals}f ft", ft)
            }
        }
    }

    fun degrees(d: Double, decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f°", d)

    fun trimZeros(s: String): String = if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
