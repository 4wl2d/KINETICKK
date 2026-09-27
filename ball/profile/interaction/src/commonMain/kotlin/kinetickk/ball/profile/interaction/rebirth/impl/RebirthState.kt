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
    val confirmationArmed: Boolean,
)

internal sealed interface RebirthEffect {
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
            !state.confirmationArmed -> RebirthReduction(
                state = state,
                effects = listOf(
                    RebirthEffect.Emit(RebirthOutput.ArmRequested),
                    RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                ),
            )
            else -> RebirthReduction(
                state = state,
                effects = listOf(RebirthEffect.Emit(RebirthOutput.ConfirmRequested)),
            )
        }
        RebirthAction.Back -> RebirthReduction(
            state = state,
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
    matter: Long = 0L,
): RebirthRenderModel = rebirthRenderModel(
    rebirthPolicy = rebirthPolicy,
    level = snapshot.progress.level,
    canAdvance = eligible && canAdvance,
    matter = matter,
)

private fun rebirthRenderModel(
    rebirthPolicy: RebirthPolicySnapshot,
    level: Int,
    canAdvance: Boolean,
    matter: Long,
): RebirthRenderModel {
    val normalizedLevel = level.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    return RebirthRenderModel(
        current = rebirthPolicy.profile(normalizedLevel),
        next = rebirthPolicy.profile(normalizedLevel + 1),
        canAdvance = canAdvance && normalizedLevel < rebirthPolicy.maximumLevel,
        matter = matter,
        minimumTier = rebirthPolicy.minimumLevel,
        maximumTier = rebirthPolicy.maximumLevel,
    )
}

/** Ladder cell states (`Rebirth.dc.html` ladder). */
internal enum class RebirthTierCell { CLEARED, CURRENT, NEXT, LATER }

internal fun RebirthRenderModel.tierCell(tier: Int): RebirthTierCell = when {
    tier < current.tier -> RebirthTierCell.CLEARED
    tier == current.tier -> RebirthTierCell.CURRENT
    tier == next.tier && !isMaximumTier -> RebirthTierCell.NEXT
    else -> RebirthTierCell.LATER
}

/** Advance button state: the game's two-press confirm plus its locked and final states. */
internal enum class RebirthActionState { READY, ARMED, LOCKED, MAXIMUM }

internal fun RebirthRenderModel.actionState(confirmationArmed: Boolean): RebirthActionState = when {
    isMaximumTier -> RebirthActionState.MAXIMUM
    !canAdvance -> RebirthActionState.LOCKED
    confirmationArmed -> RebirthActionState.ARMED
    else -> RebirthActionState.READY
}

/** A comparison row: which way the value moves from the current to the next tier. */
internal enum class RebirthChange { SAME, UP, DOWN }

internal fun rebirthChange(current: Float, next: Float): RebirthChange = when {
    next > current + 0.0001f -> RebirthChange.UP
    next < current - 0.0001f -> RebirthChange.DOWN
    else -> RebirthChange.SAME
}
