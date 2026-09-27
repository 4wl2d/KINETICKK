// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.lab.api.LabRenderModel
import kinetickk.ball.profile.interaction.lab.api.LabUpgradeRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.abs
import kotlin.math.roundToLong

internal sealed interface LabAction {
    /** The detail panel's Buy rank button for [id]. */
    data class PurchaseRequested(val id: MetaUpgradeId) : LabAction

    /** Hover, keyboard focus or a first tap moves the selection. */
    data class Select(val id: MetaUpgradeId) : LabAction

    /** Row activation: a press on the selected row buys its next rank, otherwise it selects. */
    data class Activate(val id: MetaUpgradeId) : LabAction

    data object Back : LabAction
}

/**
 * Lab presentation state: the Profile-backed [model], the selected row ([selected] null = the
 * first row) and the purchase feedback [flash] (row + a counter that restarts the animation).
 */
internal data class LabState(
    val model: LabRenderModel,
    val selected: MetaUpgradeId? = null,
    val flash: LabPurchaseFlash? = null,
) {
    val selectedUpgrade: LabUpgradeRenderModel?
        get() = model.upgrades.firstOrNull { it.id == selected } ?: model.upgrades.firstOrNull()
}

/** Purchase feedback (`lab-buy`): the row inverts for one frame and nudges 8 px (Pull). */
internal data class LabPurchaseFlash(val id: MetaUpgradeId, val sequence: Int)

internal const val LAB_PURCHASE_FLASH_MS = 500

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
            state = state.copy(selected = action.id),
            // Affordability and rank limits depend on canonical Profile state and belong to its Nucleus.
            effects = listOf(LabEffect.Purchase(action.id)),
        )
        is LabAction.Select -> LabReduction(state.copy(selected = action.id))
        is LabAction.Activate -> if (state.selectedUpgrade?.id == action.id) {
            reduce(state, LabAction.PurchaseRequested(action.id))
        } else {
            LabReduction(state.copy(selected = action.id))
        }
        LabAction.Back -> LabReduction(
            state = state,
            effects = listOf(
                LabEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                LabEffect.Emit(LabOutput.Back),
            ),
        )
    }

    /** Applies the Profile's answer to a purchase: a new model, and the flash when accepted. */
    fun purchased(state: LabState, id: MetaUpgradeId, model: LabRenderModel, accepted: Boolean): LabState = state.copy(
        model = model,
        flash = if (accepted) LabPurchaseFlash(id, (state.flash?.sequence ?: 0) + 1) else state.flash,
    )
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
            modifierPerRank = definition.modifierPerRank,
        )
    }.toImmutableList(),
)

/** Owned ranks over all ranks, for the header count (e.g. 23/76). */
internal fun LabRenderModel.rankTotals(): Pair<Int, Int> =
    upgrades.sumOf { it.rank } to upgrades.sumOf { it.maxRanks }

/**
 * The total a rank count grants ("+40", "+25%", "+0.6/s"), or null for rank 0 or an unknown
 * modifier. Values keep at most two decimals and use the language's decimal separator.
 */
internal fun labRankValue(modifier: ItemModifier?, ranks: Int, language: AppLanguage): String? {
    if (modifier == null || ranks <= 0) return null
    val total = modifier.amount * ranks
    return when (modifier.effect.unit) {
        ModifierUnit.PERCENT -> "+${labNumber(total * 100f, language)}%"
        ModifierUnit.FLAT -> "+${labNumber(total, language)}"
        ModifierUnit.PER_SECOND -> "+${labNumber(total, language)}${"/s".localizedContent(language)}"
        ModifierUnit.SECONDS -> "+${labNumber(total, language)}${"s".localizedContent(language)}"
    }
}

private fun labNumber(value: Float, language: AppLanguage): String {
    val hundredths = (value * 100f).roundToLong()
    val whole = hundredths / 100L
    val fraction = abs(hundredths % 100L)
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    if (fraction == 0L) return whole.toString()
    // 100 + fraction keeps the leading zero of 1..9 hundredths; trailing zeros are dropped.
    val decimals = (100L + fraction).toString().substring(1).trimEnd('0')
    return "$whole$separator$decimals"
}
