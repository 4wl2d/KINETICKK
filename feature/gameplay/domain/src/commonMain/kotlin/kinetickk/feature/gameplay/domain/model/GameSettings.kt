// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.model

import kinetickk.core.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.core.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.core.profile.api.DamageNumberFormat

enum class DamageNumberTier { STANDARD, STRONG, POWERFUL, DEVASTATING }

const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

fun formatDamageNumber(amount: Long, format: DamageNumberFormat): String = when (format) {
    DamageNumberFormat.COMPACT -> abbreviateNumber(amount)
    DamageNumberFormat.FULL -> amount.toString()
}

fun damageNumberTier(
    amount: Long,
    firstThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
    critical: Boolean = false,
): DamageNumberTier {
    val threshold = firstThreshold.coerceIn(
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
        DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
    ).toLong()
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
