package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
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

        //create /config/pebbles-crate/crates if it doesn't exist
        CrateConfigManager().createCratesFolder()

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

            // Check if the clicked position is in the crate data
            if (hitResult.blockPos in savedCrateData) {
                val crateName = savedCrateData[hitResult.blockPos]
                val crateConfig = CrateConfigManager().getCrateConfig(crateName!!)

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
                                Text.literal("Someone is already using this crate!").formatted(Formatting.RED), false
                            )
                            return@UseBlockCallback ActionResult.SUCCESS
                        }

                        val crateEventHandler = CrateEventHandler(
                            world,
                            hitResult.blockPos,
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
                                Text.literal("$crateName"), crateConfig
                            )
                        )
                    }
                    return@UseBlockCallback ActionResult.SUCCESS
                }
            } else {
                // Assign a new crate if the player is holding a named paper
                val heldStack = player.mainHandStack
                if (heldStack.item == Items.PAPER && heldStack.hasCustomName() && heldStack.hasNbt() && heldStack.nbt!!.contains(
                        "CrateName"
                    )) {
                    val crateName = heldStack.nbt!!.getString("CrateName")
                    savedCrateData[hitResult.blockPos] = crateName
                    crateDataManager.saveCrateData(savedCrateData)

                    player.sendMessage(
                        Text.literal("Assigned a $crateName crate to the block at ${hitResult.blockPos}")
                            .formatted(Formatting.GRAY), false
                    )
                    return@UseBlockCallback ActionResult.SUCCESS
                }
            }

            ActionResult.PASS
        })


        PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { _, player, pos, _, _ ->
            // Load the saved crate data
            val crateDataManager = CrateDataManager()
            val savedCrateData = crateDataManager.loadCrateData().toMutableMap()

            // Check if the broken block position is in the crate data
            if (pos in savedCrateData) {
                // Remove the crate data for this position
                savedCrateData.remove(pos)
                crateDataManager.saveCrateData(savedCrateData)

                // Send a message to the player for debugging purposes
                player.sendMessage(
                    Text.literal("Crate data removed for position: $pos").formatted(Formatting.GRAY), false
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
    }

    private fun spawnParticlesForAllCrates(world: ServerWorld) {
        val crateDataManager = CrateDataManager()
        val savedCrateData = crateDataManager.loadCrateData()
        val blacklist = BlacklistConfigManager().getBlacklist()

        for (pos in savedCrateData.keys) {
            // Skip crates in the blacklist
            if (pos in blacklist) continue

            val playersNearby = world.getPlayersByDistance(pos, 16.0) // Only get players within 32 blocks of the chest
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