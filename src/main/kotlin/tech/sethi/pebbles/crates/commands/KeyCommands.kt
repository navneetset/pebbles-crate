package tech.sethi.pebbles.crates.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import tech.sethi.pebbles.crates.config.Messages
import tech.sethi.pebbles.crates.keys.KeyManager
import tech.sethi.pebbles.crates.screenhandlers.keys.KeyBalanceScreenHandler
import tech.sethi.pebbles.crates.screenhandlers.keys.KeySendScreenHandler
import tech.sethi.pebbles.crates.util.PermissionUtil
import java.util.concurrent.CompletableFuture

/**
 * The player-facing `/keys` tree. Only registered when virtual keys are switched on, so a server
 * that has never enabled them sees no trace of the feature in its command list.
 */
object KeyCommand {
    private const val VIEW_BUCKET = "keys.view"
    private const val SEND_BUCKET = "keys.send"

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val keysCommand = literal("keys").requires { PermissionUtil.canUseKeys(it) }.executes { context ->
            openOwnBalance(context)
        }.then(literal("view").executes { context -> openOwnBalance(context) }).then(literal("send").executes { context ->
            val player = context.source.player ?: return@executes playerOnly(context)
            if (!CommandCooldowns.allow(player, SEND_BUCKET, 3)) return@executes 0
            KeySendScreenHandler.open(player)
            1
        }.then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                .then(CommandManager.argument("crateName", StringArgumentType.greedyString())
                    .suggests { context, builder -> suggestOwnKeys(context, builder) }
                    .executes { context -> send(context) }))))

        dispatcher.register(keysCommand)
    }

    private fun openOwnBalance(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return playerOnly(context)
        if (!CommandCooldowns.allow(player, VIEW_BUCKET, 1)) return 0

        if (!KeyManager.isReady(player.uuid)) {
            Messages.send(player, "key.loading")
            return 0
        }

        KeyBalanceScreenHandler.openOwn(player)
        return 1
    }

    private fun send(context: CommandContext<ServerCommandSource>): Int {
        val sender = context.source.player ?: return playerOnly(context)
        val target = EntityArgumentType.getPlayer(context, "player")
        val amount = IntegerArgumentType.getInteger(context, "amount")
        val crateName = StringArgumentType.getString(context, "crateName")

        // Rejected before anything is charged, and before the cooldown is spent on it.
        if (target.uuid == sender.uuid) {
            Messages.sendError(context.source, "keys.self-send")
            return 0
        }

        // Ahead of the balance check, so a failing send is rate limited like a succeeding one.
        if (!CommandCooldowns.allow(sender, SEND_BUCKET, 3)) return 0

        if (!KeyManager.isReady(sender.uuid) || !KeyManager.isReady(target.uuid)) {
            Messages.sendError(context.source, "keys.loading")
            return 0
        }

        val held = KeyManager.getBalance(sender.uuid, crateName)
        if (held < amount) {
            Messages.sendError(context.source, "keys.not-enough", "amount" to "$held", "crate_name" to crateName)
            return 0
        }

        if (!KeyManager.transfer(sender.uuid, target.uuid, crateName, amount)) {
            Messages.sendError(context.source, "keys.send-failed")
            return 0
        }

        Messages.send(
            sender, "keys.sent", "amount" to "$amount", "crate_name" to crateName, "player_name" to target.name.string
        )
        Messages.send(
            target,
            "keys.received",
            "amount" to "$amount",
            "crate_name" to crateName,
            "player_name" to sender.name.string
        )
        return 1
    }

    private fun playerOnly(context: CommandContext<ServerCommandSource>): Int {
        Messages.sendError(context.source, "command.player-only", "command" to "/keys")
        return 0
    }

    /** Only the crates this player actually holds keys for - the rest would just fail. */
    private fun suggestOwnKeys(
        context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val player = context.source.player ?: return Suggestions.empty()
        return CommandSource.suggestMatching(KeyManager.getBalances(player.uuid).keys, builder)
    }
}
