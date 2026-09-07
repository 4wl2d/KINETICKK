// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.*

class CharacterAbilityTest {
    private fun game(shape: CoreShape) = MutableGameState(canonicalGameplayContent).apply { startRun(); enemies.clear(); coreShape = shape }

    @Test fun expiringAbilityTimersClearBarrierAndLatticeWithoutChangingTheSource() {
        val source = game(CoreShape.ORB)
        source.characterRuntime = CharacterRuntime(
            barrier = 20f, barrierTime = 0.1f, parryWindow = 0.1f, parryCooldown = 0.3f,
            lattice = listOf(WorldPoint(10f, 20f)), latticeTime = 0.1f,
            turnArc = 0.5f, turnDistance = 50f,
        )
        val fork = source.copyForReduction()
        fork.updateCharacterRuntime(0.1f)
        assertEquals(0f, fork.characterRuntime.barrier)
        assertEquals(0f, fork.characterRuntime.barrierTime)
        assertEquals(0f, fork.characterRuntime.parryWindow)
        assertEquals(0.2f, fork.characterRuntime.parryCooldown, 0.0001f)
        assertEquals(0f, fork.characterRuntime.latticeTime)
        assertTrue(fork.characterRuntime.lattice.isEmpty())
        assertEquals(0f, fork.characterRuntime.turnArc)
        assertEquals(0f, fork.characterRuntime.turnDistance)
        assertEquals(20f, source.characterRuntime.barrier)
        assertEquals(1, source.characterRuntime.lattice.size)
    }

    @Test fun allSixCharactersCanUseEveryWeaponWithoutStationaryBrakeCharge() {
        for (shape in CoreShape.entries) for (weapon in WeaponId.entries) {
            val game = game(shape)
            game.equipRunWeapon(weapon)
            game.braking = true
            repeat(240) { index ->
                game.updatePointer(if (index % 2 == 0) 0f else 1_280f, 700f)
                game.updateCharacterRuntime(1f / 120f)
            }
            assertEquals(weapon, game.weapon)
            assertEquals(0f, game.characterRuntime.barrier)
            assertTrue(game.characterRuntime.lattice.isEmpty())
            if (shape != CoreShape.DIAMOND) assertEquals(0f, game.characterRuntime.charge, "$shape / $weapon")
            assertEquals(0f, game.characterRuntime.parryWindow)
        }
    }

    @Test fun circleNeedsRealArcAndNextPrimaryHitReleasesOnce() {
        val game = game(CoreShape.ORB)
        repeat(900) { index ->
            val angle = index * 0.01f
            game.velocityX = cos(angle) * 400f; game.velocityY = sin(angle) * 400f
            game.smoothedVelocityX = game.velocityX; game.smoothedVelocityY = game.velocityY
            game.previousCoreX = game.coreX; game.previousCoreY = game.coreY
            game.coreX += game.velocityX * 0.01f; game.coreY += game.velocityY * 0.01f
            game.updateCharacterRuntime(0.01f)
        }
        assertEquals(1f, game.characterRuntime.charge)
        val enemy = game.addEnemyForTesting(game.coreX + 60f, game.coreY)
        game.onCharacterPrimaryHit(enemy)
        assertTrue(enemy.hp < enemy.maxHp)
        val after = enemy.hp
        game.onCharacterPrimaryHit(enemy)
        assertEquals(after, enemy.hp)
        assertEquals(0f, game.characterRuntime.charge)
    }

    @Test fun squareConvertsActualDecelerationToBarrierAndDashSpendsIt() {
        val game = game(CoreShape.PRISM)
        game.characterRuntime = CharacterRuntime(previousSpeed = 800f)
        game.braking = true; game.velocityX = 300f; game.coreX = 3f
        game.updateCharacterRuntime(0.01f)
        assertTrue(game.characterRuntime.barrier > 0f)
        val barrier = game.characterRuntime.barrier
        repeat(60) { game.previousCoreX = game.coreX; game.updateCharacterRuntime(0.01f) }
        assertEquals(barrier, game.characterRuntime.barrier)
        game.beginCharacterDash()
        assertEquals(0f, game.characterRuntime.barrier)
        assertTrue(game.characterRuntime.ramPower > 0f)
        assertEquals(0f, game.characterRuntime.charge)
    }

    @Test fun triangleCountsEachDashEnemyOnceAndMarkNeedsLaterHit() {
        val game = game(CoreShape.SHARD)
        val enemy = game.addEnemyForTesting(50f, 0f)
        game.beginCharacterDash(); game.dashPhaseTime = 0.24f
        game.onCharacterDashContact(enemy); game.onCharacterDashContact(enemy)
        assertEquals(1, game.pendingDashHits)
        assertEquals(enemy.maxHp, enemy.hp)
        assertTrue(enemy.characterMarkTime > 0f)
        game.onCharacterPrimaryHit(enemy)
        assertTrue(enemy.hp < enemy.maxHp)
        assertEquals(0f, enemy.characterMarkTime)
    }

    @Test fun squareCanRebuildExpiredBarrierFromNewDecelerationWithRamAlreadyCharged() {
        val game = game(CoreShape.PRISM)
        game.characterRuntime = CharacterRuntime(charge = 1f, previousSpeed = 800f)
        game.braking = true
        game.velocityX = 300f
        game.coreX = 3f
        game.updateCharacterRuntime(0.01f)
        assertEquals(1f, game.characterRuntime.charge)
        assertEquals(30f, game.characterRuntime.barrier)
        game.previousCoreX = game.coreX
        game.updateCharacterRuntime(5f)
        assertEquals(0f, game.characterRuntime.barrier)
    }

    @Test fun ringHasDeadZoneAndBrakeShrinksIt() {
        val game = game(CoreShape.RING)
        game.characterRuntime = CharacterRuntime(ringRadius = 155f)
        val center = game.addEnemyForTesting(0f, 0f)
        val rim = game.addEnemyForTesting(150f, 0f)
        game.velocityX = 300f; game.coreX = 3f
        game.updateCharacterRuntime(0.01f)
        assertEquals(center.maxHp, center.hp)
        assertTrue(rim.hp < rim.maxHp)
        game.braking = true
        repeat(120) { game.updateCharacterRuntime(0.01f) }
        assertTrue(game.characterRuntime.ringRadius < 60f)
    }

    @Test fun parryRequiresMovingBrakeStartAndApproachingThreatAndExpires() {
        val game = game(CoreShape.DIAMOND)
        game.characterRuntime = CharacterRuntime(previousSpeed = 400f)
        game.velocityX = 300f; game.coreX = 3f; game.braking = true
        game.updateCharacterRuntime(0.01f)
        assertTrue(game.characterRuntime.parryWindow > 0f)
        assertFalse(game.tryCharacterParry(20f, 0f, 300f, 0f))
        assertTrue(game.tryCharacterParry(20f, 0f, -300f, 0f))
        assertFalse(game.tryCharacterParry(20f, 0f, -300f, 0f))
        game.updateCharacterRuntime(0.5f)
        assertEquals(0f, game.characterRuntime.parryWindow)
    }

    @Test fun latticeNeedsSeparatedRealDashesAndBrakeCollapsesBoundedEdges() {
        val game = game(CoreShape.TESSERACT)
        game.beginCharacterDash(); game.dashPhaseTime = 0.24f
        game.updateCharacterRuntime(0.01f)
        assertTrue(game.characterRuntime.lattice.isEmpty())
        game.coreX = 150f; game.velocityX = 600f
        game.updateCharacterRuntime(0.01f)
        assertEquals(1, game.characterRuntime.lattice.size)
        game.coreX = 0f; game.beginCharacterDash(); game.coreX = 150f
        game.updateCharacterRuntime(0.01f)
        assertEquals(1, game.characterRuntime.lattice.size)
        game.coreX = 300f; game.beginCharacterDash(); game.coreX = 450f
        game.updateCharacterRuntime(0.01f)
        assertEquals(2, game.characterRuntime.lattice.size)
        val target = game.addEnemyForTesting(200f, 0f)
        game.braking = true
        game.updateCharacterRuntime(0.01f)
        assertTrue(game.characterRuntime.lattice.isEmpty())
        assertTrue(target.hp < target.maxHp)
    }

    @Test fun abilityStateAndMarksAreIsolatedByReductionCopy() {
        val game = game(CoreShape.TESSERACT)
        game.characterRuntime = CharacterRuntime(lattice = listOf(WorldPoint(10f, 20f)), latticeTime = 3f)
        val target = game.addEnemyForTesting(10f, 20f)
        target.characterMarkTime = 3f
        val fork = MutableGameState(canonicalGameplayContent, reductionSource = game)
        fork.updateCharacterRuntime(0.1f)
        assertEquals(3f, game.characterRuntime.latticeTime)
        assertEquals(3f, target.characterMarkTime)
        assertTrue(fork.enemies.first().characterMarkTime < target.characterMarkTime)
    }
}
