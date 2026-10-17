// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObstacleSystemTest {
    // The Core touches the test asteroid (centre 300, radius 80) when its centre reaches x = 204.
    private val asteroidX = 300f
    private val contactX = asteroidX - 80f - MutableGameState.CORE_RADIUS

    private fun MutableGameState.ramAsteroid(speed: Float, kind: ObstacleKind = ObstacleKind.ASTEROID) {
        placeObstaclesForTesting(testObstacle(asteroidX, 0f, kind = kind))
        flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
    }

    private fun MutableGameState.bandSpeed(fraction: Float): Float {
        val start = obstacleDamageStartSpeed(0f)
        return start + (obstacleLethalSpeed(0f) - start) * fraction
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
            val state = buildTestState(seed = 5, rebirth = tier)
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
        // Mean per chunk: 2.2 × the tier-4 density (1.2).
        assertEquals(2.2f * 1.2f, calmTotal / 900f, 0.25f)
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

    // -----------------------------------------------------------------------------------------
    // The Core.

    @Test
    fun aSlowCoreSlidesOrBouncesWithoutDamageOrRicochet() {
        val state = buildTestState()
        val speed = state.obstacleDamageStartSpeed(0f) - 10f
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
    fun theDamageBandScalesWithSpeedIgnoresReductionAndIsAbsorbedByShields() {
        val state = buildTestState()
        val start = state.obstacleDamageStartSpeed(0f)
        val lethal = state.obstacleLethalSpeed(0f)
        val speed = state.bandSpeed(0.5f)
        state.damageReduction = 0.5f
        state.ramAsteroid(speed)
        // Tier 0: forgiving ×0.7, collision tier factor 1 + 0.04 × 0.
        val expected = state.maxHp * 0.5f * ((speed - start) / (lethal - start)).pow(1.3f) * 0.7f
        assertEquals(state.maxHp - expected, state.hp, 0.05f)
        assertEquals(1, state.pendingRicochets)
        assertTrue(state.velocityX < 0f)
        assertEquals(speed * 0.6f, -state.velocityX, 1f)
        assertFalse(IncomingDamage.OBSTACLE.cancellableByFacets)

        val shielded = buildTestState()
        shielded.maxShield = 200f
        shielded.shield = 200f
        shielded.ramAsteroid(speed)
        assertEquals(shielded.maxHp, shielded.hp, "the shield takes the hit")
        assertEquals(200f - expected, shielded.shield, 0.05f)
    }

    @Test
    fun collisionDamageUsesTheTierFactorInsteadOfTheRebirthMultiplier() {
        val tier = 6
        val state = buildTestState(rebirth = tier)
        assertTrue(state.rebirthProfile.incomingDamageMultiplier > 1.2f)
        val speed = state.bandSpeed(0.5f)
        state.ramAsteroid(speed)
        val start = state.obstacleDamageStartSpeed(0f)
        val lethal = state.obstacleLethalSpeed(0f)
        val expected = state.maxHp * 0.5f * ((speed - start) / (lethal - start)).pow(1.3f) * (1f + 0.04f * tier)
        assertEquals(state.maxHp - expected, state.hp, 0.05f)
    }

    @Test
    fun tierZeroForgivesTheStartSpeedAndDamageUntilTierThree() {
        val zero = buildTestState(rebirth = 0)
        val three = buildTestState(rebirth = 3)
        val five = buildTestState(rebirth = 5)
        assertEquals(three.obstacleDamageStartSpeed(0f) + 80f, zero.obstacleDamageStartSpeed(0f), 0.01f)
        assertEquals(three.obstacleDamageStartSpeed(0f), five.obstacleDamageStartSpeed(0f), 0.01f)
        assertEquals(three.obstacleLethalSpeed(0f), zero.obstacleLethalSpeed(0f), 0.01f)
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
        state.ramAsteroid(state.obstacleLethalSpeed(0f) + 50f)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
        assertTrue(state.hp <= 0f)
    }

    @Test
    fun prismFacetsOrBarriersNeverSaveALethalCollision() {
        val prism = buildTestState(shape = CoreShape.PRISM)
        prism.ramAsteroid(prism.obstacleLethalSpeed(0f) + 50f)
        assertEquals(GamePhase.GAME_OVER, prism.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, prism.message)
    }

    @Test
    fun secondWindAndOuroborosAnswerALethalCollision() {
        val windy = buildTestState()
        windy.matrix = windy.matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        windy.ramAsteroid(windy.obstacleLethalSpeed(0f) + 50f)
        assertEquals(GamePhase.RUNNING, windy.phase)
        assertEquals(windy.maxHp * 0.3f, windy.hp, 0.01f)
        assertTrue(windy.secondWindSpent)

        val sealed = buildTestState(RelicId.OUROBOROS_SEAL)
        sealed.ramAsteroid(sealed.obstacleLethalSpeed(0f) + 50f)
        assertEquals(GamePhase.RUNNING, sealed.phase)
        assertTrue(sealed.ouroborosSpent)
        assertTrue(sealed.hp > 0f)
    }

    @Test
    fun collisionsHurtAtMostOncePerCooldown() {
        val state = buildTestState()
        val speed = state.bandSpeed(0.4f)
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
        state.ramAsteroid(state.bandSpeed(0.2f))
        assertTrue(state.overdriveCharge >= charge + 20f, "the RICOCHET clause fired")
    }

    @Test
    fun ricochetsReachTheRunsProgressUpdate() {
        val state = buildTestState()
        state.ramAsteroid(state.bandSpeed(0.1f))
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        state.flyIntoObstaclesForTesting(300f - 34f - MutableGameState.CORE_RADIUS + 1f, 0f, 400f, 0f)
        assertEquals(2, state.pendingRicochets)
        state.exitRun()
        assertEquals(2, assertNotNull(state.takeProgressUpdate()).ricochets, "the profile counts them")
        assertEquals(0, state.pendingRicochets)
    }

    @Test
    fun bumpersThrowTheCoreBackWithinTheirBandAndRicochetOncePerCooldown() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val bumperContact = 300f - 34f - MutableGameState.CORE_RADIUS
        state.flyIntoObstaclesForTesting(bumperContact + 1f, 0f, 400f, 0f)
        assertEquals(700f, state.speed, 0.5f, "a slow Core leaves at the bumper's minimum")
        assertTrue(state.velocityX < 0f)
        assertEquals(state.maxHp, state.hp, "bumpers never hurt")
        assertEquals(1, state.pendingRicochets)

        state.flyIntoObstaclesForTesting(bumperContact + 4f, 0f, 1_200f, 0f)
        assertEquals(1_260f, state.speed, 0.5f, "1.05 × the incoming speed")
        assertEquals(1, state.pendingRicochets, "one ricochet per bumper per 1.5 s")
        state.elapsed += 1.6f
        state.flyIntoObstaclesForTesting(bumperContact + 10f, 0f, 3_000f, 0f)
        assertEquals(1_400f, state.speed, 0.5f, "never faster than the bumper's maximum")
        assertEquals(2, state.pendingRicochets)
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun aBumperNeverThrowsTheCoreToTheLethalSpeedOfItsNewHeading() {
        // A portrait phone: across its narrow width the lethal speed lies inside the bumper band.
        val state = buildTestState().apply { resize(412f, 915f) }
        val tuning = state.content.obstacles
        val ceiling = state.obstacleLethalSpeed(PI.toFloat()) * tuning.bumperLethalShare
        assertTrue(ceiling < tuning.bumperMaximumExitSpeed, "the band reaches past the lethal speed here: $ceiling")
        state.placeObstaclesForTesting(testObstacle(300f, 0f, radius = 34f, kind = ObstacleKind.BUMPER))
        val bumperContact = 300f - 34f - MutableGameState.CORE_RADIUS
        state.flyIntoObstaclesForTesting(bumperContact + 10f, 0f, 3_000f, 0f)
        assertTrue(state.velocityX < 0f)
        assertEquals(ceiling, state.speed, 0.5f, "held below the lethal speed of the throw")
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
        val speed = state.obstacleDamageStartSpeed(0f) + 20f
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        assertEquals(0, state.obstacleField.size)
        assertEquals(listOf(unstable.id), state.destroyedObstacleIds)
        assertEquals(state.maxHp - 10f, state.hp, 0.01f, "the blast deals 10 to the Core at tier 0")
        assertTrue(bystander.hp < bystander.maxHp)
        assertTrue(state.velocityX > 0f, "the Core flies on through the debris")

        val slow = buildTestState()
        slow.ramAsteroid(slow.obstacleDamageStartSpeed(0f) - 50f, kind = ObstacleKind.UNSTABLE)
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
        val speed = many.obstacleDamageStartSpeed(0f) + 20f
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
            matrix = matrix.copy(obstacleDamageMultiplier = 0.5f, obstacleLethalSpeedBonus = 300f)
        }
        assertEquals(plain.obstacleLethalSpeed(0f) + 300f, guarded.obstacleLethalSpeed(0f), 0.01f)
        val speed = plain.bandSpeed(0.6f)
        plain.ramAsteroid(speed)
        guarded.ramAsteroid(speed)
        val plainLoss = plain.maxHp - plain.hp
        val guardedLoss = guarded.maxHp - guarded.hp
        assertTrue(guardedLoss < plainLoss * 0.5f, "guard halves the damage and the wider band lowers it: $guardedLoss vs $plainLoss")
        val survivor = buildTestState().apply { matrix = matrix.copy(obstacleLethalSpeedBonus = 300f) }
        survivor.ramAsteroid(plain.obstacleLethalSpeed(0f) + 50f)
        assertEquals(GamePhase.RUNNING, survivor.phase, "the lethal speed moved past this collision")
    }

    @Test
    fun ricochetRigBouncesHarderAndBursts() {
        val state = buildTestState().apply { matrix = matrix.copy(ricochetRig = MatrixEffect.RicochetRig(0.75f, 140f)) }
        val near = state.dummy(x = contactX - 40f, y = 60f)
        val speed = state.bandSpeed(0.2f)
        state.ramAsteroid(speed)
        assertEquals(speed * 0.75f, -state.velocityX, 1f)
        assertTrue(near.hp < near.maxHp, "the ricochet bursts for impact damage")
    }

    @Test
    fun gravitySurferGrantsDashPhaseOnRicochet() {
        val state = buildTestState().apply { matrix = matrix.copy(ricochetPhaseSeconds = 0.4f) }
        state.ramAsteroid(state.bandSpeed(0.2f))
        assertEquals(0.4f, state.dashPhaseTime, 0.001f)
        val slow = buildTestState().apply { matrix = matrix.copy(ricochetPhaseSeconds = 0.4f) }
        slow.ramAsteroid(slow.obstacleDamageStartSpeed(0f) - 100f)
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

    // -----------------------------------------------------------------------------------------
    // Steps and the render model.

    @Test
    fun theFixedStepBouncesTheCoreBeforeEnemiesMeetIt() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(250f, 0f, radius = 80f))
        state.updatePointer(state.screenWidth, state.screenHeight * 0.5f)
        state.setVelocityForTesting(state.obstacleDamageStartSpeed(0f) - 200f, 0f)
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
