package com.photocapsules.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.Eclipses
import com.photocapsules.core.astro.LocalSolarEclipse
import com.photocapsules.core.astro.MeteorShowers
import com.photocapsules.core.astro.MilkyWay
import com.photocapsules.core.astro.MoonCalendar
import com.photocapsules.core.astro.PrincipalPhase
import com.photocapsules.core.astro.SunDay
import com.photocapsules.core.astro.MoonDay
import com.photocapsules.core.geo.Terrain
import com.photocapsules.core.photo.UnitSystem
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.components.MoonDisc
import com.photocapsules.ui.components.NumberDialog
import com.photocapsules.ui.theme.Palette
import java.util.Locale

data class PanelData(
    val sunDay: SunDay?,
    val moonDay: MoonDay?,
    val gcVisibility: List<MilkyWay.Visibility>,
    val localSolar: LocalSolarEclipse?,
)

@Composable
private fun PanelBox(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 8.dp).background(Palette.Surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun KV(k: String, v: String, color: Color = Palette.Text) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (on) Palette.Accent else Palette.SurfaceHigh, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = if (on) Color.Black else Palette.Text, fontSize = 11.sp)
    }
}

@Composable
fun PlannerPanel(index: Int, m: PlannerModel, f: PlannerFrame?, d: PanelData, units: UnitSystem, onEclipsePick: () -> Unit, onMeteorPick: () -> Unit) {
    val z = m.zone
    when (index) {
        0 -> {
            var edit by remember { mutableStateOf(false) }
            PanelBox("Shadow", { Toggle("Layer", m.shadowLayer) { m.shadowLayer = !m.shadowLayer } }) {
                KV("Object height", Fmt.dist(m.objectHeightM, units, 1) + "  ✎", Palette.Accent)
                Spacer(Modifier.height(2.dp))
                val src = if (f != null && f.sun.altitude > 0) "Sun" else if (f != null && f.moon.position.altitude > 0) "Moon" else null
                KV("Shadow length", f?.shadowLengthM?.let { Fmt.dist(it, units, 1) + " ($src)" } ?: "No shadow (Sun & Moon down)")
                KV("Shadow direction", f?.shadowAzimuth?.let { Fmt.deg(it) } ?: "—")
                Text("Tap to edit height", color = Palette.TextDim, fontSize = 10.sp, modifier = Modifier.clickable { edit = true })
            }
            if (edit) NumberDialog("Object height", Fmt.toDisplay(m.objectHeightM, units), Fmt.lenUnit(units), 0.01, 100000.0, false, { edit = false }) { m.objectHeightM = Fmt.fromDisplay(it, units) }
        }
        1 -> PanelBox("Pin to pin (Black Pin)", { Toggle(if (m.black == null) "Add Black Pin" else "Remove", m.black != null) { pendingBlackToggle?.invoke() } }) {
            val g = m.geodetics()
            if (g == null) Text("Add the Black Pin on your subject to measure distance, bearing and elevation angle (terrain and Earth curvature included).", color = Palette.TextDim, fontSize = 12.sp)
            else {
                KV("Distance · bearing", Fmt.dist(g.distanceM, units) + " · " + Fmt.deg(g.bearing, 2))
                KV("Altitude difference", String.format(Locale.US, "%+.0f m", g.altitudeDiffM))
                KV("Elevation angle", String.format(Locale.US, "%+.2f°", g.elevationAngle))
                f?.let { fr ->
                    val aligned = listOf("Sun" to fr.sun, "Moon" to fr.moon.position).firstOrNull { (_, p) -> kotlin.math.abs(com.photocapsules.core.astro.angleDiff(p.azimuth, g.bearing)) < 1.0 && p.altitude > -1 }
                    if (aligned != null) {
                        val (name, p) = aligned
                        val hAbove = Terrain.rayHeightAboveTarget(g.distanceM, m.redAltitude, m.blackGround, p.altitude)
                        val size = Terrain.linearSize(p.angularDiameterDeg, g.distanceM)
                        KV("$name above Black Pin", String.format(Locale.US, "%.0f m (size %.0f m)", hAbove, size), if (hAbove >= 0) Palette.Green else Palette.Red)
                    } else fr.gc?.takeIf { kotlin.math.abs(com.photocapsules.core.astro.angleDiff(it.azimuth, g.bearing)) < 1.0 }?.let { gc ->
                        KV("Galactic Center above Black Pin", String.format(Locale.US, "%.0f m", Terrain.rayHeightAboveTarget(g.distanceM, m.redAltitude, m.blackGround, gc.altitude)))
                    } ?: KV("Sun/Moon size at Black Pin", f.let { String.format(Locale.US, "%.0f m / %.0f m", Terrain.linearSize(fr.sun.angularDiameterDeg, g.distanceM), Terrain.linearSize(fr.moon.position.angularDiameterDeg, g.distanceM)) })
                }
            }
        }
        2 -> PanelBox("Sun & Moon position", {
            Toggle(when { m.sunStyle != BodyLayerStyle.OFF && m.moonStyle != BodyLayerStyle.OFF -> "Sun+Moon"; m.sunStyle != BodyLayerStyle.OFF -> "Sun"; m.moonStyle != BodyLayerStyle.OFF -> "Moon"; else -> "Off" }, true) {
                // Cycle Sun+Moon → Sun → Moon → none
                val s = m.sunStyle != BodyLayerStyle.OFF; val mo = m.moonStyle != BodyLayerStyle.OFF
                when { s && mo -> m.moonStyle = BodyLayerStyle.OFF; s -> { m.sunStyle = BodyLayerStyle.OFF; m.moonStyle = BodyLayerStyle.STANDARD }; mo -> m.moonStyle = BodyLayerStyle.OFF; else -> { m.sunStyle = BodyLayerStyle.STANDARD; m.moonStyle = BodyLayerStyle.STANDARD } }
            }
        }) {
            f?.let {
                KV("Sun az · el", Fmt.deg(it.sun.azimuth, 2) + " · " + Fmt.deg(it.sun.altitude, 2), Palette.Sun)
                KV("Moon az · el", Fmt.deg(it.moon.position.azimuth, 2) + " · " + Fmt.deg(it.moon.position.altitude, 2), Palette.Moon)
                KV("Moon phase", it.moon.phase.label + " " + Fmt.pct(it.moon.illumination))
                KV("Light", it.light.label)
            }
        }
        3 -> PanelBox("Rise & set") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KV("↑ Sun", Fmt.timeAz(d.sunDay?.sunrise?.time, d.sunDay?.sunrise?.azimuth, z), Palette.Sunrise)
                    KV("↓ Sun", Fmt.timeAz(d.sunDay?.sunset?.time, d.sunDay?.sunset?.azimuth, z), Palette.Sunset)
                    KV("↑ Moon", Fmt.timeAz(d.moonDay?.moonrise?.time, d.moonDay?.moonrise?.azimuth, z), Palette.Moonrise)
                    KV("↓ Moon", Fmt.timeAz(d.moonDay?.moonset?.time, d.moonDay?.moonset?.azimuth, z), Palette.Moonset)
                    if (m.horizonEnabled) Text(String.format(Locale.US, "Adjusted for %.0f m above horizon", m.horizonHeightM), color = Palette.Accent, fontSize = 10.sp)
                }
                f?.let {
                    Box(Modifier.clickable { m.instant = MoonCalendar.nextPhase(m.instant.plusSeconds(60)).time }) {
                        MoonDisc(it.moon.phaseAngle, 56.dp, rotation = it.moon.brightLimbFromZenith, background = Palette.light(it.light))
                    }
                }
            }
        }
        4 -> PanelBox("Twilights", { Toggle("Layer", m.twilightLayer) { m.twilightLayer = !m.twilightLayer } }) {
            val s = d.sunDay
            KV("Civil", Fmt.time(s?.civilDawn, z) + " – " + Fmt.time(s?.sunrise?.time, z) + "  |  " + Fmt.time(s?.sunset?.time, z) + " – " + Fmt.time(s?.civilDusk, z))
            KV("Nautical", Fmt.time(s?.nauticalDawn, z) + " – " + Fmt.time(s?.civilDawn, z) + "  |  " + Fmt.time(s?.civilDusk, z) + " – " + Fmt.time(s?.nauticalDusk, z))
            KV("Astronomical", Fmt.time(s?.astronomicalDawn, z) + " – " + Fmt.time(s?.nauticalDawn, z) + "  |  " + Fmt.time(s?.nauticalDusk, z) + " – " + Fmt.time(s?.astronomicalDusk, z))
            if (s?.alwaysUp == true) Text("Midnight sun", color = Palette.Accent, fontSize = 12.sp)
            if (s?.alwaysDown == true) Text("Polar night", color = Palette.Accent, fontSize = 12.sp)
        }
        5 -> PanelBox("Golden & blue hour") {
            val s = d.sunDay
            s?.goldenHours?.forEach { KV("Golden hour", Fmt.time(it.start, z) + " – " + Fmt.time(it.end, z), Palette.Golden) }
            s?.blueHours?.forEach { KV("Blue hour", Fmt.time(it.start, z) + " – " + Fmt.time(it.end, z), Color(0xFF7D95FF)) }
            if (s != null && s.goldenHours.isEmpty() && s.blueHours.isEmpty()) Text("No golden/blue hour today", color = Palette.TextDim)
        }
        6 -> PanelBox("Galactic Center visibility", { Toggle("Milky Way", m.milkyWay) { m.milkyWay = !m.milkyWay } }) {
            if (d.gcVisibility.isEmpty()) Text("Not visible during astronomical night on this date.", color = Palette.TextDim, fontSize = 12.sp)
            d.gcVisibility.forEach { v ->
                KV("Visible", Fmt.timeAz(v.start.time, v.start.azimuth, z) + " · el " + Fmt.deg(v.start.altitude))
                KV("Not visible", Fmt.timeAz(v.end.time, v.end.azimuth, z) + " · el " + Fmt.deg(v.end.altitude))
            }
        }
        7 -> PanelBox("Milky Way position", { Toggle("Next new Moon", false) { m.instant = MoonCalendar.nextPhase(m.instant.plusSeconds(60), PrincipalPhase.NEW).time } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    f?.gc?.let { KV("Galactic Center", Fmt.deg(it.azimuth) + " · el " + Fmt.deg(it.altitude)) }
                    f?.arch?.maxByOrNull { it.altitude }?.let { KV("Arch highest point", Fmt.deg(it.azimuth) + " · el " + Fmt.deg(it.altitude)) }
                    f?.let { KV("Moon", it.moon.phase.label + " " + Fmt.pct(it.moon.illumination) + if (it.moon.position.altitude < 0) " (down)" else "") }
                }
                // Moon "energy" bar: full = no moonlight
                val dark = f?.let { if (it.moon.position.altitude < 0) 1.0 else 1.0 - it.moon.illumination } ?: 0.0
                Box(Modifier.width(14.dp).height(80.dp).background(Palette.SurfaceHigh, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(14.dp).height((80 * dark).dp).background(Palette.Moonset, RoundedCornerShape(4.dp)))
                }
            }
        }
        8 -> PanelBox("Eclipse", { Toggle("Select", m.eclipseLayer) { onEclipsePick() } }) {
            val le = m.lunarEclipse; val se = m.solarEclipse
            when {
                le != null -> {
                    KV(le.name, Fmt.date(le.peak, z))
                    KV("Umbral / penumbral mag.", String.format(Locale.US, "%.3f / %.3f", le.umbralMagnitude, le.penumbralMagnitude))
                    val local = Eclipses.lunarLocal(le, m.observer)
                    val vis = local.count { it.visible }
                    KV("From the Red Pin", if (vis == local.size) "All phases visible" else if (vis == 0) "Not visible" else "$vis of ${local.size} contacts visible")
                }
                se != null -> {
                    KV(se.name, Fmt.date(se.peak, z))
                    val l = d.localSolar
                    KV("From the Red Pin", l?.let { (if (it.visibleAboveHorizon) it.type.label else "Below horizon") + String.format(Locale.US, " · mag %.3f", it.magnitude) } ?: "Not visible")
                    l?.let { KV("Obscuration", Fmt.pct(it.obscuration)) }
                }
                else -> Text("Tap Select to choose a solar or lunar eclipse.", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
        9 -> PanelBox("Eclipse phases") {
            val le = m.lunarEclipse
            if (le != null) {
                val local = Eclipses.lunarLocal(le, m.observer)
                local.chunked(2).forEach { pair ->
                    Row { pair.forEach { c -> Text("${c.contact.code} ${Fmt.time(c.contact.time, z)}${if (c.visible) "" else "*"}  ", color = if (c.visible) Palette.Text else Palette.TextDim, fontSize = 12.sp,
                        modifier = Modifier.clickable { m.instant = c.contact.time }) } }
                }
                Text("* Moon below the horizon", color = Palette.TextDim, fontSize = 10.sp)
            } else d.localSolar?.let { l ->
                l.phases.forEach { ph -> Text("${ph.code} ${Fmt.timeSec(ph.time, z)} · Sun el ${Fmt.deg(ph.sunAltitude)}", color = if (ph.sunAltitude > 0) Palette.Text else Palette.TextDim, fontSize = 12.sp,
                    modifier = Modifier.clickable { m.instant = ph.time }) }
            } ?: Text("No eclipse selected or not visible here.", color = Palette.TextDim, fontSize = 12.sp)
        }
        else -> PanelBox("Meteor showers", { Toggle("Select", m.meteorLayer) { onMeteorPick() } }) {
            val total = MeteorShowers.totalHourlyRate(m.instant, m.observer)
            val occ = m.meteorCode?.let { c -> MeteorShowers.active(m.instant).firstOrNull { it.shower.code == c } ?: MeteorShowers.byCode(c)?.let { MeteorShowers.occurrence(it, m.instant.atZone(z).year) } }
            if (occ != null) {
                KV(occ.shower.name, "peak " + Fmt.dateShort(occ.peak, z) + " " + Fmt.time(occ.peak, z))
                KV("Activity", Fmt.dateShort(occ.activityStart.atStartOfDay(z).toInstant(), z) + " – " + Fmt.dateShort(occ.activityEnd.atStartOfDay(z).toInstant(), z))
                f?.radiant?.let { KV("Radiant", Fmt.deg(it.azimuth) + " · el " + Fmt.deg(it.altitude)) }
            }
            KV("Meteors/h (all active)", String.format(Locale.US, "%.0f", total), Palette.Accent)
        }
    }
}

/** Set by the screen: toggles the Black Pin near the map centre. */
var pendingBlackToggle: (() -> Unit)? = null

const val PANEL_COUNT = 11
