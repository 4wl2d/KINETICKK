// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelCapTest {
    @Test
    fun dataAfterTheMaximumLevelChargesTheApexAndNeverLevels() {
        val state = buildTestState()
        val maximumLevel = state.content.tempo.maximumLevel
        assertEquals(42, maximumLevel)

        state.levelTo(maximumLevel - 1)
        assertEquals(41, state.level)
        state.phase = GamePhase.RUNNING
        state.pendingLevelChoices = 0
        val collectedBefore = state.dataCollected
        // The last level-up with 100 Data to spare: the spare Data is discarded, the cap holds no Data.
        state.gainData(state.nextLevelData + 100f)
        assertEquals(maximumLevel, state.level)
        assertEquals(0, state.data)
        assertEquals(collectedBefore + 716 + 100, state.dataCollected)
        assertEquals(716, state.nextLevelData, "the capped level displays the last requirement")

        state.phase = GamePhase.RUNNING
        state.pendingLevelChoices = 0
        val atCap = state.dataCollected
        // Skill drafts and rank-up cards follow level-ups: Data past the cap owes neither (combat §4, economy §1.6).
        val draftsAtCap = state.pendingSkillDrafts
        val rankUpsAtCap = state.pendingRankUpOffers
        state.gainData(5_000f)
        state.gainData(0.5f)
        state.gainData(0.5f)
        assertEquals(maximumLevel, state.level)
        assertEquals(0, state.data)
        assertEquals(0, state.pendingLevelChoices)
        assertEquals(draftsAtCap, state.pendingSkillDrafts, "no skill draft after the cap")
        assertEquals(rankUpsAtCap, state.pendingRankUpOffers, "no rank-up card after the cap")
        assertEquals(GamePhase.RUNNING, state.phase, "post-cap Data opens no choice")
        assertEquals(atCap + 5_001, state.dataCollected, "post-cap Data still counts as collected")
        assertEquals(0, state.creditData(30f), "credit at the cap adds nothing to the level's Data")
        assertEquals(atCap + 5_031, state.dataCollected)
        assertEquals(0, state.data)
    }

    @Test
    fun aLevelUpDividendOnTheLastLevelUpNeverLevels() {
        val state = buildTestState(rebirth = 7).apply { own("Compound Interest", stacks = 2) }
        val item = state.itemNamed("Compound Interest")
        val dividend = 8f * state.build.magnitude(item.id) * state.build.bonus(item.id)
        state.levelTo(state.content.tempo.maximumLevel - 1)
        state.phase = GamePhase.RUNNING
        state.data = state.nextLevelData - 1
        state.dataFraction = 0f
        val collectedBefore = state.dataCollected

        state.gainData(1f)

        assertEquals(state.content.tempo.maximumLevel, state.level)
        assertEquals(0, state.data, "the dividend of the 41 -> 42 level-up arrives at the cap")
        assertEquals(collectedBefore + 1 + floor(dividend).toLong(), state.dataCollected, "and is counted, then lost")
    }

    @Test
    fun eliteBountyStillOpensAnItemOfferAtTheMaximumLevel() {
        val state = MutableGameState(canonicalGameplayContent, seed = 5_150, initialMatter = 0, initialRebirthLevel = 2).apply {
            matrixRanks[content.matrixNodes.indexOfFirst { it.id == MatrixNodeId.ELITE_BOUNTY }] = 1
            unlockWeaponsForTesting(listOf(WeaponId.FLUX_WAKE))
            startingWeapon = WeaponId.FLUX_WAKE
            resize(1_280f, 720f)
            startRun()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 0f
        }
        assertTrue(state.matrix.eliteBounty)
        state.levelTo(state.content.tempo.maximumLevel)
        state.phase = GamePhase.RUNNING
        state.pendingLevelChoices = 0
        state.choices = emptyList()

        val elite = state.spawnedEnemy(EnemyType.ELITE, state.coreX + 400f, state.coreY)
        state.enemies += elite
        state.onEnemyKilled(elite)
        assertEquals(1, state.pendingLevelChoices)
        state.openNextPendingChoice()

        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.ITEM, state.activeChoiceType)
        assertTrue(state.choices.isNotEmpty())
        assertEquals(state.content.tempo.maximumLevel, state.level)
    }

    @Test
    fun theRenderModelShowsTheCapWithoutData() {
        val state = buildTestState()
        state.levelTo(state.content.tempo.maximumLevel)
        state.phase = GamePhase.RUNNING
        state.gainData(250f)

        val model = state.toRenderModel()
        assertEquals(42, model.maxLevel)
        assertEquals(model.maxLevel, model.level)
        assertEquals(0, model.data)
        assertEquals(716, model.nextLevelData)
    }

    @Test
    fun theRebirthTierScalesTheRunsRequirementsFromLevelSixteen() {
        val tierZero = buildTestState()
        val tierThree = buildTestState(rebirth = 3)
        val tierTen = buildTestState(rebirth = 10)
        // The opening levels arrive as on tier 0 at every tier.
        assertEquals(listOf(18, 18, 18), listOf(tierZero, tierThree, tierTen).map { it.nextLevelData })
        assertEquals(18, MutableGameState(canonicalGameplayContent, initialRebirthLevel = 10).nextLevelData)
        assertEquals(166, tierTen.dataRequiredForLevel(15))
        assertEquals(298, tierThree.dataRequiredForLevel(16), "210 × 1.42 = 298.2")
        assertEquals(504, tierTen.dataRequiredForLevel(16), "210 × 2.4 = 504")
        assertEquals(1_017, tierThree.dataRequiredForLevel(41))

        // One Data short of the tier's total stops a level below the cap with the last requirement less one.
        for ((tier, total) in listOf(0 to 14_428, 3 to 19_912, 10 to 32_702)) {
            val short = buildTestState(rebirth = tier)
            short.gainData(total - 1f)
            assertEquals(41, short.level, "tier $tier")
            assertEquals(short.dataRequiredForLevel(41) - 1, short.data, "tier $tier")
            assertEquals(short.dataRequiredForLevel(41), short.nextLevelData, "tier $tier")

            val exact = buildTestState(rebirth = tier)
            exact.gainData(total.toFloat())
            assertEquals(42, exact.level, "tier $tier")
            assertEquals(0, exact.data, "tier $tier")
            assertEquals(total.toLong(), exact.dataCollected, "tier $tier")
        }
    }

    /** Levels up one requirement at a time, reopening the run after each offer. */
    private fun MutableGameState.levelTo(target: Int) {
        while (level < target) {
            phase = GamePhase.RUNNING
            gainData(nextLevelData.toFloat())
        }
    }
}
