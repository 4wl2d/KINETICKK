// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Window kinds: two draws from the site stream by weight, positions and every other stream untouched. */
class SiteSelectionTest {
    private val design = canonicalGameplayContent.withWindowWeights(DesignWindowWeights)

    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 731_991) =
        MutableGameState(content, seed).apply { startRun(); enemies.clear() }

    /** A different Core pose per window: even windows head along the velocity, odd ones along the aim. */
    private fun MutableGameState.pose(index: Int) {
        coreX = 300f * index - 700f
        coreY = 150f - 200f * index
        if (index % 2 == 0) {
            velocityX = 420f * cos(index.toFloat()); velocityY = 420f * sin(index.toFloat())
        } else {
            velocityX = 12f; velocityY = -5f
            lastAimDirectionX = cos(index * 1.3f); lastAimDirectionY = sin(index * 1.3f)
        }
    }

    /** The 0.5.0 placement: heading ∓ 1 rad at 560 u, moved clear of obstacles. */
    private fun MutableGameState.releasedPositions(): List<Pair<Float, Float>> {
        val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
        return List(2) { side ->
            val angle = heading + if (side == 0) -1.0f else 1.0f
            clearPointOfInterestSite(coreX + cos(angle) * 560f, coreY + sin(angle) * 560f)
        }
    }

    @Test
    fun windowKindsComeFromTheSiteStreamAndKeepTheirPositions() {
        for (seed in listOf(1, 2_026, 731_991, -77_001)) {
            val drawn = game(design, seed)
            val anomalies = game(canonicalGameplayContent.withAnomalyWindowsOnly(), seed)
            val reference = CloneableXorWowRandom(seed xor MutableGameState.SITE_SEED_SALT)
            repeat(6) { index ->
                drawn.pose(index)
                anomalies.pose(index)
                val expected = drawn.releasedPositions()
                val rewardStream = drawn.rewardRandom.snapshot()
                val spawnStream = drawn.spawnRandom.snapshot()
                val gameplayStream = drawn.gameplayRandom.snapshot()
                val pair = drawn.offerWindowForTesting(index)
                val anomalyPair = anomalies.offerWindowForTesting(index)

                assertEquals(expected, pair.map { it.x to it.y }, "seed $seed window $index keeps the 0.5.0 positions")
                assertEquals(anomalyPair.map { it.x to it.y }, pair.map { it.x to it.y }, "kinds never move a site")
                assertEquals(listOf(index * 2L, index * 2L + 1L), pair.map { it.id })
                val expiresAt = drawn.elapsed + drawn.content.sites.windows.offerLifetime
                assertTrue(pair.all { it.expiresAt == expiresAt && !it.active })
                assertEquals(rewardStream, drawn.rewardRandom.snapshot())
                assertEquals(spawnStream, drawn.spawnRandom.snapshot())
                assertEquals(gameplayStream, drawn.gameplayRandom.snapshot())
                repeat(2) { reference.nextFloat() }
                assertEquals(reference.snapshot(), drawn.siteRandom.snapshot(), "exactly two site draws per window")
            }
        }

        // A window that is missed or finds a pair still offered is consumed without a draw.
        val skipped = game(design)
        val stream = skipped.siteRandom.snapshot()
        skipped.elapsed = 45f + skipped.content.sites.windows.offerLifetime
        skipped.updateSites(0.01f)
        assertTrue(skipped.sites.isEmpty())
        assertEquals(1, skipped.nextSiteWindowIndex)
        skipped.sites = listOf(SiteState(0L, SiteKind.RESONANT_CIRCUIT, 4_000f, 0f, 200f))
        skipped.elapsed = 165f
        skipped.updateSites(0.01f)
        assertEquals(2, skipped.nextSiteWindowIndex)
        assertEquals(1, skipped.sites.size)
        assertEquals(stream, skipped.siteRandom.snapshot())
    }

    @Test
    fun aPairNeverRepeatsAKind() {
        for (seed in 0 until 200) {
            val game = game(design, seed)
            repeat(6) { index ->
                val pair = game.offerWindowForTesting(index)
                assertNotEquals(pair[0].kind, pair[1].kind, "seed $seed window $index")
            }
        }
    }

    /**
     * The drawn pair of every window for run seeds 0 until [seeds] (the draw alone: a rift claim may replace a side
     * of the offered pair afterwards).
     */
    private fun drawnPairs(content: GameplayContentSnapshot, seeds: Int, rebirthLevel: Int = 0): List<List<Pair<SiteKind, SiteKind>>> {
        val game = game(content).apply { this.rebirthLevel = rebirthLevel }
        val windows = content.sites.windows
        return List(6) { index ->
            val scheduledAt = windows.firstOfferAt + index * windows.offerInterval
            List(seeds) { seed ->
                game.siteRandom = CloneableXorWowRandom(seed xor MutableGameState.SITE_SEED_SALT)
                game.rollWindowKinds(scheduledAt)
            }
        }
    }

    @Test
    fun theSecondDrawDownWeightsTheFirstKindsFamily() {
        val weights = SiteKind.entries.filter { it.family == SiteFamily.ANOMALY }.associateWith { 1f } +
            (SiteKind.GATE_RACE to 1f)
        fun pairs(sameFamilyWeight: Float): List<Pair<SiteKind, SiteKind>> {
            val base = canonicalGameplayContent.withWindowWeights(weights)
            return drawnPairs(base.copy(sites = base.sites.copy(sameFamilyWeight = sameFamilyWeight)), 100).flatten()
        }
        // Weight 0: a pair is always one anomaly and the race, whichever comes first.
        val apart = pairs(0f)
        assertTrue(apart.all { (first, second) -> (first.family == SiteFamily.ANOMALY) != (second.family == SiteFamily.ANOMALY) })
        assertTrue(apart.any { it.first == SiteKind.GATE_RACE } && apart.any { it.second == SiteKind.GATE_RACE })
        // Weight 1: two anomalies may pair up.
        assertTrue(pairs(1f).any { (first, second) -> first.family == SiteFamily.ANOMALY && second.family == SiteFamily.ANOMALY })
    }

    @Test
    fun ineligibleKindsAreNeverDrawn() {
        val definitions = design.sites.definitions.map {
            when (it.kind) {
                SiteKind.ZENITH_RIFT -> it.copy(windowWeight = 50f)
                SiteKind.SLALOM_TRIAL -> it.copy(windowWeight = 50f, minimumRebirth = 3)
                else -> it
            }
        }
        val content = design.copy(sites = design.sites.copy(definitions = definitions.toImmutableList()))
        val drawn = drawnPairs(content, 300).map { window -> window.flatMap { listOf(it.first, it.second) }.toSet() }
        val all = drawn.flatten().toSet()
        assertTrue(all.none { it.family == SiteFamily.PORTAL }, "a rift is never drawn, only claimed")
        assertTrue(SiteKind.SLALOM_TRIAL !in all, "a kind above the run's Rebirth tier is never drawn")
        assertTrue(drawnPairs(content, 300, rebirthLevel = 3).flatten().any { SiteKind.SLALOM_TRIAL in it.toList() })
        // The bounty starts at 160 s and keeps a 90 s reserve before the Architect: windows 165..525 s only.
        assertTrue(SiteKind.BOUNTY_ARENA !in drawn[0] && SiteKind.BOUNTY_ARENA !in drawn[5])
        assertTrue((1..4).all { SiteKind.BOUNTY_ARENA in drawn[it] })
        // The production offer follows the same rule.
        val offered = game(content, seed = 2_026)
        repeat(6) { index -> assertTrue(offered.offerWindowForTesting(index).none { it.kind == SiteKind.SLALOM_TRIAL }) }
    }

    @Test
    fun aDisabledPolicyOffersNothing() {
        val content = canonicalGameplayContent.copy(sites = canonicalGameplayContent.sites.copy(enabled = false))
        val game = game(content)
        val stream = game.siteRandom.snapshot()
        game.sites = listOf(SiteState(0L, SiteKind.RESONANT_CIRCUIT, 4_000f, 0f, 200f))
        repeat(6) { index -> assertTrue(game.offerWindowForTesting(index).isEmpty()) }
        game.elapsed = 45f
        game.nextSiteWindowIndex = 0
        game.updateSites(0.01f)
        assertTrue(game.sites.isEmpty())
        assertEquals(0, game.nextSiteWindowIndex)
        assertNull(game.secretSigil)
        assertEquals(stream, game.siteRandom.snapshot())
    }

    @Test
    fun refusedActivationLeavesThePairOffered() {
        val game = game(canonicalGameplayContent.withAnomalyWindowsOnly())
        val offered = game.offerWindowForTesting(0)
        assertEquals(2, offered.size)
        fun flyThrough(site: SiteState) {
            game.previousCoreX = site.x - 120f; game.previousCoreY = site.y
            game.coreX = site.x + 120f; game.coreY = site.y
            game.updateSites(0.01f)
        }
        game.secretSigil = SiteState(16L, SiteKind.FAR_SIGIL, -5_000f, 0f, Float.POSITIVE_INFINITY, active = true)
        assertTrue(game.engagementActive())
        flyThrough(offered[0])
        assertEquals(offered, game.sites, "another engagement refuses the activation and keeps both offers")
        game.secretSigil = null
        flyThrough(offered[0])
        assertEquals(1, game.sites.size)
        assertTrue(game.sites.single().active)
        assertEquals(offered[0].kind, game.sites.single().kind)
    }

    @Test
    fun sameSeedSameKinds() {
        fun kinds(seed: Int): List<SiteKind> {
            val game = game(design, seed)
            return (0 until 6).flatMap { index -> game.pose(index); game.offerWindowForTesting(index).map { it.kind } }
        }
        assertEquals(kinds(2_026), kinds(2_026))
        assertTrue((1..20).map(::kinds).toSet().size > 1, "the run seed reshapes the kinds")
    }

    @Test
    fun aClaimedRiftTakesItsSideAndNamesTheRift() {
        val plain = game()
        val claimed = game()
        plain.offerWindowPair(0, 45f, SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, null)
        assertEquals(SiteNotice.TWO_ANOMALIES.english, plain.message)

        claimed.offerWindowPair(0, 45f, SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, PortalClaim(WorldLayer.ZENITH, 1))
        assertEquals(listOf(SiteKind.RESONANT_CIRCUIT, SiteKind.ZENITH_RIFT), claimed.sites.map { it.kind })
        assertEquals(plain.sites.map { it.x to it.y }, claimed.sites.map { it.x to it.y }, "a rift keeps the window's positions")
        assertEquals(DimensionNotices.RIFT_DETECTED, claimed.message)
        assertEquals(2.5f, claimed.messageTime)

        claimed.offerWindowPair(1, 165f, SiteKind.GATE_RACE, SiteKind.COLLAPSING_ORBIT, PortalClaim(WorldLayer.NADIR, 0))
        assertEquals(listOf(SiteKind.NADIR_RIFT, SiteKind.COLLAPSING_ORBIT), claimed.sites.map { it.kind })
        assertEquals(DimensionNotices.RIFT_DETECTED, claimed.message)

        // Realspace and the Archive are never window rifts; the drawn pair stands.
        claimed.offerWindowPair(2, 285f, SiteKind.GATE_RACE, SiteKind.COLLAPSING_ORBIT, PortalClaim(WorldLayer.ARCHIVE, 0))
        assertEquals(listOf(SiteKind.GATE_RACE, SiteKind.COLLAPSING_ORBIT), claimed.sites.map { it.kind })
        assertEquals(SiteNotice.TWO_SITES.english, claimed.message)
    }
}
