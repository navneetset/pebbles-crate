package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.util.math.BlockPos
import tech.sethi.pebbles.crates.PebblesCrate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.HashMap
import java.util.HashSet

class BlacklistConfigManager {
    private val blacklistPath: Path = Paths.get("config/pebbles-crate/blacklist.txt")
    private val blacklist = HashSet<BlockPos>()
    private var isReadDone: Boolean = false

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
        if (isReadDone) return blacklist
        blacklist.addAll(Files.readAllLines(blacklistPath).mapNotNull { line ->
            try {
                val (x, y, z) = line.split(',').map { it.trim().toInt() }
                BlockPos(x, y, z)
            } catch (e: Exception) {
                System.err.println("Error parsing line '$line': ${e.message}")
                null
            }
        }.toSet());
        isReadDone = true
        return blacklist
    }


    fun addToBlacklist(pos: BlockPos) {
        if (!blacklist.contains(pos)) {
            blacklist.add(pos)
            Files.writeString(blacklistPath, blacklist.joinToString("\n") { "${it.x},${it.y},${it.z}" })
        }
    }


    fun removeFromBlacklist(pos: BlockPos) {
        blacklist.remove(pos)
        Files.writeString(blacklistPath, blacklist.joinToString("\n") { "${it.x},${it.y},${it.z}" })
    }
}
