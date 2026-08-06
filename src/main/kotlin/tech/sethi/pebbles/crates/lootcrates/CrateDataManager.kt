package tech.sethi.pebbles.crates.lootcrates

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.config.CrateStyle
import tech.sethi.pebbles.crates.config.CrateStyles
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * One block that has been turned into a crate: which crate it is, and anything about the way this
 * particular one looks or sounds. A placement with no style of its own is the normal case and is
 * written back as the bare crate name it has always been.
 */
data class CratePlacement(val name: String, val style: CrateStyle? = null)

/**
 * In-memory owner of crate_data.json. The file is read once at server start (and again on
 * /padmin reload); every mutation is written straight back to disk. Nothing on the tick,
 * click or tab-complete path touches the filesystem.
 */
object CrateDataManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val CRATE_DATA_FILE = Paths.get("config", "pebbles-crate", "crate_data.json").toString()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val crateData = LinkedHashMap<WorldBlockPos, CratePlacement>()

    /** Positions grouped per world, rebuilt on every mutation so the particle tick never filters. */
    @Volatile
    private var positionsByWorld: Map<String, List<WorldBlockPos>> = emptyMap()

    private var loaded = false

    /**
     * Reads crate data from disk into memory, accepting every shape the file has ever had:
     * dimension-less keys (migrated to the overworld and rewritten), and values that are a plain
     * crate name rather than an object. Both are read forever; only the key migration rewrites the
     * file, because a value that carries no style is still written as a plain name.
     */
    fun load() {
        synchronized(crateData) {
            crateData.clear()
            var needsMigration = false

            try {
                val text = File(CRATE_DATA_FILE).takeIf { it.exists() }?.readText()
                val root = text?.takeIf { it.isNotBlank() }?.let { JsonParser.parseString(it) as? JsonObject }

                if (root != null) {
                    for ((key, element) in root.entrySet()) {
                        // WorldBlockPos.decode handles both legacy (long-only) and new (world:long) formats
                        val worldPos = try {
                            WorldBlockPos.decode(key)
                        } catch (e: Exception) {
                            logger.warn("[Pebbles-Crates] Skipping unreadable crate_data entry '$key': ${e.message}")
                            continue
                        }

                        val placement = readPlacement(key, element) ?: continue
                        crateData[worldPos] = placement

                        // Check if this was a legacy format entry (plain number)
                        if (key.toLongOrNull() != null) {
                            needsMigration = true
                        }
                    }
                }
            } catch (e: Exception) {
                // Malformed json would otherwise escape into the server-start event and take the
                // whole load with it; the file is left alone so it can still be repaired by hand.
                logger.error("[Pebbles-Crates] crate_data.json is not readable, starting with no placed crates", e)
            }

            loaded = true
            rebuildIndex()

            // Sound and particle ids can only be checked once the registries are populated.
            if (PebblesCrate.server != null) {
                for ((worldPos, placement) in crateData) {
                    CrateStyles.warnUnknownIds(placement.style, "crate_data.json entry '${worldPos.encode()}'")
                }
            }

            // Auto-migrate legacy data to new format
            if (needsMigration) {
                logger.info("[Pebbles-Crates] Migrating crate data to world-aware format...")
                writeToDisk()
                logger.info("[Pebbles-Crates] Migration complete! ${crateData.size} crates migrated to overworld.")
            }
        }
    }

    /** A value is either the crate name on its own (every file written before styles) or an object. */
    private fun readPlacement(key: String, element: JsonElement): CratePlacement? {
        if (element.isJsonPrimitive) {
            return element.asString.takeIf { it.isNotBlank() }?.let { CratePlacement(it) }
        }

        val obj = element as? JsonObject
        if (obj == null) {
            logger.warn("[Pebbles-Crates] Skipping crate_data entry '$key': it is neither a crate name nor an object")
            return null
        }

        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        if (name == null) {
            logger.warn("[Pebbles-Crates] Skipping crate_data entry '$key': it has no crate name")
            return null
        }

        val style = try {
            GSON.fromJson(obj.get("style"), CrateStyle::class.java)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] crate_data entry '$key' has an unreadable style, ignoring it: ${e.message}")
            null
        }

        return CratePlacement(name, CrateStyles.sanitize(style, "crate_data.json entry '$key'"))
    }

    fun reload() = load()

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    fun getCrateName(worldPos: WorldBlockPos): String? = getPlacement(worldPos)?.name

    fun getPlacement(worldPos: WorldBlockPos): CratePlacement? {
        ensureLoaded()
        synchronized(crateData) {
            return crateData[worldPos]
        }
    }

    /** Immutable copy, safe to iterate and index while the live map changes. */
    fun snapshot(): Map<WorldBlockPos, CratePlacement> {
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

    /**
     * Registers a crate at a block. A style already set for that exact block is kept: it describes
     * the decoration standing there, and breaking the block is what clears it.
     */
    fun assignCrate(worldPos: WorldBlockPos, crateName: String) {
        ensureLoaded()
        synchronized(crateData) {
            crateData[worldPos] = CratePlacement(crateName, crateData[worldPos]?.style)
            rebuildIndex()
            writeToDisk()
        }
    }

    /** Null clears the placement's overrides, so it inherits its crate type again. */
    fun setPlacementStyle(worldPos: WorldBlockPos, style: CrateStyle?): Boolean {
        ensureLoaded()
        synchronized(crateData) {
            val current = crateData[worldPos] ?: return false
            crateData[worldPos] = current.copy(style = style?.orNull())
            writeToDisk()
            return true
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
     * Writes crate data to disk using the world-aware format. A placement with no style of its own
     * is written as the bare crate name, which is byte-for-byte what every earlier version wrote -
     * only a placement that has been given a style grows into an object.
     */
    private fun writeToDisk() {
        val root = JsonObject()

        for ((worldPos, placement) in crateData) {
            val style = placement.style
            if (style == null) {
                root.addProperty(worldPos.encode(), placement.name)
                continue
            }

            val entry = JsonObject()
            entry.addProperty("name", placement.name)
            entry.add("style", GSON.toJsonTree(style))
            root.add(worldPos.encode(), entry)
        }

        try {
            // Ensure parent directory exists
            val path = Paths.get(CRATE_DATA_FILE)
            Files.createDirectories(path.parent)
            Files.writeString(path, GSON.toJson(root))
        } catch (e: IOException) {
            logger.error("[Pebbles-Crates] Failed to save crate data", e)
        }
    }
}
