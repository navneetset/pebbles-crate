package tech.sethi.pebbles.crates.lootcrates

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Paths

/**
 * In-memory owner of crate_data.json. The file is read once at server start (and again on
 * /padmin reload); every mutation is written straight back to disk. Nothing on the tick,
 * click or tab-complete path touches the filesystem.
 */
object CrateDataManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val CRATE_DATA_FILE = Paths.get("config", "pebbles-crate", "crate_data.json").toString()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CRATE_DATA_TYPE: Type = object : TypeToken<Map<String, String>>() {}.type

    private val crateData = LinkedHashMap<WorldBlockPos, String>()

    /** Positions grouped per world, rebuilt on every mutation so the particle tick never filters. */
    @Volatile
    private var positionsByWorld: Map<String, List<WorldBlockPos>> = emptyMap()

    private var loaded = false

    /**
     * Reads crate data from disk into memory. Automatically migrates legacy format (coordinate-only)
     * to new format (world + coordinate) using overworld as the default world.
     */
    fun load() {
        synchronized(crateData) {
            crateData.clear()
            var needsMigration = false

            try {
                FileReader(CRATE_DATA_FILE).use { reader ->
                    val rawCrateData = GSON.fromJson<Map<String, String>>(reader, CRATE_DATA_TYPE)
                    if (rawCrateData != null) {
                        for ((key, value) in rawCrateData) {
                            // WorldBlockPos.decode handles both legacy (long-only) and new (world:long) formats
                            try {
                                crateData[WorldBlockPos.decode(key)] = value
                            } catch (e: Exception) {
                                logger.warn("[Pebbles-Crates] Skipping unreadable crate_data entry '$key': ${e.message}")
                                continue
                            }

                            // Check if this was a legacy format entry (plain number)
                            if (key.toLongOrNull() != null) {
                                needsMigration = true
                            }
                        }
                    }
                }
            } catch (e: FileNotFoundException) {
                // No file yet: nothing on this server has been turned into a crate.
            } catch (e: Exception) {
                // Malformed json would otherwise escape into the server-start event and take the
                // whole load with it; the file is left alone so it can still be repaired by hand.
                logger.error("[Pebbles-Crates] crate_data.json is not readable, starting with no placed crates", e)
            }

            loaded = true
            rebuildIndex()

            // Auto-migrate legacy data to new format
            if (needsMigration) {
                logger.info("[Pebbles-Crates] Migrating crate data to world-aware format...")
                writeToDisk()
                logger.info("[Pebbles-Crates] Migration complete! ${crateData.size} crates migrated to overworld.")
            }
        }
    }

    fun reload() = load()

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    fun getCrateName(worldPos: WorldBlockPos): String? {
        ensureLoaded()
        synchronized(crateData) {
            return crateData[worldPos]
        }
    }

    /** Immutable copy, safe to iterate and index while the live map changes. */
    fun snapshot(): Map<WorldBlockPos, String> {
        ensureLoaded()
        synchronized(crateData) {
            return LinkedHashMap(crateData)
        }
    }

    /** Every registered crate position in the given world; empty (and allocation free) when there are none. */
    fun cratesInWorld(worldId: String): List<WorldBlockPos> {
        ensureLoaded()
        return positionsByWorld[worldId] ?: emptyList()
    }

    fun assignCrate(worldPos: WorldBlockPos, crateName: String) {
        ensureLoaded()
        synchronized(crateData) {
            crateData[worldPos] = crateName
            rebuildIndex()
            writeToDisk()
        }
    }

    fun removeCrate(worldPos: WorldBlockPos): Boolean {
        ensureLoaded()
        synchronized(crateData) {
            if (crateData.remove(worldPos) == null) return false
            rebuildIndex()
            writeToDisk()
            return true
        }
    }

    private fun rebuildIndex() {
        positionsByWorld = crateData.keys.groupBy { it.worldId }
    }

    /**
     * Writes crate data to disk using the world-aware format.
     */
    private fun writeToDisk() {
        val rawCrateData = LinkedHashMap<String, String>()

        for ((worldPos, value) in crateData) {
            rawCrateData[worldPos.encode()] = value
        }

        try {
            // Ensure parent directory exists
            val path = Paths.get(CRATE_DATA_FILE)
            Files.createDirectories(path.parent)

            FileWriter(CRATE_DATA_FILE).use { writer ->
                GSON.toJson(rawCrateData, CRATE_DATA_TYPE, writer)
            }
        } catch (e: IOException) {
            logger.error("[Pebbles-Crates] Failed to save crate data", e)
        }
    }
}
