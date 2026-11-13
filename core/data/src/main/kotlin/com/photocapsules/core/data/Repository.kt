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
/** [onChange] runs after every successful save (e.g. to refresh home-screen widgets). */
class JsonFileLibraryRepository(private val file: File, private val onChange: (() -> Unit)? = null) : LibraryRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val lock = Any()

    override fun load(): LibraryState = synchronized(lock) {
        if (!file.exists()) return LibraryState()
        return try { json.decodeFromString<LibraryState>(file.readText()) } catch (e: Exception) {
            // Keep a copy of a corrupt file rather than silently losing it.
            runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) }
            LibraryState()
        }
    }

    override fun save(state: LibraryState) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LibraryState.serializer(), state))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
        onChange?.let { runCatching { it() } }
    }

    // Read-modify-write as one step, so a background import and a save from the UI cannot lose each other's change.
    override fun upsertPlan(plan: Plan) = synchronized(lock) { super.upsertPlan(plan) }
    override fun deletePlan(id: String) = synchronized(lock) { super.deletePlan(id) }
    override fun upsertPoi(poi: PointOfInterest) = synchronized(lock) { super.upsertPoi(poi) }
    override fun deletePoi(id: String) = synchronized(lock) { super.deletePoi(id) }
    override fun merge(imported: LibraryState): Pair<Int, Int> = synchronized(lock) { super.merge(imported) }
}

class InMemoryLibraryRepository(private var state: LibraryState = LibraryState()) : LibraryRepository {
    override fun load() = state
    override fun save(state: LibraryState) { this.state = state }
}
