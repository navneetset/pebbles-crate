package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Items
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.commands.CommandCooldowns
import tech.sethi.pebbles.crates.commands.CrateCommand
import tech.sethi.pebbles.crates.commands.KeyCommand
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager
import tech.sethi.pebbles.crates.keys.PhysicalKeyProvider
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.lootcrates.CrateOpener
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PebblesCrate : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"

    /** How often the stale-state sweep runs, in ticks. */
    private const val SWEEP_INTERVAL_TICKS = 100L

    /** Crates currently mid-roll, mapped to the millisecond their animation started. */
    val cratesInUse: MutableMap<WorldBlockPos, Long> = ConcurrentHashMap()
    val playerCooldowns: MutableMap<UUID, Long> = ConcurrentHashMap()

    var server: MinecraftServer? = null

    var nbtOps: RegistryOps<NbtElement>? = null

    /**
     * Gets the world identifier string from a World object.
     * Format: "namespace:path" (e.g., "minecraft:overworld", "minecraft:the_nether")
     */
    fun getWorldId(world: World): String {
        return world.registryKey.value.toString()
    }

    override fun onInitialize() {
        logger.info("Initializing Pebbles Loot Crates!")

        //create /config/pebbles-crate/crates if it doesn't exist
        CrateConfigManager.createCratesFolder()

        TickHandler.register()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CrateCommand.register(dispatcher)

            // Commands cannot be added after this point, and the config file can be read without a
            // server, so the switch is consulted here: with virtual keys off, /keys never exists.
            if (KeyManager.featureEnabled) {
                KeyCommand.register(dispatcher)
            }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            KeyManager.onPlayerJoin(handler.player)
        }

        // Persists the wallet and drops it, so a player rejoining - possibly on another server -
        // reads whatever the shared store holds by then rather than this server's stale copy.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            KeyManager.onPlayerQuit(handler.player)
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
            if (world.isClient || hand != Hand.MAIN_HAND) {
                return@UseBlockCallback ActionResult.PASS
            }

            // Create a world-aware position for the clicked block
            val worldId = getWorldId(world)
            val worldBlockPos = WorldBlockPos(worldId, hitResult.blockPos)

            // Check if the clicked position is in the crate data
            val registeredCrateName = CrateDataManager.getCrateName(worldBlockPos)
            if (registeredCrateName != null) {
                val crateConfig = CrateConfigManager.getCrateConfig(registeredCrateName)

                if (crateConfig != null) {
                    val serverPlayer = player as ServerPlayerEntity
                    val displayName = crateConfig.screenName ?: crateConfig.crateName

                    // A virtual crate is opened from its preview screen, so the block itself always
                    // opens that. A physical crate keeps the click-with-key-in-hand behaviour.
                    if (!KeyManager.isVirtual(crateConfig) && PhysicalKeyProvider.hasKey(serverPlayer, crateConfig)) {
                        CrateOpener.open(serverPlayer, world, worldBlockPos, crateConfig, PhysicalKeyProvider)
                        return@UseBlockCallback ActionResult.SUCCESS
                    }

                    // Keys minted before the crate went virtual are dead weight until they are converted.
                    if (KeyManager.isVirtual(crateConfig) && PhysicalKeyProvider.hasKey(serverPlayer, crateConfig)) {
                        Messages.send(serverPlayer, "crate.convert-hint", "player_name" to serverPlayer.name.string)
                    }

                    player.openHandledScreen(
                        PrizeDisplayScreenHandlerFactory(
                            ParseableName(displayName).returnMessageAsStyledText(), crateConfig, world, worldBlockPos
                        )
                    )
                    return@UseBlockCallback ActionResult.SUCCESS
                }
            } else {
                // Assign a new crate if the player is holding a named paper
                val heldStack = player.mainHandStack
                if (heldStack.item == Items.PAPER && heldStack.componentChanges.get(DataComponentTypes.CUSTOM_NAME) != null && heldStack.get(
                        DataComponentTypes.CUSTOM_DATA
                    )?.nbt?.contains(
                        "CrateName"
                    ) == true
                ) {
                    val crateName = heldStack.get(DataComponentTypes.CUSTOM_DATA)?.nbt?.getString("CrateName")
                        ?: return@UseBlockCallback ActionResult.PASS

                    if (!PermissionUtil.isAdmin(player)) {
                        Messages.send(player, "crate.no-permission-place")
                        return@UseBlockCallback ActionResult.SUCCESS
                    }

                    CrateDataManager.assignCrate(worldBlockPos, crateName)

                    Messages.send(
                        player,
                        "crate.assigned",
                        "crate_name" to crateName,
                        "position" to hitResult.blockPos.toShortString(),
                        "world" to worldId
                    )
                    return@UseBlockCallback ActionResult.SUCCESS
                }
            }

            ActionResult.PASS
        })


        // A registered crate is admin property: a survival player mining it would otherwise silently
        // unregister it, and the block would come back as an ordinary chest on the next placement.
        PlayerBlockBreakEvents.BEFORE.register(PlayerBlockBreakEvents.Before { world, player, pos, _, _ ->
            if (world.isClient) return@Before true

            val worldBlockPos = WorldBlockPos(getWorldId(world), pos)
            if (CrateDataManager.getCrateName(worldBlockPos) == null) return@Before true
            if (PermissionUtil.isAdmin(player)) return@Before true

            Messages.send(player, "crate.no-permission-break")
            false
        })

        PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { world, player, pos, _, _ ->
            if (world.isClient) return@After

            // Create a world-aware position for the broken block
            val worldId = getWorldId(world)
            val worldBlockPos = WorldBlockPos(worldId, pos)

            // Check if the broken block position is in the crate data
            if (CrateDataManager.removeCrate(worldBlockPos)) {
                Messages.send(
                    player, "crate.removed", "position" to pos.toShortString(), "world" to worldId
                )
            }
        })

        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            for (world in server.worlds) {
                if (world is ServerWorld) {
                    spawnParticlesForAllCrates(world)
                }
            }
            CrateParticles.updateTimers()

            if (TickHandler.currentTick % SWEEP_INTERVAL_TICKS == 0L) {
                sweepStaleState()
            }
        })

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            this.server = server
            nbtOps = server.registryManager.getOps(NbtOps.INSTANCE)
            reloadConfigs()
            KeyManager.start(server)
        }

        // Nothing here survives a world: the singletons outlive an integrated server otherwise.
        ServerLifecycleEvents.SERVER_STOPPED.register {
            KeyManager.stop()
            TickHandler.clear()
            CommandCooldowns.clear()
            cratesInUse.clear()
            playerCooldowns.clear()
            this.server = null
        }
    }

    /**
     * Frees crates whose animation never finished (an exception, a crash mid-roll, a world unload)
     * and drops cooldown entries for players who are long past theirs, so neither map can grow
     * without bound over an uptime.
     */
    private fun sweepStaleState() {
        val now = System.currentTimeMillis()

        val freed = cratesInUse.entries.removeIf { now - it.value > GlobalConfigManager.maxAnimationMillis }
        if (freed) {
            logger.warn("[Pebbles-Crates] Released a crate whose roll animation never finished")
        }

        playerCooldowns.entries.removeIf { now - it.value > GlobalConfigManager.crate.cooldownMillis }
    }

    /** Re-reads every config file from disk. Called at server start and by /padmin reload. */
    fun reloadConfigs() {
        GlobalConfigManager.reload()
        Messages.reload()
        CrateConfigManager.loadCrateConfigs()
        CrateDataManager.reload()
        BlacklistConfigManager.reload()
    }

    private fun spawnParticlesForAllCrates(world: ServerWorld) {
        val players = world.players
        if (players.isEmpty()) return

        val crates = CrateDataManager.cratesInWorld(getWorldId(world))
        if (crates.isEmpty()) return

        val blacklist = BlacklistConfigManager.getBlacklist()
        val radiusSquared = GlobalConfigManager.particleRadiusSquared

        for (worldBlockPos in crates) {
            // Skip crates in the blacklist
            if (worldBlockPos in blacklist) continue

            val pos = worldBlockPos.pos
            // Never load the chunk just to draw particles - a crate nobody can see does not need them
            if (!world.isChunkLoaded(pos.x shr 4, pos.z shr 4)) continue

            for (player in players) {
                // Only players within the configured radius of the crate block get the particles
                if (player.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= radiusSquared) {
                    CrateParticles.spawnCrossSpiralsParticles(player, pos, world)
                }
            }
        }
    }
}