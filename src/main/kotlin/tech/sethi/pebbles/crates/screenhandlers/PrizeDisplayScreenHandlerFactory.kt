package tech.sethi.pebbles.crates.screenhandlers

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateOpener
import tech.sethi.pebbles.crates.lootcrates.Prize
import tech.sethi.pebbles.crates.screenhandlers.keys.KeyIcons
import tech.sethi.pebbles.crates.util.NbtItemUtil
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.WorldBlockPos
import tech.sethi.pebbles.crates.util.setLore

/**
 * The prize list a player sees when they click a crate they cannot (or need not) open with an item.
 *
 * When the crate spends virtual keys, this screen is also how it gets opened: [world] and
 * [worldBlockPos] identify the block the animation belongs to, and are absent when the preview was
 * opened from somewhere other than a placed crate - in which case there is nothing to open.
 */
class PrizeDisplayScreenHandlerFactory(
    private val title: Text,
    private val crateConfig: CrateConfig,
    private val world: World? = null,
    private val worldBlockPos: WorldBlockPos? = null
) : NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler {
        var currentPage = 0

        val crateItems = crateConfig.prize

        // Only a preview opened by clicking a placed crate knows where to run the animation.
        val crateWorld = world
        val cratePos = worldBlockPos
        val openable = crateWorld != null && cratePos != null && KeyManager.isVirtual(crateConfig)

        val handler = object : GenericContainerScreenHandler(
            ScreenHandlerType.GENERIC_9X6,
            syncId,
            inv,
            CrateInventory(crateItems, currentPage, crateConfig, openable, player),
            6
        ) {
            override fun onSlotClick(
                slotNumber: Int, button: Int, action: SlotActionType, playerEntity: PlayerEntity
            ) {
                if (slotNumber == GlobalConfigManager.gui.openButtonSlot && crateWorld != null && cratePos != null && openable) {
                    val serverPlayer = playerEntity as? ServerPlayerEntity ?: return
                    // Closed first: the roll animation happens at the block, behind this screen.
                    serverPlayer.closeHandledScreen()
                    CrateOpener.open(serverPlayer, crateWorld, cratePos, crateConfig)
                } else if (slotNumber == 45) { // Previous page arrow
                    if (currentPage > 0) {
                        currentPage--
                        (this.inventory as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else if (slotNumber == 53) { // Next page arrow
                    if (currentPage < (crateItems.size - 1) / 45) {
                        currentPage++
                        (this.inventory as CrateInventory).populateInventory(crateItems, currentPage)
                    }
                } else {
                    return
                }
            }
        }
        return handler

    }

    override fun getDisplayName(): Text {
        return title
    }
}


class CrateInventory(
    crateItems: List<Prize>,
    currentPage: Int,
    private val crateConfig: CrateConfig? = null,
    private val openable: Boolean = false,
    private val viewer: PlayerEntity? = null
) : SimpleInventory(54) {
    init {
        populateInventory(crateItems, currentPage)
    }

    fun populateInventory(crateItems: List<Prize>, currentPage: Int) {
        clear()
        val itemsPerPage = 45
        val startIndex = currentPage * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(crateItems.size)

        // A crate whose chances all sit at zero would otherwise show every prize as NaN%
        val totalWeight = crateItems.sumOf { it.chance }
        for (index in startIndex until endIndex) {
            val prize = crateItems[index]
            val parsedName = ParseableName(prize.name).returnMessageAsStyledText()

            val chance = if (totalWeight > 0) prize.chance.toDouble() / totalWeight.toDouble() * 100 else 0.0
            val roundedChance = String.format("%.2f", chance)

            val itemStack = NbtItemUtil.applyNbt(
                ItemStack(Registries.ITEM.get(Identifier.tryParse(prize.material)), prize.amount),
                prize.nbt,
                prize.amount,
                "prize '${prize.name}'"
            )

            // A prize whose material the game does not have leaves the slot blank rather than
            // having a name and lore set on an empty stack.
            if (itemStack.isEmpty) {
                setStack(index - startIndex, ItemStack.EMPTY)
                continue
            }

            if (prize.lore != null) {
                val lore = prize.lore
                val parsedPrizeLore = lore.map {
                    val message = it.replace("{chance}", roundedChance)
                    ParseableMessage(message, prizeName = prize.name).returnMessageAsStyledText()
                }
                setLore(itemStack, parsedPrizeLore)
            } else {
                setLore(itemStack, listOf(Messages.text("gui.prize.chance", "chance" to roundedChance)))
            }

            setStack(index - startIndex, itemStack.apply {
                set(DataComponentTypes.CUSTOM_NAME, parsedName)
            })
        }

        // Fill the bottom row with gray stained glass
        for (i in 45..53) {
            setStack(i, ItemStack(Items.GRAY_STAINED_GLASS_PANE))
        }

        if (crateItems.size > 45) {
            val pageText = Messages.text(
                "gui.page", "page" to "${currentPage + 1}", "pages" to "${((crateItems.size - 1) / 45) + 1}"
            )

            // Set the page text
            setStack(52, ItemStack(Items.PAPER).apply { set(DataComponentTypes.CUSTOM_NAME, pageText) })

            // Set the navigation arrows
            setStack(45, ItemStack(Items.ARROW).apply {
                set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.previous"))
            })
            setStack(53, ItemStack(Items.ARROW).apply {
                set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.next"))
            })
        }

        if (openable && crateConfig != null) {
            setStack(GlobalConfigManager.gui.openButtonSlot, openButton(crateConfig))
        }
    }

    /** Doubles as the balance readout: a player should not have to leave the crate to check it. */
    private fun openButton(crateConfig: CrateConfig): ItemStack {
        val balance = viewer?.let { KeyManager.getBalance(it.uuid, crateConfig.crateName) } ?: 0

        val stack = ItemStack(KeyIcons.keyMaterial(crateConfig))
        stack.set(DataComponentTypes.CUSTOM_NAME, Messages.text("gui.open.name"))

        setLore(
            stack, listOf(
                Messages.text("gui.open.balance", "amount" to "$balance"),
                Messages.text(if (balance > 0) "gui.open.ready" else "gui.open.no-key")
            )
        )

        return stack
    }
}
