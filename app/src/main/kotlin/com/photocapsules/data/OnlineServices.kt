package com.photocapsules.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class GeocodeResult(val name: String, val latitude: Double, val longitude: Double)
data class NearbyPlace(val title: String, val latitude: Double, val longitude: Double, val distanceM: Double, val pageId: Long)

/** Free, key-less web services. All calls are optional; failures return null/empty. */
object OnlineServices {
    private const val UA = "PhotoCapsules/1.0 (Android; https://github.com/githintz/PhotoCapsules-)"
    private val elevationCache = object : LinkedHashMap<String, Double>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>?) = size > 512
    }

    private fun get(url: String, timeoutMs: Int = 8000): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try { if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    } catch (e: Exception) { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun f(v: Double) = String.format(Locale.US, "%.5f", v)

    /** Terrain elevation (m) from Open-Meteo (Copernicus DEM GLO-90). */
    suspend fun elevation(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        val key = "${f(lat)},${f(lon)}"
        synchronized(elevationCache) { elevationCache[key] }?.let { return@withContext it }
        val body = get("https://api.open-meteo.com/v1/elevation?latitude=${f(lat)}&longitude=${f(lon)}") ?: return@withContext null
        val v = runCatching { JSONObject(body).getJSONArray("elevation").getDouble(0) }.getOrNull()?.takeIf { it.isFinite() && it > -500 }
        if (v != null) synchronized(elevationCache) { elevationCache[key] = v }
        v
    }

    /** IANA time zone for a location (Open-Meteo `timezone=auto`). */
    suspend fun timeZone(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val body = get("https://api.open-meteo.com/v1/forecast?latitude=${f(lat)}&longitude=${f(lon)}&timezone=auto&forecast_days=1&daily=sunrise") ?: return@withContext null
        runCatching { JSONObject(body).getString("timezone") }.getOrNull()?.takeIf { it.contains('/') || it == "UTC" || it == "GMT" }
    }

    /** Place search: Open-Meteo geocoding first, Nominatim (addresses) as fallback. */
    suspend fun geocode(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        val out = ArrayList<GeocodeResult>()
        get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=8&language=en&format=json")?.let { body ->
            runCatching {
                val arr = JSONObject(body).optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val label = listOfNotNull(o.optString("name").ifBlank { null }, o.optString("admin1").ifBlank { null }, o.optString("country").ifBlank { null }).joinToString(", ")
                    out.add(GeocodeResult(label, o.getDouble("latitude"), o.getDouble("longitude")))
                }
            }
        }
        if (out.size < 3) get("https://nominatim.openstreetmap.org/search?q=${enc(query)}&format=jsonv2&limit=8")?.let { body ->
            runCatching {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(GeocodeResult(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble()))
                }
            }
        }
        out
    }

    /** Nearby notable places from Wikipedia GeoSearch (radius ≤ 10 km). */
    suspend fun nearbyPlaces(lat: Double, lon: Double, radiusM: Int = 10000): List<NearbyPlace> = withContext(Dispatchers.IO) {
        val body = get("https://en.wikipedia.org/w/api.php?action=query&list=geosearch&gscoord=${f(lat)}%7C${f(lon)}&gsradius=${radiusM.coerceIn(10, 10000)}&gslimit=50&format=json")
            ?: return@withContext emptyList()
        runCatching {
            val arr = JSONObject(body).getJSONObject("query").getJSONArray("geosearch")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NearbyPlace(o.getString("title"), o.getDouble("lat"), o.getDouble("lon"), o.getDouble("dist"), o.getLong("pageid"))
            }
        }.getOrDefault(emptyList())
    }
}
