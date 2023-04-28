package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.commands.GetCrateCommand
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.lootcrates.CrateEventHandler
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbleslootcrate.commands.CrateCommand
import java.util.concurrent.ConcurrentHashMap


object PebblesCrate : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"
    val cratesInUse = ConcurrentHashMap.newKeySet<BlockPos>()

    override fun onInitialize() {
        logger.info("Initializing Pebbles Loot Crates!")

        val getCrateCommand = GetCrateCommand()

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

                    if (crateConfig != null) {
                        if (player.isSneaking) {
                            // Handle the scenario when the player is sneaking
                            val crateEventHandler = CrateEventHandler(world, hitResult.blockPos, player as ServerPlayerEntity, crateConfig.prize, cratesInUse)

                            if (cratesInUse.contains(hitResult.blockPos)) {
                                player.sendMessage(Text.literal("Someone is already using this crate!").formatted(Formatting.RED), false)
                                return@UseBlockCallback ActionResult.SUCCESS
                            }
                            val selectedPrize = crateEventHandler.weightedRandomSelection(crateConfig.prize)
                            crateEventHandler.showPrizesAnimation(selectedPrize)

                            // Play the crate open sound
                            world.playSound(
                                null as ServerPlayerEntity?,
                                hitResult.blockPos,
                                SoundEvents.BLOCK_LEVER_CLICK,
                                SoundCategory.BLOCKS,
                                1.0f,
                                1.0f
                            )

                            // Floating item will be spawned in the CrateEventHandler's init block
                        } else {
                            // Open crate preview GUI
                            player.openHandledScreen(PrizeDisplayScreenHandlerFactory( Text.literal("$crateName") , crateConfig))
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
                CrateParticles.spawnSpiralParticles(player, pos, world)
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


//        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
//            val itemStack = player.getStackInHand(hand)
//            player.sendMessage(Text.literal("Placing Enderchest"), false)
//
//            if (itemStack.nbt?.contains("CrateName") == true) {
//                val hitResult = player.raycast(4.5, 0.0f, false)
//
//                if (hitResult.type == HitResult.Type.BLOCK) {
//                    player.sendMessage(Text.literal("Placing Enderchest"), false)
//                    val blockHitResult = hitResult as BlockHitResult
//                    val blockPos = blockHitResult.blockPos.offset(blockHitResult.side)
//
//                    if (world.canPlayerModifyAt(player, blockPos) && world.getBlockState(blockPos).isAir) {
//                        val crateName = itemStack.nbt!!.getString("CrateName")
//                        crateDataMap[blockPos] = crateName
//                        crateDataManager.saveCrateData(crateDataMap)
//                        player.sendMessage(Text.literal("Placing $crateName"), false)
//                        return@UseItemCallback TypedActionResult.success(itemStack, world.isClient)
//                    }
//                }
//            }
//            TypedActionResult.pass(itemStack)
//        })