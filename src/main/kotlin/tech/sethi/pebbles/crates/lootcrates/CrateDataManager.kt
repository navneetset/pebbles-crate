package tech.sethi.pebbles.crates.lootcrates

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Paths
import java.util.HashMap

class CrateDataManager {
    private val CRATE_DATA_FILE = Paths.get("config", "pebbles-crate", "crate_data.json").toString()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CRATE_DATA_TYPE: Type = object : TypeToken<Map<String, String>>() {}.type

    /**
     * Loads crate data from disk. Automatically migrates legacy format (coordinate-only)
     * to new format (world + coordinate) using overworld as the default world.
     */
    fun loadCrateData(): Map<WorldBlockPos, String> {
        val crateData = HashMap<WorldBlockPos, String>()
        var needsMigration = false

        try {
            FileReader(CRATE_DATA_FILE).use { reader ->
                val rawCrateData = GSON.fromJson<Map<String, String>>(reader, CRATE_DATA_TYPE) ?: return crateData
                for ((key, value) in rawCrateData) {
                    // WorldBlockPos.decode handles both legacy (long-only) and new (world:long) formats
                    val worldPos = WorldBlockPos.decode(key)
                    crateData[worldPos] = value

                    // Check if this was a legacy format entry (plain number)
                    if (key.toLongOrNull() != null) {
                        needsMigration = true
                    }
                }
            }
        } catch (e: IOException) {
            // File not found, assume no data yet
        }

        // Auto-migrate legacy data to new format
        if (needsMigration) {
            println("[Pebbles-Crates] Migrating crate data to world-aware format...")
            saveCrateData(crateData)
            println("[Pebbles-Crates] Migration complete! ${crateData.size} crates migrated to overworld.")
        }

        return crateData
    }

    /**
     * Saves crate data to disk using the new world-aware format.
     */
    fun saveCrateData(crateData: Map<WorldBlockPos, String>) {
        val rawCrateData = HashMap<String, String>()

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
            e.printStackTrace()
        }
    }
}
