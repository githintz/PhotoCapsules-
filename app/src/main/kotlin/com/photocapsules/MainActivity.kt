package com.photocapsules

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.photocapsules.core.astro.GeoPoint
import com.photocapsules.core.data.Kml
import com.photocapsules.data.AppState
import com.photocapsules.data.LocationService
import com.photocapsules.ui.AppNavigation
import com.photocapsules.ui.components.Fmt
import com.photocapsules.ui.theme.PhotoCapsulesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fmt.use24h = PhotoCapsulesApp.instance.settings.current.use24h
        setContent { PhotoCapsulesTheme { AppNavigation() } }
        if (!LocationService.hasPermission(this)) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        } else refreshLocation()
        handleImport(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImport(intent)
    }

    private fun refreshLocation() {
        lifecycleScope.launch {
            LocationService.lastKnown(this@MainActivity)?.let { applyLocation(it.latitude, it.longitude, LocationService.seaLevelAltitude(this@MainActivity, it)) }
            LocationService.current(this@MainActivity)?.let { applyLocation(it.latitude, it.longitude, LocationService.seaLevelAltitude(this@MainActivity, it)) }
        }
    }

    private fun applyLocation(lat: Double, lon: Double, alt: Double) {
        val cur = AppState.sky.value
        if (cur.live) AppState.sky.value = cur.copy(point = GeoPoint(lat, lon, alt.coerceAtLeast(-430.0)), label = "Current location", instant = Instant.now(), zone = ZoneId.systemDefault())
    }

    /** Opening a .kmz/.kml file imports plans and POIs. */
    private fun handleImport(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    PhotoCapsulesApp.instance.library.merge(Kml.parse(bytes))
                }
            }
            val msg = result.fold({ (p, q) -> "Imported $p plans and $q points of interest" }, { "Could not import file: ${it.message}" })
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
