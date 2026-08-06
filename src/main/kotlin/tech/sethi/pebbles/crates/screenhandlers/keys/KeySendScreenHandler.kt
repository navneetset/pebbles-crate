package tech.sethi.pebbles.crates.screenhandlers.keys

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager
import java.util.*

/**
 * Sending keys, as three steps: pick a key, pick a player, pick an amount and confirm.
 *
 * Which crate and which player a click means is read out of the clicked stack's custom data rather
 * than from its slot index or its rendered name, so paging never shifts the meaning of a click and
 * a styled crate name cannot be mistaken for a different crate. Nothing is moved until Confirm, and
 * everything the confirmation was built from is re-checked at that moment.
 */
class KeySendScreenHandler(
    syncId: Int, private val sender: ServerPlayerEntity
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, sender.inventory, SimpleInventory(9 * 6), 6) {

    private enum class Mode { KEY_SELECTION, PLAYER_SELECTION, CONFIRMATION }

    private var mode = Mode.KEY_SELECTION
    private var page = 0

    private var selectedCrate: String? = null
    private var selectedTarget: UUID? = null
    private var selectedTargetName: String = ""
    private var amount = 1

    init {
        populate()
    }

    private fun populate() {
        for (slot in 0 until 54) inventory.setStack(slot, ItemStack.EMPTY)

        when (mode) {
            Mode.KEY_SELECTION -> populateKeySelection()
            Mode.PLAYER_SELECTION -> populatePlayerSelection()
            Mode.CONFIRMATION -> populateConfirmation()
        }
    }

    // region key selection
    private fun populateKeySelection() {
        // Re-read every time: keys can arrive or be spent while this screen is open.
        val keys = KeyManager.getBalances(sender.uuid).filterValues { it > 0 }.toList().sortedBy { it.first }

        val pageCount = pageCount(keys.size)
        page = page.coerceIn(0, pageCount - 1)

        val firstIndex = page * PER_PAGE
        val lastIndex = (firstIndex + PER_PAGE).coerceAtMost(keys.size)
        for (index in firstIndex until lastIndex) {
            val (crate, held) = keys[index]
            inventory.setStack(
                index - firstIndex, KeyIcons.keyIcon(crate, held, Messages.list("gui.keys.send.pick-key"))
            )
        }

        fillBottomRow()

        if (keys.isEmpty()) {
            inventory.setStack(
                22, KeyIcons.button(
                    Items.BARRIER, Messages.text("gui.keys.send.empty"), Messages.list("gui.keys.send.empty-lore")
                )
            )
        }

        addPager(pageCount, PREVIOUS_SLOT, NEXT_SLOT, PAGE_SLOT)
    }
    // endregion

    // region player selection
    private fun populatePlayerSelection() {
        // Excluding the sender is what makes a self-send impossible to start, rather than something
        // to catch after the keys have already moved.
        val candidates =
            sender.server.playerManager.playerList.filter { it.uuid != sender.uuid }.sortedBy { it.name.string }

        val pageCount = pageCount(candidates.size)
        page = page.coerceIn(0, pageCount - 1)

        val firstIndex = page * PER_PAGE
        val lastIndex = (firstIndex + PER_PAGE).coerceAtMost(candidates.size)
        for (index in firstIndex until lastIndex) {
            inventory.setStack(
                index - firstIndex,
                KeyIcons.playerHead(candidates[index], Messages.list("gui.keys.send.pick-target"))
            )
        }

        fillBottomRow()

        if (candidates.isEmpty()) {
            inventory.setStack(
                22, KeyIcons.button(
                    Items.BARRIER,
                    Messages.text("gui.keys.send.no-targets"),
                    Messages.list("gui.keys.send.no-targets-lore")
                )
            )
        }

        inventory.setStack(
            BACK_SLOT, KeyIcons.button(Items.ARROW, Messages.text("gui.back"), action = ACTION_BACK)
        )
        addPager(pageCount, PREVIOUS_SLOT_WITH_BACK, NEXT_SLOT_WITH_BACK, PAGE_SLOT)
    }
    // endregion

    // region confirmation
    private fun populateConfirmation() {
        val crate = selectedCrate ?: return backToKeys()
        val held = KeyManager.getBalance(sender.uuid, crate)
        amount = amount.coerceIn(1, held.coerceAtLeast(1))

        for (slot in 0 until 54) inventory.setStack(slot, KeyIcons.filler())

        inventory.setStack(
            AMOUNT_DOWN_SLOT, KeyIcons.button(
                Items.RED_STAINED_GLASS_PANE,
                Messages.text("gui.keys.send.fewer"),
                stepLore(),
                ACTION_AMOUNT_DOWN
            )
        )

        inventory.setStack(
            AMOUNT_SLOT, KeyIcons.keyIcon(
                crate, amount, listOf(
                    Messages.text("gui.keys.send.sending", "amount" to "$amount"),
                    Messages.text("gui.keys.send.held", "held" to "$held")
                )
            )
        )

        inventory.setStack(
            AMOUNT_UP_SLOT, KeyIcons.button(
                Items.LIME_STAINED_GLASS_PANE, Messages.text("gui.keys.send.more"), stepLore(), ACTION_AMOUNT_UP
            )
        )

        val target = selectedTarget?.let { sender.server.playerManager.getPlayer(it) }
        inventory.setStack(
            TARGET_SLOT, if (target != null) {
                KeyIcons.playerHead(target, Messages.list("gui.keys.send.recipient"))
            } else {
                KeyIcons.button(
                    Items.BARRIER,
                    Messages.text("gui.keys.send.offline", "player_name" to selectedTargetName),
                    Messages.list("gui.keys.send.offline-lore")
                )
            }
        )

        inventory.setStack(
            CONFIRM_SLOT, KeyIcons.button(
                Items.GREEN_WOOL, Messages.text("gui.keys.send.confirm"), Messages.list(
                    "gui.keys.send.confirm-lore",
                    "amount" to "$amount",
                    "crate_name" to crate,
                    "player_name" to selectedTargetName
                ), ACTION_CONFIRM
            )
        )

        inventory.setStack(
            CANCEL_SLOT, KeyIcons.button(
                Items.RED_WOOL, Messages.text("gui.keys.send.cancel"), action = ACTION_CANCEL
            )
        )
    }

    private fun stepLore(): List<Text> =
        Messages.list("gui.keys.send.steps", "right" to "$STEP_RIGHT", "shift" to "$STEP_SHIFT")

    private fun confirm() {
        val crate = selectedCrate ?: return backToKeys()
        val targetUuid = selectedTarget ?: return backToKeys()

        // Everything the confirmation screen was built from can have changed while it was open.
        if (targetUuid == sender.uuid) {
            Messages.send(sender, "keys.self-send")
            return backToKeys()
        }

        val target = sender.server.playerManager.getPlayer(targetUuid)
        if (target == null) {
            Messages.send(sender, "keys.target-offline", "player_name" to selectedTargetName)
            return backToKeys()
        }

        if (!KeyManager.isReady(sender.uuid) || !KeyManager.isReady(targetUuid)) {
            Messages.send(sender, "keys.loading")
            return
        }

        if (!KeyManager.transfer(sender.uuid, targetUuid, crate, amount)) {
            Messages.send(sender, "keys.not-enough-now", "amount" to "$amount", "crate_name" to crate)
            return backToKeys()
        }

        Messages.send(
            sender, "keys.sent", "amount" to "$amount", "crate_name" to crate, "player_name" to target.name.string
        )
        Messages.send(
            target, "keys.received", "amount" to "$amount", "crate_name" to crate, "player_name" to sender.name.string
        )
        sender.closeHandledScreen()
    }
    // endregion

    private fun backToKeys() {
        mode = Mode.KEY_SELECTION
        page = 0
        selectedCrate = null
        selectedTarget = null
        selectedTargetName = ""
        amount = 1
        populate()
    }

    private fun fillBottomRow() {
        for (slot in PER_PAGE until 54) inventory.setStack(slot, KeyIcons.filler())
    }

    private fun pageCount(size: Int) = ((size - 1) / PER_PAGE + 1).coerceAtLeast(1)

    private fun addPager(pageCount: Int, previousSlot: Int, nextSlot: Int, indicatorSlot: Int) {
        if (pageCount <= 1) return

        inventory.setStack(indicatorSlot, ItemStack(Items.PAPER).apply {
            set(
                DataComponentTypes.CUSTOM_NAME,
                Messages.text("gui.page", "page" to "${page + 1}", "pages" to "$pageCount")
            )
        })
        if (page > 0) {
            inventory.setStack(
                previousSlot, KeyIcons.button(Items.ARROW, Messages.text("gui.previous"), action = ACTION_PREVIOUS)
            )
        }
        if (page < pageCount - 1) {
            inventory.setStack(
                nextSlot, KeyIcons.button(Items.ARROW, Messages.text("gui.next"), action = ACTION_NEXT)
            )
        }
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun onSlotClick(slotIndex: Int, clickData: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex < 0 || slotIndex >= 54) return

        val clicked = inventory.getStack(slotIndex)
        if (clicked.isEmpty) return

        when (KeyIcons.actionOf(clicked)) {
            ACTION_PREVIOUS -> {
                page--
                populate()
                return
            }

            ACTION_NEXT -> {
                page++
                populate()
                return
            }

            ACTION_BACK, ACTION_CANCEL -> return backToKeys()

            ACTION_AMOUNT_DOWN -> return adjustAmount(-step(clickData, actionType))
            ACTION_AMOUNT_UP -> return adjustAmount(step(clickData, actionType))
            ACTION_CONFIRM -> return confirm()
        }

        when (mode) {
            Mode.KEY_SELECTION -> {
                selectedCrate = KeyIcons.crateOf(clicked) ?: return
                mode = Mode.PLAYER_SELECTION
                page = 0
                populate()
            }

            Mode.PLAYER_SELECTION -> {
                val target = KeyIcons.playerOf(clicked) ?: return
                if (target == sender.uuid) return
                selectedTarget = target
                selectedTargetName = sender.server.playerManager.getPlayer(target)?.name?.string ?: ""
                amount = 1
                mode = Mode.CONFIRMATION
                populate()
            }

            Mode.CONFIRMATION -> return
        }
    }

    private fun adjustAmount(delta: Int) {
        val crate = selectedCrate ?: return backToKeys()
        val held = KeyManager.getBalance(sender.uuid, crate)
        amount = (amount + delta).coerceIn(1, held.coerceAtLeast(1))
        populate()
    }

    private fun step(clickData: Int, actionType: SlotActionType): Int = when {
        actionType == SlotActionType.QUICK_MOVE -> STEP_SHIFT
        clickData == 1 -> STEP_RIGHT
        else -> 1
    }

    companion object {
        private const val PER_PAGE = 45
        private const val PREVIOUS_SLOT = 45
        private const val PAGE_SLOT = 49
        private const val NEXT_SLOT = 53

        private const val BACK_SLOT = 45
        private const val PREVIOUS_SLOT_WITH_BACK = 47
        private const val NEXT_SLOT_WITH_BACK = 51

        private const val AMOUNT_DOWN_SLOT = 20
        private const val AMOUNT_SLOT = 22
        private const val AMOUNT_UP_SLOT = 24
        private const val TARGET_SLOT = 31
        private const val CONFIRM_SLOT = 47
        private const val CANCEL_SLOT = 51

        private const val STEP_RIGHT = 10
        private const val STEP_SHIFT = 64

        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_NEXT = "next"
        private const val ACTION_BACK = "back"
        private const val ACTION_CANCEL = "cancel"
        private const val ACTION_CONFIRM = "confirm"
        private const val ACTION_AMOUNT_UP = "amount_up"
        private const val ACTION_AMOUNT_DOWN = "amount_down"

        fun open(player: ServerPlayerEntity) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, screenPlayer ->
                KeySendScreenHandler(syncId, screenPlayer as ServerPlayerEntity)
            }, Messages.text("gui.keys.send-title")))
        }
    }
}
