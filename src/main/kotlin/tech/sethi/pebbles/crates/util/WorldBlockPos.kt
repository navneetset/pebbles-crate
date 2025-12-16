package tech.sethi.pebbles.crates.util

import net.minecraft.util.math.BlockPos

data class WorldBlockPos(
    val worldId: String,
    val pos: BlockPos
) {
    fun encode(): String {
        return "$worldId:${pos.asLong()}"
    }

    companion object {
        fun decode(encoded: String, defaultWorld: String = "minecraft:overworld"): WorldBlockPos {
            val asLong = encoded.toLongOrNull()
            if (asLong != null) {
                return WorldBlockPos(defaultWorld, BlockPos.fromLong(asLong))
            }

            val lastColon = encoded.lastIndexOf(':')
            if (lastColon == -1) {
                throw IllegalArgumentException("Invalid WorldBlockPos format: $encoded")
            }

            val worldId = encoded.substring(0, lastColon)
            val posLong = encoded.substring(lastColon + 1).toLong()
            return WorldBlockPos(worldId, BlockPos.fromLong(posLong))
        }

        fun of(worldId: String, pos: BlockPos): WorldBlockPos {
            return WorldBlockPos(worldId, pos)
        }
    }
}
