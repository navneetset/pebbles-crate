package tech.sethi.pebbles.crates.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.item.ItemStack
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager
import tech.sethi.pebbles.crates.keys.PhysicalKeyProvider
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.ActiveCrateList
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler
import tech.sethi.pebbles.crates.screenhandlers.keys.KeyBalanceScreenHandler
import tech.sethi.pebbles.crates.util.PermissionUtil
import java.util.concurrent.CompletableFuture

object CrateCommand {
    /** A hundred stacks. Beyond this a typo costs the server an inventory full of dropped keys. */
    private const val MAX_KEY_AMOUNT = 6400

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val padminCommand = literal("padmin").requires { source -> PermissionUtil.isAdmin(source) }

        val crateCommand = literal("crate").executes { context ->
            val source = context.source

            // Open the crate UI
            source.player?.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CrateListScreenHandler(syncId, p)
            }, Messages.text("gui.crates.title")))

            1
        }

        val getCrateCommand = literal("getcrate").then(
            CommandManager.argument("crateName", StringArgumentType.greedyString())
                .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                .executes { context -> getCrate(context) },
        )

        // The bare form follows whatever the crate is configured for; `physical` and `virtual`
        // force one, so a virtual server can still mint a real key item and vice versa.
        val giveKeyCommand = literal("givekey").then(
            CommandManager.argument("player", EntityArgumentType.players()).then(
                CommandManager.argument("amount", IntegerArgumentType.integer(1, MAX_KEY_AMOUNT))
                    .then(CommandManager.argument("crateName", StringArgumentType.greedyString())
                        .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                        .executes { context -> giveCrateKey(context, null) })
            )
        )

        if (KeyManager.featureEnabled) {
            giveKeyCommand.then(literal("physical").then(giveKeyArguments { context -> giveCrateKey(context, false) }))
            giveKeyCommand.then(literal("virtual").then(giveKeyArguments { context -> giveCrateKey(context, true) }))
        }


        val activeCrateConfigCommand = literal("activecrates").executes { context ->
            val source = context.source

            source.player?.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                ActiveCrateList(syncId, p)
            }, Messages.text("gui.activecrates.title")))

            1
        }

        val reloadCommand = literal("reload").executes { context ->
            PebblesCrate.reloadConfigs()
            Messages.feedback(context.source, "command.reloaded")
            1
        }

        padminCommand.then(crateCommand).then(getCrateCommand).then(giveKeyCommand).then(activeCrateConfigCommand)
            .then(reloadCommand)

        if (KeyManager.featureEnabled) {
            padminCommand.then(
                literal("keys").then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes { context -> viewPlayerKeys(context) })
            )
            padminCommand.then(
                literal("convertkeys").then(CommandManager.argument("player", EntityArgumentType.players())
                    .executes { context -> convertKeys(context) })
            )
        }

        dispatcher.register(padminCommand)
    }

    private fun giveKeyArguments(execute: (CommandContext<ServerCommandSource>) -> Int) =
        CommandManager.argument("player", EntityArgumentType.players()).then(
            CommandManager.argument("amount", IntegerArgumentType.integer(1, MAX_KEY_AMOUNT))
                .then(CommandManager.argument("crateName", StringArgumentType.greedyString())
                    .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                    .executes { context -> execute(context) })
        )

    private fun getCrateNameSuggestions(
        context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return CommandSource.suggestMatching(CrateConfigManager.getCrateNames(), builder)
    }

    private fun getCrate(context: CommandContext<ServerCommandSource>): Int {
        val crateName = StringArgumentType.getString(context, "crateName")

        // The transformer paper has to land in someone's inventory, so the console cannot get one
        val player = context.source.player
        if (player == null) {
            Messages.sendError(context.source, "command.player-only", "command" to "/padmin getcrate")
            return 0
        }

        if (CrateConfigManager.getCrateConfig(crateName) == null) {
            Messages.sendError(context.source, "command.crate-unknown", "crate_name" to crateName)
            return 0
        }

        CrateTransformer(crateName, player).giveTransformer()
        return 1
    }


    /** [virtual] null means "whatever this crate is configured for". */
    private fun giveCrateKey(context: CommandContext<ServerCommandSource>, virtual: Boolean?): Int {
        val crateName = StringArgumentType.getString(context, "crateName")
        val amount = IntegerArgumentType.getInteger(context, "amount")

        val crateConfig = CrateConfigManager.getCrateConfig(crateName)
        if (crateConfig == null) {
            Messages.sendError(context.source, "command.crate-unknown", "crate_name" to crateName)
            return 0
        }

        val giveVirtual = virtual ?: KeyManager.isVirtual(crateConfig)
        if (giveVirtual && !KeyManager.active) {
            Messages.sendError(context.source, "command.virtual-disabled")
            return 0
        }

        val players = EntityArgumentType.getPlayers(context, "player")
        var given = 0

        for (player in players) {
            val who = player.name.string

            if (!giveVirtual) {
                if (!CrateTransformer(crateName, player).giveKey(amount, player)) {
                    Messages.sendError(context.source, "command.givekey.no-item", "crate_name" to crateName)
                    continue
                }
                Messages.feedback(
                    context.source,
                    "command.givekey.physical",
                    "player_name" to who,
                    "amount" to "$amount",
                    "crate_name" to crateName
                )
                given++
                continue
            }

            if (!KeyManager.isReady(player.uuid)) {
                Messages.sendError(context.source, "command.keys-loading", "player_name" to who)
                continue
            }

            if (!KeyManager.add(player.uuid, crateName, amount)) {
                Messages.sendError(context.source, "command.givekey.failed", "player_name" to who)
                continue
            }

            Messages.send(player, "key.received-virtual", "amount" to "$amount", "crate_name" to crateName)
            Messages.feedback(
                context.source,
                "command.givekey.virtual",
                "player_name" to who,
                "amount" to "$amount",
                "crate_name" to crateName
            )
            given++
        }

        return given
    }

    private fun viewPlayerKeys(context: CommandContext<ServerCommandSource>): Int {
        val admin = context.source.player
        if (admin == null) {
            Messages.sendError(context.source, "command.player-only", "command" to "/padmin keys")
            return 0
        }

        val target = EntityArgumentType.getPlayer(context, "player")
        if (!KeyManager.isReady(target.uuid)) {
            Messages.sendError(context.source, "command.keys-loading", "player_name" to target.name.string)
            return 0
        }

        KeyBalanceScreenHandler.open(
            admin,
            Messages.text("gui.keys.other-title", "player_name" to target.name.string),
            KeyManager.getBalances(target.uuid)
        )
        return 1
    }

    /**
     * Turns the physical keys a player is carrying into wallet balance. This is the migration path
     * for a server switching an existing crate over: without it every key already in circulation
     * would simply stop working.
     */
    private fun convertKeys(context: CommandContext<ServerCommandSource>): Int {
        if (!KeyManager.active) {
            Messages.sendError(context.source, "command.virtual-disabled")
            return 0
        }

        // Only crates that actually spend virtual keys; converting the rest would destroy usable keys.
        val virtualCrates = CrateConfigManager.getCrateConfigs().filter { KeyManager.isVirtual(it) }
        if (virtualCrates.isEmpty()) {
            Messages.sendError(context.source, "command.convertkeys.none-configured")
            return 0
        }

        val players = EntityArgumentType.getPlayers(context, "player")
        var converted = 0

        for (player in players) {
            if (!KeyManager.isReady(player.uuid)) {
                Messages.sendError(context.source, "command.keys-loading", "player_name" to player.name.string)
                continue
            }

            val counts = linkedMapOf<String, Int>()
            for (slot in 0 until player.inventory.size()) {
                val stack = player.inventory.getStack(slot)
                if (stack.isEmpty) continue

                val crate = virtualCrates.firstOrNull { PhysicalKeyProvider.matches(stack, it) } ?: continue

                // Credited before the item is removed: a failed write must not eat the key.
                if (!KeyManager.add(player.uuid, crate.crateName, stack.count)) continue
                counts[crate.crateName] = (counts[crate.crateName] ?: 0) + stack.count
                player.inventory.setStack(slot, ItemStack.EMPTY)
            }

            if (counts.isEmpty()) {
                Messages.feedback(context.source, "command.convertkeys.nothing", "player_name" to player.name.string)
                continue
            }

            val summary = counts.entries.joinToString(", ") { "${it.value} ${it.key}" }
            Messages.send(player, "command.convertkeys.player", "summary" to summary)
            Messages.feedback(
                context.source,
                "command.convertkeys.report",
                "player_name" to player.name.string,
                "summary" to summary
            )
            converted++
        }

        return converted
    }
}
