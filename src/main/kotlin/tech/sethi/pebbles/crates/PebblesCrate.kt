package tech.sethi.pebbles.crates

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.commands.GetCrateCommand
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbleslootcrate.commands.CrateCommand


object PebblesCrate : ModInitializer {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    const val MOD_ID = "pebbles_crate"

    override fun onInitialize() {
        logger.info("Initializing Pebbles Loot Crates!")

        val getCrateCommand = GetCrateCommand()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CrateCommand.register(dispatcher)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            getCrateCommand.register(dispatcher)
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
            if (world.isClient) {
                return@UseBlockCallback ActionResult.PASS
            }

            val itemStack = player.getStackInHand(hand)

            if (itemStack.item == Items.PAPER) {
                val tag = itemStack.nbt

                if (tag != null) {
                    if (tag.contains("CrateName")) {
                        val blockState = world.getBlockState(hitResult.blockPos)

                        if (blockState.block == Blocks.CHEST || blockState.block == Blocks.ENDER_CHEST) {
                            val crateName = tag.getString("CrateName")

                            player.sendMessage(Text.literal("Converting chest to $crateName"), false)

                            // Save the BlockPos and crate data
                            val crateDataManager = CrateDataManager()
                            val currentCrateData = crateDataManager.loadCrateData().toMutableMap()
                            currentCrateData[hitResult.blockPos] = crateName
                            crateDataManager.saveCrateData(currentCrateData)

                            return@UseBlockCallback ActionResult.SUCCESS
                        }
                    }
                }
            }

            ActionResult.PASS
        })



        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
            if (world.isClient) {
                return@UseBlockCallback ActionResult.PASS
            }

            val blockState = world.getBlockState(hitResult.blockPos)
            if (blockState.block == Blocks.CHEST || blockState.block == Blocks.ENDER_CHEST) {
                // Load the saved crate data
                val crateDataManager = CrateDataManager()
                val savedCrateData = crateDataManager.loadCrateData()

                // Check if the clicked position is in the crate data
                if (hitResult.blockPos in savedCrateData) {
                    val crateName = savedCrateData[hitResult.blockPos]
                    // Retrieve the CrateConfig by the crate name
                    val crateConfig = CrateConfigManager().getCrateConfig(crateName!!)

                    // Open the PrizeDisplayScreenHandler for the player
                    if (crateConfig != null) {
                        player.sendMessage(Text.literal("Opening crate $crateName"), false)
                        getCrateCommand.previewCratePrize(player.commandSource, crateConfig)
                        return@UseBlockCallback ActionResult.SUCCESS
                    }
                }
            }

            ActionResult.PASS
        })

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