// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal const val DEFAULT_MAX_ACTIVE_ENEMIES: Int = 120
internal const val DEFAULT_MIN_SPAWN_INTERVAL_SECONDS: Float = 0.09f
internal const val DEFAULT_MIN_ELITE_INTERVAL_SECONDS: Float = 24f

internal fun defaultRebirthProfiles(): ImmutableList<RebirthProfile> =
    (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL)
        .map(::defaultRebirthProfile)
        .toImmutableList()

private fun defaultRebirthProfile(tier: Int): RebirthProfile {
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
        enemyHealthMultiplier =
            1f + tier * 0.18f + tier * tier * 0.012f + fortifiedRanks * 0.02f,
        enemySpeedMultiplier = 1f + tier * 0.025f + overclockedRanks * 0.005f,
        incomingDamageMultiplier = 1f + tier * 0.08f + overclockedRanks * 0.005f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = tier * 8f,
        playerPowerMultiplier = 1f + tier * 0.05f,
        playerIntegrityBonus = tier * 3f,
        matterGainMultiplier = 1f + tier * 0.12f + fortifiedRanks * 0.01f,
        bonusRerolls = tier / 5,
        maximumActiveEnemies = DEFAULT_MAX_ACTIVE_ENEMIES,
        minimumSpawnIntervalSeconds = DEFAULT_MIN_SPAWN_INTERVAL_SECONDS,
        minimumEliteIntervalSeconds = DEFAULT_MIN_ELITE_INTERVAL_SECONDS,
    )
}
