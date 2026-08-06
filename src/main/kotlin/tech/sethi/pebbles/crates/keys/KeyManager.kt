package tech.sethi.pebbles.crates.keys

import net.minecraft.server.MinecraftServer
import net.minecraft.server.PlayerManager
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.util.TickHandler
import java.util.*

/**
 * Owns the key store for the server's lifetime and decides, per crate, whether keys are virtual.
 *
 * Whether the feature exists at all is read straight from the config file ([featureEnabled]) because
 * commands are registered before the server object exists. Everything else needs [start] to have run.
 */
object KeyManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")

    @Volatile
    private var store: KeyStore? = null

    /** True when config.json turns virtual keys on. Safe to ask before the server exists. */
    val featureEnabled: Boolean
        get() = GlobalConfigManager.virtualKeys.enabled

    /** True once a store is actually backing the feature. */
    val active: Boolean
        get() = store != null

    /**
     * Whether this crate spends virtual keys. A crate may opt back out with `"virtualKey": false`;
     * a missing field inherits the global switch, which is what keeps old crate JSON valid.
     */
    fun isVirtual(crateConfig: CrateConfig): Boolean = store != null && (crateConfig.virtualKey ?: true)

    fun start(server: MinecraftServer) {
        if (!featureEnabled || store != null) return

        val settings = GlobalConfigManager.virtualKeys
        if (settings.storage.equals("mongodb", ignoreCase = true)) {
            val mongo = MongoKeyStore(settings.mongo) { reason, error -> degradeToLocal(server, reason, error) }
            store = mongo
            // Wallets can only be fetched once the connection is up; nobody is online before then anyway.
            mongo.connect { TickHandler.schedule(0) { loadOnlinePlayers(server) } }
        } else {
            store = LocalJsonKeyStore()
            logger.info("[Pebbles-Crates] Virtual keys enabled, using local json storage")
        }
        // Nobody can be online yet - the player manager does not even exist at this point. Wallets
        // arrive through the join event, or through loadOnlinePlayers when a store swaps in later.
    }

    fun stop() {
        val current = store ?: return
        store = null
        try {
            current.close()
        } catch (e: Exception) {
            logger.error("[Pebbles-Crates] Could not shut the key store down cleanly", e)
        }
    }

    /**
     * MongoDB was configured but is not answering. Rather than leave every crate unopenable, the
     * server carries on against the local file; anything granted while the connection was being
     * established comes along so no key is silently lost.
     */
    private fun degradeToLocal(server: MinecraftServer, reason: String, error: Throwable?) {
        val failed = store as? MongoKeyStore ?: return

        logger.error(
            "[Pebbles-Crates] MongoDB is unavailable for virtual keys ($reason); " + "falling back to local json storage. Keys will NOT be shared across servers until this is fixed.",
            error
        )

        // Held across both steps: a grant that lands between the snapshot and the swap would
        // otherwise be written into a store nobody reads from again.
        val local = LocalJsonKeyStore()
        failed.whileFrozen {
            local.adoptInSessionState(failed.snapshot())
            store = local
        }

        try {
            failed.close()
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Could not close the failed MongoDB store: ${e.message}")
        }

        // This runs on the store's IO thread; the player list belongs to the game thread.
        TickHandler.schedule(0) { loadOnlinePlayers(server) }
    }

    /** Only reachable once a store swaps in mid-run; the player manager may still be absent. */
    private fun loadOnlinePlayers(server: MinecraftServer) {
        val current = store ?: return
        val playerManager: PlayerManager? = server.playerManager
        for (player in playerManager?.playerList.orEmpty()) {
            current.load(player.uuid, player.name.string)
        }
    }

    fun onPlayerJoin(player: ServerPlayerEntity) {
        store?.load(player.uuid, player.name.string)
    }

    fun onPlayerQuit(player: ServerPlayerEntity) {
        store?.unload(player.uuid)
    }

    /**
     * Whether this player's wallet has arrived. Only ever false for a shared database in the moment
     * between joining and the first read landing; spending then would write a wallet that never had
     * the other server's keys in it.
     */
    fun isReady(uuid: UUID): Boolean = store?.isReady(uuid) ?: false

    fun getBalances(uuid: UUID): Map<String, Int> = store?.getBalances(uuid) ?: emptyMap()

    fun getBalance(uuid: UUID, crate: String): Int = store?.getBalance(uuid, crate) ?: 0

    fun add(uuid: UUID, crate: String, amount: Int): Boolean = store?.add(uuid, crate, amount) ?: false

    fun consume(uuid: UUID, crate: String, amount: Int = 1): Boolean = store?.consume(uuid, crate, amount) ?: false

    fun transfer(from: UUID, to: UUID, crate: String, amount: Int): Boolean =
        store?.transfer(from, to, crate, amount) ?: false

    fun flush() {
        store?.flush()
    }
}
