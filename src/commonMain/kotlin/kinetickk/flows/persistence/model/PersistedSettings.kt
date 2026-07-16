// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.model

import kinetickk.flows.persistence.ProgressPersistenceSchema
import kotlin.math.roundToInt

/**
 * Flow-owned Settings capture used by the combined-save wire format.
 *
 * Enum-like values are stable primitive codes from [ProgressPersistenceSchema], never Settings
 * authority domain types. Assembly owns the explicit mapping in both directions.
 */
data class PersistedSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = ProgressPersistenceSchema.DEFAULT_MASTER_VOLUME,
    val simulationSpeed: Float = ProgressPersistenceSchema.DEFAULT_SIMULATION_SPEED,
    val textScale: Float = ProgressPersistenceSchema.DEFAULT_TEXT_SCALE,
    val screenShake: Boolean = true,
    val particleDensityCode: Int = ProgressPersistenceSchema.PARTICLE_DENSITY_NORMAL_CODE,
    val damageNumbers: Boolean = true,
    val damageNumberSizeCode: Int = ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE,
    val damageNumberFormatCode: Int = ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE,
    val damageNumberTierThreshold: Int =
        ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
) {
    /** Canonicalizes untrusted Resource values without importing Settings authority semantics. */
    fun normalized(): PersistedSettings = copy(
        masterVolume = masterVolume.normalizedIn(
            minimum = ProgressPersistenceSchema.MIN_MASTER_VOLUME,
            maximum = ProgressPersistenceSchema.MAX_MASTER_VOLUME,
            fallback = ProgressPersistenceSchema.DEFAULT_MASTER_VOLUME,
        ),
        simulationSpeed = simulationSpeed.normalizedIn(
            minimum = ProgressPersistenceSchema.MIN_SIMULATION_SPEED,
            maximum = ProgressPersistenceSchema.MAX_SIMULATION_SPEED,
            fallback = ProgressPersistenceSchema.DEFAULT_SIMULATION_SPEED,
        ),
        textScale = textScale.normalizedIn(
            minimum = ProgressPersistenceSchema.MIN_TEXT_SCALE,
            maximum = ProgressPersistenceSchema.MAX_TEXT_SCALE,
            fallback = ProgressPersistenceSchema.DEFAULT_TEXT_SCALE,
        ),
        particleDensityCode = particleDensityCode.takeIf(
            ProgressPersistenceSchema::isSupportedParticleDensityCode,
        ) ?: ProgressPersistenceSchema.PARTICLE_DENSITY_NORMAL_CODE,
        damageNumberSizeCode = damageNumberSizeCode.takeIf(
            ProgressPersistenceSchema::isSupportedDamageNumberSizeCode,
        ) ?: ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE,
        damageNumberFormatCode = damageNumberFormatCode.takeIf(
            ProgressPersistenceSchema::isSupportedDamageNumberFormatCode,
        ) ?: ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE,
        damageNumberTierThreshold = damageNumberTierThreshold.takeIf(
            ProgressPersistenceSchema::isSupportedDamageNumberTierThreshold,
        ) ?: ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
    )
}

private fun Float.normalizedIn(minimum: Float, maximum: Float, fallback: Float): Float =
    if (isFinite()) {
        ((coerceIn(minimum, maximum) * 100f).roundToInt() / 100f)
            .coerceIn(minimum, maximum)
    } else {
        fallback
    }
