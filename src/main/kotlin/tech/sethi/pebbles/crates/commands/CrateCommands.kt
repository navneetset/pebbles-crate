package tech.sethi.pebbleslootcrate.commands

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text

import net.minecraft.server.command.CommandManager.literal
import tech.sethi.pebbles.crates.screenhandlers.admin.cratelist.CrateListScreenHandler

object CrateCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(literal("padmin").requires { source -> source.hasPermissionLevel(2) }
            .then(literal("crate").executes { context ->
                val source = context.source

                // Open the crate UI
                source.player?.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, inv, p ->
                    CrateListScreenHandler(syncId, p)
                }, Text.literal("Crate Management")))

                1
            })
        )
    }
}
