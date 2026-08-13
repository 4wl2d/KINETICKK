// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.lab.api.LabRenderModel
import kinetickk.ball.profile.interaction.lab.api.LabUpgradeRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal sealed interface LabAction {
    data class PurchaseRequested(val id: MetaUpgradeId) : LabAction
    data object Back : LabAction
}

internal data class LabState(
    val model: LabRenderModel,
)

internal sealed interface LabEffect {
    data class Purchase(val id: MetaUpgradeId) : LabEffect
    data class PlayAudio(val cue: ProfileAudioCue) : LabEffect
    data class Emit(val output: LabOutput) : LabEffect
}

internal data class LabReduction(
    val state: LabState,
    val effects: List<LabEffect> = emptyList(),
)

internal object LabReducer {
    fun reduce(state: LabState, action: LabAction): LabReduction = when (action) {
        is LabAction.PurchaseRequested -> LabReduction(
            state = state,
            // Affordability and rank limits depend on canonical Profile state and belong to its Nucleus.
            effects = listOf(LabEffect.Purchase(action.id)),
        )
        LabAction.Back -> LabReduction(
            state = state,
            effects = listOf(
                LabEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                LabEffect.Emit(LabOutput.Back),
            ),
        )
    }
}

internal fun LabProfileSnapshot.toRenderModel(
    metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
): LabRenderModel = labRenderModel(
    metaUpgrades = metaUpgrades,
    matter = economy.matter,
    rank = progress::rank,
)

private fun labRenderModel(
    metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
    matter: Long,
    rank: (MetaUpgradeId) -> Int,
): LabRenderModel = LabRenderModel(
    matter = matter,
    upgrades = metaUpgrades.map { definition ->
        val currentRank = rank(definition.id).coerceIn(0, definition.maxRanks)
        val maxed = currentRank >= definition.maxRanks
        val cost = if (maxed) 0L else definition.cost(currentRank).toLong()
        LabUpgradeRenderModel(
            id = definition.id,
            name = definition.name,
            description = definition.description,
            rank = currentRank,
            maxRanks = definition.maxRanks,
            nextCost = cost,
            isMaxed = maxed,
            isAffordable = !maxed && matter >= cost,
        )
    }.toImmutableList(),
)
