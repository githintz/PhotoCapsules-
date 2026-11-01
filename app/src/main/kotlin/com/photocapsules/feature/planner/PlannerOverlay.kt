package com.photocapsules.feature.planner

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.view.MotionEvent
import com.photocapsules.core.astro.BodyPosition
import com.photocapsules.core.astro.HorizontalCoord
import com.photocapsules.core.astro.LatLonPoint
import com.photocapsules.core.astro.LightType
import com.photocapsules.core.astro.LunarEclipse
import com.photocapsules.core.astro.MoonInfo
import com.photocapsules.core.astro.SolarEclipseMapData
import com.photocapsules.core.geo.Geodesy
import com.photocapsules.core.geo.LatLon
import com.photocapsules.core.photo.DofResult
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.osmdroid.util.GeoPoint as OsmPoint

/** Immutable snapshot of everything the overlay draws. */
data class PlannerFrame(
    val red: LatLon,
    val black: LatLon?,
    val blackElevationAngle: Double?,
    val blackBearing: Double?,
    val zone: ZoneId,
    val sun: BodyPosition,
    val moon: MoonInfo,
    val sunRiseAz: Double?, val sunSetAz: Double?,
    val moonRiseAz: Double?, val moonSetAz: Double?,
    val sunPath: List<Pair<Instant, BodyPosition>>,
    val moonPath: List<Pair<Instant, BodyPosition>>,
    val light: LightType,
    val gc: HorizontalCoord?,
    val arch: List<HorizontalCoord>,
    val archCrossings: List<Double>,
    val gcVisibleAz: Double?, val gcHiddenAz: Double?,
    val gcRiseAz: Double?, val gcSetAz: Double?,
    val radiant: HorizontalCoord?,
    val radiantPath: List<HorizontalCoord>,
    val shadowLengthM: Double?,
    val shadowAzimuth: Double?,
    val solarMap: SolarEclipseMapData?,
    val lunarEclipse: LunarEclipse?,
    val toolAzimuth: Double,
    val toolHAngle: Double,
    val toolFocusM: Double,
    val toolDof: DofResult?,
    val sunMoonToolRadiusM: Double?,
    val droneFootprint: Pair<Double, Double>?,
    val droneGrid: CompositionGrid,
    val model: PlannerModel,
)

class PlannerOverlay(private val onToolAzimuth: (Double) -> Unit, private val onToolHAngle: (Double) -> Unit) : Overlay() {
    @Volatile var frame: PlannerFrame? = null

    private val sunColor = 0xFFFFC93C.toInt(); private val sunriseColor = 0xFFFFE066.toInt(); private val sunsetColor = 0xFFFF8A2B.toInt()
    private val moonColor = 0xFF7FC8FF.toInt(); private val moonriseColor = 0xFF9FDBFF.toInt(); private val moonsetColor = 0xFF3F6FD8.toInt()
    private fun paint(color: Int, width: Float, dashed: Boolean = false, fill: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = width; style = if (fill) Paint.Style.FILL else Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 30f; setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK) }

    private var dragTarget = 0 // 1 = azimuth handle, 2 = FoV edge handle

    private fun px(mv: MapView, p: LatLon): PointF {
        val pt = mv.projection.toPixels(OsmPoint(p.latitude, p.longitude), Point())
        return PointF(pt.x.toFloat(), pt.y.toFloat())
    }

    /** Metres per screen pixel near the red pin. */
    private fun metersPerPixel(mv: MapView, f: PlannerFrame): Double {
        val c = px(mv, f.red)
        val g = mv.projection.fromPixels((c.x + 100).toInt(), c.y.toInt())
        return (Geodesy.haversine(f.red, LatLon(g.latitude, LatLon.normalizeLongitude(g.longitude))) / 100.0).coerceAtLeast(1e-3)
    }

    /** Screen unit vector for a true azimuth at the red pin (handles map rotation). */
    private fun dir(mv: MapView, f: PlannerFrame, az: Double): PointF {
        val c = px(mv, f.red)
        val e = px(mv, Geodesy.direct(f.red, az, 200.0))
        val l = hypot((e.x - c.x).toDouble(), (e.y - c.y).toDouble()).toFloat().coerceAtLeast(1e-3f)
        return PointF((e.x - c.x) / l, (e.y - c.y) / l)
    }

    private fun azLine(c: Canvas, mv: MapView, f: PlannerFrame, az: Double, lengthPx: Float, p: Paint) {
        val o = px(mv, f.red)
        if (f.model.realAzimuthLines) {
            val mpp = metersPerPixel(mv, f)
            val pts = Geodesy.geodesicLine(f.red, az, lengthPx * mpp, 48)
            val path = Path()
            pts.forEachIndexed { i, q -> val s = px(mv, q); if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y) }
            c.drawPath(path, p)
        } else {
            val d = dir(mv, f, az)
            c.drawLine(o.x, o.y, o.x + d.x * lengthPx, o.y + d.y * lengthPx, p)
        }
    }

    /** Polar sky-plot position (zenith at the pin, horizon at radius R). */
    private fun polar(mv: MapView, f: PlannerFrame, az: Double, alt: Double, r: Float): PointF {
        val o = px(mv, f.red); val d = dir(mv, f, az)
        val rr = r * ((90 - alt) / 90.0).toFloat()
        return PointF(o.x + d.x * rr, o.y + d.y * rr)
    }

    private fun bodyHiddenByBlack(f: PlannerFrame, az: Double, alt: Double): Boolean {
        val bb = f.blackBearing ?: return false
        val ea = f.blackElevationAngle ?: return false
        return kotlin.math.abs(com.photocapsules.core.astro.angleDiff(az, bb)) < 1.5 && alt < ea
    }

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val f = frame ?: return
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val base = minOf(w, h) * 0.42f
        val len = if (f.model.extendLines) maxOf(w, h) * 2.5f else base
        val m = f.model

        // Twilight colouring of the map (light at the red pin).
        if (m.twilightLayer) {
            val col: Long = when (f.light) {
                LightType.DAY -> 0x00000000L; LightType.GOLDEN_HOUR -> 0x40FFB347L; LightType.BLUE_HOUR -> 0x503A5BD9L
                LightType.NAUTICAL_TWILIGHT -> 0x602B3A8CL; LightType.ASTRONOMICAL_TWILIGHT -> 0x701C2357L; LightType.NIGHT -> 0x800B0E24L
            }
            if (col != 0L) c.drawRect(0f, 0f, w, h, paint(col.toInt(), 0f, fill = true))
        }

        // Eclipse layer
        if (m.eclipseLayer) {
            f.solarMap?.let { sm ->
                fun poly(pts: List<LatLonPoint>, p: Paint) {
                    val path = Path(); var prev: PointF? = null
                    pts.forEach { q ->
                        val s = px(mv, LatLon(q.latitude, q.longitude))
                        if (prev == null || hypot((s.x - prev!!.x).toDouble(), (s.y - prev!!.y).toDouble()) > w) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                        prev = s
                    }
                    c.drawPath(path, p)
                }
                sm.partialContours.forEach { (lvl, pts) -> poly(pts + pts.take(1), paint(if (lvl == 0.0) 0xFFFF6B6B.toInt() else 0xC0FFE066.toInt(), 3f)) }
                poly(sm.umbraNorthLimit, paint(0xFF202020.toInt(), 5f)); poly(sm.umbraSouthLimit, paint(0xFF202020.toInt(), 5f))
                poly(sm.centralLine, paint(0xFF3F6FD8.toInt(), 4f))
                val ge = px(mv, LatLon(sm.greatestEclipse.latitude, sm.greatestEclipse.longitude))
                c.drawCircle(ge.x, ge.y, 10f, paint(0xFFFF4D4D.toInt(), 0f, fill = true)); c.drawText("GE", ge.x + 12, ge.y, textPaint)
            }
            f.lunarEclipse?.let { le ->
                val colors = mapOf("P1" to 0xFF3F6FD8.toInt(), "P4" to 0xFF3F6FD8.toInt(), "U1" to 0xFFFFE066.toInt(), "U4" to 0xFFFFE066.toInt(), "U2" to 0xFFFF4D4D.toInt(), "U3" to 0xFFFF4D4D.toInt())
                le.contacts.filter { it.code != "Max" }.forEach { ct ->
                    val pts = com.photocapsules.core.astro.Eclipses.horizonCircle(ct.subLunarPoint, 120)
                    val path = Path(); var prev: PointF? = null
                    pts.forEach { q ->
                        val s = px(mv, LatLon(q.latitude, q.longitude))
                        if (prev == null || hypot((s.x - prev!!.x).toDouble(), (s.y - prev!!.y).toDouble()) > w / 2) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                        prev = s
                    }
                    c.drawPath(path, paint(colors[ct.code] ?: 0xFFFFFFFF.toInt(), 3f))
                }
                le.contacts.firstOrNull { it.code == "Max" }?.let { mx ->
                    val s = px(mv, LatLon(mx.subLunarPoint.latitude, mx.subLunarPoint.longitude))
                    c.drawCircle(s.x, s.y, 10f, paint(0xFFFF4D4D.toInt(), 0f, fill = true)); c.drawText("GE", s.x + 12, s.y, textPaint)
                }
            }
        }

        val o = px(mv, f.red)
        val showContours = m.sunStyle == BodyLayerStyle.PATH || m.moonStyle == BodyLayerStyle.PATH || m.milkyWay || m.meteorLayer
        if (showContours) for (alt in 0..80 step 10) c.drawCircle(o.x, o.y, base * (90 - alt) / 90f, paint(0x40FFFFFF, 1.5f))

        // Sun layer
        if (m.sunStyle != BodyLayerStyle.OFF) {
            f.sunRiseAz?.let { azLine(c, mv, f, it, len, paint(sunriseColor, 9f, bodyHiddenByBlack(f, it, 0.0))) }
            f.sunSetAz?.let { azLine(c, mv, f, it, len, paint(sunsetColor, 9f, bodyHiddenByBlack(f, it, 0.0))) }
            if (f.sun.altitude > -0.833) {
                if (m.showSunSize) sizeSector(c, mv, f, f.sun, len, sunColor)
                else azLine(c, mv, f, f.sun.azimuth, len, paint(sunColor, 4f, bodyHiddenByBlack(f, f.sun.azimuth, f.sun.altitude)))
            }
            if (m.sunStyle == BodyLayerStyle.PATH) pathPolar(c, mv, f, f.sunPath, base, sunColor, f.sun)
            if (m.sunStyle == BodyLayerStyle.LIGHT) lightDirections(c, mv, f, f.sunPath, base, sunColor)
        }
        // Moon layer
        if (m.moonStyle != BodyLayerStyle.OFF) {
            f.moonRiseAz?.let { azLine(c, mv, f, it, len, paint(moonriseColor, 9f, bodyHiddenByBlack(f, it, 0.0))) }
            f.moonSetAz?.let { azLine(c, mv, f, it, len, paint(moonsetColor, 9f, bodyHiddenByBlack(f, it, 0.0))) }
            val mp = f.moon.position
            if (mp.altitude > -0.833) {
                if (m.showMoonSize) sizeSector(c, mv, f, mp, len, moonColor)
                else azLine(c, mv, f, mp.azimuth, len, paint(moonColor, 4f, bodyHiddenByBlack(f, mp.azimuth, mp.altitude)))
            }
            if (m.moonStyle == BodyLayerStyle.PATH) pathPolar(c, mv, f, f.moonPath, base, moonColor, mp)
            if (m.moonStyle == BodyLayerStyle.LIGHT) lightDirections(c, mv, f, f.moonPath.filter { (t, _) -> true }, base, moonColor, nightOnly = true)
        }
        // Milky Way
        if (m.milkyWay) {
            f.gcVisibleAz?.let { azLine(c, mv, f, it, base, paint(0xFFCCCCCC.toInt(), 6f)) }
            f.gcHiddenAz?.let { azLine(c, mv, f, it, base, paint(0xFF777777.toInt(), 6f)) }
            // Galactic Center rise / set directions (dashed)
            f.gcRiseAz?.let { azLine(c, mv, f, it, base * 1.1f, paint(0xCCFFFFFF.toInt(), 3f, dashed = true)) }
            f.gcSetAz?.let { azLine(c, mv, f, it, base * 1.1f, paint(0x99FFFFFF.toInt(), 3f, dashed = true)) }
            if (f.light == LightType.NIGHT || f.light == LightType.ASTRONOMICAL_TWILIGHT) {
                f.archCrossings.forEach { azLine(c, mv, f, it, base, paint(0xFFFFFFFF.toInt(), 2f)) }
                f.arch.filter { it.altitude > 0 }.forEach { q -> val s = polar(mv, f, q.azimuth, q.altitude, base); c.drawCircle(s.x, s.y, 4f, paint(0xFFFFFFFF.toInt(), 0f, fill = true)) }
                f.gc?.takeIf { it.altitude > 0 }?.let { gc ->
                    azLine(c, mv, f, gc.azimuth, base, paint(0xFFFFFFFF.toInt(), 6f, bodyHiddenByBlack(f, gc.azimuth, gc.altitude)))
                    val s = polar(mv, f, gc.azimuth, gc.altitude, base); c.drawCircle(s.x, s.y, 12f, paint(0xFFFFFFFF.toInt(), 0f, fill = true))
                }
            }
        }
        // Meteor radiant
        if (m.meteorLayer) {
            val path = Path(); var started = false
            f.radiantPath.forEach { q -> if (q.altitude > 0) { val s = polar(mv, f, q.azimuth, q.altitude, base); if (!started) { path.moveTo(s.x, s.y); started = true } else path.lineTo(s.x, s.y) } else started = false }
            c.drawPath(path, paint(0xFFEC407A.toInt(), 3f, dashed = true))
            f.radiant?.takeIf { it.altitude > 0 }?.let { r ->
                azLine(c, mv, f, r.azimuth, base, paint(0xFFEC407A.toInt(), 4f))
                val s = polar(mv, f, r.azimuth, r.altitude, base); c.drawCircle(s.x, s.y, 12f, paint(0xFFEC407A.toInt(), 4f))
            }
        }
        // Shadow
        if (m.shadowLayer) {
            val sl = f.shadowLengthM; val sa = f.shadowAzimuth
            if (sl != null && sa != null) {
                val end = px(mv, Geodesy.direct(f.red, sa, sl.coerceAtMost(50_000.0)))
                c.drawLine(o.x, o.y, end.x, end.y, paint(0xFF000000.toInt(), 10f))
                c.drawLine(o.x, o.y, end.x, end.y, paint(0x80FFFFFF.toInt(), 2f))
            }
        }
        // Black pin link
        f.black?.let { b -> val s = px(mv, b); c.drawLine(o.x, o.y, s.x, s.y, paint(0xC0000000.toInt(), 3f, dashed = true)) }

        // Tools
        when (m.tool) {
            MapTool.FOV, MapTool.DOF -> drawWedge(c, mv, f, w, h)
            MapTool.SUN_MOON -> f.sunMoonToolRadiusM?.let { r ->
                val center = f.black ?: f.red
                val cpx = px(mv, center)
                val rpx = (r / metersPerPixel(mv, f)).toFloat()
                c.drawCircle(cpx.x, cpx.y, rpx, paint(0xFF9E9E9E.toInt(), 5f))
                c.drawText(com.photocapsules.ui.components.Fmt.dist(r, com.photocapsules.ui.components.Fmt.units), cpx.x + rpx * 0.71f, cpx.y - rpx * 0.71f, textPaint)
            }
            MapTool.NONE -> {}
        }
        if (m.mode == PlannerMode.DRONE) drawDrone(c, mv, f)
    }

    private fun sizeSector(c: Canvas, mv: MapView, f: PlannerFrame, pos: BodyPosition, len: Float, color: Int) {
        val half = pos.angularDiameterDeg / 2
        val p = paint(color, 2f)
        azLine(c, mv, f, pos.azimuth - half, len, p)
        azLine(c, mv, f, pos.azimuth + half, len, p)
    }

    private fun pathPolar(c: Canvas, mv: MapView, f: PlannerFrame, path: List<Pair<Instant, BodyPosition>>, r: Float, color: Int, now: BodyPosition) {
        val pth = Path(); var started = false
        for ((t, pos) in path) {
            if (pos.altitude < 0) { started = false; continue }
            val s = polar(mv, f, pos.azimuth, pos.altitude, r)
            if (!started) { pth.moveTo(s.x, s.y); started = true } else pth.lineTo(s.x, s.y)
            if (t.atZone(f.zone).minute == 0) { c.drawCircle(s.x, s.y, 5f, paint(color, 0f, fill = true)); c.drawText("${t.atZone(f.zone).hour}h", s.x + 6, s.y - 6, textPaint.apply { textSize = 22f }) }
        }
        textPaint.textSize = 30f
        c.drawPath(pth, paint(color, 4f))
        if (now.altitude > 0) { val s = polar(mv, f, now.azimuth, now.altitude, r); c.drawCircle(s.x, s.y, 14f, paint(color, 0f, fill = true)) }
    }

    private fun lightDirections(c: Canvas, mv: MapView, f: PlannerFrame, path: List<Pair<Instant, BodyPosition>>, r: Float, color: Int, nightOnly: Boolean = false) {
        for ((t, pos) in path) {
            if (pos.altitude <= 0 || t.atZone(f.zone).minute != 0) continue
            if (nightOnly && f.sunPath.firstOrNull { it.first == t }?.second?.altitude?.let { it > -6 } == true) continue
            val o = px(mv, f.red); val d = dir(mv, f, pos.azimuth)
            c.drawLine(o.x, o.y, o.x + d.x * r, o.y + d.y * r, paint(color and 0xA0FFFFFF.toInt(), 2.5f))
            c.drawText("${t.atZone(f.zone).hour}h", o.x + d.x * (r + 10), o.y + d.y * (r + 10), textPaint.apply { textSize = 22f })
        }
        textPaint.textSize = 30f
    }

    private fun drawWedge(c: Canvas, mv: MapView, f: PlannerFrame, w: Float, h: Float) {
        val o = px(mv, f.red)
        val mpp = metersPerPixel(mv, f)
        val farLimit = f.toolDof?.farLimitM?.takeIf { it.isFinite() } ?: f.toolFocusM
        val reach = (maxOf(f.toolFocusM * 1.6, farLimit * 1.15, (f.toolDof?.hyperfocalM ?: 0.0) * 1.15) / mpp).toFloat().coerceIn(120f, maxOf(w, h) * 3)
        val left = dir(mv, f, f.toolAzimuth - f.toolHAngle / 2); val right = dir(mv, f, f.toolAzimuth + f.toolHAngle / 2)
        // Darken everything outside the wedge.
        // Sector with rays long enough to leave the canvas (works for any field of view up to 180°).
        val far = 2f * hypot(w.toDouble(), h.toDouble()).toFloat()
        val wedge = Path().apply {
            moveTo(o.x, o.y)
            val steps = 24
            for (i in 0..steps) {
                val d = dir(mv, f, f.toolAzimuth - f.toolHAngle / 2 + f.toolHAngle * i / steps)
                lineTo(o.x + d.x * far, o.y + d.y * far)
            }
            close()
        }
        val outside = Path().apply { addRect(0f, 0f, w, h, Path.Direction.CW); op(wedge, Path.Op.DIFFERENCE) }
        c.drawPath(outside, paint(0x66000000, 0f, fill = true))
        val edge = paint(0xFF000000.toInt(), 3f)
        c.drawLine(o.x, o.y, o.x + left.x * reach, o.y + left.y * reach, edge)
        c.drawLine(o.x, o.y, o.x + right.x * reach, o.y + right.y * reach, edge)
        val mid = dir(mv, f, f.toolAzimuth)
        c.drawLine(o.x, o.y, o.x + mid.x * reach, o.y + mid.y * reach, paint(0x99000000.toInt(), 2f, dashed = true))
        fun arc(distM: Double, color: Int, width: Float) {
            if (!distM.isFinite()) return
            val rp = (distM / mpp).toFloat()
            val startAngle = Math.toDegrees(atan2(left.y.toDouble(), left.x.toDouble())).toFloat()
            var sweep = Math.toDegrees(atan2(right.y.toDouble(), right.x.toDouble())).toFloat() - startAngle
            while (sweep < 0) sweep += 360f
            c.drawArc(o.x - rp, o.y - rp, o.x + rp, o.y + rp, startAngle, sweep, false, paint(color, width))
        }
        f.toolDof?.let { d ->
            arc(d.nearLimitM, 0xFF9E9E9E.toInt(), 6f); arc(d.farLimitM, 0xFF9E9E9E.toInt(), 6f); arc(d.hyperfocalM, 0xFF3F6FD8.toInt(), 4f)
            if (d.farLimitM.isInfinite()) c.drawText("Far limit: ∞", 20f, h - 30f, textPaint)
        }
        arc(f.toolFocusM, 0xFFFF4D4D.toInt(), 4f)
        // Handles: azimuth (centre) and FoV (edge)
        c.drawCircle(o.x + mid.x * reach, o.y + mid.y * reach, 18f, paint(0xFF000000.toInt(), 0f, fill = true))
        c.drawCircle(o.x + right.x * reach * 0.7f, o.y + right.y * reach * 0.7f, 16f, paint(0xFF000000.toInt(), 0f, fill = true))
        handleCache = Triple(PointF(o.x, o.y), PointF(o.x + mid.x * reach, o.y + mid.y * reach), PointF(o.x + right.x * reach * 0.7f, o.y + right.y * reach * 0.7f))
    }

    private var handleCache: Triple<PointF, PointF, PointF>? = null

    override fun onTouchEvent(e: MotionEvent, mv: MapView): Boolean {
        val f = frame ?: return false
        if (f.model.tool != MapTool.FOV && f.model.tool != MapTool.DOF) return false
        val (o, azH, fovH) = handleCache ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = when {
                    hypot((e.x - azH.x).toDouble(), (e.y - azH.y).toDouble()) < 60 -> 1
                    hypot((e.x - fovH.x).toDouble(), (e.y - fovH.y).toDouble()) < 60 -> 2
                    else -> 0
                }
                return dragTarget != 0
            }
            MotionEvent.ACTION_MOVE -> if (dragTarget != 0) {
                // Screen angle → true azimuth: compare with the screen direction of north.
                val north = dir(mv, f, 0.0)
                val aNorth = atan2(north.y.toDouble(), north.x.toDouble())
                val aTouch = atan2((e.y - o.y).toDouble(), (e.x - o.x).toDouble())
                val az = com.photocapsules.core.astro.normalizeDegrees(Math.toDegrees(aTouch - aNorth))
                if (dragTarget == 1) onToolAzimuth(az)
                else onToolHAngle((2 * kotlin.math.abs(com.photocapsules.core.astro.angleDiff(az, f.toolAzimuth))).coerceIn(1.0, 170.0))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val was = dragTarget != 0; dragTarget = 0; return was }
        }
        return false
    }

    private fun drawDrone(c: Canvas, mv: MapView, f: PlannerFrame) {
        val (fw, fh) = f.droneFootprint ?: return
        val az = f.toolAzimuth
        // Corners of the footprint centred on the red pin, rotated by the drone heading.
        val hw = fw / 2; val hh = fh / 2
        val corners = listOf(-hw to hh, hw to hh, hw to -hh, -hw to -hh).map { (x, y) ->
            val dist = hypot(x, y); val brg = az + Math.toDegrees(atan2(x, y))
            px(mv, Geodesy.direct(f.red, brg, dist))
        }
        val path = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        c.drawPath(path, paint(0x30FFFFFF, 0f, fill = true)); c.drawPath(path, paint(0xFFFFC93C.toInt(), 4f))
        val u = com.photocapsules.ui.components.Fmt.units
        val areaM2 = fw * fh
        val areaTxt = if (u == com.photocapsules.core.photo.UnitSystem.METRIC) (if (areaM2 >= 10_000) String.format(java.util.Locale.US, "%.2f ha", areaM2 / 10_000) else String.format(java.util.Locale.US, "%.0f m²", areaM2))
            else String.format(java.util.Locale.US, "%.2f acres", areaM2 / 4046.856)
        c.drawText("Drone " + com.photocapsules.ui.components.Fmt.len(f.model.droneAltitudeM, u) + " · " + com.photocapsules.ui.components.Fmt.len(fw, u) + " × " +
            com.photocapsules.ui.components.Fmt.len(fh, u) + " · " + areaTxt, 20f, c.height - 30f, textPaint)
        fun lerp(a: PointF, b: PointF, t: Float) = PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val gp = paint(0xC0FFFFFF.toInt(), 2f)
        val (tl, tr, br, bl) = corners
        fun vline(t: Float) { val a = lerp(tl, tr, t); val b = lerp(bl, br, t); c.drawLine(a.x, a.y, b.x, b.y, gp) }
        fun hline(t: Float) { val a = lerp(tl, bl, t); val b = lerp(tr, br, t); c.drawLine(a.x, a.y, b.x, b.y, gp) }
        when (f.droneGrid) {
            CompositionGrid.THIRDS -> { vline(1 / 3f); vline(2 / 3f); hline(1 / 3f); hline(2 / 3f) }
            CompositionGrid.GOLDEN -> { vline(0.382f); vline(0.618f); hline(0.382f); hline(0.618f) }
            CompositionGrid.DIAGONAL -> { c.drawLine(tl.x, tl.y, br.x, br.y, gp); c.drawLine(tr.x, tr.y, bl.x, bl.y, gp) }
            CompositionGrid.TRIANGLE -> { c.drawLine(tl.x, tl.y, br.x, br.y, gp); val k = lerp(tl, br, 0.5f); c.drawLine(tr.x, tr.y, (tr.x + bl.x) / 2 + (k.x - (tr.x + bl.x) / 2) * 0.6f, (tr.y + bl.y) / 2 + (k.y - (tr.y + bl.y) / 2) * 0.6f, gp) }
            CompositionGrid.CROSSHAIR -> { vline(0.5f); hline(0.5f) }
            CompositionGrid.SPIRAL -> {
                // Fibonacci-style golden spiral: quarter arcs in successively smaller squares, mapped into the frame.
                fun at(u: Float, v: Float): PointF { val top = lerp(tl, tr, u); val bot = lerp(bl, br, u); return lerp(top, bot, v) }
                val path = Path(); var first = true
                var x0 = 0f; var y0 = 0f; var w0 = 1f; var h0 = 1f
                val phi = 0.618034f
                for (k in 0 until 8) {
                    // Square of side s carved from the remaining rectangle, rotating CW each step.
                    val dir = k % 4
                    val sW = if (dir % 2 == 0) w0 * phi else w0; val sH = if (dir % 2 == 0) h0 else h0 * phi
                    val (cx, cy, a0) = when (dir) {
                        0 -> Triple(x0 + sW, y0 + sH, 180f)
                        1 -> Triple(x0, y0 + sH, 270f)
                        2 -> Triple(x0 + w0 - sW, y0, 0f)
                        else -> Triple(x0 + w0, y0 + h0 - sH, 90f)
                    }
                    for (i in 0..12) {
                        val ang = Math.toRadians((a0 + 90f * i / 12f).toDouble())
                        val u = cx + sW * kotlin.math.cos(ang).toFloat(); val v = cy + sH * kotlin.math.sin(ang).toFloat()
                        val pt = at(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
                        if (first) { path.moveTo(pt.x, pt.y); first = false } else path.lineTo(pt.x, pt.y)
                    }
                    when (dir) { 0 -> { x0 += sW; w0 -= sW }; 1 -> { y0 += sH; h0 -= sH }; 2 -> { w0 -= sW }; else -> { h0 -= sH } }
                }
                c.drawPath(path, gp)
            }
            CompositionGrid.NONE -> {}
        }
    }
}
