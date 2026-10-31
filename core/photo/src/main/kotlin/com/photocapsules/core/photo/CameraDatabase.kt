package com.photocapsules.core.photo

import kotlinx.serialization.json.Json

/** Bundled camera list (cameras.json in resources). */
object CameraDatabase {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<Camera> by lazy {
        val text = CameraDatabase::class.java.getResource("/cameras.json")?.readText()
            ?: error("cameras.json missing")
        json.decodeFromString<List<Camera>>(text).sortedWith(compareBy({ it.make.lowercase() }, { it.model.lowercase() }))
    }

    fun find(name: String): Camera? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun search(query: String): List<Camera> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter { cam -> terms.all { cam.name.lowercase().contains(it) } }
    }
}
