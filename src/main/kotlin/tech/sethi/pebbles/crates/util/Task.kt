package tech.sethi.pebbles.crates.util

import net.minecraft.server.world.ServerWorld

data class Task(val world: ServerWorld, val tick: Long, val action: () -> Unit)
