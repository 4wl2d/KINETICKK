// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.model.formatDamageNumber
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage

data class ParticleProjection(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val colorIndex: Int,
    val size: Float,
)

data class MotionEchoProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val intensity: Float,
)

data class ShockwaveProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val maxRadius: Float,
    val colorIndex: Int,
)

/**
 * A damage number anchored where the hit landed. [driftX]/[driftY] is the unit direction away
 * from the Core at the hit; the renderer eases the drift along it from the remaining [life].
 */
data class DamageNumberProjection(
    val x: Float,
    val y: Float,
    val amount: Long,
    val critical: Boolean,
    val life: Float,
    val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
    val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
    val russianCompactAmount: String = compactAmount.russianDamageNumber(),
    val driftX: Float = 0f,
    val driftY: Float = -1f,
    /** 0 for Kinetic damage, otherwise 1 + the status element ordinal (the hue of the number). */
    val tone: Int = 0,
) {
    fun formattedAmount(format: DamageNumberFormat, language: AppLanguage = AppLanguage.English): String = when (format) {
        DamageNumberFormat.COMPACT -> when (language) {
            AppLanguage.English -> compactAmount
            AppLanguage.Russian -> russianCompactAmount
        }
        DamageNumberFormat.FULL -> fullAmount
    }
}

/** Translate the already rounded number; general UI formatting truncates and has different bounds. */
internal fun String.russianDamageNumber(): String {
    val suffix = when {
        endsWith("Qa") -> "Qa" to " квадрлн"
        endsWith("Qi") -> "Qi" to " квинтлн"
        endsWith("K") -> "K" to " тыс."
        endsWith("M") -> "M" to " млн"
        endsWith("B") -> "B" to " млрд"
        endsWith("T") -> "T" to " трлн"
        else -> return this
    }
    return removeSuffix(suffix.first).replace('.', ',') + suffix.second
}

data class WeaponArcProjection(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val life: Float,
    /** The link's look (a gameplay `LinkStyleCode`); 0 is the weapon look. */
    val style: Int = 0,
    /** The life it started with (a styled link lives at least its look's life, [LinkLooks]). */
    val maxLife: Float = life,
)

/**
 * How a build toast ranks in the feed. Discoveries (reactions, pairings) and warnings (a relic
 * suppressing an item) outrank the first Break notes of a run, which outrank acquisitions and the
 * tiers they reach; a lower toast never evicts a higher one and is dropped while the feed is full
 * of higher ones.
 */
enum class BuildNoticeKind(val priority: Int, val lifeSeconds: Float, val merges: Boolean) {
    CHANGE(0, 6f, false),
    BREAK(1, 3.5f, true),
    DISCOVERY(2, 6f, true),
    WARNING(2, 6f, true),
}

/**
 * A build toast. [life] counts down from [maxLife] and starts again when a merge adds a line;
 * [age] counts the seconds since the toast opened and never restarts, so the merge window and the
 * entrance run from the toast's opening.
 */
data class BuildNotificationProjection(
    val title: String,
    val details: ImmutableList<String>,
    val life: Float,
    val kind: BuildNoticeKind = BuildNoticeKind.CHANGE,
    val maxLife: Float = kind.lifeSeconds,
    val age: Float = maxLife - life,
)

/**
 * A Break ([secondary] and [reaction] are -1) or an elemental reaction flash at a world point.
 * [primary] and [secondary] are status element ordinals; [weight] counts the events of the same
 * kind merged into this flash (1..[InteractionFxLimits.MAX_BURST_WEIGHT]). [radius] is the look's
 * reach ([ReactionFxSpec.reachOf]); [direction] a heading in radians (NaN when unused), [flags] the
 * cue's `ReactionCueFlags` and [seed] the look's own hash seed.
 */
data class ElementBurstProjection(
    val x: Float,
    val y: Float,
    val primary: Int,
    val secondary: Int,
    val reaction: Int,
    val radius: Float,
    val life: Float,
    val maxLife: Float,
    val weight: Int,
    val direction: Float = Float.NaN,
    val flags: Int = 0,
    val seed: Int = 0,
)

/**
 * A skill cast ([phase] 0) or its delayed impact (1) at a world point toward ([dirX], [dirY]), with
 * the look's reach [radius] at [rank] 1..3; [flags] holds the cue's `SkillCueCodes` bits.
 */
data class SkillBurstProjection(
    val skill: Int,
    val phase: Int,
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    val radius: Float,
    val rank: Int,
    val flags: Int,
    val seed: Int,
    val life: Float,
    val maxLife: Float,
)

/** A character's Apex activation ([shape] a `CoreShape` ordinal) at the Core's position. */
data class ApexBurstProjection(
    val shape: Int,
    val x: Float,
    val y: Float,
    val seed: Int,
    val life: Float,
    val maxLife: Float,
)

/** The once-per-profile name stamp of a reaction met for the first time, at its world point. */
data class ReactionStampProjection(val x: Float, val y: Float, val reaction: Int, val life: Float, val maxLife: Float)

/** Immutable Interaction-owned visual snapshot attached after the stamped Game read. */
data class VisualFxProjection(
    val particles: ImmutableList<ParticleProjection>,
    val motionEchoes: ImmutableList<MotionEchoProjection>,
    val shockwaves: ImmutableList<ShockwaveProjection>,
    val damageNumbers: ImmutableList<DamageNumberProjection>,
    val weaponArcs: ImmutableList<WeaponArcProjection>,
    val buildNotifications: ImmutableList<BuildNotificationProjection> = immutableListOf(),
    val elementBursts: ImmutableList<ElementBurstProjection> = immutableListOf(),
    val reactionStamps: ImmutableList<ReactionStampProjection> = immutableListOf(),
    val skillBursts: ImmutableList<SkillBurstProjection> = immutableListOf(),
    val apexBursts: ImmutableList<ApexBurstProjection> = immutableListOf(),
    /** Reactions (bits by reaction ordinal) seen in this run, and those first met in this run. */
    val runReactionMask: Int = 0,
    val runDiscoveryMask: Int = 0,
) {
    companion object {
        val EMPTY = VisualFxProjection(
            particles = immutableListOf(),
            motionEchoes = immutableListOf(),
            shockwaves = immutableListOf(),
            damageNumbers = immutableListOf(),
            weaponArcs = immutableListOf(),
        )
    }
}
