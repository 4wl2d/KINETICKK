// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterPoiRegressionTest {
    @Test
    fun legacyTotemCannotConsumeAKeyOrReplaceAnExistingRewardChoice() {
        val game = game()
        game.activateTotemForTesting()
        game.openDirectedReward(DirectedReward.ITEM_AND_REPAIR)
        val focusOptions = game.choices
        game.updateTotem(0.01f)
        assertEquals(focusOptions, game.choices)
        assertEquals(1, game.keys)
        assertEquals(DirectedReward.ITEM_AND_REPAIR, game.directedReward)
        game.phase = GamePhase.PAUSED
        val before = game.totem?.copy()
        game.updateTotem(1f)
        assertEquals(before, game.totem)
        assertEquals(GamePhase.PAUSED, game.phase)
    }

    @Test
    fun diamondParriesAnIncomingShotThatTraversedTheEntireCoreThisStep() {
        val game = game().apply {
            coreShape = CoreShape.DIAMOND
            runGrace = 0f
            characterRuntime = CharacterRuntime(parryWindow = 0.15f)
        }
        val target = game.addEnemyForTesting(300f, 0f)
        game.projectiles += Projectile(x = -40f, y = 0f, vx = -10_000f, vy = 0f,
            radius = 5f, life = 1f, hostile = true).apply { previousX = 40f }
        game.resolveProjectileHits()
        assertEquals(game.maxHp, game.hp)
        assertEquals(0f, game.characterRuntime.parryWindow)
        assertTrue(game.projectiles.any { !it.hostile }, "Parry must emit its counterattack")
        assertTrue(target.hp > 0f)
    }

    @Test
    fun activeVaultDefendersRemainAvailableInsideTheTrialEscapeDistance() {
        val game = game()
        game.activatePointOfInterest(PointOfInterestState(0, PointOfInterestKind.SEALED_ANOMALY, 0f, 0f, 85f))
        val defenderIds = game.pointsOfInterest.single().defenderIds
        game.coreX = 2_000f
        game.previousCoreX = game.coreX
        game.updatePointsOfInterest(0.01f)
        assertTrue(game.pointsOfInterest.single().active)
        game.updateEnemies(0.01f)
        assertEquals(3, game.enemies.count { it.id in defenderIds })
        game.coreX = 0f
        game.enemies.filter { it.id in defenderIds }.forEach { game.onEnemyKilled(it) }
        game.updatePointsOfInterest(0.01f)
        assertEquals(listOf(DirectedReward.RELIC), game.pendingDirectedRewards)
    }

    @Test
    fun legacyTotemAndSimultaneousLevelRelicAndPoiRewardsAreAllConsumedOnce() {
        val game = game()
        game.pendingLevelChoices = 1
        game.pendingRelicChoices = 1
        game.pendingDirectedRewards = listOf(DirectedReward.ITEM_AND_REPAIR)
        game.hp = 50f
        game.activateTotemForTesting()
        game.updateTotem(0.01f)
        assertEquals(ChoiceType.TOTEM, game.choiceType)
        game.choose(0)
        assertEquals(2, game.weaponLevel)
        assertEquals(ChoiceType.ITEM, game.choiceType)
        game.choose(0)
        assertEquals(DirectedReward.ITEM_AND_REPAIR, game.directedReward)
        game.choose(0) // focus
        game.choose(0) // item
        assertEquals(ChoiceType.RELIC, game.choiceType)
        assertEquals(75f, game.hp)
        game.choose(0)
        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(1, game.equippedRelics.size)
        assertEquals(2, game.acquiredItemCount)
        assertEquals(0, game.keys)
        assertTrue(game.pendingDirectedRewards.isEmpty())
        game.openNextPendingChoice()
        assertEquals(GamePhase.RUNNING, game.phase)
    }

    private fun game() = MutableGameState(canonicalGameplayContent).apply { startRun(); enemies.clear() }
}
