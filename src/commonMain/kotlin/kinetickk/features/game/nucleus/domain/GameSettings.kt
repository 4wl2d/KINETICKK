// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus


enum class ParticleDensity { LOW, NORMAL, HIGH }

enum class DamageNumberSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.25f),
    HUGE(1.55f),
}

enum class DamageNumberFormat { COMPACT, FULL }

enum class DamageNumberTier { STANDARD, STRONG, POWERFUL, DEVASTATING }

internal const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD = 50
internal const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
internal const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

internal val DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS = listOf(
    10,
    25,
    50,
    100,
    250,
    500,
    1_000,
    2_500,
    5_000,
    10_000,
    25_000,
    50_000,
    100_000,
    250_000,
    500_000,
    1_000_000,
    2_500_000,
    5_000_000,
    10_000_000,
    25_000_000,
    50_000_000,
    100_000_000,
)

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

data class GameSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
    val simulationSpeed: Float = 1.15f,
    val textScale: Float = 1.25f,
    val screenShake: Boolean = true,
    val particleDensity: ParticleDensity = ParticleDensity.NORMAL,
    val damageNumbers: Boolean = true,
    val damageNumberSize: DamageNumberSize = DamageNumberSize.NORMAL,
    val damageNumberFormat: DamageNumberFormat = DamageNumberFormat.COMPACT,
    val damageNumberTierThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
) {
    fun normalized(): GameSettings = copy(
        masterVolume = masterVolume.coerceIn(0f, 1f),
        simulationSpeed = simulationSpeed.coerceIn(0.75f, 2f),
        textScale = textScale.coerceIn(1f, 1.75f),
        damageNumberTierThreshold = damageNumberTierThreshold.coerceIn(
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        ),
    )
}
