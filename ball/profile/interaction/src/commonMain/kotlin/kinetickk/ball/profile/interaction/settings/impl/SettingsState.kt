// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel

internal enum class SettingsRow {
    LANGUAGE,
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
    RUN_STATISTICS_SIDE,
}

internal enum class SettingsGroup(val label: ProfileText, val rows: List<SettingsRow>) {
    GAME(ProfileText.SettingsGame, listOf(SettingsRow.LANGUAGE, SettingsRow.SIMULATION_SPEED)),
    SOUND(ProfileText.SettingsSound, listOf(SettingsRow.SFX, SettingsRow.MUSIC, SettingsRow.MASTER_VOLUME)),
    GRAPHICS(ProfileText.SettingsGraphics, listOf(
        SettingsRow.SCREEN_SHAKE, SettingsRow.PARTICLES, SettingsRow.DAMAGE_NUMBERS,
        SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT, SettingsRow.DAMAGE_COLOR_THRESHOLDS,
    )),
    INTERFACE(ProfileText.SettingsInterface, listOf(SettingsRow.TEXT_SIZE, SettingsRow.RUN_STATISTICS_SIDE)),
}

internal sealed interface SettingsAction {
    data class SelectGroup(val group: SettingsGroup) : SettingsAction
    data class SelectLanguage(val language: AppLanguage) : SettingsAction
    data class SetMasterVolume(val percent: Int) : SettingsAction
    data class Adjust(val row: SettingsRow, val direction: Int) : SettingsAction
    data class PageSelected(val page: Int) : SettingsAction
    data object Back : SettingsAction
}

internal data class SettingsState(
    val model: SettingsRenderModel,
    val page: Int,
    val group: SettingsGroup = SettingsGroup.GAME,
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
        is SettingsAction.SelectGroup -> if (state.group == action.group) {
            SettingsReduction(state)
        } else {
            SettingsReduction(
                state = state.copy(group = action.group, page = 0),
                effects = listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            )
        }
        is SettingsAction.SelectLanguage -> if (state.model.preferences.language == action.language) {
            SettingsReduction(state)
        } else {
            SettingsReduction(state, listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetLanguage(action.language)),
                SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
            ))
        }
        is SettingsAction.SetMasterVolume -> if (action.percent !in 0..100 ||
            state.model.preferences.masterVolume == action.percent / 100f
        ) {
            SettingsReduction(state)
        } else {
            SettingsReduction(state, listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetMasterVolume(action.percent)),
            ))
        }
        is SettingsAction.Adjust -> {
            if (action.row == SettingsRow.LANGUAGE || (action.direction != -1 && action.direction != 1)) {
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
        SettingsRow.LANGUAGE -> error("Language selection uses a typed SelectLanguage action")
        SettingsRow.SFX -> ProfilePreferenceAdjustment.ToggleSoundEffects
        SettingsRow.MUSIC -> ProfilePreferenceAdjustment.ToggleMusic
        SettingsRow.MASTER_VOLUME -> ProfilePreferenceAdjustment.StepMasterVolume(adjustmentDirection)
        SettingsRow.SIMULATION_SPEED -> ProfilePreferenceAdjustment.StepSimulationSpeed(adjustmentDirection)
        SettingsRow.TEXT_SIZE -> ProfilePreferenceAdjustment.StepTextScale(adjustmentDirection)
        SettingsRow.SCREEN_SHAKE -> ProfilePreferenceAdjustment.ToggleScreenShake
        SettingsRow.RUN_STATISTICS_SIDE -> ProfilePreferenceAdjustment.ToggleRunStatisticsSide
        SettingsRow.PARTICLES -> ProfilePreferenceAdjustment.StepParticleDensity(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBERS -> ProfilePreferenceAdjustment.ToggleDamageNumbers
        SettingsRow.DAMAGE_NUMBER_SIZE -> ProfilePreferenceAdjustment.StepDamageNumberSize(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBER_FORMAT -> ProfilePreferenceAdjustment.StepDamageNumberFormat(adjustmentDirection)
        SettingsRow.DAMAGE_COLOR_THRESHOLDS ->
            ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(adjustmentDirection)
    }
}
