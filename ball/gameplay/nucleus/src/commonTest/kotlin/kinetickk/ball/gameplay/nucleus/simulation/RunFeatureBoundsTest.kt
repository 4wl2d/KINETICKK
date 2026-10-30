// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunFeatureBoundsTest {
    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent) = MutableGameState(content).apply {
        startRun()
        enemies.clear()
    }

    @Test
    fun activationClosesAlternativeAndNeverRetainsTwoActiveTrials() {
        // Anomaly windows only, so whichever kind the site stream draws can start its trial.
        val state = game(canonicalGameplayContent.withAnomalyWindowsOnly())
        state.elapsed = 45f
        state.updateSites(0.01f)
        val offered = state.sites
        assertEquals(2, offered.size)
        state.activateWindowSite(offered.first())
        assertEquals(1, state.sites.size)
        assertTrue(state.sites.single().active)
        assertEquals(offered.first().kind, state.sites.single().kind)
    }

    @Test
    fun aRaceActivationClosesItsAlternativeAndIsTheOnlyTrial() {
        // A race and an anomaly share every window, so either side starts its own trial.
        val content = canonicalGameplayContent.withWindowWeights(mapOf(SiteKind.GATE_RACE to 1f, SiteKind.RESONANT_CIRCUIT to 1f))
        val state = game(content)
        state.elapsed = 45f
        state.updateSites(0.01f)
        val offered = state.sites
        assertEquals(setOf(SiteKind.GATE_RACE, SiteKind.RESONANT_CIRCUIT), offered.map { it.kind }.toSet())
        val race = offered.single { it.kind == SiteKind.GATE_RACE }
        val other = offered.single { it.kind != SiteKind.GATE_RACE }
        state.activateWindowSite(race)
        assertEquals(race.id, state.sites.single().id)
        assertTrue(state.sites.single().active)

        // Flying through the closed alternative never starts it: only the race (if still on course) remains.
        state.previousCoreX = other.x - 50f
        state.previousCoreY = other.y
        state.coreX = other.x
        state.coreY = other.y
        state.updateSites(0.01f)
        assertTrue(state.sites.all { it.id == race.id && it.active })
    }

    @Test
    fun aChallengeActivationClosesItsAlternativeAndIsTheOnlyTrial() {
        // A Ram rite and an anomaly share every window; the rite owns its eight foes and nothing else starts.
        val content = canonicalGameplayContent.withWindowWeights(mapOf(SiteKind.RAM_TRIAL to 1f, SiteKind.RESONANT_CIRCUIT to 1f))
        val state = game(content)
        state.elapsed = 45f
        state.updateSites(0.01f)
        val offered = state.sites
        assertEquals(setOf(SiteKind.RAM_TRIAL, SiteKind.RESONANT_CIRCUIT), offered.map { it.kind }.toSet())
        val rite = offered.single { it.kind == SiteKind.RAM_TRIAL }
        val other = offered.single { it.kind != SiteKind.RAM_TRIAL }
        state.activateWindowSite(rite)
        assertEquals(rite.id, state.sites.single().id)
        assertTrue(state.sites.single().active)
        assertEquals(state.enemies.map { it.id }, state.engagementFoeIds())

        state.previousCoreX = other.x - 50f
        state.previousCoreY = other.y
        state.coreX = other.x
        state.coreY = other.y
        state.updateSites(0.01f)
        assertTrue(state.sites.all { it.id == rite.id && it.active })
    }

    @Test
    fun aBountyActivationClosesItsAlternativeAndIsTheOnlyTrial() {
        // A bounty and an anomaly share the 165 s window (two anomalies keep every window drawable); the bounty owns its
        // boss and nothing else starts.
        val content = canonicalGameplayContent.withWindowWeights(
            mapOf(SiteKind.BOUNTY_ARENA to 1f, SiteKind.RESONANT_CIRCUIT to 1f, SiteKind.SEALED_ANOMALY to 1f),
        )
        val (state, offered) = (1..50).asSequence()
            .map { seed -> MutableGameState(content, seed).apply { startRun(); enemies.clear() } }
            .map { it to it.offerWindowForTesting(1) }
            .first { (_, pair) -> pair.any { it.kind == SiteKind.BOUNTY_ARENA } }
        val bounty = offered.single { it.kind == SiteKind.BOUNTY_ARENA }
        val other = offered.single { it.kind != SiteKind.BOUNTY_ARENA }
        state.activateWindowSite(bounty)
        assertEquals(bounty.id, state.sites.single().id)
        assertTrue(state.sites.single().active)
        assertEquals(state.enemies.map { it.id }, state.engagementFoeIds())

        state.previousCoreX = other.x - 50f
        state.previousCoreY = other.y
        state.coreX = other.x
        state.coreY = other.y
        state.updateSites(0.01f)
        assertTrue(state.sites.all { it.id == bounty.id && it.active })
    }

    @Test
    fun tenthDirectedRewardFitsAndEleventhSalvagesWithoutTouchingSource() {
        val source = game()
        val bound = source.content.sites.maxPendingDirectedRewards
        assertEquals(10, bound)
        fun completingCircuit() = listOf(SiteState(
            0L, SiteKind.RESONANT_CIRCUIT, source.coreX, source.coreY, 85f,
            active = true, step = 2,
        ))
        source.pendingDirectedRewards = List(bound - 1) { DirectedReward.WEAPON }
        source.sites = completingCircuit()
        source.previousCoreX = source.coreX
        source.previousCoreY = source.coreY
        source.updateSites(0.01f)
        assertEquals(bound, source.pendingDirectedRewards.size)
        assertEquals(DirectedReward.WEAPON, source.pendingDirectedRewards.last())
        assertEquals(SiteNotice.ANOMALY_RESOLVED.english, source.message)
        val sourceMatter = source.runMatter + source.matterFraction.toDouble()

        // The eleventh completion refuses the queue and salvages 8 Matter (after the run's gain) instead of crashing.
        val candidate = source.copyForReduction()
        candidate.sites = completingCircuit()
        candidate.updateSites(0.01f)
        assertEquals(bound, candidate.pendingDirectedRewards.size)
        assertTrue(candidate.sites.isEmpty())
        assertEquals(SiteNotice.REWARD_SALVAGED.english, candidate.message)
        assertEquals(
            sourceMatter + 8.0 * candidate.matterGain,
            candidate.runMatter + candidate.matterFraction.toDouble(),
            1e-3,
        )

        // The candidate's salvage never reaches the source snapshot.
        assertEquals(bound, source.pendingDirectedRewards.size)
        assertTrue(source.sites.isEmpty())
        assertEquals(sourceMatter, source.runMatter + source.matterFraction.toDouble())
        assertEquals(SiteNotice.ANOMALY_RESOLVED.english, source.message)
    }

    @Test
    fun fourthSeparatedDashCollapsesInsteadOfGrowingRetainedLattice() {
        val state = game()
        state.coreShape = CoreShape.TESSERACT
        repeat(4) { index ->
            state.coreX = index * 400f
            state.beginCharacterDash()
            state.previousCoreX = state.coreX
            state.coreX += 150f
            state.velocityX = 600f
            state.dashPhaseTime = 0.24f
            state.updateCharacterRuntime(0.01f)
            assertEquals(if (index == 3) 0 else index + 1, state.characterRuntime.latticeCount)
        }
        assertEquals(0, state.latticeNodeCount)
    }
}
