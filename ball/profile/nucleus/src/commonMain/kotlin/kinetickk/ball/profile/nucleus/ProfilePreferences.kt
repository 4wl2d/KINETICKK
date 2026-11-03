// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun ProfileNucleus.adjustPreference(
    state: ProfileState,
    adjustment: ProfilePreferenceAdjustment,
): ProfileDecision {
    val current = state.profile.preferences
    val next = when (adjustment) {
        is ProfilePreferenceAdjustment.SetLanguage -> current.copy(language = adjustment.language)
        ProfilePreferenceAdjustment.ToggleSoundEffects ->
            current.copy(soundEnabled = !current.soundEnabled)
        ProfilePreferenceAdjustment.ToggleMusic ->
            current.copy(musicEnabled = !current.musicEnabled)
        is ProfilePreferenceAdjustment.StepMasterVolume -> current.copy(
            masterVolume = stepPercentage(current.masterVolume, adjustment.direction, 0f, 1f),
        )
        is ProfilePreferenceAdjustment.SetMasterVolume -> current.copy(masterVolume = adjustment.percent / 100f)
        is ProfilePreferenceAdjustment.StepSimulationSpeed -> {
            val currentIndex = SIMULATION_SPEED_OPTIONS.indices.minByOrNull { index ->
                abs(SIMULATION_SPEED_OPTIONS[index] - current.simulationSpeed)
            } ?: DEFAULT_SIMULATION_SPEED_INDEX
            current.copy(
                simulationSpeed = SIMULATION_SPEED_OPTIONS[
                    (currentIndex + adjustment.direction.delta).coerceIn(SIMULATION_SPEED_OPTIONS.indices)
                ],
            )
        }
        is ProfilePreferenceAdjustment.SetSimulationSpeed -> current.copy(simulationSpeed = adjustment.speed)
        is ProfilePreferenceAdjustment.StepTextScale -> current.copy(
            textScale = stepPercentage(current.textScale, adjustment.direction, 1f, 1.75f),
        )
        ProfilePreferenceAdjustment.ToggleScreenShake ->
            current.copy(screenShake = !current.screenShake)
        ProfilePreferenceAdjustment.ToggleRunStatisticsSide ->
            current.copy(runStatisticsOnLeft = !current.runStatisticsOnLeft)
        ProfilePreferenceAdjustment.ToggleMinimap ->
            current.copy(minimapEnabled = !current.minimapEnabled)
        is ProfilePreferenceAdjustment.StepParticleDensity -> current.copy(
            particleDensity = ParticleDensity.entries[
                (current.particleDensity.ordinal + adjustment.direction.delta)
                    .coerceIn(ParticleDensity.entries.indices)
            ],
        )
        is ProfilePreferenceAdjustment.SetParticleDensity -> current.copy(particleDensity = adjustment.density)
        ProfilePreferenceAdjustment.ToggleDamageNumbers ->
            current.copy(damageNumbers = !current.damageNumbers)
        is ProfilePreferenceAdjustment.StepDamageNumberSize -> current.copy(
            damageNumberSize = DamageNumberSize.entries[
                (current.damageNumberSize.ordinal + adjustment.direction.delta)
                    .coerceIn(DamageNumberSize.entries.indices)
            ],
        )
        is ProfilePreferenceAdjustment.SetDamageNumberSize -> current.copy(damageNumberSize = adjustment.size)
        is ProfilePreferenceAdjustment.StepDamageNumberFormat -> current.copy(
            damageNumberFormat = DamageNumberFormat.entries[
                (current.damageNumberFormat.ordinal + adjustment.direction.delta)
                    .coerceIn(DamageNumberFormat.entries.indices)
            ],
        )
        is ProfilePreferenceAdjustment.SetDamageNumberFormat -> current.copy(damageNumberFormat = adjustment.format)
        is ProfilePreferenceAdjustment.SetColorVision -> current.copy(colorVision = adjustment.colorVision)
        is ProfilePreferenceAdjustment.StepDamageNumberTierThreshold -> {
            val currentIndex = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
                abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - current.damageNumberTierThreshold)
            } ?: DEFAULT_DAMAGE_THRESHOLD_INDEX
            current.copy(
                damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[
                    (currentIndex + adjustment.direction.delta)
                        .coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
                ],
            )
        }
        is ProfilePreferenceAdjustment.SetWindowMode -> current.copy(windowMode = adjustment.mode)
        is ProfilePreferenceAdjustment.SetWindowSize -> current.copy(windowSize = adjustment.preset)
        ProfilePreferenceAdjustment.ToggleVsync -> current.copy(vsync = !current.vsync)
        is ProfilePreferenceAdjustment.SetFrameRateCap -> current.copy(frameRateCap = adjustment.fps)
        is ProfilePreferenceAdjustment.SetRefreshRate -> current.copy(refreshRateHz = adjustment.hz)
        ProfilePreferenceAdjustment.ToggleImmersive -> current.copy(immersive = !current.immersive)
        is ProfilePreferenceAdjustment.SetInterfaceScale -> current.copy(interfaceScalePercent = adjustment.percent)
        ProfilePreferenceAdjustment.ToggleShowFps -> current.copy(showFps = !current.showFps)
        ProfilePreferenceAdjustment.TogglePauseOnFocusLoss ->
            current.copy(pauseOnFocusLoss = !current.pauseOnFocusLoss)
        is ProfilePreferenceAdjustment.SetReactionEffects -> current.copy(reactionEffects = adjustment.level)
        // A key another action holds swaps with it, so the map stays complete and conflict-free.
        is ProfilePreferenceAdjustment.SetKeyBinding ->
            current.copy(keyBindings = current.keyBindings.with(adjustment.action, adjustment.key))
        ProfilePreferenceAdjustment.ResetKeyBindings -> current.copy(keyBindings = KeyBindings.DEFAULT)
    }.normalized()
    if (next == current) return rejected(ProfileRejection.NoChange)
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(preferences = next),
    )
}

internal fun ProfileNucleus.toggleMute(
    state: ProfileState,
): ProfileDecision {
    val current = state.profile.preferences
    val enable = !current.soundEnabled && !current.musicEnabled
    val next = current.copy(soundEnabled = enable, musicEnabled = enable)
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(preferences = next),
        commandOutput = ProfileOutput.SettingsChanged(ProfileSettingsChanged(state.revision.next(), next)),
    )
}

private val PreferenceAdjustmentDirection.delta: Int
    get() = when (this) {
        PreferenceAdjustmentDirection.DECREASE -> -1
        PreferenceAdjustmentDirection.INCREASE -> 1
    }

private fun stepPercentage(
    value: Float,
    direction: PreferenceAdjustmentDirection,
    minimum: Float,
    maximum: Float,
): Float {
    val nextPercent = (value * 100f).roundToInt() + direction.delta
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}

private const val DEFAULT_SIMULATION_SPEED_INDEX: Int = 2

private const val DEFAULT_DAMAGE_THRESHOLD_INDEX: Int = 2
