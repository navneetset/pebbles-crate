package tech.sethi.pebbles.crates.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import java.util.concurrent.CompletableFuture

class GetCrateCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("padmin")
            .requires { it.hasPermissionLevel(2) } // Require OP permission level
            .then(
                CommandManager.literal("crate").then(
                    CommandManager.literal("get")
                        .then(CommandManager.argument("crateName", StringArgumentType.string())
                            .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                            .executes { context -> getCrate(context) })
                )
            )
        )
    }

    private fun getCrateNameSuggestions(
        context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val crateConfigManager = CrateConfigManager()
        val crateNames = crateConfigManager.loadCrateConfigs().map { it.crateName }
        return CommandSource.suggestMatching(crateNames, builder)
    }

    private fun getCrate(context: CommandContext<ServerCommandSource>): Int {
        val crateConfigManager = CrateConfigManager()
        val crateName = StringArgumentType.getString(context, "crateName")
        val crateConfig = crateConfigManager.getCrateConfig(crateName)

        if (crateConfig != null) {
            val player = context.source.player
            if (player != null) {
                val crateItemStack = ItemStack(Items.PAPER)
                crateItemStack.setCustomName(Text.literal(crateName))

                //get all prize.names and add them to the lore
                val prizeLore = crateConfig.prize.map { Text.literal(it.name) }
                setLore(crateItemStack, prizeLore)

                val nbt = crateItemStack.orCreateNbt
                nbt.putString("CrateName", crateName)
                crateItemStack.nbt = nbt

                player.sendMessage(Text.literal("Giving $crateName to ${player.name.string}"), false)

                player.giveItemStack(crateItemStack)
                context.source.sendFeedback(
                    Text.literal("Successfully gave $crateName to ${player.name.string}"), false
                )
                return 1
            }
        } else {
            context.source.sendError(Text.literal("Crate with name $crateName not found"))
            return -1
        }
        return 1
    }


//    private fun giveCrateKey(source: ServerCommandSource, crateConfig: CrateConfig) {
//        val player = source.player
//        if (player != null) {
//            val materialIdentifier = Identifier.tryParse(crateConfig.crateKey.material)
//            if (materialIdentifier != null) {
//                val item = Registry.ITEM.get(materialIdentifier)
//                if (item != Items.AIR) {
//                    val crateKeyItemStack = ItemStack(item)
//                    crateKeyItemStack.setCustomName(Text.literal(crateConfig.crateKey.name))
//
//                    // Set the lore for the crate key item
//                    val crateKeyLore = crateConfig.crateKey.lore.map { Text.literal(it) }
//                    crateKeyItemStack.setLore(crateKeyLore)
//
//                    player.giveItemStack(crateKeyItemStack)
//                    source.sendFeedback(
//                        Text.literal("Successfully gave ${crateConfig.crateKey.name} to ${player.name.string}"), false
//                    )
//                }
//            }
//        }
//    }

     fun previewCratePrize(
        source: ServerCommandSource,
        crateConfig: CrateConfig
    ) {
        val player = source.player ?: return
        val title = Text.translatable(crateConfig.crateName)
        val screenHandlerFactory = PrizeDisplayScreenHandlerFactory(title, crateConfig)
        player.openHandledScreen(screenHandlerFactory)
    }



    private fun setLore(itemStack: ItemStack, lore: List<Text>) {
        val itemNbt = itemStack.getOrCreateSubNbt("display")
        val loreNbt = NbtList()

        for (line in lore) {
            loreNbt.add(NbtString.of(Text.Serializer.toJson(line)))
        }

        itemNbt.put("Lore", loreNbt)
    }
}
