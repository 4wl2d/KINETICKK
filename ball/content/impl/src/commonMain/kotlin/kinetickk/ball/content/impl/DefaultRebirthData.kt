// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.defaultRebirthProfile
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal const val DEFAULT_MAX_ACTIVE_ENEMIES: Int = 120
internal const val DEFAULT_MIN_SPAWN_INTERVAL_SECONDS: Float = 0.09f
internal const val DEFAULT_MIN_ELITE_INTERVAL_SECONDS: Float = 24f

/** Tiers 0..10 of the shipped Rebirth ladder (formulas and mutators: [defaultRebirthProfile]). */
internal fun defaultRebirthProfiles(): ImmutableList<RebirthProfile> =
    (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL)
        .map { tier ->
            defaultRebirthProfile(
                tier = tier,
                maximumActiveEnemies = DEFAULT_MAX_ACTIVE_ENEMIES,
                minimumSpawnIntervalSeconds = DEFAULT_MIN_SPAWN_INTERVAL_SECONDS,
                minimumEliteIntervalSeconds = DEFAULT_MIN_ELITE_INTERVAL_SECONDS,
            )
        }
        .toImmutableList()
