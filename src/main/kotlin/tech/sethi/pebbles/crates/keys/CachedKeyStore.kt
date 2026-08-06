package tech.sethi.pebbles.crates.keys

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared body of both stores: the wallet lives in memory and the backend only ever sees
 * write-behind. Reads therefore cost a map lookup, which is what lets [getBalance] sit on the block
 * interaction path.
 *
 * All mutations are serialised on [lock] so a transfer cannot interleave with a crate opening, and
 * so [persist] always sees a consistent snapshot of the wallet it is about to write.
 */
abstract class CachedKeyStore : KeyStore {
    protected val lock = Any()
    protected val wallets = ConcurrentHashMap<UUID, MutableMap<String, Int>>()
    protected val names = ConcurrentHashMap<UUID, String>()

    /** Writes the given player's wallet out. Called on the game thread inside [lock]; must not block. */
    protected abstract fun persist(uuid: UUID)

    override fun isReady(uuid: UUID): Boolean = wallets.containsKey(uuid)

    override fun getBalances(uuid: UUID): Map<String, Int> = wallets[uuid]?.toMap() ?: emptyMap()

    override fun getBalance(uuid: UUID, crate: String): Int = wallets[uuid]?.get(crate) ?: 0

    override fun add(uuid: UUID, crate: String, amount: Int): Boolean {
        if (amount == 0) return true

        synchronized(lock) {
            val wallet = wallets[uuid] ?: return false
            applyDelta(wallet, crate, amount)
            persist(uuid)
            return true
        }
    }

    override fun consume(uuid: UUID, crate: String, amount: Int): Boolean {
        if (amount <= 0) return false

        synchronized(lock) {
            val wallet = wallets[uuid] ?: return false
            if ((wallet[crate] ?: 0) < amount) return false
            applyDelta(wallet, crate, -amount)
            persist(uuid)
            return true
        }
    }

    override fun transfer(from: UUID, to: UUID, crate: String, amount: Int): Boolean {
        if (amount <= 0 || from == to) return false

        synchronized(lock) {
            val source = wallets[from] ?: return false
            val target = wallets[to] ?: return false
            if ((source[crate] ?: 0) < amount) return false

            applyDelta(source, crate, -amount)
            applyDelta(target, crate, amount)
            persist(from)
            persist(to)
            return true
        }
    }

    override fun snapshot(): Map<UUID, PlayerKeys> = synchronized(lock) {
        wallets.entries.associate { (uuid, wallet) -> uuid to toRecord(uuid, wallet) }
    }

    /**
     * Runs [action] with this store's mutation lock held. Only used to retire a store: carrying its
     * wallets over and dropping it have to look like one step, or a grant landing in between would
     * be written to a store nothing reads again.
     */
    fun <T> whileFrozen(action: () -> T): T = synchronized(lock) { action() }

    override fun unload(uuid: UUID) {
        synchronized(lock) {
            persist(uuid)
            wallets.remove(uuid)
            names.remove(uuid)
        }
    }

    /** Puts a wallet read back from a backend into the cache, without clobbering live changes. */
    protected fun cacheWallet(uuid: UUID, name: String, keys: Map<String, Int>) {
        synchronized(lock) {
            if (wallets.containsKey(uuid)) return
            names[uuid] = name
            wallets[uuid] = LinkedHashMap(keys.filterValues { it > 0 })
        }
    }

    protected fun rememberName(uuid: UUID, name: String) {
        if (name.isNotEmpty()) names[uuid] = name
    }

    protected fun toRecord(uuid: UUID, wallet: Map<String, Int>): PlayerKeys = PlayerKeys(
        uuid.toString(),
        names[uuid] ?: "",
        wallet.entries.map { CrateKeyAmount(it.key, it.value) }.toMutableList()
    )

    /** A balance can never go below zero, and an emptied crate leaves no entry behind. */
    private fun applyDelta(wallet: MutableMap<String, Int>, crate: String, delta: Int) {
        val updated = (wallet[crate] ?: 0) + delta
        if (updated <= 0) wallet.remove(crate) else wallet[crate] = updated
    }
}
