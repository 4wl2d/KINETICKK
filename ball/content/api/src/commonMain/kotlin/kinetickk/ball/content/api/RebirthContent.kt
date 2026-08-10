// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

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
    val maximumActiveEnemies: Int,
    val minimumSpawnIntervalSeconds: Float,
    val minimumEliteIntervalSeconds: Float,
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
    }

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
