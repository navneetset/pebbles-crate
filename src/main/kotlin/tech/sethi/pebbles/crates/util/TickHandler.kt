package tech.sethi.pebbles.crates.util

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.LoggerFactory
import java.util.concurrent.PriorityBlockingQueue

/**
 * Server-wide delayed task scheduler, used to drive the crate roll animation.
 *
 * Tasks used to be keyed by the exact `world.time` they were due on, which breaks as soon as that
 * clock stops being monotonic (`/time set`) or a tick is missed: the task was simply never found
 * again and the crate it belonged to stayed marked as in-use forever. The counter here only moves
 * forward and every task with `tick <= now` is drained, so a skipped tick delays a task instead of
 * dropping it. One failing task can no longer take the rest of the animation down with it either.
 */
object TickHandler {
    private val logger = LoggerFactory.getLogger("pebbles-crates")

    private val tasks = PriorityBlockingQueue<Task>(16, compareBy(Task::tick))

    /** Ticks elapsed since server start. Monotonic, unlike `world.time`. */
    @Volatile
    var currentTick = 0L
        private set

    fun register() {
        ServerTickEvents.START_SERVER_TICK.register(ServerTickEvents.StartTick { processTasks() })
    }

    fun schedule(tickDelay: Long, action: () -> Unit) {
        tasks.add(Task(currentTick + tickDelay.coerceAtLeast(0L), action))
    }

    /** Drops everything still queued; used when the server shuts down between animations. */
    fun clear() {
        tasks.clear()
    }

    private fun processTasks() {
        currentTick++

        while (true) {
            val next = tasks.peek() ?: return
            if (next.tick > currentTick) return
            val task = tasks.poll() ?: return

            try {
                task.action()
            } catch (e: Exception) {
                logger.error("[Pebbles-Crates] A scheduled crate task failed", e)
            }
        }
    }
}
