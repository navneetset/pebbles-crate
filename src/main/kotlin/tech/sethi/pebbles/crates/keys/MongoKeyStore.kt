package tech.sethi.pebbles.crates.keys

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.config.MongoConfig
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Cross-server key storage. Wallets live in one shared collection so a player carries the same keys
 * onto every server pointed at it.
 *
 * The connection, the index and every read and write happen on [io]; the game thread only ever
 * touches the in-memory cache it inherits from [CachedKeyStore]. A wallet is fetched when its owner
 * joins and dropped when they leave, which is also what makes the wallet current: the copy this
 * server holds is only authoritative while the player is on it.
 *
 * The document shape - `{uuid, name, keys: [{crate, amount}]}` - matches what the older virtual-key
 * mod wrote, so an existing PlayerKeys collection keeps working with no migration.
 */
class MongoKeyStore(
    private val config: MongoConfig,
    /** Called once, from the IO thread, when this store has decided the database is not usable. */
    private val onUnavailable: (String, Throwable?) -> Unit
) : CachedKeyStore() {
    private val logger = LoggerFactory.getLogger("pebbles-crates")

    @Volatile
    private var ioThread: Thread? = null

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pebbles-crates-mongo-io").apply {
            isDaemon = true
            ioThread = this
        }
    }

    @Volatile
    private var client: MongoClient? = null

    @Volatile
    private var collection: MongoCollection<Document>? = null

    @Volatile
    private var unavailable = false

    /** Connecting is itself IO, so it happens on the executor and start-up never waits on a socket. */
    fun connect(onConnected: () -> Unit) {
        submit {
            val timeout = config.serverSelectionTimeoutSeconds.toLong()
            val settings =
                MongoClientSettings.builder().applyConnectionString(ConnectionString(config.uri)).applyToClusterSettings {
                    it.serverSelectionTimeout(timeout, TimeUnit.SECONDS)
                }.applyToSocketSettings {
                    it.connectTimeout(timeout.toInt(), TimeUnit.SECONDS)
                }.build()

            val opened = MongoClients.create(settings)
            try {
                val database = opened.getDatabase(config.database)
                // Nothing above actually opens a socket; this is the call that proves the server is there.
                database.runCommand(Document("ping", 1))

                val openedCollection = database.getCollection(config.collection)
                createUuidIndex(openedCollection)

                client = opened
                collection = openedCollection
                logger.info("[Pebbles-Crates] Connected to MongoDB for virtual keys (${config.database}.${config.collection})")
                onConnected()
            } catch (e: Exception) {
                try {
                    opened.close()
                } catch (ignored: Exception) {
                }
                throw e
            }
        }
    }

    /**
     * The donor mod scanned the whole collection for every lookup. One unique index turns that into
     * a point query - and stops two servers from racing a wallet into existence twice.
     */
    private fun createUuidIndex(collection: MongoCollection<Document>) {
        try {
            collection.createIndex(Indexes.ascending("uuid"), IndexOptions().unique(true))
        } catch (e: Exception) {
            // Pre-existing duplicates, or a user without index rights. Slower, but still correct.
            logger.warn("[Pebbles-Crates] Could not create the unique uuid index on ${config.collection}: ${e.message}")
        }
    }

    override fun load(uuid: UUID, name: String) {
        rememberName(uuid, name)
        submit {
            val collection = collection ?: return@submit
            val document = collection.find(Filters.eq("uuid", uuid.toString())).first()
            cacheWallet(uuid, name, document?.let(::readKeys) ?: emptyMap())
        }
    }

    override fun persist(uuid: UUID) {
        // Snapshotted here, on the game thread inside the mutation lock: the IO thread must never
        // read a wallet that a later interaction is already changing.
        val record = toRecord(uuid, wallets[uuid] ?: emptyMap())
        submit {
            val collection = collection ?: return@submit
            collection.replaceOne(
                Filters.eq("uuid", record.uuid), toDocument(record), ReplaceOptions().upsert(true)
            )
        }
    }

    override fun flush() {
        // Draining is what a flush is - but the failure path closes this store from the IO thread
        // itself, where a task queued behind the running one can only ever time out.
        if (Thread.currentThread() === ioThread) return

        try {
            val drained = io.submit { }
            drained.get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Pending key writes did not finish in time: ${e.message}")
        }
    }

    override fun close() {
        flush()
        io.shutdown()
        try {
            client?.close()
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Could not close the MongoDB client cleanly: ${e.message}")
        }
        client = null
        collection = null
    }

    /**
     * Every database call funnels through here so that a failure can only ever cost a log line and,
     * at worst, a switch to local storage - never an exception on the game thread.
     */
    private fun submit(action: () -> Unit) {
        if (unavailable) return

        try {
            io.execute {
                if (unavailable) return@execute
                try {
                    action()
                } catch (e: Exception) {
                    markUnavailable("a key store operation failed", e)
                }
            }
        } catch (e: Exception) {
            markUnavailable("the key store worker is not accepting work", e)
        }
    }

    private fun markUnavailable(reason: String, error: Throwable?) {
        if (unavailable) return
        unavailable = true
        onUnavailable(reason, error)
    }

    private fun readKeys(document: Document): Map<String, Int> {
        val entries = try {
            document.getList("keys", Document::class.java)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Ignoring an unreadable keys array for uuid ${document.getString("uuid")}")
            null
        } ?: return emptyMap()

        val wallet = LinkedHashMap<String, Int>()
        for (entry in entries) {
            val crate = entry.getString("crate") ?: continue
            val amount = (entry.get("amount") as? Number)?.toInt() ?: continue
            if (amount > 0) wallet[crate] = amount
        }
        return wallet
    }

    private fun toDocument(record: PlayerKeys): Document = Document("uuid", record.uuid).append("name", record.name)
        .append("keys", record.keys.map { Document("crate", it.crate).append("amount", it.amount) })

    private companion object {
        const val FLUSH_TIMEOUT_SECONDS = 10L
    }
}
