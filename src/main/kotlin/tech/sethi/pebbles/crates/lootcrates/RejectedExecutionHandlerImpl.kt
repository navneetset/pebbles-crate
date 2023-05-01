package tech.sethi.pebbles.crates.lootcrates

import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class RejectedExecutionHandlerImpl : RejectedExecutionHandler {
    override fun rejectedExecution(runnable: Runnable, threadPoolExecutor: ThreadPoolExecutor) {
        try {
            threadPoolExecutor.queue.offer(runnable, 10L, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // Log the exception or handle it appropriately
        }
    }
}
