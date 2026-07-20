// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.RebirthProgression
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.feature.rebirth.api.RebirthOutput
import kinetickk.feature.rebirth.api.RebirthRenderModel

internal sealed interface RebirthAction {
    data object AdvanceRequested : RebirthAction
    data object Back : RebirthAction
}

internal data class RebirthState(
    val model: RebirthRenderModel,
    val armed: Boolean,
)

internal sealed interface RebirthEffect {
    data object AdvanceCycle : RebirthEffect
    data class Emit(val output: RebirthOutput) : RebirthEffect
}

internal data class RebirthReduction(
    val state: RebirthState,
    val effects: List<RebirthEffect> = emptyList(),
)

internal object RebirthReducer {
    fun reduce(state: RebirthState, action: RebirthAction): RebirthReduction = when (action) {
        RebirthAction.AdvanceRequested -> when {
            !state.model.canAdvance || state.model.isMaximumTier -> RebirthReduction(state)
            !state.armed -> RebirthReduction(
                state = state.copy(armed = true),
                effects = listOf(
                    RebirthEffect.Emit(RebirthOutput.Cue(AudioCue.UI_CLICK)),
                ),
            )
            else -> RebirthReduction(
                state = state,
                effects = listOf(RebirthEffect.AdvanceCycle),
            )
        }
        RebirthAction.Back -> RebirthReduction(
            state = state.copy(armed = false),
            effects = listOf(
                RebirthEffect.Emit(RebirthOutput.Cue(AudioCue.UI_CLICK)),
                RebirthEffect.Emit(RebirthOutput.Back),
            ),
        )
    }
}

internal fun RebirthProfileSnapshot.toRenderModel(eligible: Boolean = true): RebirthRenderModel = rebirthRenderModel(
    level = progress.level,
    highestCleared = progress.highestCleared,
    eligible = eligible,
)

private fun rebirthRenderModel(level: Int, highestCleared: Int, eligible: Boolean): RebirthRenderModel {
    val normalizedLevel = level.coerceIn(0, RebirthProgression.MAX_LEVEL)
    return RebirthRenderModel(
        current = RebirthProgression.profile(normalizedLevel),
        next = RebirthProgression.profile(normalizedLevel + 1),
        canAdvance = eligible &&
            normalizedLevel < RebirthProgression.MAX_LEVEL &&
            highestCleared.coerceIn(-1, normalizedLevel) >= normalizedLevel,
    )
}
