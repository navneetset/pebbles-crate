package tech.sethi.pebbles.crates.config

import org.slf4j.LoggerFactory

/**
 * What one crate looks and sounds like, as a set of overrides.
 *
 * Every field is nullable and null means "inherit": from the placement to the crate type to
 * `config.json`. That is what keeps a crate file written before this existed valid as-is, and it is
 * also why the fields are read one at a time - a placement that only raises the pitch of the reward
 * sound still inherits its id and volume.
 */
data class CrateStyle(
    /** One of [ParticleStyle]'s ids. Anything else is dropped with a warning at load. */
    val particleStyle: String? = null,
    /** Which particle the style draws, when the style's own default is not wanted. */
    val particleType: String? = null,
    val shuffleSound: SoundOverride? = null,
    val rewardSound: SoundOverride? = null,
    /** The particle thrown when a prize is won, in place of `particles.reward`. */
    val rewardParticle: String? = null,
    /** How many of them, in place of `particles.rewardCount`. */
    val rewardParticleCount: Int? = null,
    val animationSteps: Int? = null,
    val ticksPerStep: Int? = null,
    val finalScale: Double? = null
) {
    /** An override that overrides nothing is written back as no override at all. */
    val isEmpty: Boolean
        get() = particleStyle == null && particleType == null && shuffleSound.isNullOrEmpty() && rewardSound.isNullOrEmpty() && rewardParticle == null && rewardParticleCount == null && animationSteps == null && ticksPerStep == null && finalScale == null

    /** Null rather than an empty object, so nothing hollow reaches disk. */
    fun orNull(): CrateStyle? = if (isEmpty) null else this
}

data class SoundOverride(
    val id: String? = null, val volume: Float? = null, val pitch: Float? = null
) {
    val isEmpty: Boolean
        get() = id == null && volume == null && pitch == null

    fun orNull(): SoundOverride? = if (isEmpty) null else this
}

fun SoundOverride?.isNullOrEmpty(): Boolean = this == null || isEmpty

/**
 * The idle particle patterns a crate can draw.
 *
 * [periodTicks] is how often the pattern emits: the two spirals move every tick, the scattered ones
 * would be a smoke screen at that rate. [defaultParticle] null means "whatever `particles.idle` in
 * config.json says", which is what keeps [CROSS_SPIRAL] - the pattern the mod has always drawn -
 * behaving exactly as it did for servers that tuned that value.
 *
 * The order is the order the admin screen cycles through, so new patterns go after the ones that
 * were already there and [NONE] stays at the end next to "inherit".
 */
enum class ParticleStyle(
    val id: String, val defaultParticle: String?, val periodTicks: Int
) {
    CROSS_SPIRAL("cross-spiral", null, 1),
    SPIRAL("spiral", "minecraft:firework", 1),
    SPARKLE("sparkle", "minecraft:end_rod", 40),
    HEART("heart", "minecraft:heart", 30),
    DOUBLE_HELIX("double-helix", "minecraft:firework", 1),
    ORBIT("orbit", "minecraft:end_rod", 2),
    COLUMN("column", "minecraft:soul_fire_flame", 2),
    ENCHANT("enchant", "minecraft:enchant", 6),
    RAIN("rain", "minecraft:falling_water", 3),
    NONE("none", null, 1);

    companion object {
        val DEFAULT = CROSS_SPIRAL

        fun of(id: String?): ParticleStyle? {
            val trimmed = id?.trim() ?: return null
            return entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }
        }

        val ids: List<String> get() = entries.map { it.id }
    }
}

/**
 * Load-time repair for styles, applied to both the crate files and crate_data.json.
 *
 * Numbers are pulled into the same ranges [GlobalConfigManager] uses, and a particle style nobody
 * has heard of is dropped so the crate inherits instead of drawing nothing. Sound and particle *ids*
 * are not checked here - the registries are empty when the configs are first read - they are checked
 * by the validation pass and fall through to the next level at resolution time.
 */
object CrateStyles {
    private val logger = LoggerFactory.getLogger("pebbles-crates")

    const val MIN_STEPS = 0
    const val MAX_STEPS = 200
    const val MIN_TICKS_PER_STEP = 1
    const val MAX_TICKS_PER_STEP = 200
    const val MIN_SCALE = 0.1
    const val MAX_SCALE = 10.0
    const val MIN_REWARD_COUNT = 0
    const val MAX_REWARD_COUNT = 500
    const val MIN_VOLUME = 0.0f
    const val MAX_VOLUME = 10.0f
    const val MIN_PITCH = 0.5f
    const val MAX_PITCH = 2.0f

    @Suppress("SENSELESS_COMPARISON") // gson bypasses constructors, so even these can arrive null
    fun sanitize(style: CrateStyle?, where: String): CrateStyle? {
        if (style == null) return null

        val particleStyle = style.particleStyle?.takeIf { name ->
            val known = ParticleStyle.of(name) != null
            if (!known) {
                logger.warn(
                    "[Pebbles-Crates] $where: '$name' is not a particle style, inheriting instead; " + "known styles are ${
                        ParticleStyle.ids.joinToString(", ")
                    }"
                )
            }
            known
        }

        return CrateStyle(
            particleStyle = particleStyle,
            particleType = style.particleType,
            shuffleSound = sanitizeSound(style.shuffleSound),
            rewardSound = sanitizeSound(style.rewardSound),
            rewardParticle = style.rewardParticle?.takeIf { it.isNotBlank() },
            rewardParticleCount = style.rewardParticleCount?.coerceIn(MIN_REWARD_COUNT, MAX_REWARD_COUNT),
            animationSteps = style.animationSteps?.coerceIn(MIN_STEPS, MAX_STEPS),
            ticksPerStep = style.ticksPerStep?.coerceIn(MIN_TICKS_PER_STEP, MAX_TICKS_PER_STEP),
            finalScale = style.finalScale?.coerceIn(MIN_SCALE, MAX_SCALE)
        ).orNull()
    }

    private fun sanitizeSound(sound: SoundOverride?): SoundOverride? {
        if (sound == null) return null
        return SoundOverride(
            id = sound.id?.takeIf { it.isNotBlank() },
            volume = sound.volume?.coerceIn(MIN_VOLUME, MAX_VOLUME),
            pitch = sound.pitch?.coerceIn(MIN_PITCH, MAX_PITCH)
        ).orNull()
    }

    /** Logs the ids a style names that the game does not know. Needs populated registries. */
    fun warnUnknownIds(style: CrateStyle?, where: String) {
        if (style == null) return

        warnUnknownParticle(style.particleType, where)
        warnUnknownParticle(style.rewardParticle, "$where rewardParticle")
        warnUnknownSound(style.shuffleSound, "$where shuffleSound")
        warnUnknownSound(style.rewardSound, "$where rewardSound")
    }

    private fun warnUnknownParticle(id: String?, where: String) {
        if (id == null || GlobalConfigManager.particleEffect(id) != null) return
        logger.warn("[Pebbles-Crates] $where: particle '$id' is not known, that crate falls back to the inherited one")
    }

    private fun warnUnknownSound(sound: SoundOverride?, where: String) {
        val id = sound?.id ?: return
        if (GlobalConfigManager.soundEvent(id) == null) {
            logger.warn("[Pebbles-Crates] $where: sound '$id' is not known, that crate falls back to the inherited one")
        }
    }
}
