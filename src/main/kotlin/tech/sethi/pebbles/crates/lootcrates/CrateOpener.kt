package tech.sethi.pebbles.crates.lootcrates

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.config.StyleResolver
import tech.sethi.pebbles.crates.keys.KeyProvider
import tech.sethi.pebbles.crates.keys.KeyProviders
import tech.sethi.pebbles.crates.util.WorldBlockPos

/**
 * The one path that opens a crate, shared by right-clicking a crate with a physical key and by the
 * "Open Crate" button on a virtual crate's preview screen.
 *
 * The order matters and is the same for both: the prize is rolled *before* the key is taken, so a
 * crate with nothing to give cannot eat one.
 */
object CrateOpener {
    fun open(
        player: ServerPlayerEntity,
        world: World,
        worldBlockPos: WorldBlockPos,
        crateConfig: CrateConfig,
        provider: KeyProvider = KeyProviders.forCrate(crateConfig)
    ): Boolean {
        if (!provider.hasKey(player, crateConfig)) {
            player.sendMessage(provider.missingKeyMessage(player, crateConfig), false)
            return false
        }

        if (PebblesCrate.cratesInUse.containsKey(worldBlockPos)) {
            Messages.send(player, "crate.in-use")
            return false
        }

        val displayName = crateConfig.screenName ?: crateConfig.crateName
        val crateEventHandler = CrateEventHandler(
            world,
            worldBlockPos,
            player,
            crateConfig.prize,
            PebblesCrate.cratesInUse,
            PebblesCrate.playerCooldowns,
            displayName,
            StyleResolver.at(worldBlockPos, crateConfig)
        )

        // Reports the remaining cooldown to the player itself.
        if (!crateEventHandler.canOpenCrate()) return false

        val finalPrize = crateEventHandler.weightedRandomSelection(crateConfig.prize)
        if (finalPrize == null) {
            Messages.send(player, "crate.no-prizes")
            return false
        }

        if (!provider.consumeKey(player, crateConfig)) {
            player.sendMessage(provider.missingKeyMessage(player, crateConfig), false)
            return false
        }

        crateEventHandler.showPrizesAnimation(finalPrize)
        crateEventHandler.updatePlayerCooldown()
        return true
    }
}
