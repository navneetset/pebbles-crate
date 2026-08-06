package tech.sethi.pebbles.crates.commands

import net.minecraft.server.network.ServerPlayerEntity
import tech.sethi.pebbles.crates.config.Messages
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiting for the player-facing key commands.
 *
 * One bucket per command rather than one per player: sharing a single bucket meant opening the key
 * screen also blocked sending a key for five seconds, which reads as the command being broken.
 */
object CommandCooldowns {
    private val buckets = ConcurrentHashMap<String, MutableMap<UUID, Long>>()

    /**
     * Stamps the player's last use and returns true when the command may run. Reports the wait to
     * the player itself when it may not.
     */
    fun allow(player: ServerPlayerEntity, bucket: String, seconds: Int): Boolean {
        val uses = buckets.getOrPut(bucket) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        val cooldownMillis = seconds * 1000L

        val last = uses[player.uuid]
        if (last != null && now - last < cooldownMillis) {
            val remaining = ((cooldownMillis - (now - last)) / 1000L) + 1
            Messages.send(player, "command.rate-limited", "seconds" to "$remaining")
            return false
        }

        uses[player.uuid] = now

        // Players who left keep an entry otherwise, and the map is never rebuilt.
        if (uses.size > PRUNE_THRESHOLD) {
            uses.entries.removeIf { now - it.value > cooldownMillis }
        }
        return true
    }

    fun clear() = buckets.clear()

    private const val PRUNE_THRESHOLD = 256
}
