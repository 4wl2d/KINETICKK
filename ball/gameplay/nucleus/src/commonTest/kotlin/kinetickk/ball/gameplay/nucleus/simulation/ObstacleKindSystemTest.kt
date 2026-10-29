// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.NEAR_OBSTACLE_MAX_DISTANCE
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.phaseShardSolid
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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

    /**
     * At every fixed step of a whole cycle, both turns included, the Core bounces off a phase shard, an enemy inside it is
     * pushed out, a shot through it is spent and sight across it is blocked exactly when the content's cycle reads it
     * solid at that step's time. The renderer draws its fill from the same cycle at the same time.
     */
    @Test
    fun theCoreEnemiesShotsAndSightMeetAPhaseShardAtExactlyTheStepsItsCycleIsSolid() {
        val seed = 11
        val shard = testObstacle(300f, 0f, radius = 80f, kind = ObstacleKind.PHASE_SHARD, shapeSeed = seed)
        val steps = (kinds.phasePeriodSeconds / MutableGameState.FIXED_STEP).roundToInt()
        var solidSteps = 0
        var turns = 0
        var before = kinds.phaseShardSolid(1f - MutableGameState.FIXED_STEP, seed)
        for (step in 0..steps) {
            val time = 1f + step * MutableGameState.FIXED_STEP
            val solid = kinds.phaseShardSolid(time, seed)
            if (solid) solidSteps++
            if (solid != before) turns++
            before = solid
            val label = "step $step at $time (solid $solid)"
            val state = buildTestState().apply { elapsed = time }
            state.placeObstaclesForTesting(shard)
            assertEquals(solid, state.obstacleBlocksSegment(0f, 0f, 600f, 0f, 7f), "$label: sight")
            val shot = state.friendlyShotThrough(300f)
            state.blockProjectilesAtObstacles()
            assertEquals(solid, shot.spent, "$label: shot")
            val enemy = state.dummy(x = 300f, y = 60f, hp = 10_000f)
            state.resolveEnemyObstacles()
            assertEquals(if (solid) 80f + enemy.radius else 60f, enemy.y, 0.01f, "$label: enemy")
            val speed = state.obstacleDamageStartSpeed() - 10f
            val contactX = 300f - 80f - core
            state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
            if (solid) assertTrue(state.velocityX < 0f, "$label: the Core bounces") else assertEquals(speed, state.velocityX, "$label: the Core passes")
            assertEquals(state.maxHp, state.hp, label)
        }
        assertTrue(turns >= 2, "the sweep crosses both turns: $turns")
        val share = solidSteps / (steps + 1f)
        assertTrue(abs(share - kinds.phaseSolidShare) <= 2f / steps, "solid for its share of the cycle: $share")
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

    // -----------------------------------------------------------------------------------------
    // Gravity knots.

    private val knotRadius = kinds.wellCoreRadius

    /** The acceleration a body of [bodyRadius] feels [distance] from a knot's centre: linear from the core to the field's edge. */
    private fun expectedPull(surfacePull: Float, distance: Float, bodyRadius: Float): Float =
        surfacePull * ((kinds.wellInfluence - distance) / (kinds.wellInfluence - knotRadius - bodyRadius)).coerceIn(0f, 1f)

    private fun knot(x: Float, y: Float, repels: Boolean = false, id: Long = 41L) =
        testObstacle(x, y, radius = knotRadius, kind = ObstacleKind.GRAVITY_WELL, id = id, repels = repels)

    @Test
    fun aKnotPullsTheCoreTowardItsCentreAndARepulsorPushesItAway() {
        val step = MutableGameState.FIXED_STEP
        for (repels in listOf(false, true)) {
            val sign = if (repels) -1f else 1f
            fun restAt(distance: Float): MutableGameState = buildTestState().apply {
                placeObstaclesForTesting(knot(600f, 0f, repels))
                flyIntoObstaclesForTesting(600f - distance, 0f, 0f, 0f)
            }
            for (distance in listOf(60f, 150f, kinds.wellInfluence - 1f)) {
                val state = restAt(distance)
                assertEquals(sign * expectedPull(kinds.wellCorePull, distance, core) * step, state.velocityX, 0.0005f,
                    "repels $repels at $distance")
                assertEquals(0f, state.velocityY)
            }
            assertTrue(restAt(60f).velocityX * sign > restAt(150f).velocityX * sign, "strongest near the core")
            assertEquals(0f, restAt(kinds.wellInfluence).velocityX, "nothing at the field's edge")
            assertEquals(0f, restAt(kinds.wellInfluence + 40f).velocityX, "nothing outside")
        }
        // The pull adds to the Core's travel along the line to the knot's centre.
        val state = buildTestState()
        state.placeObstaclesForTesting(knot(0f, 400f))
        state.flyIntoObstaclesForTesting(0f, 250f, 300f, 0f)
        assertEquals(300f, state.velocityX)
        assertEquals(expectedPull(kinds.wellCorePull, 150f, core) * step, state.velocityY, 0.0005f)
    }

    @Test
    fun aKnotPullsEnemiesByTheirKnockbackIntakeAndLeavesImmovableFramesAlone() {
        val step = MutableGameState.FIXED_STEP
        for (repels in listOf(false, true)) {
            val sign = if (repels) -1f else 1f
            val state = buildTestState()
            state.placeObstaclesForTesting(knot(600f, 0f, repels))
            val drifter = state.dummy(x = 450f, y = 0f)
            val elite = state.dummy(x = 750f, y = 0f, type = EnemyType.ELITE)
            val charger = state.dummy(x = 600f, y = 150f, type = EnemyType.CHARGER)
            val outside = state.dummy(x = 600f, y = -(kinds.wellInfluence + 1f))
            // The Core rests far outside the field.
            state.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
            assertEquals(0f, state.velocityX)
            val drifterPull = expectedPull(kinds.wellEnemyPull, 150f, drifter.radius) * state.knockbackTaken(drifter) * step
            assertEquals(sign * drifterPull, drifter.vx, 0.001f, "repels $repels")
            assertEquals(0f, drifter.vy)
            val elitePull = expectedPull(kinds.wellEnemyPull, 150f, elite.radius) * state.knockbackTaken(elite) * step
            assertEquals(-sign * elitePull, elite.vx, 0.001f)
            assertTrue(abs(elite.vx) < 0.5f * abs(drifter.vx), "an Elite is dragged far less: ${elite.vx} / ${drifter.vx}")
            assertEquals(0f, charger.vx)
            assertEquals(0f, charger.vy, "a frame that never moves stays put")
            assertEquals(0f, outside.vy, "nothing outside the field")
        }
    }

    @Test
    fun onlyKnotsWithinTheActivityRangeOfTheCoreMoveEnemies() {
        for ((offset, moves) in listOf(ENEMY_FIELD_ACTIVITY_RANGE - 1f to true, ENEMY_FIELD_ACTIVITY_RANGE + 1f to false)) {
            val state = buildTestState()
            state.placeObstaclesForTesting(knot(offset, 0f))
            val enemy = state.dummy(x = offset - 150f, y = 0f)
            state.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
            assertEquals(moves, enemy.vx > 0f, "a knot $offset from the Core")
        }
    }

    /** Calibrated Coil gains Overdrive on BOOST from a knot and vents heat on BOOST from a gate. */
    private val boostContent = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.BOOST, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.GRAVITY_WELL)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
        EffectClause(EffectTrigger.BOOST, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.ACCEL_GATE)),
            immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
    )

    /** A run with the BOOST clauses and one attracting knot (id 41) at (0, 300). */
    private fun slingState(): MutableGameState = buildTestState(content = boostContent).apply {
        own("Calibrated Coil")
        heat = 50f
        placeObstaclesForTesting(knot(0f, 300f))
    }

    private fun radians(degrees: Float): Float = degrees * PI.toFloat() / 180f

    /** Enters the knot's field heading east at [speed], then leaves it [turnDegrees] off that heading at the same speed. */
    private fun MutableGameState.slingThrough(turnDegrees: Float, speed: Float) {
        flyIntoObstaclesForTesting(-100f, 100f, speed, 0f)
        assertEquals(41L, slingWellId, "the knot's field holds the Core")
        val turn = radians(turnDegrees)
        flyIntoObstaclesForTesting(400f, 100f, cos(turn) * speed, sin(turn) * speed)
    }

    @Test
    fun leavingAKnotAfterAHardTurnAtSpeedSlingsTheCoreOnce() {
        val state = slingState()
        val cap = state.obstacleLethalSpeed() * kinds.boostLethalShare
        assertTrue(800f + kinds.slingImpulse < cap, "the test sling stays under the boost cap: $cap")
        state.takeSoundCues()
        state.takeVisualFxCues()
        state.slingThrough(100f, 800f)
        assertEquals(800f + kinds.slingImpulse, state.speed, 0.01f)
        assertEquals(radians(100f), atan2(state.velocityY, state.velocityX), 0.0001f, "thrown on along its travel")
        assertTrue(state.overdriveCharge > 0f, "BOOST fired with the knot's kind")
        assertEquals(50f, state.heat, "a gate's BOOST clause stays quiet")
        assertEquals(1, state.takeSoundCues().count { it == GameplayAudioCue.DASH })
        assertEquals(1, state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>()
            .count { it.colorIndex == FxColorCode.elementTone(StatusElement.GRAVITIC) })
        assertEquals(NO_OBSTACLE_ID, state.slingWellId)
        // Out in open space nothing more happens.
        val charge = state.overdriveCharge
        state.elapsed += 1f
        state.flyIntoObstaclesForTesting(400f, 100f, state.velocityX, state.velocityY)
        assertEquals(charge, state.overdriveCharge)
        assertEquals(800f + kinds.slingImpulse, state.speed, 0.01f)
        assertTrue(state.takeSoundCues().none { it == GameplayAudioCue.DASH })
    }

    @Test
    fun aGentleTurnOrASlowExitSlingsNothing() {
        // The turn and speed thresholds, each just met and just missed (the knot's pull tilts the entry heading by
        // well under a degree).
        for ((turn, speed, slings) in listOf(
            Triple(92f, 800f, true), Triple(88f, 800f, false), Triple(60f, 800f, false),
            Triple(100f, kinds.slingMinimumSpeed + 1f, true), Triple(100f, kinds.slingMinimumSpeed - 1f, false),
            Triple(100f, 500f, false),
        )) {
            val state = slingState()
            state.slingThrough(turn, speed)
            assertEquals(if (slings) speed + kinds.slingImpulse else speed, state.speed, 0.01f, "$turn deg at $speed")
            assertEquals(slings, state.overdriveCharge > 0f, "BOOST at $turn deg and $speed")
            assertEquals(NO_OBSTACLE_ID, state.slingWellId)
        }
    }

    @Test
    fun aKnotThatLeavesTheWindowWhileHoldingTheCoreSlingsNothing() {
        val state = slingState()
        state.flyIntoObstaclesForTesting(-100f, 100f, 800f, 0f)
        assertEquals(41L, state.slingWellId)
        state.clearObstaclesForTesting()
        val turn = radians(100f)
        state.flyIntoObstaclesForTesting(400f, 100f, cos(turn) * 800f, sin(turn) * 800f)
        assertEquals(800f, state.speed, 0.01f)
        assertEquals(0f, state.overdriveCharge)
        assertEquals(NO_OBSTACLE_ID, state.slingWellId)
        assertEquals(0f, state.slingEntryHeading)
    }

    @Test
    fun passingFromOneKnotsFieldIntoAnothersSettlesTheFirstAndHoldsTheSecond() {
        val state = slingState()
        state.placeObstaclesForTesting(knot(0f, 300f), knot(0f, -300f, id = 42L))
        state.flyIntoObstaclesForTesting(-100f, 100f, 800f, 0f)
        assertEquals(41L, state.slingWellId)
        // Into the second knot's field only, 100 degrees off the entry heading.
        val turn = radians(100f)
        state.flyIntoObstaclesForTesting(150f, -150f, cos(turn) * 800f, sin(turn) * 800f)
        assertEquals(800f + kinds.slingImpulse, state.speed, 1.5f, "the first knot slings (the second pulls a little)")
        assertTrue(state.overdriveCharge > 0f)
        assertEquals(42L, state.slingWellId, "the second knot holds the Core now")
        assertEquals(atan2(state.velocityY, state.velocityX), state.slingEntryHeading, "from its new heading")
    }

    @Test
    fun theNearestKnotWhoseFieldHoldsTheCoreOwnsTheSling() {
        // Two overlapping fields: whichever centre lies nearer holds the Core, wherever the lookup meets it.
        for ((y, holder) in listOf(30f to 41L, -30f to 42L)) {
            val state = slingState()
            state.placeObstaclesForTesting(knot(0f, 200f), knot(0f, -200f, id = 42L))
            state.flyIntoObstaclesForTesting(-100f, y, 800f, 0f)
            assertEquals(holder, state.slingWellId, "at y = $y")
        }
    }

    @Test
    fun everyObstacleBoostLeavesTheCoreAtMostNineTenthsOfTheLethalSpeedAndNeverSlowsIt() {
        val state = buildTestState(content = boostContent).apply { own("Calibrated Coil") }
        val lethal = state.obstacleLethalSpeed()
        val cap = lethal * kinds.boostLethalShare
        val impulses = listOf(kinds.slingImpulse, kinds.gateImpulse, kinds.ventImpulse, 2f * kinds.gateImpulse, 10_000f)
        var speed = 0f
        while (speed <= lethal * 1.2f) {
            for (impulse in impulses) {
                state.elapsed += 1f
                state.overdriveCharge = 0f
                state.setVelocityForTesting(speed * 0.6f, -speed * 0.8f)
                state.applyObstacleBoost(impulse, ObstacleKind.GRAVITY_WELL)
                val after = state.speed
                assertTrue(after <= max(speed, cap) + 0.01f, "$impulse at $speed left at $after (cap $cap)")
                assertTrue(after >= speed - 0.01f, "$impulse at $speed slowed to $after")
                if (speed + impulse <= cap) assertEquals(speed + impulse, after, 0.05f, "$impulse at $speed")
                if (speed > 1f) {
                    assertEquals(0.6f, state.velocityX / after, 0.0001f, "along its travel")
                    assertEquals(-0.8f, state.velocityY / after, 0.0001f)
                }
                assertTrue(state.overdriveCharge > 0f, "BOOST fires even when the cap absorbs the impulse ($impulse at $speed)")
            }
            speed += 37f
        }
        // At rest the boost follows the aim.
        state.setVelocityForTesting(0f, 0f)
        state.applyObstacleBoost(kinds.slingImpulse, ObstacleKind.GRAVITY_WELL)
        assertEquals(kinds.slingImpulse * state.lastAimDirectionX, state.velocityX, 0.01f)
        assertEquals(kinds.slingImpulse * state.lastAimDirectionY, state.velocityY, 0.01f)
    }

    // -----------------------------------------------------------------------------------------
    // Accelerator gates.

    private val gateRadius = 100f
    /** From the gate's centre to each post's centre: the posts sit inside its body. */
    private val halfSpan = gateRadius - kinds.gatePostRadius

    /** A run with the BOOST clauses and one gate of radius 100 at (300, 0) whose normal points along [angle]. */
    private fun gateState(angle: Float = 0f): MutableGameState = buildTestState(content = boostContent).apply {
        own("Calibrated Coil")
        heat = 50f
        placeObstaclesForTesting(testGate(300f, 0f, angle, gateRadius))
    }

    /** One step at [speed] heading [headingDegrees] whose midpoint lies on the gate line x = 300 at height [y]. */
    private fun MutableGameState.stepAcrossGateLine(headingDegrees: Float, speed: Float, y: Float = 0f) {
        val heading = radians(headingDegrees)
        val vx = cos(heading) * speed
        val vy = sin(heading) * speed
        val half = 0.5f * MutableGameState.FIXED_STEP
        flyIntoObstaclesForTesting(300f + vx * half, y + vy * half, vx, vy)
    }

    @Test
    fun crossingAnAcceleratorGateBoostsTheCoreAlongItsTravelEitherWay() {
        val cap = gateState().obstacleLethalSpeed() * kinds.boostLethalShare
        assertTrue(800f + kinds.gateImpulse < cap, "the test crossing stays under the boost cap: $cap")
        for (heading in listOf(0f, 180f, 30f, 210f)) {
            val state = gateState()
            state.takeSoundCues()
            state.takeVisualFxCues()
            state.stepAcrossGateLine(heading, 800f)
            assertEquals(800f + kinds.gateImpulse, state.speed, 0.01f, "heading $heading")
            assertEquals(cos(radians(heading)), state.velocityX / state.speed, 0.0001f, "along its travel, heading $heading")
            assertEquals(sin(radians(heading)), state.velocityY / state.speed, 0.0001f)
            assertTrue(state.heat < 50f, "BOOST fired with the gate's kind (heading $heading)")
            assertEquals(0f, state.overdriveCharge, "a knot's BOOST clause stays quiet")
            assertEquals(1, state.takeSoundCues().count { it == GameplayAudioCue.DASH })
            val rings = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>().filter { it.x == 300f && it.y == 0f }
            assertEquals(1, rings.size, "one ring around the gate")
            assertEquals(halfSpan * 1.3f, rings.single().maxRadius, 0.01f)
            assertEquals(0, rings.single().colorIndex, "the volt tone")
        }
    }

    @Test
    fun aGateBoostsOnlyACrossingBetweenItsPostsWithinSixtyDegreesOfItsNormal() {
        for ((heading, y, boosts) in listOf(
            Triple(0f, halfSpan - 1f, true), Triple(0f, halfSpan + 1f, false), Triple(180f, -(halfSpan - 1f), true),
            Triple(180f, -(halfSpan + 1f), false), Triple(58f, 0f, true), Triple(62f, 0f, false), Triple(70f, 0f, false),
            Triple(238f, 0f, true), Triple(-70f, 0f, false), Triple(90f, 0f, false),
        )) {
            val state = gateState()
            state.stepAcrossGateLine(heading, 800f, y)
            assertEquals(if (boosts) 800f + kinds.gateImpulse else 800f, state.speed, 0.01f, "heading $heading at $y")
            assertEquals(boosts, state.heat < 50f, "BOOST heading $heading at $y")
        }
        // A step that only reaches the line crosses it; the next step leaving the line does not cross it again.
        assertTrue(accelGateCrossed(290f, 0f, 300f, 0f, 300f, 0f, 0f, halfSpan, kinds.gateAlignment))
        assertFalse(accelGateCrossed(300f, 0f, 310f, 0f, 300f, 0f, 0f, halfSpan, kinds.gateAlignment))
        assertFalse(accelGateCrossed(280f, 0f, 290f, 0f, 300f, 0f, 0f, halfSpan, kinds.gateAlignment), "the same side")
        // A turned gate, its normal along 135 degrees: along the normal, 45 degrees off its reverse, and 75 degrees off it.
        for ((heading, boosts) in listOf(135f to true, 0f to true, 210f to false)) {
            val turned = gateState(radians(135f))
            turned.stepAcrossGateLine(heading, 800f)
            assertEquals(if (boosts) 800f + kinds.gateImpulse else 800f, turned.speed, 0.01f, "heading $heading through a turned gate")
        }
    }

    @Test
    fun aGateRestsForItsCooldownAfterABoost() {
        val state = gateState()
        state.elapsed = 1f
        state.stepAcrossGateLine(0f, 800f)
        assertEquals(800f + kinds.gateImpulse, state.speed, 0.01f)
        // Back through before the cooldown ends: nothing.
        state.elapsed = 1f + kinds.gateCooldownSeconds - 0.01f
        state.stepAcrossGateLine(180f, 800f)
        assertEquals(800f, state.speed, 0.01f, "still resting")
        // Through again as it ends: boosted.
        state.elapsed = 1f + kinds.gateCooldownSeconds
        state.stepAcrossGateLine(180f, 800f)
        assertEquals(800f + kinds.gateImpulse, state.speed, 0.01f, "ready again after its cooldown")
        // Each gate keeps its own cooldown.
        val first = state.obstacleField.obstacles.single { it.kind == ObstacleKind.ACCEL_GATE }
        assertEquals(1f + 2f * kinds.gateCooldownSeconds, first.ricochetReadyAt, "the gate keeps its own next boost time")
        state.placeObstaclesForTesting(first, testGate(300f, 400f, 0f, gateRadius, id = 62L))
        state.stepAcrossGateLine(0f, 800f, 400f)
        assertEquals(800f + kinds.gateImpulse, state.speed, 0.01f, "the second gate is ready")
    }

    @Test
    fun aGateBoostStopsAtNineTenthsOfTheLethalSpeedAndStillFiresBoost() {
        val probe = gateState()
        val lethal = probe.obstacleLethalSpeed()
        val cap = lethal * kinds.boostLethalShare
        val near = gateState()
        near.stepAcrossGateLine(0f, lethal * 0.85f)
        assertEquals(cap, near.speed, 0.01f, "at 0.85 of the lethal speed the gate tops out at 0.9")
        assertTrue(near.heat < 50f)
        val above = gateState()
        above.stepAcrossGateLine(0f, lethal * 0.95f)
        assertEquals(lethal * 0.95f, above.speed, 0.01f, "above the cap the speed stays")
        assertTrue(above.heat < 50f, "BOOST fires even when the cap absorbs the impulse")
    }

    // -----------------------------------------------------------------------------------------
    // Thermal vents.

    private val ventSeed = 23
    private val ventRadius = 80f
    private val plume = ventRadius + kinds.ventPlumeMargin

    /** Lifts on BOOST from a vent show as Overdrive; a gate's BOOST clause would vent heat. */
    private val ventBoostContent = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.BOOST, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.THERMAL_VENT)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
        EffectClause(EffectTrigger.BOOST, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.ACCEL_GATE)),
            immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
    )

    /** A run time from [from] at which the vent with [seed] erupts (or stays quiet) for the next half second. */
    private fun ventTime(seed: Int, erupting: Boolean, from: Float = 1f): Float {
        var time = from
        repeat(2_000) {
            if ((0..60).all { step -> kinds.ventErupting(time + step * MutableGameState.FIXED_STEP, seed) == erupting }) return time
            time += MutableGameState.FIXED_STEP
        }
        fail("no ${if (erupting) "eruption" else "quiet"} window for seed $seed")
    }

    /** A run with the vent BOOST clause and one vent (radius 80) at (300, 0), at heat 20. */
    private fun ventState(time: Float): MutableGameState = buildTestState(content = ventBoostContent).apply {
        own("Calibrated Coil")
        heat = 20f
        elapsed = time
        placeObstaclesForTesting(testObstacle(300f, 0f, radius = ventRadius, kind = ObstacleKind.THERMAL_VENT, shapeSeed = ventSeed))
    }

    @Test
    fun ridingAnEruptingVentHeatsAndLiftsTheCoreOncePerEruption() {
        val eruption = ventTime(ventSeed, erupting = true)
        val state = ventState(eruption)
        state.takeSoundCues()
        state.takeVisualFxCues()
        state.flyIntoObstaclesForTesting(200f, 0f, 400f, 0f)
        assertEquals(20f + kinds.ventHeat, state.heat, "the plume's heat, and no gate clause")
        assertEquals(400f + kinds.ventImpulse, state.speed, 0.01f)
        assertEquals(0f, state.velocityY, "lifted along its travel")
        assertTrue(state.overdriveCharge > 0f, "BOOST fired with the vent's kind")
        assertEquals(1, state.takeSoundCues().count { it == GameplayAudioCue.DASH })
        val rings = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>()
            .filter { it.colorIndex == FxColorCode.elementTone(StatusElement.THERMAL) }
        assertEquals(1, rings.size)
        assertEquals(plume, rings.single().maxRadius, "the ring fills the plume")
        // Later in the same eruption: nothing more.
        val charge = state.overdriveCharge
        state.elapsed += 0.2f
        state.flyIntoObstaclesForTesting(200f, 0f, 400f, 0f)
        assertEquals(20f + kinds.ventHeat, state.heat)
        assertEquals(400f, state.speed, 0.01f)
        assertEquals(charge, state.overdriveCharge)
        // The next eruption lifts again, and the one after that brings the Core to full heat: OVERHEAT.
        state.elapsed = eruption + kinds.ventPeriodSeconds
        state.flyIntoObstaclesForTesting(200f, 0f, 400f, 0f)
        assertEquals(20f + 2f * kinds.ventHeat, state.heat)
        assertEquals(400f + kinds.ventImpulse, state.speed, 0.01f)
        assertFalse(state.overheated)
        state.elapsed = eruption + 2f * kinds.ventPeriodSeconds
        state.takeSoundCues()
        state.flyIntoObstaclesForTesting(200f, 0f, 400f, 0f)
        assertEquals(MutableGameState.MAX_HEAT, state.heat, "heat stops at full")
        assertTrue(state.overheated, "OVERHEAT at full heat")
        assertTrue(GameplayAudioCue.OVERHEAT in state.takeSoundCues())
        assertEquals(400f + kinds.ventImpulse, state.speed, 0.01f, "the updraft lifts an overheated Core too")
    }

    @Test
    fun aVentLiftsOnlyACoreInsideItsPlumeWhileItErupts() {
        val eruption = ventTime(ventSeed, erupting = true)
        for ((distance, rides) in listOf(plume + core - 1f to true, plume + core + 1f to false)) {
            val state = ventState(eruption)
            state.flyIntoObstaclesForTesting(300f, distance, 400f, 0f)
            assertEquals(if (rides) 20f + kinds.ventHeat else 20f, state.heat, "at $distance from the vent")
            assertEquals(if (rides) 400f + kinds.ventImpulse else 400f, state.speed, 0.01f)
        }
        val quiet = ventState(ventTime(ventSeed, erupting = false))
        quiet.flyIntoObstaclesForTesting(300f, 0f, 400f, 0f)
        assertEquals(20f, quiet.heat, "a quiet vent")
        assertEquals(400f, quiet.speed, 0.01f)
        assertEquals(0f, quiet.overdriveCharge)
        // A resting Core is lifted away from the vent, not along its aim.
        val resting = ventState(eruption)
        resting.lastAimDirectionX = 0f
        resting.lastAimDirectionY = 1f
        resting.flyIntoObstaclesForTesting(200f, 0f, 0f, 0f)
        assertEquals(-kinds.ventImpulse, resting.velocityX, 0.01f)
        assertEquals(0f, resting.velocityY, 0.01f)
    }

    @Test
    fun aCoreHoveringInAPlumeIsLiftedOnceInEveryEruptionItMeets() {
        // Step by step through a few periods, early in a run and late in one (coarser times), always inside the plume.
        for (start in listOf(1f, 600f)) {
            val state = ventState(start)
            state.takeSoundCues()
            var eruptions = 0
            var lifts = 0
            var wasErupting = false
            repeat((3.5f * kinds.ventPeriodSeconds / MutableGameState.FIXED_STEP).toInt()) {
                state.elapsed += MutableGameState.FIXED_STEP
                val erupting = kinds.ventErupting(state.elapsed, ventSeed)
                if (erupting && !wasErupting) eruptions++
                wasErupting = erupting
                state.flyIntoObstaclesForTesting(260f, 0f, 0f, 0f)
                lifts += state.takeSoundCues().count { it == GameplayAudioCue.DASH }
            }
            assertTrue(eruptions >= 3, "from $start: $eruptions eruptions")
            assertEquals(eruptions, lifts, "from $start")
        }
    }

    @Test
    fun anEruptingPlumeScorchesEveryEnemyInsideItFourTimesASecond() {
        val eruption = ventTime(ventSeed, erupting = true)
        val state = ventState(eruption)
        val inside = state.dummy(x = 300f + plume, y = 0f)
        val outside = state.dummy(x = 300f, y = 0f).apply { y = -(plume + radius + 1f) }
        // The Core rests far from the plume (inside the field pass's range) for half a second of the eruption.
        repeat(60) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.flyIntoObstaclesForTesting(1_000f, 600f, 0f, 0f)
        }
        assertTrue(kinds.ventErupting(state.elapsed, ventSeed))
        assertEquals(2, inside.stacksOf(StatusElement.THERMAL), "two Thermal stacks in half a second")
        assertEquals(0, outside.stacksOf(StatusElement.THERMAL), "nothing just outside the plume")
        assertEquals(20f, state.heat, "the Core never touched the plume")

        val quiet = ventState(ventTime(ventSeed, erupting = false))
        val idle = quiet.dummy(x = 300f, y = 0f)
        repeat(60) {
            quiet.elapsed += MutableGameState.FIXED_STEP
            quiet.flyIntoObstaclesForTesting(1_000f, 600f, 0f, 0f)
        }
        assertEquals(0, idle.stacksOf(StatusElement.THERMAL), "a quiet vent scorches nobody")
    }

    // -----------------------------------------------------------------------------------------
    // Crystals.

    private val crystalRadius = 80f
    private val crystalContactX = 300f - crystalRadius - core

    /** Calibrated Coil gains Overdrive on SHATTER from a crystal and vents heat on SHATTER from a mine. */
    private val crystalContent = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.SHATTER, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.CRYSTAL)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
        EffectClause(EffectTrigger.SHATTER, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.DERELICT_MINE)),
            immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
    )

    /** A run with the SHATTER clauses, at heat 50, with one crystal (radius 80, id 81) at (300, 0). */
    private fun crystalState(): MutableGameState = buildTestState(content = crystalContent).apply {
        own("Calibrated Coil")
        heat = 50f
        shield = 0f
        placeObstaclesForTesting(crystal(300f, 0f))
    }

    private fun crystal(x: Float, y: Float, id: Long = 81L) =
        testObstacle(x, y, radius = crystalRadius, kind = ObstacleKind.CRYSTAL, id = id, shapeSeed = 13)

    /** One step head-on into the crystal at (300, 0) at [speed], meeting its surface halfway through the step. */
    private fun MutableGameState.flyIntoCrystal(speed: Float) =
        flyIntoObstaclesForTesting(crystalContactX + speed * 0.5f * MutableGameState.FIXED_STEP, 0f, speed, 0f)

    @Test
    fun aCrystalHitAtTheDamageStartShattersForGoodAndTheCoreFliesOnUnhurt() {
        val state = crystalState()
        val speed = state.obstacleDamageStartSpeed()
        // A dummy on the first shard's line, 150 from the crystal's centre.
        val shardAngle = state.elapsed * 1.7f
        val onLine = state.dummy(x = 300f + cos(shardAngle) * 150f, y = sin(shardAngle) * 150f)
        state.takeVisualFxCues()
        state.flyIntoCrystal(speed)
        assertTrue(state.obstacleField.obstacles.none { it.id == 81L }, "the crystal is gone")
        assertEquals(listOf(81L), state.destroyedObstacleIds, "and remembered as destroyed")
        assertEquals(speed * kinds.crystalSpeedRetention, state.velocityX, 0.01f, "the Core keeps 88 % of its speed")
        assertEquals(0f, state.velocityY)
        val flown = speed * kinds.crystalSpeedRetention * 0.5f * MutableGameState.FIXED_STEP
        assertEquals(crystalContactX + flown, state.coreX, 0.05f, "and flies on through where the crystal stood")
        assertEquals(state.maxHp, state.hp, "a crystal never hurts")
        assertEquals(GamePhase.RUNNING, state.phase)
        val arcs = state.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>().filter { it.fromX == 300f && it.fromY == 0f }
        assertEquals(kinds.crystalShards, arcs.size, "six shard lines from its centre")
        assertEquals(kinds.crystalShardDamage * state.runScale(), onLine.maxHp - onLine.hp, 0.01f, "a shard cuts the dummy on its line")
        assertEquals(1, onLine.stacksOf(StatusElement.CRYO), "and chills it")
        val data = state.pickups.filter { it.type == PickupType.DATA }
        assertEquals(kinds.crystalData, data.size, "two Data")
        data.forEach { pickup ->
            assertEquals(300f, pickup.x)
            assertEquals(0f, pickup.y)
            assertEquals(160f, kotlin.math.hypot(pickup.vx, pickup.vy), 0.01f)
        }
        assertTrue(state.overdriveCharge > 0f, "SHATTER fired with the crystal's kind")
        assertEquals(50f, state.heat, "a mine's SHATTER clause stays quiet")
    }

    @Test
    fun aGentlerHitOnlyBouncesOffACrystalAndAFatalOneStillShattersIt() {
        val gentle = crystalState()
        gentle.flyIntoCrystal(gentle.obstacleDamageStartSpeed() - 1f)
        assertTrue(gentle.velocityX < 0f, "it bounces: ${gentle.velocityX}")
        assertEquals(1, gentle.obstacleField.obstacles.count { it.id == 81L }, "the crystal stays")
        assertTrue(gentle.destroyedObstacleIds.isEmpty())
        assertEquals(gentle.maxHp, gentle.hp)
        assertEquals(0f, gentle.overdriveCharge, "no SHATTER")
        assertTrue(gentle.pickups.isEmpty())
        // Past the lethal speed a crystal still only breaks.
        val fast = crystalState()
        val speed = fast.obstacleLethalSpeed() + 200f
        fast.flyIntoCrystal(speed)
        assertEquals(GamePhase.RUNNING, fast.phase)
        assertEquals(fast.maxHp, fast.hp)
        assertEquals(speed * kinds.crystalSpeedRetention, fast.velocityX, 0.01f)
        assertEquals(listOf(81L), fast.destroyedObstacleIds)
    }

    @Test
    fun aShatteredCrystalStaysGoneWhenItsChunkIsGeneratedAgain() {
        val state = buildTestState(content = contentWithObstacleMix(ObstacleMix(crystal = 1f)))
        val crystals = state.obstacleField.obstacles.filter { it.kind == ObstacleKind.CRYSTAL }
        assertTrue(crystals.isNotEmpty(), "the window holds crystals")
        val broken = crystals.first()
        state.shatterCrystal(state.obstacleField.obstacles.indexOf(broken), broken, 0f, byCore = false)
        val before = state.obstacleField.size
        state.obstacleField = ObstacleField.EMPTY
        state.refreshObstacleWindow()
        assertEquals(before, state.obstacleField.size, "everything else comes back")
        assertTrue(state.obstacleField.obstacles.none { it.id == broken.id }, "the broken crystal does not")
    }

    @Test
    fun slammedEnemiesBreakOneCrystalPerStepAndTheOthersWaitForTheirNextContact() {
        val state = crystalState()
        state.placeObstaclesForTesting(crystal(500f, 0f, id = 81L), crystal(500f, 400f, id = 82L))
        fun thrownInto(y: Float) = state.dummy(x = 0f, y = y, hp = 10_000f).apply {
            x = 500f - crystalRadius - radius + 4f
            vx = 700f
            state.markThrown(this)
        }
        val first = thrownInto(0f)
        val second = thrownInto(400f)
        state.resolveEnemyObstacles()
        assertTrue(first.hp < first.maxHp && second.hp < second.maxHp, "both slams landed")
        assertEquals(listOf(81L), state.destroyedObstacleIds, "one crystal breaks this step")
        assertEquals(listOf(82L), state.obstacleField.obstacles.map { it.id })
        assertTrue(state.overdriveCharge > 0f, "SHATTER fired with the crystal's kind")
        // The second crystal waits for its next contact: the enemy is thrown in again once its slam is ready.
        state.elapsed += canonicalGameplayContent.obstacles.slamCooldownSeconds
        second.x = 500f - crystalRadius - second.radius + 4f
        second.vx = 700f
        state.resolveEnemyObstacles()
        assertEquals(listOf(81L, 82L), state.destroyedObstacleIds)
        // An enemy drifting into a crystal never breaks it.
        val calm = crystalState()
        val drifter = calm.dummy(x = 0f, y = 0f).apply {
            x = 300f - crystalRadius - radius + 2f
            vx = 100f
        }
        calm.resolveEnemyObstacles()
        assertEquals(drifter.maxHp, drifter.hp)
        assertEquals(1, calm.obstacleField.size)
    }

    // -----------------------------------------------------------------------------------------
    // Derelict mines.

    private val mineRadius = 50f
    private val armReach = mineRadius + kinds.mineArmMargin + core
    private val blastReach = kinds.mineBlastRadius + core

    /** Calibrated Coil gains Overdrive on SHATTER from a mine and vents heat when a mine hurts the Core. */
    private val mineContent = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.SHATTER, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.DERELICT_MINE)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
        EffectClause(EffectTrigger.HURT, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.DERELICT_MINE)),
            immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
    )

    private fun mine(x: Float, y: Float, id: Long = 71L, fuseAt: Float = 0f) =
        testObstacle(x, y, radius = mineRadius, kind = ObstacleKind.DERELICT_MINE, id = id, fuseAt = fuseAt)

    /** A run 1 s in with the mine clauses, at heat 50 and no shield, with [obstacles] placed. */
    private fun mineState(vararg obstacles: kinetickk.ball.gameplay.nucleus.model.Obstacle, rebirth: Int = 0): MutableGameState =
        buildTestState(rebirth = rebirth, content = mineContent).apply {
            own("Calibrated Coil")
            heat = 50f
            shield = 0f
            elapsed = 1f
            placeObstaclesForTesting(*obstacles)
        }

    private fun MutableGameState.mineWithId(id: Long) = obstacleField.obstacles.singleOrNull { it.id == id }

    @Test
    fun aMineArmsWhenTheCoreComesNearAndBlowsAfterItsFuse() {
        val state = mineState(mine(300f, 0f), rebirth = 3)
        val near = state.dummy(x = 400f, y = 0f)
        val far = state.dummy(x = 300f, y = 0f).apply { y = kinds.mineBlastRadius + radius + 1f }
        state.flyIntoObstaclesForTesting(300f - armReach - 1f, 0f, 0f, 0f)
        assertEquals(0f, state.mineWithId(71L)!!.fuseAt, "just outside its arming reach it sleeps")
        state.takeVisualFxCues()
        state.flyIntoObstaclesForTesting(300f - armReach + 1f, 0f, 0f, 0f)
        assertEquals(1f + kinds.mineFuseSeconds, state.mineWithId(71L)!!.fuseAt, "just inside it arms")
        val telegraph = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>().filter { it.x == 300f && it.y == 0f }
        assertEquals(1, telegraph.size)
        assertEquals(kinds.mineFuseSeconds, telegraph.single().life, "the telegraph lasts the fuse")
        assertEquals(kinds.mineBlastRadius, telegraph.single().maxRadius, "and reaches the blast's edge")
        assertEquals(4, telegraph.single().colorIndex, "in the threat tone")
        // Moving away again does not disarm it.
        state.elapsed = 1f + kinds.mineFuseSeconds - 0.01f
        state.flyIntoObstaclesForTesting(300f - armReach + 1f, 0f, 0f, 0f)
        assertEquals(state.maxHp, state.hp, "nothing before the fuse runs out")
        assertTrue(state.mineWithId(71L) != null)
        state.elapsed = 1f + kinds.mineFuseSeconds
        state.flyIntoObstaclesForTesting(300f - armReach + 1f, 0f, 0f, 0f)
        assertEquals(null, state.mineWithId(71L), "it blew")
        assertEquals(listOf(71L), state.destroyedObstacleIds, "for good")
        val expected = state.maxHp * kinds.mineBlastShare * (1f + canonicalGameplayContent.obstacles.damagePerTier * 3)
        assertEquals(state.maxHp - expected, state.hp, 0.01f, "a tenth of integrity, grown by the tier, as obstacle damage")
        val dealt = near.maxHp - near.hp
        assertEquals(kinds.mineBlastEnemyDamage * state.runScale(), dealt, kinds.mineBlastEnemyDamage * state.runScale() * 0.01f)
        assertEquals(far.maxHp, far.hp, "nothing just outside the blast")
        assertTrue(state.overdriveCharge > 0f, "SHATTER fired with the mine's kind")
        assertEquals(30f, state.heat, "HURT fired with the mine's kind")
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun aMineBlastReachesTheCoreOnlyWithinItsRadius() {
        for ((distance, hurts) in listOf(blastReach - 1f to true, blastReach + 1f to false)) {
            val state = mineState(mine(300f, 0f, fuseAt = 1f))
            state.flyIntoObstaclesForTesting(300f - distance, 0f, 0f, 0f)
            assertEquals(null, state.mineWithId(71L))
            assertEquals(hurts, state.hp < state.maxHp, "a blast $distance from the Core")
            assertEquals(hurts, state.heat < 50f, "HURT at $distance")
        }
    }

    @Test
    fun aFastPassEscapesAMineAndASlowOneIsCaught() {
        for ((speed, escapes) in listOf(1_000f to true, 300f to false)) {
            val state = mineState(mine(600f, 0f))
            var x = 300f
            repeat(150) {
                state.elapsed += MutableGameState.FIXED_STEP
                x += speed * MutableGameState.FIXED_STEP
                state.flyIntoObstaclesForTesting(x, 0f, speed, 0f)
            }
            assertEquals(null, state.mineWithId(71L), "the mine blew behind a pass at $speed")
            assertEquals(escapes, state.hp == state.maxHp, "a pass at $speed")
        }
    }

    @Test
    fun aPlayerShotSetsAMineOffOnTheQuickFuseAndAHostileOneDoesNot() {
        val quick = 1f + kinds.mineQuickFuseSeconds
        val friendly = mineState(mine(300f, 0f))
        val shot = friendly.friendlyShotThrough(300f)
        friendly.blockProjectilesAtObstacles()
        assertFalse(shot.spent, "the shot flies on")
        assertEquals(quick, friendly.mineWithId(71L)!!.fuseAt)

        val hostile = mineState(mine(300f, 0f))
        val enemyShot = Projectile(x = 360f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, hostile = true, previousX = 180f, previousY = 0f)
        hostile.projectiles += enemyShot
        hostile.blockProjectilesAtObstacles()
        assertFalse(enemyShot.spent)
        assertEquals(0f, hostile.mineWithId(71L)!!.fuseAt, "a hostile shot leaves it dormant")

        // Beside the body: nothing.
        val beside = mineState(mine(300f, 0f))
        beside.projectiles += Projectile(x = 360f, y = mineRadius + 6f, vx = 500f, vy = 0f, radius = 5f, life = 2f, hostile = false,
            previousX = 180f, previousY = mineRadius + 6f)
        beside.blockProjectilesAtObstacles()
        assertEquals(0f, beside.mineWithId(71L)!!.fuseAt)

        // An armed mine's longer fuse shortens; a shorter one stays.
        for ((fuse, after) in listOf(1.5f to quick, 1.05f to 1.05f)) {
            val armed = mineState(mine(300f, 0f, fuseAt = fuse))
            armed.friendlyShotThrough(300f)
            armed.blockProjectilesAtObstacles()
            assertEquals(after, armed.mineWithId(71L)!!.fuseAt, "a fuse ending at $fuse")
        }
    }

    @Test
    fun aThrownEnemyTouchingAMineSetsItOffOnTheQuickFuse() {
        val quick = 1f + kinds.mineQuickFuseSeconds
        for ((offset, thrown, fused) in listOf(
            Triple(-1f, true, true), Triple(1f, true, false), Triple(-1f, false, false),
        )) {
            val state = mineState(mine(300f, 0f))
            val enemy = state.dummy(x = 0f, y = 0f)
            enemy.x = 300f + mineRadius + enemy.radius + offset
            if (thrown) state.markThrown(enemy)
            // The Core rests far outside the arming reach, inside the field pass's range.
            state.flyIntoObstaclesForTesting(1_000f, 600f, 0f, 0f)
            assertEquals(if (fused) quick else 0f, state.mineWithId(71L)!!.fuseAt, "thrown $thrown, ${offset} off its body's edge")
        }
    }

    @Test
    fun aBlastSetsOffTheMinesItReachesOnTheQuickFuse() {
        val chainReach = kinds.mineBlastRadius + mineRadius
        val state = mineState(
            mine(300f, 0f, id = 71L, fuseAt = 1f),
            mine(300f + chainReach - 1f, 0f, id = 72L),
            mine(300f, chainReach + 1f, id = 73L),
        )
        state.flyIntoObstaclesForTesting(1_000f, -600f, 0f, 0f)
        assertEquals(null, state.mineWithId(71L))
        assertEquals(1f + kinds.mineQuickFuseSeconds, state.mineWithId(72L)!!.fuseAt, "inside the blast: the quick fuse")
        assertEquals(0f, state.mineWithId(73L)!!.fuseAt, "just outside it: dormant")
        state.elapsed = 1f + kinds.mineQuickFuseSeconds
        state.flyIntoObstaclesForTesting(1_000f, -600f, 0f, 0f)
        assertEquals(null, state.mineWithId(72L), "the second one follows")
        assertEquals(listOf(71L, 72L), state.destroyedObstacleIds)
    }

    @Test
    fun atMostFourMinesDetonateInOneStepAndTheFifthWaitsForTheNext() {
        for (count in listOf(kinds.maximumMineDetonationsPerStep, kinds.maximumMineDetonationsPerStep + 1)) {
            val mines = Array(count) { index -> mine(400f + 300f * index, 600f, id = 100L + index, fuseAt = 1f) }
            val state = mineState(*mines)
            state.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
            val left = count - kinds.maximumMineDetonationsPerStep
            assertEquals(left, state.obstacleField.size, "$count due mines: ${kinds.maximumMineDetonationsPerStep} blow this step")
            assertEquals(kinds.maximumMineDetonationsPerStep, state.destroyedObstacleIds.size)
            state.elapsed += MutableGameState.FIXED_STEP
            state.flyIntoObstaclesForTesting(0f, 0f, 0f, 0f)
            assertEquals(0, state.obstacleField.size, "$count due mines: the rest blow the next step")
            assertEquals(count, state.destroyedObstacleIds.size)
        }
    }

    @Test
    fun aLethalMineBlastEndsTheRunAsAnObstacleCollision() {
        val state = mineState(mine(300f, 0f, fuseAt = 1f))
        state.hp = 1f
        state.flyIntoObstaclesForTesting(250f, 0f, 0f, 0f)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
    }

    // -----------------------------------------------------------------------------------------
    // Unstable asteroids and candidate isolation.

    @Test
    fun anUnstableAsteroidFiresShatterAndItsBlastHurtsWithItsKind() {
        val content = contentWithClauses(
            "Calibrated Coil",
            EffectClause(EffectTrigger.SHATTER, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.UNSTABLE)),
                immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
            EffectClause(EffectTrigger.HURT, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.UNSTABLE)),
                immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
        )
        val state = buildTestState(content = content).apply {
            own("Calibrated Coil")
            heat = 50f
            shield = 0f
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = 80f, kind = ObstacleKind.UNSTABLE, id = 91L))
        }
        val speed = state.obstacleDamageStartSpeed()
        state.flyIntoObstaclesForTesting(300f - 80f - core + speed * 0.5f * MutableGameState.FIXED_STEP, 0f, speed, 0f)
        assertEquals(listOf(91L), state.destroyedObstacleIds)
        assertEquals(state.maxHp - canonicalGameplayContent.obstacles.unstableBlastDamage, state.hp, 0.01f)
        assertTrue(state.overdriveCharge > 0f, "SHATTER fired with the unstable rock's kind")
        assertEquals(30f, state.heat, "its blast's HURT carries the kind")
    }

    @Test
    fun breakingAndArmingInACandidateLeaveTheSourceUntouched() {
        val source = crystalState()
        source.placeObstaclesForTesting(crystal(300f, 0f), mine(200f, 150f))
        val field = source.obstacleField
        val destroyed = source.destroyedObstacleIds
        val candidate = source.copyForReduction()
        candidate.flyIntoCrystal(candidate.obstacleDamageStartSpeed())
        assertEquals(listOf(81L), candidate.destroyedObstacleIds)
        assertEquals(candidate.elapsed + kinds.mineFuseSeconds, candidate.mineWithId(71L)!!.fuseAt, "the candidate armed the mine")
        candidate.elapsed += kinds.mineFuseSeconds
        candidate.flyIntoObstaclesForTesting(candidate.coreX, candidate.coreY, 0f, 0f)
        assertEquals(0, candidate.obstacleField.size, "and saw it blow")
        assertSame(field, source.obstacleField, "the source keeps its window")
        assertEquals(listOf(81L, 71L), source.obstacleField.obstacles.map { it.id })
        assertEquals(0f, source.mineWithId(71L)!!.fuseAt, "with the mine dormant")
        assertSame(destroyed, source.destroyedObstacleIds)
        assertTrue(source.destroyedObstacleIds.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Builds around obstacles: GRAZE, the kind every obstacle trigger carries, obstacle tuning.

    private val grazeRadius = 40f

    /** Every GRAZE gains Data; one off an asteroid also charges Overdrive. */
    private val grazeContent = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.GRAZE, immutableListOf(), immutableListOf(EffectAction.GainData(5f))),
        EffectClause(EffectTrigger.GRAZE, immutableListOf(EffectCondition.ObstacleIs(ObstacleKind.ASTEROID)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
    )

    /** A run at [time] with the GRAZE clauses and one obstacle of [kind] (radius 40, shape seed [seed]) at (300, 0). */
    private fun grazeState(kind: ObstacleKind = ObstacleKind.ASTEROID, seed: Int = 7, time: Float = 1f): MutableGameState =
        buildTestState(content = grazeContent).apply {
            own("Calibrated Coil")
            nextLevelData = Int.MAX_VALUE / 2
            elapsed = time
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = grazeRadius, kind = kind, shapeSeed = seed))
        }

    /** One step east at [speed] that ends [gap] above the surface of the obstacle at (300, 0). */
    private fun MutableGameState.skim(gap: Float, speed: Float) =
        flyIntoObstaclesForTesting(300f, grazeRadius + core + gap, speed, 0f)

    private fun MutableGameState.grazeSparks(): List<VisualFxCue.Burst> =
        takeVisualFxCues().filterIsInstance<VisualFxCue.Burst>().filter { it.colorIndex == 2 }

    @Test
    fun aFastSkimPastAnAsteroidGrazesWithItsKindAtMostOncePerCooldown() {
        val state = grazeState()
        state.takeVisualFxCues()
        state.skim(20f, 1_000f)
        val perGraze = state.dataCollected
        assertTrue(perGraze > 0L, "GRAZE fired")
        assertTrue(state.overdriveCharge > 0f, "with the asteroid's kind")
        val sparks = state.grazeSparks()
        assertEquals(1, sparks.size, "one spray of sparks")
        assertEquals(300f, sparks.single().x, 0.001f, "off the surface, toward the Core")
        assertEquals(grazeRadius, sparks.single().y, 0.001f)
        assertEquals(4, sparks.single().requestedCount)
        // Within the cooldown the next skim grazes nothing; once it ends, the next one grazes again.
        state.elapsed = 1f + kinds.grazeCooldownSeconds - 0.01f
        state.skim(20f, 1_000f)
        assertEquals(perGraze, state.dataCollected, "still within the cooldown")
        assertTrue(state.grazeSparks().isEmpty())
        state.elapsed = 1f + kinds.grazeCooldownSeconds
        state.skim(20f, 1_000f)
        assertEquals(2L * perGraze, state.dataCollected, "the cooldown ended")
    }

    @Test
    fun aGrazeNeedsTheGrazeDistanceAndSpeedAndAStepThatTouchesNothing() {
        fun grazes(gap: Float, speed: Float): Boolean = grazeState().run {
            skim(gap, speed)
            dataCollected > 0L
        }
        assertTrue(grazes(kinds.grazeDistance, kinds.grazeMinimumSpeed), "at the graze distance and speed")
        assertFalse(grazes(kinds.grazeDistance + 1f, 1_000f), "just beyond the graze distance")
        assertFalse(grazes(20f, kinds.grazeMinimumSpeed - 1f), "just below the graze speed")
        assertFalse(grazes(20f, 800f))
        // A step that scrapes a second rock above its path at full speed grazes nothing, though it ends close to the first.
        val scraped = grazeState()
        scraped.placeObstaclesForTesting(
            testObstacle(300f, 0f, radius = grazeRadius),
            testObstacle(300f, 2f * (grazeRadius + core) + 20f - 0.5f, radius = grazeRadius, id = 2L),
        )
        scraped.skim(20f, 1_000f)
        assertTrue(scraped.speed >= kinds.grazeMinimumSpeed, "a scrape keeps the graze speed: ${scraped.speed}")
        val firstGap = length(scraped.coreX - 300f, scraped.coreY) - grazeRadius - core
        assertTrue(firstGap in 0f..kinds.grazeDistance, "and ends within the graze distance of the first rock: $firstGap")
        assertEquals(scraped.maxHp, scraped.hp, "a harmless scrape")
        assertEquals(0L, scraped.dataCollected, "but a touch is no graze")
    }

    @Test
    fun onlyAGrazeableSolidSurfaceGrazesAndTheGrazeCarriesItsKind() {
        val seed = 11
        for ((kind, solidPhase, grazes) in listOf(
            Triple(ObstacleKind.ASTEROID, true, true),
            Triple(ObstacleKind.CRYSTAL, true, true),
            Triple(ObstacleKind.GRAVITY_WELL, true, true),
            Triple(ObstacleKind.PHASE_SHARD, true, true),
            Triple(ObstacleKind.PHASE_SHARD, false, false),
            Triple(ObstacleKind.BUMPER, true, false),
            Triple(ObstacleKind.ACCEL_GATE, true, false),
            Triple(ObstacleKind.DERELICT_MINE, true, false),
            Triple(ObstacleKind.THERMAL_VENT, true, false),
        )) {
            val state = grazeState(kind, seed, phaseTime(seed, solidPhase))
            state.skim(20f, 1_000f)
            assertEquals(grazes, state.dataCollected > 0L, "$kind solid phase $solidPhase")
            assertEquals(grazes && kind == ObstacleKind.ASTEROID, state.overdriveCharge > 0f, "$kind: only an asteroid holds ASTEROID")
        }
    }

    @Test
    fun aGrazeLeavesMatrixNearMissAsItWas() {
        // A crystal: Near Miss counts it, and the asteroid's Overdrive clause stays quiet, so all Overdrive is Near Miss.
        val state = grazeState(ObstacleKind.CRYSTAL).apply { matrix = matrix.copy(nearMiss = MatrixEffect.NearMiss(40f, 900f, 6f, 0.5f)) }
        state.skim(20f, 1_000f)
        assertTrue(state.dataCollected > 0L, "GRAZE fired")
        assertEquals(6f, state.overdriveCharge, 0.01f, "Near Miss granted its own Overdrive")
    }

    /** Calibrated Coil gains Overdrive when [named] hurts the Core and vents heat on a ricochet off [named]. */
    private fun hitContent(named: ObstacleKind) = contentWithClauses(
        "Calibrated Coil",
        EffectClause(EffectTrigger.HURT, immutableListOf(EffectCondition.ObstacleIs(named)),
            immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
        EffectClause(EffectTrigger.RICOCHET, immutableListOf(EffectCondition.ObstacleIs(named)),
            immutableListOf(EffectAction.Vent(20f)), cooldownSeconds = 0.5f),
    )

    /** A run at [time] naming [named] in its HURT and RICOCHET clauses, at heat 50 with no shield, with [obstacle] placed. */
    private fun hitState(named: ObstacleKind, obstacle: Obstacle, time: Float = 1f): MutableGameState =
        buildTestState(content = hitContent(named)).apply {
            own("Calibrated Coil")
            heat = 50f
            shield = 0f
            elapsed = time
            placeObstaclesForTesting(obstacle)
        }

    /** One step east at [speed] into the body of [radius] at (300, 0), meeting its surface halfway through the step. */
    private fun MutableGameState.ram(radius: Float, speed: Float) =
        flyIntoObstaclesForTesting(300f - radius - core + speed * 0.5f * MutableGameState.FIXED_STEP, 0f, speed, 0f)

    @Test
    fun aHurtingBounceOffASolidBodyCarriesItsKindToHurtAndRicochet() {
        val seed = 11
        val time = phaseTime(seed, solid = true)
        for ((kind, radius) in listOf(
            ObstacleKind.ASTEROID to 40f, ObstacleKind.GRAVITY_WELL to knotRadius, ObstacleKind.PHASE_SHARD to 40f,
        )) {
            for (named in listOf(kind, ObstacleKind.CRYSTAL)) {
                val state = hitState(named, testObstacle(300f, 0f, radius = radius, kind = kind, shapeSeed = seed), time)
                val speed = state.obstacleRicochetSpeed() + 20f
                assertTrue(speed < state.obstacleLethalSpeed(), "a band hit")
                state.ram(radius, speed)
                assertTrue(state.hp < state.maxHp, "$kind hurts at $speed")
                assertEquals(named == kind, state.overdriveCharge > 0f, "HURT off $kind naming $named")
                assertEquals(named == kind, state.heat < 50f, "RICOCHET off $kind naming $named")
            }
        }
        // A bumper throw is a ricochet off a bumper, and never hurts.
        for (named in listOf(ObstacleKind.BUMPER, ObstacleKind.ASTEROID)) {
            val state = hitState(named, testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
            state.ram(34f, state.obstacleRicochetSpeed() + 20f)
            assertEquals(state.maxHp, state.hp)
            assertEquals(named == ObstacleKind.BUMPER, state.heat < 50f, "RICOCHET off a bumper naming $named")
        }
        // A structure wall's hit names no obstacle kind.
        val wall = hitState(ObstacleKind.ASTEROID, testObstacle(2_000f, 0f))
        wall.setVelocityForTesting(-(wall.obstacleRicochetSpeed() + 20f), 0f)
        wall.resolveSolidImpact(surfaceId = 77L, nx = 1f, ny = 0f, contactX = wall.coreX, contactY = wall.coreY)
        assertTrue(wall.hp < wall.maxHp, "the wall hurts")
        assertEquals(0f, wall.overdriveCharge, "with no kind to name")
        assertEquals(50f, wall.heat)
        // In Baby mode a lethal hit only hurts, and its HURT names the kind.
        val easy = MutableGameState(hitContent(ObstacleKind.ASTEROID), seed = 5, runMode = RunMode.EASY).apply {
            startRun()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            own("Calibrated Coil")
            shield = 0f
            hurtCooldown = 0f
            invulnerableUntil = 0f
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f))
        }
        easy.ram(40f, easy.obstacleLethalSpeed() + 100f)
        assertEquals(GamePhase.RUNNING, easy.phase)
        assertTrue(easy.hp < easy.maxHp)
        assertTrue(easy.overdriveCharge > 0f, "the lethal hit's HURT names the asteroid")
    }

    @Test
    fun anEnemySlammedIntoAnObstacleCarriesItsKindAndOneSlammedIntoAnotherEnemyNone() {
        for (named in listOf(ObstacleKind.GRAVITY_WELL, ObstacleKind.ASTEROID)) {
            val content = contentWithClauses(
                "Calibrated Coil",
                EffectClause(EffectTrigger.SLAM, immutableListOf(EffectCondition.ObstacleIs(named)),
                    immutableListOf(EffectAction.Overdrive(10f)), cooldownSeconds = 0.5f),
            )
            val state = buildTestState(content = content).apply {
                own("Calibrated Coil")
                placeObstaclesForTesting(knot(500f, 0f))
            }
            val thrown = state.dummy(x = 0f, y = 0f, hp = 10_000f).apply {
                x = 500f - knotRadius - radius + 4f
                vx = 700f
                state.markThrown(this)
            }
            state.resolveEnemyObstacles()
            assertTrue(thrown.hp < thrown.maxHp, "the slam into the knot landed")
            assertEquals(named == ObstacleKind.GRAVITY_WELL, state.overdriveCharge > 0f, "SLAM into a knot naming $named")
            // Two enemies slamming into each other strike no obstacle.
            state.overdriveCharge = 0f
            state.elapsed += 1f
            state.clearObstaclesForTesting()
            val hurled = state.dummy(x = 0f, y = 300f, hp = 10_000f)
            val struck = state.dummy(x = hurled.radius * 2f - 4f, y = 300f, hp = 10_000f)
            hurled.vx = 700f
            state.markThrown(hurled)
            state.resolveEnemyObstacles()
            assertTrue(struck.hp < struck.maxHp, "the enemies slammed")
            assertEquals(0f, state.overdriveCharge, "no kind to name")
        }
    }

    /** The canonical content (or [base]) with one probe item per amount, each holding one [aspect] tuning; the run owns them. */
    private fun tunedState(
        aspect: ObstacleAspect,
        vararg amounts: Float,
        base: kinetickk.ball.content.api.GameplayContentSnapshot = canonicalGameplayContent,
    ): MutableGameState {
        val probes = amounts.mapIndexed { index, amount -> obstacleProbe("Test $aspect $index", ItemRule.ObstacleTuning(aspect, amount)) }
        return buildTestState(content = base.withProbes(*probes.toTypedArray())).apply {
            probes.forEach { own(it.name) }
            shield = 0f
        }
    }

    @Test
    fun restitutionTuningAddsToEveryBounceUpToItsCap() {
        fun bounce(vararg amounts: Float, rig: Boolean = false): Float {
            val state = tunedState(ObstacleAspect.RESTITUTION, *amounts).apply {
                if (rig) matrix = matrix.copy(ricochetRig = MatrixEffect.RicochetRig(0.75f, 0f))
                placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f))
            }
            val speed = state.obstacleDamageStartSpeed() - 10f
            state.ram(40f, speed)
            assertEquals(state.maxHp, state.hp, "a bounce below the damage start")
            return -state.velocityX / speed
        }
        val restitution = canonicalGameplayContent.obstacles.restitution
        assertEquals(restitution, bounce(), 1e-4f)
        assertEquals(restitution + 0.15f, bounce(0.15f), 1e-4f, "a bonus adds to the restitution")
        assertEquals(restitution + MAX_OBSTACLE_RESTITUTION_BONUS, bounce(0.3f, 0.3f), 1e-4f, "two stop at the bonus cap")
        assertEquals(0.75f + 0.15f, bounce(0.15f, rig = true), 1e-4f, "on top of Ricochet Rig")
        assertEquals(MAX_OBSTACLE_RESTITUTION, bounce(0.3f, rig = true), 1e-4f, "never past the restitution cap")
    }

    @Test
    fun theObstacleDamageCutScalesBandHitsAndBlastsAtMostByHalf() {
        fun bandLoss(vararg cuts: Float): Float = tunedState(ObstacleAspect.COLLISION_DAMAGE, *cuts).run {
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f))
            val start = obstacleDamageStartSpeed()
            ram(40f, start + (obstacleLethalSpeed() - start) * 0.5f)
            maxHp - hp
        }
        fun mineLoss(vararg cuts: Float): Float = tunedState(ObstacleAspect.COLLISION_DAMAGE, *cuts).run {
            elapsed = 1f
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = 50f, kind = ObstacleKind.DERELICT_MINE, fuseAt = 1f))
            flyIntoObstaclesForTesting(250f, 0f, 0f, 0f)
            maxHp - hp
        }
        fun unstableLoss(vararg cuts: Float): Float = tunedState(ObstacleAspect.COLLISION_DAMAGE, *cuts).run {
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f, kind = ObstacleKind.UNSTABLE))
            ram(40f, obstacleDamageStartSpeed())
            maxHp - hp
        }
        val band = bandLoss()
        val mine = mineLoss()
        val unstable = unstableLoss()
        assertTrue(band > 0f && mine > 0f && unstable > 0f)
        assertEquals(band * 0.65f, bandLoss(0.35f), band * 1e-4f, "a band hit")
        assertEquals(mine * 0.65f, mineLoss(0.35f), mine * 1e-4f, "a mine blast")
        assertEquals(unstable * 0.65f, unstableLoss(0.35f), unstable * 1e-4f, "an unstable asteroid's blast")
        assertEquals(band * (1f - MAX_OBSTACLE_DAMAGE_CUT), bandLoss(0.5f, 0.5f), band * 1e-4f, "two cuts stop at half")
        assertEquals(mine * (1f - MAX_OBSTACLE_DAMAGE_CUT), mineLoss(0.5f, 0.5f), mine * 1e-4f)
    }

    @Test
    fun theItemLethalBonusRaisesTheLethalSpeedOnlyUpToTheBuildsOwnCeiling() {
        val plain = buildTestState()
        val caged = tunedState(ObstacleAspect.LETHAL_SPEED, 150f)
        assertEquals(150f, caged.obstacleLethalSpeedBonus())
        assertEquals(plain.obstacleLethalSpeed() + 150f, caged.obstacleLethalSpeed(), 0.01f, "under the ceiling the bonus adds")
        assertEquals(MAX_ITEM_LETHAL_BONUS, tunedState(ObstacleAspect.LETHAL_SPEED, 300f, 300f).obstacleLethalSpeedBonus(),
            "two stop at the item cap")
        // Two of them with Impact Dampers II and Gravity Surfer's 700: the sum stops at the build's own ceiling.
        val stacked = tunedState(ObstacleAspect.LETHAL_SPEED, 300f, 300f).apply {
            matrix = matrix.copy(obstacleLethalSpeedBonus = 700f)
        }
        val ceiling = stacked.buildSteadyCursorSpeed(stacked.content.obstacles.lethalCeilingReach)
        assertTrue(ceiling < plain.obstacleLethalSpeed() + MAX_ITEM_LETHAL_BONUS + 700f)
        assertEquals(ceiling, stacked.obstacleLethalSpeed(), 0.01f, "the ceiling holds")
        // A hit just past the plain lethal speed is only a band hit for the caged build.
        caged.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 40f))
        caged.ram(40f, plain.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.RUNNING, caged.phase)
        assertTrue(caged.hp < caged.maxHp)
    }

    @Test
    fun boostPowerScalesEveryObstacleBoostUpToDoubleAndTheCapStillHolds() {
        fun boosted(vararg powers: Float): MutableGameState =
            tunedState(ObstacleAspect.BOOST_POWER, *powers, base = boostContent).apply {
                own("Calibrated Coil")
                heat = 50f
                placeObstaclesForTesting(testGate(300f, 0f, 0f, gateRadius))
            }
        val clean = boosted(0.3f)
        val cap = clean.obstacleLethalSpeed() * kinds.boostLethalShare
        assertTrue(600f + kinds.gateImpulse * 1.3f < cap, "the test crossing stays under the boost cap: $cap")
        clean.stepAcrossGateLine(0f, 600f)
        assertEquals(600f + kinds.gateImpulse * 1.3f, clean.speed, 0.01f, "a gate gives +494 at +30 %")
        assertTrue(clean.heat < 50f, "BOOST fired with the gate's kind")
        val doubled = boosted(0.6f, 0.6f)
        doubled.stepAcrossGateLine(0f, 200f)
        assertEquals(200f + kinds.gateImpulse * (1f + MAX_BOOST_POWER_BONUS), doubled.speed, 0.01f, "two stop at double")
        var speed = 0f
        while (speed <= cap * 1.2f) {
            doubled.setVelocityForTesting(speed, 0f)
            doubled.applyObstacleBoost(kinds.gateImpulse, ObstacleKind.ACCEL_GATE)
            assertTrue(doubled.speed <= max(speed, cap) + 0.01f, "a doubled boost at $speed left at ${doubled.speed} (cap $cap)")
            speed += 53f
        }
    }

    @Test
    fun knotPullTuningDragsEnemiesHarderAndLeavesTheCoreAlone() {
        val step = MutableGameState.FIXED_STEP
        val tuned = tunedState(ObstacleAspect.WELL_PULL, 0.6f, 0.6f).apply { placeObstaclesForTesting(knot(600f, 0f)) }
        val drifter = tuned.dummy(x = 600f, y = 150f)
        tuned.flyIntoObstaclesForTesting(450f, 0f, 0f, 0f)
        assertEquals(expectedPull(kinds.wellCorePull, 150f, core) * step, tuned.velocityX, 0.0005f, "the Core feels the plain pull")
        val doubled = expectedPull(kinds.wellEnemyPull, 150f, drifter.radius) * tuned.knockbackTaken(drifter) *
            (1f + MAX_WELL_PULL_BONUS) * step
        assertEquals(-doubled, drifter.vy, 0.001f, "an enemy feels it doubled")
    }
}
