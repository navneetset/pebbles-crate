package tech.sethi.pebbles.crates.config

import tech.sethi.pebbles.crates.lootcrates.CrateConfig
import tech.sethi.pebbles.crates.lootcrates.CrateConfigManager
import tech.sethi.pebbles.crates.lootcrates.CrateDataManager
import tech.sethi.pebbles.crates.util.WorldBlockPos

/** Where the value a crate actually uses came from. Only the admin screen cares. */
enum class StyleSource { PLACEMENT, CRATE, GLOBAL }

/**
 * Everything a roll needs, with the inheriting already done. Built once per roll and once per crate
 * per particle tick; nothing downstream looks at the override layers again.
 */
data class ResolvedStyle(
    val particleStyle: ParticleStyle,
    /** The particle the idle pattern draws, or null when there is nothing to draw. */
    val particleId: String?,
    val shuffleSound: SoundSettings,
    val rewardSounds: List<SoundSettings>,
    /** The particle thrown when the prize lands, and how many of it. */
    val rewardParticleId: String,
    val rewardParticleCount: Int,
    val steps: Int,
    val ticksPerStep: Long,
    val finalScale: Float
) {
    /** From the first roll step to the display being taken down. */
    val rollTicks: Long
        get() = ticksPerStep * (steps + 1) + GlobalConfigManager.animation.holdTicks
}

/**
 * The one place the placement -> crate type -> config.json order lives.
 *
 * Resolution is per field, not per object: a placement that sets only `finalScale` inherits its
 * sounds from the crate type, and a crate type that sets only a shuffle sound id inherits that
 * sound's volume and pitch from config.json. Ids that the game does not know are skipped rather
 * than used, so a typo costs the level it was written at instead of the effect entirely.
 */
object StyleResolver {
    fun resolve(placement: CrateStyle?, crate: CrateStyle?): ResolvedStyle {
        val animation = GlobalConfigManager.animation
        val particles = GlobalConfigManager.particles
        val style = particleStyle(placement, crate)

        return ResolvedStyle(
            particleStyle = style,
            particleId = particleId(style, placement, crate),
            shuffleSound = sound(placement?.shuffleSound, crate?.shuffleSound, animation.shuffleSound),
            rewardSounds = rewardSounds(placement?.rewardSound, crate?.rewardSound),
            rewardParticleId = knownParticleId(placement?.rewardParticle) ?: knownParticleId(crate?.rewardParticle)
            ?: particles.reward,
            rewardParticleCount = placement?.rewardParticleCount ?: crate?.rewardParticleCount ?: particles.rewardCount,
            steps = placement?.animationSteps ?: crate?.animationSteps ?: animation.steps,
            ticksPerStep = (placement?.ticksPerStep ?: crate?.ticksPerStep)?.toLong() ?: animation.ticksPerStep,
            finalScale = (placement?.finalScale ?: crate?.finalScale)?.toFloat() ?: animation.finalScale
        )
    }

    /** The style for a crate that is standing in the world, both layers read from their managers. */
    fun at(worldPos: WorldBlockPos, crateConfig: CrateConfig?): ResolvedStyle =
        resolve(CrateDataManager.getPlacement(worldPos)?.style, crateConfig?.style)

    fun at(worldPos: WorldBlockPos): ResolvedStyle {
        val placement = CrateDataManager.getPlacement(worldPos)
        val crate = placement?.name?.let { CrateConfigManager.getCrateConfig(it) }
        return resolve(placement?.style, crate?.style)
    }

    fun particleStyle(placement: CrateStyle?, crate: CrateStyle?): ParticleStyle =
        ParticleStyle.of(placement?.particleStyle) ?: ParticleStyle.of(crate?.particleStyle) ?: ParticleStyle.DEFAULT

    /**
     * The particle id for a style, or null when nothing is drawn. A style's own default stands in
     * where no override names one - except for the pattern the mod has always drawn, which keeps
     * following `particles.idle` so servers that tuned that value see no change.
     */
    fun particleId(style: ParticleStyle, placement: CrateStyle?, crate: CrateStyle?): String? {
        if (style == ParticleStyle.NONE) return null

        val fallback = style.defaultParticle ?: GlobalConfigManager.particles.idle
        val candidates = listOfNotNull(placement?.particleType, crate?.particleType)
        return candidates.firstOrNull { GlobalConfigManager.particleEffect(it) != null } ?: fallback
    }

    /**
     * Which layer a field's value came from. Passed the same two nullable fields the resolution
     * order reads, so the screen can never disagree with what actually plays.
     */
    fun sourceOf(placement: Any?, crate: Any?): StyleSource = when {
        placement != null -> StyleSource.PLACEMENT
        crate != null -> StyleSource.CRATE
        else -> StyleSource.GLOBAL
    }

    private fun sound(placement: SoundOverride?, crate: SoundOverride?, global: SoundSettings): SoundSettings =
        SoundSettings(
            id = knownSoundId(placement?.id) ?: knownSoundId(crate?.id) ?: global.id,
            volume = placement?.volume ?: crate?.volume ?: global.volume,
            pitch = placement?.pitch ?: crate?.pitch ?: global.pitch
        )

    /**
     * config.json can name several reward sounds and play them together; an override is a single
     * sound, so one being present replaces the chord with that one sound - merged, field by field,
     * over the first of the configured ones. With no override at all the whole configured list plays.
     */
    private fun rewardSounds(placement: SoundOverride?, crate: SoundOverride?): List<SoundSettings> {
        val configured = GlobalConfigManager.animation.rewardSounds
        if (placement.isNullOrEmpty() && crate.isNullOrEmpty()) return configured

        return listOf(sound(placement, crate, configured.firstOrNull() ?: SoundSettings()))
    }

    private fun knownSoundId(id: String?): String? = id?.takeIf { GlobalConfigManager.soundEvent(it) != null }

    private fun knownParticleId(id: String?): String? = id?.takeIf { GlobalConfigManager.particleEffect(it) != null }
}
