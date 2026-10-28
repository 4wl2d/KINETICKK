// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RunVarietyNotices
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Event signals (variety.md §7): three of the five slots and three distinct kinds per run from stateless hashes, one live
 * signal beside the heading, expiry and loss, a touch queuing a prompt that opens after every other choice, each card's
 * cost, lock and payout through the existing channels, chained floor offers, curses that always land, and the Architect,
 * other world layers, engagements and the quiet arena holding or skipping slots. Queue and live bounds are in
 * RunVarietyBoundsTest.
 */
class EventSignalTest {
    private val content = canonicalGameplayContent
    private val policy = content.variety.signals
    private val tuning = policy.tuning

    /** A started run with no enemies and no obstacles at 0 s; only what a test adds acts. */
    private fun arena(seed: Int = 5_150, content: GameplayContentSnapshot = this.content, rebirth: Int = 0): MutableGameState =
        buildTestState(seed = seed, rebirth = rebirth, content = content).apply { clearObstaclesForTesting() }

    /** Sets the clock to [time] and runs one signal step there. */
    private fun MutableGameState.signalStepAt(time: Float) {
        elapsed = time
        updateEventSignals()
    }

    /** Places a live signal of [kind] at ([x], [y]) as the run's latest spawned signal. */
    private fun MutableGameState.liveSignal(kind: SignalKind, x: Float, y: Float, expiresAt: Float = elapsed + 20f): EventSignalProjection {
        val signal = EventSignalProjection(nextEntityId++, kind, x, y, expiresAt)
        eventSignals = immutableListOf(signal)
        signalsSpawned = 1
        return signal
    }

    /** Moves the Core in one step from where it is to ([x], [y]). */
    private fun MutableGameState.sweepCoreTo(x: Float, y: Float) {
        previousCoreX = coreX
        previousCoreY = coreY
        coreX = x
        coreY = y
    }

    /** Queues a prompt of [kind] for signal [index] and opens its cards. */
    private fun MutableGameState.openPrompt(kind: SignalKind, index: Int = 0): MutableGameState {
        pendingSignalPrompts = immutableListOf(SignalPrompt(kind, index))
        openSignalPrompt()
        return this
    }

    private fun MutableGameState.card(option: SignalOption) = choices.single { it.signalOption == option }

    private fun MutableGameState.take(option: SignalOption) = choose(choices.indexOfFirst { it.signalOption == option })

    private fun MutableGameState.signalBuff(option: SignalOption): ActiveBuff? =
        buffs.singleOrNull { it.sourceKey == BuffSource.signal(option) }

    private fun MutableGameState.streams(): List<Any> =
        listOf(rewardRandom.snapshot(), spawnRandom.snapshot(), gameplayRandom.snapshot(), siteRandom.snapshot())

    @Test
    fun everyRunUsesThreeOfItsFiveSlotsAndMeetsThreeDistinctKinds() {
        val slotRuns = IntArray(policy.slots)
        val kindRuns = IntArray(SignalKind.entries.size)
        val firstKinds = IntArray(SignalKind.entries.size)
        val distinct = HashSet<List<Any>>()
        for (seed in 1..SEEDS) {
            val slots = plannedSignalSlots(seed, policy)
            val kinds = plannedSignalKinds(seed, policy)
            assertEquals(policy.perRun, slots.size, "Seed $seed slots")
            assertEquals(slots.sorted(), slots, "Seed $seed slots run in time order")
            assertTrue(slots.all { it in 0 until policy.slots })
            assertEquals(policy.perRun, kinds.toSet().size, "Seed $seed repeats no kind")
            assertEquals(slots, plannedSignalSlots(seed, policy), "Seed $seed picks the same slots again")
            assertEquals(kinds, plannedSignalKinds(seed, policy), "Seed $seed meets the same kinds again")
            for (slot in 0 until policy.slots) assertEquals(slot in slots, signalSlotChosen(seed, policy, slot))
            slots.forEach { slotRuns[it]++ }
            kinds.forEach { kindRuns[it.ordinal]++ }
            firstKinds[kinds.first().ordinal]++
            distinct.add(listOf(slots, kinds))
        }
        // Three of five equal chances: each slot and each kind in about 60 % of runs, each kind first in about 20 %.
        slotRuns.forEachIndexed { slot, runs -> assertEquals(0.6f, runs / SEEDS.toFloat(), 0.04f, "Slot $slot share") }
        kindRuns.forEachIndexed { kind, runs -> assertEquals(0.6f, runs / SEEDS.toFloat(), 0.04f, "${SignalKind.entries[kind]} share") }
        firstKinds.forEachIndexed { kind, runs -> assertEquals(0.2f, runs / SEEDS.toFloat(), 0.03f, "${SignalKind.entries[kind]} first") }
        // 10 slot sets × 60 kind orders: 2,000 seeds fill most of the 600 skeletons.
        assertTrue(distinct.size > 500, "Seeds differ: ${distinct.size} distinct plans")
    }

    @Test
    fun zeroWeightKindsAreNeverMetAndAnOffPolicyPlansNothing() {
        val two = policy.copy(perRun = 2, weights = immutableListOf(0f, 1f, 0f, 1f, 0f))
        for (seed in 1..200) {
            assertEquals(setOf(SignalKind.SHRINE, SignalKind.CURSED_CACHE), plannedSignalKinds(seed, two).toSet())
            assertEquals(2, plannedSignalSlots(seed, two).size)
        }
        val off = policy.copy(enabled = false)
        assertTrue(plannedSignalSlots(7, off).isEmpty())
        assertTrue(plannedSignalKinds(7, off).isEmpty())
    }

    @Test
    fun everySlotWindowSitsBetweenTwoSiteWindowsAndFadesBeforeTheArchitect() {
        val windows = content.sites.windows
        for (slot in 0 until policy.slots) {
            val opens = policy.slotAt(slot)
            val closes = opens + policy.lifetimeSeconds
            val trialEnds = windows.firstOfferAt + slot * windows.offerInterval + windows.offerLifetime + windows.trialDuration
            val nextWindow = windows.firstOfferAt + (slot + 1) * windows.offerInterval
            assertTrue(opens >= trialEnds, "Slot $slot opens at $opens after the window trial ends at $trialEnds")
            assertTrue(closes <= nextWindow, "Slot $slot closes at $closes before the next window at $nextWindow")
            assertTrue(closes < content.tempo.bossAtSeconds)
        }
        assertEquals(listOf(115f, 235f, 355f, 475f, 595f), (0 until policy.slots).map { policy.slotAt(it) })
    }

    @Test
    fun aChosenSlotSpawnsOneSignalBesideTheHeadingWithItsBanner() {
        val seed = 5_150
        val state = arena(seed)
        val slot = plannedSignalSlots(seed, policy).first()
        val kind = plannedSignalKinds(seed, policy).first()
        state.setVelocityForTesting(0f, 300f)
        state.signalStepAt(policy.slotAt(slot).nextDown())
        assertTrue(state.eventSignals.isEmpty(), "Nothing before the slot opens")
        val id = state.nextEntityId
        state.signalStepAt(policy.slotAt(slot))

        val signal = state.eventSignals.single()
        assertEquals(EventSignalProjection(id, kind, signal.x, signal.y, policy.slotAt(slot) + policy.lifetimeSeconds), signal)
        assertEquals(id + 1, state.nextEntityId)
        assertEquals(policy.distance, sqrt(distanceSquared(0f, 0f, signal.x, signal.y)), 0.01f)
        // Heading is straight down the y axis; the signal sits sideAngle to one side of it.
        val offHeading = atan2(signal.y, signal.x) - atan2(300f, 0f)
        assertEquals(policy.sideAngle, abs(offHeading), 0.0001f)
        assertEquals("SIGNAL DETECTED // " + kind.displayName.uppercase(), state.message)
        assertTrue(state.message in RunVarietyNotices)
        assertEquals(1, state.signalsSpawned)
        assertEquals(slot + 1, state.nextSignalSlot)
        assertFalse(state.eventSignalTouched(), "A signal that was only seen is no engagement")
    }

    @Test
    fun theRunMeetsItsSignalsInItsSlotsAndKindsAndNoMore() {
        for (seed in listOf(1, 77, 5_150, 90_210)) {
            val state = arena(seed)
            val spawnedAt = mutableListOf<Int>()
            val kinds = mutableListOf<SignalKind>()
            var time = 0f
            while (time < content.tempo.bossAtSeconds) {
                time += 0.5f
                val before = state.signalsSpawned
                state.signalStepAt(time)
                if (state.signalsSpawned > before) {
                    spawnedAt += ((time - policy.firstAtSeconds) / policy.intervalSeconds).toInt()
                    kinds += state.eventSignals.single().kind
                }
            }
            assertEquals(plannedSignalSlots(seed, policy), spawnedAt, "Seed $seed slots")
            assertEquals(plannedSignalKinds(seed, policy), kinds, "Seed $seed kinds")
            assertEquals(policy.slots, state.nextSignalSlot)
            assertTrue(state.eventSignals.isEmpty(), "Seed $seed's last signal faded")
            assertEquals(SIGNAL_LOST, state.message)
        }
    }

    @Test
    fun aSignalExpiresAtTheEndOfItsWindowOrIsLostBeyondItsDistance() {
        val state = arena()
        state.elapsed = 200f
        state.liveSignal(SignalKind.MERCHANT, 900f, 0f, expiresAt = 210f)
        state.signalStepAt(210f.nextDown())
        assertEquals(1, state.eventSignals.size)
        state.message = ""
        state.signalStepAt(210f)
        assertTrue(state.eventSignals.isEmpty())
        assertEquals(SIGNAL_LOST, state.message)
        assertEquals(SIGNAL_LOST_SECONDS, state.messageTime)

        // At the lost distance the signal stays; one unit beyond it is gone.
        val far = arena()
        far.elapsed = 200f
        far.liveSignal(SignalKind.SHRINE, policy.lostDistance, 0f)
        far.signalStepAt(200.5f)
        assertEquals(1, far.eventSignals.size, "Exactly at the lost distance")
        far.coreX = -1f
        far.previousCoreX = -1f
        far.signalStepAt(201f)
        assertTrue(far.eventSignals.isEmpty(), "Beyond the lost distance")
        assertEquals(SIGNAL_LOST, far.message)
        assertEquals(0, far.signalsEngaged)
    }

    @Test
    fun touchingASignalQueuesItsPromptAndTheRunCountsAsEngaged() {
        val state = arena()
        state.elapsed = 300f
        val signal = state.liveSignal(SignalKind.GAMBLE, 400f, policy.touchRadius + 10f)
        // A path that passes just outside the touch radius touches nothing.
        state.sweepCoreTo(800f, 0f)
        state.signalStepAt(300.1f)
        assertEquals(1, state.eventSignals.size)
        // A path through the ring between two steps touches it, even with both ends outside it.
        state.coreX = 0f
        state.coreY = signal.y
        state.sweepCoreTo(800f, signal.y)
        state.signalStepAt(300.2f)
        assertTrue(state.eventSignals.isEmpty())
        assertEquals(listOf(SignalPrompt(SignalKind.GAMBLE, 0)), state.pendingSignalPrompts.toList())
        assertEquals(1, state.signalsEngaged)
        assertTrue(state.eventSignalTouched())
        assertTrue(state.optionalSitesTouched())
    }

    @Test
    fun aTouchedSignalOpensItsPromptAtTheEndOfTheFixedStep() {
        val state = arena(content = content.withoutWorldEvents())
        state.elapsed = 200f
        state.liveSignal(SignalKind.DERELICT, state.coreX + 5f, state.coreY)
        state.simulateStep(MutableGameState.FIXED_STEP)

        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.SIGNAL, state.choiceType)
        assertEquals(
            listOf(SignalOption.DERELICT_REPAIR, SignalOption.DERELICT_CARGO, SignalOption.DERELICT_VAULT, SignalOption.IGNORE),
            state.choices.map { it.signalOption },
        )
        assertTrue(state.choices.all { it.type == ChoiceType.SIGNAL && it.signalKind == SignalKind.DERELICT })
        assertEquals(listOf("Patch the hull", "Strip the cargo", "Pry the vault", "Ignore"), state.choices.map { it.title })
        assertFalse(state.choicesCanReroll, "A prompt is never rerolled")
        // The open prompt holds the head of the queue until a card resolves it.
        assertEquals(1, state.pendingSignalPrompts.size)
        state.take(SignalOption.IGNORE)
        assertTrue(state.pendingSignalPrompts.isEmpty())
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun aPromptOpensOnlyAfterTheLevelOfferAndTheRelicOfferQueuedWithIt() {
        val state = arena()
        state.pendingLevelChoices = 1
        state.pendingRelicChoices = 1
        state.pendingSignalPrompts = immutableListOf(SignalPrompt(SignalKind.SHRINE, 0))
        state.openNextPendingChoice()
        assertEquals(ChoiceType.ITEM, state.choiceType)
        state.choose(0)
        assertEquals(ChoiceType.RELIC, state.choiceType)
        state.choose(state.choices.indexOfFirst { it.relicAction == RelicChoiceAction.DECLINE })
        assertEquals(ChoiceType.SIGNAL, state.choiceType)
        assertEquals(SignalKind.SHRINE, state.choices.first().signalKind)
    }

    @Test
    fun merchantPricesFollowTheRebirthMatterGainAndTheClockInStepsOfFive() {
        fun prices(time: Float, rebirth: Int = 0, content: GameplayContentSnapshot = this.content): List<Int> =
            arena(content = content, rebirth = rebirth).apply { elapsed = time }.openPrompt(SignalKind.MERCHANT).let { state ->
                listOf(SignalOption.MERCHANT_RARE_OFFER, SignalOption.MERCHANT_EPIC_OFFER, SignalOption.MERCHANT_REROLLS)
                    .map { state.card(it).signalCost }
            }
        assertEquals(listOf(30, 75, 15), prices(0f))
        // f = 0.5 + 115 / 480: 44.4, 110.9 and 22.2 round to the nearest five.
        assertEquals(listOf(45, 110, 20), prices(115f))
        assertEquals(listOf(60, 150, 30), prices(240f))
        // Tier 10 gains Matter three times as fast, and pays three times as much.
        assertEquals(listOf(180, 450, 90), prices(240f, rebirth = 10))
        // A price never rounds below one step.
        val cheap = content.copy(variety = content.variety.copy(signals = policy.copy(tuning = tuning.copy(merchantRerollCost = 1f))))
        assertEquals(5, prices(0f, content = cheap)[2])
        val state = arena().apply { elapsed = 240f }.openPrompt(SignalKind.MERCHANT)
        assertEquals(tuning.merchantRerolls, state.card(SignalOption.MERCHANT_REROLLS).signalPayout)
    }

    @Test
    fun theMerchantSpendsUnbankedMatterAndChainsAnOfferAtItsFloor() {
        val state = arena().apply {
            elapsed = 240f
            runMatter = 500L
            itemDeck = ItemDeck.EVERYTHING
            level = 60
        }.openPrompt(SignalKind.MERCHANT)
        val rerolls = state.rerollsRemaining
        state.take(SignalOption.MERCHANT_EPIC_OFFER)
        assertEquals(350L, state.runMatter)
        assertEquals(rerolls, state.rerollsRemaining)
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.ITEM, state.choiceType)
        assertEquals(ItemRarity.EPIC, state.offerRarityFloor)
        assertTrue(state.pendingSignalPrompts.isEmpty())
        assertEquals(TRADE_COMPLETE, state.message)
        state.choose(0)
        assertNull(state.offerRarityFloor)
        assertEquals(GamePhase.RUNNING, state.phase)

        val bundle = arena().apply {
            elapsed = 240f
            runMatter = 30L
        }.openPrompt(SignalKind.MERCHANT)
        val before = bundle.rerollsRemaining
        bundle.take(SignalOption.MERCHANT_REROLLS)
        assertEquals(0L, bundle.runMatter)
        assertEquals(before + tuning.merchantRerolls, bundle.rerollsRemaining)
        assertEquals(GamePhase.RUNNING, bundle.phase)
    }

    @Test
    fun theMerchantNeverSpendsBankedMatter() {
        val state = arena().apply {
            elapsed = 240f
            runMatter = 100L
            bankedRunMatter = 41L
        }.openPrompt(SignalKind.MERCHANT)
        // 59 unbanked against a price of 60: locked, and taking it changes nothing.
        assertTrue(state.card(SignalOption.MERCHANT_RARE_OFFER).locked)
        assertTrue(state.card(SignalOption.MERCHANT_REROLLS).let { !it.locked && it.signalCost == 30 })
        val choices = state.choices
        state.take(SignalOption.MERCHANT_RARE_OFFER)
        assertSame(choices, state.choices)
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(100L, state.runMatter)
        assertEquals(1, state.pendingSignalPrompts.size)
        // One more unbanked Matter unlocks it.
        val enough = arena().apply {
            elapsed = 240f
            runMatter = 101L
            bankedRunMatter = 41L
        }.openPrompt(SignalKind.MERCHANT)
        assertFalse(enough.card(SignalOption.MERCHANT_RARE_OFFER).locked)
    }

    @Test
    fun aChainedFloorOfferDrawsLikeAnOrdinaryOfferAndNeverDealsBelowItsFloor() {
        /** The rarities a floor card of [option] deals at Rebirth [rebirth], after checking its draws match an offer. */
        fun dealt(option: SignalOption, rebirth: Int = 0): List<ItemRarity> {
            val prompt = arena(seed = 41, rebirth = rebirth).apply {
                elapsed = 240f
                runMatter = 1_000L
                itemDeck = ItemDeck.EVERYTHING
                level = 60
            }
            val plain = prompt.copyForReduction()
            prompt.openPrompt(option.kind!!)
            prompt.take(option)
            plain.openItemChoice()
            assertEquals(ChoiceType.ITEM, prompt.choiceType)
            assertEquals(plain.choices.size, prompt.choices.size, "$option deals a full offer")
            assertEquals(plain.rewardRandom.snapshot(), prompt.rewardRandom.snapshot(), "$option draws exactly what an offer draws")
            return prompt.choices.map { prompt.content.items[it.itemId!!].rarity }.sortedBy { it.rank }
        }
        assertTrue(dealt(SignalOption.MERCHANT_RARE_OFFER).all { it.rank >= ItemRarity.RARE.rank })
        assertTrue(dealt(SignalOption.MERCHANT_EPIC_OFFER).all { it == ItemRarity.EPIC })
        assertTrue(dealt(SignalOption.CACHE_CRATE).all { it == ItemRarity.EPIC })
        assertEquals(List(3) { ItemRarity.LEGENDARY }, dealt(SignalOption.CACHE_RELIQUARY, rebirth = 10))
        // A floor no remaining item meets deals the rarest that remains: tier 0 offers no Legendary item, tier 2 two.
        assertEquals(List(3) { ItemRarity.EPIC }, dealt(SignalOption.CACHE_RELIQUARY))
        assertEquals(listOf(ItemRarity.EPIC, ItemRarity.LEGENDARY, ItemRarity.LEGENDARY), dealt(SignalOption.CACHE_RELIQUARY, rebirth = 2))
    }

    @Test
    fun theShrineTradesIntegrityForBlessingsAndARelic() {
        val blood = arena().openPrompt(SignalKind.SHRINE)
        val impact = blood.damageMultiplier
        val weapon = blood.weaponPower
        assertEquals((blood.maxHp * tuning.shrineBloodShare).roundToInt(), blood.card(SignalOption.SHRINE_BLOOD).signalCost)
        blood.take(SignalOption.SHRINE_BLOOD)
        assertEquals(blood.maxHp * (1f - tuning.shrineBloodShare), blood.hp, 0.001f)
        assertEquals(impact + tuning.shrineBloodImpact, blood.damageMultiplier, 0.0001f)
        assertEquals(weapon + tuning.shrineBloodWeapon, blood.weaponPower, 0.0001f)
        assertEquals(0.0, blood.damageTaken, "Paying integrity is not a hit")
        assertEquals(BLESSING_RECEIVED, blood.message)

        val ward = arena().openPrompt(SignalKind.SHRINE)
        val maxHp = ward.maxHp
        val shield = ward.maxShield
        ward.take(SignalOption.SHRINE_WARD)
        assertEquals(maxHp - tuning.shrineWardIntegrity, ward.maxHp)
        assertEquals(shield + tuning.shrineWardShield, ward.maxShield)
        assertTrue(ward.damageReduction > 0f)

        val relic = arena().openPrompt(SignalKind.SHRINE)
        relic.take(SignalOption.SHRINE_RELIC)
        assertEquals(relic.maxHp * (1f - tuning.shrineRelicShare), relic.hp, 0.001f)
        assertEquals(ChoiceType.RELIC, relic.choiceType, "The Relic offer opens next")
        assertEquals(0, relic.pendingRelicChoices)
    }

    @Test
    fun integrityCardsLockUnlessMoreThanOneIntegrityWouldRemain() {
        fun locked(hp: Float, option: SignalOption): Boolean = arena().apply {
            maxHp = 100f
            this.hp = hp
        }.openPrompt(option.kind!!).card(option).locked
        // A card that costs share × 100 integrity locks at cost + 1 and opens just above it.
        for ((option, share) in listOf(
            SignalOption.SHRINE_BLOOD to tuning.shrineBloodShare,
            SignalOption.SHRINE_RELIC to tuning.shrineRelicShare,
            SignalOption.GAMBLE_INTEGRITY to tuning.gambleIntegrityShare,
        )) {
            val last = share * 100f + 1f
            assertTrue(locked(last, option), "$option at $last")
            assertFalse(locked(last.nextUp(), option), "$option above $last")
        }
        // The ward needs more than 40 maximum integrity.
        fun wardLocked(maxHp: Float) =
            arena().apply { this.maxHp = maxHp }.openPrompt(SignalKind.SHRINE).card(SignalOption.SHRINE_WARD).locked
        assertTrue(wardLocked(40f))
        assertFalse(wardLocked(40f.nextUp()))
    }

    @Test
    fun theShrinePreviewShowsTheStatsItsBlessingLeaves() {
        for (option in listOf(SignalOption.SHRINE_BLOOD, SignalOption.SHRINE_WARD)) {
            val state = arena().openPrompt(SignalKind.SHRINE)
            val preview = state.rewardPreviews()[state.choices.indexOfFirst { it.signalOption == option }]
            assertTrue(preview.changes.isNotEmpty(), "$option previews its stats")
            val taken = state.copyForReduction().apply { take(option) }.buildStats()
            preview.changes.forEach { change ->
                assertEquals(taken.single { it.name == change.name }.value, change.after, 0.0001f, "$option ${change.name}")
            }
        }
        val gamble = arena().openPrompt(SignalKind.GAMBLE)
        assertTrue(gamble.rewardPreviews().all { it.changes.isEmpty() }, "Gambles preview nothing")
    }

    @Test
    fun aRerollWagerWinsThreeOrLosesItsStakeAtEvenOdds() {
        var wins = 0
        for (index in 0 until WAGERS) {
            val state = arena().openPrompt(SignalKind.GAMBLE, index)
            assertEquals(1, state.card(SignalOption.GAMBLE_REROLL).signalCost)
            assertEquals(tuning.gambleRerollWin, state.card(SignalOption.GAMBLE_REROLL).signalPayout)
            val before = state.rerollsRemaining
            val streams = state.streams()
            state.take(SignalOption.GAMBLE_REROLL)
            val change = state.rerollsRemaining - before
            assertTrue(change == tuning.gambleRerollWin || change == -1, "Signal $index changed rerolls by $change")
            assertEquals(if (change > 0) WAGER_WON else WAGER_LOST, state.message)
            assertEquals(streams, state.streams(), "A wager draws from no stream")
            if (change > 0) wins++
            // The same signal's wager always ends the same way.
            val again = arena().openPrompt(SignalKind.GAMBLE, index).apply { take(SignalOption.GAMBLE_REROLL) }
            assertEquals(change, again.rerollsRemaining - before)
        }
        assertEquals(tuning.gambleRerollChance, wins / WAGERS.toFloat(), 0.075f)
        val broke = arena().apply { rerollsRemaining = 0 }.openPrompt(SignalKind.GAMBLE)
        assertTrue(broke.card(SignalOption.GAMBLE_REROLL).locked)
    }

    @Test
    fun aMatterWagerStakesAQuarterOfTheUnbankedMatterAndPaysRawMatter() {
        fun stakeOf(unbanked: Long) = arena().apply { runMatter = unbanked }.openPrompt(SignalKind.GAMBLE).card(SignalOption.GAMBLE_MATTER)
        assertTrue(stakeOf(9L).locked)
        assertEquals(10, stakeOf(10L).signalCost)
        assertFalse(stakeOf(10L).locked)
        assertEquals(10, stakeOf(43L).signalCost)
        assertEquals(50, stakeOf(200L).signalCost)
        assertEquals(110, stakeOf(200L).signalPayout)

        var wins = 0
        for (index in 0 until WAGERS) {
            val state = arena().apply {
                runMatter = 200L
                matterGain = 2f
            }.openPrompt(SignalKind.GAMBLE, index)
            state.take(SignalOption.GAMBLE_MATTER)
            // Won: 2.2 × 50 back for the stake of 50; lost: the stake. Matter gain plays no part.
            assertTrue(state.runMatter == 260L || state.runMatter == 150L, "Signal $index left ${state.runMatter}")
            assertEquals(if (state.runMatter > 200L) WAGER_WON else WAGER_LOST, state.message)
            if (state.runMatter > 200L) wins++
        }
        assertEquals(tuning.gambleMatterChance, wins / WAGERS.toFloat(), 0.075f)
    }

    @Test
    fun anIntegrityWagerAlwaysCostsItsStakeAndSometimesOpensAnEpicOffer() {
        var wins = 0
        for (index in 0 until WAGERS) {
            val state = arena().apply {
                itemDeck = ItemDeck.EVERYTHING
                level = 60
            }.openPrompt(SignalKind.GAMBLE, index)
            state.take(SignalOption.GAMBLE_INTEGRITY)
            assertEquals(state.maxHp * (1f - tuning.gambleIntegrityShare), state.hp, 0.001f)
            if (state.phase == GamePhase.CHOICE) {
                wins++
                assertEquals(ChoiceType.ITEM, state.choiceType)
                assertEquals(ItemRarity.EPIC, state.offerRarityFloor)
                assertEquals(WAGER_WON, state.message)
            } else {
                assertEquals(WAGER_LOST, state.message)
            }
        }
        assertEquals(tuning.gambleIntegrityChance, wins / WAGERS.toFloat(), 0.075f)
    }

    @Test
    fun aCursedCachePaysWithACurseItsBuffKeepsForItsSeconds() {
        val crate = arena().apply {
            elapsed = 300f
            itemDeck = ItemDeck.EVERYTHING
            level = 60
        }.openPrompt(SignalKind.CURSED_CACHE)
        crate.take(SignalOption.CACHE_CRATE)
        assertEquals(
            ActiveBuff(BuffSource.signal(SignalOption.CACHE_CRATE), BuffStat.DAMAGE_TAKEN, tuning.crateCurse, 1, 450f),
            crate.signalBuff(SignalOption.CACHE_CRATE),
        )
        assertEquals(ItemRarity.EPIC, crate.offerRarityFloor)
        assertEquals(CURSE_BOUND, crate.message)

        val reliquary = arena().apply {
            elapsed = 300f
            itemDeck = ItemDeck.EVERYTHING
            level = 60
        }.openPrompt(SignalKind.CURSED_CACHE)
        reliquary.take(SignalOption.CACHE_RELIQUARY)
        assertEquals(
            ActiveBuff(BuffSource.signal(SignalOption.CACHE_RELIQUARY), BuffStat.DAMAGE, -tuning.reliquaryCurse, 1, 540f),
            reliquary.signalBuff(SignalOption.CACHE_RELIQUARY),
        )
        assertEquals(ItemRarity.LEGENDARY, reliquary.offerRarityFloor)

        val idol = arena().apply { elapsed = 300f }.openPrompt(SignalKind.CURSED_CACHE)
        idol.take(SignalOption.CACHE_IDOL)
        assertEquals(
            ActiveBuff(BuffSource.signal(SignalOption.CACHE_IDOL), BuffStat.COOLING, -tuning.idolCurse, 1, 450f),
            idol.signalBuff(SignalOption.CACHE_IDOL),
        )
        assertEquals(ChoiceType.RELIC, idol.choiceType)
    }

    @Test
    fun aCurseLandsWithEverySixteenBuffsLiveByDroppingTheOldestOtherBuff() {
        val state = arena().apply { elapsed = 300f }
        val idol = ActiveBuff(BuffSource.signal(SignalOption.CACHE_IDOL), BuffStat.COOLING, -0.4f, 1, 400f)
        val others = List(MutableGameState.MAX_BUFFS - 1) { index -> ActiveBuff(index + 1, BuffStat.WEAPON, 0.01f, 1, 350f + index) }
        // An expired buff first, then a signal curse, then fifteen others: sixteen live.
        val expired = ActiveBuff(99, BuffStat.WEAPON, 0.5f, 1, 100f)
        state.buffs = listOf(expired, idol) + others
        state.openPrompt(SignalKind.CURSED_CACHE)
        state.take(SignalOption.CACHE_CRATE)
        val live = state.buffs.filter { it.expiresAt > state.elapsed }
        assertEquals(MutableGameState.MAX_BUFFS, live.size)
        assertTrue(state.signalBuff(SignalOption.CACHE_CRATE) != null, "The curse landed")
        assertTrue(idol in state.buffs, "Another signal's curse is kept")
        assertFalse(state.buffs.any { it.sourceKey == 1 }, "The oldest other buff made room")
        assertTrue(others.drop(1).all { it in state.buffs })
    }

    @Test
    fun aDerelictRepairsSalvagesMatterOrLeavesAKey() {
        val repair = arena().apply { hp = 30f }.openPrompt(SignalKind.DERELICT)
        assertEquals((repair.maxHp * tuning.derelictRepairShare).roundToInt(), repair.card(SignalOption.DERELICT_REPAIR).signalPayout)
        repair.take(SignalOption.DERELICT_REPAIR)
        assertEquals(30f + repair.maxHp * tuning.derelictRepairShare, repair.hp, 0.001f)
        assertEquals(SALVAGE_COMPLETE, repair.message)

        fun cargo(rebirth: Int): Pair<Int, Long> {
            val state = arena(rebirth = rebirth).apply {
                elapsed = 240f
                heirloomPending = false
            }.openPrompt(SignalKind.DERELICT)
            val shown = state.card(SignalOption.DERELICT_CARGO).signalPayout
            val before = state.runMatter
            state.take(SignalOption.DERELICT_CARGO)
            return shown to state.runMatter - before
        }
        val (shown, granted) = cargo(rebirth = 0)
        val matterGain = arena().matterGain
        // At 240 s the cargo is its base, through the run's Matter gain.
        assertEquals((tuning.derelictCargoMatter * matterGain).toLong(), granted)
        assertEquals(granted, shown.toLong())
        // The Rebirth tier's Matter gain applies once, through the run's Matter gain.
        val (_, tierTen) = cargo(rebirth = 10)
        assertEquals((tuning.derelictCargoMatter * arena(rebirth = 10).matterGain).toLong(), tierTen)

        val vault = arena().openPrompt(SignalKind.DERELICT)
        vault.take(SignalOption.DERELICT_VAULT)
        val key = vault.pickups.single { it.type == PickupType.KEY }
        assertEquals(vault.coreX to vault.coreY, key.x to key.y)
    }

    @Test
    fun ignoringAPromptChangesNothingButTheQueue() {
        for (kind in SignalKind.entries) {
            val state = arena().apply {
                elapsed = 300f
                runMatter = 400L
            }.openPrompt(kind)
            val reference = state.copyForReduction()
            state.take(SignalOption.IGNORE)
            assertTrue(state.pendingSignalPrompts.isEmpty())
            assertEquals(GamePhase.RUNNING, state.phase)
            assertEquals(reference.runMatter, state.runMatter)
            assertEquals(reference.hp, state.hp)
            assertEquals(reference.rerollsRemaining, state.rerollsRemaining)
            assertEquals(reference.buffs, state.buffs)
            assertEquals(reference.pickups.size, state.pickups.size)
            assertEquals(reference.pendingRelicChoices, state.pendingRelicChoices)
            assertEquals(reference.buildStats(), state.buildStats())
            assertEquals(reference.streams(), state.streams())
        }
    }

    @Test
    fun aForkResolvesAPromptWithoutTouchingTheCommittedRun() {
        val state = arena().apply {
            elapsed = 240f
            runMatter = 100L
        }.openPrompt(SignalKind.MERCHANT)
        val prompts = state.pendingSignalPrompts
        val fork = state.copyForReduction()
        fork.take(SignalOption.MERCHANT_REROLLS)
        assertEquals(70L, fork.runMatter)
        assertEquals(100L, state.runMatter)
        assertSame(prompts, state.pendingSignalPrompts)
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.SIGNAL, state.choiceType)
    }

    @Test
    fun theArchitectAndOtherWorldLayersDropTheSignalSilentlyAndSkipTheirSlots() {
        val boss = arena()
        boss.elapsed = 300f
        boss.liveSignal(SignalKind.MERCHANT, 500f, 0f)
        boss.bossSpawned = true
        boss.message = "THE ARCHITECT"
        boss.signalStepAt(300.1f)
        assertTrue(boss.eventSignals.isEmpty())
        assertEquals("THE ARCHITECT", boss.message)
        val spawned = boss.signalsSpawned
        boss.signalStepAt(policy.slotAt(policy.slots - 1) + 1f)
        assertEquals(policy.slots, boss.nextSignalSlot, "Every slot passed is skipped")
        assertEquals(spawned, boss.signalsSpawned)

        val layer = arena()
        layer.elapsed = 300f
        layer.liveSignal(SignalKind.SHRINE, 500f, 0f)
        layer.worldLayer = WorldLayer.ZENITH
        layer.message = ""
        layer.signalStepAt(300.1f)
        assertTrue(layer.eventSignals.isEmpty())
        assertEquals("", layer.message)
        layer.signalStepAt(policy.slotAt(policy.slots - 1) + 1f)
        assertEquals(policy.slots, layer.nextSignalSlot)
        assertEquals(1, layer.signalsSpawned)
    }

    @Test
    fun anEngagementHoldsTheSlotUpToTheTrialDelayAndTheSignalKeepsItsWindow() {
        val seed = 5_150
        val slot = plannedSignalSlots(seed, policy).first()
        val opens = policy.slotAt(slot)
        val trial = SiteState(1L, SiteKind.SEALED_ANOMALY, 900f, 900f, Float.POSITIVE_INFINITY, active = true)

        val waiting = arena(seed)
        waiting.sites = listOf(trial)
        waiting.signalStepAt(opens)
        waiting.signalStepAt(opens + 6f)
        assertTrue(waiting.eventSignals.isEmpty(), "The slot waits for the trial")
        assertEquals(slot, waiting.nextSignalSlot)
        waiting.sites = emptyList()
        waiting.signalStepAt(opens + 6.1f)
        assertEquals(opens + policy.lifetimeSeconds, waiting.eventSignals.single().expiresAt, "A late signal keeps its slot's window")

        val skipped = arena(seed)
        skipped.sites = listOf(trial)
        skipped.signalStepAt(opens + policy.trialDelaySeconds.nextDown() - 0.01f)
        assertEquals(slot, skipped.nextSignalSlot)
        skipped.signalStepAt(opens + policy.trialDelaySeconds)
        assertEquals(slot + 1, skipped.nextSignalSlot, "The slot is skipped once the trial delay passes")
        skipped.sites = emptyList()
        skipped.signalStepAt(opens + policy.trialDelaySeconds + 0.1f)
        assertTrue(skipped.eventSignals.isEmpty())
        assertEquals(0, skipped.signalsSpawned)
    }

    @Test
    fun aRebaseMovesTheLiveSignalAndPilotsSeeItsMark() {
        val state = arena()
        state.elapsed = 300f
        state.liveSignal(SignalKind.GAMBLE, 1_000f, -200f)
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        assertEquals(1, state.optionalSiteMarks(marks))
        assertEquals(listOf(1_000f, -200f, policy.touchRadius), marks.take(3))
        state.rebaseRunVariety(400f, -100f)
        assertEquals(600f to -100f, state.eventSignals.single().let { it.x to it.y })
        state.optionalSiteMarks(marks)
        assertEquals(listOf(600f, -100f), marks.take(2))
        assertFalse(state.optionalSitesTouched(), "A mark alone is no engagement")
    }

    @Test
    fun signalsDrawFromNoStreamAcrossAWholeRunOfTouchedPrompts() {
        val state = arena(seed = 90_210)
        val streams = state.streams()
        var time = 0f
        var resolved = 0
        while (time < content.tempo.bossAtSeconds) {
            time += 0.5f
            state.signalStepAt(time)
            val signal = state.eventSignals.firstOrNull() ?: continue
            state.sweepCoreTo(signal.x, signal.y)
            state.signalStepAt(time + 0.01f)
            state.openNextPendingChoice()
            assertEquals(ChoiceType.SIGNAL, state.choiceType)
            state.take(SignalOption.IGNORE)
            resolved++
        }
        assertEquals(policy.perRun, resolved)
        assertEquals(policy.perRun, state.signalsEngaged)
        assertEquals(streams, state.streams())
    }

    @Test
    fun theQuietArenaAndTheOffSwitchSpawnNothing() {
        val quiet = arena().apply { quietArenaForTesting() }
        val off = arena(content = content.withoutEventSignals())
        for (state in listOf(quiet, off)) {
            var time = 0f
            while (time < content.tempo.bossAtSeconds) {
                time += 1f
                state.signalStepAt(time)
            }
            assertEquals(0, state.signalsSpawned)
            assertTrue(state.eventSignals.isEmpty())
        }
        assertEquals(0, off.nextSignalSlot, "An off policy never advances its slots")
        assertNotEquals(0, quiet.nextSignalSlot)
    }

    private companion object {
        const val SEEDS = 2_000
        const val WAGERS = 400
    }
}
