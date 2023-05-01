package tech.sethi.pebbles.crates.util

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import tech.sethi.pebbles.crates.PebblesCrate

class TickHandler {
    init {
        ServerTickEvents.START_SERVER_TICK.register(ServerTickEvents.StartTick {
            processTasks(it)
        })
    }

    private fun processTasks(server: MinecraftServer) {
        val currentTick = server.worlds.first().time

        PebblesCrate.tasks[currentTick]?.let { tasks ->
            for (task in tasks) {
                task.action()
            }
            PebblesCrate.tasks.remove(currentTick) // Remove the executed tasks from the storage
        }
    }
}