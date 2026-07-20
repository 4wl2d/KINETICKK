// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.core.profile.api.DamageNumberFormat
import kinetickk.core.profile.api.DamageNumberSize
import kinetickk.core.profile.api.ParticleDensity
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.settings.api.SettingsOutput
import kinetickk.feature.settings.api.SettingsRenderModel
import kotlin.math.abs
import kotlin.math.roundToInt

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
    data class UpdatePreferences(val preferences: PlayerPreferences) : SettingsEffect
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
                val preferences = adjustPreferences(
                    preferences = state.model.preferences,
                    row = action.row,
                    direction = action.direction,
                )
                SettingsReduction(
                    state = state.copy(model = preferences.toRenderModel()),
                    effects = listOf(
                        SettingsEffect.UpdatePreferences(preferences),
                        SettingsEffect.Emit(SettingsOutput.Cue(AudioCue.UI_CLICK)),
                    ),
                )
            }
        }
        is SettingsAction.PageSelected -> SettingsReduction(
            state = state.copy(page = action.page.coerceAtLeast(0)),
            effects = listOf(SettingsEffect.Emit(SettingsOutput.Cue(AudioCue.UI_CLICK))),
        )
        SettingsAction.Back -> SettingsReduction(
            state = state,
            effects = listOf(
                SettingsEffect.Emit(SettingsOutput.Cue(AudioCue.UI_CLICK)),
                SettingsEffect.Emit(SettingsOutput.Back),
            ),
        )
    }
}

internal fun PlayerPreferences.toRenderModel(): SettingsRenderModel = SettingsRenderModel(
    preferences = normalized(),
)

private fun adjustPreferences(
    preferences: PlayerPreferences,
    row: SettingsRow,
    direction: Int,
): PlayerPreferences = when (row) {
    SettingsRow.SFX -> preferences.copy(soundEnabled = !preferences.soundEnabled)
    SettingsRow.MUSIC -> preferences.copy(musicEnabled = !preferences.musicEnabled)
    SettingsRow.MASTER_VOLUME -> preferences.copy(
        masterVolume = stepPercentage(preferences.masterVolume, direction, 0f, 1f),
    )
    SettingsRow.SIMULATION_SPEED -> {
        val current = SIMULATION_SPEEDS.indices.minByOrNull { index ->
            abs(SIMULATION_SPEEDS[index] - preferences.simulationSpeed)
        } ?: 2
        preferences.copy(
            simulationSpeed = SIMULATION_SPEEDS[
                (current + direction).coerceIn(SIMULATION_SPEEDS.indices)
            ],
        )
    }
    SettingsRow.TEXT_SIZE -> preferences.copy(
        textScale = stepPercentage(preferences.textScale, direction, 1f, 1.75f),
    )
    SettingsRow.SCREEN_SHAKE -> preferences.copy(screenShake = !preferences.screenShake)
    SettingsRow.PARTICLES -> {
        val next = (preferences.particleDensity.ordinal + direction)
            .coerceIn(ParticleDensity.entries.indices)
        preferences.copy(particleDensity = ParticleDensity.entries[next])
    }
    SettingsRow.DAMAGE_NUMBERS -> preferences.copy(damageNumbers = !preferences.damageNumbers)
    SettingsRow.DAMAGE_NUMBER_SIZE -> {
        val next = (preferences.damageNumberSize.ordinal + direction)
            .coerceIn(DamageNumberSize.entries.indices)
        preferences.copy(damageNumberSize = DamageNumberSize.entries[next])
    }
    SettingsRow.DAMAGE_NUMBER_FORMAT -> {
        val next = (preferences.damageNumberFormat.ordinal + direction)
            .coerceIn(DamageNumberFormat.entries.indices)
        preferences.copy(damageNumberFormat = DamageNumberFormat.entries[next])
    }
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val current = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
            abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - preferences.damageNumberTierThreshold)
        } ?: 2
        val next = (current + direction).coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
        preferences.copy(damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[next])
    }
}.normalized()

private fun stepPercentage(
    value: Float,
    direction: Int,
    minimum: Float,
    maximum: Float,
): Float {
    val nextPercent = (value * 100f).roundToInt() + direction.coerceIn(-1, 1)
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}

private val SIMULATION_SPEEDS = listOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)
