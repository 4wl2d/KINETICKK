// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionObjectiveWeight
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compare the same seeds and absolute cells with the prior dense policy and the shipped exploration policy. */
class ExplorationRarityTest {
    private val content = canonicalGameplayContent

    @Test
    fun theSameExploredAreaHoldsSubstantiallyFewerDungeons() {
        val current = content.structures
        val previous = current.copy(
            minimumDistance = 4_000f, fullChanceDistance = 10_000f, maximumChance = 0.4f,
            templatesPerRun = StructureTemplateId.entries.size, secretTemplateChance = 1f,
        )
        var before = 0
        var after = 0
        for (seed in 1..32) {
            val encountered = HashSet<StructureTemplateId>()
            for (y in -10 until 10) for (x in -10 until 10) {
                if (placeStructure(previous, seed, WorldLayer.BASE, x, y) != null) before++
                val site = placeStructure(current, seed, WorldLayer.BASE, x, y) ?: continue
                after++
                if (!current.template(site.template).secret) encountered += site.template
                assertEquals(site, placeStructure(current, seed, WorldLayer.BASE, x, y), "same cell remains stable")
            }
            assertTrue(encountered.size <= 3, "seed $seed cannot expose every dungeon type")
        }
        assertTrue(after > 0)
        assertTrue(after < before * 0.35, "density before=$before after=$after")
        println("EXPLORATION structures seeds=32 cellsPerSeed=400 before=$before after=$after")
    }

    @Test
    fun seededDungeonPoolsVaryAndKeepEveryTypeAvailableAcrossRuns() {
        val policy = content.structures
        val ordinary = policy.templates.filterNot { it.secret }.map { it.id }
        val masks = HashSet<Int>()
        val all = HashSet<StructureTemplateId>()
        var secretRuns = 0
        for (seed in 1..256) {
            val mask = structureTemplateMask(policy, seed)
            masks += mask
            val selected = ordinary.filter { mask and (1 shl it.ordinal) != 0 }
            assertEquals(3, selected.size)
            all += selected
            if (mask and (1 shl StructureTemplateId.ARCHITECTS_DOODLE.ordinal) != 0) secretRuns++
        }
        assertEquals(ordinary.toSet(), all)
        assertTrue(masks.size >= 15, "many combinations occur across seeds: ${masks.size}")
        assertTrue(secretRuns in 25..80, "Doodle is available only in some seeds: $secretRuns")

        val onlyVault = policy.copy(templates = policy.templates.map {
            it.copy(weight = if (it.id == StructureTemplateId.VAULT) 1f else 0f)
        }.toImmutableList())
        for (seed in 1..16) assertEquals(1 shl StructureTemplateId.VAULT.ordinal, structureTemplateMask(onlyVault, seed))
        println("EXPLORATION dungeonPools=${masks.size} doodleSeeds=$secretRuns/256")
    }

    @Test
    fun cachesAndSecretsAreRarerInTheSameCellsAndAbsentSecretsCannotBeRerolledByTravel() {
        val before = IntArray(content.sites.ambient.rules.size)
        val after = IntArray(before.size)
        for (seed in 1..64) {
            val state = MutableGameState(content, seed)
            val streams = listOf(state.gameplayRandom.snapshot(), state.siteRandom.snapshot(), state.spawnRandom.snapshot())
            content.sites.ambient.rules.forEachIndexed { index, rule ->
                val oldRule = ambientMechanicsContent.sites.ambient.rules.single { it.kind == rule.kind }
                for (y in 10..29) for (x in -30..-11) {
                    if (state.ambientCandidate(oldRule, x, y) != null) before[index]++
                    val candidate = state.ambientCandidate(rule, x, y)
                    if (candidate != null) after[index]++
                    if (!ambientRuleAvailable(rule, seed)) assertNull(candidate)
                    assertEquals(candidate, state.ambientCandidate(rule, x, y))
                }
            }
            assertEquals(streams, listOf(state.gameplayRandom.snapshot(), state.siteRandom.snapshot(), state.spawnRandom.snapshot()))
        }
        content.sites.ambient.rules.forEachIndexed { index, rule ->
            assertTrue(after[index] > 0, "${rule.kind} remains discoverable")
            assertTrue(after[index] < before[index] * 0.4, "${rule.kind}: before=${before[index]} after=${after[index]}")
            assertFalse(ambientRuleAvailable(rule.copy(runChance = 0f), 1))
            assertTrue(ambientRuleAvailable(rule.copy(runChance = 1f), 1))
            println("EXPLORATION ${rule.kind} seeds=64 cellsPerSeed=400 before=${before[index]} after=${after[index]}")
        }
    }

    @Test
    fun farTravelRevealsASigilOnlyInSelectedSeedsAndNeverRerolls() {
        var offered = 0
        for (seed in 1..64) {
            val state = MutableGameState(content, seed).apply {
                startRun()
                coreX = content.sites.secrets.farTravelDistance
                coreY = 0f
            }
            state.updateSecretTrackers(0f)
            assertTrue(state.hasSecret(RunSecretFlag.FAR_TRAVEL))
            val expected = farSigilAvailable(seed, content.sites.secrets.farSigilRunChance)
            assertEquals(expected, state.secretSigil != null)
            if (expected) offered++
            state.secretSigil = null
            state.coreX += 50_000f
            state.updateSecretTrackers(0f)
            assertNull(state.secretSigil, "travel never rerolls availability")
        }
        assertTrue(offered in 6..26, "roughly a quarter of qualifying runs: $offered")
        assertFalse(farSigilAvailable(1, 0f))
        assertTrue(farSigilAvailable(1, 1f))
        println("EXPLORATION farSigil=$offered/64 (previously64/64)")
    }

    @Test
    fun threeSiteWindowsLeaveBothDimensionEntrancesPossibleAndSignalsAreOccasional() {
        val windows = content.sites.windows
        val times = generateSequence(windows.firstOfferAt) { it + windows.offerInterval }.takeWhile { it <= windows.lastOfferAt }.toList()
        assertEquals(listOf(45f, 225f, 405f), times)
        val targets = HashSet<WorldLayer>()
        for (seed in 1..64) {
            val state = MutableGameState(content, seed).apply { startRun() }
            times.forEachIndexed { index, time -> state.claimPortalForWindow(index, time)?.let { targets += it.target } }
            assertTrue(state.dimensionRun.portalOffers <= 2)
        }
        assertEquals(setOf(WorldLayer.ZENITH, WorldLayer.NADIR), targets)
        val quiet = content.sites.ambient.rules.single { it.kind == SiteKind.QUIET_SIGNAL }
        assertTrue((1..64).any { ambientRuleAvailable(quiet, it) }, "the Archive's conditional entrance remains available")
        assertEquals(1, content.variety.signals.perRun)
        println("EXPLORATION timedSiteWindows=3(previously6) signals=1(previously3)")
    }

    @Test
    fun lateEntryAndFullLengthTrialsStillUnlockTheArchiveInEitherDimensionOrder() {
        // Keep shipped rift chance and clocks; force boss objectives so completion goes through the real kill hook.
        val bossTrials = content.withDimensions {
            copy(definitions = definitions.map { definition ->
                val objective = when (definition.layer) {
                    WorldLayer.ZENITH -> DimensionObjective.JUDGEMENT
                    WorldLayer.NADIR -> DimensionObjective.PYRE
                    else -> null
                }
                if (objective == null) definition else definition.copy(objectives = immutableListOf(DimensionObjectiveWeight(objective, 1f)))
            }.toImmutableList())
        }
        val windows = content.sites.windows
        val times = (1..2).map { windows.firstOfferAt + it * windows.offerInterval }
        for (firstLayer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR)) {
            val seed = (1..256).first { candidate ->
                val probe = MutableGameState(bossTrials, candidate).apply { startRun() }
                probe.claimPortalForWindow(1, times[0])?.target == firstLayer &&
                    probe.claimPortalForWindow(2, times[1]) != null
            }
            val state = MutableGameState(bossTrials, seed).apply { startRun() }
            times.forEachIndexed { index, scheduledAt ->
                val rift = state.offerWindowForTesting(index + 1).single { it.kind.family == SiteFamily.PORTAL }
                // Use almost the full offer lifetime before entering, then almost the entire allowed visit.
                state.elapsed = scheduledAt + windows.offerLifetime - 0.01f
                state.flyThroughForTesting(rift.x, rift.y)
                state.updateSites(0f)
                val visit = assertNotNull(state.dimensionRun.visit)
                if (index == 0) assertEquals(firstLayer, visit.layer)
                state.elapsed += content.dimensions.objectives.bossDelaySeconds
                state.updateDimension(0f)
                val boss = state.enemies.single { it.id == state.dimensionRun.visit?.bossId }
                state.elapsed = visit.endsAt - 1f
                state.onEnemyKilled(boss)
                assertTrue(assertNotNull(state.dimensionRun.visit).completed)
                val exit = assertNotNull(state.dimensionRun.visit?.exitPortal)
                state.elapsed += 0.5f
                state.flyThroughForTesting(exit.x, exit.y)
                state.updateDimension(0f)
                assertEquals(WorldLayer.BASE, state.worldLayer)
                assertNull(state.dimensionRun.visit)
            }
            assertEquals(WorldLayer.ZENITH.bit or WorldLayer.NADIR.bit, state.dimensionRun.completedMask)
            assertTrue(state.elapsed <= content.dimensions.latestGlitchRiftAt(content.tempo.bossAtSeconds))
            val archive = assertNotNull(state.dimensionRun.glitchPortal, "both full trials must finish before the Archive deadline")
            state.elapsed = archive.expiresAt - 0.01f
            state.flyThroughForTesting(archive.x, archive.y)
            state.updateDimension(0f)
            assertEquals(WorldLayer.ARCHIVE, state.worldLayer)
            val archiveVisit = assertNotNull(state.dimensionRun.visit)
            assertEquals(content.dimensions.definition(WorldLayer.ARCHIVE).visitSeconds, archiveVisit.endsAt - state.elapsed, 0.001f)
            assertTrue(archiveVisit.endsAt <= content.tempo.bossAtSeconds - content.dimensions.exitMarginSeconds)
        }
    }
}
