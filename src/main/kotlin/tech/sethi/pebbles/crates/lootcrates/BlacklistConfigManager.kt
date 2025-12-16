package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.util.math.BlockPos
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BlacklistConfigManager {
    private val blacklistPath: Path = Paths.get("config/pebbles-crate/blacklist.txt")

    init {
        createBlacklistFile()
    }

    private fun createBlacklistFile() {
        if (Files.notExists(blacklistPath)) {
            Files.createDirectories(blacklistPath.parent)
            Files.createFile(blacklistPath)
        }
    }

    /**
     * Gets the blacklist as a set of WorldBlockPos.
     * Supports both legacy format (x,y,z) and new format (world_id,x,y,z).
     * Automatically migrates legacy format to new format using overworld as default.
     */
    fun getBlacklist(): Set<WorldBlockPos> {
        var needsMigration = false
        val blacklist = Files.readAllLines(blacklistPath).mapNotNull { line ->
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
                        System.err.println("Invalid blacklist line format: '$line'")
                        null
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error parsing blacklist line '$line': ${e.message}")
                null
            }
        }.toSet()

        // Auto-migrate legacy data to new format
        if (needsMigration) {
            println("[Pebbles-Crates] Migrating blacklist to world-aware format...")
            saveBlacklist(blacklist)
            println("[Pebbles-Crates] Blacklist migration complete! ${blacklist.size} entries migrated to overworld.")
        }

        return blacklist
    }

    /**
     * Saves the blacklist in the new world-aware format.
     */
    private fun saveBlacklist(blacklist: Set<WorldBlockPos>) {
        Files.writeString(
            blacklistPath,
            blacklist.joinToString("\n") { "${it.worldId},${it.pos.x},${it.pos.y},${it.pos.z}" }
        )
    }

    fun addToBlacklist(worldPos: WorldBlockPos) {
        val blacklist = getBlacklist().toMutableSet()
        if (!blacklist.contains(worldPos)) {
            blacklist.add(worldPos)
            saveBlacklist(blacklist)
        }
    }

    fun removeFromBlacklist(worldPos: WorldBlockPos) {
        val blacklist = getBlacklist().toMutableSet()
        blacklist.remove(worldPos)
        saveBlacklist(blacklist)
    }
}
