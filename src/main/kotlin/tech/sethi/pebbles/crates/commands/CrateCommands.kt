package tech.sethi.pebbleslootcrate.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import net.minecraft.server.command.CommandManager.literal
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.ActiveCrateList
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler
import tech.sethi.pebbles.crates.util.ParseableMessage
import java.util.concurrent.CompletableFuture

object CrateCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val padminCommand = literal("padmin").requires { source ->
            val player = source.player as? PlayerEntity
            player != null && (source.hasPermissionLevel(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin.crate").asBoolean()) || source.entity == null
        }

        val crateCommand = literal("crate").requires { source ->
            val player = source.player as? PlayerEntity
            player != null && (source.hasPermissionLevel(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
                player.uuid
            )!!.cachedData.permissionData.checkPermission("pebbles.admin.crate")
                .asBoolean()) || source.entity == null
        }.executes { context ->
            val source = context.source

            // Open the crate UI
            source.player?.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                CrateListScreenHandler(syncId, p)
            }, Text.literal("Crate Management")))

            1
        }

        val getCrateCommand = literal("getcrate").then(
            CommandManager.argument("crateName", StringArgumentType.greedyString())
                .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                .executes { context -> getCrate(context) },
        )

        val giveKeyCommand = literal("givekey").then(
            CommandManager.argument("player", EntityArgumentType.players()).then(
                CommandManager.argument("amount", IntegerArgumentType.integer(1))
                    .then(CommandManager.argument("crateName", StringArgumentType.greedyString())
                        .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                        .executes { context -> giveCrateKey(context) })
            )
        )


        val activeCrateConfigCommand = literal("activecrates").executes { context ->
            val source = context.source

            source.player?.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, _, p ->
                ActiveCrateList(syncId, p)
            }, Text.literal("Blacklist Particles")))

            1
        }

        // Register the commands
        dispatcher.register(
            padminCommand.then(crateCommand).then(getCrateCommand).then(giveKeyCommand).then(activeCrateConfigCommand)
        )
    }

    private fun isLuckPermsPresent(): Boolean {
        return try {
            Class.forName("net.luckperms.api.LuckPerms")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun getLuckPermsApi(): LuckPerms? {
        return try {
            LuckPermsProvider.get()
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun getCrateNameSuggestions(
        context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val crateConfigManager = CrateConfigManager()
        val crateNames = crateConfigManager.loadCrateConfigs().map { it.crateName }
        return CommandSource.suggestMatching(crateNames, builder)
    }

    private fun getCrate(context: CommandContext<ServerCommandSource>): Int {

        val crateName = StringArgumentType.getString(context, "crateName")
        val crateTransformer = CrateTransformer(crateName, context.source.player as PlayerEntity)

        crateTransformer.giveTransformer()
        return 1
    }


    private fun giveCrateKey(context: CommandContext<ServerCommandSource>): Int {
        val crateName = StringArgumentType.getString(context, "crateName")
        val amount = IntegerArgumentType.getInteger(context, "amount")

        val players = EntityArgumentType.getPlayers(context, "player")
        for (player in players) {
            CrateTransformer(crateName, player).giveKey(amount, player)
            val adminMessage = "${player.name.string} received $amount $crateName keys!"

            if (context.source.player != null) {
                ParseableMessage(adminMessage, context.source.player, "placeholder").send()
                println(adminMessage)
            }

            println(adminMessage)

        }

        return 1
    }


}
