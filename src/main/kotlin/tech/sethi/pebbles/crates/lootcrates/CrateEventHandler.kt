package tech.sethi.pebbles.crates.lootcrates

import com.mojang.brigadier.ParseResults
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import tech.sethi.pebbles.crates.util.FloatingPrizeItemEntity
import tech.sethi.pebbles.crates.util.ParseableMessage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadLocalRandom


class CrateEventHandler(
    private val world: World,
    private val pos: BlockPos,
    private val player: ServerPlayerEntity,
    private val prizes: List<Prize>,
    private val cratesInUse: MutableSet<BlockPos>
) {
    init {}

    private var lastArmorStandEntity: ArmorStandEntity? = null
    private var lastFloatingPrizeItemEntity: FloatingPrizeItemEntity? = null
    fun weightedRandomSelection(prizes: List<Prize>): Prize {
        val totalWeight = prizes.sumOf { it.chance }
        val randomValue = ThreadLocalRandom.current().nextInt(totalWeight)
        var cumulativeWeight = 0

        for (prize in prizes) {
            cumulativeWeight += prize.chance
            if (randomValue < cumulativeWeight) {
                return prize
            }
        }

        throw IllegalStateException("No prize could be selected.")
    }


    private fun spawnFloatingItem(prize: Prize) {
        if (world is ServerWorld) {
            spawnParticles()

            val executor = Executors.newScheduledThreadPool(1)

            executor.schedule({
                revealPrize(prize, isFinalPrize = true)
                val message = prize.messageToOpener.replace("{prize_name}", prize.name)
                var broadcast = prize.broadcast.replace("{prize_name}", prize.name)
                broadcast = broadcast.replace("{player_name}", player.entityName)
                broadcast = broadcast.replace("{crate_name}", "Shiny Crate")
                ParseableMessage(message, player, prize.name).send()
                ParseableMessage(broadcast, player, prize.name).sendToAll()
            }, 0, TimeUnit.MILLISECONDS)
        }
    }

    private fun revealPrize(prize: Prize, isFinalPrize: Boolean) {
        // Remove any previous floating item
        removeFloatingItem()

        val parsedPrize = Registry.ITEM.get(Identifier.tryParse(prize.material))
        val itemStack = ItemStack(parsedPrize)
        val nbt: NbtCompound = NbtHelper.fromNbtProviderString(prize.nbt)
        itemStack.nbt = nbt
        itemStack.setCustomName(Text.of(prize.name))

        var height = pos.y.toDouble()

        if (isFinalPrize) {
            height = pos.y + 1.0
        }
        val spawnPos = Vec3d(pos.x + 0.5, height, pos.z + 0.5)
        val armorStandEntity = ArmorStandEntity(world, spawnPos.x, spawnPos.y, spawnPos.z).apply {
            isInvisible = true
            setNoGravity(true)
            equipStack(EquipmentSlot.CHEST, itemStack)
        }
        world.spawnEntity(armorStandEntity)

        // Store the last spawned armor stand entity
        lastArmorStandEntity = armorStandEntity

        val floatingPrizeItemEntity =
            FloatingPrizeItemEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, itemStack, armorStandEntity)
        world.spawnEntity(floatingPrizeItemEntity)

        // Store the last spawned floating prize item entity
        lastFloatingPrizeItemEntity = floatingPrizeItemEntity
    }


    fun showPrizesAnimation(finalPrize: Prize) {
        if (world is ServerWorld) {
            if (cratesInUse.contains(pos)) {
                player.sendMessage(
                    Text.literal("Someone is already using this crate!").formatted(Formatting.RED), false
                )
                return
            }
            cratesInUse.add(pos) // Add the crate's BlockPos to the cratesInUse set

            val executor = Executors.newScheduledThreadPool(1)
            val animationPrizesCount = 5
            val delayBetweenPrizes = 500L
            var delay = 0L

            for (i in 0 until animationPrizesCount) {
                executor.schedule({
                    val randomPrize = weightedRandomSelection(prizes)
                    revealPrize(randomPrize, false)
                    world.playSound(
                        null as ServerPlayerEntity?,
                        pos,
                        SoundEvents.BLOCK_NOTE_BLOCK_BANJO,
                        SoundCategory.BLOCKS,
                        1.0f,
                        1.0f
                    )
                }, delay, TimeUnit.MILLISECONDS)

                delay += delayBetweenPrizes
            }

            // Delay the final prize reveal so that the last random prize is shown for a while
            delay += delayBetweenPrizes

            executor.schedule({
                revealPrize(finalPrize, true)
                spawnFloatingItem(finalPrize) // Display the final prize as a floating item

                for (command in finalPrize.commands) {
                    val cmd = command.replace("{player_name}", player.entityName)
                    try {
                        val parseResults: ParseResults<ServerCommandSource> =
                            player.server.commandManager.dispatcher.parse(cmd, player.server.commandSource)
                        player.server.commandManager.dispatcher.execute(parseResults)
                    } catch (e: Exception) {
                        player.sendMessage(Text.of("Error executing command: $command"), false)
                    }
                }
            }, delay, TimeUnit.MILLISECONDS)

            delay += 3000L
            executor.schedule({
                cratesInUse.remove(pos) // Remove the crate's BlockPos from the cratesInUse set when the crate usage is completed
            }, delay, TimeUnit.MILLISECONDS)
        }
    }


    private fun removeFloatingItem() {
        lastArmorStandEntity?.kill()
        lastFloatingPrizeItemEntity?.kill()
    }

    private fun spawnParticles() {
        if (world is ServerWorld) {
            for (i in 0..30) {
                val xOffset = (ThreadLocalRandom.current().nextFloat() - 0.5) * 1.5
                val yOffset = (ThreadLocalRandom.current().nextFloat() - 0.5) * 1.5
                val zOffset = (ThreadLocalRandom.current().nextFloat() - 0.5) * 1.5

                world.spawnParticles(
                    ParticleTypes.SCULK_SOUL,
                    pos.x + 0.5 + xOffset,
                    pos.y + 1 + yOffset,
                    pos.z + 0.5 + zOffset,
                    10,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                )
                world.playSound(
                    null as ServerPlayerEntity?, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 0.5f, 1f
                )
            }
        }
    }


}