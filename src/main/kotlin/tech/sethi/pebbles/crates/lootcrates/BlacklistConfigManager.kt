package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.util.math.BlockPos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

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

    fun getBlacklist(): Set<BlockPos> {
        return Files.readAllLines(blacklistPath).mapNotNull { line ->
            try {
                val (x, y, z) = line.split(',').map { it.trim().toInt() }
                BlockPos(x, y, z)
            } catch (e: Exception) {
                System.err.println("Error parsing line '$line': ${e.message}")
                null
            }
        }.toSet()
    }


    fun addToBlacklist(pos: BlockPos) {
        val blacklist = getBlacklist().toMutableSet()
        if (!blacklist.contains(pos)) {
            blacklist.add(pos)
            Files.writeString(blacklistPath, blacklist.joinToString("\n") { "${it.x},${it.y},${it.z}" })
        }
    }


    fun removeFromBlacklist(pos: BlockPos) {
        val blacklist = getBlacklist().toMutableSet()
        blacklist.remove(pos)
        Files.writeString(blacklistPath, blacklist.joinToString("\n") { "${it.x},${it.y},${it.z}" })
    }
}
