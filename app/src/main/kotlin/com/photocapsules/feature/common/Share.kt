package com.photocapsules.feature.common

import android.content.Context
import android.content.Intent
import com.photocapsules.core.astro.Timeline
import com.photocapsules.data.SkyContext
import com.photocapsules.ui.components.Fmt
import java.util.Locale

/** Shares a plain-text summary through the system share sheet. */
fun shareText(ctx: Context, subject: String, body: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
    ctx.startActivity(Intent.createChooser(i, subject))
}

/** Day summary (all Sun/Moon/Milky Way events) for the given context. */
fun daySummary(c: SkyContext, title: String): String {
    val date = c.instant.atZone(c.zone).toLocalDate()
    val events = Timeline.events(date, c.zone, c.point, c.horizonHeightM)
    val sb = StringBuilder()
    sb.append("$title – ${Fmt.date(c.instant, c.zone)}\n")
    sb.append(String.format(Locale.US, "%s (%.5f, %.5f) · %s\n\n", c.label, c.point.latitude, c.point.longitude, c.zone.id))
    for (e in events) sb.append(Fmt.time(e.time, c.zone)).append("  ").append(e.kind.label).append(e.azimuth?.let { "  @ " + Fmt.az(it) } ?: "").append('\n')
    sb.append("\nShared from PhotoCapsules")
    return sb.toString()
}
