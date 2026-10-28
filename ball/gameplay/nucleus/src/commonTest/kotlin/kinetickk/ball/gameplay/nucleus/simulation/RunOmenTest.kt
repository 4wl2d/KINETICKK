// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.OmenPolicy
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.RunVarietyNotices
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerProfile
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Run omens (variety.md §2): the production start arms the offer, which opens after the first fixed step before the
 * Heirloom, deals three eligible omens and No omen from its own stream, is never rerolled, and folds an accepted omen's
 * typed effects through the run's existing seams. The arm gate against No omen is RunOmenArmsGateTest (V10).
 */
class RunOmenTest {
    private val definitions = canonicalGameplayContent.variety.omens.definitions

    @Test
    fun theProductionStartArmsTheOfferAndItOpensAfterTheFirstStep() {
        val started = startThroughTheNucleus(seed = 2_026)
        val model = assertNotNull(started.engine).model
        assertTrue(model.offersRunOmen)
        assertTrue(model.omenPending)
        assertEquals(GamePhase.RUNNING, model.phase)

        val offered = frame(started, GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 30f))
        val render = assertNotNull(GameplayNucleus.renderSnapshot(offered).renderModel)
        assertEquals(GamePhase.CHOICE, render.phase)
        assertEquals(ChoiceType.OMEN, render.choiceType)
        assertEquals(1, (render.elapsed / MutableGameState.FIXED_STEP).roundToInt(), "It opens at the end of the first step")
        assertEquals(4, render.choices.size)
        assertNull(render.choices.last().omenId)
        assertFalse(render.choicesCanReroll, "The render mirror never offers a reroll")

        val declined = offered.declineOmenIfOffered()
        val running = assertNotNull(declined.engine).model
        assertEquals(GamePhase.RUNNING, running.phase)
        assertNull(running.runOmen)
        assertFalse(running.omenPending)
        // Once declined it never comes back.
        val later = frame(declined, GameplayInteractionPulse.FrameElapsed.fromValidated(0.5f))
        assertEquals(GamePhase.RUNNING, assertNotNull(later.engine).model.phase)
    }

    @Test
    fun anUnarmedRunOrDisabledOmensNeverOpenTheOffer() {
        val unarmed = startedRun(armed = false)
        assertFalse(unarmed.offersRunOmen)
        assertFalse(unarmed.omenPending)
        val disabled = startedRun(content = withOmens(OmenPolicy(enabled = false)))
        assertTrue(disabled.offersRunOmen)
        assertFalse(disabled.omenPending)
        for (state in listOf(unarmed, disabled)) {
            repeat(240) {
                state.step()
                assertNotEquals(ChoiceType.OMEN, state.choiceType)
            }
            assertNull(state.runOmen)
        }
        // A pending flag with omens switched off deals nothing and the run goes on.
        val pending = startedRun(armed = false, content = withOmens(OmenPolicy(enabled = false))).apply { omenPending = true }
        pending.openNextPendingChoice()
        assertEquals(GamePhase.RUNNING, pending.phase)
        assertFalse(pending.omenPending)
        // The arming flag is part of the run and survives every reduction copy.
        assertTrue(startedRun().copyForReduction().offersRunOmen)
    }

    @Test
    fun theOfferDealsThreeDistinctEligibleOmensAndNoOmenBeforeTheHeirloom() {
        val state = startedRun(seed = 77, rebirth = 1) { heirloomCandidates = listOf(RelicId.GYRE_ENGINE) }
        assertTrue(state.heirloomPending)
        state.step()
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.OMEN, state.choiceType, "The omen opens before the Heirloom")
        val omens = state.choices.dropLast(1)
        assertEquals(3, omens.size)
        assertEquals(3, omens.map { it.omenId }.toSet().size)
        omens.forEach { card ->
            val omen = definitions[assertNotNull(card.omenId).ordinal]
            assertTrue(state.omenEligible(omen))
            assertEquals(ChoiceType.OMEN, card.type)
            assertEquals(omen.name, card.title)
            assertEquals(omen.upside, card.description)
        }
        val decline = state.choices.last()
        assertNull(decline.omenId)
        assertEquals(NO_OMEN_TITLE, decline.title)
        assertEquals(NO_OMEN_TEXT, decline.description)
        assertEquals("DECLINE", decline.tag)

        // Never rerolled, though the run holds rerolls.
        assertTrue(state.rerollsRemaining > 0)
        assertFalse(state.choicesCanReroll)
        assertFalse(state.toRenderModel().choicesCanReroll)
        val cards = state.choices
        state.rerollChoices()
        assertSame(cards, state.choices)

        // No omen hands the run to the Heirloom.
        val rerolls = state.rerollsRemaining
        state.choose(state.choices.indexOfFirst { it.omenId == null })
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.RELIC, state.choiceType)
        assertTrue(state.choices.all { it.heirloom })
        assertNull(state.runOmen)
        assertEquals(rerolls, state.rerollsRemaining)
    }

    @Test
    fun theOfferIsTheSameForASeedVariesAcrossSeedsAndDrawsFromNoRunStream() {
        fun offer(seed: Int): List<OmenId?> = startedRun(seed = seed).apply { step() }.choices.map { it.omenId }
        assertEquals(offer(31), offer(31))
        val seen = (1..60).flatMap { seed -> offer(seed).filterNotNull() }.toSet()
        assertEquals(OmenId.entries.toSet(), seen, "Every omen is offered somewhere over 60 seeds")

        for (seed in 1..8) {
            val armed = startedRun(seed = seed).apply { step() }
            val unarmed = startedRun(seed = seed, armed = false).apply { step() }
            assertEquals(ChoiceType.OMEN, armed.choiceType)
            assertEquals(unarmed.gameplayRandom.snapshot(), armed.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(unarmed.rewardRandom.snapshot(), armed.rewardRandom.snapshot(), "Seed $seed reward stream")
            assertEquals(unarmed.spawnRandom.snapshot(), armed.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertEquals(unarmed.siteRandom.snapshot(), armed.siteRandom.snapshot(), "Seed $seed site stream")
        }
    }

    @Test
    fun noOmenLeavesTheRunAsTheUnarmedRunAfterSixHundredSteps() {
        for (seed in listOf(5, 1_618)) {
            val armed = startedRun(seed = seed)
            val unarmed = startedRun(seed = seed, armed = false)
            armed.step()
            unarmed.step()
            armed.choose(armed.choices.indexOfFirst { it.omenId == null })
            repeat(599) {
                armed.step()
                unarmed.step()
            }
            assertEquals(GamePhase.RUNNING, armed.phase)
            assertEquals(unarmed.outcome(), armed.outcome(), "Seed $seed")
        }
    }

    @Test
    fun eachOmenFoldsItsTypedEffectsThroughTheRunSeams() {
        for (id in OmenId.entries) {
            val omen = definitions[id.ordinal]
            val base = startedRun(seed = 404, armed = false)
            val accepted = startedRun(seed = 404, armed = false).apply { acceptRunOmen(id) }
            assertEquals(id, accepted.runOmen)

            // Stats go through the item seam, gains then costs.
            val stats = startedRun(seed = 404, armed = false)
            (omen.gains + omen.costs).filterIsInstance<RunModifierEffect.Stat>().forEach { stats.applyStatDelta(it.effect, it.amount) }
            var matter = base.matterGain
            var rerolls = base.rerollsRemaining
            var health = 1f
            var speed = 1f
            var incoming = 1f
            var spawnRate = 1f
            var eliteRate = 1f
            var mask = 0
            for (effect in omen.gains + omen.costs) {
                when (effect) {
                    is RunModifierEffect.Stat -> Unit
                    is RunModifierEffect.MatterGainScale -> matter *= effect.factor
                    is RunModifierEffect.Rerolls -> rerolls = maxOf(0, rerolls + effect.delta)
                    is RunModifierEffect.HostileScale -> {
                        health *= effect.health
                        speed *= effect.speed
                        incoming *= effect.incomingDamage
                        spawnRate *= effect.spawnRate
                        eliteRate *= effect.eliteRate
                    }
                    is RunModifierEffect.Mutator -> mask = mask or (1 shl effect.mutator.ordinal)
                    is RunModifierEffect.KeynoteFloor -> Unit
                }
            }
            val withoutMatter = { state: MutableGameState -> state.buildStats().filter { it.name != "Matter gain" } }
            assertEquals(withoutMatter(stats), withoutMatter(accepted), "$id stats")
            assertEquals(matter, accepted.matterGain, 1e-6f, "$id Matter gain")
            assertEquals(rerolls, accepted.rerollsRemaining, "$id rerolls")
            val before = base.activeRebirthProfile
            val after = accepted.activeRebirthProfile
            assertEquals(before.enemyHealthMultiplier * health, after.enemyHealthMultiplier, 1e-6f, "$id health")
            assertEquals(before.enemySpeedMultiplier * speed, after.enemySpeedMultiplier, 1e-6f, "$id speed")
            assertEquals(before.incomingDamageMultiplier * incoming, after.incomingDamageMultiplier, 1e-6f, "$id damage")
            assertEquals(before.spawnRateMultiplier * spawnRate, after.spawnRateMultiplier, 1e-6f, "$id spawn rate")
            assertEquals(before.eliteRateMultiplier * eliteRate, after.eliteRateMultiplier, 1e-6f, "$id Elite rate")
            // The overlay never touches the tier itself: its number, mutators and Data curve stay.
            assertEquals(before.tier, after.tier)
            assertEquals(before.mutators, after.mutators)
            assertEquals(before.dataRequirementScale, after.dataRequirementScale)
            assertEquals(mask, accepted.runMutatorMask, "$id mutators")
            RebirthMutator.entries.forEach { mutator ->
                assertEquals(mask and (1 shl mutator.ordinal) != 0, accepted.runMutatorActive(mutator), "$id $mutator")
            }
            // The first Elite follows the scaled profile while it is still ahead.
            assertEquals(after.eliteInterval(canonicalGameplayContent.tempo.firstEliteAtSeconds), accepted.nextEliteAt, "$id first Elite")
            if (eliteRate > 1f) assertTrue(accepted.nextEliteAt < base.nextEliteAt, "$id brings the first Elite sooner")
            assertEquals("OMEN // ${omen.name.uppercase()}", accepted.message)
            assertTrue(accepted.message in RunVarietyNotices)
            assertEquals(2.2f, accepted.messageTime)
        }
    }

    @Test
    fun theShippedOmensReadAsTheirTexts() {
        val base = startedRun(seed = 12, armed = false)
        fun accepted(id: OmenId) = startedRun(seed = 12, armed = false).apply { acceptRunOmen(id) }
        accepted(OmenId.GLASS_SUN).let {
            assertEquals(base.damageMultiplier + 0.25f, it.damageMultiplier, 1e-6f)
            assertEquals(base.weaponPower + 0.2f, it.weaponPower, 1e-6f)
            assertEquals(base.maxHp - 30f, it.maxHp, 1e-4f)
            assertEquals(it.maxHp, it.hp, "Integrity never exceeds the lowered ceiling")
        }
        accepted(OmenId.RICH_VEINS).let { assertEquals(base.matterGain * 1.5f, it.matterGain, 1e-6f) }
        accepted(OmenId.ASCETIC_PATH).let {
            assertEquals(base.rerollsRemaining + 2, it.rerollsRemaining)
            assertEquals(base.matterGain * 0.6f, it.matterGain, 1e-6f)
        }
        accepted(OmenId.STILL_WATER).let {
            assertEquals(base.maxShield + 25f, it.maxShield)
            assertEquals(base.shield + 25f, it.shield)
            assertEquals(base.damageMultiplier - 0.15f, it.damageMultiplier, 1e-6f)
        }
        // Crowned Quarry's Hardened Elites also bring the 6:00 Herald at tier 0 (the mutator's full meaning).
        accepted(OmenId.CROWNED_QUARRY).let {
            assertTrue(it.runMutatorActive(RebirthMutator.HARDENED_ELITES))
            assertFalse(it.rebirthProfile.has(RebirthMutator.HARDENED_ELITES))
        }
        // A reroll cost never takes the run below zero.
        val broke = startedRun(seed = 12, armed = false).apply { rerollsRemaining = 0 }
        broke.acceptRunOmen(OmenId.AMPLIFIED_KEYNOTE)
        assertEquals(0, broke.rerollsRemaining)
    }

    @Test
    fun theKeynoteOmenFillsTheKeynoteAndNeedsOne() {
        val state = startedRun(seed = 23, armed = false)
        val tag = assertNotNull(state.keynoteTag)
        val element = assertNotNull(state.keynoteElement)
        val reward = state.rewardRandom.snapshot()
        state.acceptRunOmen(OmenId.AMPLIFIED_KEYNOTE)
        val universe = state.content.items.filter { it.minimumRebirth <= state.rebirthRank }
        assertShareHolds(state, universe.filter { it.isKeynotePassive(tag) }, 0.95f)
        assertShareHolds(state, universe.filter { it.isKeynotePassive(element) }, 0.95f)
        assertEquals(reward, state.rewardRandom.snapshot(), "The floors draw nothing")

        val keynoteOmen = definitions[OmenId.AMPLIFIED_KEYNOTE.ordinal]
        assertTrue(state.omenEligible(keynoteOmen))
        val plain = startedRun(seed = 23, content = canonicalGameplayContent.withoutKeynote())
        assertFalse(plain.omenEligible(keynoteOmen))
        repeat(40) { seed ->
            val offer = startedRun(seed = seed, content = canonicalGameplayContent.withoutKeynote()).apply { step() }
            assertTrue(offer.choices.none { it.omenId == OmenId.AMPLIFIED_KEYNOTE }, "Seed $seed")
        }
    }

    @Test
    fun mutatorOmensLeaveTheOfferAtTiersThatAlreadyRunTheirMutator() {
        for (tier in 0..10) {
            val state = startedRun(seed = 900 + tier, rebirth = tier)
            definitions.forEach { omen ->
                val mutator = omen.costs.filterIsInstance<RunModifierEffect.Mutator>().singleOrNull()?.mutator
                val expected = mutator == null || !state.rebirthProfile.has(mutator)
                assertEquals(expected, state.omenEligible(omen), "Tier $tier ${omen.id}")
            }
            state.step()
            assertEquals(ChoiceType.OMEN, state.choiceType, "Tier $tier")
            assertEquals(4, state.choices.size, "Tier $tier still deals three omens and No omen")
            state.choices.mapNotNull { it.omenId }.forEach { id -> assertTrue(state.omenEligible(definitions[id.ordinal])) }
        }
        val top = startedRun(rebirth = 10)
        assertEquals(7, definitions.count { top.omenEligible(it) }, "Five mutator omens drop out at tier 10")
    }

    @Test
    fun runMutatorActiveIsTheTiersOwnWhileTheMaskIsEmpty() {
        for (tier in 0..10) {
            val state = startedRun(seed = 50 + tier, rebirth = tier, armed = false)
            assertEquals(0, state.runMutatorMask)
            RebirthMutator.entries.forEach { mutator ->
                assertEquals(state.rebirthProfile.has(mutator), state.runMutatorActive(mutator), "Tier $tier $mutator")
            }
        }
    }

    @Test
    fun faultLineRebuildsTheObstacleWindowWithUnstableAsteroidsButItsPreviewDoesNot() {
        val state = (1..60).asSequence()
            .map { seed -> startedRun(seed = seed).apply { step() } }
            .first { offer -> offer.choices.any { it.omenId == OmenId.FAULT_LINE } }
        val calm = state.obstacleField
        assertTrue(calm.obstacles.none { it.kind == ObstacleKind.UNSTABLE })
        state.rewardPreviews()
        assertSame(calm, state.obstacleField, "A preview rebuilds no world")
        state.choose(state.choices.indexOfFirst { it.omenId == OmenId.FAULT_LINE })
        assertTrue(state.runMutatorActive(RebirthMutator.FRACTURED_SPACE))
        assertTrue(state.obstacleField.obstacles.any { it.kind == ObstacleKind.UNSTABLE })
        assertTrue(state.obstacleField.size > calm.size, "Fractured space holds more obstacles")

        // A harness arm forcing the omen at the start rebuilds the window the same way.
        val forced = startedRun(seed = 61, armed = false).apply { forceRunOmenForTesting(OmenId.FAULT_LINE) }
        assertTrue(forced.obstacleField.obstacles.any { it.kind == ObstacleKind.UNSTABLE })
        assertEquals(OmenId.FAULT_LINE, forced.runOmen)

        val preview = startedRun(seed = 61, armed = false)
        val window = preview.obstacleField
        preview.acceptRunOmen(OmenId.FAULT_LINE, preview = true)
        assertSame(window, preview.obstacleField)
        assertTrue(preview.runMutatorActive(RebirthMutator.FRACTURED_SPACE))
    }

    @Test
    fun aPreviewShowsTheAcceptedStatsAndLeavesTheRunUntouched() {
        for (seed in 1..6) {
            val state = startedRun(seed = seed).apply { step() }
            val beforeStats = state.buildStats()
            val profile = state.activeRebirthProfile
            val deck = state.itemDeck
            val previews = state.rewardPreviews()
            assertEquals(state.choices.size, previews.size)
            state.choices.forEachIndexed { index, card ->
                val preview = previews[index]
                if (card.omenId == null) {
                    assertTrue(preview.changes.isEmpty(), "Seed $seed: No omen previews nothing")
                    return@forEachIndexed
                }
                val accepted = state.copyForReduction().apply { choose(index) }
                val afterStats = accepted.buildStats()
                val changed = afterStats.filterIndexed { row, stat -> kotlin.math.abs(stat.value - beforeStats[row].value) >= 0.00001f }
                assertEquals(changed.map { it.name }, preview.changes.map { it.name }, "Seed $seed ${card.omenId}")
                preview.changes.forEach { change ->
                    assertEquals(afterStats.single { it.name == change.name }.value, change.after, "Seed $seed ${card.omenId} ${change.name}")
                }
            }
            // The committed run is untouched by its previews.
            assertNull(state.runOmen)
            assertEquals(0, state.runMutatorMask)
            assertEquals(profile, state.activeRebirthProfile)
            assertSame(deck, state.itemDeck)
            assertEquals(beforeStats, state.buildStats())
            assertEquals(ChoiceType.OMEN, state.choiceType)
        }
    }

    @Test
    fun choosingAnOmenAnnouncesItAndNotesItsStatChanges() {
        val state = startedRun(seed = 8).apply { step() }
        state.takeVisualFxCues()
        val index = state.choices.indexOfFirst { it.omenId != null }
        val id = assertNotNull(state.choices[index].omenId)
        state.choose(index)
        assertEquals(id, state.runOmen)
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals("OMEN // ${definitions[id.ordinal].name.uppercase()}", state.message)
        val notice = state.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>().single()
        assertEquals(definitions[id.ordinal].name, notice.title)
    }

    @Test
    fun aStatsRebuildKeepsTheOmensStatAndMatterEffects() {
        for (id in OmenId.entries) {
            val state = startedRun(seed = 70, armed = false).apply { acceptRunOmen(id) }
            val stats = state.buildStats()
            val matter = state.matterGain
            val rerolls = state.rerollsRemaining
            val profile = state.activeRebirthProfile
            state.resetRunStats()
            assertEquals(stats, state.buildStats(), "$id")
            assertEquals(matter, state.matterGain, "$id")
            assertEquals(rerolls, state.rerollsRemaining, "$id rerolls are not reapplied")
            assertEquals(profile, state.activeRebirthProfile, "$id hostile scales are not reapplied")
        }
        // Without an omen a rebuild is the plain run's.
        val plain = startedRun(seed = 70, armed = false)
        val stats = plain.buildStats()
        plain.resetRunStats()
        assertEquals(stats, plain.buildStats())
    }

    private fun startedRun(
        seed: Int = 5_150,
        rebirth: Int = 0,
        armed: Boolean = true,
        content: GameplayContentSnapshot = canonicalGameplayContent,
        before: MutableGameState.() -> Unit = {},
    ): MutableGameState = MutableGameState(
        content,
        seed = seed,
        initialMatter = 0,
        initialRebirthLevel = rebirth,
        offerRunOmen = armed,
    ).apply {
        resize(1_280f, 720f)
        before()
        startRun()
        takeVisualFxCues()
        takeSoundCues()
    }

    /** One fixed step of a running run. */
    private fun MutableGameState.step() {
        check(phase == GamePhase.RUNNING) { "Stepping a run in $phase" }
        simulateStep(MutableGameState.FIXED_STEP)
        takeVisualFxCues()
        takeSoundCues()
    }

    private fun withOmens(omens: OmenPolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(variety = canonicalGameplayContent.variety.copy(omens = omens))

    private fun startThroughTheNucleus(seed: Int): GameplayState {
        val profile = PlayerProfile()
        return assertIs<GameplayDecision.Accepted>(
            GameplayNucleus.decide(
                GameplayState.initial(RunId(3), canonicalGameplayContent),
                GameplayNucleusPulse.StartRun,
                GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
                    canonicalGameplayContent,
                    GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix,
                        profile.collection, profile.rebirthProgress),
                    seed,
                ))),
            ),
        ).frame.nextState
    }

    private fun frame(state: GameplayState, pulse: GameplayInteractionPulse): GameplayState =
        assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))).frame.nextState

    private fun assertShareHolds(state: MutableGameState, items: List<ItemDefinition>, share: Float) {
        val dealt = items.count { it.id in state.itemDeck }
        assertTrue(dealt.toFloat() >= share * items.size, "$dealt of ${items.size} dealt, share $share")
    }

    /** What a run plays out to: positions, health, economy, entities, obstacles, every stream and the run's modifiers. */
    private fun MutableGameState.outcome(): List<Any?> = listOf(
        elapsed.toRawBits(), coreX.toRawBits(), coreY.toRawBits(), velocityX.toRawBits(), velocityY.toRawBits(),
        hp.toRawBits(), heat.toRawBits(), level, data, kills, runMatter, matterFraction.toRawBits(), rerollsRemaining,
        message, messageTime.toRawBits(), nextEliteAt.toRawBits(), spawnClock.toRawBits(), activeRebirthProfile,
        runMutatorMask, runOmen, keynoteTag, keynoteElement, featuredAspect, itemDeck.dealtIds(), buildStats(),
        gameplayRandom.snapshot(), rewardRandom.snapshot(), spawnRandom.snapshot(), siteRandom.snapshot(),
        enemies.map { listOf(it.id, it.type, it.x.toRawBits(), it.y.toRawBits(), it.hp.toRawBits()) },
        projectiles.size, pickups.map { listOf(it.type, it.x.toRawBits(), it.y.toRawBits()) },
        obstacleField.obstacles.map { it.id },
    )
}
