// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings.nucleus.transition

import kotlin.math.abs
import kotlin.math.roundToInt
import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.features.settings.nucleus.domain.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.features.settings.nucleus.domain.DamageNumberFormat
import kinetickk.features.settings.nucleus.domain.DamageNumberSize
import kinetickk.features.settings.nucleus.domain.ParticleDensity
import kinetickk.features.settings.nucleus.domain.SIMULATION_SPEED_OPTIONS
import kinetickk.features.settings.nucleus.domain.SettingsAdjustmentDirection
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.protocol.SettingsDecisionContext
import kinetickk.features.settings.nucleus.protocol.SettingsIntent
import kinetickk.features.settings.nucleus.protocol.SettingsOutputKind
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionOutput
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionPayload
import kinetickk.features.settings.nucleus.protocol.SettingsProtocol
import kinetickk.features.settings.nucleus.protocol.SettingsRejection
import kinetickk.features.settings.nucleus.protocol.SettingsSemanticHandle

internal data class SettingsBallState(
    val values: SettingsValues,
    val transitionSteps: Int = 0,
)

internal class SettingsNucleus : Decider<
    SettingsBallState,
    SettingsIntent,
    SettingsDecisionContext,
    SettingsProjectionOutput,
> {
    override fun decide(
        state: SettingsBallState,
        pulse: SettingsIntent,
        context: SettingsDecisionContext,
    ): DecisionResult<SettingsBallState, SettingsProjectionOutput> {
        if (context.transitionArtifact != SettingsProtocol.TRANSITION_ARTIFACT) {
            return Rejected(
                SettingsRejection.InvalidContext(
                    field = "transitionArtifact",
                    reason = "unsupported artifact",
                ),
            )
        }
        if (context.operationId.value == 0uL) {
            return Rejected(
                SettingsRejection.InvalidContext(
                    field = "operationId",
                    reason = "must be reserved",
                ),
            )
        }

        val nextValues = state.values.apply(pulse).normalized()
        val output = SettingsProjectionOutput(
            semanticHandle = SettingsSemanticHandle(
                operationId = context.operationId,
                outputKind = SettingsOutputKind.SETTINGS_CHANGED,
                localOrdinalOrName = "settings-changed",
            ),
            sourceOrdinal = 0u,
            payload = SettingsProjectionPayload.SettingsChanged(nextValues),
        )
        return Accepted(
            Decision(
                nextState = SettingsBallState(
                    values = nextValues,
                    transitionSteps = 1,
                ),
                outputs = listOf(output),
            ),
        )
    }
}

private fun SettingsValues.apply(intent: SettingsIntent): SettingsValues = when (intent) {
    SettingsIntent.MuteToggled -> {
        val enable = !soundEnabled && !musicEnabled
        copy(soundEnabled = enable, musicEnabled = enable)
    }
    SettingsIntent.SoundToggled -> copy(soundEnabled = !soundEnabled)
    SettingsIntent.MusicToggled -> copy(musicEnabled = !musicEnabled)
    is SettingsIntent.MasterVolumeAdjusted -> copy(
        masterVolume = stepPercentage(
            value = masterVolume,
            direction = intent.direction,
            minimum = SettingsValues.MIN_MASTER_VOLUME,
            maximum = SettingsValues.MAX_MASTER_VOLUME,
        ),
    )
    is SettingsIntent.SimulationSpeedAdjusted -> copy(
        simulationSpeed = SIMULATION_SPEED_OPTIONS.stepFrom(
            currentValue = simulationSpeed,
            direction = intent.direction,
        ),
    )
    is SettingsIntent.TextScaleAdjusted -> copy(
        textScale = stepPercentage(
            value = textScale,
            direction = intent.direction,
            minimum = SettingsValues.MIN_TEXT_SCALE,
            maximum = SettingsValues.MAX_TEXT_SCALE,
        ),
    )
    SettingsIntent.ScreenShakeToggled -> copy(screenShake = !screenShake)
    is SettingsIntent.ParticleDensityAdjusted -> copy(
        particleDensity = ParticleDensity.entries.stepFrom(particleDensity, intent.direction),
    )
    SettingsIntent.DamageNumbersToggled -> copy(damageNumbers = !damageNumbers)
    is SettingsIntent.DamageNumberSizeAdjusted -> copy(
        damageNumberSize = DamageNumberSize.entries.stepFrom(damageNumberSize, intent.direction),
    )
    is SettingsIntent.DamageNumberFormatAdjusted -> copy(
        damageNumberFormat = DamageNumberFormat.entries.stepFrom(damageNumberFormat, intent.direction),
    )
    is SettingsIntent.DamageNumberTierThresholdAdjusted -> copy(
        damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.stepFrom(
            currentValue = damageNumberTierThreshold,
            direction = intent.direction,
        ),
    )
}

private fun stepPercentage(
    value: Float,
    direction: SettingsAdjustmentDirection,
    minimum: Float,
    maximum: Float,
): Float {
    val finiteValue = value.takeIf(Float::isFinite) ?: minimum
    val nextPercent = (finiteValue * 100f).roundToInt() + direction.delta
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}

private fun List<Float>.stepFrom(
    currentValue: Float,
    direction: SettingsAdjustmentDirection,
): Float {
    val finiteValue = currentValue.takeIf(Float::isFinite) ?: SettingsValues.DEFAULT_SIMULATION_SPEED
    val currentIndex = indices.minByOrNull { index -> abs(this[index] - finiteValue) } ?: 0
    return this[(currentIndex + direction.delta).coerceIn(indices)]
}

private fun List<Int>.stepFrom(
    currentValue: Int,
    direction: SettingsAdjustmentDirection,
): Int {
    val currentIndex = indices.minByOrNull { index -> abs(this[index].toLong() - currentValue.toLong()) } ?: 0
    return this[(currentIndex + direction.delta).coerceIn(indices)]
}

private fun <Value> List<Value>.stepFrom(
    currentValue: Value,
    direction: SettingsAdjustmentDirection,
): Value {
    val currentIndex = indexOf(currentValue).coerceAtLeast(0)
    return this[(currentIndex + direction.delta).coerceIn(indices)]
}
