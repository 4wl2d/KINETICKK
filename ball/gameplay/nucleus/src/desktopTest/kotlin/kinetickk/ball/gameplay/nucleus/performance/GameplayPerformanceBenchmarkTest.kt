// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.gameplay.nucleus.simulation.EncounterOwner
import kinetickk.ball.gameplay.nucleus.simulation.EncounterState
import kinetickk.ball.gameplay.nucleus.simulation.WorldKeepOut
import kinetickk.ball.gameplay.nucleus.simulation.copyForScalarInputReduction
import kinetickk.ball.gameplay.nucleus.simulation.encounterTag
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.ION_SWARM_COOLDOWN
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.addField
import kinetickk.ball.gameplay.nucleus.simulation.cooldown
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.performance.validateBenchmarkScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameplayPerformanceBenchmarkTest {
    @Test
    fun releaseWorkloadRetainsAllScenariosAndValidatesDeterministicOutcomes() {
        assertEquals("gameplay-core-v4", GAMEPLAY_BENCHMARK_SUITE_VERSION)
        val first = gameplayBenchmarkScenarios()
        val second = gameplayBenchmarkScenarios()
        assertEquals(24, first.size)
        assertEquals(first.map { it.name }.toSet().size, first.size)
        assertEquals(first.map { it.metadata }, second.map { it.metadata })
        first.forEach { validateBenchmarkScenario(it) }
    }

    @Test
    fun reactionWorkloadsExerciseCombatAndGaugeChangesWithoutMutatingTheSource() {
        // A 100 ms delta at 2x leaves the final near-step Float remainder for the following frame.
        for ((speed, delta, steps) in listOf(Triple(1f, 1f / 60f, 2), Triple(2f, 1f / 60f, 4), Triple(2f, 0.1f, 23))) {
            val model = reactionPerformanceState(seed = 732_001, simulationSpeed = speed)
            val source = authorityState(model)
            val original = canonicalStateFingerprint(model)
            val snapshot = GameplayNucleus.renderSnapshot(source)
            val pulse = GameplayNucleusPulse.Intent(GameplayInteractionPulse.FrameElapsed.fromValidated(delta))
            val frame = (GameplayNucleus.decide(source, pulse) as GameplayDecision.Accepted).frame
            val result = checkNotNull(frame.nextState.engine).model

            assertEquals(model.content.rebirth.maxActiveEnemies, model.enemies.size)
            assertEquals(model.content.build.policy.maximumFields, model.combatFieldTable.count)
            assertTrue(model.cooldown(ION_SWARM_COOLDOWN) < ION_SWARM_COOLDOWN * 0.5f)
            assertEquals(GamePhase.RUNNING, result.phase)
            assertEquals(steps, result.lastTransitionSteps)
            assertTrue(result.stormCharge > model.stormCharge, "The Stormheart gauge must change during the measured frame")
            assertTrue(result.damageDealt > model.damageDealt, "The crowd must receive actual damage")
            // Every field the frame did not replace (a new reaction overwrites the oldest of its kind) aged.
            val aged = (0 until result.combatFieldTable.count).filter { index ->
                index < model.combatFieldTable.count &&
                    result.combatFieldTable.sequence(index) == model.combatFieldTable.sequence(index)
            }
            assertTrue(aged.isNotEmpty())
            aged.forEach { index ->
                assertTrue(result.combatFieldTable[index, FieldCell.REMAINING] < model.combatFieldTable[index, FieldCell.REMAINING])
            }
            assertTrue(result.enemies.any { it.status[StatusCell.LAST_REACTION_AT] > model.elapsed })
            assertTrue(frame.outputs.filterIsInstance<GameplayOutput.EmitVisualFx>().any { output ->
                output.cues.any { it is VisualFxCue.ReactionTriggered }
            }, "A measured frame must execute reactions, not merely carry status labels")
            assertEquals(
                exactRenderValueFacts(checkNotNull(GameplayNucleus.renderSnapshot(frame.nextState).renderModel)),
                exactRenderValueFacts(checkNotNull(GameplayNucleus.renderSnapshot(frame.nextState, source, snapshot).renderModel)),
            )
            assertEquals(original, canonicalStateFingerprint(model), "Benchmark repetitions must retain the source")
            val repeated = (GameplayNucleus.decide(source, pulse) as GameplayDecision.Accepted).frame
            assertEquals(canonicalStateFingerprint(result), canonicalStateFingerprint(checkNotNull(repeated.nextState.engine).model))
            assertEquals(original, canonicalStateFingerprint(model))
        }
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
            { sites = listOf(SiteState(
                0L, SiteKind.RESONANT_CIRCUIT, 10f, 20f, 40f,
            )) },
            { rebornItems[0] = 1 },
            { clauseReadyAt[0] = 1f },
            { clauseCounters[0] = 1 },
            { statusAccumulators[0] = 0.5f },
            { buffs = listOf(ActiveBuff(0, BuffStat.DAMAGE, 0.1f, 1, 2f)) },
            { addField(CombatFieldKind.FUSION, FieldSource.REACTION, 0, 0f, 0f, radius = 10f, seconds = 1f, damagePerSecond = 1f) },
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
            { recentEventTypes = 4L },
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
            // 0.5.0 combat foundations.
            { addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, 2, 0f, 0f, radius = 10f, seconds = 1f, damagePerSecond = 0f) },
            { coreFieldMask = 1 },
            { fieldEnterReadyAt = 1f },
            { ablazeUntil = 1f },
            { frictionlessUntil = 1f },
            { blastJumpUntil = 1f },
            { weaveUntil = 1f },
            { weaveDamage = 1f },
            { weaveRange = 1f },
            { guardShards = 2 },
            { guardUntil = 1f },
            { guardDamage = 1f },
            { voidAnchorId = 3 },
            { voidAnchorUntil = 1f },
            { overcurrentReadyAt = 1f },
            { flameRefundReadyAt = 1f },
            { erasureLatchId = 3 },
            { coreImpulseReadyAt = 1f },
            { primedDashUntil = 1f },
            { phaseUntil = 1f },
            { shotErasedDispatches = 1 },
            { markDispatches = 1 },
            { shotsErased = 1L },
            { dashHeatBank = 1f },
            { pendingActiveItemId = content.activeItemIds.first { it >= 0 } },
            { pendingActiveReborn = true },
            { skillBufferMask = 2 },
            { skillBufferTime = 0.1f },
            { apexBufferTime = 0.1f },
            { enemies.first().status[StatusCell.MARK] = 1f },
            { enemies.first().status[StatusCell.MARK_UNTIL] = 2f },
            // 0.6.0 shared world contracts: terrain, sites, run variety, enemies, structures and dimensions.
            { coreStraightness = 0.5f },
            { gripStrength = 2f },
            { slingStrength = 2f },
            { obstacleHitSurfaceId = 7L },
            { coreObstacleGap = 40f },
            { grazeReadyAt = 1f },
            { slingWellId = 3L },
            { slingEntryHeading = 1f },
            { nextSiteWindowIndex = 3 },
            { siteRandom.nextInt() },
            { ambientSites = AmbientSiteWindow(1, 2, immutableListOf(
                SiteState(5L, SiteKind.DRIFT_CACHE, 1f, 2f, Float.POSITIVE_INFINITY),
            )) },
            { secretSigil = SiteState(16L, SiteKind.FAR_SIGIL, 1f, 2f, Float.POSITIVE_INFINITY) },
            { openedSiteIds = listOf(4L) },
            { runSecretFlags = 1 },
            { worldDiscoveryMask = 2 },
            { pendingWorldDiscoveryMask = 2 },
            { pendingFloorOffers = listOf(ItemRarity.RARE) },
            { goldenDrifterId = 5 },
            { goldenDrifterUntil = 3f },
            { spinBuckets = listOf(1f) },
            { spinCurrent = 1f },
            { spinClock = 1f },
            { spinLastAngle = 1f },
            { spinPrimed = true },
            { omenPending = true },
            { runOmen = OmenId.RICH_VEINS },
            { runMutatorMask = 1 },
            { directorPersonality = DirectorPersonalityId.TIDE },
            { featuredAspect = 2 },
            { surgeIndex = 1 },
            { surgeThemeType = 3 },
            { varietyAnnouncement = 1 },
            { keynoteTag = BuildTag.DRIVE },
            { keynoteElement = StatusElement.SONIC },
            { worldEventPlans = immutableListOf(WorldEventPlan(WorldEventKind.DATA_STORM, 1f, 2f, 3f)) },
            { nextWorldEvent = 1 },
            { worldEvent = WorldEventProjection(WorldEventKind.SOLAR_FLARE, 1f, 2f, 3f) },
            { worldEventPulse = 1 },
            { worldEventNextPulseAt = 2f },
            { eventSignals = immutableListOf(EventSignalProjection(1, SignalKind.MERCHANT, 1f, 2f, 3f)) },
            { nextSignalSlot = 1 },
            { signalsSpawned = 1 },
            { signalsEngaged = 1 },
            { pendingSignalPrompts = immutableListOf(SignalPrompt(SignalKind.DERELICT, 0)) },
            { offerRarityFloor = ItemRarity.EPIC },
            { secretBossMask = 1L shl 34 },
            { encounters = immutableListOf(
                EncounterState(encounterTag(EncounterOwner.SITE, 1L), 0f, 0f, 300f, 2_400f, 1, 0, -1, 0f),
            ) },
            { structureField = StructureField.of(
                0, 0, 0, false,
                listOf(PlacedStructure(1L shl 63, StructureTemplateId.VAULT, 0, 900f, 0f, 300f, 200f, 0, 1, false)),
                listOf(Wall(1L shl 63, WallKind.SOLID, 600f, -200f, 1_200f, -200f, 24f)),
                emptyList(),
            ) },
            { dungeon = DungeonState(1L shl 63, StructureTemplateId.VAULT, 0, 900f, 0f, enteredAt = 1f) },
            { spentStructureIds = listOf(1L) },
            { lastAnnouncedStructureId = 1L },
            { worldLayer = WorldLayer.ZENITH },
            { worldKeepOut = WorldKeepOut(WorldLayer.BASE, 0.0, 0.0, 900f) },
            { runBoons = immutableListOf(32) },
            { dimensionRun = DimensionRunState(visitedMask = 2) },
            { dimensionSlowSeconds = 0.5f },
            { dimensionScorchClock = 0.25f },
            { dimensionLocalClock = 3f },
            { dimensionRewardLayer = WorldLayer.NADIR },
            { enemies.first().ownerTag = 5L },
            { enemies.first().charges = 1 },
            { enemies.first().phaseStartPending = true },
            { enemies.first().affix = EliteAffix.SHIELDED },
            { enemies.first().champion = true },
        )
        changes.forEachIndexed { index, change ->
            val changed = source.copyForReduction().apply(change)
            assertNotEquals(original, canonicalStateFingerprint(changed), "change $index")
            assertEquals(canonicalStateFingerprint(changed), canonicalStateFingerprint(changed.copyForReduction()))
            assertEquals(original, canonicalStateFingerprint(source))
        }
        // Baby mode is fixed when the run is created, so it is witnessed by construction and survives every copy.
        val easy = MutableGameState(canonicalGameplayContent, runMode = RunMode.EASY).apply { startRun() }
        assertNotEquals(original, canonicalStateFingerprint(easy))
        assertTrue(easy.copyForReduction().easyMode)
        assertTrue(easy.copyForScalarInputReduction().easyMode)
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
        assertEquals(6_813_101_050_753_336_834L, obstacleFieldFingerprint(17L, obstacles))
        val projections = obstacles.map { ObstacleProjection(it.id, it.kind, it.x, it.y, it.radius, it.shapeSeed, it.fuseAt, it.repels) }
        assertEquals(306_409_003_833_105_930L, obstacleProjectionFingerprint(17L, projections))
        val fusedProjections = projections.mapIndexed { index, projection -> if (index == 2) projection.copy(fuseAt = 4f) else projection }
        assertNotEquals(obstacleProjectionFingerprint(17L, projections), obstacleProjectionFingerprint(17L, fusedProjections))
        val repellingProjections = projections.mapIndexed { index, projection -> if (index == 0) projection.copy(repels = true) else projection }
        assertNotEquals(obstacleProjectionFingerprint(17L, projections), obstacleProjectionFingerprint(17L, repellingProjections))
        val rekinded = obstacles.mapIndexed { index, obstacle -> if (index == 0) obstacle.copy(kind = ObstacleKind.UNSTABLE) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, rekinded))
        val rearmed = obstacles.mapIndexed { index, obstacle -> if (index == 1) obstacle.copy(ricochetReadyAt = 3f) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, rearmed))
        val fused = obstacles.mapIndexed { index, obstacle -> if (index == 2) obstacle.copy(fuseAt = 4f) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, fused))
        val repelling = obstacles.mapIndexed { index, obstacle -> if (index == 0) obstacle.copy(repels = true) else obstacle }
        assertNotEquals(obstacleFieldFingerprint(17L, obstacles), obstacleFieldFingerprint(17L, repelling))
    }
}
