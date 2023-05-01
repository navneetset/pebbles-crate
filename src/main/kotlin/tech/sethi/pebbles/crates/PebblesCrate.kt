package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.commands.GetCrateCommand
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.lootcrates.CrateEventHandler
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.util.Task
import tech.sethi.pebbles.crates.util.TickHandler
import tech.sethi.pebbles.crates.util.setLore
import tech.sethi.pebbleslootcrate.commands.CrateCommand
import java.util.*

object PebblesCrate : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"
    val cratesInUse = Collections.synchronizedSet(mutableSetOf<BlockPos>())
    val playerCooldowns: MutableMap<UUID, Long> = Collections.synchronizedMap(mutableMapOf())
    val tasks: MutableMap<Long, MutableList<Task>> = mutableMapOf()


    override fun onInitialize() {
        logger.info("Initializing Pebbles Loot Crates!")

        val getCrateCommand = GetCrateCommand()

        TickHandler()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CrateCommand.register(dispatcher)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            getCrateCommand.register(dispatcher)
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, _, hitResult ->
            if (world.isClient) {
                return@UseBlockCallback ActionResult.PASS
            }

            val blockState = world.getBlockState(hitResult.blockPos)
            if (blockState.block == Blocks.CHEST || blockState.block == Blocks.ENDER_CHEST) {
                // Load the saved crate data
                val crateDataManager = CrateDataManager()
                val savedCrateData = crateDataManager.loadCrateData().toMutableMap()

                // Check if the clicked position is in the crate data
                if (hitResult.blockPos in savedCrateData) {
                    val crateName = savedCrateData[hitResult.blockPos]
                    val crateConfig = CrateConfigManager().getCrateConfig(crateName!!)

                    val parsedKey = Registry.ITEM.get(
                        Identifier.tryParse(
                            crateConfig?.crateKey?.material ?: "minecraft:paper"
                        )
                    )
                    val parseKeyStack = ItemStack(parsedKey)
                    val crateKeyLore = crateConfig?.crateKey?.lore?.map { Text.of(it) }
                    if (crateKeyLore != null) {
                        setLore(parseKeyStack, crateKeyLore)
                    }
                    val nbt = parseKeyStack.orCreateNbt
                    if (crateConfig != null) {
                        nbt.putString("CrateName", crateConfig.crateName)
                    }

                    if (crateConfig != null) {
                        val heldStack = player.mainHandStack
                        if (heldStack.item == parseKeyStack.item && heldStack.hasNbt() && heldStack.nbt!!.getString(
                                "CrateName"
                            ) == crateConfig.crateName
                        ) {
                            if (cratesInUse.contains(hitResult.blockPos)) {
                                player.sendMessage(
                                    Text.literal("Someone is already using this crate!").formatted(Formatting.RED),
                                    false
                                )
                                return@UseBlockCallback ActionResult.SUCCESS
                            }

                            val crateEventHandler = CrateEventHandler(
                                world,
                                hitResult.blockPos,
                                player as ServerPlayerEntity,
                                crateConfig.prize,
                                cratesInUse,
                                playerCooldowns
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
                                    Text.literal("$crateName"), crateConfig
                                )
                            )
                        }
                        return@UseBlockCallback ActionResult.SUCCESS
                    }
                } else {
                    // Assign a new crate if the player is holding a named paper
                    val heldStack = player.mainHandStack
                    if (heldStack.item == Items.PAPER && heldStack.hasCustomName()) {
                        val crateName = heldStack.name.string
                        savedCrateData[hitResult.blockPos] = crateName
                        crateDataManager.saveCrateData(savedCrateData)

                        player.sendMessage(
                            Text.literal("Assigned a $crateName crate to the chest at ${hitResult.blockPos}"), false
                        )
                        return@UseBlockCallback ActionResult.SUCCESS
                    }
                }
            }

            ActionResult.PASS
        })


        PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { _, player, pos, state, _ ->
            // Load the saved crate data
            val crateDataManager = CrateDataManager()
            val savedCrateData = crateDataManager.loadCrateData().toMutableMap()

            // Check if the broken block position is in the crate data
            if (pos in savedCrateData && state.block == Blocks.CHEST || pos in savedCrateData && state.block == Blocks.ENDER_CHEST) {
                // Remove the crate data for this position
                savedCrateData.remove(pos)
                crateDataManager.saveCrateData(savedCrateData)

                // Send a message to the player for debugging purposes
                player.sendMessage(Text.literal("Crate data removed for position: $pos"), false)
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
    }

    private fun spawnParticlesForAllCrates(world: ServerWorld) {
        val crateDataManager = CrateDataManager()
        val savedCrateData = crateDataManager.loadCrateData()

        for (pos in savedCrateData.keys) {
            val playersNearby = world.getPlayersByDistance(pos, 32.0) // Only get players within 32 blocks of the chest
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