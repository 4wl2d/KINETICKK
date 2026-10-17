// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthBuildModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

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
                    RebirthEffect.Emit(RebirthOutput.ArmRequested(state.model.targetTier)),
                    RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                ),
            )
            else -> RebirthReduction(
                state = state,
                effects = listOf(RebirthEffect.Emit(RebirthOutput.ConfirmRequested(state.model.targetTier))),
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
    build: RebirthBuildContent? = null,
    heirloomIds: List<RelicId> = emptyList(),
): RebirthRenderModel = rebirthRenderModel(
    rebirthPolicy = rebirthPolicy,
    level = snapshot.progress.level,
    canAdvance = eligible && canAdvance,
    matter = matter,
    build = build,
    heirloomIds = heirloomIds,
)

private fun rebirthRenderModel(
    rebirthPolicy: RebirthPolicySnapshot,
    level: Int,
    canAdvance: Boolean,
    matter: Long,
    build: RebirthBuildContent?,
    heirloomIds: List<RelicId>,
): RebirthRenderModel {
    val normalizedLevel = level.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    val targetLevel = (normalizedLevel + 1).coerceAtMost(rebirthPolicy.maximumLevel)
    return RebirthRenderModel(
        current = rebirthPolicy.profile(normalizedLevel),
        next = rebirthPolicy.profile(normalizedLevel + 1),
        canAdvance = canAdvance && normalizedLevel < rebirthPolicy.maximumLevel,
        matter = matter,
        minimumTier = rebirthPolicy.minimumLevel,
        maximumTier = rebirthPolicy.maximumLevel,
        build = build?.model(normalizedLevel, targetLevel, heirloomIds),
    )
}

/**
 * The catalog content the Rebirth screen reads for builds, prepared once: every Rebirth-gated
 * Relic and item, the Relic lookup for stored Heirlooms and the Reborn card odds of the policy.
 */
internal class RebirthBuildContent(
    relics: List<RelicDefinition>,
    items: List<ItemDefinition>,
    private val policy: BuildPolicy,
) {
    private val relicsById: Map<RelicId, RelicDefinition> = relics.associateBy(RelicDefinition::id)

    val unlocks: ImmutableList<RebirthUnlock> = rebirthUnlocks(relics, items)

    /**
     * The build model between [currentTier] and [targetTier]. Heirlooms keep the profile's stored order;
     * ids the catalog no longer knows and repeats are dropped, and at most one per Relic slot shows.
     */
    fun model(currentTier: Int, targetTier: Int, heirloomIds: List<RelicId>): RebirthBuildModel = RebirthBuildModel(
        currentRebornChance = policy.rebornChance(currentTier),
        nextRebornChance = policy.rebornChance(targetTier),
        unlocks = unlocks,
        heirlooms = heirloomIds.distinct().mapNotNull(relicsById::get).take(MAX_HEIRLOOM_RELICS).toImmutableList(),
    )
}

/** Rebirth-gated content in unlock order: by tier, Relics before items, then catalog order. */
internal fun rebirthUnlocks(relics: List<RelicDefinition>, items: List<ItemDefinition>): ImmutableList<RebirthUnlock> =
    buildList<RebirthUnlock> {
        relics.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Relic(it) }
        items.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Item(it) }
    }.sortedBy(RebirthUnlock::tier).toImmutableList()

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
