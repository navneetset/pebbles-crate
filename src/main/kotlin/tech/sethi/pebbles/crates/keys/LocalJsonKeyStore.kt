package tech.sethi.pebbles.crates.keys

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Single-server key storage: one json file holding every wallet, read once at start and written
 * back on a debounce. A player handing out twenty keys in a row costs one write, not twenty.
 *
 * Because the whole file is in memory, every wallet is always "ready" - there is nothing to wait
 * for, and a player who has never held a key simply has an empty one.
 */
class LocalJsonKeyStore : CachedKeyStore() {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file = File("config/pebbles-crate/playerdata/keys.json")

    private val writer = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "pebbles-crates-keys-io").apply { isDaemon = true }
    }.apply {
        // Otherwise a debounced write still queued at shutdown fires after the final flush.
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }

    private var pendingWrite: ScheduledFuture<*>? = null

    init {
        read()
    }

    /** Every wallet is in memory, so an unknown player is an empty wallet rather than a missing one. */
    override fun isReady(uuid: UUID): Boolean = true

    override fun load(uuid: UUID, name: String) {
        rememberName(uuid, name)
        synchronized(lock) {
            wallets.getOrPut(uuid) { LinkedHashMap() }
        }
    }

    /** Nothing to release: the file is the cache. Keeping it avoids a re-read on every relog. */
    override fun unload(uuid: UUID) = Unit

    override fun add(uuid: UUID, crate: String, amount: Int): Boolean {
        ensureWallet(uuid)
        return super.add(uuid, crate, amount)
    }

    override fun consume(uuid: UUID, crate: String, amount: Int): Boolean {
        ensureWallet(uuid)
        return super.consume(uuid, crate, amount)
    }

    override fun transfer(from: UUID, to: UUID, crate: String, amount: Int): Boolean {
        ensureWallet(from)
        ensureWallet(to)
        return super.transfer(from, to, crate, amount)
    }

    /** Carries wallets over from a backend that just went away, without overwriting saved balances. */
    fun adoptInSessionState(records: Map<UUID, PlayerKeys>) {
        synchronized(lock) {
            for ((uuid, record) in records) {
                if (record.keys.isEmpty()) continue
                rememberName(uuid, record.name)
                val wallet = wallets.getOrPut(uuid) { LinkedHashMap() }
                for (entry in record.keys) {
                    if (entry.amount > 0) wallet[entry.crate] = entry.amount
                }
            }
            if (records.isNotEmpty()) scheduleWrite()
        }
    }

    private fun ensureWallet(uuid: UUID) {
        synchronized(lock) { wallets.getOrPut(uuid) { LinkedHashMap() } }
    }

    override fun persist(uuid: UUID) = scheduleWrite()

    private fun scheduleWrite() {
        if (pendingWrite?.isDone == false) return
        pendingWrite = try {
            writer.schedule(
                { write() }, GlobalConfigManager.virtualKeys.localWriteDelayMillis, TimeUnit.MILLISECONDS
            )
        } catch (e: Exception) {
            // The executor is already shutting down; the flush on stop covers this.
            null
        }
    }

    /**
     * Cancelling only catches a write that has not started yet; one already running on the IO thread
     * is finished by [write]'s own lock before this one begins, rather than racing it into the same
     * temporary file.
     */
    override fun flush() {
        pendingWrite?.cancel(false)
        write()
    }

    override fun close() {
        flush()
        writer.shutdown()
    }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun read() {
        if (!file.exists()) return

        val records = try {
            gson.fromJson<List<PlayerKeys>>(file.readText(), RECORD_LIST_TYPE)
        } catch (e: Exception) {
            logger.error("[Pebbles-Crates] keys.json is not readable, starting with empty key balances", e)
            return
        } ?: return

        for (record in records) {
            if (record == null || record.uuid == null) continue

            val uuid = try {
                UUID.fromString(record.uuid)
            } catch (e: IllegalArgumentException) {
                logger.warn("[Pebbles-Crates] Skipping key balances for unreadable uuid '${record.uuid}'")
                continue
            }

            cacheWallet(uuid, record.name ?: "", record.keys.orEmpty().associate { it.crate to it.amount })
        }

        logger.info("[Pebbles-Crates] Loaded virtual key balances for ${wallets.size} player(s)")
    }

    /**
     * Written to a temporary file and moved into place: a crash mid-write would otherwise leave
     * every player's keys behind a truncated json file. Synchronized because the shutdown flush
     * writes from the game thread while the debounced write may still be running on the IO one.
     */
    @Synchronized
    private fun write() {
        val json = synchronized(lock) {
            gson.toJson(wallets.entries.map { (uuid, wallet) -> toRecord(uuid, wallet) })
        }

        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            logger.error("[Pebbles-Crates] Could not save virtual key balances", e)
        }
    }

    private companion object {
        val RECORD_LIST_TYPE = object : TypeToken<List<PlayerKeys>>() {}.type
    }
}
