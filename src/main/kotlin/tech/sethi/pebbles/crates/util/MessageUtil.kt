package tech.sethi.pebbles.crates.util

import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.config.GlobalConfigManager
import tech.sethi.pebbles.crates.config.TextType

private val logger = LoggerFactory.getLogger("pebbles-crates")

private var cachedAudiencesServer: MinecraftServer? = null
private var cachedAudiences: FabricServerAudiences? = null

/**
 * `&a` colour codes plus the `&#RRGGBB` hex form. Adventure's `legacyAmpersand()` has hex support
 * switched off, so every hex colour in a crate config used to be printed as literal text.
 */
private val LEGACY_SERIALIZER: LegacyComponentSerializer =
    LegacyComponentSerializer.builder().character('&').hexColors().build()

private val MINI_SERIALIZER: MiniMessage = MiniMessage.miniMessage()

/**
 * Live configs write hex colours both as `&#RRGGBB` and as a bare `#RRGGBB`; only the first is
 * legacy syntax, so the bare form is promoted before the serializer ever sees it. A `#` already
 * inside a MiniMessage tag is left alone.
 */
private val BARE_HEX_COLOR = Regex("(?<![&§<])#([0-9a-fA-F]{6})")

/** Colour codes as written in configs, used to strip them back out of the fallback text. */
private val LEGACY_CODE = Regex("[&§](#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])")

/** The MiniMessage tag each legacy code stands for, so both syntaxes can share one document. */
private val LEGACY_CODE_TAGS = mapOf(
    '0' to "black",
    '1' to "dark_blue",
    '2' to "dark_green",
    '3' to "dark_aqua",
    '4' to "dark_red",
    '5' to "dark_purple",
    '6' to "gold",
    '7' to "gray",
    '8' to "dark_gray",
    '9' to "blue",
    'a' to "green",
    'b' to "aqua",
    'c' to "red",
    'd' to "light_purple",
    'e' to "yellow",
    'f' to "white",
    'k' to "obfuscated",
    'l' to "bold",
    'm' to "strikethrough",
    'n' to "underlined",
    'o' to "italic",
    'r' to "reset"
)

private fun acceptBareHexColors(text: String): String = BARE_HEX_COLOR.replace(text, "&#$1")

/** A MiniMessage tag, whose insides belong to MiniMessage and must be left exactly as written. */
private val MINI_TAG = Regex("<[^<>]*>")

/**
 * Rewrites `&`-codes as MiniMessage tags. Servers that switch `textType` to MINIMESSAGE almost never
 * rewrite every crate file at the same time, so a config mixing `&6` into `<gradient>` still comes
 * out coloured instead of printing the codes as text.
 *
 * Only the text between tags is touched: `<gradient:#ff0000:#0000ff>` already contains two hex
 * colours, and promoting those to colour tags of their own would break the gradient.
 */
private fun legacyCodesAsTags(text: String): String {
    val translated = StringBuilder()
    var index = 0

    for (tag in MINI_TAG.findAll(text)) {
        translated.append(legacyCodesInPlainText(text.substring(index, tag.range.first)))
        translated.append(tag.value)
        index = tag.range.last + 1
    }
    translated.append(legacyCodesInPlainText(text.substring(index)))

    return translated.toString()
}

private fun legacyCodesInPlainText(text: String): String =
    LEGACY_CODE.replace(acceptBareHexColors(text)) { match ->
        val code = match.groupValues[1]
        when {
            code.startsWith("#") -> "<$code>"
            else -> LEGACY_CODE_TAGS[code.lowercase()[0]]?.let { "<$it>" } ?: match.value
        }
    }

/**
 * One string of config text as a component, using whichever serializer `textType` names.
 *
 * Italics are switched off on both paths: item names and lore render italic by default in-game and
 * nothing the mod writes is meant to be.
 */
fun styledComponent(text: String): Component {
    val component = when (GlobalConfigManager.textType) {
        TextType.MINIMESSAGE -> try {
            MINI_SERIALIZER.deserialize(legacyCodesAsTags(text))
        } catch (e: Exception) {
            // Malformed tags should cost the styling, not the message.
            logger.warn("[Pebbles-Crates] Could not read '$text' as MiniMessage, showing it as legacy text: ${e.message}")
            LEGACY_SERIALIZER.deserialize(acceptBareHexColors(text))
        }

        TextType.LEGACY -> LEGACY_SERIALIZER.deserialize(acceptBareHexColors(text))
    }

    return component.decoration(TextDecoration.ITALIC, false)
}

/** The same text with all of its markup removed, for the last-resort path in [componentAsText]. */
private fun stripStyling(text: String): String = when (GlobalConfigManager.textType) {
    TextType.MINIMESSAGE -> try {
        MINI_SERIALIZER.stripTags(legacyCodesAsTags(text))
    } catch (e: Exception) {
        LEGACY_CODE.replace(acceptBareHexColors(text), "")
    }

    TextType.LEGACY -> LEGACY_CODE.replace(acceptBareHexColors(text), "")
}

/** One audience per server lifecycle instead of one per message. */
fun serverAudiences(server: MinecraftServer): FabricServerAudiences {
    val cached = cachedAudiences
    if (cached != null && cachedAudiencesServer === server) return cached

    val audiences = FabricServerAudiences.of(server)
    cachedAudiencesServer = server
    cachedAudiences = audiences
    return audiences
}

fun parseMessageWithStyles(text: String, prizeName: String): Component {
    val styledPrizeName = styledComponent(prizeName)

    val parts = text.split(Regex.escape("{prize_name}"))

    val styledParts = mutableListOf<Component>()

    for (i in parts.indices) {
        styledParts.add(styledComponent(parts[i]))
        if (i < parts.size - 1) {
            styledParts.add(styledPrizeName)
        }
    }

    return Component.join(Component.empty(), styledParts).decoration(TextDecoration.ITALIC, false)
}

/** Adventure component -> Text, falling back to the unstyled string if the json will not parse. */
private fun componentAsText(component: Component, fallback: String): Text {
    val json = GsonComponentSerializer.gson().serialize(component)
    return Text.Serialization.fromJson(json, DynamicRegistryManager.EMPTY) ?: Text.literal(stripStyling(fallback))
}

/** Config text as a [Text], with no placeholder substitution of its own. */
fun styledText(text: String): Text = componentAsText(styledComponent(text), text)

class ParseableMessage(
    private val message: String,
    private val player: ServerPlayerEntity? = null,
    private val prizeName: String = "",
) {
    fun sendToAll() {
        val server = player?.server ?: return
        serverAudiences(server).all().sendMessage(parseMessageWithStyles(message, prizeName))
    }

    fun send() {
        val target = player ?: return
        serverAudiences(target.server).player(target.uuid).sendMessage(parseMessageWithStyles(message, prizeName))
    }

    fun returnMessageAsStyledText(): Text =
        componentAsText(parseMessageWithStyles(message, prizeName), message.replace("{prize_name}", prizeName))
}

class ParseableName(
    private val name: String,
) {
    fun returnMessageAsStyledText(): Text = componentAsText(parseMessageWithStyles(name, ""), name)
}
