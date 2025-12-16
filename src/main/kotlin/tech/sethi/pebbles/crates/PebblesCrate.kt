package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.lootcrates.CrateEventHandler
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.util.*
import tech.sethi.pebbleslootcrate.commands.CrateCommand
import java.util.*

object PebblesCrate : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"
    val cratesInUse = Collections.synchronizedSet(mutableSetOf<WorldBlockPos>())
    val playerCooldowns: MutableMap<UUID, Long> = Collections.synchronizedMap(mutableMapOf())
    val tasks: MutableMap<Long, MutableList<Task>> = mutableMapOf()

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

        TickHandler()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CrateCommand.register(dispatcher)
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
            if (world.isClient || hand != Hand.MAIN_HAND) {
                return@UseBlockCallback ActionResult.PASS
            }

            val crateDataManager = CrateDataManager()
            val savedCrateData = crateDataManager.loadCrateData().toMutableMap()

            // Create a world-aware position for the clicked block
            val worldId = getWorldId(world)
            val worldBlockPos = WorldBlockPos(worldId, hitResult.blockPos)

            // Check if the clicked position is in the crate data
            if (worldBlockPos in savedCrateData) {
                var crateName = savedCrateData[worldBlockPos]
                val crateConfig = CrateConfigManager.getCrateConfig(crateName!!)

                if (crateConfig != null && crateConfig.screenName != null) {
                    crateName = crateConfig.screenName
                }

                val parsedKey = Registries.ITEM.get(
                    Identifier.tryParse(
                        crateConfig?.crateKey?.material ?: "minecraft:gold_nugget"
                    )
                )
                val parseKeyStack = ItemStack(parsedKey)
                val crateKeyLore = crateConfig?.crateKey?.lore?.map { Text.of(it) }
                if (crateKeyLore != null) {
                    setLore(parseKeyStack, crateKeyLore)
                }
                if (crateConfig != null) {
                    val nbt = NbtComponent.of(NbtCompound().apply { putString("CrateName", crateName) })
                    parseKeyStack.set(DataComponentTypes.CUSTOM_DATA, nbt)
                }

                if (crateConfig != null) {
                    val heldStack = player.mainHandStack
                    val heldStackNbt = heldStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()
                    if (heldStack.item == parseKeyStack.item && heldStackNbt != null && heldStackNbt.getString(
                            "CrateName"
                        ) == crateConfig.crateName
                    ) {
                        if (cratesInUse.contains(worldBlockPos)) {
                            player.sendMessage(
                                Text.literal("Someone is already using this crate!").formatted(Formatting.RED), false
                            )
                            return@UseBlockCallback ActionResult.SUCCESS
                        }

                        val crateEventHandler = CrateEventHandler(
                            world,
                            worldBlockPos,
                            player as ServerPlayerEntity,
                            crateConfig.prize,
                            cratesInUse,
                            playerCooldowns,
                            crateName
                        )

                        if (crateEventHandler.canOpenCrate()) {
                            heldStack.decrement(1)
                            val finalPrize = crateEventHandler.weightedRandomSelection(crateConfig.prize)
                            crateEventHandler.showPrizesAnimation(finalPrize)
                            crateEventHandler.updatePlayerCooldown()
                        }


                        // Floating item will be spawned in the CrateEventHandler's init block
                    } else {
                        // Open crate preview GUI
                        player.openHandledScreen(
                            PrizeDisplayScreenHandlerFactory(
                                ParseableName("$crateName").returnMessageAsStyledText(), crateConfig
                            )
                        )
                    }
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
                    savedCrateData[worldBlockPos] = crateName
                    crateDataManager.saveCrateData(savedCrateData)

                    player.sendMessage(
                        Text.literal("Assigned a $crateName crate to the block at ${hitResult.blockPos} in ${worldId}")
                            .formatted(Formatting.GRAY), false
                    )
                    return@UseBlockCallback ActionResult.SUCCESS
                }
            }

            ActionResult.PASS
        })


        PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { world, player, pos, _, _ ->
            // Load the saved crate data
            val crateDataManager = CrateDataManager()
            val savedCrateData = crateDataManager.loadCrateData().toMutableMap()

            // Create a world-aware position for the broken block
            val worldId = getWorldId(world)
            val worldBlockPos = WorldBlockPos(worldId, pos)

            // Check if the broken block position is in the crate data
            if (worldBlockPos in savedCrateData) {
                // Remove the crate data for this position
                savedCrateData.remove(worldBlockPos)
                crateDataManager.saveCrateData(savedCrateData)

                // Send a message to the player for debugging purposes
                player.sendMessage(
                    Text.literal("Crate data removed for position: $pos in $worldId").formatted(Formatting.GRAY), false
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
        })

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            this.server = server
            nbtOps = server!!.registryManager.getOps(NbtOps.INSTANCE)
        }
    }

    private fun spawnParticlesForAllCrates(world: ServerWorld) {
        val crateDataManager = CrateDataManager()
        val savedCrateData = crateDataManager.loadCrateData()
        val blacklist = BlacklistConfigManager().getBlacklist()

        // Get the world ID for the current world
        val currentWorldId = getWorldId(world)

        for (worldBlockPos in savedCrateData.keys) {
            // Only process crates in this world
            if (worldBlockPos.worldId != currentWorldId) continue

            // Skip crates in the blacklist
            if (worldBlockPos in blacklist) continue

            val pos = worldBlockPos.pos
            world.getChunk(pos.x shr 4, pos.z shr 4)

            val playersNearby =
                world.getPlayersByDistance(pos, 16.0) // Only get players within 16 blocks of the crate block
            for (player in playersNearby) {
                CrateParticles.spawnCrossSpiralsParticles(player, pos, world)
            }
        }
    }


    private fun ServerWorld.getPlayersByDistance(pos: BlockPos, distance: Double): List<ServerPlayerEntity> {
        return this.players.filter { player ->
            player.squaredDistanceTo(
                Vec3d(
                    pos.x + 0.5, pos.y + 0.5, pos.z + 0.5
                )
            ) <= distance * distance
        }
    }
}