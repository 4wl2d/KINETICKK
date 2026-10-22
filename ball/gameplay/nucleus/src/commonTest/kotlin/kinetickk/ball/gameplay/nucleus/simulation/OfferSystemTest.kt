// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seeded offers per odds scenario. */
private const val OFFER_SAMPLES = 4_000

/** Largest gap between two card shares that a correct offer keeps equal (about four standard deviations at 12,000 cards). */
private const val SHARE_TOLERANCE = 0.025

/** Seeds per pity sweep here; OfferPitySweepTest (desktop) runs the long 20,000-seed sweeps. */
private const val PITY_SWEEP_SEEDS = 500

/** An unlock level no run reaches, which keeps an item out of every ordinary offer. */
private const val NEVER_UNLOCKED = Int.MAX_VALUE

/**
 * The full-catalog veteran's offers over the odds seeds. Several odds checks compare against this same immutable
 * sample, so it is built once per run, which keeps each check well inside the browser runner's per-test timeout.
 */
private val veteranOffers: List<List<ItemDefinition>> by lazy { buildTestState().apply { lifetimeMatter = 4_000L }.sampleOffers() }

/** Builds one offer per seed from a fresh reward stream with the given pity counter and returns its cards. */
private fun MutableGameState.sampleOffers(seeds: IntRange = 1..OFFER_SAMPLES, pity: Int = 0): List<List<ItemDefinition>> =
    seeds.map { seed ->
        rewardRandom = CloneableXorWowRandom(seed)
        offerPity = pity
        buildItemChoices()
        choices.map { content.items[requireNotNull(it.itemId)] }
    }

class OfferSystemTest {
    @Test
    fun offersAreSeededByTheRewardStreamAndNeverShiftCombatRandomness() {
        val first = buildTestState(seed = 77)
        val second = buildTestState(seed = 77)
        val combatCursor = first.gameplayRandom.snapshot()
        first.openItemChoice()
        second.openItemChoice()
        assertEquals(first.choices, second.choices)
        assertEquals(combatCursor, first.gameplayRandom.snapshot(), "Drafting must not draw from the combat stream")
        assertEquals(second.gameplayRandom.snapshot(), first.gameplayRandom.snapshot())
        repeat(3) {
            first.rerollsRemaining = 1
            second.rerollsRemaining = 1
            first.rerollChoices()
            second.rerollChoices()
            assertEquals(first.choices, second.choices)
        }
    }

    @Test
    fun offersHoldDistinctCardsAndExcludeFullyDisabledItems() {
        val state = buildTestState(RelicId.GLASS_COMET).apply { lifetimeMatter = 4_000L }
        val shieldCell = state.itemNamed("Shield Cell")
        val capacitorAegis = state.itemNamed("Capacitor Aegis")
        assertFalse(shieldCell in state.offerableItems())
        assertFalse(capacitorAegis in state.offerableItems())
        repeat(60) {
            state.buildItemChoices()
            val ids = state.choices.mapNotNull { it.itemId }
            assertEquals(3, ids.size)
            assertEquals(ids.size, ids.toSet().size, "An offer never repeats a card")
            assertTrue(shieldCell.id !in ids && capacitorAegis.id !in ids)
        }
    }

    @Test
    fun rebirthGatedItemsAndRelicsStayOutOfOffersBelowTheirTier() {
        // A veteran's run that can use every gated item: a skill drafted and a plain item of each element held, so skill
        // items have something to work on and every reaction a keystone item names is reachable.
        fun gatedItems(rebirth: Int): Set<String> = buildTestState(rebirth = rebirth).apply {
            lifetimeMatter = 4_000L
            acquireSkill(SkillId.SOLAR_FLARE)
            StatusElement.entries.forEach { element ->
                acquireItem(content.items.first { it.elements.singleOrNull() == element && it.rarity == ItemRarity.COMMON }.id)
            }
        }.offerableItems().filter { it.minimumRebirth > 0 }.map { it.name }.toSet()
        fun gatedUpTo(rebirth: Int): Set<String> =
            canonicalGameplayContent.items.filter { it.minimumRebirth in 1..rebirth }.map { it.name }.toSet()
        assertTrue(gatedItems(0).isEmpty())
        // Each tier opens exactly what its gates name, and every lower tier's items stay open.
        for (rebirth in 1..10) assertEquals(gatedUpTo(rebirth), gatedItems(rebirth), "Reborn $rebirth")
        assertTrue("Glacial Heart" in gatedItems(2) && "Resonance Fork" !in gatedItems(3) && "Resonance Fork" in gatedItems(4))
        assertTrue("Last Word" !in gatedItems(9) && "Last Word" in gatedItems(10))
        assertEquals(43 + 2, gatedItems(10).size, "Three original legendaries, forty gated 0.4.0 items and the gated 0.5.0 items")

        fun sovereigns(rebirth: Int): Set<RelicId> = buildTestState(rebirth = rebirth).offerableRelics()
            .filter { it.minimumRebirth > 0 }.map { it.id }.toSet()
        assertTrue(sovereigns(0).isEmpty())
        assertEquals(setOf(RelicId.AGONY_SCEPTER), sovereigns(1))
        assertEquals(setOf(RelicId.AGONY_SCEPTER, RelicId.HOLLOW_CROWN), sovereigns(3))
        assertEquals(setOf(RelicId.AGONY_SCEPTER, RelicId.HOLLOW_CROWN, RelicId.OUROBOROS_SEAL), sovereigns(5))
    }

    @Test
    fun everyItemIsOfferedFromItsOwnTierAndLevelAndIsDiscoveredWhenTaken() {
        val items = canonicalGameplayContent.items
        assertEquals(197, items.size)
        // A fresh profile per tier: no lifetime Matter, so the run level alone opens the catalog. Each run has drafted
        // a skill (the first slot opens at level 2), so skill items have something to work on.
        val tiers = (0..10).map { tier -> buildTestState(rebirth = tier).apply { acquireSkill(SkillId.SOLAR_FLARE) } }
        for (item in items) {
            val own = runThatCanUse(item, item.minimumRebirth, tiers).apply { level = item.unlockLevel }
            assertTrue(item in own.offerableItems(), "${item.name} is offered at Reborn ${item.minimumRebirth}, level ${item.unlockLevel}")
            if (item.unlockLevel > 1) {
                own.level = item.unlockLevel - 1
                assertFalse(item in own.offerableItems(), "${item.name} waits for level ${item.unlockLevel}")
            }
            if (item.minimumRebirth > 0) {
                val below = runThatCanUse(item, item.minimumRebirth - 1, tiers).apply { level = 99 }
                assertFalse(item in below.offerableItems(), "${item.name} waits for Reborn ${item.minimumRebirth}")
            }
        }

        // Taking an item records its discovery for the Codex, whichever item it is.
        val collector = buildTestState(rebirth = 10)
        items.forEach { collector.acquireItem(it.id) }
        assertEquals(items.map { it.id }.toSet(), collector.takeProgressUpdate()?.discoveredItemIds?.toSet())
    }

    @Test
    fun rebornChanceGrowsWithRebirthTierAndFortuneThree() {
        assertEquals(0f, buildTestState(rebirth = 0).rebornChance())
        assertEquals(0.15f, buildTestState(rebirth = 3).rebornChance(), 0.0001f)
        assertEquals(0.4f, buildTestState(rebirth = 10).rebornChance(), 0.0001f)

        val fortune = buildTestState().apply {
            own("Magnet Lens", stacks = 3)
            own("Lucky Coin")
            own("Data Siphon")
            own("Jackpot Relay")
            own("Gambler's Ledger")
        }
        assertEquals(3, fortune.tagTier(BuildTag.FORTUNE))
        assertEquals(0.1f, fortune.rebornChance(), 0.0001f, "Fortune III works even before the first Rebirth")

        val plain = buildTestState()
        repeat(40) {
            plain.buildItemChoices()
            assertTrue(plain.choices.none { it.reborn })
        }
    }

    @Test
    fun aRebornCardIsStrongerAndCountsDoubleForResonance() {
        val state = buildTestState()
        val plain = buildTestState()
        val ballast = state.itemNamed("Ballast Core")
        state.acquireItem(ballast.id, reborn = true)
        plain.acquireItem(ballast.id)
        val mass = ballast.stats.single { it.effect == kinetickk.ball.content.api.ItemEffect.MASS }.amount
        assertEquals(1f + mass * state.content.build.policy.rebornMagnitude, state.mass, 0.0001f)
        assertEquals(1f + mass, plain.mass, 0.0001f)
        assertEquals(2, state.build.tagPoints(BuildTag.RAM))
        assertEquals(1, plain.build.tagPoints(BuildTag.RAM))
        assertEquals(1.5f, state.build.bonus(ballast.id), 0.0001f)
    }

    @Test
    fun pityGuaranteesARareCardAfterSixResolvedOffersWithoutOne() {
        fun hasRare(pity: Int, seed: Int): Boolean {
            val offer = buildTestState(seed = seed).apply {
                level = 20
                lifetimeMatter = 4_000L
                offerPity = pity
                buildItemChoices()
            }
            return offer.choices.any { option -> offer.itemOf(option.itemId).rarity.rank >= ItemRarity.RARE.rank }
        }
        val seeds = 1..300
        assertTrue(seeds.all { hasRare(PITY_OFFERS, it) })
        assertFalse(seeds.all { hasRare(PITY_OFFERS - 1, it) }, "Without pity some offers carry no Rare card")

        // Pity holds even when the rolled rarity has nothing to offer (no Legendary before Rebirth 2). A short sweep
        // keeps this test fast on Wasm; OfferPitySweepTest (desktop) repeats it over 20,000 seeds.
        val sweep = buildTestState().apply {
            level = 20
            lifetimeMatter = 4_000L
        }
        val misses = (1..PITY_SWEEP_SEEDS).count { seed ->
            sweep.rewardRandom = kinetickk.foundation.random.CloneableXorWowRandom(seed)
            sweep.offerPity = PITY_OFFERS
            sweep.buildItemChoices()
            sweep.choices.none { option -> sweep.itemOf(option.itemId).rarity.rank >= ItemRarity.RARE.rank }
        }
        assertEquals(0, misses)

        // Resolving an offer advances the counter, a Rare pick resets it, and rerolls never count.
        val state = buildTestState()
        state.openItemChoice()
        state.rerollChoices()
        assertEquals(0, state.offerPity)
        val common = state.choices.indexOfFirst { state.itemOf(it.itemId).rarity.rank < ItemRarity.RARE.rank }
        state.choose(common)
        assertEquals(1, state.offerPity)
        state.resolveItemOfferPity(state.itemNamed("Kinetic Lance"))
        assertEquals(0, state.offerPity)
    }

    @Test
    fun gamblersLedgerAddsAFourthCard() {
        val state = buildTestState().apply { own("Gambler's Ledger") }
        state.openItemChoice()
        assertEquals(4, state.choices.size)
        assertEquals(4, state.choices.mapNotNull { it.itemId }.toSet().size)
    }

    // The odds tests below sample seeded offers and check fixed bounds around the probability model, or compare two
    // catalogs on the same seeds, each with at least four standard deviations of margin, so they survive draw-order
    // changes and small odds tuning.

    @Test
    fun aFullCatalogVeteranSeesAboutOneEpicCardEveryThreeOffers() {
        val offers = veteranOffers
        assertTrue(offers.all { it.size == 3 })
        assertTrue(offers.all { offer -> offer.map { it.id }.toSet().size == offer.size }, "An offer never repeats a card")
        // Cards 0 and 1 are Epic 7% of the time (Legendary falls back to Epic before Rebirth 2), the volatile card 19%.
        assertWithin(0.25..0.42, offers.cardsPerOffer { it.rarity == ItemRarity.EPIC }, "Epic cards per offer")
        assertWithin(0.22..0.38, offers.offerShare { it.rarity == ItemRarity.EPIC }, "offers with an Epic card")
        assertWithin(0.62..0.82, offers.offerShare { it.rarity.rank >= ItemRarity.RARE.rank }, "offers with a Rare or better")
        assertTrue(offers.cardShare { it.rarity.rank <= ItemRarity.UNCOMMON.rank } >= 0.60, "Commons and Uncommons fill most cards")
        assertEquals(0.0, offers.cardShare { it.rarity == ItemRarity.LEGENDARY }, "No Legendary before Rebirth 2")
        // Risk items are all Epic; a lane that overrides the rolled rarity would make the volatile card Epic most of the time.
        repeat(3) { position ->
            assertTrue(offers.positionShare(position) { it.rarity == ItemRarity.EPIC } <= 0.25, "Epic share of card $position")
        }
        assertTrue(offers.cardShare { it.risk } <= 0.15, "Risk items stay a minority of cards")
    }

    @Test
    fun gamblersLedgerAddsAVolatileCardWithoutFloodingOffersWithEpics() {
        val offers = buildTestState().apply {
            lifetimeMatter = 4_000L
            own("Gambler's Ledger")
        }.sampleOffers()
        assertTrue(offers.all { it.size == 4 })
        assertTrue(offers.all { offer -> offer.map { it.id }.toSet().size == offer.size }, "An offer never repeats a card")
        assertTrue(offers.offerShare { it.rarity == ItemRarity.EPIC } <= 0.52, "offers with an Epic card")
        repeat(4) { position ->
            assertTrue(offers.positionShare(position) { it.rarity == ItemRarity.EPIC } <= 0.25, "Epic share of card $position")
        }
    }

    @Test
    fun rebirthTwoOpensLegendariesWithoutRaisingEpicOdds() {
        val offers = buildTestState(rebirth = 2).apply {
            level = 20
            lifetimeMatter = 4_000L
        }.sampleOffers()
        assertWithin(0.015..0.05, offers.cardShare { it.rarity == ItemRarity.LEGENDARY }, "Legendary share of cards")
        assertTrue(offers.cardsPerOffer { it.rarity == ItemRarity.EPIC } <= 0.33, "Epic cards per offer")
    }

    // The two fallback checks below compare a catalog with the full-catalog veteran on the same seeds and odds, so they
    // hold under any base odds, while an open draw or an upward fallback moves the shares far apart.

    @Test
    fun aRolledTierWithNoAvailableItemFallsToTheNearestLowerTier() {
        val veteran = veteranOffers
        // No Epic is unlocked at level 9, so Epic and Legendary rolls become Rare cards instead of open draws.
        val midRun = buildTestState().apply {
            level = 9
            lifetimeMatter = 0L
        }.sampleOffers()
        assertClose(veteran.cardShare(rareOrBetter), midRun.cardShare { it.rarity == ItemRarity.RARE }, "Rare share of cards at level 9")
    }

    @Test
    fun aRolledTierFallsBelowAnEmptyTierEvenWithItemsAboveIt() {
        val veteran = veteranOffers
        // With no Uncommon offerable, Uncommon rolls fall to Common even though Rare items are available above them.
        val noUncommons = buildTestState(content = contentOffering { it.rarity != ItemRarity.UNCOMMON })
            .apply { lifetimeMatter = 4_000L }
            .sampleOffers()
        assertClose(
            veteran.cardShare { it.rarity.rank <= ItemRarity.UNCOMMON.rank },
            noUncommons.cardShare { it.rarity == ItemRarity.COMMON },
            "Common share of cards without Uncommons",
        )
        assertClose(
            veteran.cardShare { it.rarity == ItemRarity.RARE },
            noUncommons.cardShare { it.rarity == ItemRarity.RARE },
            "Rare share of cards without Uncommons",
        )
    }

    @Test
    fun aLaneOnlyChoosesInsideTheRolledTier() {
        // A fresh run at level 14 offers Epics, but none of them fits the fresh build, so the aligned card would rarely
        // be Epic if a lane picked its preferred items before honouring the rolled rarity.
        val freshRun = buildTestState().apply {
            level = 14
            lifetimeMatter = 0L
        }.sampleOffers()
        // Cards 0 and 1 are Epic 7% of the time (Legendary falls back to Epic before Rebirth 2).
        repeat(2) { position ->
            assertTrue(freshRun.positionShare(position) { it.rarity == ItemRarity.EPIC } >= 0.04, "Epic share of card $position")
        }
    }

    @Test
    fun luckRaisesRarityOddsWithinBounds() {
        val unlucky = veteranOffers
        val lucky = buildTestState().apply {
            lifetimeMatter = 4_000L
            luck = 1f
        }.sampleOffers()
        // Luck 1 lifts offers with a Rare or better from 72% to 81% on the same seeds.
        val luckGain = lucky.offerShare(rareOrBetter) - unlucky.offerShare(rareOrBetter)
        assertTrue(luckGain >= 0.04, "Luck 1 raises the share of offers with a Rare or better by only $luckGain")
        assertTrue(lucky.cardsPerOffer { it.rarity == ItemRarity.EPIC } <= 0.65, "Epic cards per offer at luck 1")
    }

    @Test
    fun pityFindsARareOrBetterCardWhereverTheOfferableOnesSit() {
        fun veteran(content: GameplayContentSnapshot = canonicalGameplayContent, rebirth: Int = 0) =
            buildTestState(rebirth = rebirth, content = content).apply {
                level = 20
                lifetimeMatter = 4_000L
            }
        val singleRare = canonicalGameplayContent.items.first { it.rarity == ItemRarity.RARE && it.minimumRebirth == 0 }
        val scenarios = mapOf(
            "Rebirth 2" to veteran(rebirth = 2),
            "a single Rare or better" to veteran(contentOffering { !rareOrBetter(it) || it.id == singleRare.id }),
            // The pity card climbs from an empty Rare tier to Epic instead of falling below Rare.
            "no Rare" to veteran(contentOffering { it.rarity != ItemRarity.RARE }),
            // The pity card climbs past the empty Rare and Epic tiers to a Legendary.
            "no Rare or Epic at Rebirth 2" to veteran(
                contentOffering { it.rarity != ItemRarity.RARE && it.rarity != ItemRarity.EPIC },
                rebirth = 2,
            ),
        )
        assertEquals(1, scenarios.getValue("a single Rare or better").offerableItems().count(rareOrBetter))
        scenarios.forEach { (name, state) ->
            val offers = state.sampleOffers(seeds = 1..PITY_SWEEP_SEEDS, pity = PITY_OFFERS)
            assertEquals(0, offers.count { offer -> offer.none(rareOrBetter) }, "Offers without a Rare or better ($name)")
            assertTrue(offers.all { offer -> offer.map { it.id }.toSet().size == offer.size }, "An offer never repeats a card ($name)")
        }
    }

    @Test
    fun heirloomOfferOpensRebornRunsWithSeededRelicsAndADecline() {
        val heirlooms = listOf(RelicId.GYRE_ENGINE, RelicId.GLASS_COMET, RelicId.ECHO_CHAMBER)
        val state = heirloomRun(rebirth = 1, heirlooms)
        val twin = heirloomRun(rebirth = 1, heirlooms)
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.RELIC, state.choiceType)
        assertEquals(twin.choices, state.choices, "The Heirloom offer is seeded")
        val relicCards = state.choices.filter { it.relicAction == RelicChoiceAction.ACQUIRE }
        assertEquals(2, relicCards.size)
        assertTrue(relicCards.all { it.heirloom && it.relicId in heirlooms })
        assertEquals(RelicChoiceAction.DECLINE, state.choices.last().relicAction)
        assertTrue(state.choices.last().heirloom)
        assertFalse(state.choicesCanReroll)

        val rerolls = state.rerollsRemaining
        state.choose(state.choices.lastIndex)
        assertEquals(rerolls + 2, state.rerollsRemaining)
        assertTrue(state.equippedRelics.isEmpty())
        assertTrue(state.takeProgressUpdate()?.heirloomResolved == true)

        val chosen = relicCards.first().relicId
        twin.choose(0)
        assertEquals(listOf(chosen), twin.equippedRelics.map { it.id })
        assertEquals(1, twin.equippedRelics.single().rank)
        assertTrue(twin.takeProgressUpdate()?.heirloomResolved == true)
        assertTrue(twin.heirloomCandidates.isEmpty())

        assertEquals(3, heirloomRun(rebirth = 5, heirlooms).choices.count { it.relicAction == RelicChoiceAction.ACQUIRE })
        val firstRun = heirloomRun(rebirth = 0, heirlooms)
        assertEquals(GamePhase.RUNNING, firstRun.phase, "No Heirloom offer before the first Rebirth")
    }

    @Test
    fun victoryRecordsBoundRelicsAndDiscoveriesInOneProgressUpdate() {
        val state = buildTestState(RelicId.GYRE_ENGINE, RelicId.FURNACE_COVENANT, RelicId.OVERDRIVE_CASCADE)
        val target = state.dummy()
        state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
        state.applyStatus(target, StatusElement.CRYO, 1, generation = 0)
        val architect = state.finalPhaseArchitectForTesting(x = -400f, y = 0f)
        state.dealHit(architect, 10_000f, HitSource.WEAPON, canCrit = false)
        state.sweepKills()

        assertEquals(GamePhase.VICTORY, state.phase)
        val update = requireNotNull(state.takeProgressUpdate())
        assertEquals(0, update.clearedRebirthLevel)
        assertEquals(listOf(RelicId.GYRE_ENGINE, RelicId.FURNACE_COVENANT, RelicId.OVERDRIVE_CASCADE), update.victoryRelicIds?.toList())
        assertEquals(setOf(ReactionId.THERMAL_SHOCK), update.discoveredReactionIds.toSet())
        assertFalse(update.heirloomResolved)
        assertNull(state.takeProgressUpdate())
    }

    private fun MutableGameState.itemOf(itemId: Int?) = content.items[requireNotNull(itemId)]

    /**
     * A run at Reborn [rebirth] that can use [item]: the tier's run, or, for an item that names a weapon family or
     * switches a reaction to its keystone, a fresh one with that family's weapon in hand or holding a plain item of
     * each of the reaction's elements (a keystone is only offered while its reaction is reachable), a skill drafted.
     */
    private fun runThatCanUse(item: ItemDefinition, rebirth: Int, tiers: List<MutableGameState>): MutableGameState {
        val family = (item.clauses.flatMap { it.conditions } + item.rules.mapNotNull { (it as? ItemRule.WhileModifier)?.condition })
            .firstNotNullOfOrNull { (it as? EffectCondition.WeaponFamilyIs)?.family }
        val keystone = item.keystone
        if (family == null && keystone == null) return tiers[rebirth]
        val weapon = family?.let { wanted -> canonicalGameplayContent.weapons.first { it.family == wanted }.id } ?: WeaponId.FLUX_WAKE
        return buildTestState(rebirth = rebirth, weapon = weapon).apply {
            acquireSkill(SkillId.SOLAR_FLARE)
            keystone?.let { reaction ->
                listOf(reaction.first, reaction.second).forEach { element ->
                    val source = content.items.first { it.elements.singleOrNull() == element && it.rarity == ItemRarity.COMMON }
                    acquireItem(source.id)
                }
            }
        }
    }

    private val rareOrBetter = { item: ItemDefinition -> item.rarity.rank >= ItemRarity.RARE.rank }

    /** The canonical catalog in which only the items matching [offerable] ever unlock for ordinary offers. */
    private fun contentOffering(offerable: (ItemDefinition) -> Boolean): GameplayContentSnapshot =
        canonicalGameplayContent.copy(
            items = canonicalGameplayContent.items
                .map { item -> if (offerable(item)) item else item.copy(unlockLevel = NEVER_UNLOCKED) }
                .toImmutableList(),
        )

    private fun List<List<ItemDefinition>>.cardsPerOffer(predicate: (ItemDefinition) -> Boolean): Double =
        sumOf { offer -> offer.count(predicate) }.toDouble() / size

    private fun List<List<ItemDefinition>>.offerShare(predicate: (ItemDefinition) -> Boolean): Double =
        count { offer -> offer.any(predicate) }.toDouble() / size

    private fun List<List<ItemDefinition>>.cardShare(predicate: (ItemDefinition) -> Boolean): Double =
        sumOf { offer -> offer.count(predicate) }.toDouble() / sumOf { it.size }

    private fun List<List<ItemDefinition>>.positionShare(position: Int, predicate: (ItemDefinition) -> Boolean): Double =
        count { offer -> predicate(offer[position]) }.toDouble() / size

    private fun assertWithin(range: ClosedFloatingPointRange<Double>, actual: Double, message: String) =
        assertTrue(actual in range, "$message: $actual is outside $range")

    private fun assertClose(expected: Double, actual: Double, message: String) =
        assertTrue(abs(actual - expected) <= SHARE_TOLERANCE, "$message: $actual is not within $SHARE_TOLERANCE of $expected")

    private fun heirloomRun(rebirth: Int, heirlooms: List<RelicId>): MutableGameState {
        val snapshot = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = PlayerCollection(heirloomRelicIds = heirlooms.toImmutableList()),
            rebirthProgress = RebirthProgress(level = rebirth),
        )
        return MutableGameState(canonicalGameplayContent, seed = 4_404, bootstrapProgress = snapshot).apply {
            startRun()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            update(MutableGameState.FIXED_STEP * 1.5f)
        }
    }
}
