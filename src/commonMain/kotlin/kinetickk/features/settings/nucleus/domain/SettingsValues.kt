// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings.nucleus.domain

import kotlin.math.roundToInt

enum class ParticleDensity { LOW, NORMAL, HIGH }

enum class DamageNumberSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.25f),
    HUGE(1.55f),
}

enum class DamageNumberFormat { COMPACT, FULL }

enum class SettingsAdjustmentDirection(val delta: Int) {
    DECREASE(-1),
    INCREASE(1),
}

/** The complete immutable semantic state owned by the local Settings Ball. */
data class SettingsValues(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val simulationSpeed: Float = DEFAULT_SIMULATION_SPEED,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val screenShake: Boolean = true,
    val particleDensity: ParticleDensity = ParticleDensity.NORMAL,
    val damageNumbers: Boolean = true,
    val damageNumberSize: DamageNumberSize = DamageNumberSize.NORMAL,
    val damageNumberFormat: DamageNumberFormat = DamageNumberFormat.COMPACT,
    val damageNumberTierThreshold: Int = DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
) {
    /** Quarantines non-finite values and clamps every bounded numeric setting. */
    fun normalized(): SettingsValues = copy(
        masterVolume = masterVolume.normalizedIn(
            minimum = MIN_MASTER_VOLUME,
            maximum = MAX_MASTER_VOLUME,
            fallback = DEFAULT_MASTER_VOLUME,
        ),
        simulationSpeed = simulationSpeed.normalizedIn(
            minimum = MIN_SIMULATION_SPEED,
            maximum = MAX_SIMULATION_SPEED,
            fallback = DEFAULT_SIMULATION_SPEED,
        ),
        textScale = textScale.normalizedIn(
            minimum = MIN_TEXT_SCALE,
            maximum = MAX_TEXT_SCALE,
            fallback = DEFAULT_TEXT_SCALE,
        ),
        damageNumberTierThreshold = damageNumberTierThreshold.coerceIn(
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(),
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        ),
    )

    companion object {
        const val DEFAULT_MASTER_VOLUME = 0.65f
        const val DEFAULT_SIMULATION_SPEED = 1.15f
        const val DEFAULT_TEXT_SCALE = 1.25f
        const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD = 50

        const val MIN_MASTER_VOLUME = 0f
        const val MAX_MASTER_VOLUME = 1f
        const val MIN_SIMULATION_SPEED = 0.75f
        const val MAX_SIMULATION_SPEED = 2f
        const val MIN_TEXT_SCALE = 1f
        const val MAX_TEXT_SCALE = 1.75f
    }
}

internal val SIMULATION_SPEED_OPTIONS = listOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)

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

private fun Float.normalizedIn(minimum: Float, maximum: Float, fallback: Float): Float =
    if (isFinite()) {
        ((coerceIn(minimum, maximum) * 100f).roundToInt() / 100f)
            .coerceIn(minimum, maximum)
    } else {
        fallback
    }
