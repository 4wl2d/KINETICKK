// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class RebirthDirective(
    val displayName: String,
    val description: String,
) {
    BASELINE("Baseline", "The original Architect cycle."),
    SWARM("Swarm", "This cycle adds another rank of hostile density."),
    FORTIFIED("Fortified", "This cycle adds another rank of enemy integrity."),
    OVERCLOCKED("Overclocked", "This cycle adds another rank of hostile speed and damage."),
}

/**
 * A named rule a Rebirth tier adds to the run. Tier n activates entry n - 1 and every entry before
 * it; the order is the activation order, so entries are append-only.
 */
enum class RebirthMutator(
    val displayName: String,
    val description: String,
) {
    PACK_HUNTERS("Pack Hunters", "Enemies arrive in larger packs."),
    HARDENED_ELITES("Hardened Elites", "Elites carry more integrity and arrive with escorts."),
    HUNTER_PROTOCOL("Hunter Protocol", "Ranged hunters arrive earlier in the run."),
    VOLATILE_REMAINS("Volatile Remains", "Ordinary enemies burst shortly after they die."),
    FRACTURED_SPACE("Fractured Space", "Space holds more obstacles, and some asteroids are unstable."),
    OVERCLOCKED_SWARM("Overclocked Swarm", "Enemies attack more often."),
    ENTROPY_TIDE("Entropy Tide", "Healing and shield regeneration are weaker."),
    ARMORED_VANGUARD("Armored Vanguard", "Some enemies wear armor until an impact cracks it."),
    BLACKOUT("Blackout", "Data fades sooner and the minimap sees less."),
    SINGULARITY("Singularity", "The Architect gains an enraged fourth phase."),
}

/** Immutable, render-ready tuning for one Rebirth cycle. */
data class RebirthProfile(
    val tier: Int,
    val directive: RebirthDirective,
    val openingEnemyCount: Int,
    val enemyCapMultiplier: Float,
    val spawnRateMultiplier: Float,
    val enemyHealthMultiplier: Float,
    val enemySpeedMultiplier: Float,
    val incomingDamageMultiplier: Float,
    val eliteRateMultiplier: Float,
    val threatTimeOffsetSeconds: Float,
    val playerPowerMultiplier: Float,
    val playerIntegrityBonus: Float,
    val matterGainMultiplier: Float,
    val bonusRerolls: Int,
    val maximumActiveEnemies: Int,
    val minimumSpawnIntervalSeconds: Float,
    val minimumEliteIntervalSeconds: Float,
    /** Active mutators in activation order; see [RebirthMutator]. */
    val mutators: ImmutableList<RebirthMutator> = immutableListOf(),
    /** Extra enemies added to every spawned pack. */
    val packSizeBonus: Int = 0,
    /** Scales the mean number of world obstacles per chunk. */
    val obstacleDensityMultiplier: Float = 1f,
) {
    init {
        require(tier >= 0) { "Rebirth tier must be non-negative" }
        require(maximumActiveEnemies > 0) { "Rebirth enemy cap must be positive" }
        require(minimumSpawnIntervalSeconds.isFinite() && minimumSpawnIntervalSeconds > 0f) {
            "Minimum spawn interval must be finite and positive"
        }
        require(minimumEliteIntervalSeconds.isFinite() && minimumEliteIntervalSeconds > 0f) {
            "Minimum elite interval must be finite and positive"
        }
        require(mutators.toSet().size == mutators.size) { "Rebirth mutators must be distinct" }
        require(packSizeBonus >= 0) { "Rebirth pack size bonus must not be negative" }
        require(obstacleDensityMultiplier.isFinite() && obstacleDensityMultiplier > 0f) {
            "Obstacle density multiplier must be finite and positive"
        }
    }

    private val mutatorMask: Int = mutators.fold(0) { mask, mutator -> mask or (1 shl mutator.ordinal) }

    /** True when this cycle activates [mutator]; allocation-free for per-step reads. */
    fun has(mutator: RebirthMutator): Boolean = mutatorMask and (1 shl mutator.ordinal) != 0

    fun enemyCap(base: Int): Int = min(
        maximumActiveEnemies,
        max(base, floor(base.coerceAtLeast(0) * enemyCapMultiplier).toInt()),
    )

    fun spawnInterval(baseSeconds: Float): Float =
        max(minimumSpawnIntervalSeconds, baseSeconds / spawnRateMultiplier)

    fun eliteInterval(baseSeconds: Float): Float =
        max(minimumEliteIntervalSeconds, baseSeconds / eliteRateMultiplier)

    fun enemyHealth(base: Float): Float = base * enemyHealthMultiplier
}
