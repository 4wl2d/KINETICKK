// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.performance.validateBenchmarkScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameplayPerformanceBenchmarkTest {
    @Test
    fun releaseWorkloadRetainsAllScenariosAndValidatesDeterministicOutcomes() {
        assertEquals("gameplay-core-v3", GAMEPLAY_BENCHMARK_SUITE_VERSION)
        val first = gameplayBenchmarkScenarios()
        val second = gameplayBenchmarkScenarios()
        assertEquals(21, first.size)
        assertEquals(first.map { it.name }.toSet().size, first.size)
        assertEquals(first.map { it.metadata }, second.map { it.metadata })
        first.forEach { validateBenchmarkScenario(it) }
    }

    @Test
    fun witnessDetectsNewRetainedGameplayFactsAndCopyPreservesThem() {
        val source = MutableGameState(canonicalGameplayContent).apply { startRun() }
        val original = canonicalStateFingerprint(source)
        val changes: List<MutableGameState.() -> Unit> = listOf(
            { coreShape = CoreShape.SHARD },
            { characterRuntime.charge = 0.5f },
            { characterRuntime.ghostPath[0] = 3f },
            { smoothedVelocityX = 10f },
            { turnHoldTime = 0.1f },
            { pendingEliteKills = 1 },
            { pendingDashHits = 1 },
            { pendingCompletedOrbits = 1 },
            { pendingArchitectDefeatedWith = CoreShape.ORB },
            { pendingTotalKills = 1 },
            { pendingRicochets = 1 },
            { pendingDataPickups = 1 },
            { achievementFlushDue = true },
            { itemOffersOpened = 1 },
            { secondWindSpent = true },
            { matrixFreeDashReadyAt = 2f },
            { matrixRanks[0] = 1 },
            { matrix = matrix.copy(eliteBounty = true) },
            { pendingDirectedRewards = listOf(DirectedReward.RELIC) },
            { pointsOfInterest = listOf(PointOfInterestState(
                0, PointOfInterestKind.RESONANT_CIRCUIT, 10f, 20f, 40f,
            )) },
            { rebornItems[0] = 1 },
            { clauseReadyAt[0] = 1f },
            { clauseCounters[0] = 1 },
            { statusAccumulators[0] = 0.5f },
            { buffs = listOf(ActiveBuff(0, BuffStat.DAMAGE, 0.1f, 1, 2f)) },
            { combatFields = listOf(CombatField(CombatFieldKind.FUSION, 1f, 0f, 0f, 10f, 1f)) },
            { echoHits += EchoHit(1, 0.3f, 2f, false) },
            { severedRelics = listOf(SeveredRelic(RelicId.GLASS_COMET, 30f)) },
            { reactionTokens = 3f },
            { discoveredReactionMask = 1 },
            { stasisBank = 2 },
            { overdriveBurnoutUntil = 4f },
            { offerPity = 2 },
            { rewardRandom.nextInt() },
            { enemies.first().status[StatusCell.STACKS + StatusElement.ION.ordinal] = 2f },
            { enemies.first().characterMarkTime = 1f },
            { enemies.first().lastCharacterDash = 1 },
            { elitesSpawned = 1 },
            { enemies.first().aspect = StatusElement.CRYO.ordinal },
            { enemies.first().aspectImmune = true },
            { enemies.first().phase = 1 },
            { enemies.first().shiftUntil = 3f },
            { enemies.first().escortsPending = 2 },
            { enemies.first().lastDamagedAt = 2f },
            { coreRadius = 20f },
            { enemies.first().disabledUntil = 2f },
            { enemies.first().silencedUntil = 2f },
            // 0.4.0 wave A: the spawn director, the new enemy behaviors, Null Covenant and frame pacing.
            { spawnRandom.nextInt() },
            { heraldsSpawned = 1 },
            { directorPhase = 2 },
            { spawnBudget = 3f },
            { pendingEventKind = 1 },
            { recentEventTypes = 4 },
            { ambushReadyAt = 5f },
            { capOverflowReadyAt = 5f },
            { spawnEventCount = 3 },
            { lastSpawnEventKind = 2 },
            { enemies.first().mode = 2 },
            { enemies.first().facing = 1f },
            { enemies.first().linkedBy = 7 },
            { enemies.first().shieldUntil = 3f },
            { enemies.first().abilityAt = 3f },
            { integrityLiabilityCut = 9f },
            { accumulator = 0.004f },
            { previousCameraX = 5f },
            { previousMorningstarX = 5f },
            { viewportDensity = 2f },
            { interpolationPrimed = !interpolationPrimed },
            // 0.4.0 world obstacles and pickups.
            { worldOriginX = 1_200.0 },
            { obstacleField = ObstacleField.of(0, 0, 1, listOf(listOf(
                Obstacle(id = 1L, kind = ObstacleKind.ASTEROID, x = 3_000f, y = 0f, radius = 60f, shapeSeed = 4),
            ))) },
            { destroyedObstacleIds = listOf(9L) },
            { obstacleHitReadyAt = 1f },
            { nearMissReadyAt = 1f },
            { reviveInvulnerableUntil = 1f },
            { pickups += Pickup(PickupType.DATA, 5f, 5f, attracted = true) },
            { enemies.first().thrownUntil = 1f },
            { enemies.first().slamReadyAt = 1f },
            // 0.4.0 weapons: the weapon runtime.
            { weaponRuntime.orbitalPhase = 1f },
            { weaponRuntime.entityPassActive = true },
            { weaponRuntime.tetherRamp = 1.5f },
            { weaponRuntime.entities += kinetickk.ball.gameplay.nucleus.model.WeaponEntity(
                kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind.DISC, x = 17f, y = 27f, radius = 5f, life = 1f, maxLife = 1f,
            ) },
            // 0.4.0 Rebirth rework.
            { rebirthRank = 9 },
            { volatileBursts = kinetickk.foundation.collections.immutableListOf(
                kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection(1f, 2f, 70f, 0f, 0.35f),
            ) },
            { enemies.first().armored = true },
            { enemies.first().armorCracked = true },
        )
        changes.forEachIndexed { index, change ->
            val changed = source.copyForReduction().apply(change)
            assertNotEquals(original, canonicalStateFingerprint(changed), "change $index")
            assertEquals(canonicalStateFingerprint(changed), canonicalStateFingerprint(changed.copyForReduction()))
            assertEquals(original, canonicalStateFingerprint(source))
        }
    }

    /**
     * The published outcome must compare across JVM processes. An enum's `hashCode()` is an identity hash that
     * changes between processes (and with unrelated hashing earlier on the thread), so obstacle witnesses pin
     * to a constant: any identity hash in them would miss it.
     */
    @Test
    fun obstacleWitnessesArePinnedAcrossProcesses() {
        val obstacles = listOf(
            Obstacle(id = 1L, kind = ObstacleKind.ASTEROID, x = 120.5f, y = -40f, radius = 60f, shapeSeed = 4),
            Obstacle(id = 2L, kind = ObstacleKind.BUMPER, x = -300f, y = 75.25f, radius = 34f, shapeSeed = -9, ricochetReadyAt = 1.5f),
            Obstacle(id = 3L, kind = ObstacleKind.UNSTABLE, x = 900f, y = 900f, radius = 150f, shapeSeed = 123_456),
        )
        assertEquals(-757_718_850_270_372_542L, obstacleFieldFingerprint(17L, obstacles))
        val projections = obstacles.map { ObstacleProjection(it.id, it.kind, it.x, it.y, it.radius, it.shapeSeed) }
        assertEquals(2_368_442_101_832_176_810L, obstacleProjectionFingerprint(17L, projections))
        val rekinded = obstacles.mapIndexed { index, obstacle -> if (index == 0) obstacle.copy(kind = ObstacleKind.UNSTABLE) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, rekinded))
        val rearmed = obstacles.mapIndexed { index, obstacle -> if (index == 1) obstacle.copy(ricochetReadyAt = 3f) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, rearmed))
    }
}
