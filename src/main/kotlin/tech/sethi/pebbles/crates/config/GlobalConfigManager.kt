package tech.sethi.pebbles.crates.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.particle.ParticleEffect
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.crates.PebblesCrate
import java.io.File

/**
 * The mod-wide `config/pebbles-crate/config.json`, separate from the per-crate files.
 *
 * Everything in here has a default that reproduces the behaviour the mod shipped with, so a server
 * that never opens this file keeps behaving exactly as it did. The file is written with defaults the
 * first time it is asked for, which happens during command registration - before the server itself
 * exists - so [load] may not touch registries. Anything that has to resolve an id waits for
 * [validateRegistryEntries], which runs from `/padmin reload` and at server start.
 *
 * A file written by an older version simply lacks the newer sections; those come back as defaults
 * and the file is rewritten complete on the next load.
 */
object GlobalConfigManager {
    private val logger = LoggerFactory.getLogger("pebbles-crates")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val configFile = File("config/pebbles-crate/config.json")

    @Volatile
    private var cached: GlobalConfig? = null

    val config: GlobalConfig
        get() = cached ?: load()

    val virtualKeys: VirtualKeysConfig
        get() = config.virtualKeys

    val crate: CrateSettings
        get() = config.crate

    val animation: AnimationSettings
        get() = config.animation

    val particles: ParticleSettings
        get() = config.particles

    val gui: GuiSettings
        get() = config.gui

    /** Which serializer parses config text. Unknown values fall back to legacy rather than failing. */
    val textType: TextType
        get() = TextType.of(config.textType)

    /** Compared against `squaredDistanceTo`, so the configured radius is squared once here. */
    val particleRadiusSquared: Double
        get() = config.particles.radiusBlocks.let { it * it }

    /**
     * How long a crate may stay marked in-use before the stale-state sweep frees it: the whole roll
     * it was actually given plus a wide margin, so neither retuning the animation nor a per-crate
     * override can leave the sweep firing mid-roll.
     */
    fun maxAnimationMillis(style: ResolvedStyle): Long = style.rollTicks * 50 + 10_000L

    @Synchronized
    fun load(): GlobalConfig {
        cached?.let { return it }

        val loaded = if (configFile.exists()) {
            try {
                sanitize(gson.fromJson(configFile.readText(), RawGlobalConfig::class.java))
            } catch (e: Exception) {
                logger.warn("[Pebbles-Crates] config.json is not valid JSON, using defaults: ${e.message}")
                GlobalConfig()
            }
        } else {
            GlobalConfig()
        }

        // Also covers a file from an older version: the sections it never heard of are written in.
        writeIfChanged(loaded)

        cached = loaded
        return loaded
    }

    /**
     * Re-reads the file. Whether the feature is on and which store backs it are both decided once at
     * server start (commands cannot be unregistered, and swapping a live store would strand queued
     * writes), so a reload that changes those two says so instead of pretending to apply them.
     * Everything else takes effect immediately.
     */
    fun reload() {
        val previous = cached
        cached = null
        val current = load()

        if (previous != null && (previous.virtualKeys.enabled != current.virtualKeys.enabled || !previous.virtualKeys.storage.equals(
                current.virtualKeys.storage, ignoreCase = true
            ))
        ) {
            logger.warn("[Pebbles-Crates] virtualKeys enabled/storage changed in config.json; restart the server to apply it")
        }

        validateRegistryEntries()
    }

    /**
     * Swaps out any sound or particle the game does not know for the default it replaced, so a typo
     * costs the effect it names instead of throwing out of the roll animation. Only meaningful once
     * the registries are populated, i.e. from server start onwards.
     */
    fun validateRegistryEntries() {
        if (PebblesCrate.server == null) return
        val current = cached ?: return
        val defaults = GlobalConfig()

        val animation = current.animation
        val particles = current.particles

        val checked = current.copy(
            crate = current.crate.copy(
                defaultKeyMaterial = checkedItem(current.crate.defaultKeyMaterial, defaults.crate.defaultKeyMaterial)
            ), animation = animation.copy(
                shuffleSound = checkedSound(animation.shuffleSound, defaults.animation.shuffleSound, "shuffleSound"),
                rewardSounds = animation.rewardSounds.filter { sound ->
                    val known = soundEvent(sound.id) != null
                    if (!known) warnUnknown("sound", sound.id, "animation.rewardSounds")
                    known
                }.ifEmpty { defaults.animation.rewardSounds }), particles = particles.copy(
                idle = checkedParticle(particles.idle, defaults.particles.idle, "particles.idle"),
                reward = checkedParticle(particles.reward, defaults.particles.reward, "particles.reward")
            )
        )

        cached = checked
    }

    /** The sound a config names, or null when nothing is registered under that id. */
    fun soundEvent(id: String): SoundEvent? = Identifier.tryParse(id)?.let { Registries.SOUND_EVENT.get(it) }

    /**
     * The particle a config names. Only particles that carry no extra data (the vast majority) can
     * be named by id alone, so anything else is treated as unknown.
     */
    fun particleEffect(id: String): ParticleEffect? =
        Identifier.tryParse(id)?.let { Registries.PARTICLE_TYPE.get(it) } as? ParticleEffect

    private fun checkedSound(sound: SoundSettings, fallback: SoundSettings, field: String): SoundSettings {
        if (soundEvent(sound.id) != null) return sound
        warnUnknown("sound", sound.id, field)
        return fallback
    }

    private fun checkedParticle(id: String, fallback: String, field: String): String {
        if (particleEffect(id) != null) return id
        warnUnknown("particle", id, field)
        return fallback
    }

    private fun checkedItem(material: String, fallback: String): String {
        val identifier = Identifier.tryParse(material)
        if (identifier != null && Registries.ITEM.containsId(identifier)) return material
        warnUnknown("item", material, "crate.defaultKeyMaterial")
        return fallback
    }

    private fun warnUnknown(kind: String, id: String, field: String) {
        logger.warn("[Pebbles-Crates] config.json: '$id' is not a known $kind, falling back to the default for $field")
    }

    /** Leaves a hand-edited file alone unless it is actually missing something. */
    private fun writeIfChanged(config: GlobalConfig) {
        val json = gson.toJson(config)

        try {
            if (configFile.exists() && configFile.readText().trim() == json.trim()) return
            configFile.parentFile?.mkdirs()
            configFile.writeText(json)
        } catch (e: Exception) {
            logger.warn("[Pebbles-Crates] Could not write config.json: ${e.message}")
        }
    }

    /**
     * Turns what the file actually contains into a complete config. Every field is optional, and an
     * absent one is not the same as one an operator wrote: absent takes the default, present is
     * clamped into a range the mod can work with. Reading them as plain numbers would make a section
     * a file predates arrive as zeroes and get clamped, which is how a partially written config used
     * to end up with a one-second animation lifetime or no particles at all.
     */
    private fun sanitize(raw: RawGlobalConfig?): GlobalConfig {
        if (raw == null) return GlobalConfig()
        val defaults = GlobalConfig()

        val crate = raw.crate
        val animation = raw.animation
        val particles = raw.particles
        val virtualKeys = raw.virtualKeys
        val mongo = virtualKeys?.mongo
        val mongoDefaults = MongoConfig()

        return GlobalConfig(
            textType = raw.textType ?: defaults.textType,
            crate = CrateSettings(
                // A zero cooldown is a legitimate choice; a negative one is not.
                cooldownMillis = crate?.cooldownMillis?.coerceAtLeast(0L) ?: defaults.crate.cooldownMillis,
                defaultKeyMaterial = crate?.defaultKeyMaterial ?: defaults.crate.defaultKeyMaterial
            ),
            animation = AnimationSettings(
                steps = animation?.steps?.coerceIn(0, 200) ?: defaults.animation.steps,
                ticksPerStep = animation?.ticksPerStep?.coerceIn(1L, 200L) ?: defaults.animation.ticksPerStep,
                holdTicks = animation?.holdTicks?.coerceIn(0L, 1200L) ?: defaults.animation.holdTicks,
                finalScale = animation?.finalScale?.coerceIn(0.1f, 10f) ?: defaults.animation.finalScale,
                maxLifetimeTicks = animation?.maxLifetimeTicks?.coerceIn(20, 12000)
                    ?: defaults.animation.maxLifetimeTicks,
                shuffleSound = sanitizeSound(animation?.shuffleSound, defaults.animation.shuffleSound),
                rewardSounds = animation?.rewardSounds?.filterNotNull()?.map { sanitizeSound(it, SoundSettings()) }
                    ?: defaults.animation.rewardSounds,
                rewardSoundRepeats = animation?.rewardSoundRepeats?.coerceIn(0, 20)
                    ?: defaults.animation.rewardSoundRepeats
            ),
            particles = ParticleSettings(
                radiusBlocks = particles?.radiusBlocks?.coerceIn(0.0, 256.0) ?: defaults.particles.radiusBlocks,
                idle = particles?.idle ?: defaults.particles.idle,
                reward = particles?.reward ?: defaults.particles.reward,
                rewardCount = particles?.rewardCount?.coerceIn(0, 500) ?: defaults.particles.rewardCount
            ),
            gui = GuiSettings(
                openButtonSlot = raw.gui?.openButtonSlot?.let(::sanitizeOpenButtonSlot) ?: defaults.gui.openButtonSlot
            ),
            virtualKeys = VirtualKeysConfig(
                enabled = virtualKeys?.enabled ?: defaults.virtualKeys.enabled,
                storage = virtualKeys?.storage ?: defaults.virtualKeys.storage,
                localWriteDelayMillis = virtualKeys?.localWriteDelayMillis?.coerceIn(0L, 60_000L)
                    ?: defaults.virtualKeys.localWriteDelayMillis,
                mongo = MongoConfig(
                    uri = mongo?.uri ?: mongoDefaults.uri,
                    database = mongo?.database ?: mongoDefaults.database,
                    collection = mongo?.collection ?: mongoDefaults.collection,
                    // A zero written by hand would mean "never time out", which is not a choice worth honouring.
                    serverSelectionTimeoutSeconds = mongo?.serverSelectionTimeoutSeconds?.takeIf { it > 0 }
                        ?.coerceAtMost(60) ?: mongoDefaults.serverSelectionTimeoutSeconds
                )
            )
        )
    }

    /**
     * Row six of a 6x9 screen, minus the three slots the preview screen keeps for its page arrows
     * and page counter - a button under one of those could never be clicked. Anything else is pulled
     * to the nearest usable slot.
     */
    private fun sanitizeOpenButtonSlot(slot: Int): Int {
        val usable = slot.coerceIn(46, 51)
        if (usable != slot) {
            logger.warn(
                "[Pebbles-Crates] config.json: gui.openButtonSlot $slot is not usable " + "(the bottom row's slots 45, 52 and 53 carry the page controls), using $usable"
            )
        }
        return usable
    }

    private fun sanitizeSound(raw: RawSoundSettings?, fallback: SoundSettings): SoundSettings {
        if (raw == null) return fallback
        return SoundSettings(
            id = raw.id ?: fallback.id,
            volume = raw.volume?.coerceIn(0f, 10f) ?: fallback.volume,
            pitch = raw.pitch?.coerceIn(0.5f, 2f) ?: fallback.pitch
        )
    }
}

/**
 * What Gson is actually handed the file as. Boxed and nullable throughout so a key the file simply
 * does not have arrives as null rather than as a zero indistinguishable from a written one; [sanitize]
 * is the only thing that reads these.
 */
private class RawGlobalConfig {
    val textType: String? = null
    val crate: RawCrateSettings? = null
    val animation: RawAnimationSettings? = null
    val particles: RawParticleSettings? = null
    val gui: RawGuiSettings? = null
    val virtualKeys: RawVirtualKeysConfig? = null
}

private class RawCrateSettings {
    val cooldownMillis: Long? = null
    val defaultKeyMaterial: String? = null
}

private class RawSoundSettings {
    val id: String? = null
    val volume: Float? = null
    val pitch: Float? = null
}

private class RawAnimationSettings {
    val steps: Int? = null
    val ticksPerStep: Long? = null
    val holdTicks: Long? = null
    val finalScale: Float? = null
    val maxLifetimeTicks: Int? = null
    val shuffleSound: RawSoundSettings? = null
    val rewardSounds: List<RawSoundSettings?>? = null
    val rewardSoundRepeats: Int? = null
}

private class RawParticleSettings {
    val radiusBlocks: Double? = null
    val idle: String? = null
    val reward: String? = null
    val rewardCount: Int? = null
}

private class RawGuiSettings {
    val openButtonSlot: Int? = null
}

private class RawVirtualKeysConfig {
    val enabled: Boolean? = null
    val storage: String? = null
    val localWriteDelayMillis: Long? = null
    val mongo: RawMongoConfig? = null
}

private class RawMongoConfig {
    val uri: String? = null
    val database: String? = null
    val collection: String? = null
    val serverSelectionTimeoutSeconds: Int? = null
}

data class GlobalConfig(
    /** "LEGACY" for `&` colour codes, "MINIMESSAGE" for `<green>`-style tags. */
    val textType: String = "LEGACY",
    val crate: CrateSettings = CrateSettings(),
    val animation: AnimationSettings = AnimationSettings(),
    val particles: ParticleSettings = ParticleSettings(),
    val gui: GuiSettings = GuiSettings(),
    val virtualKeys: VirtualKeysConfig = VirtualKeysConfig()
)

/** How config text is written. Unknown values in the file read back as [LEGACY]. */
enum class TextType {
    LEGACY, MINIMESSAGE;

    companion object {
        fun of(raw: String?): TextType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: LEGACY
    }
}

data class CrateSettings(
    /** How long a player has to wait between opening any two crates. */
    val cooldownMillis: Long = 8000L,
    /** Stands in wherever a crate's own `crateKey.material` does not resolve to an item. */
    val defaultKeyMaterial: String = "minecraft:tripwire_hook"
)

data class SoundSettings(
    val id: String = "minecraft:block.note_block.banjo",
    val volume: Float = 0.5f,
    val pitch: Float = 1.0f
)

data class AnimationSettings(
    /** How many prizes flick past before the real one lands. */
    val steps: Int = 10,
    val ticksPerStep: Long = 6L,
    /** How long the won prize stays up afterwards. */
    val holdTicks: Long = 100L,
    /** The won prize is shown slightly larger than the ones rolled past. */
    val finalScale: Float = 1.25f,
    /** Backstop: a display entity discards itself after this even if its cleanup task never ran. */
    val maxLifetimeTicks: Int = 600,
    val shuffleSound: SoundSettings = SoundSettings("minecraft:block.note_block.banjo", 0.5f, 1.0f),
    /** Played together when the prize is awarded; an empty list means silence. */
    val rewardSounds: List<SoundSettings> = listOf(
        SoundSettings("minecraft:entity.allay.death", 0.5f, 0.5f),
        SoundSettings("minecraft:block.note_block.bell", 0.5f, 1.0f)
    ),
    val rewardSoundRepeats: Int = 5
)

data class ParticleSettings(
    /** How close a player has to be to a crate to be sent its idle particles. */
    val radiusBlocks: Double = 16.0,
    val idle: String = "minecraft:firework",
    val reward: String = "minecraft:sculk_soul",
    val rewardCount: Int = 50
)

data class GuiSettings(
    /** Where the "Open Crate" button sits on a virtual crate's preview screen (bottom row, 46-51). */
    val openButtonSlot: Int = 49
)

data class VirtualKeysConfig(
    /** Master switch. While false there are no /keys commands and no key store is ever created. */
    val enabled: Boolean = false,
    /** "local" for the json file next to the crate configs, "mongodb" for a shared wallet. */
    val storage: String = "local",
    /** How long local storage waits before writing, so a burst of grants costs one write. */
    val localWriteDelayMillis: Long = 2000L,
    val mongo: MongoConfig = MongoConfig()
)

data class MongoConfig(
    val uri: String = "mongodb://localhost:27017",
    val database: String = "pebbles-crates",
    val collection: String = "PlayerKeys",
    /** Kept short so an unreachable database degrades to local storage during startup, not minutes later. */
    val serverSelectionTimeoutSeconds: Int = 5
)
