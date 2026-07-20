// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.toImmutableList
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.profile.api.LabProfileSnapshot
import kinetickk.feature.lab.api.LabOutput
import kinetickk.feature.lab.api.LabRenderModel
import kinetickk.feature.lab.api.LabUpgradeRenderModel

internal sealed interface LabAction {
    data class PurchaseRequested(val id: MetaUpgradeId) : LabAction
    data object Back : LabAction
}

internal data class LabState(
    val model: LabRenderModel,
)

internal sealed interface LabEffect {
    data class Purchase(val id: MetaUpgradeId) : LabEffect
    data class Emit(val output: LabOutput) : LabEffect
}

internal data class LabReduction(
    val state: LabState,
    val effects: List<LabEffect> = emptyList(),
)

internal object LabReducer {
    fun reduce(state: LabState, action: LabAction): LabReduction = when (action) {
        is LabAction.PurchaseRequested -> {
            val upgrade = state.model.upgrades.firstOrNull { it.id == action.id }
            if (upgrade == null || upgrade.isMaxed || !upgrade.isAffordable) {
                LabReduction(state)
            } else {
                LabReduction(
                    state = state,
                    effects = listOf(LabEffect.Purchase(action.id)),
                )
            }
        }
        LabAction.Back -> LabReduction(
            state = state,
            effects = listOf(
                LabEffect.Emit(LabOutput.Cue(AudioCue.UI_CLICK)),
                LabEffect.Emit(LabOutput.Back),
            ),
        )
    }
}

internal fun LabProfileSnapshot.toRenderModel(): LabRenderModel = labRenderModel(
    matter = economy.matter,
    rank = progress::rank,
)

private fun labRenderModel(
    matter: Long,
    rank: (MetaUpgradeId) -> Int,
): LabRenderModel = LabRenderModel(
    matter = matter,
    upgrades = MetaUpgradeCatalog.all.map { definition ->
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
