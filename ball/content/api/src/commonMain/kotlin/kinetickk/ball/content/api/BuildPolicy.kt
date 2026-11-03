// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * The per-run draft deck: each run deals a share of every rank-eligible rarity tier into its deck, and only dealt items
 * reach ordinary offers. The floors keep every run playable: early choice, an early Rare for pity, the Data ladder, the
 * character's archetype, every archetype and element, the risk wagers and skill support. Active items
 * ([ItemDefinition.isActive]) are dealt by their tier's share like any item, but every floor counts and promotes passive
 * items only: the floors guarantee passive coverage and never inflate the actives. History weights lower the chance that
 * recently taken items are dealt again. [FULL] deals the whole catalog (characterisation tests). Shares and minimums are
 * by [ItemRarity] (Common to Legendary).
 */
data class DraftDeckPolicy(
    val shares: ImmutableList<Float> = immutableListOf(0.55f, 0.55f, 0.6f, 0.65f, 0.75f),
    val minimums: ImmutableList<Int> = immutableListOf(18, 18, 12, 8, 2),
    /** Commons and Uncommons unlocking by [earlyOpenUnlock], so the first offers have choice. */
    val earlyOpenFloor: Int = 10,
    val earlyOpenUnlock: Int = 2,
    /** Rare-or-better items unlocking by [earlyRareUnlock], so pity can resolve early. */
    val earlyRareFloor: Int = 5,
    val earlyRareUnlock: Int = 6,
    /**
     * Rank-0 Data-economy items of Rare or lower ([ItemDefinition.isDataEconomy], the level-42 Data ladder), and how many
     * of them unlock by [earlyDataUnlock].
     */
    val dataEconomyFloor: Int = 4,
    val earlyDataFloor: Int = 2,
    val earlyDataUnlock: Int = 5,
    /** Items of Rare or lower carrying the run character's archetype. */
    val characterTagFloor: Int = 7,
    /** Items of Rare or lower per [BuildTag]. */
    val tagFloor: Int = 5,
    /** Items of Rare or lower per [StatusElement]. */
    val elementFloor: Int = 4,
    /** Risk items (the wagers). */
    val riskFloor: Int = 4,
    /** Passive items using skill vocabulary ([ItemDefinition.usesSkills]): support for the run's active items. */
    val skillFloor: Int = 6,
    /** A tier holding fewer offerable dealt items than this deals its next offerable reserve item. */
    val refillFloor: Int = 3,
    /** Deck weight of an item the Profile has never discovered. */
    val undiscoveredWeight: Float = 1.2f,
    /** History: deck weight 1 / (1 + [historyPenalty] × recency penalty), never below [historyFloor]. */
    val historyPenalty: Float = 0.8f,
    val historyFloor: Float = 0.35f,
) {
    init {
        require(shares.size == ItemRarity.entries.size && shares.all { it.isFinite() && it > 0f && it <= 1f })
        require(minimums.size == ItemRarity.entries.size && minimums.all { it >= 0 })
        require(earlyOpenFloor >= 0 && earlyOpenUnlock >= 1 && earlyRareFloor >= 0 && earlyRareUnlock >= 1)
        require(dataEconomyFloor >= 0 && earlyDataFloor in 0..dataEconomyFloor && earlyDataUnlock >= 1)
        require(characterTagFloor >= 0 && tagFloor >= 0 && elementFloor >= 0 && riskFloor >= 0 && skillFloor >= 0)
        require(refillFloor in 1..8)
        require(undiscoveredWeight.isFinite() && undiscoveredWeight in 1f..2f)
        require(historyPenalty.isFinite() && historyPenalty > 0f && historyPenalty <= 2f)
        require(historyFloor.isFinite() && historyFloor > 0f && historyFloor <= 1f)
    }

    /** True when every share is 1 and every floor 0: the deck is the whole rank-eligible catalog. */
    val dealsEverything: Boolean
        get() = shares.all { it >= 1f }

    companion object {
        /** No deck: every rank-eligible item is dealt (characterisation and pre-deck comparisons). */
        val FULL: DraftDeckPolicy = DraftDeckPolicy(
            shares = immutableListOf(1f, 1f, 1f, 1f, 1f),
            minimums = immutableListOf(0, 0, 0, 0, 0),
            earlyOpenFloor = 0, earlyRareFloor = 0, dataEconomyFloor = 0, earlyDataFloor = 0, characterTagFloor = 0,
            tagFloor = 0, elementFloor = 0, riskFloor = 0, skillFloor = 0,
        )
    }
}

/**
 * The item offer's lanes. The aligned card picks, [alignedChance] of the time, among items that fit the build, weighted
 * 1 + [alignedFitWeight] × min(fit, [alignedFitCap]); a tag counts toward fit only with points the player chose
 * ([loadoutFits] false leaves the character's own point out, and lets an item naming the equipped weapon's family fit
 * only once the build took one), and half when already at the top resonance tier ([saturatedTagFit]). The volatile card lifts its rarity [volatileLiftChance] of the time and prefers risk items only
 * [volatileRiskChance] of the time. In every lane an active item's weight is multiplied by [activeWeight], by
 * [characterActiveWeight] when its effect is in the run character's [CoreShapeDefinition.activeLane], and by
 * [ownedActiveWeight] when the card would rank up an owned active; the factors only weight a choice inside a rolled
 * tier and never add a draw. [RELEASE_040] is the 0.4.0 funnel, for comparisons.
 */
data class OfferLanePolicy(
    val alignedChance: Float = 0.55f,
    val alignedFitWeight: Float = 0.6f,
    val alignedFitCap: Float = 3f,
    val loadoutFits: Boolean = false,
    val saturatedTagFit: Float = 0.5f,
    val volatileLiftChance: Float = 0.6f,
    val volatileRiskChance: Float = 0.36f,
    /** Offer weight factor of every active item; 0 means actives are never offered, not merely rare. */
    val activeWeight: Float = 1f,
    /** Extra factor for the active items in the run character's lane. */
    val characterActiveWeight: Float = 3f,
    /** Extra factor for a card that would rank up an owned active item. */
    val ownedActiveWeight: Float = 8f,
) {
    init {
        require(alignedChance.isFinite() && alignedChance in 0f..1f)
        require(alignedFitWeight.isFinite() && alignedFitWeight >= 0f)
        require(alignedFitCap.isFinite() && alignedFitCap > 0f || alignedFitCap == Float.POSITIVE_INFINITY)
        require(saturatedTagFit.isFinite() && saturatedTagFit in 0f..1f)
        require(volatileLiftChance.isFinite() && volatileLiftChance in 0f..1f)
        require(volatileRiskChance.isFinite() && volatileRiskChance in 0f..volatileLiftChance)
        require(activeWeight.isFinite() && activeWeight >= 0f) { "Active weight must be finite and non-negative" }
        require(characterActiveWeight.isFinite() && characterActiveWeight >= 0f) {
            "Character active weight must be finite and non-negative"
        }
        require(ownedActiveWeight.isFinite() && ownedActiveWeight >= 0f) { "Owned active weight must be finite and non-negative" }
    }

    companion object {
        /**
         * The 0.4.0 lanes: the loadout fits, no saturation, an uncapped 0.8 fit weight, risk on every lift. The active
         * factors are the shipped ones, so a comparison against these lanes isolates the lanes themselves.
         */
        val RELEASE_040: OfferLanePolicy = OfferLanePolicy(
            alignedChance = 0.75f, alignedFitWeight = 0.8f, alignedFitCap = Float.POSITIVE_INFINITY, loadoutFits = true,
            saturatedTagFit = 1f, volatileLiftChance = 0.6f, volatileRiskChance = 0.6f,
        )
    }
}

/** Catalog-wide build tuning captured with the content snapshot. */
data class BuildPolicy(
    val resonanceThresholds: ImmutableList<Int> = immutableListOf(3, 5, 7),
    val affinityThresholds: ImmutableList<Int> = immutableListOf(2, 4, 6),
    /** Magnitude multiplier of an item at 1, 2 and 3 stacks (diminishing returns). */
    val stackMagnitudes: ImmutableList<Float> = immutableListOf(1f, 1.7f, 2.2f),
    val rebornMagnitude: Float = 1.5f,
    val rebornChancePerTier: Float = 0.05f,
    val maximumRebornChance: Float = 0.4f,
    val attunementBonus: Float = 0.3f,
    val severanceSeconds: Float = 45f,
    val harmonyScale: Float = 1.1f,
    val dissonanceScale: Float = 0.85f,
    /** Damage pipeline: base × (1 + amp) × crit × (1 + vulnerability) × overrule. */
    val amplificationSoftCap: Float = 1.5f,
    val criticalMultiplierCap: Float = 4f,
    val vulnerabilityCap: Float = 1.5f,
    val overdriveGainCap: Float = 2.5f,
    /** Rolling per-second budgets for cascading combat work (reject past the budget, never clip). */
    val breaksPerSecond: Int = 40,
    val reactionsPerSecond: Int = 30,
    val maximumFields: Int = 12,
    /** Lingering fields reserved for active effects (one per slot); reactions and items share the rest of [maximumFields]. */
    val maximumSkillFields: Int = 3,
    val reactionCooldownSeconds: Float = 0.5f,
    val reactionPairCooldownSeconds: Float = 2f,
    /**
     * Extra points an item at its maximum stacks adds to its first element's affinity, or to its first tag when it has
     * no element (an active item at rank III included: its tags and element count like any item's, once).
     */
    val masteredTagPoints: Int = 1,
    /** The per-run draft deck of offerable items. */
    val draftDeck: DraftDeckPolicy = DraftDeckPolicy(),
    /** How the item offer's three lanes choose inside a rolled rarity. */
    val offerLanes: OfferLanePolicy = OfferLanePolicy(),
) {
    init {
        require(resonanceThresholds.size == ContentBounds.RESONANCE_TIERS)
        require(resonanceThresholds.zipWithNext().all { (a, b) -> a in 1 until b })
        require(affinityThresholds.size == ContentBounds.RESONANCE_TIERS)
        require(affinityThresholds.zipWithNext().all { (a, b) -> a in 1 until b })
        require(stackMagnitudes.size == ContentBounds.MAX_ITEM_STACKS)
        require(stackMagnitudes.all { it.isFinite() && it > 0f } && stackMagnitudes.zipWithNext().all { (a, b) -> a < b })
        require(rebornMagnitude.isFinite() && rebornMagnitude >= 1f)
        require(rebornChancePerTier.isFinite() && rebornChancePerTier in 0f..1f)
        require(maximumRebornChance.isFinite() && maximumRebornChance in 0f..1f)
        require(attunementBonus.isFinite() && attunementBonus >= 0f)
        require(severanceSeconds.isFinite() && severanceSeconds >= 0f)
        require(harmonyScale.isFinite() && harmonyScale > 0f)
        require(dissonanceScale.isFinite() && dissonanceScale > 0f)
        require(amplificationSoftCap.isFinite() && amplificationSoftCap > 0f)
        require(criticalMultiplierCap.isFinite() && criticalMultiplierCap >= 1f)
        require(vulnerabilityCap.isFinite() && vulnerabilityCap >= 0f)
        require(overdriveGainCap.isFinite() && overdriveGainCap >= 1f)
        require(breaksPerSecond in 1..240 && reactionsPerSecond in 1..240)
        require(maximumFields in 1..ContentBounds.MAX_COMBAT_FIELDS)
        require(maximumSkillFields in 1 until maximumFields)
        require(reactionCooldownSeconds.isFinite() && reactionCooldownSeconds > 0f)
        require(reactionPairCooldownSeconds.isFinite() && reactionPairCooldownSeconds >= reactionCooldownSeconds)
        require(masteredTagPoints in 0..2)
    }

    fun stackMagnitude(stacks: Int): Float =
        if (stacks <= 0) 0f else stackMagnitudes[(stacks - 1).coerceAtMost(stackMagnitudes.lastIndex)]

    fun rebornChance(rebirthTier: Int): Float =
        (rebirthTier.coerceAtLeast(0) * rebornChancePerTier).coerceAtMost(maximumRebornChance)

    /** Highest reached tier (0 = none) for [points] against [thresholds]. */
    fun tier(points: Int, thresholds: ImmutableList<Int>): Int = thresholds.count { points >= it }
}
