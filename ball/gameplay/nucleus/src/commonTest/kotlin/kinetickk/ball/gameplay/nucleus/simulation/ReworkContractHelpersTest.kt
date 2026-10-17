// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReworkContractHelpersTest {
    @Test
    fun disabledEnemiesNeitherActNorAttackAndSilencedEnemiesOnlyAct() {
        val state = buildTestState()
        val enemy = state.addEnemyForTesting(x = 300f, y = 0f)
        assertTrue(state.canAct(enemy))
        assertTrue(state.canAttack(enemy))

        state.elapsed = 2f
        enemy.disabledUntil = 2.5f
        assertFalse(state.canAct(enemy))
        assertFalse(state.canAttack(enemy))
        state.elapsed = 2.5f
        assertTrue(state.canAct(enemy), "the disable ends at its absolute time")
        assertTrue(state.canAttack(enemy))

        enemy.silencedUntil = 3f
        assertTrue(state.canAct(enemy))
        assertFalse(state.canAttack(enemy))
        state.elapsed = 3f
        assertTrue(state.canAttack(enemy), "the silence ends at its absolute time")

        val copy = enemy.isolatedCopy()
        assertEquals(enemy.disabledUntil, copy.disabledUntil)
        assertEquals(enemy.silencedUntil, copy.silencedUntil)
    }

    @Test
    fun hostileDamageGrowsWithTheRunAndLeavesTheRebirthMultiplierToTakeDamage() {
        val baseline = buildTestState()
        val reborn = buildTestState(rebirth = 6)
        assertTrue(reborn.rebirthProfile.incomingDamageMultiplier > 1f)
        val enemy = baseline.addEnemyForTesting(x = 300f, y = 0f)
        val bossAt = baseline.content.tempo.bossAtSeconds

        assertEquals(10f, baseline.hostileDamage(enemy, 10f))
        assertEquals(10f, baseline.hostileDamage(null, 10f))
        baseline.elapsed = bossAt * 0.5f
        assertEquals(12.5f, baseline.hostileDamage(enemy, 10f), 0.0001f)
        baseline.elapsed = bossAt
        assertEquals(15f, baseline.hostileDamage(enemy, 10f), 0.0001f)
        assertEquals(1f, baseline.hostileOutputFactor(enemy))

        reborn.elapsed = bossAt
        val amount = reborn.hostileDamage(null, 10f)
        assertEquals(15f, amount, 0.0001f, "the Rebirth multiplier is applied once, by takeDamage")
        reborn.takeDamage(amount, kind = IncomingDamage.PROJECTILE)
        assertEquals(
            reborn.maxHp - amount * reborn.rebirthProfile.incomingDamageMultiplier,
            reborn.hp,
            0.001f,
        )
    }

    @Test
    fun abilityPowerSharesWeaponFactorsButGrowsOnTheAbilityCurve() {
        val state = buildTestState(rebirth = 2)
        assertEquals(state.effectiveWeaponPower(), state.abilityPower(), 0.0001f, "both curves start at 1")

        state.elapsed = state.content.tempo.bossAtSeconds
        val weaponRatio = state.content.tempo.weaponRunGrowth(state.elapsed)
        assertEquals(2f, state.content.tempo.abilityRunGrowth(state.elapsed), 0.0001f)
        assertEquals(state.effectiveWeaponPower() / weaponRatio * 2f, state.abilityPower(), 0.0001f)

        val calm = state.abilityPower()
        state.overdriveTime = 1f
        assertEquals(calm * 1.45f, state.abilityPower(), 0.0001f)
        state.overdriveTime = 0f

        assertEquals(40f * state.abilityPower(), state.basisAmount(DamageBasis.WEAPON, hit = 0f, victim = null), 0.0001f)
        assertEquals(state.abilityPower(), state.toRenderModel().abilityPower)
    }

    @Test
    fun characterAbilitiesScaleWithAbilityPower() {
        val state = buildTestState(shape = CoreShape.SHARD)
        state.elapsed = state.content.tempo.bossAtSeconds
        val enemy = state.addEnemyForTesting(x = 200f, y = 0f, hp = 10_000f)
        enemy.characterMarkTime = 1f

        state.onCharacterPrimaryHit(enemy)

        val expected = 48f * state.abilityPower() * enemy.weaponTaken
        assertEquals(expected, 10_000f - enemy.hp, 0.01f)
        assertNotEquals(48f * state.effectiveWeaponPower() * enemy.weaponTaken, 10_000f - enemy.hp)
    }

    @Test
    fun hostileProjectilesHitTheCoreAtItsCurrentRadius() {
        val state = buildTestState()
        assertEquals(MutableGameState.CORE_RADIUS, state.coreRadius)
        assertEquals(GameplayRenderModel.CORE_RADIUS, state.toRenderModel().coreRadius)
        state.previousCoreX = state.coreX
        state.previousCoreY = state.coreY
        // 25 units out with radius 4: outside the default 16 + 4 contact, inside 30 + 4.
        state.projectiles += Projectile(state.coreX + 25f, state.coreY, 0f, 0f, radius = 4f, life = 1f, damage = 12f)

        state.resolveProjectileHits()
        assertEquals(1, state.projectiles.size)
        assertEquals(state.maxHp, state.hp)

        state.coreRadius = 30f
        assertEquals(30f, state.toRenderModel().coreRadius)
        state.resolveProjectileHits()
        assertTrue(state.projectiles.isEmpty())
        assertTrue(state.hp < state.maxHp)

        assertEquals(30f, state.copyForReduction().coreRadius)
        state.resetRunStats()
        assertEquals(MutableGameState.CORE_RADIUS, state.coreRadius, "a new run starts at the default radius")
    }

    @Test
    fun obstacleQueriesReportAnEmptyWorldAndSlamsDoNothing() {
        val state = buildTestState()
        val enemy = state.addEnemyForTesting(x = 200f, y = 0f, hp = 50f)

        assertFalse(state.isSpawnPointBlocked(200f, 0f, 30f))
        assertFalse(state.obstacleBlocksSegment(0f, 0f, 1_000f, 0f, 20f))
        state.applySlamDamage(enemy, normalSpeed = 2_000f)
        assertEquals(50f, enemy.hp)
        assertTrue(enemy.alive)
    }
}
