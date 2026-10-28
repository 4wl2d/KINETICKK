// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState
import kinetickk.ball.gameplay.nucleus.model.Ember
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Rifts, entry, the visit clock and the return: the run state of dimension visits. */
class DimensionSystemTest {
    private val policy = canonicalGameplayContent.dimensions
    private val bossAt = canonicalGameplayContent.tempo.bossAtSeconds
    private val returnBy = bossAt - policy.exitMarginSeconds

    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 60_601) =
        MutableGameState(content, seed).apply {
            startRun()
            enemies.clear()
            elapsed = 200f
        }

    private fun MutableGameState.streams() =
        listOf(gameplayRandom.snapshot(), rewardRandom.snapshot(), spawnRandom.snapshot(), siteRandom.snapshot())

    /** One fixed step of the real loop; an open choice takes its first card first, so the clock keeps running. */
    private fun MutableGameState.runningStep() {
        var guard = 0
        while (phase == GamePhase.CHOICE && guard++ < 8) choose(0)
        check(phase == GamePhase.RUNNING) { "the run left RUNNING ($phase) at $elapsed s" }
        simulateStep(MutableGameState.FIXED_STEP)
    }

    private fun MutableGameState.exitThroughItsRift() {
        val exit = checkNotNull(dimensionRun.visit?.exitPortal)
        flyThroughForTesting(exit.x, exit.y)
        updateDimension(MutableGameState.FIXED_STEP)
    }

    @Test
    fun riftClaimsDrawNothingFromTheRunStreams() {
        val always = canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f) }
        val never = canonicalGameplayContent.withDimensions { copy(portalWindowChance = 0f) }
        for (seed in listOf(1, 2_026, 731_991, -77_001)) {
            val claimed = game(always, seed)
            val plain = game(never, seed)
            val riftWindows = mutableListOf<Int>()
            repeat(6) { index ->
                val pair = claimed.offerWindowForTesting(index)
                val plainPair = plain.offerWindowForTesting(index)
                assertEquals(plain.streams(), claimed.streams(), "seed $seed window $index: a claim draws nothing")
                assertEquals(plainPair.map { it.x to it.y }, pair.map { it.x to it.y }, "a rift keeps the window's positions")
                if (pair.any { it.kind.family == SiteFamily.PORTAL }) riftWindows += index
            }
            // Window 0 is before the first rift window; the cap of three ends the claims after windows 1..3.
            assertEquals(listOf(1, 2, 3), riftWindows, "seed $seed")
            assertEquals(policy.maxPortalOffersPerRun, claimed.dimensionRun.portalOffers)
            assertEquals(0, plain.dimensionRun.portalOffers)
        }
        // The claim alone: no run stream moves, whatever it answers.
        val direct = game(always)
        val streams = direct.streams()
        repeat(6) { index -> direct.claimPortalForWindow(index, 45f + index * 120f) }
        assertEquals(streams, direct.streams())
    }

    @Test
    fun aWindowHostsARiftOnlyWhileItsVisitEndsBeforeTheExitMargin() {
        val game = game(canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f) })
        fun claim(offerId: Int, scheduledAt: Float): PortalClaim? {
            game.dimensionRun = game.dimensionRun.copy(portalOffers = 0)
            return game.claimPortalForWindow(offerId, scheduledAt)
        }
        assertNull(claim(0, 45f), "the opening window stays a plain window")
        assertNotNull(claim(1, 165f))
        // The latest window whose rift visit, entered as the offer closes, still ends 60 s before the Architect.
        val latest = returnBy - policy.longestWindowVisitSeconds - canonicalGameplayContent.sites.windows.offerLifetime
        assertEquals(545f, latest)
        assertNotNull(claim(4, latest))
        assertNull(claim(4, latest.nextAbove()))
        assertNull(claim(5, 645f))

        // Only in realspace, only while enabled, and at most three per run.
        game.worldLayer = WorldLayer.ZENITH
        assertNull(claim(1, 165f))
        game.worldLayer = WorldLayer.BASE
        val disabled = game(canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f, enabled = false) })
        assertNull(disabled.claimPortalForWindow(1, 165f))
        game.dimensionRun = game.dimensionRun.copy(portalOffers = policy.maxPortalOffersPerRun - 1)
        assertNotNull(game.claimPortalForWindow(2, 285f))
        assertEquals(policy.maxPortalOffersPerRun, game.dimensionRun.portalOffers)
        assertNull(game.claimPortalForWindow(3, 405f))
        assertEquals(policy.maxPortalOffersPerRun, game.dimensionRun.portalOffers)
    }

    @Test
    fun aRiftLeadsOnlyIntoAWindowDimensionNotEnteredYet() {
        val game = game(canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f) })
        fun targets(visitedMask: Int): Set<WorldLayer?> = (1..4).flatMap { offerId ->
            listOf(2_026, 7, 731_991, -5, 88_001).map { seed ->
                val run = game(canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f) }, seed)
                run.dimensionRun = run.dimensionRun.copy(visitedMask = visitedMask)
                run.claimPortalForWindow(offerId, 45f + offerId * 120f)?.target
            }
        }.toSet()
        assertEquals(setOf<WorldLayer?>(WorldLayer.ZENITH, WorldLayer.NADIR), targets(0))
        assertEquals(setOf<WorldLayer?>(WorldLayer.NADIR), targets(WorldLayer.ZENITH.bit))
        assertEquals(setOf<WorldLayer?>(WorldLayer.ZENITH), targets(WorldLayer.NADIR.bit or WorldLayer.ARCHIVE.bit))
        assertEquals(setOf<WorldLayer?>(null), targets(WorldLayer.ZENITH.bit or WorldLayer.NADIR.bit))
        game.dimensionRun = game.dimensionRun.copy(visitedMask = WorldLayer.ZENITH.bit or WorldLayer.NADIR.bit)
        assertNull(game.claimPortalForWindow(1, 165f))
        assertEquals(0, game.dimensionRun.portalOffers, "a window without a target claims nothing")
    }

    @Test
    fun windowRiftsFollowTheShippedChanceAndTheSeed() {
        var claimed = 0
        val targets = mutableSetOf<WorldLayer>()
        val sides = mutableSetOf<Int>()
        val samples = 4_000
        for (seed in 0 until samples) {
            val game = MutableGameState(canonicalGameplayContent, seed)
            val claim = game.claimPortalForWindow(1, 165f) ?: continue
            claimed++
            targets += claim.target
            sides += claim.side
            assertEquals(claim, MutableGameState(canonicalGameplayContent, seed).claimPortalForWindow(1, 165f), "seed $seed")
        }
        val share = claimed.toFloat() / samples
        assertTrue(share in 0.27f..0.33f, "rift share $share for chance ${policy.portalWindowChance}")
        assertEquals(setOf(WorldLayer.ZENITH, WorldLayer.NADIR), targets)
        assertEquals(setOf(0, 1), sides)
    }

    @Test
    fun flyingThroughARiftEntersItsDimensionAndClearsTheArrival() {
        val game = game()
        game.elapsed = 170f
        game.nextEliteAt = 400f
        game.addEnemyForTesting(300f, 0f, type = EnemyType.ELITE)
        game.addEnemyForTesting(-300f, 120f)
        val tag = encounterTag(EncounterOwner.SITE, 3L)
        game.encounters = immutableListOf(EncounterState(tag, -300f, 120f, 300f, 2_400f, 1, 0, -1, 160f))
        game.addEnemyForTesting(-320f, 140f).ownerTag = tag
        game.projectiles += Projectile(x = 50f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 2f, hostile = true)
        game.projectiles += Projectile(x = 60f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 2f, hostile = false)
        game.pickups += Pickup(PickupType.KEY, 400f, 300f)
        game.totem = Totem(600f, 0f)
        val kills = game.kills
        val keys = game.keys
        val (x, y) = game.openPortalForTesting(WorldLayer.ZENITH)
        game.takeVisualFxCues()
        val streams = game.streams()

        game.flyThroughForTesting(x, y)
        game.updateSites(0.01f)

        assertEquals(WorldLayer.ZENITH, game.worldLayer)
        assertEquals(streams, game.streams(), "entry draws from no run stream (the director reset included)")
        assertEquals(WorldKeepOut(WorldLayer.ZENITH, game.worldOriginX + x, game.worldOriginY + y, 900f), game.worldKeepOut)
        assertTrue(game.obstacleField.size > 0)
        for (obstacle in game.obstacleField.obstacles) {
            assertEquals(WorldLayer.ZENITH.ordinal.toLong(), obstacle.id ushr 60, "the window is the Zenith's own")
            assertTrue(hypot(obstacle.x - x, obstacle.y - y) >= 900f + obstacle.radius - 0.01f, "the arrival is clear")
        }
        assertTrue(game.enemies.isEmpty())
        assertTrue(game.encounters.isEmpty(), "every encounter ends with the layer")
        assertEquals(kills, game.kills, "removed hostiles are no kills")
        assertEquals(listOf(false), game.projectiles.map { it.hostile }, "hostile shots go, the Core's stay")
        assertNull(game.totem)
        assertTrue(game.sites.isEmpty(), "the window's sites stay in realspace")
        assertEquals(listOf(x to y), game.pickups.map { it.x to it.y }, "pickups are drawn onto the Core")
        val visit = assertNotNull(game.dimensionRun.visit)
        assertEquals(170f + 75f, visit.endsAt)
        assertEquals(0, visit.serial)
        assertEquals(WorldLayer.ZENITH.bit, game.dimensionRun.visitedMask)
        assertTrue(game.dimensionVisitActive() && game.dimensionTouched() && game.engagementActive())
        assertEquals(DimensionNotices.entry(WorldLayer.ZENITH, visit.objective), game.message)
        assertTrue(VisualFxCue.ClearAll in game.takeVisualFxCues())
        assertTrue(game.pendingWorldDiscoveryMask and WorldDiscovery.ZENITH.bit != 0)
        assertEquals(SpawnDirectorPhase.BUILD.ordinal, game.directorPhase)
        assertEquals(game.elapsed + 10f, game.directorPhaseUntil)
        assertEquals(5f, game.spawnBudget)
        assertEquals(170f, game.nextEliteAt, "the displaced Elite is due at once and waits for realspace")

        game.runningStep()
        assertEquals(keys + 1, game.keys, "the drawn-in Key is collected with its usual effect")
        assertTrue(game.pickups.isEmpty())
        assertNull(game.totem, "no Totem appears inside a dimension")
    }

    @Test
    fun aBossClassEnemyOrTheClockKeepsARiftLocked() {
        val game = game()
        val herald = game.addEnemyForTesting(900f, 0f, type = EnemyType.HERALD)
        val (x, y) = game.openPortalForTesting(WorldLayer.NADIR)
        val offered = game.sites
        assertTrue(game.portalLocked(SiteKind.NADIR_RIFT))
        game.flyThroughForTesting(x, y)
        game.updateSites(0.01f)
        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertEquals(offered, game.sites, "a refused rift stays offered")
        assertTrue(herald in game.enemies)
        game.enemies.remove(herald)
        assertFalse(game.portalLocked(SiteKind.NADIR_RIFT))
        assertFalse(game.portalLocked(SiteKind.GATE_RACE), "only rifts lock")

        // At least the minimum visit must fit before the exit margin.
        val latestEntry = returnBy - policy.minimumVisitSeconds
        game.elapsed = latestEntry
        assertTrue(game.canEnterDimension(WorldLayer.NADIR))
        game.elapsed = latestEntry.nextAbove()
        assertFalse(game.canEnterDimension(WorldLayer.NADIR))
        assertFalse(game.enterDimension(WorldLayer.NADIR))
        assertEquals(WorldLayer.BASE, game.worldLayer)

        val other = game()
        assertFalse(other.canEnterDimension(WorldLayer.BASE))
        other.phase = GamePhase.PAUSED
        assertFalse(other.canEnterDimension(WorldLayer.ZENITH))
        other.phase = GamePhase.RUNNING
        other.bossSpawned = true
        assertFalse(other.canEnterDimension(WorldLayer.ZENITH))
        other.bossSpawned = false
        other.enterDimensionForTesting(WorldLayer.ZENITH)
        assertFalse(other.canEnterDimension(WorldLayer.NADIR), "one layer at a time")
        other.skipToVisitEndForTesting()
        other.updateDimension(MutableGameState.FIXED_STEP)
        assertFalse(other.canEnterDimension(WorldLayer.ZENITH), "each layer once per run")
        assertTrue(other.canEnterDimension(WorldLayer.NADIR))
        assertFalse(game(canonicalGameplayContent.withDimensions { copy(enabled = false) }).canEnterDimension(WorldLayer.ZENITH))
    }

    @Test
    fun theTimerPullsTheCoreBackWithTheSmallerRewardAndAQuietDirector() {
        val game = game()
        game.nextEliteAt = 500f
        game.enterDimensionForTesting(WorldLayer.ZENITH)
        game.addEnemyForTesting(400f, 0f)
        game.projectiles += Projectile(x = 50f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 2f, hostile = true)
        game.spawnBudget = 9f
        game.skipToVisitEndForTesting()
        assertEquals(275f, game.elapsed)
        val streams = game.streams()

        game.updateDimension(MutableGameState.FIXED_STEP)

        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertEquals(streams, game.streams())
        assertEquals(WorldKeepOut(WorldLayer.BASE, game.worldOriginX + game.coreX, game.worldOriginY + game.coreY, 900f), game.worldKeepOut)
        assertTrue(game.obstacleField.obstacles.all { it.id ushr 60 == 0L }, "realspace ids again")
        assertTrue(game.enemies.isEmpty(), "the dimension's enemies stay behind")
        assertTrue(game.projectiles.none { it.hostile })
        assertNull(game.dimensionRun.visit)
        assertEquals(immutableListOf(DimensionRewardGrant(WorldLayer.ZENITH, completed = false)), game.dimensionRun.pendingRewards)
        assertEquals(0, game.dimensionRun.completedMask)
        assertEquals(DimensionNotices.PULLED_BACK, game.message)
        assertEquals(SpawnDirectorPhase.BREATHER.ordinal, game.directorPhase)
        assertEquals(game.elapsed + 6f, game.directorPhaseUntil)
        assertEquals(3f, game.spawnBudget)
        assertEquals(500f, game.nextEliteAt, "a later Elite keeps its schedule")
        assertFalse(game.dimensionVisitActive())
        assertTrue(game.dimensionTouched())
    }

    @Test
    fun theExitRiftReturnsEarlyWithTheFullReward() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST)
        game.elapsed = 230f
        game.completeObjectiveForTesting()
        game.exitThroughItsRift()
        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertEquals(immutableListOf(DimensionRewardGrant(WorldLayer.NADIR, completed = true)), game.dimensionRun.pendingRewards)
        assertEquals(WorldLayer.NADIR.bit, game.dimensionRun.completedMask)
        assertEquals(DimensionNotices.REALSPACE, game.message)
        assertNull(game.dimensionRun.glitchPortal, "one completion opens nothing")
    }

    @Test
    fun aDisplacedEliteReturnsWithinEightSecondsOfTheReturn() {
        val game = game()
        game.nextEliteAt = 400f
        game.addEnemyForTesting(300f, 0f, type = EnemyType.ELITE)
        game.enterDimensionForTesting(WorldLayer.ZENITH)
        assertEquals(200f, game.nextEliteAt)
        // The layer holds every due Elite back.
        game.elapsed = 205f
        repeat(3) { game.spawnWave(MutableGameState.FIXED_STEP) }
        assertTrue(game.enemies.none { it.isElite })
        assertEquals(200f, game.nextEliteAt)
        game.elapsed = 210f
        game.completeObjectiveForTesting()
        game.exitThroughItsRift()
        assertEquals(210f + EXIT_ELITE_DELAY_SECONDS, game.nextEliteAt)
        game.elapsed = game.nextEliteAt
        game.spawnWave(MutableGameState.FIXED_STEP)
        assertTrue(game.enemies.any { it.isElite }, "the Elite arrives in realspace")

        // An Elite falling due during a visit also waits until eight seconds after the return.
        val due = game()
        due.nextEliteAt = 205f
        due.enterDimensionForTesting(WorldLayer.NADIR)
        val returnAt = checkNotNull(due.dimensionRun.visit).endsAt
        assertEquals(200f + due.content.dimensions.definition(WorldLayer.NADIR).visitSeconds, returnAt)
        due.skipToVisitEndForTesting()
        due.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(returnAt + EXIT_ELITE_DELAY_SECONDS, due.nextEliteAt)
    }

    @Test
    fun aHeraldDueDuringTheVisitArrivesAfterTheReturn() {
        val game = game()
        game.rebirthLevel = 2
        game.runMutatorMask = 1 shl RebirthMutator.HARDENED_ELITES.ordinal
        game.elapsed = 340f
        game.enterDimensionForTesting(WorldLayer.NADIR)
        game.elapsed = 370f
        repeat(3) { game.spawnWave(MutableGameState.FIXED_STEP) }
        assertEquals(0, game.heraldsSpawned)
        assertTrue(game.enemies.none { it.type == EnemyType.HERALD })
        game.skipToVisitEndForTesting()
        game.updateDimension(MutableGameState.FIXED_STEP)
        game.spawnWave(MutableGameState.FIXED_STEP)
        assertEquals(1, game.heraldsSpawned)
        assertTrue(game.enemies.any { it.type == EnemyType.HERALD })
    }

    private fun completedBoth(returnAt: Float): MutableGameState = game().apply {
        enterDimensionForTesting(WorldLayer.ZENITH)
        completeObjectiveForTesting()
        exitThroughItsRift()
        elapsed = returnAt - 30f
        enterDimensionForTesting(WorldLayer.NADIR)
        completeObjectiveForTesting()
        elapsed = returnAt
        exitThroughItsRift()
    }

    @Test
    fun theGlitchRiftOpensAfterBothCompletionsWhileTheArchiveCanStillEndInTime() {
        val latest = policy.latestGlitchRiftAt(bossAt)
        assertEquals(530f, latest)
        val opened = completedBoth(latest)
        val glitch = assertNotNull(opened.dimensionRun.glitchPortal)
        assertEquals(WorldLayer.ARCHIVE, glitch.target)
        assertEquals(latest + 30f, glitch.expiresAt)
        assertTrue(hypot(glitch.x - opened.coreX, glitch.y - opened.coreY) >= 700f - 0.01f, "700 u ahead or nudged further")
        assertEquals(DimensionNotices.ARCHIVE_STIRS, opened.message)
        assertNull(completedBoth(latest.nextAbove()).dimensionRun.glitchPortal)

        // A failed objective is no completion.
        val failed = game().apply {
            enterDimensionForTesting(WorldLayer.ZENITH)
            completeObjectiveForTesting()
            exitThroughItsRift()
            enterDimensionForTesting(WorldLayer.NADIR)
            skipToVisitEndForTesting()
            updateDimension(MutableGameState.FIXED_STEP)
        }
        assertNull(failed.dimensionRun.glitchPortal)

        // It closes at its expiry.
        val expiring = completedBoth(400f)
        expiring.elapsed = 430f
        expiring.updateDimension(MutableGameState.FIXED_STEP)
        assertNull(expiring.dimensionRun.glitchPortal)

        // Flying through enters the Archive once; its Matter is paid in realspace, unscaled.
        val portal = checkNotNull(opened.dimensionRun.glitchPortal)
        opened.flyThroughForTesting(portal.x, portal.y)
        opened.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(WorldLayer.ARCHIVE, opened.worldLayer)
        assertNull(opened.dimensionRun.glitchPortal)
        assertEquals(minOf(latest + 100f, returnBy), checkNotNull(opened.dimensionRun.visit).endsAt)
        assertEquals(DimensionObjective.ANTECEDENT, checkNotNull(opened.dimensionRun.visit).objective)
        val matter = opened.runMatter + opened.matterFraction
        opened.skipToVisitEndForTesting()
        opened.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(WorldLayer.BASE, opened.worldLayer)
        val failure = policy.definition(WorldLayer.ARCHIVE).reward.matterOnFailure
        assertEquals(failure * opened.matterGain, opened.runMatter + opened.matterFraction - matter, 0.001f)
        assertNull(opened.dimensionRun.glitchPortal, "the Archive opens once per run")
        assertFalse(opened.openArchiveRiftAt(opened.coreX + 300f, opened.coreY))
    }

    @Test
    fun theQuietSignalOpensTheGlitchRiftOnceAndOnlyInTime() {
        val game = game()
        game.elapsed = 300f
        assertTrue(game.openArchiveRiftAt(1_000f, 200f))
        val glitch = assertNotNull(game.dimensionRun.glitchPortal)
        assertEquals(WorldLayer.ARCHIVE, glitch.target)
        assertEquals(330f, glitch.expiresAt)
        assertEquals(DimensionNotices.ARCHIVE_STIRS, game.message)
        assertFalse(game.openArchiveRiftAt(-1_000f, 0f), "one glitch rift at a time")
        assertSame(glitch, game.dimensionRun.glitchPortal)

        val latest = policy.latestGlitchRiftAt(bossAt)
        assertTrue(game().apply { elapsed = latest }.openArchiveRiftAt(500f, 0f))
        assertFalse(game().apply { elapsed = latest.nextAbove() }.openArchiveRiftAt(500f, 0f))
        val inside = game().apply { enterDimensionForTesting(WorldLayer.ZENITH) }
        assertFalse(inside.openArchiveRiftAt(500f, 0f), "only in realspace")
        val visited = game().apply { dimensionRun = dimensionRun.copy(visitedMask = WorldLayer.ARCHIVE.bit) }
        assertFalse(visited.openArchiveRiftAt(500f, 0f))
        assertFalse(game(canonicalGameplayContent.withDimensions { copy(enabled = false) }).openArchiveRiftAt(500f, 0f))
    }

    @Test
    fun aQuietArenaClosesTheOpenRiftAndForgetsTheRunsDimensions() {
        val game = game()
        game.elapsed = 300f
        assertTrue(game.openArchiveRiftAt(game.coreX + 300f, game.coreY))
        val glitch = assertNotNull(game.dimensionRun.glitchPortal)
        game.dimensionRun = game.dimensionRun.copy(
            visitedMask = WorldLayer.ZENITH.bit,
            pendingRewards = immutableListOf(DimensionRewardGrant(WorldLayer.ZENITH, completed = true)),
        )

        game.quietArenaForTesting()
        assertSame(DimensionRunState.EMPTY, game.dimensionRun)
        game.flyThroughForTesting(glitch.x, glitch.y)
        game.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(WorldLayer.BASE, game.worldLayer, "an isolated encounter never falls into the Archive")
        assertNull(game.dimensionRun.visit)
    }

    @Test
    fun aVisitAlwaysEndsBeforeTheArchitect() {
        val latestEntry = returnBy - policy.minimumVisitSeconds
        for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
            for (entry in 45..650 step 5) {
                val game = game(seed = 90_001 + entry)
                game.elapsed = entry.toFloat()
                if (entry > latestEntry) {
                    assertFalse(game.enterDimension(layer), "$layer refuses an entry at $entry s")
                } else {
                    game.enterDimensionForTesting(layer)
                    val endsAt = checkNotNull(game.dimensionRun.visit).endsAt
                    assertTrue(endsAt <= returnBy, "$layer entered at $entry s ends at $endsAt s")
                    repeat(3) { game.runningStep() }
                    assertEquals(layer, game.worldLayer, "$layer at $entry s runs its visit")
                    game.elapsed = endsAt - 2f * MutableGameState.FIXED_STEP
                    repeat(4) {
                        game.runningStep()
                        if (game.elapsed >= endsAt) assertEquals(WorldLayer.BASE, game.worldLayer, "$layer at $entry s, ${game.elapsed} s")
                    }
                }
                // Every step from the return margin until the Architect arrives is in realspace.
                game.elapsed = maxOf(game.elapsed, returnBy - MutableGameState.FIXED_STEP)
                repeat(3) {
                    game.runningStep()
                    assertEquals(WorldLayer.BASE, game.worldLayer, "$layer at $entry s, ${game.elapsed} s")
                }
                game.elapsed = bossAt - 2f * MutableGameState.FIXED_STEP
                var steps = 0
                while (!game.bossSpawned && steps++ < 8) {
                    game.runningStep()
                    assertEquals(WorldLayer.BASE, game.worldLayer, "$layer at $entry s, ${game.elapsed} s")
                }
                assertTrue(game.enemies.any { it.type == EnemyType.ARCHITECT }, "$layer at $entry s: the Architect arrives in realspace")
            }
        }
    }

    @Test
    fun theArchitectPullsALingeringVisitBackFirst() {
        val game = game()
        game.elapsed = 600f
        game.enterDimensionForTesting(WorldLayer.ZENITH)
        // Only a corrupted clock could keep a visit past the margin; the Architect's arrival is the backstop.
        game.dimensionRun = game.dimensionRun.copy(visit = checkNotNull(game.dimensionRun.visit).copy(endsAt = 800f))
        game.elapsed = bossAt - MutableGameState.FIXED_STEP / 2f
        game.runningStep()
        assertTrue(game.bossSpawned)
        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertEquals("THE ARCHITECT", game.message, "a forced return keeps the Architect's announcement")
        assertTrue(game.enemies.any { it.type == EnemyType.ARCHITECT })
        assertEquals(immutableListOf(DimensionRewardGrant(WorldLayer.ZENITH, completed = false)), game.dimensionRun.pendingRewards)
    }

    @Test
    fun rebasesShiftEveryDimensionPositionAndKeepLayerIds() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.ZENITH)
        game.completeObjectiveForTesting()
        val entered = checkNotNull(game.dimensionRun.visit)
        game.dimensionRun = game.dimensionRun.copy(
            visit = entered.copy(
                halos = immutableListOf(HaloRing(100, 900f, -300f, -1), HaloRing(101, 1_200f, 400f, 0)),
                embers = immutableListOf(Ember(-500f, 250f, 230f)),
            ),
        )
        val keepOut = game.worldKeepOut
        game.coreX = 300_000f
        game.coreY = -260_000f
        game.previousCoreX = game.coreX
        game.previousCoreY = game.coreY
        fun MutableGameState.absolute(): List<Pair<Double, Double>> {
            val visit = checkNotNull(dimensionRun.visit)
            val exit = checkNotNull(visit.exitPortal)
            val points = listOf(visit.courseX to visit.courseY, exit.x to exit.y) +
                visit.halos.map { it.x to it.y } + visit.embers.map { it.x to it.y }
            return points.map { (x, y) -> worldOriginX + x to worldOriginY + y }
        }
        val before = game.absolute()
        game.rebaseWorldIfNeeded()
        assertEquals(0f, game.coreX)
        game.absolute().zip(before).forEach { (after, was) ->
            assertEquals(was.first, after.first, 0.05)
            assertEquals(was.second, after.second, 0.05)
        }
        assertEquals(keepOut, game.worldKeepOut, "the keep-out is absolute")
        game.refreshObstacleWindow()
        assertTrue(game.obstacleField.size > 0)
        assertTrue(game.obstacleField.obstacles.all { it.id ushr 60 == WorldLayer.ZENITH.ordinal.toLong() })

        // In realspace the open glitch rift moves with the world too.
        val realspace = game()
        assertTrue(realspace.openArchiveRiftAt(400f, 100f))
        val glitch = checkNotNull(realspace.dimensionRun.glitchPortal)
        realspace.coreX = -280_000f
        realspace.rebaseWorldIfNeeded()
        val moved = checkNotNull(realspace.dimensionRun.glitchPortal)
        assertEquals(glitch.x + 280_000f, moved.x)
        assertEquals(glitch.y, moved.y)
        // Nothing positional: the rebase keeps the same value.
        val quiet = game()
        val state = quiet.dimensionRun
        quiet.coreX = 260_000f
        quiet.rebaseWorldIfNeeded()
        assertSame(state, quiet.dimensionRun)
    }

    @Test
    fun pendingDimensionRewardsAcceptThreeAndRefuseAFourth() {
        val two = immutableListOf(
            DimensionRewardGrant(WorldLayer.NADIR, completed = true),
            DimensionRewardGrant(WorldLayer.ARCHIVE, completed = false),
        )
        val third = game()
        third.enterDimensionForTesting(WorldLayer.ZENITH)
        third.dimensionRun = third.dimensionRun.copy(pendingRewards = two)
        third.exitDimension(DimensionExit.TIMER)
        assertEquals(3, third.dimensionRun.pendingRewards.size)
        assertEquals(WorldLayer.ZENITH, third.dimensionRun.pendingRewards.last().layer)

        val fourth = game()
        fourth.enterDimensionForTesting(WorldLayer.ZENITH)
        fourth.dimensionRun = fourth.dimensionRun.copy(
            pendingRewards = immutableListOf(two[0], two[1], DimensionRewardGrant(WorldLayer.ZENITH, completed = true)),
        )
        val before = fourth.dimensionRun
        val field = fourth.obstacleField
        assertFailsWith<IllegalStateException> { fourth.exitDimension(DimensionExit.TIMER) }
        assertSame(before, fourth.dimensionRun, "a refused return changes nothing")
        assertEquals(WorldLayer.ZENITH, fourth.worldLayer)
        assertSame(field, fourth.obstacleField)
    }

    @Test
    fun objectivesComeFromTheRunSeedAndTheVisitSerial() {
        fun objective(seed: Int, layer: WorldLayer, visitedMask: Int = 0): DimensionObjective {
            val game = game(seed = seed)
            game.dimensionRun = game.dimensionRun.copy(visitedMask = visitedMask)
            game.enterDimensionForTesting(layer)
            val visit = checkNotNull(game.dimensionRun.visit)
            assertEquals(visitedMask.countOneBits(), visit.serial, "earlier visits number this one")
            return visit.objective
        }
        for (seed in listOf(5, 2_026, -731)) {
            assertEquals(objective(seed, WorldLayer.ZENITH), objective(seed, WorldLayer.ZENITH), "seed $seed")
        }
        val zenith = (0 until 40).map { objective(it, WorldLayer.ZENITH) }.toSet()
        val nadir = (0 until 40).map { objective(it, WorldLayer.NADIR) }.toSet()
        assertEquals(setOf(DimensionObjective.ASCENT, DimensionObjective.JUDGEMENT), zenith)
        assertEquals(setOf(DimensionObjective.HARVEST, DimensionObjective.PYRE), nadir)
        assertEquals(DimensionObjective.ANTECEDENT, objective(9, WorldLayer.ARCHIVE, WorldLayer.ZENITH.bit or WorldLayer.NADIR.bit))
    }

    @Test
    fun aVisitReplaysIdentically() {
        fun flown(): MutableGameState = game(seed = 41_017).apply {
            setVelocityForTesting(600f, 200f)
            val (x, y) = openPortalForTesting(WorldLayer.NADIR)
            flyThroughForTesting(x, y)
            updateSites(0.01f)
            check(worldLayer == WorldLayer.NADIR)
            repeat(240) { runningStep() }
            skipToVisitEndForTesting()
            runningStep()
        }
        val first = flown()
        val second = flown()
        assertEquals(WorldLayer.BASE, first.worldLayer)
        assertEquals(first.dimensionRun, second.dimensionRun)
        assertEquals(first.worldKeepOut, second.worldKeepOut)
        assertEquals(first.obstacleField.obstacles.map { it.id to (it.x to it.y) }, second.obstacleField.obstacles.map { it.id to (it.x to it.y) })
        assertEquals(first.streams(), second.streams())
        assertEquals(first.enemies.map { it.type to (it.x to it.y) }, second.enemies.map { it.type to (it.x to it.y) })
    }

    @Test
    fun theOpenGlitchRiftIsAnOptionalSiteMarkAndEnteringItIsEngagement() {
        val game = game()
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        assertEquals(0, game.optionalSiteMarks(marks), "no rift, no mark")

        // A window rift is a site: the sites mark it once, with the radius that enters it.
        val (windowX, windowY) = game.openPortalForTesting(WorldLayer.ZENITH)
        assertEquals(1, game.optionalSiteMarks(marks))
        assertEquals(listOf(windowX, windowY, canonicalGameplayContent.sites.definition(SiteKind.ZENITH_RIFT).activationRadius), marks.take(3))
        game.sites = emptyList()

        // The glitch rift: its centre and the portal radius.
        assertTrue(game.openArchiveRiftAt(1_000f, 200f))
        val glitch = checkNotNull(game.dimensionRun.glitchPortal)
        assertEquals(1, game.optionalSiteMarks(marks))
        assertEquals(listOf(glitch.x, glitch.y, policy.portalRadius), marks.take(3))
        assertEquals(MAX_OPTIONAL_SITE_MARKS, game.appendDimensionMarks(marks, MAX_OPTIONAL_SITE_MARKS - 1), "the last slot")
        assertEquals(MAX_OPTIONAL_SITE_MARKS, game.appendDimensionMarks(marks, MAX_OPTIONAL_SITE_MARKS), "nothing past the bound")

        // The mark is the circle that enters the rift: a pass just outside it skips the Archive, one just inside enters.
        fun passEnters(offset: Float): Boolean = game.copyForReduction().run {
            previousCoreX = glitch.x - 40f
            previousCoreY = glitch.y + policy.portalRadius + offset
            coreX = glitch.x + 40f
            coreY = glitch.y + policy.portalRadius + offset
            updateDimension(MutableGameState.FIXED_STEP)
            worldLayer == WorldLayer.ARCHIVE
        }
        assertFalse(passEnters(offset = 0.5f))
        assertTrue(passEnters(offset = -0.5f))

        // Skipping it is no engagement; entering is, and stays so after the return, when no mark is left.
        assertFalse(game.optionalSitesTouched())
        game.flyThroughForTesting(glitch.x, glitch.y)
        game.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(WorldLayer.ARCHIVE, game.worldLayer)
        assertTrue(game.optionalSitesTouched())
        game.skipToVisitEndForTesting()
        game.updateDimension(MutableGameState.FIXED_STEP)
        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertTrue(game.optionalSitesTouched(), "an opted-in run stays opted in")
        assertEquals(0, game.optionalSiteMarks(marks))
    }
}
