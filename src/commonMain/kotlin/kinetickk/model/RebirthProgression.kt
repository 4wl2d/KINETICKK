// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

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
) {
    fun enemyCap(base: Int): Int = min(
        RebirthProgression.MAX_ACTIVE_ENEMIES,
        max(base, floor(base.coerceAtLeast(0) * enemyCapMultiplier).toInt()),
    )

    fun spawnInterval(baseSeconds: Float): Float =
        max(RebirthProgression.MIN_SPAWN_INTERVAL_SECONDS, baseSeconds / spawnRateMultiplier)

    fun eliteInterval(baseSeconds: Float): Float =
        max(RebirthProgression.MIN_ELITE_INTERVAL_SECONDS, baseSeconds / eliteRateMultiplier)

    fun enemyHealth(base: Float): Float = base * enemyHealthMultiplier
}

object RebirthProgression {
    const val MAX_LEVEL = 10
    const val MAX_ACTIVE_ENEMIES = 120
    const val MIN_SPAWN_INTERVAL_SECONDS = 0.09f
    const val MIN_ELITE_INTERVAL_SECONDS = 24f

    fun profile(level: Int): RebirthProfile {
        val tier = level.coerceIn(0, MAX_LEVEL)
        val swarmRanks = (tier + 2) / 3
        val fortifiedRanks = (tier + 1) / 3
        val overclockedRanks = tier / 3
        val directive = when {
            tier == 0 -> RebirthDirective.BASELINE
            (tier - 1) % 3 == 0 -> RebirthDirective.SWARM
            (tier - 1) % 3 == 1 -> RebirthDirective.FORTIFIED
            else -> RebirthDirective.OVERCLOCKED
        }
        return RebirthProfile(
            tier = tier,
            directive = directive,
            openingEnemyCount = 5 + (tier + 1) / 2,
            enemyCapMultiplier = 1f + tier * 0.08f + swarmRanks * 0.01f,
            spawnRateMultiplier = 1f + tier * 0.06f + swarmRanks * 0.01f,
            enemyHealthMultiplier = 1f + tier * 0.18f + tier * tier * 0.012f + fortifiedRanks * 0.02f,
            enemySpeedMultiplier = 1f + tier * 0.025f + overclockedRanks * 0.005f,
            incomingDamageMultiplier = 1f + tier * 0.08f + overclockedRanks * 0.005f,
            eliteRateMultiplier = 1f,
            threatTimeOffsetSeconds = tier * 8f,
            playerPowerMultiplier = 1f + tier * 0.05f,
            playerIntegrityBonus = tier * 3f,
            matterGainMultiplier = 1f + tier * 0.12f + fortifiedRanks * 0.01f,
            bonusRerolls = tier / 5,
        )
    }
}
