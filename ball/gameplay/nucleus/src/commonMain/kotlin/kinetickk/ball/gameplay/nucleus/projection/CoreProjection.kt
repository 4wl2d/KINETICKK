// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/**
 * The previous reward previews while the same choice is open over an unchanged build: the same offered cards, relics
 * and compiled build (by reference: replaced, never mutated), the same weapon, time, discoveries, item stacks and
 * compared stat rows. Null when the previews must be built.
 */
internal fun MutableGameState.reusableRewardPreviews(
    previous: GameplayRenderModel?,
    identitySource: MutableGameState?,
    sources: ProjectionSources?,
): ImmutableList<RewardPreview>? {
    if (phase != GamePhase.CHOICE || previous == null || previous.phase != GamePhase.CHOICE) return null
    if (weapon != previous.weapon || elapsed != previous.elapsed ||
        discoveredPairingMask != previous.discoveredPairingMask ||
        discoveredReactionMask != previous.discoveredReactionMask ||
        itemStacks.reuseIfContentEqual(previous.itemStacks) == null
    ) return null
    val sameValues = when {
        identitySource != null -> choices === identitySource.choices && equippedRelics === identitySource.equippedRelics &&
            build === identitySource.build &&
            buildStats(withContributions = false) == identitySource.buildStats(withContributions = false)
        sources != null -> choices === sources.choices && equippedRelics === sources.equippedRelics &&
            build === sources.build && sources.previewStats != null &&
            buildStats(withContributions = false) == sources.previewStats
        else -> false
    }
    return previous.rewardPreviews.takeIf { sameValues }
}

/**
 * Relic gauges change during combat; the compiled build's immutable collections do not. The same build
 * ([previousBuild], replaced and never mutated) and pairing mask with equal item stacks ([previousItemStacks]) and
 * Reborn flags keep everything but the gauges.
 */
internal fun MutableGameState.buildProjection(
    previous: BuildProjection?,
    previousBuild: CompiledBuild?,
    previousPairingMask: Int?,
    previousItemStacks: ImmutableList<Int>?,
): BuildProjection {
    if (previous == null || build !== previousBuild || discoveredPairingMask != previousPairingMask ||
        itemStacks.reuseIfContentEqual(previousItemStacks) == null ||
        !rebornItemsMatch(previous.rebornItemIds)
    ) return buildProjection()

    val severed = severedRelics.reuseProjectionIfContentEqual(previous.severed) { value, projection ->
        value.id == projection.id && kotlin.math.max(0f, value.until - elapsed).sameBitsAs(projection.remaining)
    } ?: severedRelics.mapToImmutableList { value ->
        SeveredRelicProjection(value.id, kotlin.math.max(0f, value.until - elapsed))
    }
    val meter = stasisMeter / MutableGameState.STASIS_SECONDS
    val invulnerable = invulnerableUntil > elapsed
    val burnout = overdriveBurnoutUntil > elapsed
    val budgetSpent = relicBudgetLeft() <= 0
    val changesSpent = weaponChangesLeft() <= 0
    if (previous.severed === severed && previous.stormCharge.sameBitsAs(stormCharge) &&
        previous.stasisMeter.sameBitsAs(meter) && previous.stasisBank == stasisBank &&
        previous.ouroborosSpent == ouroborosSpent && previous.invulnerable == invulnerable &&
        previous.overdriveBurnout == burnout && previous.relicClaims == relicRun.relicClaims &&
        previous.relicBudgetSpent == budgetSpent && previous.meldedSlots == relicRun.meldedSlots &&
        previous.weaponChangesSpent == changesSpent
    ) return previous

    return previous.copy(
        severed = severed,
        stormCharge = stormCharge,
        stasisMeter = meter,
        stasisBank = stasisBank,
        ouroborosSpent = ouroborosSpent,
        invulnerable = invulnerable,
        overdriveBurnout = burnout,
        relicClaims = relicRun.relicClaims,
        relicBudgetSpent = budgetSpent,
        meldedSlots = relicRun.meldedSlots,
        weaponChangesSpent = changesSpent,
    )
}

/** True when [ids] lists exactly the items whose Reborn flag is set, in content order (as [buildProjection] builds). */
private fun MutableGameState.rebornItemsMatch(ids: ImmutableList<Int>): Boolean {
    var next = 0
    val items = content.items
    for (index in items.indices) {
        val id = items[index].id
        if (rebornItems[id] == 0) continue
        if (next >= ids.size || ids[next] != id) return false
        next++
    }
    return next == ids.size
}

/** Run totals change rarely; an unchanged set keeps the previous immutable projection. */
internal fun MutableGameState.runStatisticsProjection(reusable: RunStatistics?): RunStatistics {
    if (reusable != null &&
        reusable.damageDealt.toRawBits() == damageDealt.toRawBits() &&
        reusable.damageTaken.toRawBits() == damageTaken.toRawBits() &&
        reusable.damageAbsorbed.toRawBits() == damageAbsorbed.toRawBits() &&
        reusable.dataCollected == dataCollected &&
        reusable.pickupsCollected == pickupsCollected &&
        reusable.keysCollected == keysCollected &&
        reusable.eliteKills == eliteKills &&
        reusable.bestCombo == bestCombo
    ) {
        return reusable
    }
    return RunStatistics(
        damageDealt = damageDealt,
        damageTaken = damageTaken,
        damageAbsorbed = damageAbsorbed,
        dataCollected = dataCollected,
        pickupsCollected = pickupsCollected,
        keysCollected = keysCollected,
        eliteKills = eliteKills,
        bestCombo = bestCombo,
    )
}

/** The same list or equal relics keep the previous projection. */
internal fun MutableGameState.equippedRelicProjection(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<EquippedRelic> =
    equippedRelics.reuseIfIdentical(
        identitySource?.equippedRelics,
        reusableCollections?.equippedRelics,
    ) ?: equippedRelics.reuseIfContentEqual(reusableCollections?.equippedRelics)
        ?: equippedRelics.toImmutableList()

/** The same list or equal options keep the previous projection. */
internal fun MutableGameState.choiceProjection(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<ChoiceOption> =
    choices.reuseIfIdentical(identitySource?.choices, reusableCollections?.choices)
        ?: choices.reuseIfContentEqual(reusableCollections?.choices)
        ?: choices.toImmutableList()
