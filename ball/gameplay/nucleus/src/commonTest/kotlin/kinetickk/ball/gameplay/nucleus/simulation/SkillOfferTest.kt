// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillOfferTest {
    @Test
    fun skillSlotsOpenAtTheirLevelsWithTheDraftBeforeTheLevelsItemOffer() {
        val state = buildTestState()
        val opened = mutableListOf<Pair<Int, ChoiceType>>()
        while (state.level < 17) {
            state.levelUpOnce()
            while (state.phase == GamePhase.CHOICE) {
                opened += state.level to state.choiceType
                if (state.choiceType == ChoiceType.SKILL) {
                    assertEquals(DRAFT_CARDS, state.choices.size, "level ${state.level}")
                    assertEquals(DRAFT_CARDS, state.choices.mapNotNull { it.skillId }.toSet().size, "distinct cards")
                    assertTrue(state.choices.all { it.skillRank == 1 && !state.skillLoadout.owns(it.skillId!!) })
                }
                state.choose(0)
            }
        }
        val drafts = opened.filter { it.second == ChoiceType.SKILL }.map { it.first }
        assertEquals(state.content.tempo.skillSlotLevels.toList(), drafts)
        // Each draft opens before that level's item offer, and item offers keep their one-per-level cadence.
        for (level in drafts) {
            val atLevel = opened.filter { it.first == level }.map { it.second }
            assertEquals(listOf(ChoiceType.SKILL, ChoiceType.ITEM), atLevel, "level $level")
        }
        assertEquals((2..17).toList(), opened.filter { it.second == ChoiceType.ITEM }.map { it.first })
        assertEquals(ContentBounds.SKILL_SLOTS, state.skillLoadout.ownedCount)
    }

    @Test
    fun theHeirloomOfferStillOpensTheRunBeforeAnyDraft() {
        val state = buildTestState()
        state.rebirthRank = 1
        state.heirloomCandidates = listOf(kinetickk.ball.content.api.RelicId.GYRE_ENGINE)
        state.heirloomPending = true
        state.levelUpOnce()
        assertEquals(ChoiceType.RELIC, state.choiceType)
        assertTrue(state.choices.any { it.heirloom })
        state.choose(state.choices.indexOfFirst { it.relicId != null })
        assertEquals(ChoiceType.SKILL, state.choiceType)
        state.choose(0)
        assertEquals(ChoiceType.ITEM, state.choiceType)
    }

    @Test
    fun aDraftCostsExactlyThreeRewardDrawsAndARerollThreeMore() {
        val state = buildTestState()
        val reference = state.rewardRandom.copy()
        state.levelUpOnce()
        assertEquals(ChoiceType.SKILL, state.choiceType)
        repeat(DRAFT_CARDS) { reference.nextFloat() }
        assertEquals(reference.snapshot(), state.rewardRandom.snapshot())
        val rerolls = state.rerollsRemaining
        assertTrue(state.choicesCanReroll)
        state.rerollChoices()
        assertEquals(rerolls - 1, state.rerollsRemaining)
        repeat(DRAFT_CARDS) { reference.nextFloat() }
        assertEquals(reference.snapshot(), state.rewardRandom.snapshot())
        assertEquals(DRAFT_CARDS, state.choices.size)
    }

    @Test
    fun withNoSkillSlotInReachTheItemStreamIsUntouchedAndADraftOnlyAddsItsThreeDraws() {
        val unreachable = withoutReachableSkillSlots()
        val withDrafts = buildTestState(seed = 77)
        val without = buildTestState(seed = 77, content = unreachable)
        assertEquals(withDrafts.rewardRandom.snapshot(), without.rewardRandom.snapshot())
        withDrafts.levelUpOnce()
        without.levelUpOnce()
        assertEquals(ChoiceType.SKILL, withDrafts.choiceType)
        assertEquals(ChoiceType.ITEM, without.choiceType, "no slot opens: the level's offer comes at once")
        // The run without drafts drew the item offer already; the draft drew three floats before the same offer.
        val replay = buildTestState(seed = 77, content = unreachable)
        val cursor = replay.rewardRandom.copy()
        repeat(DRAFT_CARDS) { cursor.nextFloat() }
        assertEquals(cursor.snapshot(), withDrafts.rewardRandom.snapshot())
    }

    @Test
    fun rankUpCardsTakeTheExperimentalPlaceWithoutAnExtraDrawAndRankTheSkill() {
        val state = buildTestState()
        state.level = 2
        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.level = 4
        val withCard = state.copyForReduction()
        val control = state.copyForReduction()
        withCard.pendingRankUpOffers = 1
        control.pendingRankUpOffers = 0
        withCard.openItemChoice()
        control.openItemChoice()
        assertEquals(control.rewardRandom.snapshot(), withCard.rewardRandom.snapshot(), "the card adds no draw")
        assertEquals(control.choices.size, withCard.choices.size)
        assertEquals(ChoiceType.SKILL, withCard.choices[1].type)
        assertEquals(SkillId.SOLAR_FLARE, withCard.choices[1].skillId)
        assertEquals(2, withCard.choices[1].skillRank)
        assertEquals(control.choices[0], withCard.choices[0])
        assertEquals(control.choices[2], withCard.choices[2])
        assertEquals(0, withCard.pendingRankUpOffers)
        // A reroll keeps the card and deals the item cards again.
        withCard.rerollChoices()
        assertEquals(ChoiceType.SKILL, withCard.choices[1].type)
        withCard.choose(1)
        assertEquals(2, withCard.skillLoadout.rank(0))
        assertEquals(4f, withCard.skillRuntime[0, SkillCell.RANKED_AT_LEVEL])
        assertEquals(GamePhase.RUNNING, withCard.phase)
        // A skill drafted less than the gap ago is not yet offered a rank-up.
        val fresh = buildTestState()
        fresh.level = 4
        fresh.acquireSkill(SkillId.SOLAR_FLARE)
        fresh.pendingRankUpOffers = 1
        fresh.openItemChoice()
        assertTrue(fresh.choices.none { it.type == ChoiceType.SKILL })
        assertEquals(0, fresh.pendingRankUpOffers, "the owed card lapses with nothing to rank")
    }

    @Test
    fun aPickEverythingRunRanksItsSkillsOnTheSpecSchedule() {
        val state = buildTestState()
        val rankUps = mutableListOf<String>()
        while (state.level < 28) {
            state.levelUpOnce()
            while (state.phase == GamePhase.CHOICE) {
                val card = state.choices.indexOfFirst { it.type == ChoiceType.SKILL && (it.skillRank ?: 1) > 1 }
                if (card >= 0) {
                    val option = state.choices[card]
                    rankUps += "${"ZXC"[state.skillLoadout.slotOf(option.skillId!!)]}${option.skillRank}@${state.level}"
                    state.choose(card)
                } else {
                    state.choose(0)
                }
            }
        }
        assertEquals(listOf("Z2@4", "Z3@8", "X2@12", "X3@16", "C2@20", "C3@24"), rankUps)
        assertTrue((0 until ContentBounds.SKILL_SLOTS).all { state.skillLoadout.rank(it) == ContentBounds.MAX_SKILL_RANK })
    }

    @Test
    fun theDraftDealsAnAlignedACharacterAndAWildCard() {
        val state = buildTestState(shape = CoreShape.DIPOLE)
        state.levelUpOnce()
        val cards = state.choices.map { it.skillId!! }
        assertTrue(cards[1] in CoreShape.DIPOLE.synergySkills(), "the second card comes from the character's lane: $cards")
        // Catalyze needs two elements in play; one owned element skill is not enough.
        val catalyst = buildTestState()
        repeat(40) { seed ->
            val draft = buildTestState(seed = 1_000 + seed)
            draft.buildSkillDraft()
            assertFalse(draft.choices.any { it.skillId == SkillId.CATALYZE }, "seed ${1_000 + seed}")
        }
        catalyst.acquireSkill(SkillId.SOLAR_FLARE)
        catalyst.acquireSkill(SkillId.FROST_SNAP)
        assertTrue(StatusElement.entries.count { catalyst.build.affinity(it) >= 1 } >= CATALYZE_MIN_ELEMENTS)
        val offered = (0 until 40).any { seed ->
            val draft = buildTestState(seed = 2_000 + seed).apply {
                acquireSkill(SkillId.SOLAR_FLARE)
                acquireSkill(SkillId.FROST_SNAP)
            }
            draft.buildSkillDraft()
            draft.choices.any { it.skillId == SkillId.CATALYZE }
        }
        assertTrue(offered, "Catalyze joins the pool once two elements are in play")
    }

    @Test
    fun skillCardsPreviewWithoutTakingTheCard() {
        val state = buildTestState()
        state.levelUpOnce()
        val previews = state.rewardPreviews()
        assertEquals(state.choices.size, previews.size)
        assertEquals(0, state.skillLoadout.ownedCount, "previews never take the card")
        assertEquals(ChoiceType.SKILL, state.choiceType)
    }

    /** Adds exactly the Data the next level needs. */
    private fun MutableGameState.levelUpOnce() {
        gainData((nextLevelData - data).toFloat())
    }
}
