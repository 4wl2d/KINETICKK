// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize

/** Content-owned starting values; seconds always mean active simulation time at 1x. */
data class FatigueTuning(
    val velocitySmoothingSeconds: Float = 0.15f,
    val turnDegrees: Float = 40f,
    val minimumTurnSpeed: Float = 200f,
    val turnHoldSeconds: Float = 0.20f,
    val turnRecovery: Float = 0.40f,
    val turnCooldownSeconds: Float = 0.75f,
    val maximumBrakeRecoverySpeed: Float = 250f,
    val brakeRecoveryPerSecond: Float = 0.80f,
    val normalRecoveryPerSecond: Float = 0.30f,
    /** Normalized center-to-edge reach; 0.8 leaves the outer 10% of each screen axis strained. */
    val edgeStrainStart: Float = 0.80f,
    val edgeDrainPerSecond: Float = 0.40f,
) {
    init {
        require(velocitySmoothingSeconds.isFinite() && velocitySmoothingSeconds > 0f)
        require(turnDegrees.isFinite() && turnDegrees in 1f..179f)
        require(minimumTurnSpeed.isFinite() && minimumTurnSpeed > 0f)
        require(turnHoldSeconds.isFinite() && turnHoldSeconds > 0f)
        require(turnRecovery.isFinite() && turnRecovery in 0f..1f)
        require(turnCooldownSeconds.isFinite() && turnCooldownSeconds >= turnHoldSeconds)
        require(maximumBrakeRecoverySpeed.isFinite() && maximumBrakeRecoverySpeed > 0f)
        require(brakeRecoveryPerSecond.isFinite() && brakeRecoveryPerSecond > 0f)
        require(normalRecoveryPerSecond.isFinite() && normalRecoveryPerSecond > 0f)
        require(edgeStrainStart.isFinite() && edgeStrainStart > 0f && edgeStrainStart < 1f)
        require(edgeDrainPerSecond.isFinite() && edgeDrainPerSecond > 0f)
    }
}

/** One immutable source for encounter pressure, build growth and the displayed boss clock. */
data class RunTempoProfile(
    val bossAtSeconds: Float = 12f * 60f,
    val firstEliteAtSeconds: Float = 38f,
    val dataPickupMultiplier: Float = 2.60f,
    val matterRewardMultiplier: Float = 1.40f,
    val weaponAmplificationEveryLevels: Int = 2,
    val maximumAutomaticWeaponLevel: Int = 10,
    val fatigue: FatigueTuning = FatigueTuning(),
    /** Highest run level; Data stops levelling here (the Apex unlocks at it). */
    val maximumLevel: Int = DEFAULT_MAXIMUM_LEVEL,
    /**
     * Data needed to go from level `L` to `L + 1`, for `L = 1 until maximumLevel` (entry `L - 1`):
     * positive and non-decreasing. The default is the shipped curve ([defaultLevelRequirements]).
     */
    val levelRequirements: ImmutableList<Int> = defaultLevelRequirements(maximumLevel = maximumLevel),
    /** Maximum integrity every level-up adds. */
    val levelIntegrity: Float = 2.3f,
    /** Integrity every level-up repairs. */
    val levelRepair: Float = 7f,
    /** Run levels that open the skill slots, in slot order (one per [ContentBounds.SKILL_SLOTS]). */
    val skillSlotLevels: ImmutableList<Int> = immutableListOf(2, 8, 16),
    /** Item offers on levels divisible by this carry a skill rank-up card. */
    val skillRankEveryLevels: Int = 4,
    /** Levels a skill must be owned or ranked for before it is eligible for the next rank-up card. */
    val skillRankGapLevels: Int = 2,
    /**
     * First run level whose requirement the Rebirth tier's [RebirthProfile.dataRequirementScale] scales. Earlier
     * levels keep the table at every tier, so a high tier's opening level-ups arrive as on tier 0; 1 scales the whole
     * table and a value at or above [maximumLevel] scales nothing.
     */
    val rebirthScaleFromLevel: Int = LEGACY_LEVELS + 1,
) {
    init {
        require(bossAtSeconds.isFinite() && bossAtSeconds > 0f)
        require(firstEliteAtSeconds.isFinite() && firstEliteAtSeconds in 0f..bossAtSeconds)
        require(dataPickupMultiplier.isFinite() && dataPickupMultiplier > 0f)
        require(matterRewardMultiplier.isFinite() && matterRewardMultiplier > 0f)
        require(weaponAmplificationEveryLevels > 0)
        require(maximumAutomaticWeaponLevel in 1..100)
        require(maximumLevel in MIN_MAXIMUM_LEVEL..ContentBounds.MAX_RUN_LEVEL) {
            "Maximum run level must be within $MIN_MAXIMUM_LEVEL..${ContentBounds.MAX_RUN_LEVEL}"
        }
        require(levelRequirements.size == maximumLevel - 1) {
            "Level requirements must list one entry per level below the maximum"
        }
        require(levelRequirements.all { it > 0 }) { "Level requirements must be positive" }
        require(levelRequirements.zipWithNext().all { (first, second) -> first <= second }) {
            "Level requirements must not decrease"
        }
        require(levelIntegrity.isFinite() && levelIntegrity >= 0f)
        require(levelRepair.isFinite() && levelRepair >= 0f)
        require(skillSlotLevels.size == ContentBounds.SKILL_SLOTS) { "Every skill slot needs an opening level" }
        require(skillSlotLevels.all { it in MIN_MAXIMUM_LEVEL until maximumLevel }) {
            "Skill slots open on a level-up below the maximum level"
        }
        require(skillSlotLevels.zipWithNext().all { (first, second) -> first < second }) {
            "Skill slot levels must increase"
        }
        require(skillRankEveryLevels in 1 until maximumLevel)
        require(skillRankGapLevels in 0 until maximumLevel)
        require(rebirthScaleFromLevel in 1..ContentBounds.MAX_RUN_LEVEL) {
            "The Rebirth Data scale must start on a run level within 1..${ContentBounds.MAX_RUN_LEVEL}"
        }
    }

    fun progress(elapsedSeconds: Float): Float = (elapsedSeconds / bossAtSeconds).coerceIn(0f, 1f)

    // Fractions preserve encounter/build milestones when tuning the boss's arrival.
    fun encounterStage(elapsedSeconds: Float): Int = when {
        progress(elapsedSeconds) < 38f / 720f -> 0
        progress(elapsedSeconds) < 90f / 720f -> 1
        progress(elapsedSeconds) < 150f / 720f -> 2
        progress(elapsedSeconds) < 240f / 720f -> 3
        progress(elapsedSeconds) < 360f / 720f -> 4
        else -> 5
    }

    /** Fewer, tougher ordinary enemies: a low cap and a slow arrival rate pay for their integrity. */
    fun ordinaryEnemyCap(elapsedSeconds: Float): Int = 8 + (progress(elapsedSeconds) * 34f).toInt()
    fun spawnIntervalSeconds(elapsedSeconds: Float): Float = (1.58f - progress(elapsedSeconds) * 1.2f).coerceAtLeast(0.38f)
    fun eliteIntervalSeconds(elapsedSeconds: Float): Float = (68f - progress(elapsedSeconds) * 20f).coerceAtLeast(48f)
    /** Enemy integrity outgrows every player run growth below: 1 + 5.9p. */
    fun enemyHealthMultiplier(elapsedSeconds: Float): Float = 1f + 5.9f * progress(elapsedSeconds)
    /** Run scale of flat, status, reaction and proc damage; gameplay and reward cards both read it. */
    fun runScaleGrowth(elapsedSeconds: Float): Float = 1f + 1.6f * progress(elapsedSeconds)
    fun impactRunGrowth(elapsedSeconds: Float): Float = 1f + 1.2f * progress(elapsedSeconds)
    /** Growth of the equipped weapon's own hits: 1 + 1.6p, the same pace as flat and proc damage. */
    fun weaponRunGrowth(elapsedSeconds: Float): Float = 1f + 1.6f * progress(elapsedSeconds)
    /** Growth of character abilities and weapon-based item effects: 1 + 1.0p. */
    fun abilityRunGrowth(elapsedSeconds: Float): Float = 1f + 1.0f * progress(elapsedSeconds)
    /** Damage the build deals to its own Core (reaction recoil) keeps linear growth. */
    fun selfDamageRunGrowth(elapsedSeconds: Float): Float = 1f + 1.6f * progress(elapsedSeconds)
    fun attackRateMultiplier(elapsedSeconds: Float): Float = 1f + progress(elapsedSeconds) * 0.40f
    fun denseAttacks(elapsedSeconds: Float): Boolean = progress(elapsedSeconds) >= 0.58f
    /** Total: at and past the cap it returns the last entry, which the capped state only displays. */
    fun dataRequiredForLevel(level: Int): Int = levelRequirements[level.coerceIn(1, maximumLevel - 1) - 1]

    /**
     * [dataRequiredForLevel] at the Rebirth tier [rebirth]: scaled by its [RebirthProfile.dataRequirement] from
     * [rebirthScaleFromLevel] on. Total like [dataRequiredForLevel]; the level is clamped before the comparison, so
     * the capped level displays the last scaled entry.
     */
    fun dataRequiredForLevel(level: Int, rebirth: RebirthProfile): Int {
        val tableLevel = level.coerceIn(1, maximumLevel - 1)
        val base = levelRequirements[tableLevel - 1]
        return if (tableLevel < rebirthScaleFromLevel) base else rebirth.dataRequirement(base)
    }
    fun grantsWeaponAmplification(level: Int): Boolean = level > 1 && level % weaponAmplificationEveryLevels == 0

    companion object {
        /** A run needs at least one level-up below its cap. */
        const val MIN_MAXIMUM_LEVEL: Int = 2

        /** The shipped run level cap. */
        const val DEFAULT_MAXIMUM_LEVEL: Int = 42

        /** Levels whose requirement equals the legacy curve; the ramp starts on the next one. */
        const val LEGACY_LEVELS: Int = 15

        /** Levels over which a plateau ramps in, in equal integer steps. */
        const val PLATEAU_RAMP_LEVELS: Int = 5

        /** Extra Data per level once the first ramp (levels 16..20) is complete. */
        const val DEFAULT_RAMP_PLATEAU: Int = 160

        /** Extra Data per level once the second ramp (levels 31..35) is complete. */
        const val DEFAULT_TOP_PLATEAU: Int = 0

        /** Last level before the second ramp. */
        const val TOP_RAMP_AFTER_LEVEL: Int = 30
    }
}

/**
 * The shipped curve for levels `1 until maximumLevel` (integer arithmetic):
 * `req(L) = legacy(L) + rampPlateau × clamp(L − 15, 0, 5) / 5 + topPlateau × clamp(L − 30, 0, 5) / 5`, where
 * `legacy` is [legacyLevelRequirements]' quadratic. Levels 1–15 keep the legacy requirement; [rampPlateau] adds up to
 * its value over levels 16–20 and [topPlateau] over levels 31–35. Both are calibration knobs, so a test can rebuild
 * the table with other values.
 */
fun defaultLevelRequirements(
    rampPlateau: Int = RunTempoProfile.DEFAULT_RAMP_PLATEAU,
    topPlateau: Int = RunTempoProfile.DEFAULT_TOP_PLATEAU,
    maximumLevel: Int = RunTempoProfile.DEFAULT_MAXIMUM_LEVEL,
): ImmutableList<Int> {
    require(maximumLevel in RunTempoProfile.MIN_MAXIMUM_LEVEL..ContentBounds.MAX_RUN_LEVEL)
    require(rampPlateau >= 0 && topPlateau >= 0) { "Level plateaus must not be negative" }
    return immutableListOfSize(maximumLevel - 1) { index ->
        val level = index + 1
        val requirement = legacyLevelRequirement(level).toLong() +
            plateauStep(rampPlateau, level - RunTempoProfile.LEGACY_LEVELS) +
            plateauStep(topPlateau, level - RunTempoProfile.TOP_RAMP_AFTER_LEVEL)
        require(requirement <= Int.MAX_VALUE) { "Level $level requirement exceeds the Int range" }
        requirement.toInt()
    }
}

private fun plateauStep(plateau: Int, levelsIntoRamp: Int): Long =
    plateau.toLong() * levelsIntoRamp.coerceIn(0, RunTempoProfile.PLATEAU_RAMP_LEVELS) / RunTempoProfile.PLATEAU_RAMP_LEVELS

/** The pre-0.5 quadratic curve `18 + 8L + L²/8` (18 at level 1) for levels `1 until maximumLevel`. */
fun legacyLevelRequirements(maximumLevel: Int): ImmutableList<Int> {
    require(maximumLevel in RunTempoProfile.MIN_MAXIMUM_LEVEL..ContentBounds.MAX_RUN_LEVEL)
    return immutableListOfSize(maximumLevel - 1) { index -> legacyLevelRequirement(index + 1) }
}

private fun legacyLevelRequirement(level: Int): Int = if (level <= 1) 18 else 18 + level * 8 + level * level / 8
