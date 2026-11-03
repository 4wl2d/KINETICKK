// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kotlin.math.max
import kotlin.math.roundToLong

enum class DamageNumberTier { STANDARD, STRONG, POWERFUL, DEVASTATING }

const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

/**
 * The color tier of a damage number of [amount] displayed damage. The player's [firstThreshold] is tier-0 damage: it
 * reads at the run's [numberScale] like the number does, so the threshold, four and twenty times it mark the same
 * relative strength on every Rebirth tier.
 */
fun damageNumberTier(
    amount: Long,
    firstThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
    critical: Boolean = false,
    numberScale: Double = 1.0,
): DamageNumberTier {
    val threshold = (firstThreshold.coerceIn(
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
    ) * numberScale.coerceAtLeast(1.0)).roundToLong()
    val magnitudeTier = when {
        amount >= threshold * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER -> DamageNumberTier.DEVASTATING
        amount >= threshold * DAMAGE_NUMBER_POWERFUL_MULTIPLIER -> DamageNumberTier.POWERFUL
        amount >= threshold -> DamageNumberTier.STRONG
        else -> DamageNumberTier.STANDARD
    }
    return if (critical && magnitudeTier < DamageNumberTier.POWERFUL) {
        DamageNumberTier.POWERFUL
    } else {
        magnitudeTier
    }
}

fun formatRunTime(seconds: Float): String {
    val total = max(0, seconds.toInt())
    val minutes = total / 60
    val remaining = total % 60
    return minutes.toString().padStart(2, '0') + ":" + remaining.toString().padStart(2, '0')
}
