package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType.GENERIC_9X6
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.config.CrateStyle
import tech.sethi.pebbles.crates.config.CrateStyles
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.config.ParticleStyle
import tech.sethi.pebbles.crates.config.ResolvedStyle
import tech.sethi.pebbles.crates.config.SoundOverride
import tech.sethi.pebbles.crates.config.StyleResolver
import tech.sethi.pebbles.crates.config.StyleSource
import tech.sethi.pebbles.crates.keys.KeyManager
import tech.sethi.pebbles.crates.lootcrates.BlacklistConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.screenhandlers.keys.KeyIcons
import tech.sethi.pebbles.crates.util.PermissionUtil
import tech.sethi.pebbles.crates.util.WorldBlockPos
import tech.sethi.pebbles.crates.util.setLore
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything about one crate block: where it is, whether it draws particles, and the sounds,
 * particles and animation it uses.
 *
 * The screen edits one of two layers at a time - this placement, or the crate type every copy of it
 * shares - and the scope button says which. Whatever the scope, the lore always reports the value
 * that is actually in force and which layer it came from, so an override that is being shadowed by
 * a narrower one is visible rather than mysterious.
 *
 * Clicks are read off the clicked stack's action tag rather than its slot index, and the admin check
 * is repeated on every click: a screen can stay open a long time.
 */
class CratePlacementScreenHandler(
    syncId: Int,
    private val admin: ServerPlayerEntity,
    private val worldPos: WorldBlockPos,
    private val returnPage: Int
) : GenericContainerScreenHandler(GENERIC_9X6, syncId, admin.inventory, SimpleInventory(9 * 6), 6) {

    /** True while edits go to this one block, false while they go to the crate type. */
    private var editingPlacement = true

    init {
        populate()
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    // region drawing
    private fun populate() {
        for (slot in 0 until 54) inventory.setStack(slot, KeyIcons.filler())
        inventory.setStack(BACK_SLOT, KeyIcons.button(Items.ARROW, Messages.text("gui.back"), action = ACTION_BACK))

        val placement = CrateDataManager.getPlacement(worldPos)
        if (placement == null) {
            inventory.setStack(
                INFO_SLOT, KeyIcons.button(Items.BARRIER, Messages.text("gui.placement.missing"))
            )
            return
        }

        val crateConfig = CrateConfigManager.getCrateConfig(placement.name)
        val placementStyle = placement.style
        val crateStyle = crateConfig?.style
        val resolved = StyleResolver.resolve(placementStyle, crateStyle)
        val scoped = if (editingPlacement) placementStyle else crateStyle

        inventory.setStack(INFO_SLOT, infoItem(placement.name, crateConfig))
        inventory.setStack(TELEPORT_SLOT, teleportItem())
        inventory.setStack(PARTICLES_SLOT, particlesItem())
        inventory.setStack(SCOPE_SLOT, scopeItem(placement.name))

        inventory.setStack(STYLE_SLOT, styleItem(resolved, scoped, placementStyle, crateStyle))

        soundRow(
            SHUFFLE_SLOT,
            Messages.text("gui.placement.sound.shuffle"),
            resolved.shuffleSound.id,
            resolved.shuffleSound.volume,
            resolved.shuffleSound.pitch,
            scoped?.shuffleSound,
            placementStyle?.shuffleSound,
            crateStyle?.shuffleSound,
            ACTION_SHUFFLE,
            ACTION_SHUFFLE_VOLUME,
            ACTION_SHUFFLE_PITCH,
            Items.NOTE_BLOCK
        )

        val reward = resolved.rewardSounds.firstOrNull() ?: GlobalConfigManager.animation.shuffleSound
        soundRow(
            REWARD_SLOT,
            Messages.text("gui.placement.sound.reward"),
            reward.id,
            reward.volume,
            reward.pitch,
            scoped?.rewardSound,
            placementStyle?.rewardSound,
            crateStyle?.rewardSound,
            ACTION_REWARD,
            ACTION_REWARD_VOLUME,
            ACTION_REWARD_PITCH,
            Items.BELL
        )

        inventory.setStack(
            STEPS_SLOT, adjustItem(
                Items.CLOCK,
                Messages.text("gui.placement.steps", "value" to "${resolved.steps}"),
                scoped?.animationSteps?.toString(),
                StyleResolver.sourceOf(placementStyle?.animationSteps, crateStyle?.animationSteps),
                "1",
                ACTION_STEPS
            )
        )

        inventory.setStack(
            TICKS_SLOT, adjustItem(
                Items.REPEATER,
                Messages.text("gui.placement.ticks", "value" to "${resolved.ticksPerStep}"),
                scoped?.ticksPerStep?.toString(),
                StyleResolver.sourceOf(placementStyle?.ticksPerStep, crateStyle?.ticksPerStep),
                "1",
                ACTION_TICKS
            )
        )

        inventory.setStack(
            SCALE_SLOT, adjustItem(
                Items.SPYGLASS,
                Messages.text("gui.placement.scale", "value" to decimals(resolved.finalScale)),
                scoped?.finalScale?.let { decimals(it.toFloat()) },
                StyleResolver.sourceOf(placementStyle?.finalScale, crateStyle?.finalScale),
                decimals(SCALE_STEP.toFloat()),
                ACTION_SCALE
            )
        )
    }

    private fun infoItem(crateName: String, crateConfig: CrateConfig?): ItemStack {
        val effective = if (crateConfig != null && KeyManager.isVirtual(crateConfig)) {
            Messages.raw("gui.placement.keys.virtual")
        } else {
            Messages.raw("gui.placement.keys.physical")
        }

        val keyMode = when (crateConfig?.virtualKey) {
            true -> Messages.raw("gui.placement.keys.virtual")
            false -> Messages.raw("gui.placement.keys.physical")
            null -> Messages.raw("gui.placement.keys.inherit", "effective" to effective)
        }

        val stack = KeyIcons.button(
            KeyIcons.keyMaterial(crateConfig), Messages.text("gui.placement.info", "crate_name" to crateName)
        )
        setLore(
            stack, Messages.list(
                "gui.placement.info-lore",
                "world" to worldPos.worldId,
                "x" to "${worldPos.pos.x}",
                "y" to "${worldPos.pos.y}",
                "z" to "${worldPos.pos.z}",
                "key_mode" to keyMode,
                "file" to (CrateConfigManager.sourceFileOf(crateName) ?: "-")
            )
        )
        return stack
    }

    private fun teleportItem(): ItemStack = KeyIcons.button(
        Items.ENDER_PEARL,
        Messages.text("gui.placement.teleport"),
        Messages.list("gui.placement.teleport-lore"),
        ACTION_TELEPORT
    )

    private fun particlesItem(): ItemStack {
        val on = worldPos !in BlacklistConfigManager.getBlacklist()
        return KeyIcons.button(
            if (on) Items.GLOWSTONE_DUST else Items.GUNPOWDER,
            Messages.text(if (on) "gui.placement.particles-on" else "gui.placement.particles-off"),
            Messages.list("gui.placement.particles-lore"),
            ACTION_PARTICLES
        )
    }

    private fun scopeItem(crateName: String): ItemStack = KeyIcons.button(
        if (editingPlacement) Items.LIME_STAINED_GLASS else Items.ORANGE_STAINED_GLASS,
        Messages.text(if (editingPlacement) "gui.placement.scope.placement" else "gui.placement.scope.crate"),
        Messages.list("gui.placement.scope-lore", "crate_name" to crateName),
        ACTION_SCOPE
    )

    private fun styleItem(
        resolved: ResolvedStyle, scoped: CrateStyle?, placement: CrateStyle?, crate: CrateStyle?
    ): ItemStack {
        val stack = KeyIcons.button(
            Items.FIREWORK_STAR,
            Messages.text("gui.placement.style", "value" to resolved.particleStyle.id),
            action = ACTION_STYLE
        )

        setLore(
            stack, setLine(scoped?.particleStyle) + sourceLine(
                StyleResolver.sourceOf(placement?.particleStyle, crate?.particleStyle)
            ) + Messages.list("gui.placement.cycle-lore")
        )
        return stack
    }

    /** A sound and its two knobs, laid out left to right. */
    private fun soundRow(
        slot: Int,
        name: Text,
        resolvedId: String,
        resolvedVolume: Float,
        resolvedPitch: Float,
        scoped: SoundOverride?,
        placement: SoundOverride?,
        crate: SoundOverride?,
        soundAction: String,
        volumeAction: String,
        pitchAction: String,
        item: Item
    ) {
        val known = GlobalConfigManager.soundEvent(resolvedId) != null
        val soundStack = KeyIcons.button(item, name, action = soundAction)
        setLore(
            soundStack, listOf(
                Messages.text("gui.placement.sound-value", "value" to resolvedId)
            ) + (if (known) emptyList() else listOf(Messages.text("gui.placement.sound-unknown"))) + setLine(scoped?.id) + sourceLine(
                StyleResolver.sourceOf(placement?.id, crate?.id)
            ) + Messages.list("gui.placement.sound-lore")
        )
        inventory.setStack(slot, soundStack)

        inventory.setStack(
            slot + 1, adjustItem(
                Items.LIGHT_BLUE_DYE,
                Messages.text("gui.placement.volume", "value" to decimals(resolvedVolume)),
                scoped?.volume?.let { decimals(it) },
                StyleResolver.sourceOf(placement?.volume, crate?.volume),
                decimals(SOUND_STEP),
                volumeAction
            )
        )

        inventory.setStack(
            slot + 2, adjustItem(
                Items.LIME_DYE,
                Messages.text("gui.placement.pitch", "value" to decimals(resolvedPitch)),
                scoped?.pitch?.let { decimals(it) },
                StyleResolver.sourceOf(placement?.pitch, crate?.pitch),
                decimals(SOUND_STEP),
                pitchAction
            )
        )
    }

    private fun adjustItem(
        item: Item,
        name: Text,
        scopedValue: String?,
        source: StyleSource,
        step: String,
        action: String
    ): ItemStack {
        val stack = KeyIcons.button(item, name, action = action)
        setLore(
            stack, setLine(scopedValue) + sourceLine(source) + Messages.list("gui.placement.adjust-lore", "step" to step)
        )
        return stack
    }

    /** What the layer being edited has set, as opposed to what is in force. */
    private fun setLine(value: String?): List<Text> = listOf(
        if (value == null) Messages.text("gui.placement.inherited")
        else Messages.text("gui.placement.set-here", "value" to value)
    )

    private fun sourceLine(source: StyleSource): List<Text> = listOf(
        when (source) {
            StyleSource.PLACEMENT -> Messages.text("gui.placement.source.placement")
            StyleSource.CRATE -> Messages.text("gui.placement.source.crate")
            StyleSource.GLOBAL -> Messages.text("gui.placement.source.global")
        }
    )

    /** Trailing zeroes off, and never a decimal comma: these strings go straight into item names. */
    private fun decimals(value: Float): String =
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    // endregion

    // region clicking
    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        // Clicking outside the window reports -999
        if (slotIndex < 0 || slotIndex >= 54 || player == null) return
        if (!PermissionUtil.isAdmin(player)) return

        val clicked = inventory.getStack(slotIndex)
        if (clicked.isEmpty) return

        val shift = actionType == SlotActionType.QUICK_MOVE
        val forward = button != 1

        when (KeyIcons.actionOf(clicked)) {
            ACTION_BACK -> return back(player)
            ACTION_TELEPORT -> return teleport(player)
            ACTION_PARTICLES -> return toggleParticles()
            ACTION_SCOPE -> {
                editingPlacement = !editingPlacement
                return populate()
            }

            ACTION_STYLE -> return cycleStyle(player, forward)

            ACTION_SHUFFLE -> return soundClick(player, shift, forward, shuffle = true)
            ACTION_REWARD -> return soundClick(player, shift, forward, shuffle = false)

            ACTION_SHUFFLE_VOLUME -> return adjustSound(player, shift, forward, shuffle = true, volume = true)
            ACTION_SHUFFLE_PITCH -> return adjustSound(player, shift, forward, shuffle = true, volume = false)
            ACTION_REWARD_VOLUME -> return adjustSound(player, shift, forward, shuffle = false, volume = true)
            ACTION_REWARD_PITCH -> return adjustSound(player, shift, forward, shuffle = false, volume = false)

            ACTION_STEPS -> return adjustSteps(player, shift, forward)
            ACTION_TICKS -> return adjustTicks(player, shift, forward)
            ACTION_SCALE -> return adjustScale(player, shift, forward)
        }
    }

    private fun back(player: PlayerEntity) {
        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
            ActiveCrateList(syncId, p, returnPage)
        }, Messages.text("gui.activecrates.title")))
    }

    private fun teleport(player: PlayerEntity) {
        val world = PebblesCrate.server?.worlds?.find { PebblesCrate.getWorldId(it) == worldPos.worldId }
        if (world == null) {
            Messages.send(player, "gui.placement.teleport-failed")
            return
        }

        val pos = worldPos.pos
        admin.teleport(world, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, admin.yaw, admin.pitch)
        Messages.send(
            player,
            "gui.placement.teleported",
            "crate_name" to (CrateDataManager.getCrateName(worldPos) ?: ""),
            "world" to worldPos.worldId
        )
        admin.closeHandledScreen()
    }

    /** The blacklist stays the hard off-switch: it wins over whatever style the crate has. */
    private fun toggleParticles() {
        if (worldPos in BlacklistConfigManager.getBlacklist()) {
            BlacklistConfigManager.removeFromBlacklist(worldPos)
        } else {
            BlacklistConfigManager.addToBlacklist(worldPos)
        }
        populate()
    }

    private fun cycleStyle(player: PlayerEntity, forward: Boolean) {
        val current = scopedStyle()?.particleStyle
        val next = cycled(STYLE_CYCLE, STYLE_CYCLE.firstOrNull { it.equals(current, ignoreCase = true) }, forward)
        edit(player) { it.copy(particleStyle = next) }
    }

    /**
     * A plain click plays the sound the crate would actually make, which is the only way to judge a
     * pitch. Changing it is deliberately the shifted click, so hunting through the list never means
     * hearing something you did not ask for.
     */
    private fun soundClick(player: PlayerEntity, shift: Boolean, forward: Boolean, shuffle: Boolean) {
        val resolved = resolvedStyle()
        val sound = if (shuffle) resolved.shuffleSound else resolved.rewardSounds.firstOrNull()

        if (!shift) {
            val event = sound?.let { GlobalConfigManager.soundEvent(it.id) } ?: return
            admin.playSoundToPlayer(event, SoundCategory.BLOCKS, sound.volume, sound.pitch)
            return
        }

        val current = scopedSound(shuffle)?.id
        val next = cycled(SOUND_PRESETS, SOUND_PRESETS.firstOrNull { it == current }, forward)
        editSound(player, shuffle) { it.copy(id = next) }
    }

    private fun adjustSound(player: PlayerEntity, shift: Boolean, forward: Boolean, shuffle: Boolean, volume: Boolean) {
        if (shift && forward) {
            editSound(player, shuffle) { if (volume) it.copy(volume = null) else it.copy(pitch = null) }
            return
        }

        val resolved = resolvedStyle()
        val sound = (if (shuffle) resolved.shuffleSound else resolved.rewardSounds.firstOrNull()) ?: return
        val scoped = scopedSound(shuffle)
        val delta = if (forward) SOUND_STEP else -SOUND_STEP

        if (volume) {
            val base = scoped?.volume ?: sound.volume
            val next = rounded(base + delta).coerceIn(CrateStyles.MIN_VOLUME, CrateStyles.MAX_VOLUME)
            editSound(player, shuffle) { it.copy(volume = next) }
        } else {
            val base = scoped?.pitch ?: sound.pitch
            val next = rounded(base + delta).coerceIn(CrateStyles.MIN_PITCH, CrateStyles.MAX_PITCH)
            editSound(player, shuffle) { it.copy(pitch = next) }
        }
    }

    private fun adjustSteps(player: PlayerEntity, shift: Boolean, forward: Boolean) {
        if (shift && forward) return edit(player) { it.copy(animationSteps = null) }

        val base = scopedStyle()?.animationSteps ?: resolvedStyle().steps
        val next = (base + if (forward) 1 else -1).coerceIn(CrateStyles.MIN_STEPS, CrateStyles.MAX_STEPS)
        edit(player) { it.copy(animationSteps = next) }
    }

    private fun adjustTicks(player: PlayerEntity, shift: Boolean, forward: Boolean) {
        if (shift && forward) return edit(player) { it.copy(ticksPerStep = null) }

        val base = scopedStyle()?.ticksPerStep ?: resolvedStyle().ticksPerStep.toInt()
        val next =
            (base + if (forward) 1 else -1).coerceIn(CrateStyles.MIN_TICKS_PER_STEP, CrateStyles.MAX_TICKS_PER_STEP)
        edit(player) { it.copy(ticksPerStep = next) }
    }

    private fun adjustScale(player: PlayerEntity, shift: Boolean, forward: Boolean) {
        if (shift && forward) return edit(player) { it.copy(finalScale = null) }

        val base = scopedStyle()?.finalScale ?: resolvedStyle().finalScale.toDouble()
        val next = ((base + if (forward) SCALE_STEP else -SCALE_STEP) * 100).roundToInt() / 100.0
        edit(player) { it.copy(finalScale = next.coerceIn(GUI_MIN_SCALE, GUI_MAX_SCALE)) }
    }
    // endregion

    // region state
    private fun placementStyle(): CrateStyle? = CrateDataManager.getPlacement(worldPos)?.style

    private fun crateStyle(): CrateStyle? =
        CrateDataManager.getCrateName(worldPos)?.let { CrateConfigManager.getCrateConfig(it)?.style }

    /** The layer currently being edited. */
    private fun scopedStyle(): CrateStyle? = if (editingPlacement) placementStyle() else crateStyle()

    private fun scopedSound(shuffle: Boolean): SoundOverride? =
        scopedStyle()?.let { if (shuffle) it.shuffleSound else it.rewardSound }

    private fun resolvedStyle(): ResolvedStyle = StyleResolver.resolve(placementStyle(), crateStyle())

    private fun editSound(player: PlayerEntity, shuffle: Boolean, transform: (SoundOverride) -> SoundOverride) {
        edit(player) { style ->
            if (shuffle) {
                style.copy(shuffleSound = transform(style.shuffleSound ?: SoundOverride()).orNull())
            } else {
                style.copy(rewardSound = transform(style.rewardSound ?: SoundOverride()).orNull())
            }
        }
    }

    /** Writes a change to whichever layer the scope button is showing, then redraws from disk state. */
    private fun edit(player: PlayerEntity, transform: (CrateStyle) -> CrateStyle) {
        val placement = CrateDataManager.getPlacement(worldPos)
        if (placement == null) {
            populate()
            return
        }

        val saved = if (editingPlacement) {
            CrateDataManager.setPlacementStyle(worldPos, transform(placement.style ?: CrateStyle()).orNull())
        } else {
            val crateConfig = CrateConfigManager.getCrateConfig(placement.name)
            if (crateConfig == null) {
                Messages.send(player, "gui.placement.crate-missing", "crate_name" to placement.name)
                false
            } else {
                CrateConfigManager.setCrateStyle(
                    placement.name, transform(crateConfig.style ?: CrateStyle()).orNull()
                )
            }
        }

        if (!saved) Messages.send(player, "gui.placement.save-failed")
        populate()
    }

    private fun <T> cycled(options: List<T>, current: T?, forward: Boolean): T {
        val index = options.indexOf(current).coerceAtLeast(0)
        val next = if (forward) index + 1 else index - 1 + options.size
        return options[next % options.size]
    }

    private fun rounded(value: Float): Float = (value * 10).roundToInt() / 10f
    // endregion

    companion object {
        private const val INFO_SLOT = 4
        private const val TELEPORT_SLOT = 10
        private const val PARTICLES_SLOT = 12
        private const val SCOPE_SLOT = 14
        private const val STYLE_SLOT = 16
        private const val SHUFFLE_SLOT = 19
        private const val REWARD_SLOT = 23
        private const val STEPS_SLOT = 29
        private const val TICKS_SLOT = 31
        private const val SCALE_SLOT = 33
        private const val BACK_SLOT = 45

        private const val SOUND_STEP = 0.1f
        private const val SCALE_STEP = 0.25
        private const val GUI_MIN_SCALE = 0.5
        private const val GUI_MAX_SCALE = 4.0

        private const val ACTION_BACK = "placement_back"
        private const val ACTION_TELEPORT = "placement_teleport"
        private const val ACTION_PARTICLES = "placement_particles"
        private const val ACTION_SCOPE = "placement_scope"
        private const val ACTION_STYLE = "placement_style"
        private const val ACTION_SHUFFLE = "placement_shuffle"
        private const val ACTION_SHUFFLE_VOLUME = "placement_shuffle_volume"
        private const val ACTION_SHUFFLE_PITCH = "placement_shuffle_pitch"
        private const val ACTION_REWARD = "placement_reward"
        private const val ACTION_REWARD_VOLUME = "placement_reward_volume"
        private const val ACTION_REWARD_PITCH = "placement_reward_pitch"
        private const val ACTION_STEPS = "placement_steps"
        private const val ACTION_TICKS = "placement_ticks"
        private const val ACTION_SCALE = "placement_scale"

        /** null is "inherit", and is part of the cycle so a style can be cleared without a second gesture. */
        private val STYLE_CYCLE: List<String?> = ParticleStyle.ids + listOf(null)

        /**
         * Sounds worth putting on a crate, so the common cases need no config file. Anything else
         * vanilla or modded can still be set by hand in the JSON; the screen leaves it alone and
         * shows it as the value in force.
         */
        private val SOUND_PRESETS: List<String?> = listOf(
            null,
            "minecraft:block.note_block.banjo",
            "minecraft:block.note_block.bell",
            "minecraft:block.note_block.harp",
            "minecraft:block.note_block.pling",
            "minecraft:block.note_block.bit",
            "minecraft:block.amethyst_block.chime",
            "minecraft:block.ender_chest.open",
            "minecraft:block.beacon.activate",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:entity.player.levelup",
            "minecraft:entity.firework_rocket.twinkle",
            "minecraft:item.totem.use",
            "minecraft:ui.button.click"
        )

        fun open(player: ServerPlayerEntity, worldPos: WorldBlockPos, returnPage: Int) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CratePlacementScreenHandler(syncId, p as ServerPlayerEntity, worldPos, returnPage)
            }, Messages.text("gui.placement.title")))
        }
    }
}
