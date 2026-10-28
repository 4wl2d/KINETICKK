// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.NEAR_OBSTACLE_MAX_DISTANCE
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.phaseShardSolid
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ObstacleKindSystemTest {
    private val kinds = canonicalGameplayContent.obstacles.kinds
    private val core = MutableGameState.CORE_RADIUS

    /** A run time from 1 s on at which the phase shard with [seed] stays solid (or a ghost) for the next half second. */
    private fun phaseTime(seed: Int, solid: Boolean): Float {
        var time = 1f
        repeat(2_000) {
            if ((0..60).all { step -> kinds.phaseShardSolid(time + step * MutableGameState.FIXED_STEP, seed) == solid }) return time
            time += MutableGameState.FIXED_STEP
        }
        fail("no ${if (solid) "solid" else "ghost"} window for seed $seed")
    }

    private fun MutableGameState.friendlyShotThrough(x: Float): Projectile =
        Projectile(x = x + 60f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, hostile = false, previousX = x - 120f, previousY = 0f)
            .also { projectiles += it }

    // -----------------------------------------------------------------------------------------
    // Proximity.

    @Test
    fun theProximityPassMeasuresTheGapToTheNearestSolidSurface() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(600f, 0f, radius = 80f))
        fun restAtGap(gap: Float) = state.flyIntoObstaclesForTesting(600f - 80f - core - gap, 0f, 0f, 0f)
        val near = EffectCondition.NearObstacle(180f)

        restAtGap(170f)
        assertEquals(170f, state.coreObstacleGap, 0.01f)
        assertTrue(state.holds(near, null, null, null), "within 180 of an obstacle at 170")
        restAtGap(200f)
        assertEquals(200f, state.coreObstacleGap, 0.01f)
        assertFalse(state.holds(near, null, null, null), "not at 200")
        restAtGap(NEAR_OBSTACLE_MAX_DISTANCE - 1f)
        assertEquals(NEAR_OBSTACLE_MAX_DISTANCE - 1f, state.coreObstacleGap, 0.01f)
        restAtGap(NEAR_OBSTACLE_MAX_DISTANCE + 1f)
        assertEquals(Float.POSITIVE_INFINITY, state.coreObstacleGap, "beyond the farthest distance a condition may name")

        // Kinds with no solid body, and a phase shard while it is a ghost, are no surface.
        val seed = 11
        val open = buildTestState().apply { elapsed = phaseTime(seed, solid = false) }
        open.placeObstaclesForTesting(
            testObstacle(300f, 0f, radius = 60f, kind = ObstacleKind.ACCEL_GATE),
            testObstacle(-300f, 0f, radius = 60f, kind = ObstacleKind.DERELICT_MINE),
            testObstacle(0f, 300f, radius = 60f, kind = ObstacleKind.THERMAL_VENT),
            testObstacle(0f, -300f, radius = 60f, kind = ObstacleKind.PHASE_SHARD, shapeSeed = seed),
        )
        open.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
        assertEquals(Float.POSITIVE_INFINITY, open.coreObstacleGap)
        assertFalse(open.holds(EffectCondition.NearObstacle(NEAR_OBSTACLE_MAX_DISTANCE), null, null, null))
        open.elapsed = phaseTime(seed, solid = true)
        open.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
        assertEquals(300f - 60f - core, open.coreObstacleGap, 0.01f, "the shard counts once it materialises")
    }

    @Test
    fun nearMissCountsOnlyGrazeableSolidSurfaces() {
        val seed = 11
        val cases = listOf(
            Triple(ObstacleKind.ASTEROID, true, true),
            Triple(ObstacleKind.CRYSTAL, true, true),
            Triple(ObstacleKind.GRAVITY_WELL, true, true),
            Triple(ObstacleKind.PHASE_SHARD, true, true),
            Triple(ObstacleKind.PHASE_SHARD, false, false),
            Triple(ObstacleKind.BUMPER, true, false),
            Triple(ObstacleKind.ACCEL_GATE, true, false),
            Triple(ObstacleKind.DERELICT_MINE, true, false),
            Triple(ObstacleKind.THERMAL_VENT, true, false),
        )
        for ((kind, solidPhase, counts) in cases) {
            val state = buildTestState().apply {
                matrix = matrix.copy(nearMiss = MatrixEffect.NearMiss(40f, 900f, 6f, 0.5f))
                elapsed = phaseTime(seed, solidPhase)
            }
            state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f, kind = kind, shapeSeed = seed))
            // Passing 20 above the body's edge at 1 000 u/s.
            state.flyIntoObstaclesForTesting(300f, 40f + core + 20f, 1_000f, 0f)
            assertEquals(if (counts) 6f else 0f, state.overdriveCharge, 0.01f, "$kind solid phase $solidPhase")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Phase shards.

    @Test
    fun aGhostPhaseShardLetsTheCoreShotsAndSightThroughAndASolidOneIsARock() {
        val seed = 11
        val shard = testObstacle(300f, 0f, radius = 80f, kind = ObstacleKind.PHASE_SHARD, shapeSeed = seed)

        val ghost = buildTestState().apply { elapsed = phaseTime(seed, solid = false) }
        ghost.placeObstaclesForTesting(shard)
        val speed = ghost.obstacleLethalSpeed() + 200f
        ghost.flyIntoObstaclesForTesting(300f + speed / 240f, 0f, speed, 0f)
        assertEquals(GamePhase.RUNNING, ghost.phase)
        assertEquals(ghost.maxHp, ghost.hp)
        assertEquals(speed, ghost.velocityX, "a ghost shard never bounces the Core")
        assertFalse(ghost.obstacleBlocksSegment(0f, 0f, 600f, 0f, 7f))
        val passing = ghost.friendlyShotThrough(300f)
        ghost.blockProjectilesAtObstacles()
        assertFalse(passing.spent)

        val solid = buildTestState().apply { elapsed = phaseTime(seed, solid = true) }
        solid.placeObstaclesForTesting(shard)
        assertTrue(solid.obstacleBlocksSegment(0f, 0f, 600f, 0f, 7f))
        val stopped = solid.friendlyShotThrough(300f)
        solid.blockProjectilesAtObstacles()
        assertTrue(stopped.spent)
        // Into its surface at the lethal speed, as into an asteroid.
        val contactX = 300f - 80f - core
        solid.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertEquals(GamePhase.GAME_OVER, solid.phase)
    }

    @Test
    fun aPhaseShardMaterialisingAroundTheCoreLetsItDriftOutUntilTheNextApproach() {
        val seed = 11
        val state = buildTestState().apply { elapsed = phaseTime(seed, solid = true) }
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 80f, kind = ObstacleKind.PHASE_SHARD, shapeSeed = seed))
        // The Core rests inside the materialised body, drifting out.
        state.flyIntoObstaclesForTesting(250f, 0f, -60f, 0f)
        assertEquals(250f, state.coreX, "no push-out")
        assertEquals(-60f, state.velocityX, "no bounce")
        assertEquals(state.maxHp, state.hp)
        assertEquals(0f, state.coreObstacleGap, "touching a solid surface")
        // An asteroid in its place would have thrown the Core out at once.
        val rock = buildTestState()
        rock.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 80f))
        rock.flyIntoObstaclesForTesting(250f, 0f, -60f, 0f)
        assertTrue(rock.coreX <= 300f - 80f - core, "pushed out of the rock: ${rock.coreX}")
        // Once clear, the next approach meets the shard like a rock.
        val speed = state.obstacleDamageStartSpeed() - 10f
        val contactX = 300f - 80f - core
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertTrue(state.velocityX < 0f, "it bounces off the shard: ${state.velocityX}")
        assertTrue(state.coreX <= contactX + 0.5f)
    }

    @Test
    fun enemiesArePushedOutOfASolidPhaseShardButNeverSlamOnIt() {
        val seed = 11
        val state = buildTestState().apply { elapsed = phaseTime(seed, solid = true) }
        state.placeObstaclesForTesting(testObstacle(500f, 0f, radius = 80f, kind = ObstacleKind.PHASE_SHARD, shapeSeed = seed))
        val thrown = state.dummy(x = 500f - 80f - 17f + 4f, y = 0f, hp = 10_000f)
        thrown.vx = 700f
        state.markThrown(thrown)
        state.resolveEnemyObstacles()
        assertEquals(500f - 80f - 17f, thrown.x, 0.01f, "pushed back to the surface")
        assertEquals(0f, thrown.vx, 0.01f, "inward velocity removed")
        assertEquals(thrown.maxHp, thrown.hp, "no slam on a phasing body")

        state.elapsed = phaseTime(seed, solid = false)
        val inside = state.dummy(x = 460f, y = 0f, hp = 10_000f)
        inside.vx = 300f
        state.resolveEnemyObstacles()
        assertEquals(460f, inside.x, "a ghost shard holds nobody")
        assertEquals(300f, inside.vx)
    }

    @Test
    fun theLastGuardPushesTheCoreOutOfSteadySolidBodiesOnly() {
        val seed = 11
        val contactX = 300f - 80f - core
        for (kind in listOf(ObstacleKind.ACCEL_GATE, ObstacleKind.DERELICT_MINE, ObstacleKind.THERMAL_VENT, ObstacleKind.PHASE_SHARD)) {
            // The Core bounces off an asteroid while its rest point lies inside the other body.
            val state = buildTestState().apply { elapsed = phaseTime(seed, solid = true) }
            state.placeObstaclesForTesting(
                testObstacle(300f, 0f, radius = 80f),
                testObstacle(contactX - 10f, 0f, radius = 40f, kind = kind, shapeSeed = seed),
            )
            val speed = state.obstacleDamageStartSpeed() - 10f
            state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
            assertTrue(state.velocityX < 0f, "$kind: it bounced off the asteroid")
            val bounced = contactX + state.velocityX * 0.5f * MutableGameState.FIXED_STEP
            assertEquals(bounced, state.coreX, 0.5f, "$kind leaves the Core where the bounce put it")
            assertEquals(state.maxHp, state.hp)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Weapon entities and Rock Echo.

    @Test
    fun weaponEntitiesStopOnlyOnShotStoppingBodies() {
        val seed = 11
        val ghostTime = phaseTime(seed, solid = false)
        val cases = listOf(
            Triple(ObstacleKind.ASTEROID, 1f, true),
            Triple(ObstacleKind.CRYSTAL, 1f, true),
            Triple(ObstacleKind.GRAVITY_WELL, 1f, true),
            Triple(ObstacleKind.PHASE_SHARD, phaseTime(seed, solid = true), true),
            Triple(ObstacleKind.PHASE_SHARD, ghostTime, false),
            Triple(ObstacleKind.BUMPER, 1f, false),
            Triple(ObstacleKind.ACCEL_GATE, 1f, false),
            Triple(ObstacleKind.DERELICT_MINE, 1f, false),
            Triple(ObstacleKind.THERMAL_VENT, 1f, false),
        )
        for ((kind, time, stops) in cases) {
            // An unmastered disc turns back at a body that stops it.
            val state = buildTestState(weapon = WeaponId.GYRE_DISC).apply { elapsed = time }
            state.weaponLevel = 1
            state.placeObstaclesForTesting(testObstacle(160f, 0f, radius = 40f, kind = kind, shapeSeed = seed))
            val disc = WeaponEntity(WeaponEntityKind.DISC, 40f, 0f, GYRE_RADIUS, GYRE_MAX_LIFE, GYRE_MAX_LIFE, vx = GYRE_SPEED, extent = GYRE_REACH)
            state.weaponRuntime.entities.clear()
            state.weaponRuntime.entities += disc
            repeat(12) { if (disc.phase == 0) state.advanceWeaponEntities(MutableGameState.FIXED_STEP) }
            assertEquals(if (stops) 1 else 0, disc.phase, "$kind at $time")
        }
    }

    @Test
    fun rockEchoRepeatsOnlyFromEchoingKinds() {
        val seed = 11
        fun echoes(vararg kindsNearby: ObstacleKind): Int {
            val state = buildTestState().apply { elapsed = phaseTime(seed, solid = true) }
            val spots = listOf(300f to 200f, 500f to -200f, 300f to -200f, 500f to 200f)
            state.placeObstaclesForTesting(*Array(kindsNearby.size) { index ->
                testObstacle(spots[index].first, spots[index].second, 40f, kind = kindsNearby[index], shapeSeed = seed)
            })
            state.setOff(ReactionId.SEISMIC, state.dummy(x = 400f))
            return state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>()
                .count { it.colorIndex == FxColorCode.quake(StatusElement.GRAVITIC) } - 1
        }
        assertEquals(1, echoes(ObstacleKind.ASTEROID, ObstacleKind.PHASE_SHARD, ObstacleKind.ACCEL_GATE, ObstacleKind.DERELICT_MINE),
            "a materialised phase shard, a gate and a mine stay silent beside the asteroid")
        assertEquals(0, echoes(ObstacleKind.THERMAL_VENT))
        assertEquals(3, echoes(ObstacleKind.BUMPER, ObstacleKind.CRYSTAL, ObstacleKind.GRAVITY_WELL, ObstacleKind.UNSTABLE),
            "bumpers, crystals, knots and unstable rocks echo (at most three)")
    }
}
