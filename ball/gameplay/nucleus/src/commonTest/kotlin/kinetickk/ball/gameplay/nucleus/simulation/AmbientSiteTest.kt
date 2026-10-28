// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.AmbientSitePolicy
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.windowSiteId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ambient sites: drift caches and easter eggs hashed into absolute cells far from the spawn, a bounded window around the
 * Core, settling clear of obstacles once, caches opened by flying through them and an opened-site FIFO.
 */
class AmbientSiteTest {
    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 40_417) =
        MutableGameState(content, seed).apply {
            startRun()
            enemies.clear()
        }

    private fun ambient(configure: (AmbientSitePolicy) -> AmbientSitePolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(
            sites = canonicalGameplayContent.sites.copy(ambient = configure(canonicalGameplayContent.sites.ambient)),
        )

    /** Rests the Core at run-local ([x], [y]) without a swept step, regenerates the obstacle window there, steps the sites. */
    private fun MutableGameState.restAt(x: Float, y: Float) {
        coreX = x
        coreY = y
        previousCoreX = x
        previousCoreY = y
        refreshObstacleWindow()
        updateSites(0f)
    }

    /** Rests the Core at run-local ([x], [y]) without a swept step and steps the sites (the obstacle window stays). */
    private fun MutableGameState.standAt(x: Float, y: Float) {
        coreX = x
        coreY = y
        previousCoreX = x
        previousCoreY = y
        updateSites(0f)
    }

    /** One site step that sweeps the Core from ([fromX], [fromY]) to ([toX], [toY]). */
    private fun MutableGameState.flyStep(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        previousCoreX = fromX
        previousCoreY = fromY
        coreX = toX
        coreY = toY
        updateSites(0.01f)
    }

    /** The current window replaced by [sites] around the Core's chunk, so the next step keeps it. */
    private fun MutableGameState.holdWindow(vararg sites: SiteState) {
        ambientSites = AmbientSiteWindow(
            absoluteChunk(worldOriginX + coreX), absoluteChunk(worldOriginY + coreY), sites.toList().toImmutableList(),
        )
    }

    private fun cache(cellX: Int, x: Float, y: Float, settled: Boolean = true) =
        SiteState(ambientSiteId(SiteKind.DRIFT_CACHE, cellX, 7), SiteKind.DRIFT_CACHE, x, y, Float.POSITIVE_INFINITY, settled = settled)

    private fun MutableGameState.absoluteDistance(site: SiteState): Double = sqrt(absoluteDistanceSquared(site.x, site.y))

    private fun MutableGameState.matter(): Double = runMatter + matterFraction.toDouble()

    private fun MutableGameState.streams(): List<Any> =
        listOf(rewardRandom.snapshot(), spawnRandom.snapshot(), gameplayRandom.snapshot(), siteRandom.snapshot())

    @Test
    fun ambientSitesArePureFunctionsOfSeedAndCell() {
        // A far cache, and a meeting point 300 u short of it (never touched by a resting Core).
        val probe = game()
        probe.restAt(24_000f, 18_000f)
        val target = probe.ambientSites.sites.first { it.kind == SiteKind.DRIFT_CACHE }
        val meetX = target.x - 300f
        val meetY = target.y

        val north = game()
        val east = game()
        val streams = north.streams()
        for (step in 16 downTo 0) north.restAt(meetX, meetY - step * 600f)
        for (step in 16 downTo 0) east.restAt(meetX + step * 600f, meetY)

        assertTrue(north.openedSiteIds.isEmpty() && east.openedSiteIds.isEmpty(), "neither path opened a site on the way")
        assertEquals(north.ambientSites.chunkX, east.ambientSites.chunkX)
        assertEquals(north.ambientSites.chunkY, east.ambientSites.chunkY)
        assertEquals(north.ambientSites.sites.map { it.id }, east.ambientSites.sites.map { it.id })
        assertEquals(north.ambientSites.sites.map { it.kind }, east.ambientSites.sites.map { it.kind })
        val settledInBoth = north.ambientSites.sites.filter { site ->
            site.settled && east.ambientSites.sites.single { it.id == site.id }.settled
        }
        assertTrue(settledInBoth.any { it.id == target.id }, "the cache near the meeting point settled on both paths")
        for (site in settledInBoth) {
            val other = east.ambientSites.sites.single { it.id == site.id }
            assertEquals(site.x.toRawBits(), other.x.toRawBits(), "settled x of ${site.id}")
            assertEquals(site.y.toRawBits(), other.y.toRawBits(), "settled y of ${site.id}")
        }
        assertTrue(north.ambientSites.sites.size <= AmbientSitePolicy().rules.sumOf { rule ->
            val side = (2f * AmbientSitePolicy().queryHalfSize / rule.cellSize).toInt() + 2
            side * side
        })
        // Placement and settling draw from no run stream.
        assertEquals(streams, north.streams())
        assertEquals(streams, east.streams())
        // Another run seed places other sites.
        val other = game(seed = 40_418)
        other.restAt(meetX, meetY)
        assertNotEquals(north.ambientSites.sites.map { it.id }, other.ambientSites.sites.map { it.id })
    }

    @Test
    fun noAmbientSiteLiesWithinItsMinimumDistanceOfTheOrigin() {
        val game = game()
        val rules = game.content.sites.ambient.rules
        var placed = 0
        for (rule in rules) {
            // 400 cells around the origin.
            for (cellY in -10 until 10) for (cellX in -10 until 10) {
                val candidate = game.ambientCandidate(rule, cellX, cellY) ?: continue
                placed++
                val distance = sqrt(candidate.absoluteX * candidate.absoluteX + candidate.absoluteY * candidate.absoluteY)
                assertTrue(distance >= rule.minDistance, "${rule.kind} at $distance u in cell $cellX, $cellY")
                assertTrue(distance >= 6_000.0)
                // A placed site lies in the inner 60 % of its cell.
                val cell = rule.cellSize.toDouble()
                assertTrue(candidate.absoluteX - cellX * cell in 0.2 * cell..0.8 * cell)
                assertTrue(candidate.absoluteY - cellY * cell in 0.2 * cell..0.8 * cell)
            }
        }
        assertTrue(placed > 20, "the scan placed sites at all ($placed)")
        // The window at the spawn holds only sites from 6,000 u out, and the same cell always places the same site.
        game.restAt(0f, 0f)
        for (site in game.ambientSites.sites) assertTrue(game.absoluteDistance(site) >= 6_000.0)
        val rule = rules.first()
        assertEquals(game.ambientCandidate(rule, 3, -4), game().ambientCandidate(rule, 3, -4))
    }

    @Test
    fun theWindowRefreshesOnlyOnAnObstacleChunkChange() {
        // Without settling, the window changes only when it is rebuilt.
        val game = game(ambient { it.copy(settleDistance = 0f) })
        game.clearObstaclesForTesting()
        val chunk = game.content.obstacles.chunkSize
        val startX = 20f * chunk + chunk * 0.5f
        val startY = 15f * chunk + chunk * 0.5f
        game.standAt(startX, startY)
        val first = game.ambientSites
        assertEquals(20, first.chunkX)
        assertEquals(15, first.chunkY)
        assertTrue(first.sites.isNotEmpty())

        game.standAt(startX + chunk * 0.45f, startY - chunk * 0.45f)
        assertSame(first, game.ambientSites, "moving inside the chunk keeps the window")

        game.standAt(startX + chunk, startY)
        val next = game.ambientSites
        assertNotSame(first, next)
        assertEquals(21, next.chunkX)
        assertEquals(15, next.chunkY)
        val kept = first.sites.filter { site -> next.sites.any { it.id == site.id } }
        assertTrue(kept.isNotEmpty())
        // A site in both windows keeps its state instance (and so a settled position).
        for (site in kept) assertSame(site, next.sites.single { it.id == site.id })
    }

    @Test
    fun aSettledSiteKeepsItsPositionAcrossARefresh() {
        val game = game()
        game.clearObstaclesForTesting()
        val chunk = game.content.obstacles.chunkSize
        game.standAt(24_000f, 18_000f)
        val site = game.ambientSites.sites.first()
        game.standAt(site.x - 500f, site.y)
        val settled = game.ambientSites.sites.single { it.id == site.id }
        assertTrue(settled.settled)
        game.standAt(site.x - 500f - chunk, site.y)
        assertNotEquals(game.absoluteChunk(game.worldOriginX + site.x - 500f), game.ambientSites.chunkX)
        assertSame(settled, game.ambientSites.sites.single { it.id == site.id })
    }

    @Test
    fun aBlockedSiteSettlesOnceAlongItsHashedDirectionAndAFarSiteWaits() {
        fun settledAfterOneStep(coreOffsetY: Float): SiteState {
            val game = game()
            game.coreX = 24_000f
            game.coreY = 18_000f + coreOffsetY
            game.previousCoreX = game.coreX
            game.previousCoreY = game.coreY
            // A cache under an 80 u rock: its 70 u circle plus 40 u clears the rock (and its 24 u spawn margin) after
            // four 60 u moves from the rock's centre.
            game.placeObstaclesForTesting(testObstacle(24_500f, 18_000f))
            game.holdWindow(cache(1, 24_500f, 18_000f, settled = false))
            val streams = game.streams()
            game.updateSites(0f)
            assertEquals(streams, game.streams())
            return game.ambientSites.sites.single()
        }
        val site = settledAfterOneStep(coreOffsetY = 0f)
        assertTrue(site.settled)
        assertEquals(240f, length(site.x - 24_500f, site.y - 18_000f), 0.5f)
        // The direction comes from the site's id, not from where the Core stands.
        val elsewhere = settledAfterOneStep(coreOffsetY = 900f)
        assertEquals(site.x.toRawBits(), elsewhere.x.toRawBits())
        assertEquals(site.y.toRawBits(), elsewhere.y.toRawBits())

        // Six moves at most: a site still blocked after them (rocks every 120 u along its direction) settles where the
        // sixth move left it.
        val ux = (site.x - 24_500f) / 240f
        val uy = (site.y - 18_000f) / 240f
        val boulder = game()
        boulder.coreX = 24_000f
        boulder.coreY = 18_000f
        boulder.previousCoreX = boulder.coreX
        boulder.previousCoreY = boulder.coreY
        boulder.placeObstaclesForTesting(
            *Array(4) { index -> testObstacle(24_500f + ux * 120f * index, 18_000f + uy * 120f * index, id = index + 1L) },
        )
        boulder.holdWindow(cache(1, 24_500f, 18_000f, settled = false))
        boulder.updateSites(0f)
        val stuck = boulder.ambientSites.sites.single()
        assertTrue(stuck.settled)
        assertEquals(360f, length(stuck.x - 24_500f, stuck.y - 18_000f), 0.5f)

        // Settled once: a rock that appears later never moves it again.
        boulder.placeObstaclesForTesting(testObstacle(stuck.x, stuck.y))
        boulder.updateSites(0f)
        assertSame(stuck, boulder.ambientSites.sites.single())
    }

    @Test
    fun onlySitesWithinTheSettleDistanceSettle() {
        val game = game()
        game.clearObstaclesForTesting()
        game.coreX = 24_000f
        game.coreY = 18_000f
        game.previousCoreX = game.coreX
        game.previousCoreY = game.coreY
        val reach = game.content.sites.ambient.settleDistance
        game.holdWindow(cache(1, 24_000f + reach, 18_000f, settled = false), cache(2, 24_000f, 18_000f + reach + 1f, settled = false))
        game.updateSites(0f)
        val (edge, beyond) = game.ambientSites.sites
        assertTrue(edge.settled, "a site at the settle distance settles")
        assertFalse(beyond.settled, "a site 1 u past it waits")
        assertEquals(18_000f + reach + 1f, beyond.y)
        game.flyStep(24_000f, 18_000f, 24_000f, 18_002f)
        assertTrue(game.ambientSites.sites.all { it.settled })
    }

    @Test
    fun flyingThroughACacheOpensItWithItsContentsDuringAnyEngagement() {
        fun opened(content: GameplayContentSnapshot, engaged: Boolean = false): MutableGameState {
            val game = game(content)
            game.clearObstaclesForTesting()
            game.coreX = 24_000f
            game.coreY = 18_000f
            if (engaged) game.sites = listOf(SiteState(windowSiteId(0, 0), SiteKind.COLLAPSING_ORBIT, 23_000f, 18_000f, 85f, active = true))
            game.holdWindow(cache(3, 24_200f, 18_000f))
            game.takeSoundCues()
            game.flyStep(24_100f, 18_000f, 24_300f, 18_000f)
            return game
        }
        val data = opened(ambient { it.copy(cacheDataShare = 1f, cacheRepairShare = 0f) }, engaged = true)
        val id = ambientSiteId(SiteKind.DRIFT_CACHE, 3, 7)
        assertTrue(data.activeWindowSite() != null, "the trial still runs")
        assertTrue(data.ambientSites.sites.isEmpty(), "an opened cache leaves the window")
        assertEquals(listOf(id), data.openedSiteIds)
        assertEquals(SiteNotice.CACHE_OPENED.english, data.message)
        assertTrue(data.worldDiscoveryMask and WorldDiscovery.DRIFT_CACHE.bit != 0)
        assertTrue(data.pendingWorldDiscoveryMask and WorldDiscovery.DRIFT_CACHE.bit != 0)
        assertTrue(GameplayAudioCue.PICKUP in data.takeSoundCues())
        assertEquals(12, data.pickups.count { it.type == PickupType.DATA })
        assertTrue(data.optionalSitesTouched())

        val matterBefore = game().matter()
        val repairs = opened(ambient { it.copy(cacheDataShare = 0f, cacheRepairShare = 1f) })
        assertEquals(2, repairs.pickups.count { it.type == PickupType.REPAIR })
        assertEquals(0, repairs.pickups.count { it.type == PickupType.DATA })
        assertEquals(matterBefore + 10.0 * repairs.matterGain, repairs.matter(), 1e-3)

        val relic = opened(ambient { it.copy(cacheDataShare = 0f, cacheRepairShare = 0f) })
        assertEquals(1, relic.pickups.count { it.type == PickupType.RELIC })
        assertEquals(matterBefore, relic.matter(), 1e-3)

        // A pass just outside the cache's 70 u circle leaves it closed.
        val missed = game()
        missed.clearObstaclesForTesting()
        missed.coreX = 24_000f
        missed.coreY = 18_000f
        missed.holdWindow(cache(3, 24_200f, 18_000f))
        missed.flyStep(24_100f, 18_071f, 24_300f, 18_071f)
        assertEquals(1, missed.ambientSites.sites.size)
        assertTrue(missed.openedSiteIds.isEmpty())
        missed.flyStep(24_100f, 18_069f, 24_300f, 18_069f)
        assertTrue(missed.ambientSites.sites.isEmpty())
    }

    @Test
    fun openedSiteIdsAreAFifoOfSixtyFour() {
        val game = game()
        game.clearObstaclesForTesting()
        val bound = ContentBounds.MAX_OPENED_SITES
        assertEquals(64, bound)
        val contents = mutableSetOf<PickupType>()
        fun open(cellX: Int) {
            game.coreX = 30_600f
            game.coreY = 600f
            game.holdWindow(cache(cellX, 30_600f, 600f))
            game.pickups.clear()
            game.flyStep(30_450f, 600f, 30_750f, 600f)
            contents += game.pickups.map { it.type }
        }
        for (cellX in 0 until bound) open(cellX)
        val ids = (0 until bound).map { ambientSiteId(SiteKind.DRIFT_CACHE, it, 7) }
        assertEquals(ids, game.openedSiteIds, "sixty-four opened ids are kept, oldest first")

        open(bound)
        assertEquals(bound, game.openedSiteIds.size)
        assertEquals(ids.drop(1) + ambientSiteId(SiteKind.DRIFT_CACHE, bound, 7), game.openedSiteIds, "the 65th evicts the oldest")
        // Contents follow each cache's cell: Data, repairs and Relics all appear among them.
        assertEquals(setOf(PickupType.DATA, PickupType.REPAIR, PickupType.RELIC), contents)
    }

    @Test
    fun anOpenedCacheNeverReappearsAfterLeavingAndReturning() {
        val game = game()
        game.clearObstaclesForTesting()
        game.standAt(24_000f, 18_000f)
        val cache = game.ambientSites.sites.first { it.kind == SiteKind.DRIFT_CACHE }
        game.standAt(cache.x - 1_000f, cache.y)
        game.flyStep(cache.x - 100f, cache.y, cache.x + 100f, cache.y)
        assertEquals(listOf(cache.id), game.openedSiteIds)

        game.standAt(cache.x + 12_000f, cache.y + 12_000f)
        assertTrue(game.ambientSites.sites.none { it.id == cache.id })
        game.standAt(cache.x - 1_000f, cache.y)
        assertTrue(game.ambientSites.sites.none { it.id == cache.id }, "the opened cache stays opened")

        val untouched = game()
        untouched.clearObstaclesForTesting()
        untouched.standAt(cache.x - 1_000f, cache.y)
        assertTrue(untouched.ambientSites.sites.any { it.id == cache.id }, "a run that never opened it still finds it there")
    }

    @Test
    fun aRebaseKeepsAbsolutePositionsAndIds() {
        val game = game()
        game.clearObstaclesForTesting()
        game.standAt(300_000f, 0f)
        val before = game.ambientSites
        assertTrue(before.sites.isNotEmpty())
        val absolute = before.sites.map { game.absoluteX(it.x) to game.absoluteY(it.y) }

        game.rebaseWorldIfNeeded()
        assertEquals(300_000.0, game.worldOriginX)
        game.previousCoreX = game.coreX
        game.previousCoreY = game.coreY
        game.updateSites(0f)
        val after = game.ambientSites
        assertEquals(before.chunkX, after.chunkX)
        assertEquals(before.chunkY, after.chunkY)
        assertEquals(before.sites.map { it.id }, after.sites.map { it.id })
        after.sites.forEachIndexed { index, site ->
            assertEquals(absolute[index].first, game.absoluteX(site.x), 0.05)
            assertEquals(absolute[index].second, game.absoluteY(site.y), 0.05)
        }

        // A run that reaches the same absolute point through another origin places the same sites there.
        val shifted = game()
        shifted.clearObstaclesForTesting()
        shifted.worldOriginX = 280_000.0
        shifted.standAt(20_000f, 0f)
        assertEquals(before.sites.map { it.id }, shifted.ambientSites.sites.map { it.id })
        shifted.ambientSites.sites.forEachIndexed { index, site ->
            assertEquals(absolute[index].first, shifted.absoluteX(site.x), 0.05)
            assertEquals(absolute[index].second, shifted.absoluteY(site.y), 0.05)
        }
    }

    @Test
    fun aDisabledPolicyRealspaceOnlyAndTheArchitectLeaveNothing() {
        val off = game(ambient { it.copy(enabled = false) })
        off.restAt(24_000f, 18_000f)
        assertSame(AmbientSiteWindow.EMPTY, off.ambientSites)

        val noSites = game(canonicalGameplayContent.copy(sites = canonicalGameplayContent.sites.copy(enabled = false)))
        noSites.restAt(24_000f, 18_000f)
        assertSame(AmbientSiteWindow.EMPTY, noSites.ambientSites)

        val zenith = game()
        zenith.restAt(24_000f, 18_000f)
        assertTrue(zenith.ambientSites.sites.isNotEmpty())
        zenith.worldLayer = WorldLayer.ZENITH
        zenith.updateSites(0f)
        assertSame(AmbientSiteWindow.EMPTY, zenith.ambientSites, "ambient sites lie only in realspace")

        val boss = game()
        boss.restAt(24_000f, 18_000f)
        boss.elapsed = boss.content.tempo.bossAtSeconds
        boss.updateSites(0f)
        assertSame(AmbientSiteWindow.EMPTY, boss.ambientSites, "nothing ambient stays once the Architect arrives")
    }

    @Test
    fun structureFootprintsKeepAmbientSitesOut() {
        val structures = denseStructureContent()
        val open = game()
        open.clearObstaclesForTesting()
        open.standAt(24_000f, 18_000f)
        val built = game(structures)
        built.clearObstaclesForTesting()
        built.standAt(24_000f, 18_000f)

        val inside = open.ambientSites.sites.filter {
            built.structureFootprintContains(built.absoluteX(it.x), built.absoluteY(it.y), AMBIENT_STRUCTURE_MARGIN)
        }
        assertTrue(inside.isNotEmpty(), "dense structures cover some of the window's sites")
        assertEquals(open.ambientSites.sites.map { it.id } - inside.map { it.id }.toSet(), built.ambientSites.sites.map { it.id })
    }

    @Test
    fun pilotsAvoidTheAmbientSitesNearTheCoreAndAProximityTriggerByItsRadius() {
        val game = game()
        game.coreX = 24_000f
        game.coreY = 18_000f
        val golden = SiteState(ambientSiteId(SiteKind.GOLDEN_DRIFTER, 4, 4), SiteKind.GOLDEN_DRIFTER, 25_000f, 18_000f,
            Float.POSITIVE_INFINITY)
        game.ambientSites = AmbientSiteWindow(0, 0, immutableListOf(
            cache(1, 24_000f + AMBIENT_MARK_REACH, 18_000f),
            cache(2, 24_000f, 18_000f - AMBIENT_MARK_REACH - 1f),
            golden,
        ))
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        assertEquals(2, game.optionalSiteMarks(marks))
        assertEquals(24_000f + AMBIENT_MARK_REACH, marks[0])
        assertEquals(game.content.sites.definition(SiteKind.DRIFT_CACHE).activationRadius, marks[2])
        assertEquals(25_000f, marks[3])
        val trigger = game.content.sites.ambient.rules.single { it.kind == SiteKind.GOLDEN_DRIFTER }.triggerRadius
        assertEquals(1_300f, trigger)
        assertEquals(trigger, marks[5])
    }

    @Test
    fun ambientSiteIdsKeepTheirKindAndCellApartFromWindowIds() {
        for ((cellX, cellY) in listOf(0 to 0, -1 to 1, 4_000 to -9_000, -134_217_728 to 134_217_727)) {
            val id = ambientSiteId(SiteKind.QUIET_SIGNAL, cellX, cellY)
            assertEquals(cellX, ambientCellX(id))
            assertEquals(cellY, ambientCellY(id))
            assertEquals((SiteKind.QUIET_SIGNAL.ordinal + 1).toLong(), id ushr 56)
            assertTrue(id > windowSiteId(5, 1))
        }
        assertNotEquals(ambientSiteId(SiteKind.DRIFT_CACHE, 2, 3), ambientSiteId(SiteKind.LOST_CURSOR, 2, 3))
        assertNotEquals(ambientSiteId(SiteKind.DRIFT_CACHE, 2, 3), ambientSiteId(SiteKind.DRIFT_CACHE, 3, 2))
        assertTrue(abs(ambientSiteId(SiteKind.DRIFT_CACHE, -1, -1)) > 0L)
    }
}
