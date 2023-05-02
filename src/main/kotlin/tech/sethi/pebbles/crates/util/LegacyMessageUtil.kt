package tech.sethi.pebbles.crates.util

import net.kyori.adventure.text.Component
import net.minecraft.server.network.ServerPlayerEntity
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.text.Text

fun parse(text: String, vararg placeholders: Any): Component {
    val legecySerializer = LegacyComponentSerializer.legacyAmpersand()
    val formattedText = String.format(text, *placeholders)
    return legecySerializer.deserialize(formattedText)
}

fun parseMessageWithStyles(text: String, prizeName: String): Component {
    val legecySerializer = LegacyComponentSerializer.legacyAmpersand()
    val styledPrizeName = legecySerializer.deserialize(prizeName)

    val parts = text.split(Regex.escape("{prize_name}"))

    val styledParts = mutableListOf<Component>()

    for (i in parts.indices) {
        val styledPart = legecySerializer.deserialize(parts[i])
        styledParts.add(styledPart)
        if (i < parts.size - 1) {
            styledParts.add(styledPrizeName)
        }
    }

    return Component.join(Component.empty(), styledParts)
}

class ParseableMessage(
    private val message: String,
    private val player : ServerPlayerEntity? = null,
    private val prizeName: String,
) {
    fun sendToAll() {
        val component = parseMessageWithStyles(message, prizeName)
        val serverAudiences = FabricServerAudiences.of(player!!.server)
        serverAudiences.all().sendMessage(component)
    }

    fun send() {
        val component = parseMessageWithStyles(message, prizeName)
        val serverAudiences = FabricServerAudiences.of(player!!.server)
        serverAudiences.player(player.uuid).sendMessage(component)
    }

    fun returnMessageAsStyledText(): Text {
        val component = parseMessageWithStyles(message, prizeName)
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return Text.Serializer.fromJson(json) as Text
    }
}

class ParseableName(
    private val name: String,
) {
    fun returnMessageAsStyledText(): Text {
        val component = parseMessageWithStyles(name, "")
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return Text.Serializer.fromJson(json) as Text
    }
}