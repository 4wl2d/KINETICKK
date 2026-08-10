// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel

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
    data class PlayAudio(val cue: ProfileAudioCue) : RebirthEffect
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
                    RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
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
                RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                RebirthEffect.Emit(RebirthOutput.Back),
            ),
        )
    }
}

internal fun RebirthProgressProjection.toRenderModel(
    rebirthPolicy: RebirthPolicySnapshot,
    eligible: Boolean = true,
): RebirthRenderModel = rebirthRenderModel(
    rebirthPolicy = rebirthPolicy,
    level = snapshot.progress.level,
    canAdvance = eligible && canAdvance,
)

private fun rebirthRenderModel(
    rebirthPolicy: RebirthPolicySnapshot,
    level: Int,
    canAdvance: Boolean,
): RebirthRenderModel {
    val normalizedLevel = level.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    return RebirthRenderModel(
        current = rebirthPolicy.profile(normalizedLevel),
        next = rebirthPolicy.profile(normalizedLevel + 1),
        canAdvance = canAdvance && normalizedLevel < rebirthPolicy.maximumLevel,
    )
}
