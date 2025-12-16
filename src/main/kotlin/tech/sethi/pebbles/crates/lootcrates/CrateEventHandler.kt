package tech.sethi.pebbles.crates.lootcrates

import com.mojang.brigadier.ParseResults
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.component.DataComponentTypes
import net.minecraft.datafixer.TypeReferences
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
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
import net.minecraft.world.World
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.particles.CrateParticles
import tech.sethi.pebbles.crates.util.FloatingPrizeItemEntity
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.Task
import tech.sethi.pebbles.crates.util.WorldBlockPos
import java.util.*
import java.util.concurrent.*


class CrateEventHandler(
    private val world: World,
    private val worldBlockPos: WorldBlockPos,
    private val player: ServerPlayerEntity,
    private val prizes: List<Prize>,
    private val cratesInUse: MutableSet<WorldBlockPos>,
    private val playerCooldowns: MutableMap<UUID, Long>,
    private val crateName: String
) {
    // Extract the BlockPos for convenience
    private val pos: BlockPos = worldBlockPos.pos
    companion object {
        const val COOLDOWN_TIME = 8000L
    }

    private var lastFloatingPrizeItemEntity: FloatingPrizeItemEntity? = null

    private val random = Random()
    fun weightedRandomSelection(prizes: List<Prize>): Prize {
        val totalWeight = prizes.sumOf { it.chance }
        val randomValue = random.nextInt(totalWeight)
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
            CrateParticles.rewardParticles(player, pos)

            revealPrize(prize, isFinalPrize = true)
            if (prize.messageToOpener != null && prize.messageToOpener != "") {
                val message = prize.messageToOpener.replace("{prize_name}", prize.name)
                ParseableMessage(message, player, prize.name).send()
            }

            if (prize.broadcast != null && prize.broadcast != "") {
                var broadcast = prize.broadcast.replace("{prize_name}", prize.name)
                broadcast = broadcast.replace("{player_name}", player.name.string)
                broadcast = broadcast.replace("{crate_name}", crateName)
                if (broadcast != "") {
                    ParseableMessage(broadcast, player, prize.name).sendToAll()
                }
            }
        }
    }

    private fun revealPrize(prize: Prize, isFinalPrize: Boolean) {
        // Remove any previous floating item
        removeFloatingItem()

        val parsedPrize = Registries.ITEM.get(Identifier.tryParse(prize.material))
        var itemStack = ItemStack(parsedPrize)

        if (prize.nbt?.isNotBlank() == true && prize.nbt != "{}" && prize.nbt != "null") {
                val parsedNbt = StringNbtReader.parse(prize.nbt)

                val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

                val isLegacy = parsedNbt.keys.any { !namespacedKeyPattern.matches(it) }
                if (isLegacy) {
                    val legacyNbt = NbtCompound().apply {
                        putString("id", itemStack.registryEntry.idAsString)
                        putInt("Count", prize.amount)
                        put("tag", parsedNbt)
                    }

                    val updatedNbt = server?.dataFixer?.update(
                        TypeReferences.ITEM_STACK,
                        Dynamic(PebblesCrate.nbtOps, legacyNbt),
                        3700,
                        SharedConstants.getGameVersion().saveVersion.id
                    )?.value

                    itemStack = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
                } else {
                    val updatedNbt =
                        ComponentChanges.CODEC.parse(PebblesCrate.nbtOps, StringNbtReader.parse(prize.nbt)).result()
                            .orElse(null)
                    itemStack.applyChanges(updatedNbt)
                    itemStack.count = prize.amount
                }
        }

        itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.of(prize.name))

        var height = pos.y.toDouble()

        if (isFinalPrize) {
            height = pos.y + 1.0
        }
        val spawnPos = Vec3d(pos.x + 0.5, height + 1.5, pos.z + 0.5)

        val floatingPrizeItemEntity = FloatingPrizeItemEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, itemStack)
        world.spawnEntity(floatingPrizeItemEntity)

        // Store the last spawned floating prize item entity
        synchronized(floatingPrizeItemEntityLock) {
            lastFloatingPrizeItemEntity = floatingPrizeItemEntity
        }
    }




    fun showPrizesAnimation(finalPrize: Prize) {
        if (world is ServerWorld) {
            val currentTime = System.currentTimeMillis()
            val lastCrateOpenTime = playerCooldowns[player.uuid] ?: 0L

            if (currentTime - lastCrateOpenTime < COOLDOWN_TIME) {
                val remainingCooldown = (COOLDOWN_TIME - (currentTime - lastCrateOpenTime)) / 1000
                player.sendMessage(
                    Text.literal("You can open another crate in $remainingCooldown seconds.").formatted(Formatting.RED),
                    false
                )
                return
            }

            cratesInUse.add(worldBlockPos)

            val animationPrizesCount = 10
            val delayBetweenPrizes = 6L // 300 milliseconds converted to ticks

            for (i in 0 until animationPrizesCount) {
                val randomPrize = weightedRandomSelection(prizes)
                addTask(world, delayBetweenPrizes * i) { showRandomPrizeRunnable(randomPrize).run() }
            }

            // Delay the final prize reveal so that the last random prize is shown for a while
            val finalPrizeDelay = delayBetweenPrizes * (animationPrizesCount + 1)

            addTask(world, finalPrizeDelay) {
                revealPrize(finalPrize, true)
                spawnFloatingItem(finalPrize) // Display the final prize as a floating item

                for (command in finalPrize.commands) {
                    val cmd = command.replace("{player_name}", player.name.string)
                    try {
                        val parseResults: ParseResults<ServerCommandSource> =
                            player.server.commandManager.dispatcher.parse(cmd, player.server.commandSource)
                        player.server.commandManager.dispatcher.execute(parseResults)
                    } catch (e: Exception) {
                        player.sendMessage(Text.of("Error executing command: $command"), false)
                    }
                }
            }

            val removeCrateDelay = finalPrizeDelay + 100 // 5 seconds converted to ticks
            addTask(world, removeCrateDelay) { cratesInUse.remove(worldBlockPos) }
        }
    }


    private fun showRandomPrizeRunnable(prize: Prize) = Runnable {
        revealPrize(prize, false)
        world.playSound(
            null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(), SoundCategory.BLOCKS, 0.5f, 1.0f
        )
    }


    private val floatingPrizeItemEntityLock = Any()
    private fun removeFloatingItem() {
        synchronized(floatingPrizeItemEntityLock) {
            lastFloatingPrizeItemEntity?.kill()
            lastFloatingPrizeItemEntity = null
        }
    }

    fun canOpenCrate(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastCrateOpenTime = playerCooldowns[player.uuid] ?: 0L

        if (currentTime - lastCrateOpenTime < COOLDOWN_TIME) {
            val remainingCooldown = (COOLDOWN_TIME - (currentTime - lastCrateOpenTime)) / 1000
            player.sendMessage(
                Text.literal("You can open another crate in $remainingCooldown seconds.").formatted(Formatting.RED),
                false
            )
            return false
        }
        return true
    }

    fun updatePlayerCooldown() {
        val currentTime = System.currentTimeMillis()
        playerCooldowns[player.uuid] = currentTime
    }

    fun addTask(world: ServerWorld, tickDelay: Long, action: () -> Unit) {
        val currentTick = world.time
        val taskTick = currentTick + tickDelay
        val task = Task(world, taskTick, action)
        PebblesCrate.tasks.getOrPut(taskTick) { mutableListOf() }.add(task)
    }


}