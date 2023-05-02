package tech.sethi.pebbles.crates.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import tech.sethi.pebbles.crates.screenhandlers.PrizeDisplayScreenHandlerFactory
import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateTransformer
import tech.sethi.pebbles.crates.util.ParseableMessage
import java.util.concurrent.CompletableFuture

class GetCrateCommand {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("padmin")
            .requires { it.hasPermissionLevel(2) } // Require OP permission level
            .then(
                CommandManager.literal("crate").then(
                    CommandManager.literal("get").then(
                        CommandManager.argument("crateName", StringArgumentType.greedyString())
                            .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                            .executes { context -> getCrate(context) },
                    ),
                ).then(
                    CommandManager.literal("givekey").then(
                                CommandManager.argument("player", StringArgumentType.string())
                                    .suggests { context, builder ->
                                        CommandSource.suggestMatching(
                                            context.source.server.playerManager.playerList.map { it.name.string },
                                            builder
                                        )
                                    }.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .then(CommandManager.argument("crateName", StringArgumentType.greedyString())
                                            .suggests { context, builder -> getCrateNameSuggestions(context, builder) }
                                            .executes { context -> giveCrateKey(context) })
                                    )
                            ),
                    ),
                ),
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

        val crateName = StringArgumentType.getString(context, "crateName")
        val crateTransformer = CrateTransformer(crateName, context.source.player as PlayerEntity)

        crateTransformer.giveTransformer()
        return 1
    }


    private fun giveCrateKey(context: CommandContext<ServerCommandSource>): Int {

        val crateName = StringArgumentType.getString(context, "crateName")
        val crateTransformer = CrateTransformer(crateName, context.source.player as PlayerEntity)

        val playerName = StringArgumentType.getString(context, "player")
        val player = context.source.server.playerManager.getPlayer(playerName)

        val amount = IntegerArgumentType.getInteger(context, "amount")

        crateTransformer.giveKey(amount, player as PlayerEntity)

//        val playerName = StringArgumentType.getString(context, "player")
//        val player = context.source.server.playerManager.getPlayer(playerName)
//        val source = context.source
//        val crateName = StringArgumentType.getString(context, "crateName")
//        val crateConfigManager = CrateConfigManager()
//        val crateConfig = crateConfigManager.getCrateConfig(crateName) ?: return -1
//        if (player != null) {
//            val materialIdentifier = Identifier.tryParse(crateConfig.crateKey.material)
//            if (materialIdentifier != null) {
//                val amount = IntegerArgumentType.getInteger(context, "amount")
//
//                val item = Registry.ITEM.get(materialIdentifier)
//                if (item != Items.AIR) {
//                    val crateKeyItemStack = ItemStack(item, amount)
//                    val parsedName =
//                        ParseableMessage(crateConfig.crateKey.name, player, "placeholder").returnMessageAsStyledText()
//
//                    crateKeyItemStack.setCustomName(parsedName)
//
//                    // Set the lore for the crate key item
//                    val crateKeyLore = crateConfig.crateKey.lore
//                    val parsedCrateKeyLore = crateKeyLore.map {
//                        ParseableMessage(it, player, "placeholder").returnMessageAsStyledText()
//                    }
//                    setLore(crateKeyItemStack, parsedCrateKeyLore)
//
//                    val nbt = crateKeyItemStack.orCreateNbt
//                    nbt.putString("CrateName", crateConfig.crateName)
//
//                    player.giveItemStack(crateKeyItemStack)
//
//                    source.sendFeedback(
//                        Text.literal("Successfully gave ${crateConfig.crateKey.name} to ${player.name.string}"), false
//                    )
//                    val message = "You received a ${crateConfig.crateKey.name} for ${crateConfig.crateName}!"
//                    ParseableMessage(message, player, "placeholder").send()
//                }
//            }
//        }
        return 1
    }

    fun previewCratePrize(
        source: ServerCommandSource, crateConfig: CrateConfig
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
