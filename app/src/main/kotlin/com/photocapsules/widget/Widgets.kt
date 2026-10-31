package com.photocapsules.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.photocapsules.MainActivity
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.R
import com.photocapsules.core.astro.DayEvents
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.astro.MilkyWay
import com.photocapsules.core.astro.MoonCalc
import com.photocapsules.data.AppState
import com.photocapsules.data.LocationService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private fun t(i: Instant?, z: ZoneId) = i?.atZone(z)?.format(hm) ?: "—"

/** Location for widgets: last known device location, else the app's last sky context. */
private fun widgetLocation(ctx: Context): GeoPoint =
    LocationService.lastKnown(ctx)?.let { GeoPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else 0.0) } ?: AppState.sky.value.point

private fun launchIntent(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

abstract class BaseWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context): RemoteViews
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) runCatching { manager.updateAppWidget(id, build(context)) }
    }
}

class SunWidget : BaseWidget() {
    override fun build(ctx: Context): RemoteViews {
        val where = widgetLocation(ctx); val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        val s = DayEvents.sun(today, zone, where); val m = DayEvents.moon(today, zone, where)
        val info = MoonCalc.info(Instant.now(), where)
        val golden = s.goldenHours.joinToString("  ") { "${t(it.start, zone)}–${t(it.end, zone)}" }
        val blue = s.blueHours.joinToString("  ") { "${t(it.start, zone)}–${t(it.end, zone)}" }
        return RemoteViews(ctx.packageName, R.layout.widget_sun).apply {
            setTextViewText(R.id.w_title, "Sun · " + DayEvents.lightType(Instant.now(), where).label)
            setTextViewText(R.id.w_line1, "↑ ${t(s.sunrise?.time, zone)}   ↓ ${t(s.sunset?.time, zone)}")
            setTextViewText(R.id.w_line2, "Golden $golden")
            setTextViewText(R.id.w_line3, "Blue $blue")
            setTextViewText(R.id.w_line4, "Astro. twilight ${t(s.astronomicalDawn, zone)} / ${t(s.astronomicalDusk, zone)} · Moon ↑ ${t(m.moonrise?.time, zone)} ↓ ${t(m.moonset?.time, zone)} · ${(info.illumination * 100).toInt()}%")
            setOnClickPendingIntent(R.id.w_root, launchIntent(ctx))
        }
    }
}

class MoonWidget : BaseWidget() {
    override fun build(ctx: Context): RemoteViews {
        val where = widgetLocation(ctx); val zone = ZoneId.systemDefault()
        val m = DayEvents.moon(LocalDate.now(zone), zone, where)
        val info = MoonCalc.info(Instant.now(), where)
        return RemoteViews(ctx.packageName, R.layout.widget_moon).apply {
            setTextViewText(R.id.w_title, "${info.phase.label} ${(info.illumination * 100).toInt()}%")
            setTextViewText(R.id.w_line1, "Moonrise ${t(m.moonrise?.time, zone)}  ·  Moonset ${t(m.moonset?.time, zone)}")
            setOnClickPendingIntent(R.id.w_root, launchIntent(ctx))
        }
    }
}

class MilkyWayWidget : BaseWidget() {
    override fun build(ctx: Context): RemoteViews {
        val where = widgetLocation(ctx); val zone = ZoneId.systemDefault()
        val noon = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant()
        val v = MilkyWay.visibility(noon, noon.plusSeconds(86400), where)
        val text = if (v.isEmpty()) "Galactic Center not visible tonight" else v.joinToString("  ") {
            "Visible ${t(it.start.time, zone)} (${it.start.azimuth.toInt()}°) → ${t(it.end.time, zone)} (${it.end.azimuth.toInt()}°)"
        }
        return RemoteViews(ctx.packageName, R.layout.widget_mw).apply {
            setTextViewText(R.id.w_title, "Milky Way")
            setTextViewText(R.id.w_line1, text)
            setOnClickPendingIntent(R.id.w_root, launchIntent(ctx))
        }
    }
}

class PlansWidget : BaseWidget() {
    override fun build(ctx: Context): RemoteViews {
        val now = System.currentTimeMillis()
        val plans = PhotoCapsulesApp.instance.library.load().plans.filter { it.epochMillis >= now }.sortedBy { it.epochMillis }.take(5)
        val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.US)
        val text = if (plans.isEmpty()) "No upcoming plans" else plans.joinToString("\n") { p ->
            val z = runCatching { ZoneId.of(p.zoneId) }.getOrDefault(ZoneId.systemDefault())
            Instant.ofEpochMilli(p.epochMillis).atZone(z).format(fmt) + "  " + p.name
        }
        return RemoteViews(ctx.packageName, R.layout.widget_plans).apply {
            setTextViewText(R.id.w_title, "Upcoming plans")
            setTextViewText(R.id.w_line1, text)
            setOnClickPendingIntent(R.id.w_root, launchIntent(ctx))
        }
    }
}
