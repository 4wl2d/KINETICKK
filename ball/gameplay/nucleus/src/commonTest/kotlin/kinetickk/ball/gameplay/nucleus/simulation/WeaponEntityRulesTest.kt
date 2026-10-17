// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Weapon entities and the impact shockwave meet the enemy rules shots meet: shield facing, phasing and Rebirth power. */
class WeaponEntityRulesTest {
    @Test
    fun aReflectorMeetsWeaponEntitiesByWhereTheyComeFrom() {
        val state = buildTestState(weapon = WeaponId.GYRE_DISC, shape = CoreShape.HEXAGON)
        state.critChance = 0f
        val reflector = state.spawn(EnemyType.REFLECTOR, 300f, 0f)
        state.updateEnemies(STEP)
        assertTrue(reflector.guarded, "It turns its shield to the Core")
        reflector.previousX = reflector.x
        reflector.previousY = reflector.y

        /** One entity crossing the Reflector from [fromX]; returns the damage it dealt. */
        fun strike(kind: WeaponEntityKind, fromX: Float, phase: Int = 0): Float {
            val entity = WeaponEntity(kind, fromX, reflector.y, 8f, 1f, 1f, damage = 40f, phase = phase)
            entity.x = reflector.x
            val entities = state.weaponRuntime.entities
            entities.clear()
            entities += entity
            val before = reflector.hp
            state.resolveWeaponEntityHits(STEP)
            return before - reflector.hp
        }

        // The outward disc meets the shield from the Core's side; the returning one strikes its back.
        val front = strike(WeaponEntityKind.DISC, reflector.x - 120f)
        val rear = strike(WeaponEntityKind.DISC, reflector.x + 120f, phase = 1)
        assertTrue(front > 0f)
        assertEquals(4f, rear / front, 1e-3f, "front $front, rear $rear")
        assertTrue(reflector.guarded, "The shield follows the Core again after the hit")
        // A missile coming round from behind lands in full too.
        assertEquals(rear, strike(WeaponEntityKind.MISSILE, reflector.x + 120f), 1e-2f)
        assertTrue(reflector.guarded)
    }

    @Test
    fun aTetherLetsGoOfAPhasedPhantomForAnEnemyItCanHit() {
        val state = buildTestState(weapon = WeaponId.TETHER_ARC, shape = CoreShape.HEXAGON)
        val phantom = state.spawn(EnemyType.PHANTOM, 120f, 0f)
        val drifter = state.spawn(EnemyType.DRIFTER, -200f, 0f)
        state.stepTethers(seconds = 1f)
        assertEquals(phantom.id, state.weaponRuntime.tetherTargetId, "The nearer Phantom is held first")
        assertEquals(DUMMY_HP, drifter.hp)

        phantom.shiftUntil = state.elapsed + PHANTOM_PHASE_SECONDS
        state.stepTethers(seconds = 0.5f)
        assertEquals(drifter.id, state.weaponRuntime.tetherTargetId, "The tether moves to the enemy it can hit")
        assertTrue(drifter.hp < DUMMY_HP, "The new target takes the tether's damage")
    }

    @Test
    fun anAegisShardTurnsFromAPhasedTargetToOneItCanHit() {
        val state = buildTestState(weapon = WeaponId.AEGIS_SHARDS, shape = CoreShape.HEXAGON)
        val phantom = state.spawn(EnemyType.PHANTOM, 150f, 0f)
        val drifter = state.spawn(EnemyType.DRIFTER, 0f, 150f)
        val shard = WeaponEntity(WeaponEntityKind.SHARD, state.coreX, state.coreY, AEGIS_SHARD_RADIUS, AEGIS_SHARD_LIFE, AEGIS_SHARD_LIFE,
            vx = AEGIS_SPEED, damage = 10f, targetId = phantom.id)
        state.weaponRuntime.entities.clear()
        state.weaponRuntime.entities += shard
        phantom.shiftUntil = state.elapsed + PHANTOM_PHASE_SECONDS
        state.advanceWeaponEntities(STEP)
        assertEquals(drifter.id, shard.targetId)
    }

    @Test
    fun theConcussionShockwaveTakesTheRebirthPowerOnce() {
        fun wave(tier: Int): Float {
            val state = buildTestState(rebirth = tier, weapon = WeaponId.CONCUSSION_ENGINE, shape = CoreShape.HEXAGON)
            state.critChance = 0f
            val target = state.dummy(x = state.coreX + 40f, y = state.coreY)
            state.onWeaponImpact(IMPACT_SPEED, state.impactPower(IMPACT_SPEED))
            return target.maxHp - target.hp
        }
        val power = canonicalGameplayContent.rebirth.profile(10).playerPowerMultiplier
        assertTrue(power > 1f)
        val base = wave(0)
        assertTrue(base > 0f)
        assertEquals(power, wave(10) / base, 1e-3f)
    }

    @Test
    fun aGravityMineWellGrowsWithWeaponPower() {
        /** One tick of the third-form well a mine leaves 200 from the Core, at item weapon power [power]. */
        fun wellTick(power: Float): Float {
            val state = buildTestState(weapon = WeaponId.GRAVITY_MINES, shape = CoreShape.HEXAGON)
            state.weaponLevel = 10
            state.weaponPower = power
            state.weaponRuntime.entities.clear()
            state.explodeMine(WeaponNode(WeaponNodeType.GRAVITY_MINE, state.coreX + 200f, state.coreY, 1f, 1f, 40f))
            val well = state.weaponRuntime.entities.single { it.kind == WeaponEntityKind.WELL }
            val target = state.spawn(EnemyType.DRIFTER, 200f, 0f)
            // Low enough integrity that one tick reads exactly in Float.
            target.hp = 1_000f
            state.resolveWeaponEntityHits(STEP)
            assertEquals(MINE_WELL_DPS * state.effectiveWeaponPower(), well.damage, 1e-3f)
            return 1_000f - target.hp
        }
        val base = wellTick(1f)
        assertTrue(base > 0f)
        assertEquals(3f, wellTick(3f) / base, 1e-3f)
    }

    @Test
    fun aTetherArcEquippedNextToAnEnemyStartsAtItsBaseDamage() {
        val state = buildTestState(weapon = WeaponId.FLUX_WAKE, shape = CoreShape.HEXAGON)
        state.critChance = 0f
        state.equipRunWeapon(WeaponId.TETHER_ARC)
        val target = state.spawn(EnemyType.DRIFTER, 150f, 0f)
        // Low enough integrity that one tick reads exactly in Float.
        target.hp = 1_000f
        state.stepTethers(seconds = STEP)
        assertEquals(target.id, state.weaponRuntime.tetherTargetId)
        assertTrue(state.weaponRuntime.tetherRamp >= 1f, "ramp ${state.weaponRuntime.tetherRamp}")
        val dealt = 1_000f - target.hp
        assertTrue(dealt >= TETHER_DPS * STEP * state.effectiveWeaponPower() * 0.999f, "first tick $dealt")
    }

    @Test
    fun theTetherEndFollowsItsTargetToWhereItIsDrawn() {
        val state = buildTestState(weapon = WeaponId.TETHER_ARC, shape = CoreShape.HEXAGON)
        val target = state.spawn(EnemyType.DRIFTER, 150f, 0f)
        state.stepTethers(seconds = STEP)
        // The enemy moves after the weapon step anchored the tether, as it does in a game step.
        repeat(3) {
            state.elapsed += STEP
            state.updateWeapons(STEP)
            target.previousX = target.x
            target.previousY = target.y
            target.x += 6f
            target.y -= 4f
            state.resolveWeaponEntityHits(STEP)
            val tether = state.weaponRuntime.entities.single { it.kind == WeaponEntityKind.TETHER }
            assertEquals(target.previousX, tether.previousX, 1e-3f)
            assertEquals(target.previousY, tether.previousY, 1e-3f)
            assertEquals(target.x, tether.x, 1e-3f)
            assertEquals(target.y, tether.y, 1e-3f)
        }
    }

    /** A sturdy [type] at ([x], [y]) from the Core. */
    private fun MutableGameState.spawn(type: EnemyType, x: Float, y: Float): Enemy {
        val enemy = spawnedEnemy(type, coreX + x, coreY + y)
        enemy.hp = DUMMY_HP
        enemies += enemy
        return enemy
    }

    /** Runs the weapon step and the entity hits for [seconds], the enemies held still. */
    private fun MutableGameState.stepTethers(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            elapsed += STEP
            updateWeapons(STEP)
            resolveWeaponEntityHits(STEP)
            takeVisualFxCues()
            takeSoundCues()
            left -= STEP
        }
    }

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
        const val IMPACT_SPEED = 600f
        const val DUMMY_HP = 100_000f
    }
}
