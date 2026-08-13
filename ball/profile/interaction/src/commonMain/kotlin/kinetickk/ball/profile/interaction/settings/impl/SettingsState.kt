// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel

internal enum class SettingsRow {
    SFX,
    MUSIC,
    MASTER_VOLUME,
    SIMULATION_SPEED,
    TEXT_SIZE,
    SCREEN_SHAKE,
    PARTICLES,
    DAMAGE_NUMBERS,
    DAMAGE_NUMBER_SIZE,
    DAMAGE_NUMBER_FORMAT,
    DAMAGE_COLOR_THRESHOLDS,
}

internal sealed interface SettingsAction {
    data class Adjust(val row: SettingsRow, val direction: Int) : SettingsAction
    data class PageSelected(val page: Int) : SettingsAction
    data object Back : SettingsAction
}

internal data class SettingsState(
    val model: SettingsRenderModel,
    val page: Int,
)

internal sealed interface SettingsEffect {
    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : SettingsEffect

    data class PlayAudio(val cue: ProfileAudioCue) : SettingsEffect
    data class Emit(val output: SettingsOutput) : SettingsEffect
}

internal data class SettingsReduction(
    val state: SettingsState,
    val effects: List<SettingsEffect> = emptyList(),
)

internal object SettingsReducer {
    fun reduce(state: SettingsState, action: SettingsAction): SettingsReduction = when (action) {
        is SettingsAction.Adjust -> {
            if (action.direction != -1 && action.direction != 1) {
                SettingsReduction(state)
            } else {
                SettingsReduction(
                    state = state,
                    effects = listOf(
                        SettingsEffect.AdjustPreference(
                            adjustment = action.row.toAdjustment(action.direction),
                        ),
                        SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                    ),
                )
            }
        }
        is SettingsAction.PageSelected -> SettingsReduction(
            state = state.copy(page = action.page.coerceAtLeast(0)),
            effects = listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
        )
        SettingsAction.Back -> SettingsReduction(
            state = state,
            effects = listOf(
                SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                SettingsEffect.Emit(SettingsOutput.Back),
            ),
        )
    }
}

internal fun PlayerPreferences.toRenderModel(): SettingsRenderModel = SettingsRenderModel(
    preferences = normalized(),
)

private fun SettingsRow.toAdjustment(direction: Int): ProfilePreferenceAdjustment {
    val adjustmentDirection = if (direction < 0) {
        PreferenceAdjustmentDirection.DECREASE
    } else {
        PreferenceAdjustmentDirection.INCREASE
    }
    return when (this) {
        SettingsRow.SFX -> ProfilePreferenceAdjustment.ToggleSoundEffects
        SettingsRow.MUSIC -> ProfilePreferenceAdjustment.ToggleMusic
        SettingsRow.MASTER_VOLUME -> ProfilePreferenceAdjustment.StepMasterVolume(adjustmentDirection)
        SettingsRow.SIMULATION_SPEED -> ProfilePreferenceAdjustment.StepSimulationSpeed(adjustmentDirection)
        SettingsRow.TEXT_SIZE -> ProfilePreferenceAdjustment.StepTextScale(adjustmentDirection)
        SettingsRow.SCREEN_SHAKE -> ProfilePreferenceAdjustment.ToggleScreenShake
        SettingsRow.PARTICLES -> ProfilePreferenceAdjustment.StepParticleDensity(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBERS -> ProfilePreferenceAdjustment.ToggleDamageNumbers
        SettingsRow.DAMAGE_NUMBER_SIZE -> ProfilePreferenceAdjustment.StepDamageNumberSize(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBER_FORMAT -> ProfilePreferenceAdjustment.StepDamageNumberFormat(adjustmentDirection)
        SettingsRow.DAMAGE_COLOR_THRESHOLDS ->
            ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(adjustmentDirection)
    }
}
