package com.photocapsules.core.data

import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Persistence abstraction for plans and POIs (swap for Room/cloud later). */
interface LibraryRepository {
    fun load(): LibraryState
    fun save(state: LibraryState)

    fun upsertPlan(plan: Plan) = save(load().let { s -> s.copy(plans = s.plans.filterNot { it.id == plan.id } + plan) })
    fun deletePlan(id: String) = save(load().let { s -> s.copy(plans = s.plans.filterNot { it.id == id }) })
    fun upsertPoi(poi: PointOfInterest) = save(load().let { s -> s.copy(pois = s.pois.filterNot { it.id == poi.id } + poi) })
    fun deletePoi(id: String) = save(load().let { s -> s.copy(pois = s.pois.filterNot { it.id == id }) })

    /** Merges imported items, skipping exact duplicates (same id or same name+position). */
    fun merge(imported: LibraryState): Pair<Int, Int> {
        val s = load()
        val newPlans = imported.plans.filter { p -> s.plans.none { it.id == p.id || (it.name == p.name && it.redPin == p.redPin && it.epochMillis == p.epochMillis) } }
        val newPois = imported.pois.filter { p -> s.pois.none { it.id == p.id || (it.name == p.name && it.latitude == p.latitude && it.longitude == p.longitude) } }
        save(s.copy(plans = s.plans + newPlans, pois = s.pois + newPois))
        return newPlans.size to newPois.size
    }

    companion object { fun newId(): String = UUID.randomUUID().toString() }
}

/** JSON-file implementation; atomic writes via temp file + rename. Thread-safe. */
class JsonFileLibraryRepository(private val file: File) : LibraryRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val lock = Any()

    override fun load(): LibraryState = synchronized(lock) {
        if (!file.exists()) return LibraryState()
        return try { json.decodeFromString<LibraryState>(file.readText()) } catch (e: Exception) {
            // Keep a copy of a corrupt file rather than silently losing it.
            file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true)
            LibraryState()
        }
    }

    override fun save(state: LibraryState) = synchronized(lock) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(LibraryState.serializer(), state))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        Unit
    }
}

class InMemoryLibraryRepository(private var state: LibraryState = LibraryState()) : LibraryRepository {
    override fun load() = state
    override fun save(state: LibraryState) { this.state = state }
}
