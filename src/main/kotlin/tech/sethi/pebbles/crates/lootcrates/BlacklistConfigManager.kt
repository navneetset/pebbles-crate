package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * In-memory owner of blacklist.txt. Read once at server start (and on /padmin reload), written
 * through on every mutation. The particle tick reads [getBlacklist] straight out of memory.
 */
object BlacklistConfigManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val blacklistPath: Path = Paths.get("config/pebbles-crate/blacklist.txt")

    @Volatile
    private var blacklist: Set<WorldBlockPos> = emptySet()

    private var loaded = false

    private fun createBlacklistFile() {
        if (Files.notExists(blacklistPath)) {
            Files.createDirectories(blacklistPath.parent)
            Files.createFile(blacklistPath)
        }
    }

    /**
     * Reads the blacklist from disk into memory.
     * Supports both legacy format (x,y,z) and new format (world_id,x,y,z).
     * Automatically migrates legacy format to new format using overworld as default.
     */
    fun load() {
        val lines = try {
            createBlacklistFile()
            Files.readAllLines(blacklistPath)
        } catch (e: Exception) {
            logger.error("[Pebbles-Crates] Could not read the particle blacklist, treating it as empty", e)
            blacklist = emptySet()
            loaded = true
            return
        }

        var needsMigration = false
        val entries = lines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            try {
                val parts = line.split(',').map { it.trim() }
                when (parts.size) {
                    3 -> {
                        // Legacy format: x,y,z - migrate to overworld
                        val (x, y, z) = parts.map { it.toInt() }
                        needsMigration = true
                        WorldBlockPos("minecraft:overworld", BlockPos(x, y, z))
                    }

                    4 -> {
                        // New format: world_id,x,y,z
                        val worldId = parts[0]
                        val (x, y, z) = parts.drop(1).map { it.toInt() }
                        WorldBlockPos(worldId, BlockPos(x, y, z))
                    }

                    else -> {
                        logger.warn("[Pebbles-Crates] Invalid blacklist line format: '$line'")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.warn("[Pebbles-Crates] Error parsing blacklist line '$line': ${e.message}")
                null
            }
        }.toSet()

        blacklist = entries
        loaded = true

        // Auto-migrate legacy data to new format
        if (needsMigration) {
            logger.info("[Pebbles-Crates] Migrating blacklist to world-aware format...")
            writeToDisk(entries)
            logger.info("[Pebbles-Crates] Blacklist migration complete! ${entries.size} entries migrated to overworld.")
        }
    }

    fun reload() = load()

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    fun getBlacklist(): Set<WorldBlockPos> {
        ensureLoaded()
        return blacklist
    }

    /**
     * Writes the blacklist in the world-aware format.
     */
    private fun writeToDisk(entries: Set<WorldBlockPos>) {
        try {
            Files.writeString(
                blacklistPath, entries.joinToString("\n") { "${it.worldId},${it.pos.x},${it.pos.y},${it.pos.z}" })
        } catch (e: Exception) {
            logger.error("[Pebbles-Crates] Failed to save blacklist", e)
        }
    }

    fun addToBlacklist(worldPos: WorldBlockPos) {
        ensureLoaded()
        if (worldPos in blacklist) return
        val updated = blacklist + worldPos
        blacklist = updated
        writeToDisk(updated)
    }

    fun removeFromBlacklist(worldPos: WorldBlockPos) {
        ensureLoaded()
        if (worldPos !in blacklist) return
        val updated = blacklist - worldPos
        blacklist = updated
        writeToDisk(updated)
    }
}
