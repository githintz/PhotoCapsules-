package com.photocapsules.core.astro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

internal object Golden {
    fun load(name: String): JsonObject = Json.parseToJsonElement(Golden::class.java.getResource("/$name")!!.readText()).jsonObject
}

internal fun JsonElement.o() = jsonObject
internal fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.double
internal fun JsonObject.s(k: String): String? = this[k]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
internal fun JsonObject.geo() = GeoPoint(d("lat"), d("lon"), d("elev"))
internal fun parseUtc(s: String): Instant = if (s.endsWith("Z")) Instant.parse(s) else LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)
internal fun minutesBetween(a: Instant, b: Instant) = kotlin.math.abs(a.toEpochMilli() - b.toEpochMilli()) / 60000.0
internal fun angDist(a: Double, b: Double) = kotlin.math.abs(angleDiff(a, b))
