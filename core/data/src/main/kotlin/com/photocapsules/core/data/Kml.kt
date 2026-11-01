package com.photocapsules.core.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * KML/KMZ export & import of plans and POIs (Google Earth compatible). App-specific fields are stored
 * in <ExtendedData> so a round trip is lossless; plain placemarks from other apps import as POIs.
 */
object Kml {
    private const val NS_PC = "photocapsules"
    /** Accepted plan dates: years 1000–3000 (the ephemerides are not meaningful far outside). */
    const val MIN_EPOCH_MS = -30_610_224_000_000L
    const val MAX_EPOCH_MS = 32_503_680_000_000L

    /** XML-escapes and drops characters that are not allowed in XML 1.0 (so backups always re-import). */
    private fun esc(s: String): String {
        val clean = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ok = cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
            if (ok) clean.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    /** Plain decimal notation (no exponent), as KML expects. */
    private fun num(v: Double): String = if (!v.isFinite()) "0" else java.math.BigDecimal(v).setScale(9, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun coords(lon: Double, lat: Double, alt: Double?) = if (alt != null) "${num(lon)},${num(lat)},${num(alt)}" else "${num(lon)},${num(lat)}"

    fun toKml(state: LibraryState, documentName: String = "PhotoCapsules"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n<name>${esc(documentName)}</name>\n")
        for (p in state.plans) {
            append("<Placemark>\n<name>${esc(p.name)}</name>\n")
            append("<description>${esc(p.notes)}</description>\n")
            append("<TimeStamp><when>${Instant.ofEpochMilli(p.epochMillis)}</when></TimeStamp>\n")
            append("<ExtendedData>\n")
            fun data(k: String, v: Any?) { if (v != null) append("<Data name=\"$NS_PC:$k\"><value>${esc(if (v is Double) num(v) else v.toString())}</value></Data>\n") }
            data("type", "plan"); data("id", p.id); data("epochMillis", p.epochMillis); data("zoneId", p.zoneId)
            data("redAltitude", p.redPin.altitudeM); data("redOffset", p.redPin.offsetM)
            if (p.photoUris.isNotEmpty()) data("photoUris", p.photoUris.joinToString("\n"))
            p.blackPin?.let { data("blackLat", it.latitude); data("blackLon", it.longitude); data("blackAltitude", it.altitudeM); data("blackOffset", it.offsetM) }
            append("</ExtendedData>\n")
            append("<Point><coordinates>${coords(p.redPin.longitude, p.redPin.latitude, p.redPin.altitudeM)}</coordinates></Point>\n</Placemark>\n")
        }
        for (p in state.pois) {
            append("<Placemark>\n<name>${esc(p.name)}</name>\n<description>${esc(p.notes)}</description>\n<ExtendedData>\n")
            append("<Data name=\"$NS_PC:type\"><value>poi</value></Data>\n<Data name=\"$NS_PC:id\"><value>${esc(p.id)}</value></Data>\n")
            append("<Data name=\"$NS_PC:createdMillis\"><value>${p.createdMillis}</value></Data>\n")
            if (p.photoUris.isNotEmpty()) append("<Data name=\"$NS_PC:photoUris\"><value>${esc(p.photoUris.joinToString("\n"))}</value></Data>\n")
            append("</ExtendedData>\n<Point><coordinates>${coords(p.longitude, p.latitude, p.altitudeM)}</coordinates></Point>\n</Placemark>\n")
        }
        append("</Document>\n</kml>\n")
    }

    fun toKmz(state: LibraryState, documentName: String = "PhotoCapsules"): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("doc.kml"))
            z.write(toKml(state, documentName).toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    /** Parses KML or KMZ bytes (detected by the ZIP magic number). */
    fun parse(bytes: ByteArray): LibraryState {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return parseKml(ByteArrayInputStream(bytes))
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name.lowercase().endsWith(".kml")) return parseKml(ByteArrayInputStream(z.readBytes()))
                e = z.nextEntry
            }
        }
        return LibraryState()
    }

    fun parseKml(input: InputStream): LibraryState {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Hardening against XXE
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(input)
        // Namespace-aware lookups so both <Placemark> and prefixed <kml:Placemark> documents import.
        val placemarks = doc.getElementsByTagNameNS("*", "Placemark")
        val plans = ArrayList<Plan>(); val pois = ArrayList<PointOfInterest>()
        for (i in 0 until placemarks.length) {
            val pm = placemarks.item(i) as org.w3c.dom.Element
            fun text(tag: String): String? = pm.getElementsByTagNameNS("*", tag).let { if (it.length > 0) it.item(0).textContent.trim() else null }
            val coords = text("coordinates")?.split(Regex("[,\\s]+"))?.filter { it.isNotBlank() } ?: continue
            if (coords.size < 2) continue
            val lon = coords[0].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            val lat = coords[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val alt = coords.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val data = HashMap<String, String>()
            val dataNodes = pm.getElementsByTagNameNS("*", "Data")
            for (j in 0 until dataNodes.length) {
                val d = dataNodes.item(j) as org.w3c.dom.Element
                val v = d.getElementsByTagNameNS("*", "value").let { if (it.length > 0) it.item(0).textContent else "" }
                data[d.getAttribute("name").removePrefix("$NS_PC:")] = v
            }
            fun fnum(k: String): Double? = data[k]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val name = text("name") ?: "Imported"
            val notes = text("description") ?: ""
            if (data["type"] == "plan") {
                val when_ = (data["epochMillis"]?.toLongOrNull() ?: text("when")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })
                    ?.takeIf { it in MIN_EPOCH_MS..MAX_EPOCH_MS } ?: continue // years 1000–3000 only
                val black = fnum("blackLat")?.takeIf { it in -90.0..90.0 }?.let { bl ->
                    fnum("blackLon")?.takeIf { it in -180.0..180.0 }?.let { PinState(bl, it, fnum("blackAltitude"), fnum("blackOffset") ?: 0.0) }
                }
                plans.add(Plan(
                    id = data["id"] ?: LibraryRepository.newId(), name = name,
                    redPin = PinState(lat, lon, fnum("redAltitude") ?: alt, fnum("redOffset") ?: 0.0),
                    blackPin = black, epochMillis = when_, zoneId = data["zoneId"]?.takeIf { z -> runCatching { java.time.ZoneId.of(z) }.isSuccess } ?: "UTC", notes = notes,
                    photoUris = data["photoUris"]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                ))
            } else {
                pois.add(PointOfInterest(data["id"] ?: LibraryRepository.newId(), name, lat, lon, alt, notes,
                    photoUris = data["photoUris"]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
                    createdMillis = data["createdMillis"]?.toLongOrNull()?.takeIf { it in MIN_EPOCH_MS..MAX_EPOCH_MS } ?: System.currentTimeMillis()))
            }
        }
        return LibraryState(plans, pois)
    }
}
