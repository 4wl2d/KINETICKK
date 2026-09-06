// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.*

class PointOfInterestTest {
    private fun game() = MutableGameState(canonicalGameplayContent).apply { startRun(); enemies.clear() }

    @Test fun scheduleOffersTwoWorldFixedPointsAndNeverReplacesSkippedOffer() {
        val game = game()
        game.elapsed = 45f
        game.updatePointsOfInterest(0.01f)
        assertEquals(2, game.pointsOfInterest.size)
        val first = game.pointsOfInterest.toList()
        game.coreX += 2_000f
        game.updatePointsOfInterest(0.01f)
        assertEquals(first, game.pointsOfInterest)
        game.elapsed = 86f
        game.updatePointsOfInterest(0.01f)
        assertTrue(game.pointsOfInterest.isEmpty())
        game.updatePointsOfInterest(0.01f)
        assertTrue(game.pointsOfInterest.isEmpty())
        game.elapsed = 165f
        game.updatePointsOfInterest(0.01f)
        assertEquals(2, game.pointsOfInterest.size)
    }

    @Test fun circuitRequiresThreeSeparatedBeaconsAndReturnAndRewardsExactlyOnce() {
        val game = game()
        game.activatePointOfInterest(PointOfInterestState(0, PointOfInterestKind.RESONANT_CIRCUIT, 0f, 0f, 85f))
        game.previousCoreX = -1_000f; game.previousCoreY = 0f; game.coreX = 1_000f; game.coreY = 0f
        game.updatePointsOfInterest(0.01f)
        assertEquals(1, game.pointsOfInterest.single().nextBeacon)
        repeat(3) {
            val beacon = game.pointsOfInterest.single().beacon(it + 1)
            game.previousCoreX = beacon.x; game.previousCoreY = beacon.y
            game.coreX = beacon.x; game.coreY = beacon.y
            game.updatePointsOfInterest(0.01f)
        }
        assertEquals(listOf(DirectedReward.WEAPON), game.pendingDirectedRewards)
        assertTrue(game.pointsOfInterest.isEmpty())
        game.updatePointsOfInterest(0.01f)
        assertEquals(1, game.pendingDirectedRewards.size)
    }

    @Test fun orbitNeedsEightSecondsOfActualMovementAndPausesDoNotAdvanceIt() {
        val game = game()
        game.activatePointOfInterest(PointOfInterestState(0, PointOfInterestKind.COLLAPSING_ORBIT, 0f, 0f, 85f))
        game.coreX = 155f; game.previousCoreX = 155f
        repeat(100) { game.updatePointsOfInterest(0.01f) }
        assertEquals(0f, game.pointsOfInterest.single().orbitSeconds)
        game.phase = GamePhase.PAUSED
        val before = game.pointsOfInterest
        game.updatePointsOfInterest(1f)
        assertEquals(before, game.pointsOfInterest)
        game.phase = GamePhase.RUNNING
        repeat(810) { index ->
            game.previousCoreX = game.coreX; game.previousCoreY = game.coreY
            game.coreX = cos(index * 0.01f) * 155f; game.coreY = sin(index * 0.01f) * 155f
            game.updatePointsOfInterest(0.01f)
        }
        assertEquals(listOf(DirectedReward.ITEM_AND_REPAIR), game.pendingDirectedRewards)
        assertEquals(1, game.pendingCompletedOrbits)
    }

    @Test fun defendersReserveThreeExistingEnemySlotsAndRequireTheirDeaths() {
        val game = game()
        repeat(game.content.rebirth.maxActiveEnemies - 2) { game.addEnemyForTesting(1_000f, 1_000f) }
        val offer = PointOfInterestState(0, PointOfInterestKind.SEALED_ANOMALY, 0f, 0f, 85f)
        game.activatePointOfInterest(offer)
        assertTrue(game.pointsOfInterest.isEmpty())
        game.enemies.removeAt(0)
        game.activatePointOfInterest(offer)
        assertEquals(game.content.rebirth.maxActiveEnemies, game.enemies.size)
        val trial = game.pointsOfInterest.single()
        val defenders = game.enemies.filter { it.id in trial.defenderIds }
        defenders.forEach { game.onEnemyKilled(it); game.onEnemyKilled(it) }
        game.updatePointsOfInterest(0.01f)
        assertEquals(listOf(DirectedReward.RELIC), game.pendingDirectedRewards)
    }

    @Test fun timeoutEscapeRebaseAndBossBoundaryCannotDuplicateRewards() {
        val game = game()
        game.activatePointOfInterest(PointOfInterestState(0, PointOfInterestKind.RESONANT_CIRCUIT, 300_000f, 20f, 85f))
        game.coreX = 300_000f
        game.rebaseWorldIfNeeded()
        assertEquals(0f, game.pointsOfInterest.single().x)
        game.updatePointsOfInterest(25f)
        assertTrue(game.pointsOfInterest.isEmpty())
        assertTrue(game.pendingDirectedRewards.isEmpty())
        game.elapsed = 645f; game.nextPointOfferIndex = 5
        game.updatePointsOfInterest(0.01f)
        assertEquals(2, game.pointsOfInterest.size)
        game.elapsed = game.content.tempo.bossAtSeconds
        game.updatePointsOfInterest(0.01f)
        assertTrue(game.pointsOfInterest.isEmpty())
    }

    @Test fun directedRewardSharesQueueAndFocusCannotRerollOutOfCategory() {
        val game = game()
        game.pendingLevelChoices = 1
        game.pendingDirectedRewards = listOf(DirectedReward.RELIC)
        game.openNextPendingChoice()
        assertEquals(ChoiceType.ITEM, game.choiceType)
        game.choose(0)
        assertEquals(3, game.choices.size)
        val focus = game.choices[0].rewardFocus!!
        assertFalse(game.choicesCanReroll)
        game.choose(0)
        assertEquals(3, game.choices.size)
        assertTrue(game.choices.all { game.content.relic(it.relicId!!).aspect.name == focus.name })
        game.choose(0)
        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(1, game.equippedRelics.size)
        assertNull(game.directedReward)
    }
}
