// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.formatMultiplier
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel
import kotlin.math.abs
import kotlin.math.roundToInt

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
    COLOR_VISION,
}

/** The three row controls of the design: segmented choice, toggle and slider with −/+ steppers. */
internal enum class SettingsControl { SEGMENTED, TOGGLE, SLIDER }

internal val SettingsRow.control: SettingsControl
    get() = when (this) {
        SettingsRow.LANGUAGE,
        SettingsRow.SIMULATION_SPEED,
        SettingsRow.PARTICLES,
        SettingsRow.DAMAGE_NUMBER_SIZE,
        SettingsRow.DAMAGE_NUMBER_FORMAT,
        SettingsRow.RUN_STATISTICS_SIDE,
        SettingsRow.COLOR_VISION,
        -> SettingsControl.SEGMENTED
        SettingsRow.SFX,
        SettingsRow.MUSIC,
        SettingsRow.SCREEN_SHAKE,
        SettingsRow.DAMAGE_NUMBERS,
        -> SettingsControl.TOGGLE
        SettingsRow.MASTER_VOLUME,
        SettingsRow.TEXT_SIZE,
        SettingsRow.DAMAGE_COLOR_THRESHOLDS,
        -> SettingsControl.SLIDER
    }

/** Stable test-tag segment of a row (`kinetickk.settings.<id>.…`). */
internal val SettingsRow.tagId: String
    get() = if (this == SettingsRow.COLOR_VISION) "colorvision" else name.lowercase()

internal enum class SettingsGroup(val label: ProfileText, val rows: List<SettingsRow>) {
    GAME(ProfileText.SettingsGame, listOf(SettingsRow.LANGUAGE, SettingsRow.SIMULATION_SPEED)),
    SOUND(ProfileText.SettingsSound, listOf(SettingsRow.SFX, SettingsRow.MUSIC, SettingsRow.MASTER_VOLUME)),
    GRAPHICS(ProfileText.SettingsGraphics, listOf(
        SettingsRow.COLOR_VISION, SettingsRow.SCREEN_SHAKE, SettingsRow.PARTICLES, SettingsRow.DAMAGE_NUMBERS,
        SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT, SettingsRow.DAMAGE_COLOR_THRESHOLDS,
    )),
    INTERFACE(ProfileText.SettingsInterface, listOf(SettingsRow.TEXT_SIZE, SettingsRow.RUN_STATISTICS_SIDE)),
}

internal sealed interface SettingsAction {
    data class SelectGroup(val group: SettingsGroup) : SettingsAction
    data class SelectLanguage(val language: AppLanguage) : SettingsAction
    /** Direct choice of a segmented row's [option] (see [optionCount]). */
    data class Select(val row: SettingsRow, val option: Int) : SettingsAction
    data class SetMasterVolume(val percent: Int) : SettingsAction
    /** Toggles a toggle row, or steps a slider row by one step in [direction] (−1 or 1). */
    data class Adjust(val row: SettingsRow, val direction: Int) : SettingsAction
    /** Opens the row's (!) explanation, or closes it when it is already open. */
    data class ToggleInfo(val row: SettingsRow) : SettingsAction
    /** A press on the open explanation's slip: closes it and changes no preference. */
    data object CloseInfo : SettingsAction
    data class PageSelected(val page: Int) : SettingsAction
    data object Back : SettingsAction
}

internal data class SettingsState(
    val model: SettingsRenderModel,
    val page: Int,
    val group: SettingsGroup = SettingsGroup.GAME,
    /** Row whose (!) explanation was opened by a press; hover and keyboard focus open it transiently. */
    val info: SettingsRow? = null,
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
                state = state.copy(group = action.group, page = 0, info = null),
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
        is SettingsAction.Select -> {
            val preferences = state.model.preferences
            if (action.row.control != SettingsControl.SEGMENTED || action.option !in 0 until action.row.optionCount() ||
                action.option == action.row.selectedOption(preferences)
            ) {
                SettingsReduction(state)
            } else {
                SettingsReduction(state, listOf(
                    SettingsEffect.AdjustPreference(action.row.choiceAdjustment(action.option)),
                    SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                ))
            }
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
            if (action.row == SettingsRow.LANGUAGE || action.row == SettingsRow.COLOR_VISION ||
                (action.direction != -1 && action.direction != 1)
            ) {
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
        is SettingsAction.ToggleInfo -> SettingsReduction(
            state.copy(info = if (state.info == action.row) null else action.row),
        )
        SettingsAction.CloseInfo -> SettingsReduction(state.copy(info = null))
        is SettingsAction.PageSelected -> SettingsReduction(
            state = state.copy(page = action.page.coerceAtLeast(0), info = null),
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

/** Number of choices of a segmented row; 0 for toggles and sliders. */
internal fun SettingsRow.optionCount(): Int = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries.size
    SettingsRow.SIMULATION_SPEED -> SIMULATION_SPEED_OPTIONS.size
    SettingsRow.PARTICLES -> ParticleDensity.entries.size
    SettingsRow.DAMAGE_NUMBER_SIZE -> DamageNumberSize.entries.size
    SettingsRow.DAMAGE_NUMBER_FORMAT -> DamageNumberFormat.entries.size
    SettingsRow.RUN_STATISTICS_SIDE -> 2
    SettingsRow.COLOR_VISION -> ColorVision.entries.size
    else -> 0
}

/** Index of the accepted choice of a segmented row (the listed speed nearest to the stored one). */
internal fun SettingsRow.selectedOption(preferences: PlayerPreferences): Int = when (this) {
    SettingsRow.LANGUAGE -> preferences.language.ordinal
    SettingsRow.SIMULATION_SPEED -> SIMULATION_SPEED_OPTIONS.indices.minByOrNull { index ->
        abs(SIMULATION_SPEED_OPTIONS[index] - preferences.simulationSpeed)
    } ?: 0
    SettingsRow.PARTICLES -> preferences.particleDensity.ordinal
    SettingsRow.DAMAGE_NUMBER_SIZE -> preferences.damageNumberSize.ordinal
    SettingsRow.DAMAGE_NUMBER_FORMAT -> preferences.damageNumberFormat.ordinal
    SettingsRow.RUN_STATISTICS_SIDE -> if (preferences.runStatisticsOnLeft) 0 else 1
    SettingsRow.COLOR_VISION -> preferences.colorVision.ordinal
    else -> -1
}

/** Visible label of a segmented choice (uppercase is applied by the text style). */
internal fun SettingsRow.optionLabel(option: Int, language: AppLanguage): String = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries[option].nativeName
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(SIMULATION_SPEED_OPTIONS[option], language).replace('x', '×')
    SettingsRow.PARTICLES -> language.text(when (ParticleDensity.entries[option]) {
        ParticleDensity.LOW -> ProfileText.Low
        ParticleDensity.NORMAL -> ProfileText.Normal
        ParticleDensity.HIGH -> ProfileText.High
    })
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(damageNumberSizeText(DamageNumberSize.entries[option]))
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(when (DamageNumberFormat.entries[option]) {
        DamageNumberFormat.COMPACT -> ProfileText.Compact
        DamageNumberFormat.FULL -> ProfileText.Full
    })
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(if (option == 0) ProfileText.LeftSide else ProfileText.RightSide)
    SettingsRow.COLOR_VISION -> language.text(colorVisionText(ColorVision.entries[option]))
    else -> error("$this has no segmented choices")
}

/** Stable test-tag suffix of a segmented choice (`kinetickk.settings.<row>.<option>`). */
internal fun SettingsRow.optionId(option: Int): String = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries[option].code
    SettingsRow.SIMULATION_SPEED -> (SIMULATION_SPEED_OPTIONS[option] * 100f).roundToInt().toString()
    SettingsRow.PARTICLES -> ParticleDensity.entries[option].name.lowercase()
    SettingsRow.DAMAGE_NUMBER_SIZE -> DamageNumberSize.entries[option].name.lowercase()
    SettingsRow.DAMAGE_NUMBER_FORMAT -> DamageNumberFormat.entries[option].name.lowercase()
    SettingsRow.RUN_STATISTICS_SIDE -> if (option == 0) "left" else "right"
    SettingsRow.COLOR_VISION -> ColorVision.entries[option].name.lowercase()
    else -> error("$this has no segmented choices")
}

internal fun colorVisionText(colorVision: ColorVision): SettingsRedesignText = when (colorVision) {
    ColorVision.DEFAULT -> SettingsRedesignText.ColorVisionDefault
    ColorVision.PROTAN -> SettingsRedesignText.ColorVisionProtan
    ColorVision.DEUTAN -> SettingsRedesignText.ColorVisionDeutan
    ColorVision.TRITAN -> SettingsRedesignText.ColorVisionTritan
    ColorVision.MONO -> SettingsRedesignText.ColorVisionMono
}

internal fun damageNumberSizeText(size: DamageNumberSize): ProfileText = when (size) {
    DamageNumberSize.SMALL -> ProfileText.Small
    DamageNumberSize.NORMAL -> ProfileText.Normal
    DamageNumberSize.LARGE -> ProfileText.Large
    DamageNumberSize.HUGE -> ProfileText.Huge
}

private fun SettingsRow.choiceAdjustment(option: Int): ProfilePreferenceAdjustment = when (this) {
    SettingsRow.LANGUAGE -> ProfilePreferenceAdjustment.SetLanguage(AppLanguage.entries[option])
    SettingsRow.SIMULATION_SPEED -> ProfilePreferenceAdjustment.SetSimulationSpeed(SIMULATION_SPEED_OPTIONS[option])
    SettingsRow.PARTICLES -> ProfilePreferenceAdjustment.SetParticleDensity(ParticleDensity.entries[option])
    SettingsRow.DAMAGE_NUMBER_SIZE -> ProfilePreferenceAdjustment.SetDamageNumberSize(DamageNumberSize.entries[option])
    SettingsRow.DAMAGE_NUMBER_FORMAT -> ProfilePreferenceAdjustment.SetDamageNumberFormat(DamageNumberFormat.entries[option])
    // Only reached for the side that is not selected, so one toggle lands exactly on it.
    SettingsRow.RUN_STATISTICS_SIDE -> ProfilePreferenceAdjustment.ToggleRunStatisticsSide
    SettingsRow.COLOR_VISION -> ProfilePreferenceAdjustment.SetColorVision(ColorVision.entries[option])
    else -> error("$this has no segmented choices")
}

private fun SettingsRow.toAdjustment(direction: Int): ProfilePreferenceAdjustment {
    val adjustmentDirection = if (direction < 0) {
        PreferenceAdjustmentDirection.DECREASE
    } else {
        PreferenceAdjustmentDirection.INCREASE
    }
    return when (this) {
        SettingsRow.LANGUAGE -> error("Language selection uses a typed SelectLanguage action")
        SettingsRow.COLOR_VISION -> error("Color vision uses a typed Select action")
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
