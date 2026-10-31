package com.photocapsules.core.data

import kotlinx.serialization.Serializable

@Serializable
data class PinState(
    val latitude: Double,
    val longitude: Double,
    /** Terrain altitude above sea level (m), null if unknown. */
    val altitudeM: Double? = null,
    /** Extra height above the terrain (building, tripod...), m. */
    val offsetM: Double = 0.0,
)

/** A saved photo plan (Planner state). */
@Serializable
data class Plan(
    val id: String,
    val name: String,
    val redPin: PinState,
    val blackPin: PinState? = null,
    /** Planned instant, epoch milliseconds. */
    val epochMillis: Long,
    /** IANA time zone id used for display. */
    val zoneId: String,
    val notes: String = "",
    val photoUris: List<String> = emptyList(),
    val createdMillis: Long = System.currentTimeMillis(),
    val updatedMillis: Long = System.currentTimeMillis(),
)

/** A saved point of interest. */
@Serializable
data class PointOfInterest(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    val notes: String = "",
    val photoUris: List<String> = emptyList(),
    val createdMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class LibraryState(val plans: List<Plan> = emptyList(), val pois: List<PointOfInterest> = emptyList())
