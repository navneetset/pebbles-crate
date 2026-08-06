package tech.sethi.pebbles.crates.util

/** A unit of work queued to run once the server tick counter reaches [tick]. */
data class Task(val tick: Long, val action: () -> Unit)
