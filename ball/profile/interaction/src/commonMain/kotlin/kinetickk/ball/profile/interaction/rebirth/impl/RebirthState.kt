// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthBuildModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthTierStatus
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal sealed interface RebirthAction {
    /** Shows [tier]'s details (a ladder cell press, or keyboard focus on it). */
    data class FocusTier(val tier: Int) : RebirthAction

    /** Moves the focus one tier left (-1) or right (+1). */
    data class StepFocus(val delta: Int) : RebirthAction

    /** Starts a run at the focused tier (two presses for an uncleared one). */
    data object SelectRequested : RebirthAction
    data object Back : RebirthAction
}

/** The screen's state: the profile-backed [model] (its focus is presentation-only) and the shell's arm flag. */
internal data class RebirthState(
    val model: RebirthRenderModel,
    val confirmationArmed: Boolean,
)

internal sealed interface RebirthEffect {
    data class PlayAudio(val cue: ProfileAudioCue) : RebirthEffect
    data class Emit(val output: RebirthOutput) : RebirthEffect

    /** The presentation focus moves to [tier]. */
    data class Focus(val tier: Int) : RebirthEffect
}

internal data class RebirthReduction(
    val state: RebirthState,
    val effects: List<RebirthEffect> = emptyList(),
)

internal object RebirthReducer {
    fun reduce(state: RebirthState, action: RebirthAction): RebirthReduction = when (action) {
        is RebirthAction.FocusTier -> focus(state, action.tier)
        is RebirthAction.StepFocus -> focus(state, state.model.focus.tier + action.delta)
        RebirthAction.SelectRequested -> {
            val model = state.model
            val tier = model.focus.tier
            when {
                !model.canSelect -> RebirthReduction(state)
                model.needsConfirmation && !state.confirmationArmed -> RebirthReduction(
                    state = state,
                    effects = listOf(
                        RebirthEffect.Emit(RebirthOutput.ArmRequested(tier)),
                        RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                    ),
                )
                else -> RebirthReduction(state = state, effects = listOf(RebirthEffect.Emit(RebirthOutput.ConfirmRequested(tier))))
            }
        }
        RebirthAction.Back -> RebirthReduction(
            state = state,
            effects = listOf(
                RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                RebirthEffect.Emit(RebirthOutput.Back),
            ),
        )
    }

    private fun focus(state: RebirthState, requested: Int): RebirthReduction {
        val tier = requested.coerceIn(state.model.minimumTier, state.model.maximumTier)
        if (tier == state.model.focus.tier) return RebirthReduction(state)
        return RebirthReduction(
            state = state,
            effects = listOf(RebirthEffect.Focus(tier), RebirthEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
        )
    }
}

/**
 * The tier the ladder opens on: the earned, not yet cleared tier when one waits above the selected
 * tier (the next challenge), otherwise the selected tier.
 */
internal fun RebirthProgressProjection.defaultFocusTier(rebirthPolicy: RebirthPolicySnapshot): Int {
    val progress = snapshot.progress
    val level = progress.level.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    val rank = (progress.highestCleared + 1).coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    return if (rank > level && rank > progress.highestCleared) rank else level
}

internal fun RebirthProgressProjection.toRenderModel(
    rebirthPolicy: RebirthPolicySnapshot,
    eligible: Boolean = true,
    matter: Long = 0L,
    build: RebirthBuildContent? = null,
    heirloomIds: List<RelicId> = emptyList(),
    focusTier: Int = defaultFocusTier(rebirthPolicy),
): RebirthRenderModel {
    val progress = snapshot.progress
    val level = progress.level.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    val focus = focusTier.coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
    return RebirthRenderModel(
        current = rebirthPolicy.profile(level),
        focus = rebirthPolicy.profile(focus),
        highestCleared = progress.highestCleared.coerceIn(-1, rebirthPolicy.maximumLevel),
        eligible = eligible,
        matter = matter,
        minimumTier = rebirthPolicy.minimumLevel,
        maximumTier = rebirthPolicy.maximumLevel,
        build = build?.model(level, focus, heirloomIds),
    )
}

/**
 * The catalog content the Rebirth ladder reads, prepared once: every Rebirth-gated weapon, character,
 * Relic, item, Matrix node and enemy type, the Relic lookup for stored Heirlooms and the Reborn card
 * odds of the policy.
 */
internal class RebirthBuildContent(
    relics: List<RelicDefinition>,
    items: List<ItemDefinition>,
    private val policy: BuildPolicy,
    weapons: List<WeaponDefinition> = emptyList(),
    coreShapes: List<CoreShapeDefinition> = emptyList(),
    matrixNodes: List<MatrixNodeDefinition> = emptyList(),
    enemies: List<EnemyDefinition> = emptyList(),
) {
    private val relicsById: Map<RelicId, RelicDefinition> = relics.associateBy(RelicDefinition::id)

    val unlocks: ImmutableList<RebirthUnlock> = rebirthUnlocks(relics, items, weapons, coreShapes, matrixNodes, enemies)

    /**
     * The build model between the selected [currentTier] and the [focusTier]. Heirlooms keep the
     * profile's stored order; ids the catalog no longer knows and repeats are dropped, and at most one
     * per Relic slot shows.
     */
    fun model(currentTier: Int, focusTier: Int, heirloomIds: List<RelicId>): RebirthBuildModel = RebirthBuildModel(
        currentRebornChance = policy.rebornChance(currentTier),
        nextRebornChance = policy.rebornChance(focusTier),
        unlocks = unlocks,
        heirlooms = heirloomIds.distinct().mapNotNull(relicsById::get).take(MAX_HEIRLOOM_RELICS).toImmutableList(),
    )
}

/**
 * Rebirth-gated content in ladder order: by tier, then weapons, characters, Relics, items, Matrix nodes
 * and enemies, each in catalog order.
 */
internal fun rebirthUnlocks(
    relics: List<RelicDefinition>,
    items: List<ItemDefinition>,
    weapons: List<WeaponDefinition> = emptyList(),
    coreShapes: List<CoreShapeDefinition> = emptyList(),
    matrixNodes: List<MatrixNodeDefinition> = emptyList(),
    enemies: List<EnemyDefinition> = emptyList(),
): ImmutableList<RebirthUnlock> = buildList<RebirthUnlock> {
    weapons.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Weapon(it) }
    coreShapes.filter { it.unlockRequirement == CharacterUnlockRequirement.REBIRTH_RANK && it.unlockTarget > 0 }
        .mapTo(this) { RebirthUnlock.Character(it) }
    relics.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Relic(it) }
    items.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Item(it) }
    matrixNodes.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.MatrixNode(it) }
    enemies.filter { it.minimumRebirth > 0 }.mapTo(this) { RebirthUnlock.Enemy(it) }
}.sortedBy(RebirthUnlock::tier).toImmutableList()

/** Ladder cell look: the record ([RebirthTierStatus]) plus whether it is the selected tier. */
internal fun RebirthRenderModel.tierStatus(tier: Int): RebirthTierStatus = status(tier)

/** Select button state: the two-press confirm for an uncleared tier, a locked tier, a closed shell. */
internal enum class RebirthActionState { READY, ARMED, LOCKED, UNAVAILABLE }

internal fun RebirthRenderModel.actionState(confirmationArmed: Boolean): RebirthActionState = when {
    focus.tier > rank -> RebirthActionState.LOCKED
    !eligible -> RebirthActionState.UNAVAILABLE
    needsConfirmation && confirmationArmed -> RebirthActionState.ARMED
    else -> RebirthActionState.READY
}

/** A comparison row: which way the value moves from the selected to the focused tier. */
internal enum class RebirthChange { SAME, UP, DOWN }

internal fun rebirthChange(current: Float, next: Float): RebirthChange = when {
    next > current + 0.0001f -> RebirthChange.UP
    next < current - 0.0001f -> RebirthChange.DOWN
    else -> RebirthChange.SAME
}
