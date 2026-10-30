// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.Polarity
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObstacleSystemTest {
    private companion object {
        /** A test mix that puts every newer kind on asteroid candidates. */
        val EVERY_KIND_MIX = ObstacleMix(
            gravityWell = 0.15f, accelGate = 0.15f, crystal = 0.15f, derelictMine = 0.15f, phaseShard = 0.15f, thermalVent = 0.15f,
        )
    }

    // The Core touches the test asteroid (centre 300, radius 80) when its centre reaches x = 204.
    private val asteroidX = 300f
    private val contactX = asteroidX - 80f - MutableGameState.CORE_RADIUS

    private fun MutableGameState.ramAsteroid(speed: Float, kind: ObstacleKind = ObstacleKind.ASTEROID) {
        placeObstaclesForTesting(testObstacle(asteroidX, 0f, kind = kind))
        flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
    }

    private fun MutableGameState.bandSpeed(fraction: Float): Float {
        val start = obstacleDamageStartSpeed()
        return start + (obstacleLethalSpeed() - start) * fraction
    }

    // -----------------------------------------------------------------------------------------
    // Generation.

    @Test
    fun theWindowIsDeterministicPerSeedBoundedGappedAndClearOfTheOrigin() {
        fun window(seed: Int) = buildTestState(seed = seed).apply {
            coreX = 7_300f
            coreY = -4_100f
            refreshObstacleWindow()
        }
        val first = window(41)
        val again = window(41)
        val other = window(42)
        assertEquals(first.obstacleField.obstacles, again.obstacleField.obstacles)
        assertNotEquals(first.obstacleField.obstacles, other.obstacleField.obstacles)
        assertTrue(first.obstacleField.size in 1..ContentBounds.MAX_OBSTACLES)

        val tuning = canonicalGameplayContent.obstacles
        val obstacles = first.obstacleField.obstacles
        obstacles.forEachIndexed { index, obstacle ->
            assertTrue(obstacle.radius in tuning.bumperRadius..tuning.asteroidMaximumRadius)
            for (other in obstacles.indices) if (other != index) {
                val gap = length(obstacle.x - obstacles[other].x, obstacle.y - obstacles[other].y) -
                    obstacle.radius - obstacles[other].radius
                assertTrue(gap >= tuning.minimumGap - 0.01f, "obstacles keep the minimum gap: $gap")
            }
        }
        assertEquals(obstacles.size, obstacles.map { it.id }.toSet().size, "ids are unique")
        // No chunk holds more than its slots.
        val perChunk = obstacles.groupBy { it.id shr 4 }
        assertTrue(perChunk.values.all { it.size <= tuning.maximumPerChunk })

        val origin = buildTestState(seed = 41)
        origin.obstacleField.obstacles.forEach { obstacle ->
            assertTrue(length(obstacle.x, obstacle.y) - obstacle.radius >= tuning.safeRadius, "the run starts clear")
        }
    }

    @Test
    fun theWindowRegeneratesOnlyWhenTheCoreChangesChunk() {
        val state = buildTestState(seed = 77)
        val before = state.obstacleField
        state.coreX = 500f
        state.refreshObstacleWindow()
        assertSame(before, state.obstacleField, "moving inside the chunk keeps the window")
        state.coreX = 1_300f
        state.refreshObstacleWindow()
        assertNotEquals(before.centerChunkX, state.obstacleField.centerChunkX)
        // Chunks that stay in view keep their exact obstacles.
        val shared = before.chunkObstacles(1, 0)!!
        assertEquals(shared.toList(), state.obstacleField.chunkObstacles(1, 0)!!.toList())
    }

    @Test
    fun rebasesKeepAbsoluteObstaclePositionsAndIds() {
        val state = buildTestState(seed = 91)
        state.coreX = 249_990f
        state.coreY = 3_000f
        state.refreshObstacleWindow()
        val absolute = state.obstacleField.obstacles.map { Triple(it.id, it.x + state.worldOriginX, it.y + state.worldOriginY) }
        state.coreX = 250_010f
        state.refreshObstacleWindow()
        val stillInWindow = state.obstacleField.obstacles.associate { it.id to (it.x + state.worldOriginX to it.y + state.worldOriginY) }
        state.rebaseWorldIfNeeded()
        assertEquals(250_010.0, state.worldOriginX, 0.01)
        assertEquals(0f, state.coreX)
        state.obstacleField.obstacles.forEach { obstacle ->
            val (x, y) = stillInWindow.getValue(obstacle.id)
            assertEquals(x, obstacle.x + state.worldOriginX, 0.05)
            assertEquals(y, obstacle.y + state.worldOriginY, 0.05)
        }
        // Regenerating after the rebase reproduces the same rocks for the chunks already seen.
        val fresh = buildTestState(seed = 91).apply {
            worldOriginX = state.worldOriginX
            worldOriginY = state.worldOriginY
            coreY = state.coreY
            refreshObstacleWindow()
        }
        assertEquals(
            state.obstacleField.obstacles.map { it.id }.sorted(),
            fresh.obstacleField.obstacles.map { it.id }.sorted(),
        )
        assertTrue(absolute.isNotEmpty())
    }

    @Test
    fun fracturedSpaceAddsDensityAndUnstableAsteroidsWithFewerBumpers() {
        fun census(tier: Int): Map<ObstacleKind, Int> {
            // Far from the origin: a world without structures, whose footprints would clear part of the census.
            val state = buildTestState(seed = 5, rebirth = tier, content = structuresDisabledContent)
            val counts = mutableMapOf<ObstacleKind, Int>()
            for (chunkX in 10 until 40) for (chunkY in -15 until 15) {
                state.generateChunk(chunkX, chunkY).forEach { counts[it.kind] = (counts[it.kind] ?: 0) + 1 }
            }
            return counts
        }
        val calm = census(4)
        val fractured = census(5)
        assertEquals(null, calm[ObstacleKind.UNSTABLE], "unstable asteroids need Fractured Space")
        assertTrue((fractured[ObstacleKind.UNSTABLE] ?: 0) > 0)
        val calmTotal = calm.values.sum()
        val fracturedTotal = fractured.values.sum()
        assertTrue(fracturedTotal > calmTotal * 1.2f, "density ×1.4: $fracturedTotal vs $calmTotal")
        val calmBumpers = (calm[ObstacleKind.BUMPER] ?: 0).toFloat() / calmTotal
        val fracturedBumpers = (fractured[ObstacleKind.BUMPER] ?: 0).toFloat() / fracturedTotal
        assertEquals(0.18f, calmBumpers, 0.04f)
        assertEquals(0.08f, fracturedBumpers, 0.03f)
        val asteroids = fracturedTotal - (fractured[ObstacleKind.BUMPER] ?: 0)
        assertEquals(0.15f, (fractured[ObstacleKind.UNSTABLE] ?: 0).toFloat() / asteroids, 0.04f)
        // Candidate mean scales by the tier-4 density (1.2), before spacing removes overlaps.
        assertEquals(canonicalGameplayContent.obstacles.meanPerChunk * 1.2f, calmTotal / 900f, 0.25f)
    }

    @Test
    fun variantKindsLeaveTheFieldLayoutUnchanged() {
        val tuning = canonicalGameplayContent.obstacles
        val mixed = contentWithObstacleMix(EVERY_KIND_MIX)
        val zero = withoutObstacleVariants()
        fun compare(rebirth: Int, chunkXs: IntRange, chunkYs: IntRange, content: GameplayContentSnapshot = mixed): List<Obstacle> {
            val plain = buildTestState(seed = 23, rebirth = rebirth, content = zero)
            val varied = buildTestState(seed = 23, rebirth = rebirth, content = content)
            val converted = mutableListOf<Obstacle>()
            for (chunkX in chunkXs) for (chunkY in chunkYs) {
                val before = plain.generateChunk(chunkX, chunkY)
                val after = varied.generateChunk(chunkX, chunkY)
                assertEquals(before.map { it.id }, after.map { it.id }, "the same candidates survive in chunk $chunkX, $chunkY")
                before.zip(after).forEach { (candidate, obstacle) ->
                    assertEquals(candidate.x, obstacle.x)
                    assertEquals(candidate.y, obstacle.y)
                    assertEquals(candidate.shapeSeed, obstacle.shapeSeed)
                    if (candidate.kind != ObstacleKind.ASTEROID) assertEquals(candidate, obstacle, "only asteroid candidates convert")
                    assertTrue(obstacle.radius <= candidate.radius, "a body never outgrows the asteroid it replaced")
                    assertTrue(obstacle.radius in tuning.bumperRadius..tuning.asteroidMaximumRadius)
                    if (obstacle.repels) assertEquals(ObstacleKind.GRAVITY_WELL, obstacle.kind, "only knots repel")
                    converted += obstacle
                }
            }
            return converted
        }
        val realspace = compare(rebirth = 0, chunkXs = 10 until 30, chunkYs = -10 until 10)
        assertEquals(ObstacleKind.entries.toSet() - ObstacleKind.UNSTABLE, realspace.map { it.kind }.toSet(),
            "every newer kind appears on asteroid candidates, beside asteroids and bumpers")
        val knots = realspace.filter { it.kind == ObstacleKind.GRAVITY_WELL }
        assertTrue(knots.any { it.repels } && knots.any { !it.repels }, "both knots appear")
        // Fractured Space keeps its unstable asteroids as the layout made them.
        val fractured = compare(rebirth = 5, chunkXs = 10 until 20, chunkYs = -5 until 5)
        assertTrue(fractured.any { it.kind == ObstacleKind.UNSTABLE })
        // The shipped realspace mix moves nothing either, over 400 chunks, and brings every newer kind and both knots.
        val shipped = compare(rebirth = 0, chunkXs = 10 until 30, chunkYs = -10 until 10, content = canonicalGameplayContent)
        assertEquals(ObstacleKind.entries.toSet() - ObstacleKind.UNSTABLE, shipped.map { it.kind }.toSet())
        val shippedKnots = shipped.filter { it.kind == ObstacleKind.GRAVITY_WELL }
        assertTrue(shippedKnots.any { it.repels } && shippedKnots.any { !it.repels }, "both knots ship")
    }

    /**
     * Every obstacle [layer] generates at tier 0 with [content] in the 900 chunks east of the origin, for each of three
     * runs (more than 1,800 obstacles, enough to compare kind shares across the sparser field). Far from the origin:
     * a world without structures, whose realspace footprints would clear part of the census.
     */
    private fun census(layer: WorldLayer, content: GameplayContentSnapshot = structuresDisabledContent): List<Obstacle> = buildList {
        for (seed in 31..33) {
            val state = buildTestState(seed = seed, content = content).apply { worldLayer = layer }
            for (chunkX in 10 until 40) for (chunkY in -15 until 15) addAll(state.generateChunk(chunkX, chunkY))
        }
    }

    /**
     * The share of all candidates the mix tables give [kind] in a layer: its roll share times the chance a uniform
     * candidate radius (46..150) is eligible (spec: P = 0.769 for knots and gates, 0.615 for crystals, 0.865 for vents,
     * every radius for mines and shards) times the layer's non-bumper share (bumpers 0.18 × the layer's scale).
     */
    private fun tableShare(mix: ObstacleMix, kind: ObstacleKind): Float {
        val eligible = when (kind) {
            ObstacleKind.GRAVITY_WELL, ObstacleKind.ACCEL_GATE -> 80f / 104f
            ObstacleKind.CRYSTAL -> 64f / 104f
            ObstacleKind.THERMAL_VENT -> 90f / 104f
            else -> 1f
        }
        return mix.share(kind) * eligible * (1f - 0.18f * mix.bumperScale)
    }

    @Test
    fun spaceMixCensusMatchesItsShares() {
        val kinds = canonicalGameplayContent.obstacles.kinds
        val field = census(WorldLayer.BASE)
        val counts = field.groupingBy { it.kind }.eachCount()
        val total = field.size.toFloat()
        assertTrue(total > 1_800f, "2,700 chunks still supply a representative obstacle sample: $total")
        val newKinds = ObstacleKind.entries.filter { kinds.space.share(it) > 0f }
        assertEquals(6, newKinds.size, "realspace ships every newer kind")
        newKinds.forEach { kind ->
            assertEquals(tableShare(kinds.space, kind), (counts[kind] ?: 0) / total, 0.02f, "$kind's share of all candidates")
        }
        val converted = newKinds.sumOf { counts[it] ?: 0 } / total
        assertTrue(converted in 0.25f..0.35f, "new kinds take 25-35 % of realspace candidates: $converted")
        assertEquals(0.295f, converted, 0.02f)
        assertEquals(0.18f, (counts[ObstacleKind.BUMPER] ?: 0) / total, 0.04f, "bumpers keep their share")
        assertEquals(0.525f, (counts[ObstacleKind.ASTEROID] ?: 0) / total, 0.02f, "asteroids keep the rest")
        assertEquals(null, counts[ObstacleKind.UNSTABLE], "unstable asteroids stay Fractured Space's")
        val knots = field.filter { it.kind == ObstacleKind.GRAVITY_WELL }
        assertEquals(kinds.space.repelShare, knots.count { it.repels }.toFloat() / knots.size, 0.15f, "Repulsors among knots")
    }

    @Test
    fun layerMixesAreDistinct() {
        val kinds = canonicalGameplayContent.obstacles.kinds
        val space = census(WorldLayer.BASE)
        // Every layer meets its table: each kind within 2 pp, the new kinds' total and the bumpers within 2 pp.
        val layers = listOf(
            Triple(WorldLayer.ZENITH, kinds.zenith, 0.345f to 0.234f),
            Triple(WorldLayer.NADIR, kinds.nadir, 0.464f to 0.108f),
            Triple(WorldLayer.ARCHIVE, kinds.archive, 0.447f to 0.144f),
        )
        val fields = layers.associate { (layer, mix, table) ->
            val field = census(layer)
            val counts = field.groupingBy { it.kind }.eachCount()
            val total = field.size.toFloat()
            val newKinds = ObstacleKind.entries.filter { mix.share(it) > 0f }
            newKinds.forEach { kind ->
                assertEquals(tableShare(mix, kind), (counts[kind] ?: 0) / total, 0.02f, "$layer: $kind's share of all candidates")
            }
            assertEquals(table.first, newKinds.sumOf { counts[it] ?: 0 } / total, 0.02f, "$layer: new kinds")
            assertEquals(table.second, (counts[ObstacleKind.BUMPER] ?: 0) / total, 0.02f, "$layer: bumpers")
            assertTrue(field.all { it.id ushr 60 != 0L }, "$layer tags its ids")
            layer to counts
        }
        val zenith = fields.getValue(WorldLayer.ZENITH)
        val nadir = fields.getValue(WorldLayer.NADIR)
        val archive = fields.getValue(WorldLayer.ARCHIVE)
        assertEquals(null, zenith[ObstacleKind.DERELICT_MINE], "the Zenith has no mines")
        assertEquals(null, zenith[ObstacleKind.THERMAL_VENT], "the Zenith has no vents")
        val spaceCounts = space.groupingBy { it.kind }.eachCount()
        val all = listOf(spaceCounts, zenith, nadir, archive)
        assertEquals(nadir, all.maxBy { (it[ObstacleKind.THERMAL_VENT] ?: 0).toFloat() / it.values.sum() }, "the Nadir has the most vents")
        assertEquals(nadir, all.maxBy { (it[ObstacleKind.DERELICT_MINE] ?: 0).toFloat() / it.values.sum() }, "the Nadir has the most mines")
        assertEquals(archive, all.maxBy { (it[ObstacleKind.PHASE_SHARD] ?: 0).toFloat() / it.values.sum() }, "the Archive has the most shards")
        // Density follows each layer's scale: the Zenith a little sparser, the Nadir denser than realspace.
        assertTrue(zenith.values.sum() < space.size && space.size < nadir.values.sum(),
            "zenith ${zenith.values.sum()} < space ${space.size} < nadir ${nadir.values.sum()}")
        // Zenith knots mostly lift (Repulsors), Nadir knots mostly pull.
        fun repelShare(layer: WorldLayer) = census(layer).filter { it.kind == ObstacleKind.GRAVITY_WELL }
            .let { knots -> knots.count { it.repels }.toFloat() / knots.size }
        assertTrue(repelShare(WorldLayer.ZENITH) > 0.5f && repelShare(WorldLayer.NADIR) < 0.5f)
    }

    // -----------------------------------------------------------------------------------------
    // Queries.

    @Test
    fun spawnPointsAndSegmentsSeeObstacles() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(1_000f, 0f, radius = 100f))
        assertTrue(state.isSpawnPointBlocked(1_000f, 120f, 20f))
        assertFalse(state.isSpawnPointBlocked(1_000f, 300f, 20f))
        assertTrue(state.obstacleBlocksSegment(700f, 0f, 1_300f, 0f, 7f))
        assertTrue(state.obstacleBlocksSegment(700f, 105f, 1_300f, 105f, 7f), "the swept radius counts")
        assertFalse(state.obstacleBlocksSegment(700f, 200f, 1_300f, 200f, 7f))
        assertFalse(state.obstacleBlocksSegment(0f, 0f, 600f, 0f, 7f))
    }

    @Test
    fun totemsAndAnomaliesMoveOutFromUnderObstacles() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(600f, 0f, radius = 140f))
        val (totemX, totemY) = state.nudgedClearOfObstacles(600f, 0f, 70f)
        assertFalse(state.isSpawnPointBlocked(totemX, totemY, 70f))
        assertEquals(0f, totemY, 0.01f)
        val (pointX, pointY) = state.clearPointOfInterestSite(600f, 0f)
        assertFalse(state.pointSiteBlocked(pointX, pointY))
        assertTrue(pointX > 600f)
    }

    @Test
    fun nonSolidKindsNeverCollideOrBlockSegments() {
        for (kind in listOf(ObstacleKind.ACCEL_GATE, ObstacleKind.DERELICT_MINE, ObstacleKind.THERMAL_VENT)) {
            val state = buildTestState()
            val body = testObstacle(asteroidX, 0f, radius = 60f, kind = kind)
            state.placeObstaclesForTesting(body)
            // The step runs the Core straight through the body's centre above the lethal speed.
            val speed = state.obstacleLethalSpeed() + 200f
            val endX = asteroidX + speed / 240f
            state.flyIntoObstaclesForTesting(endX, 0f, speed, 0f)
            assertEquals(GamePhase.RUNNING, state.phase, "$kind")
            assertEquals(state.maxHp, state.hp, "$kind")
            assertEquals(0, state.pendingRicochets, "$kind")
            assertEquals(speed, state.velocityX, "$kind never bounces the Core")
            assertEquals(endX, state.coreX, "$kind never pushes the Core out")
            assertFalse(state.obstacleBlocksSegment(0f, 0f, 600f, 0f, 7f), "$kind")
            // Shots fly on and enemies are neither pushed out nor slowed.
            val shot = Projectile(x = asteroidX + 30f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, hostile = false,
                previousX = asteroidX - 90f, previousY = 0f)
            state.projectiles += shot
            state.blockProjectilesAtObstacles()
            assertFalse(shot.spent, "$kind lets shots through")
            val enemy = state.dummy(x = asteroidX - 20f, y = 0f, hp = 10_000f)
            enemy.vx = 700f
            state.markThrown(enemy)
            state.resolveEnemyObstacles()
            assertEquals(asteroidX - 20f, enemy.x, "$kind")
            assertEquals(700f, enemy.vx, "$kind")
            assertEquals(enemy.maxHp, enemy.hp, "$kind")
            // Spawns keep clear of its hazard reach (a gate's body for a gate).
            val keepOut = max(body.radius, state.obstacleHazardReach(body)) + 20f + SPAWN_OBSTACLE_CLEARANCE
            assertTrue(state.isSpawnPointBlocked(asteroidX, keepOut - 1f, 20f), "$kind")
            assertFalse(state.isSpawnPointBlocked(asteroidX, keepOut + 1f, 20f), "$kind")
        }
        val mine = testObstacle(0f, 0f, radius = 50f, kind = ObstacleKind.DERELICT_MINE)
        val vent = testObstacle(0f, 0f, radius = 50f, kind = ObstacleKind.THERMAL_VENT)
        val kinds = canonicalGameplayContent.obstacles.kinds
        val state = buildTestState()
        assertEquals(50f + kinds.mineArmMargin, state.obstacleHazardReach(mine))
        assertEquals(50f + kinds.ventPlumeMargin, state.obstacleHazardReach(vent))
        assertEquals(0f, state.obstacleHazardReach(testObstacle(0f, 0f, kind = ObstacleKind.ACCEL_GATE)))
    }

    @Test
    fun bumpersStillLetShotsThroughAndNeverGraze() {
        // Every GRAZE would gain Data.
        val grazing = contentWithClauses(
            "Calibrated Coil", EffectClause(EffectTrigger.GRAZE, immutableListOf(), immutableListOf(EffectAction.GainData(5f))),
        )
        val state = buildTestState(content = grazing).apply {
            own("Calibrated Coil")
            nextLevelData = Int.MAX_VALUE / 2
            matrix = matrix.copy(nearMiss = MatrixEffect.NearMiss(40f, 900f, 6f, 0.5f))
        }
        state.placeObstaclesForTesting(testObstacle(asteroidX, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val shot = Projectile(x = asteroidX + 30f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, hostile = false,
            previousX = asteroidX - 60f, previousY = 0f)
        state.projectiles += shot
        state.blockProjectilesAtObstacles()
        assertFalse(shot.spent)
        // Skimming 20 above its surface at 1 000 u/s earns no Near Miss and no GRAZE, though the bumper is a solid surface
        // nearby.
        state.flyIntoObstaclesForTesting(asteroidX, 34f + MutableGameState.CORE_RADIUS + 20f, 1_000f, 0f)
        assertEquals(0f, state.overdriveCharge)
        assertEquals(0L, state.dataCollected, "no GRAZE")
        assertEquals(20f, state.coreObstacleGap, 0.01f)
    }

    // -----------------------------------------------------------------------------------------
    // The Core.

    @Test
    fun aSlowCoreSlidesOrBouncesWithoutDamageOrRicochet() {
        val state = buildTestState()
        val speed = state.obstacleDamageStartSpeed() - 10f
        state.ramAsteroid(speed)
        assertEquals(state.maxHp, state.hp)
        assertEquals(0, state.pendingRicochets)
        assertTrue(state.velocityX < 0f, "it bounces back")
        assertEquals(speed * 0.6f, -state.velocityX, 0.5f)
        assertTrue(state.coreX <= contactX + 0.5f, "it never ends inside the rock")

        val crawl = buildTestState()
        crawl.ramAsteroid(60f)
        assertEquals(0f, crawl.velocityX, 0.01f, "below the slide speed the normal motion simply stops")
    }

    @Test
    fun theDamageBandScalesWithSpeedIgnoresReductionAndBleedsThroughShields() {
        val state = buildTestState()
        val start = state.obstacleDamageStartSpeed()
        val lethal = state.obstacleLethalSpeed()
        val speed = state.bandSpeed(0.5f)
        state.damageReduction = 0.5f
        state.ramAsteroid(speed)
        // Tier 0: forgiving ×0.7, collision tier factor 1 + 0.04 × 0; the band runs linearly from the 6 % floor to 55 %.
        val expected = state.maxHp * (0.06f + 0.49f * (speed - start) / (lethal - start)) * 0.7f
        assertEquals(state.maxHp - expected, state.hp, 0.05f)
        assertEquals(1, state.pendingRicochets)
        assertTrue(state.velocityX < 0f)
        assertEquals(speed * 0.6f, -state.velocityX, 1f)
        assertFalse(IncomingDamage.OBSTACLE.cancellableByFacets)

        val shielded = buildTestState()
        shielded.maxShield = 200f
        shielded.shield = 200f
        shielded.ramAsteroid(speed)
        assertEquals(shielded.maxHp - expected * 0.4f, shielded.hp, 0.05f, "40 % bleeds through the shield")
        assertEquals(200f - expected * 0.6f, shielded.shield, 0.05f, "the shield takes the rest")
    }

    @Test
    fun collisionDamageUsesTheTierFactorInsteadOfTheRebirthMultiplier() {
        val tier = 6
        val state = buildTestState(rebirth = tier)
        assertTrue(state.rebirthProfile.incomingDamageMultiplier > 1.2f)
        val speed = state.bandSpeed(0.5f)
        state.ramAsteroid(speed)
        val start = state.obstacleDamageStartSpeed()
        val lethal = state.obstacleLethalSpeed()
        val expected = state.maxHp * (0.06f + 0.49f * (speed - start) / (lethal - start)) * (1f + 0.04f * tier)
        assertEquals(state.maxHp - expected, state.hp, 0.05f)
    }

    @Test
    fun tierZeroForgivesTheStartSpeedAndDamageUntilTierThree() {
        val zero = buildTestState(rebirth = 0)
        val three = buildTestState(rebirth = 3)
        val five = buildTestState(rebirth = 5)
        assertEquals(
            three.obstacleDamageStartSpeed() + zero.content.obstacles.forgivingStartBonus,
            zero.obstacleDamageStartSpeed(),
            0.01f,
        )
        assertEquals(three.obstacleDamageStartSpeed(), five.obstacleDamageStartSpeed(), 0.01f)
        assertEquals(three.obstacleLethalSpeed(), zero.obstacleLethalSpeed(), 0.01f)
        val band = 0.6f
        val zeroDamage = zero.obstacleBandDamage(10f, 0f, 1f / band * 10f)
        val threeDamage = three.obstacleBandDamage(10f, 0f, 1f / band * 10f)
        assertEquals(threeDamage / three.maxHp * 0.7f, zeroDamage / zero.maxHp, 0.0001f)
    }

    @Test
    fun aLethalCollisionEndsTheRunPastShieldsAndDashPhase() {
        val state = buildTestState()
        state.maxShield = 500f
        state.shield = 500f
        state.dashPhaseTime = 0.2f
        state.ramAsteroid(state.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
        assertTrue(state.hp <= 0f)
    }

    @Test
    fun prismFacetsOrBarriersNeverSaveALethalCollision() {
        val prism = buildTestState(shape = CoreShape.PRISM)
        assertEquals(4, prism.intactFacets, "every facet is up")
        prism.ramAsteroid(prism.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.GAME_OVER, prism.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, prism.message)
    }

    @Test
    fun prismFacetsNeverCancelABandCollision() {
        val prism = buildTestState(shape = CoreShape.PRISM)
        prism.ramAsteroid(prism.bandSpeed(0.4f))
        assertTrue(prism.hp < prism.maxHp, "the collision hurt")
        assertEquals(4, prism.intactFacets, "no facet was spent on it")
        assertEquals(GamePhase.RUNNING, prism.phase)
    }

    @Test
    fun secondWindAndOuroborosAnswerALethalCollision() {
        val windy = buildTestState()
        windy.matrix = windy.matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        windy.ramAsteroid(windy.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.RUNNING, windy.phase)
        assertEquals(windy.maxHp * 0.3f, windy.hp, 0.01f)
        assertTrue(windy.secondWindSpent)

        val sealed = buildTestState(RelicId.OUROBOROS_SEAL)
        sealed.ramAsteroid(sealed.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.RUNNING, sealed.phase)
        assertTrue(sealed.ouroborosSpent)
        assertTrue(sealed.hp > 0f)
    }

    @Test
    fun onlyAReviveWindowHoldsOffALethalCollision() {
        // Glass Comet's window after an impact kill and Bulwark III's after a shield break are no revive.
        for (window in listOf(GLASS_INVULNERABILITY_SECONDS, 0.8f)) {
            val state = buildTestState(RelicId.GLASS_COMET)
            state.invulnerableUntil = state.elapsed + window
            state.ramAsteroid(state.obstacleLethalSpeed() + 50f)
            assertEquals(GamePhase.GAME_OVER, state.phase, "a $window s window")
            assertEquals(OBSTACLE_COLLISION_REASON, state.message)
        }

        val windy = buildTestState()
        windy.matrix = windy.matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        windy.ramAsteroid(windy.obstacleLethalSpeed() + 50f)
        assertTrue(windy.secondWindSpent)
        windy.elapsed += 1f
        windy.flyIntoObstaclesForTesting(contactX + 10f, 0f, windy.obstacleLethalSpeed() + 50f, 0f)
        assertEquals(GamePhase.RUNNING, windy.phase, "the revive's own window still protects")
        windy.elapsed += 1.5f
        windy.flyIntoObstaclesForTesting(contactX + 10f, 0f, windy.obstacleLethalSpeed() + 50f, 0f)
        assertEquals(GamePhase.GAME_OVER, windy.phase, "once it ends, the next lethal hit kills")
    }

    @Test
    fun aLethalHitRightAfterABandHitStillKills() {
        val state = buildTestState()
        val band = state.bandSpeed(0.4f)
        state.ramAsteroid(band)
        assertTrue(state.hp < state.maxHp)
        state.elapsed += 0.1f
        val lethal = state.obstacleLethalSpeed() + 50f
        state.flyIntoObstaclesForTesting(contactX + lethal / 240f, 0f, lethal, 0f)
        assertEquals(GamePhase.GAME_OVER, state.phase, "the band hit's cooldown does not shield a lethal one")
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
    }

    @Test
    fun collisionsHurtAtMostOncePerCooldown() {
        val state = buildTestState()
        val speed = state.obstacleRicochetSpeed() + 20f
        state.ramAsteroid(speed)
        val afterFirst = state.hp
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertEquals(afterFirst, state.hp, "inside the cooldown")
        state.elapsed += 0.3f
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertTrue(state.hp < afterFirst)
        assertEquals(2, state.pendingRicochets)
    }

    @Test
    fun aRicochetFiresRicochetClausesInAScriptedCollision() {
        val content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.RICOCHET, actions = immutableListOf(EffectAction.Overdrive(20f)), cooldownSeconds = 0.5f))
        val state = buildTestState(content = content)
        state.own("Calibrated Coil")
        val charge = state.overdriveCharge
        state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
        assertTrue(state.overdriveCharge >= charge + 20f, "the RICOCHET clause fired")
    }

    @Test
    fun ricochetsReachTheRunsProgressUpdate() {
        val state = buildTestState()
        state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val fast = state.obstacleRicochetSpeed() + 20f
        state.flyIntoObstaclesForTesting(300f - 34f - MutableGameState.CORE_RADIUS + fast / 240f, 0f, fast, 0f)
        assertEquals(2, state.pendingRicochets)
        state.exitRun()
        assertEquals(2, assertNotNull(state.takeProgressUpdate()).ricochets, "the profile counts them")
        assertEquals(0, state.pendingRicochets)
    }

    @Test
    fun aRealRicochetFiresAShippedRicochetItem() {
        // Heat Shunt vents 20 heat on RICOCHET (0.5 s cooldown).
        val state = buildTestState()
        state.own("Heat Shunt")
        state.heat = 50f
        state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
        assertEquals(1, state.pendingRicochets)
        assertEquals(30f, state.heat, 0.01f, "the shipped RICOCHET clause vented")
        // A slow bounce is no ricochet and vents nothing.
        state.elapsed += 1f
        state.heat = 50f
        state.ramAsteroid(state.obstacleDamageStartSpeed() * 0.5f)
        assertEquals(1, state.pendingRicochets)
        assertEquals(50f, state.heat, 0.01f)
    }

    @Test
    fun aRealSlamFiresAShippedSlamItem() {
        // Concussive Rivets apply 2 Sonic to the slammed enemy (0.5 s cooldown).
        val state = buildTestState()
        state.own("Concussive Rivets")
        val rockX = state.coreX + 400f
        state.placeObstaclesForTesting(testObstacle(rockX, state.coreY, radius = 80f))
        val enemy = state.dummy(x = state.coreX + 120f, y = state.coreY, hp = 10_000f)
        // The Core's push throws the enemy into the rock.
        state.pushEnemies(state.coreX, state.coreY, 200f, 1_200f, inward = false)
        assertTrue(enemy.thrownUntil > state.elapsed)
        var steps = 0
        while (enemy.hp == enemy.maxHp && steps < 120) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateEnemies(MutableGameState.FIXED_STEP)
            steps++
        }
        assertTrue(enemy.hp < enemy.maxHp, "the throw slammed into the rock")
        assertEquals(2, enemy.stacksOf(StatusElement.SONIC), "the shipped SLAM clause applied Sonic")
    }

    @Test
    fun realRicochetsReachTheProgressCounterOfTheDipoleUnlock() {
        val state = buildTestState()
        val dipole = state.content.coreShape(CoreShape.DIPOLE)
        assertEquals(CharacterUnlockRequirement.RICOCHETS, dipole.unlockRequirement)
        repeat(dipole.unlockTarget) {
            state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
            state.hp = state.maxHp
            state.elapsed += 0.3f
        }
        assertEquals(dipole.unlockTarget, state.pendingRicochets)
        state.exitRun()
        assertEquals(dipole.unlockTarget, assertNotNull(state.takeProgressUpdate()).ricochets)
    }

    @Test
    fun bumpersThrowTheCoreBackWithinTheirBandAndRicochetOnlyAtRicochetSpeedOncePerCooldown() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val bumperContact = 300f - 34f - MutableGameState.CORE_RADIUS
        val ricochet = state.obstacleRicochetSpeed()
        assertTrue(ricochet > 840f, "the bounces below stay under the ricochet speed ($ricochet)")
        state.flyIntoObstaclesForTesting(bumperContact + 1f, 0f, 400f, 0f)
        assertEquals(700f, state.speed, 0.5f, "a slow Core leaves at the bumper's minimum")
        assertTrue(state.velocityX < 0f)
        assertEquals(state.maxHp, state.hp, "bumpers never hurt")
        assertEquals(0, state.pendingRicochets, "a bounce below the ricochet speed is no ricochet")

        state.flyIntoObstaclesForTesting(bumperContact + 4f, 0f, 800f, 0f)
        assertEquals(840f, state.speed, 0.5f, "1.05 × the incoming speed")
        assertEquals(0, state.pendingRicochets, "holding still behind a bumper farms nothing")

        state.flyIntoObstaclesForTesting(bumperContact + ricochet / 240f, 0f, ricochet, 0f)
        assertEquals(1, state.pendingRicochets, "the ricochet speed makes it a ricochet")
        state.flyIntoObstaclesForTesting(bumperContact + 10f, 0f, 3_000f, 0f)
        assertEquals(minOf(1_400f, state.obstacleLethalSpeed() * state.content.obstacles.bumperLethalShare),
            state.speed, 0.5f, "the maximum throw respects the current flight's lethal cap")
        assertEquals(1, state.pendingRicochets, "one ricochet per bumper per 1.5 s")
        state.elapsed += 1.6f
        state.flyIntoObstaclesForTesting(bumperContact + 10f, 0f, 3_000f, 0f)
        assertEquals(2, state.pendingRicochets)
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun aBumperNeverThrowsTheCoreToTheLethalSpeed() {
        // A small window: its lethal speed lies inside the bumper band.
        val state = buildTestState().apply { resize(360f, 640f) }
        val tuning = state.content.obstacles
        val ceiling = state.obstacleLethalSpeed() * tuning.bumperLethalShare
        assertTrue(ceiling < tuning.bumperMaximumExitSpeed, "the band reaches past the lethal speed here: $ceiling")
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val bumperContact = 300f - 34f - MutableGameState.CORE_RADIUS
        state.flyIntoObstaclesForTesting(bumperContact + 10f, 0f, 3_000f, 0f)
        assertTrue(state.velocityX < 0f)
        assertEquals(ceiling, state.speed, 0.5f, "held below the lethal speed")
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun glassCometDoublesCollisionDamageAndKeepsShieldsOut() {
        val plain = buildTestState()
        val speed = plain.bandSpeed(0.4f)
        plain.ramAsteroid(speed)
        val plainShare = (plain.maxHp - plain.hp) / plain.maxHp
        assertTrue(plainShare > 0f)
        val glass = buildTestState(RelicId.GLASS_COMET)
        glass.maxShield = 300f
        glass.shield = 300f
        glass.ramAsteroid(speed)
        assertEquals(300f, glass.shield, "the liability keeps shields from absorbing it")
        assertEquals(plainShare * 2f, (glass.maxHp - glass.hp) / glass.maxHp, 0.0005f)
    }

    @Test
    fun unstableAsteroidsExplodeOnceAndStayGone() {
        val state = buildTestState()
        val bystander = state.dummy(x = 300f, y = 150f)
        val unstable = testObstacle(asteroidX, 0f, kind = ObstacleKind.UNSTABLE)
        state.placeObstaclesForTesting(unstable)
        val speed = state.obstacleDamageStartSpeed() + 20f
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertEquals(0, state.obstacleField.size)
        assertEquals(listOf(unstable.id), state.destroyedObstacleIds)
        assertEquals(state.maxHp - 10f, state.hp, 0.01f, "the blast deals 10 to the Core at tier 0")
        assertTrue(bystander.hp < bystander.maxHp)
        assertTrue(state.velocityX > 0f, "the Core flies on through the debris")

        val slow = buildTestState()
        slow.ramAsteroid(slow.obstacleDamageStartSpeed() - 50f, kind = ObstacleKind.UNSTABLE)
        assertEquals(1, slow.obstacleField.size, "below the damage start it is an ordinary rock")
    }

    @Test
    fun destroyedObstaclesNeverRegenerateAndTheMemoryIsBounded() {
        val state = buildTestState(seed = 3, rebirth = 5)
        var chunk = 2
        while (state.generateChunk(chunk, 4).isEmpty()) chunk++
        val target = state.generateChunk(chunk, 4).first()
        state.destroyedObstacleIds = listOf(target.id)
        assertTrue(state.generateChunk(chunk, 4).none { it.id == target.id })
        assertEquals(
            state.copyForReduction().apply { destroyedObstacleIds = emptyList() }.generateChunk(chunk, 4).filter { it.id != target.id },
            state.generateChunk(chunk, 4),
            "the rest of the chunk does not move",
        )
        val many = buildTestState()
        many.destroyedObstacleIds = List(ContentBounds.MAX_DESTROYED_OBSTACLES) { it.toLong() }
        val unstable = testObstacle(asteroidX, 0f, kind = ObstacleKind.UNSTABLE, id = 99_999L)
        many.placeObstaclesForTesting(unstable)
        val speed = many.obstacleDamageStartSpeed() + 20f
        many.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertEquals(ContentBounds.MAX_DESTROYED_OBSTACLES, many.destroyedObstacleIds.size)
        assertEquals(99_999L, many.destroyedObstacleIds.last())
        assertEquals(1L, many.destroyedObstacleIds.first(), "the oldest id is forgotten first")
    }

    // -----------------------------------------------------------------------------------------
    // Matrix.

    @Test
    fun obstacleGuardCutsDamageAndRaisesTheLethalSpeed() {
        val plain = buildTestState()
        val guarded = buildTestState().apply {
            matrix = matrix.copy(obstacleDamageMultiplier = 0.5f, obstacleLethalSpeedBonus = 150f)
        }
        assertEquals(plain.obstacleLethalSpeed() + 150f, guarded.obstacleLethalSpeed(), 0.01f, "a bonus under the cap adds")
        val speed = plain.bandSpeed(0.6f)
        plain.ramAsteroid(speed)
        guarded.ramAsteroid(speed)
        val plainLoss = plain.maxHp - plain.hp
        val guardedLoss = guarded.maxHp - guarded.hp
        assertTrue(guardedLoss < plainLoss * 0.5f, "guard halves the damage and the wider band lowers it: $guardedLoss vs $plainLoss")
        val survivor = buildTestState().apply { matrix = matrix.copy(obstacleLethalSpeedBonus = 150f) }
        survivor.ramAsteroid(plain.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.RUNNING, survivor.phase, "the lethal speed moved past this collision")
        // Stacked bonuses stop at the build's straight steady speed at the ceiling reach.
        val stacked = buildTestState().apply { matrix = matrix.copy(obstacleLethalSpeedBonus = 5_000f) }
        val ceiling = stacked.buildSteadyCursorSpeed(stacked.content.obstacles.lethalCeilingReach)
        assertTrue(ceiling < plain.obstacleLethalSpeed() + 5_000f)
        assertEquals(ceiling, stacked.obstacleLethalSpeed(), 0.01f, "the cap holds the lethal speed")
    }

    @Test
    fun ricochetRigBouncesHarderAndBursts() {
        val state = buildTestState().apply { matrix = matrix.copy(ricochetRig = MatrixEffect.RicochetRig(0.75f, 140f)) }
        val near = state.dummy(x = contactX - 40f, y = 60f)
        val speed = state.obstacleRicochetSpeed() + 20f
        state.ramAsteroid(speed)
        assertEquals(speed * 0.75f, -state.velocityX, 1f)
        assertTrue(near.hp < near.maxHp, "the ricochet bursts for impact damage")
    }

    @Test
    fun gravitySurferGrantsDashPhaseOnRicochet() {
        val state = buildTestState().apply { matrix = matrix.copy(ricochetPhaseSeconds = 0.4f) }
        state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
        assertEquals(0.4f, state.dashPhaseTime, 0.001f)
        val slow = buildTestState().apply { matrix = matrix.copy(ricochetPhaseSeconds = 0.4f) }
        slow.ramAsteroid(slow.obstacleDamageStartSpeed() - 100f)
        assertEquals(0f, slow.dashPhaseTime, "no ricochet, no phase")
    }

    @Test
    fun nearMissGrantsOverdriveWhenSkimmingFastAndRestsOnItsCooldown() {
        val state = buildTestState().apply { matrix = matrix.copy(nearMiss = MatrixEffect.NearMiss(40f, 900f, 6f, 0.5f)) }
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 80f))
        // Passing 20 above the surface at 1 000 u/s.
        val y = 80f + MutableGameState.CORE_RADIUS + 20f
        state.flyIntoObstaclesForTesting(300f, y, 1_000f, 0f)
        assertEquals(6f, state.overdriveCharge, 0.01f)
        state.flyIntoObstaclesForTesting(305f, y, 1_000f, 0f)
        assertEquals(6f, state.overdriveCharge, 0.01f, "cooldown")
        state.elapsed += 0.6f
        state.flyIntoObstaclesForTesting(300f, y, 800f, 0f)
        assertEquals(6f, state.overdriveCharge, 0.01f, "too slow")
        state.flyIntoObstaclesForTesting(300f, y + 30f, 1_000f, 0f)
        assertEquals(6f, state.overdriveCharge, 0.01f, "too far")
        state.flyIntoObstaclesForTesting(300f, y, 1_000f, 0f)
        assertEquals(12f, state.overdriveCharge, 0.01f)
    }

    // -----------------------------------------------------------------------------------------
    // Enemies.

    @Test
    fun enemiesArePushedOutAndSlamWhenThrownIntoAnObstacle() {
        val content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.SLAM, actions = immutableListOf(EffectAction.Strike(DamageBasis.FLAT, 40f)), cooldownSeconds = 0.5f))
        val state = buildTestState(content = content)
        state.own("Calibrated Coil")
        state.placeObstaclesForTesting(testObstacle(500f, 0f, radius = 80f))
        val enemy = state.dummy(x = 500f - 80f - 17f + 4f, y = 0f, hp = 10_000f)
        enemy.vx = 700f
        state.resolveEnemyObstacles()
        assertEquals(500f - 80f - 17f, enemy.x, 0.01f, "pushed back to the surface")
        assertEquals(0f, enemy.vx, 0.01f, "inward velocity removed")
        val slam = (700f - 200f) * 0.12f * state.runScale()
        assertTrue(enemy.maxHp - enemy.hp > slam + 10f, "slam plus the SLAM clause: ${enemy.maxHp - enemy.hp} vs $slam")

        val gentle = state.dummy(x = 500f, y = -80f - 17f + 4f, hp = 10_000f)
        gentle.vy = 200f
        state.resolveEnemyObstacles()
        assertEquals(gentle.maxHp, gentle.hp, "below 260 there is no slam")
    }

    @Test
    fun selfPropelledEnemiesSlideAroundObstacles() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(500f, 0f, radius = 80f))
        val enemy = state.dummy(x = 500f - 80f - 17f - 30f, y = 5f)
        enemy.vx = 100f
        enemy.vy = 0f
        state.resolveEnemyObstacles()
        assertTrue(enemy.vx < 100f, "the approach bends")
        assertTrue(enemy.vy > 0f, "along the surface")
        val thrown = state.dummy(x = 500f - 80f - 17f - 30f, y = 5f)
        thrown.vx = 100f
        state.markThrown(thrown)
        state.resolveEnemyObstacles()
        assertEquals(100f, thrown.vx, "thrown enemies fly straight into it")
    }

    @Test
    fun enemiesStartAvoidingALargeObstacleAcrossAChunkBorder() {
        val state = buildTestState()
        // The obstacle's centre lies in the next chunk; the enemy is inside 1.6 × their combined radii.
        val obstacle = testObstacle(state.content.obstacles.chunkSize + 10f, 5f, radius = 150f)
        state.placeObstaclesForTesting(obstacle)
        val enemy = state.dummy(x = obstacle.x - (150f + 17f) * 1.4f, y = 0f)
        enemy.vx = 100f
        enemy.vy = 0f
        state.resolveEnemyObstacles()
        assertTrue(enemy.vx < 100f, "the approach bends before the chunk border")
        assertTrue(enemy.vy < 0f, "along the surface")
    }

    @Test
    fun thrownEnemiesSlamIntoEachOther() {
        val state = buildTestState()
        state.clearObstaclesForTesting()
        val thrown = state.dummy(x = 400f, y = 0f, hp = 10_000f)
        val struck = state.dummy(x = 430f, y = 0f, hp = 10_000f)
        thrown.vx = 600f
        state.resolveEnemyObstacles()
        assertEquals(thrown.maxHp, thrown.hp, "unthrown contact is no slam")
        state.markThrown(thrown)
        state.resolveEnemyObstacles()
        assertTrue(thrown.hp < thrown.maxHp)
        assertTrue(struck.hp < struck.maxHp)
        assertTrue(struck.vx > 0f, "the struck enemy is knocked on")
        // The per-enemy slam cooldown keeps one contact from slamming every step.
        val after = struck.hp
        thrown.vx = 600f
        struck.vx = 0f
        state.resolveEnemyObstacles()
        assertEquals(after, struck.hp)
    }

    // -----------------------------------------------------------------------------------------
    // Characters.

    /** The canonical catalog whose Calibrated Coil only counts SLAM triggers (an every-64th gate never pays). */
    private val slamCounting = contentWithClauses("Calibrated Coil",
        EffectClause(EffectTrigger.SLAM, actions = immutableListOf(EffectAction.Overdrive(1f)), everyNth = 64))

    private fun MutableGameState.slamTriggers(): Int = clauseCounters[content.clauseIndex(itemNamed("Calibrated Coil").id, 0)]

    private fun repellingDipole(): MutableGameState = buildTestState(shape = CoreShape.DIPOLE, content = slamCounting).apply {
        own("Calibrated Coil")
        characterRuntime.polarity = Polarity.REPEL
    }

    /** Fixed steps of the two passes that can meet a thrown enemy's contact, in step order. */
    private fun MutableGameState.runThrowSteps(steps: Int) = repeat(steps) {
        elapsed += MutableGameState.FIXED_STEP
        updateCharacterRuntime(MutableGameState.FIXED_STEP)
        updateEnemies(MutableGameState.FIXED_STEP)
    }

    @Test
    fun aDipoleThrowIntoAnAsteroidSlamsOnceForTheDipoleDamageAlone() {
        val state = repellingDipole()
        val rockX = state.coreX + 500f
        state.placeObstaclesForTesting(testObstacle(rockX, state.coreY, radius = 80f))
        // Off-centre, so the enemy keeps sliding along the rock after the push-out (far outside the field).
        val thrown = state.dummy(x = rockX - 97f, y = state.coreY + 30f, hp = 10_000f)
        thrown.vx = 700f
        thrown.characterMarkTime = THROW_SECONDS
        assertTrue(state.isThrownByPolarity(thrown))
        state.runThrowSteps(24)
        val lost = thrown.maxHp - thrown.hp
        val power = state.abilityPower()
        // One Dipole slam at the contact's normal speed (below the 700 flight, above 600): no obstacle slam
        // on top, and no second slam from the slide that follows.
        assertTrue(lost in (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * 600f) * power..(SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * 700f) * power,
            "one Dipole slam: $lost")
        assertEquals(1, state.slamTriggers(), "one SLAM per contact")
        assertFalse(state.isThrownByPolarity(thrown), "the slam ends the throw")
    }

    @Test
    fun aDipolePairContactSlamsEachEnemyOnce() {
        val state = repellingDipole()
        state.clearObstaclesForTesting()
        val thrown = state.dummy(x = state.coreX + 400f, y = state.coreY, hp = 10_000f)
        val struck = state.dummy(x = state.coreX + 430f, y = state.coreY, hp = 10_000f)
        thrown.vx = 600f
        thrown.characterMarkTime = THROW_SECONDS
        // Thrown by the player too, so the obstacle pass's own enemy-enemy slam also looks at the pair.
        state.markThrown(thrown)
        state.runThrowSteps(24)
        val slam = (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * 600f) * state.abilityPower()
        assertEquals(thrown.maxHp - slam, thrown.hp, 0.5f)
        assertEquals(struck.maxHp - slam, struck.hp, 0.5f)
        assertEquals(2, state.slamTriggers(), "one SLAM for each slammed enemy")
    }

    @Test
    fun aDipoleThrowPassesThroughAPhasedPhantomWithoutASlam() {
        val state = repellingDipole()
        state.clearObstaclesForTesting()
        val thrown = state.dummy(x = state.coreX + 400f, y = state.coreY, hp = 10_000f)
        val phantom = state.dummy(x = state.coreX + 430f, y = state.coreY, hp = 10_000f, type = EnemyType.PHANTOM)
        phantom.shiftUntil = state.elapsed + 1f
        thrown.vx = 600f
        thrown.characterMarkTime = THROW_SECONDS
        state.elapsed += MutableGameState.FIXED_STEP
        state.updateCharacterRuntime(MutableGameState.FIXED_STEP)
        assertEquals(thrown.maxHp, thrown.hp, "no slam on the thrown enemy")
        assertEquals(phantom.maxHp, phantom.hp, "no slam on the phased Phantom")
        assertEquals(600f, thrown.vx, "no momentum shared with an intangible Phantom")
        assertTrue(state.isThrownByPolarity(thrown), "the throw flies on")
        assertEquals(0, state.slamTriggers())
        // No slam reaches a shifting enemy from any pass, so it never counts as a SLAM either.
        assertFalse(state.applySlamDamage(phantom, 900f))
        assertEquals(0, state.slamTriggers())
    }

    @Test
    fun aSlamThatOpensALevelUpChoiceEndsTheStep() {
        val content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.SLAM, actions = immutableListOf(EffectAction.GainData(5f))))
        val state = buildTestState(shape = CoreShape.DIPOLE, content = content).apply {
            own("Calibrated Coil")
            characterRuntime.polarity = Polarity.REPEL
        }
        state.clearObstaclesForTesting()
        state.data = state.nextLevelData - 1
        val thrown = state.dummy(x = state.coreX + 400f, y = state.coreY, hp = 10_000f)
        state.dummy(x = state.coreX + 430f, y = state.coreY, hp = 10_000f)
        thrown.vx = 600f
        thrown.characterMarkTime = THROW_SECONDS
        val bystander = state.dummy(x = state.coreX - 400f, y = state.coreY, hp = 10_000f)
        bystander.vx = 50f
        val bystanderX = bystander.x
        state.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(thrown.hp < thrown.maxHp, "the pair slammed")
        assertEquals(GamePhase.CHOICE, state.phase, "the slam's Data opened the level-up choice")
        assertEquals(bystanderX, bystander.x, "no enemy acts once the choice is open")
    }

    @Test
    fun anOrdinaryThrowStillTakesTheObstacleSlamUnderTheDipole() {
        // Only REPEL throws use the Dipole slam; a player-thrown enemy meets the obstacle rule.
        val state = repellingDipole()
        state.placeObstaclesForTesting(testObstacle(state.coreX + 500f, state.coreY, radius = 80f))
        val thrown = state.dummy(x = state.coreX + 500f - 80f - 17f + 4f, y = state.coreY, hp = 10_000f)
        thrown.vx = 700f
        state.resolveEnemyObstacles()
        assertEquals((700f - 200f) * 0.12f * state.runScale(), thrown.maxHp - thrown.hp, 0.01f)
        assertEquals(1, state.slamTriggers())
    }

    @Test
    fun aCrescentBlinkStopsShortOfAnAsteroidOnItsPath() {
        val state = buildTestState(shape = CoreShape.CRESCENT)
        state.heat = 0f
        // The rock's surface sits 150 ahead of the Core's leading edge, inside the 240 Blink range.
        val rockX = state.coreX + state.coreRadius + 150f + 60f
        state.placeObstaclesForTesting(testObstacle(rockX, state.coreY, radius = 60f))
        state.setVelocityForTesting(500f, 0f)
        state.pointerX = state.screenWidth
        state.pointerY = state.screenHeight * 0.5f
        val startX = state.coreX
        state.performDash()
        assertTrue(state.coreX > startX + 100f, "it still blinks most of the way: ${state.coreX - startX}")
        assertTrue(state.coreX + state.coreRadius < rockX - 60f, "it stops before the rock")
        assertFalse(state.obstacleBlocksSegment(startX, state.coreY, state.coreX, state.coreY, state.coreRadius))
        assertEquals(state.coreX, state.previousCoreX, "a blink is a teleport")
        assertEquals(1, state.characterRuntime.blinkCharges)
        // The next fixed step meets no rock: the blink never lands the Core inside one.
        state.updateObstacles()
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun aBlinkDriveBlinkStopsShortOfAnAsteroidOnItsPath() {
        // Any character's Blink Drive follows the Crescent's rule.
        val state = buildTestState(RelicId.BLINK_DRIVE)
        state.heat = 0f
        // The rock's surface sits 150 ahead of the Core's leading edge, inside the Blink Drive range.
        val rockX = state.coreX + state.coreRadius + 150f + 60f
        state.placeObstaclesForTesting(testObstacle(rockX, state.coreY, radius = 60f))
        state.setVelocityForTesting(500f, 0f)
        state.pointerX = state.screenWidth
        state.pointerY = state.screenHeight * 0.5f
        val startX = state.coreX
        state.performDash()
        assertTrue(state.coreX > startX + 100f, "it still blinks most of the way: ${state.coreX - startX}")
        assertTrue(state.coreX + state.coreRadius < rockX - 60f, "it stops before the rock: ${state.coreX}")
        assertFalse(state.obstacleBlocksSegment(startX, state.coreY, state.coreX, state.coreY, state.coreRadius))
        assertEquals(state.coreX, state.previousCoreX, "a blink is a teleport")
        assertTrue(state.blinkThisStep, "the rest of the step sees the Blink")
        // The next fixed step meets no rock: the Core neither lands inside one nor comes out past it.
        state.updateObstacles()
        assertEquals(state.maxHp, state.hp)
        assertTrue(state.coreX < rockX, "the Core stays on its side of the rock")
    }

    @Test
    fun aGravitySurferPhaseNeverRevivesAnEarlierDash() {
        val state = buildTestState(shape = CoreShape.TESSERACT).apply { matrix = matrix.copy(ricochetPhaseSeconds = 0.4f) }
        // A Dash that ends near its origin (straight into a rock, say) records no lattice node.
        state.beginCharacterDash()
        state.dashPhaseTime = DASH_PHASE_SECONDS
        while (state.dashPhaseTime > 0f) state.simulateStep(MutableGameState.FIXED_STEP)
        assertEquals(0, state.latticeNodeCount)
        // Far from that origin, a ricochet's dash phase is no Dash: the old origin never becomes a node.
        state.ramAsteroid(state.obstacleRicochetSpeed() + 20f)
        assertEquals(0.4f, state.dashPhaseTime, 0.001f)
        state.updateCharacterRuntime(MutableGameState.FIXED_STEP)
        assertEquals(0, state.latticeNodeCount, "the ricochet phase recorded no node")

        // The Pentagon's on-beat Dash ends with its phase, so a later ricochet phase is not on the beat.
        val pentagon = buildTestState(shape = CoreShape.PENTAGON)
        pentagon.characterRuntime.dashOnBeat = true
        pentagon.dashPhaseTime = DASH_PHASE_SECONDS
        while (pentagon.dashPhaseTime > 0f) pentagon.simulateStep(MutableGameState.FIXED_STEP)
        assertFalse(pentagon.characterRuntime.dashOnBeat)
    }

    @Test
    fun pushesFromThePlayerMarkEnemiesThrown() {
        val state = buildTestState()
        val enemy = state.dummy(x = 100f)
        state.pushEnemies(state.coreX, state.coreY, 200f, 400f, inward = false)
        assertTrue(enemy.thrownUntil > state.elapsed)
    }

    // -----------------------------------------------------------------------------------------
    // Shots.

    @Test
    fun asteroidsStopShotsButBumpersAndMinesLetThemBe() {
        val state = buildTestState()
        state.placeObstaclesForTesting(
            testObstacle(600f, 0f, radius = 80f),
            testObstacle(600f, 400f, radius = 34f, kind = ObstacleKind.BUMPER),
        )
        val hostile = Projectile(x = 525f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 500f, previousY = 0f)
        val friendly = Projectile(x = 670f, y = 20f, vx = -500f, vy = 0f, radius = 5f, life = 2f, hostile = false,
            previousX = 700f, previousY = 20f)
        val pastBumper = Projectile(x = 610f, y = 400f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 560f, previousY = 400f)
        val clear = Projectile(x = 300f, y = -200f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 290f, previousY = -200f)
        state.projectiles += listOf(hostile, friendly, pastBumper, clear)
        state.resolveCollisions(MutableGameState.FIXED_STEP)
        assertFalse(hostile in state.projectiles)
        assertFalse(friendly in state.projectiles)
        assertTrue(pastBumper in state.projectiles)
        assertTrue(clear in state.projectiles)
        assertEquals(state.maxHp, state.hp, "the stopped bolt never reached the Core")
    }

    @Test
    fun aDiscWiderThanTheCoreThrownAwayFromTheRockTheCoreRestsOnFliesItsFullReach() {
        val state = buildTestState(weapon = WeaponId.GYRE_DISC)
        // The first form widens the disc past the Orb's radius.
        state.weaponLevel = 3
        state.placeObstaclesForTesting(testObstacle(state.coreX - 80f - state.coreRadius - 0.05f, state.coreY))
        state.lastAimDirectionX = 1f
        state.lastAimDirectionY = 0f
        state.weaponClock = 0f
        state.updateWeapons(MutableGameState.FIXED_STEP)
        val disc = state.weaponRuntime.entities.single { it.kind == WeaponEntityKind.DISC }
        assertTrue(disc.radius > state.coreRadius)
        var reach = 0f
        while (disc.phase == 0 && disc.life > 0f) {
            reach = max(reach, disc.x - state.coreX)
            state.weaponClock = Float.MAX_VALUE
            state.updateWeapons(MutableGameState.FIXED_STEP)
        }
        assertTrue(reach >= GYRE_REACH * 0.95f, "The disc turned back after $reach")
    }

    @Test
    fun aShotWiderThanTheCoreLeavesTheRockTheCoreRestsOnButNotIntoIt() {
        val state = buildTestState(shape = CoreShape.SHARD)
        val rockX = state.coreX - 80f - state.coreRadius - 0.05f
        state.placeObstaclesForTesting(testObstacle(rockX, state.coreY))
        fun shot(vx: Float) = Projectile(x = state.coreX + vx * MutableGameState.FIXED_STEP, y = state.coreY, vx = vx, vy = 0f,
            radius = state.coreRadius + 1f, life = 2f, hostile = false, previousX = state.coreX, previousY = state.coreY)
        val away = shot(690f)
        val into = shot(-690f)
        state.projectiles += listOf(away, into)
        state.blockProjectilesAtObstacles()
        assertFalse(away.spent, "A shot fired away from the rock flies on")
        assertTrue(into.spent, "A shot fired into the rock stops")
    }

    // -----------------------------------------------------------------------------------------
    // Steps and the render model.

    @Test
    fun theFixedStepBouncesTheCoreBeforeEnemiesMeetIt() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(250f, 0f, radius = 80f))
        state.updatePointer(state.screenWidth, state.screenHeight * 0.5f)
        state.setVelocityForTesting(state.obstacleDamageStartSpeed() - 200f, 0f)
        repeat(60) { state.simulateStep(MutableGameState.FIXED_STEP) }
        assertTrue(state.coreX < 250f - 80f - MutableGameState.CORE_RADIUS + 0.5f, "never tunnels: ${state.coreX}")
    }

    @Test
    fun theRenderModelPublishesObstaclesAndReusesThemWhileTheWindowHolds() {
        val state = buildTestState(seed = 12)
        state.coreX = 3_000f
        state.refreshObstacleWindow()
        val first = state.toRenderModel()
        assertEquals(state.obstacleField.size, first.obstacles.size)
        first.obstacles.zip(state.obstacleField.obstacles).forEach { (projection, obstacle) ->
            assertEquals(obstacle.id, projection.id)
            assertEquals(obstacle.kind, projection.kind)
            assertEquals(obstacle.x, projection.x)
            assertEquals(obstacle.radius, projection.radius)
            assertEquals(obstacle.shapeSeed, projection.shapeSeed)
        }
        val next = state.copyForReduction()
        next.coreX += 10f
        val second = next.toRenderModel(first, state)
        assertSame(first.obstacles, second.obstacles)
        val equalContent = state.copyForReduction().apply { obstacleField = obstacleField.shifted(0f, 0f) }
        assertSame(first.obstacles, equalContent.toRenderModel(first, null).obstacles)
    }

    @Test
    fun theMinimapRangeFollowsTheViewportAndBlackoutHalvesIt() {
        val state = buildTestState()
        state.resize(1_280f, 800f)
        assertEquals(2_200f, state.minimapRange())
        state.resize(3_200f, 2_000f)
        assertEquals(1.4f * sqrt(3_200f * 3_200f + 2_000f * 2_000f) * 0.5f, state.minimapRange(), 0.5f)
        val blackout = buildTestState(rebirth = 9)
        blackout.resize(1_280f, 800f)
        assertEquals(1_100f, blackout.minimapRange())
        assertEquals(1_100f, blackout.toRenderModel().minimapRange)
    }

    @Test
    fun theMinimapPreferenceIsAValidProjectionThatReachesTheRenderModel() {
        val hidden = kinetickk.ball.profile.api.PlayerPreferences(minimapEnabled = false)
        assertTrue(kinetickk.ball.gameplay.nucleus.GameplayNucleus.isValidPreferencesProjection(hidden))
        val state = buildTestState()
        state.applyPreferences(hidden)
        assertFalse(state.toRenderModel().settings.minimapEnabled)
    }

    @Test
    fun spawnsAndLeashedEnemiesLandClearOfObstacles() {
        val state = buildTestState(seed = 33)
        state.coreX = 5_000f
        state.refreshObstacleWindow()
        repeat(40) { state.spawnScheduled(EnemyType.DRIFTER) }
        assertTrue(state.enemies.isNotEmpty())
        state.enemies.forEach { enemy ->
            state.obstacleField.obstacles.forEach { obstacle ->
                assertTrue(
                    distanceSquared(enemy.x, enemy.y, obstacle.x, obstacle.y) >= (obstacle.radius + enemy.radius).let { it * it },
                    "spawned inside an obstacle",
                )
            }
        }
    }
}
