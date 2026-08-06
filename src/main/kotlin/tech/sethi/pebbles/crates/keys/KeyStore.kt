package tech.sethi.pebbles.crates.keys

import java.util.*

/**
 * A player's virtual key wallet, keyed by **crateName** - never by the screen name. A key granted
 * under a display name cannot be spent by a crate that looks itself up by id, which is how the
 * donor mod ended up with unspendable keys.
 *
 * Every implementation answers reads from memory: [getBalance] is called from the block interaction
 * handler and must never touch a socket or a disk.
 */
interface KeyStore {
    /**
     * Whether this player's wallet is in memory yet. Local storage is always ready; a shared
     * database is only ready once the player's document has arrived, and spending before that would
     * write a wallet that never had the other server's keys in it.
     */
    fun isReady(uuid: UUID): Boolean

    /** Every crate this player holds at least one key for. Reads the cache, never blocks. */
    fun getBalances(uuid: UUID): Map<String, Int>

    fun getBalance(uuid: UUID, crate: String): Int

    /** Adds (or, with a negative [amount], removes) keys. Floors at zero and prunes empty entries. */
    fun add(uuid: UUID, crate: String, amount: Int): Boolean

    /** Takes [amount] keys if the player has that many. Returns false and changes nothing otherwise. */
    fun consume(uuid: UUID, crate: String, amount: Int = 1): Boolean

    /** Moves keys between two wallets, or returns false having moved nothing. */
    fun transfer(from: UUID, to: UUID, crate: String, amount: Int): Boolean

    /** Brings a player's wallet into memory, e.g. when they join. */
    fun load(uuid: UUID, name: String)

    /** Persists and forgets a player's wallet, so their next login re-reads it. */
    fun unload(uuid: UUID)

    /** Everything currently in memory, used to hand state over when a backend degrades. */
    fun snapshot(): Map<UUID, PlayerKeys>

    /** Writes out anything still pending. */
    fun flush()

    /** Flushes and releases the backend. */
    fun close()
}

/**
 * On-disk shape, identical for the local file and for MongoDB so the same wallet can be moved
 * between them - and so the collections the donor mod already wrote are read back unchanged.
 */
data class PlayerKeys(
    val uuid: String,
    /** Last known player name. Only there to make the raw data readable; nothing looks players up by it. */
    val name: String,
    val keys: MutableList<CrateKeyAmount> = mutableListOf()
)

data class CrateKeyAmount(
    val crate: String, var amount: Int
)
