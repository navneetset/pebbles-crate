package tech.sethi.pebbles.crates.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.util.styledText
import java.io.File

/**
 * Every line the mod says to a player, in `config/pebbles-crate/messages.json`.
 *
 * Values are parsed with the same serializer as crate configs, so admins colour them the way they
 * colour everything else. A key the file does not have falls back to the default below rather than
 * printing the key or failing - which is what makes it safe to hand-edit and to carry across
 * versions that added lines. Setting a value to `""` silences that message entirely.
 */
object Messages {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val file = File("config/pebbles-crate/messages.json")

    /** Values are either a string or, where the mod writes several lines, a list of them. */
    private val defaults: Map<String, Any> = linkedMapOf(
        // Opening and placing crates
        "crate.cooldown" to "&cYou can open another crate in {seconds} seconds.",
        "crate.in-use" to "&cSomeone is already using this crate!",
        "crate.no-prizes" to "&cThis crate has no prizes configured, ask an admin to fix it.",
        "crate.command-failed" to "&cError executing command: {command}",
        "crate.assigned" to "&7Assigned a {crate_name} crate to the block at {position} in {world}",
        "crate.removed" to "&7Crate data removed for position: {position} in {world}",
        "crate.no-permission-place" to "&cYou don't have permission to turn blocks into crates.",
        "crate.no-permission-break" to "&cThis block is a crate, only an admin can remove it.",
        "crate.convert-hint" to "&eThis crate uses virtual keys now - ask an admin to run /padmin convertkeys {player_name} to turn the key you are holding into one.",
        "crate.transformer.given" to "&7Successfully gave {crate_name} to {player_name}",
        "crate.transformer.lore" to listOf("&6Right click any block to", "&6transform it into a {crate_name}"),

        // Keys
        "key.need-physical" to "&cYou need a {key_name} to open this crate.",
        "key.none-virtual" to "&cYou have no {crate_name} keys. Check /keys to see what you have.",
        "key.loading" to "&cYour keys are still loading, try again in a moment.",
        "key.received-physical" to "&7You received {amount} {key_name} for {crate_name}!",
        "key.received-virtual" to "&aYou received {amount} {crate_name} key(s). Use /keys to see them.",

        // /padmin
        "command.reloaded" to "&7Reloaded crate configs",
        "command.player-only" to "&c{command} has to be run by a player.",
        "command.rate-limited" to "&cWait {seconds} more second(s) before using that again.",
        "command.crate-unknown" to "&cThere is no crate named '{crate_name}'.",
        "command.virtual-disabled" to "&cVirtual keys are not enabled on this server.",
        "command.givekey.physical" to "&7{player_name} received {amount} {crate_name} keys!",
        "command.givekey.virtual" to "&7{player_name} received {amount} virtual {crate_name} keys!",
        "command.givekey.failed" to "&cCould not give keys to {player_name}.",
        "command.givekey.no-item" to "&cThe key item configured for '{crate_name}' does not exist, no key was given.",
        "command.keys-loading" to "&c{player_name}'s keys have not finished loading, try again in a moment.",
        "command.convertkeys.none-configured" to "&cNo crate is configured to use virtual keys.",
        "command.convertkeys.nothing" to "&7{player_name} had no convertible crate keys.",
        "command.convertkeys.report" to "&7Converted keys for {player_name}: {summary}",
        "command.convertkeys.player" to "&aConverted your crate keys to virtual keys: {summary}",

        // /keys
        "keys.self-send" to "&cYou cannot send keys to yourself.",
        "keys.loading" to "&cKeys are still loading, try again in a moment.",
        "keys.not-enough" to "&cYou only have {amount} {crate_name} key(s).",
        "keys.not-enough-now" to "&cYou do not have {amount} {crate_name} key(s) any more.",
        "keys.send-failed" to "&cCould not send those keys, try again.",
        "keys.sent" to "&aSent {amount} {crate_name} key(s) to {player_name}.",
        "keys.received" to "&a{player_name} sent you {amount} {crate_name} key(s).",
        "keys.target-offline" to "&c{player_name} is no longer online.",

        // Shared GUI furniture
        "gui.page" to "&fPage {page} of {pages}",
        "gui.previous" to "&6Previous",
        "gui.next" to "&6Next",
        "gui.back" to "&6Back",

        // Crate preview
        "gui.prize.chance" to "&fChance: {chance}%",
        "gui.open.name" to "&aOpen Crate",
        "gui.open.balance" to "&7Your keys: &6{amount}",
        "gui.open.ready" to "&eClick to open this crate.",
        "gui.open.no-key" to "&cYou need a key for this crate.",

        // Admin screens
        "gui.crates.title" to "Crate Management",
        "gui.crates.config-title" to "{crate_name} Configuration",
        "gui.crates.missing" to "&cCrate '{crate_name}' no longer exists, reopen the crate list.",
        "gui.crates.get-crate" to "&6Get Crate",
        "gui.crates.get-key" to "&6Get Key",
        "gui.crates.web-editor" to "&6Configure Prize (Web Editor)",
        "gui.crates.web-editor-hint" to "&6To edit the config on the web UI, navigate to: ",
        "gui.crates.back" to "&cBack",
        "gui.activecrates.title" to "Placed Crates",
        "gui.activecrates.entry" to "&f[{world}] {x}, {y}, {z} - {crate_name}",
        "gui.activecrates.entry-lore" to listOf("&7Left click to open its settings."),

        // One placed crate's settings
        "gui.placement.title" to "Crate Settings",
        "gui.placement.info" to "&6{crate_name}",
        "gui.placement.info-lore" to listOf(
            "&7World: &f{world}",
            "&7Position: &f{x}, {y}, {z}",
            "&7Keys: &f{key_mode}",
            "&7Config file: &f{file}"
        ),
        "gui.placement.keys.virtual" to "virtual",
        "gui.placement.keys.physical" to "physical",
        "gui.placement.keys.inherit" to "inherit ({effective})",
        "gui.placement.missing" to "&cThis block is no longer a crate.",
        "gui.placement.crate-missing" to "&cCrate '{crate_name}' has no config any more, only the particle switch works.",
        "gui.placement.teleport" to "&bTeleport here",
        "gui.placement.teleport-lore" to listOf("&7Left click to stand at this crate."),
        "gui.placement.teleport-failed" to "&cThat crate's world is not loaded.",
        "gui.placement.teleported" to "&7Teleported to the {crate_name} crate in {world}.",
        "gui.placement.particles-on" to "&aParticles here: on",
        "gui.placement.particles-off" to "&cParticles here: off",
        "gui.placement.particles-lore" to listOf(
            "&7Left click to turn the idle particles", "&7at this one crate on or off."
        ),
        "gui.placement.scope.placement" to "&aEditing: this placement",
        "gui.placement.scope.crate" to "&6Editing: crate type",
        "gui.placement.scope-lore" to listOf(
            "&7Left click to switch between this one",
            "&7crate block and every {crate_name} crate.",
            "&7Everything below is written to whichever",
            "&7of the two is shown here."
        ),
        "gui.placement.style" to "&dParticle style: &f{value}",
        "gui.placement.sound.shuffle" to "&eShuffle sound",
        "gui.placement.sound.reward" to "&eReward sound",
        "gui.placement.sound-value" to "&7Sound: &f{value}",
        "gui.placement.sound-lore" to listOf(
            "&7Left click to hear it.",
            "&7Shift + left click: next sound",
            "&7Shift + right click: previous sound"
        ),
        "gui.placement.sound-unknown" to "&cnot a known sound",
        "gui.placement.volume" to "&eVolume: &f{value}",
        "gui.placement.pitch" to "&ePitch: &f{value}",
        "gui.placement.steps" to "&eRoll steps: &f{value}",
        "gui.placement.ticks" to "&eTicks per step: &f{value}",
        "gui.placement.scale" to "&ePrize scale: &f{value}",
        "gui.placement.cycle-lore" to listOf("&7Left click: next", "&7Right click: previous"),
        "gui.placement.adjust-lore" to listOf(
            "&7Left click: +{step}", "&7Right click: -{step}", "&7Shift + left click: inherit"
        ),
        "gui.placement.set-here" to "&7Set: &f{value}",
        "gui.placement.inherited" to "&7Set: &8inherit",
        "gui.placement.source.placement" to "&7Using: &athis placement",
        "gui.placement.source.crate" to "&7Using: &6the crate type",
        "gui.placement.source.global" to "&7Using: &fconfig.json",
        "gui.placement.inherit" to "inherit",
        "gui.placement.save-failed" to "&cCould not save that change, see the server log.",

        // Key wallet screens
        "gui.keys.own-title" to "Your Crate Keys",
        "gui.keys.other-title" to "{player_name}'s Crate Keys",
        "gui.keys.send-title" to "Send Crate Keys",
        "gui.keys.icon.crate" to "&7Crate: &f{crate_name}",
        "gui.keys.icon.amount" to "&7Keys: &6{amount}",
        "gui.keys.icon.unknown-crate" to "&cThis crate no longer exists.",
        "gui.keys.empty" to "&cNo keys",
        "gui.keys.empty-lore" to "&7You do not have any crate keys yet.",
        "gui.keys.send.empty" to "&cNo keys to send",
        "gui.keys.send.empty-lore" to "&7You do not have any crate keys.",
        "gui.keys.send.pick-key" to "&eClick to send these",
        "gui.keys.send.no-targets" to "&cNobody to send to",
        "gui.keys.send.no-targets-lore" to "&7No other players are online.",
        "gui.keys.send.pick-target" to "&eClick to send to them",
        "gui.keys.send.fewer" to "&cSend fewer",
        "gui.keys.send.more" to "&aSend more",
        "gui.keys.send.steps" to listOf("&7Left click: 1", "&7Right click: {right}", "&7Shift click: {shift}"),
        "gui.keys.send.sending" to "&7Sending: &6{amount}",
        "gui.keys.send.held" to "&7You have: &6{held}",
        "gui.keys.send.recipient" to "&7Recipient",
        "gui.keys.send.offline" to "&c{player_name} is offline",
        "gui.keys.send.offline-lore" to "&7Go back and pick someone else.",
        "gui.keys.send.confirm" to "&aConfirm",
        "gui.keys.send.confirm-lore" to "&7Send {amount} {crate_name} key(s) to {player_name}",
        "gui.keys.send.cancel" to "&cCancel"
    )

    @Volatile
    private var overrides: Map<String, Any> = emptyMap()

    fun reload() {
        overrides = read()
        writeIfChanged()
    }

    /** The configured line with its placeholders filled in. Blank means "say nothing". */
    fun raw(key: String, vararg placeholders: Pair<String, String>): String =
        fill(value(key) as? String ?: defaults[key] as? String ?: "", placeholders)

    /** The multi-line form. A key configured as a single string still reads back as one line. */
    fun rawList(key: String, vararg placeholders: Pair<String, String>): List<String> {
        val configured = value(key) ?: defaults[key]
        val lines = when (configured) {
            is List<*> -> configured.filterIsInstance<String>()
            is String -> listOf(configured)
            else -> emptyList()
        }
        return lines.map { fill(it, placeholders) }
    }

    fun text(key: String, vararg placeholders: Pair<String, String>): Text = styledText(raw(key, *placeholders))

    fun list(key: String, vararg placeholders: Pair<String, String>): List<Text> =
        rawList(key, *placeholders).map { styledText(it) }

    fun send(player: PlayerEntity, key: String, vararg placeholders: Pair<String, String>) {
        val message = raw(key, *placeholders)
        if (message.isBlank()) return
        player.sendMessage(styledText(message), false)
    }

    /** The red command-failure channel, which is also what console sees. */
    fun sendError(source: ServerCommandSource, key: String, vararg placeholders: Pair<String, String>) {
        val message = raw(key, *placeholders)
        if (message.isBlank()) return
        source.sendError(styledText(message))
    }

    /** Command feedback, which reaches the player who ran it or the console that did. */
    fun feedback(source: ServerCommandSource, key: String, vararg placeholders: Pair<String, String>) {
        val message = raw(key, *placeholders)
        if (message.isBlank()) return

        source.sendFeedback({ styledText(message) }, false)
    }

    private fun value(key: String): Any? = overrides[key]

    private fun fill(text: String, placeholders: Array<out Pair<String, String>>): String {
        var filled = text
        for ((name, replacement) in placeholders) {
            filled = filled.replace("{$name}", replacement)
        }
        return filled
    }

    private fun read(): Map<String, Any> {
        if (!file.exists()) return emptyMap()

        val root = try {
            JsonParser.parseString(file.readText()) as? JsonObject
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] messages.json is not valid JSON, using the built-in messages: ${e.message}")
            return emptyMap()
        } ?: return emptyMap()

        val parsed = LinkedHashMap<String, Any>()
        for ((key, element) in root.entrySet()) {
            when {
                element.isJsonPrimitive -> parsed[key] = element.asString
                element.isJsonArray -> parsed[key] = element.asJsonArray.mapNotNull {
                    if (it.isJsonPrimitive) it.asString else null
                }

                else -> logger.warn("[Pebbles-Crates] messages.json: '$key' is neither text nor a list of text, ignoring it")
            }
        }

        val unknown = parsed.keys - defaults.keys
        if (unknown.isNotEmpty()) {
            logger.warn("[Pebbles-Crates] messages.json: unused key(s) ${unknown.joinToString(", ")}")
        }

        return parsed
    }

    /** Writes the file the first time, and tops up a file written before a message was added. */
    private fun writeIfChanged() {
        val complete = LinkedHashMap<String, Any>()
        for ((key, fallback) in defaults) {
            complete[key] = overrides[key] ?: fallback
        }

        val json = gson.toJson(complete)
        try {
            if (file.exists() && file.readText().trim() == json.trim()) return
            file.parentFile?.mkdirs()
            file.writeText(json)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Could not write messages.json: ${e.message}")
        }
    }
}
