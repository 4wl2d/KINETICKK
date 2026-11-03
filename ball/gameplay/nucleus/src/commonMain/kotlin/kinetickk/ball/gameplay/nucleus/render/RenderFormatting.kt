// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.ball.profile.api.DamageNumberFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

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

fun formatRunTime(seconds: Float): String {
    val total = max(0, seconds.toInt())
    val minutes = total / 60
    val remaining = total % 60
    return minutes.toString().padStart(2, '0') + ":" + remaining.toString().padStart(2, '0')
}

private val NUMBER_SUFFIXES = arrayOf("", "K", "M", "B", "T", "Qa", "Qi")

fun abbreviateNumber(value: Long): String {
    if (value in -999L..999L) return value.toString()

    var scaled = value.toDouble()
    var suffixIndex = 0
    while (abs(scaled) >= 1_000.0 && suffixIndex < NUMBER_SUFFIXES.lastIndex) {
        scaled /= 1_000.0
        suffixIndex++
    }

    var roundedTenths = (scaled * 10.0).roundToLong()
    if (abs(roundedTenths) >= 10_000L && suffixIndex < NUMBER_SUFFIXES.lastIndex) {
        scaled /= 1_000.0
        suffixIndex++
        roundedTenths = (scaled * 10.0).roundToLong()
    }

    val whole = roundedTenths / 10L
    val fraction = abs(roundedTenths % 10L)
    val number = if (fraction == 0L) whole.toString() else "$whole.$fraction"
    return number + NUMBER_SUFFIXES[suffixIndex]
}
