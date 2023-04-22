package tech.sethi.pebbleslootcrate.commands

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text

import net.minecraft.server.command.CommandManager.literal
import tech.sethi.pebbles.crates.screenhandlers.CrateListScreenHandler

object CrateCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(literal("padmin")
            .then(literal("crate")
                .executes { context ->
                    val source = context.source
                    val player = source.player

                    // Open the crate UI
                    if (player != null) {
                        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, inv, p ->
                            CrateListScreenHandler(syncId, p)
                        }, Text.literal("Crate Management")))
                    }

                    1
                }))
    }
}
