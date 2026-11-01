package com.photocapsules.feature.planner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photocapsules.PhotoCapsulesApp
import com.photocapsules.core.geo.LatLon
import com.photocapsules.data.LocationService
import com.photocapsules.data.MapType
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.util.GeoPoint as OsmPoint

/** Esri World Imagery (z/y/x URL order). */
private object EsriImagery : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

fun tileSourceFor(t: MapType): ITileSource = when (t) {
    MapType.STANDARD -> TileSourceFactory.MAPNIK
    MapType.TOPO -> TileSourceFactory.OpenTopo
    MapType.SATELLITE -> EsriImagery
}

/** Teardrop pin bitmap. */
fun pinDrawable(ctx: Context, color: Int, sizeDp: Int = 36): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val w = (sizeDp * d * 0.7f).toInt(); val h = (sizeDp * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val r = w / 2f
    val path = Path().apply {
        addCircle(r, r, r * 0.95f, Path.Direction.CW)
        moveTo(r * 0.25f, r * 1.5f); lineTo(r, h.toFloat()); lineTo(r * 1.75f, r * 1.5f); close()
    }
    c.drawPath(path, p)
    c.drawCircle(r, r, r * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    return BitmapDrawable(ctx.resources, bmp)
}

class PlannerMapHolder(ctx: Context, model: PlannerModel) {
    val map = MapView(ctx)
    val overlay = PlannerOverlay(onToolAzimuth = { model.toolAzimuth = it; model.azimuthAtBlackPin = false }, onToolHAngle = { a ->
        val cam = model.toolCamera()
        if (model.focalLocked || cam.fixedFocalMm != null) return@PlannerOverlay
        // Changing the wedge angle changes the focal length.
        val (w, _) = if (model.orientation == com.photocapsules.core.photo.Orientation.LANDSCAPE) cam.sensorWidthMm to cam.sensorHeightMm else cam.sensorHeightMm to cam.sensorWidthMm
        model.focalMm = (w / (2 * kotlin.math.tan(Math.toRadians(a) / 2)) / model.teleconverter).coerceIn(1.0, 5000.0)
    })
    val redMarker = Marker(map)
    val blackMarker = Marker(map)
    val horizonMarker = Marker(map)
    var myLocation: MyLocationNewOverlay? = null
    var appliedType: MapType? = null

    init {
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.minZoomLevel = 2.0
        map.maxZoomLevel = 20.0
        map.controller.setZoom(14.0)
        map.controller.setCenter(OsmPoint(model.red.latitude, model.red.longitude))
        map.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: OsmPoint): Boolean = false
            override fun longPressHelper(p: OsmPoint): Boolean {
                model.moveRed(LatLon(p.latitude, LatLon.normalizeLongitude(p.longitude))); return true
            }
        })
        map.overlays.add(events)
        map.overlays.add(overlay)
        fun setup(m: Marker, color: Int, title: String, onMoved: (LatLon) -> Unit) {
            m.icon = pinDrawable(ctx, color)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            m.isDraggable = true
            m.title = title
            m.setOnMarkerClickListener { mk, _ -> mk.showInfoWindow(); true }
            m.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragStart(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) { onMoved(LatLon(marker.position.latitude, LatLon.normalizeLongitude(marker.position.longitude))) }
            })
        }
        setup(redMarker, 0xFFE53935.toInt(), "Red Pin (observer)") { model.moveRed(it) }
        setup(blackMarker, 0xFF212121.toInt(), "Black Pin (subject)") { model.moveBlack(it) }
        setup(horizonMarker, 0xFF1E88E5.toInt(), "Horizon Pin") { model.horizonPin = it; model.horizonPinTerrain = null }
        map.overlays.add(redMarker)
    }

    fun sync(model: PlannerModel, mapType: MapType, showMyLocation: Boolean, ctx: Context) {
        if (appliedType != mapType) { map.setTileSource(tileSourceFor(mapType)); appliedType = mapType }
        redMarker.position = OsmPoint(model.red.latitude, model.red.longitude)
        redMarker.snippet = altitudeSnippet(model.redAltitude, model)
        val b = model.black
        if (b != null) {
            blackMarker.position = OsmPoint(b.latitude, b.longitude)
            model.geodetics()?.let { g -> blackMarker.snippet = "Alt " + com.photocapsules.ui.components.Fmt.len(model.blackGround, com.photocapsules.ui.components.Fmt.units) + " · Δh " + com.photocapsules.ui.components.Fmt.len(g.altitudeDiffM, com.photocapsules.ui.components.Fmt.units, signed = true) + String.format(java.util.Locale.US, " · elev %+.2f°", g.elevationAngle) }
            if (!map.overlays.contains(blackMarker)) map.overlays.add(blackMarker)
        } else map.overlays.remove(blackMarker)
        val hp = model.horizonPin
        if (model.horizonEnabled && hp != null) {
            horizonMarker.position = OsmPoint(hp.latitude, hp.longitude)
            horizonMarker.snippet = "Height above horizon " + com.photocapsules.ui.components.Fmt.len(model.horizonHeightM, com.photocapsules.ui.components.Fmt.units)
            if (!map.overlays.contains(horizonMarker)) map.overlays.add(horizonMarker)
        } else map.overlays.remove(horizonMarker)
        if (showMyLocation && LocationService.hasPermission(ctx)) {
            if (myLocation == null) myLocation = MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).also { it.enableMyLocation(); map.overlays.add(it) }
        } else myLocation?.let { it.disableMyLocation(); map.overlays.remove(it); myLocation = null }
        map.invalidate()
    }

    private fun altitudeSnippet(alt: Double, model: PlannerModel) =
        String.format(java.util.Locale.US, "%.5f, %.5f · alt ", model.red.latitude, model.red.longitude) + com.photocapsules.ui.components.Fmt.len(alt, com.photocapsules.ui.components.Fmt.units) +
            if (model.horizonEnabled) " · " + com.photocapsules.ui.components.Fmt.len(model.horizonHeightM, com.photocapsules.ui.components.Fmt.units) + " above horizon" else ""

    fun center(p: LatLon) = map.controller.animateTo(OsmPoint(p.latitude, p.longitude))
    fun mapCenter(): LatLon = map.mapCenter.let { LatLon(it.latitude, LatLon.normalizeLongitude(it.longitude)) }
}

@Composable
fun PlannerMapView(model: PlannerModel, frame: PlannerFrame?, modifier: Modifier, holderOut: (PlannerMapHolder) -> Unit) {
    val ctx = LocalContext.current
    val settings = PhotoCapsulesApp.instance.settings.state.value
    val holder = remember { PlannerMapHolder(ctx, model).also(holderOut) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        holder.map.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); holder.map.onPause(); holder.map.onDetach() }
    }
    // Compass-driven map rotation
    DisposableEffect(model.compassRotate) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // The magnetometer error depends on where the PHONE is (not the planned Red Pin); computed once.
        val deviceDecl = (LocationService.lastKnown(ctx)?.let { LocationService.magneticDeclination(it.latitude, it.longitude, 0.0, System.currentTimeMillis()) }
            ?: LocationService.magneticDeclination(model.red.latitude, model.red.longitude, 0.0, System.currentTimeMillis()))
        val listener = object : SensorEventListener {
            val r = FloatArray(9); val rr = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                // Remap for the current display rotation so the heading is correct in landscape too.
                @Suppress("DEPRECATION")
                val rotation = (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                val (ax, ay) = when (rotation) {
                    android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(r, ax, ay, rr)
                SensorManager.getOrientation(rr, o)
                holder.map.mapOrientation = -(Math.toDegrees(o[0].toDouble()) + deviceDecl).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (model.compassRotate && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        else holder.map.mapOrientation = 0f
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(model.centerRequest) { if (model.centerRequest > 0) holder.center(model.red) }
    AndroidView(factory = { holder.map }, modifier = modifier, update = {
        holder.overlay.frame = frame
        holder.sync(model, settings.mapType, settings.showMyLocation, ctx)
    })
}
