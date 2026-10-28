// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SPIN_REWARD_MAX_MATTER
import kinetickk.ball.content.api.SecretPolicy
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.EngagementSecretMask
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.LOST_CURSOR_FOUND_FLAG
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.lostCursorFollows
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The easter eggs: the Lost Cursor and the Quiet Signal found by flying through them, the Golden Drifter released near
 * its hidden trigger, chased and caught or escaped, Spin Cycle's window of turns around the singularity, the
 * structures' Doodle hook, and the run secret flags.
 */
class SecretSiteTest {
    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent) =
        MutableGameState(content, 40_417).apply {
            startRun()
            enemies.clear()
            clearObstaclesForTesting()
            coreX = FAR_X
            coreY = FAR_Y
            previousCoreX = coreX
            previousCoreY = coreY
        }

    private fun secrets(configure: (SecretPolicy) -> SecretPolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(
            sites = canonicalGameplayContent.sites.copy(secrets = configure(canonicalGameplayContent.sites.secrets)),
        )

    /** The current window replaced by [sites] around the Core's chunk, so the next step keeps it. */
    private fun MutableGameState.holdWindow(vararg sites: SiteState) {
        ambientSites = AmbientSiteWindow(
            absoluteChunk(worldOriginX + coreX), absoluteChunk(worldOriginY + coreY), sites.toList().toImmutableList(),
        )
    }

    /** One site step that sweeps the Core from ([fromX], [fromY]) to ([toX], [toY]). */
    private fun MutableGameState.flyStep(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        previousCoreX = fromX
        previousCoreY = fromY
        coreX = toX
        coreY = toY
        updateSites(0.01f)
    }

    /** One site step with the Core resting at ([x], [y]). */
    private fun MutableGameState.standAt(x: Float, y: Float) = flyStep(x, y, x, y)

    private fun egg(kind: SiteKind, cellX: Int, x: Float, y: Float) =
        SiteState(ambientSiteId(kind, cellX, 3), kind, x, y, Float.POSITIVE_INFINITY, settled = true)

    private fun MutableGameState.matter(): Double = runMatter + matterFraction.toDouble()

    private fun MutableGameState.streams(): List<Any> =
        listOf(rewardRandom.snapshot(), spawnRandom.snapshot(), gameplayRandom.snapshot(), siteRandom.snapshot())

    /** Rebuilds the window, then rests the Core at twelve far points in turn and returns every kind its windows held. */
    private fun MutableGameState.wander(): Set<SiteKind> {
        val seen = HashSet<SiteKind>()
        ambientSites = AmbientSiteWindow.UNBUILT
        repeat(12) { step ->
            standAt(FAR_X + step * 2_000f, FAR_Y + step * 1_500f)
            ambientSites.sites.forEach { seen += it.kind }
        }
        return seen
    }

    private fun MutableGameState.found(discovery: WorldDiscovery): Boolean =
        worldDiscoveryMask and discovery.bit != 0 && pendingWorldDiscoveryMask and discovery.bit != 0

    @Test
    fun theLostCursorIsFoundOncePerRunAndThenFollowsTheCore() {
        val game = game()
        val first = egg(SiteKind.LOST_CURSOR, 1, FAR_X + 200f, FAR_Y)
        val second = egg(SiteKind.LOST_CURSOR, 2, FAR_X - 900f, FAR_Y + 400f)
        val cache = egg(SiteKind.DRIFT_CACHE, 3, FAR_X, FAR_Y - 700f)
        game.holdWindow(first, second, cache)
        val rerolls = game.rerollsRemaining
        val matter = game.matter()
        val streams = game.streams()
        game.takeSoundCues()
        assertFalse(game.toRenderModel().lostCursorFollows)

        game.flyStep(FAR_X + 100f, FAR_Y, FAR_X + 300f, FAR_Y)

        assertTrue(game.hasSecret(RunSecretFlag.LOST_CURSOR_FOUND))
        assertTrue(game.found(WorldDiscovery.LOST_CURSOR))
        assertEquals(SiteNotice.LOST_CURSOR_FOUND.english, game.message)
        assertEquals(rerolls + 1, game.rerollsRemaining)
        assertEquals(matter + 15.0 * game.matterGain, game.matter(), 1e-3)
        assertTrue(GameplayAudioCue.PICKUP in game.takeSoundCues())
        assertEquals(listOf(first.id), game.openedSiteIds)
        assertEquals(listOf(cache.id), game.ambientSites.sites.map { it.id }, "every other Lost Cursor leaves the window")
        assertTrue(game.optionalSitesTouched())
        assertTrue(game.toRenderModel().lostCursorFollows, "the found Lost Cursor follows the Core in the render model")
        assertEquals(streams, game.streams())

        // A Lost Cursor met later in the run is never found again, and a rebuilt window holds none.
        game.holdWindow(egg(SiteKind.LOST_CURSOR, 4, FAR_X + 200f, FAR_Y))
        game.flyStep(FAR_X + 100f, FAR_Y, FAR_X + 300f, FAR_Y)
        assertEquals(rerolls + 1, game.rerollsRemaining)
        assertEquals(listOf(first.id), game.openedSiteIds)
        assertTrue(SiteKind.LOST_CURSOR in game().wander(), "the same walk meets Lost Cursors in a run without the find")
        assertFalse(SiteKind.LOST_CURSOR in game.wander())

        game.startRun()
        assertFalse(game.toRenderModel().lostCursorFollows, "a new run starts without it")
    }

    @Test
    fun theLostCursorFlagIsTheRenderModelsFollowBit() {
        assertEquals(1 shl RunSecretFlag.LOST_CURSOR_FOUND.ordinal, LOST_CURSOR_FOUND_FLAG)
    }

    @Test
    fun aGoldenDrifterIsReleasedWithinItsTriggerRadiusAsTheSitesOwnDrifter() {
        val reach = canonicalGameplayContent.sites.ambient.rules.single { it.kind == SiteKind.GOLDEN_DRIFTER }.triggerRadius
        val outside = game()
        outside.holdWindow(egg(SiteKind.GOLDEN_DRIFTER, 1, FAR_X + reach + 1f, FAR_Y))
        outside.standAt(FAR_X, FAR_Y)
        assertEquals(-1, outside.goldenDrifterId, "1 u beyond the trigger radius nothing is released")
        assertTrue(outside.enemies.isEmpty())

        val game = game()
        val trigger = egg(SiteKind.GOLDEN_DRIFTER, 1, FAR_X + reach, FAR_Y)
        game.holdWindow(trigger)
        val streams = game.streams()
        game.standAt(FAR_X, FAR_Y)

        val drifter = game.enemies.single()
        assertEquals(EnemyType.DRIFTER, drifter.type, "the Golden Drifter is an ordinary Drifter")
        assertEquals(GOLDEN_DRIFTER_TAG, drifter.ownerTag, "owned by the site's own encounter")
        assertEquals(drifter.id, game.goldenDrifterId)
        assertEquals(trigger.x, drifter.x)
        assertEquals(trigger.y, drifter.y)
        assertEquals(game.elapsed + game.content.sites.secrets.goldenLifetime, game.goldenDrifterUntil)
        assertTrue(game.hasSecret(RunSecretFlag.GOLDEN_DRIFTER_SPAWNED))
        assertEquals(SiteNotice.GOLDEN_SIGHTED.english, game.message)
        assertEquals(listOf(trigger.id), game.openedSiteIds, "the trigger is consumed")
        assertTrue(game.ambientSites.sites.isEmpty())
        assertTrue(game.isPlaceBound(drifter), "leash-exempt and never evicted")
        assertEquals(drifter.id, game.toRenderModel().goldenDrifterId)
        assertEquals(streams, game.streams())

        // One flies at a time: a second trigger waits.
        val next = egg(SiteKind.GOLDEN_DRIFTER, 2, FAR_X, FAR_Y + 300f)
        game.holdWindow(next)
        game.standAt(FAR_X, FAR_Y)
        assertEquals(1, game.enemies.size)
        assertEquals(listOf(next.id), game.ambientSites.sites.map { it.id })
    }

    @Test
    fun aRefusedReleaseLeavesTheTriggerToTryAgain() {
        val game = game()
        repeat(game.content.rebirth.maxActiveEnemies) { index ->
            game.enemies += game.spawnedEnemy(EnemyType.DRIFTER, FAR_X - 1_500f + index * 3f, FAR_Y - 1_500f)
        }
        val trigger = egg(SiteKind.GOLDEN_DRIFTER, 1, FAR_X + 400f, FAR_Y)
        game.holdWindow(trigger)
        game.standAt(FAR_X, FAR_Y)
        assertEquals(-1, game.goldenDrifterId, "no room under the hard cap")
        assertEquals(listOf(trigger.id), game.ambientSites.sites.map { it.id })
        assertTrue(game.openedSiteIds.isEmpty())

        game.enemies.removeAt(0)
        game.standAt(FAR_X, FAR_Y)
        assertTrue(game.goldenDrifterId >= 0, "released once there is room")
    }

    @Test
    fun theGoldenDrifterFleesTheCoreAndGetsAwayAtItsTimeOrItsDistance() {
        val game = released()
        val drifter = game.goldenDrifter()!!
        val start = length(drifter.x - game.coreX, drifter.y - game.coreY)
        repeat(120) { game.updateEnemies(1f / 120f) }
        val away = length(drifter.x - game.coreX, drifter.y - game.coreY)
        assertTrue(away > start + 300f, "it flees: $start -> $away")
        assertTrue(drifter.vx * (drifter.x - game.coreX) + drifter.vy * (drifter.y - game.coreY) > 0f, "moving away from the Core")

        // Still in time and 2,800 u away: it stays.
        val timed = released()
        val runner = timed.goldenDrifter()!!
        val escape = timed.content.sites.secrets.goldenEscapeDistance
        runner.x = timed.coreX + escape
        runner.y = timed.coreY
        timed.elapsed = timed.goldenDrifterUntil - 0.01f
        timed.standAt(timed.coreX, timed.coreY)
        assertEquals(runner.id, timed.goldenDrifterId)
        // Its time is up.
        val kills = timed.kills
        timed.elapsed = timed.goldenDrifterUntil
        timed.standAt(timed.coreX, timed.coreY)
        assertGotAway(timed, kills)

        // 1 u past the escape distance.
        val far = released()
        val farRunner = far.goldenDrifter()!!
        farRunner.x = far.coreX + escape + 1f
        farRunner.y = far.coreY
        far.standAt(far.coreX, far.coreY)
        assertGotAway(far, far.kills)
    }

    private fun assertGotAway(game: MutableGameState, kills: Int) {
        assertEquals(-1, game.goldenDrifterId)
        assertTrue(game.enemies.isEmpty(), "removed without a kill")
        assertEquals(kills, game.kills)
        assertNull(game.encounter(GOLDEN_DRIFTER_TAG))
        assertEquals(SiteNotice.GOLDEN_ESCAPED.english, game.message)
        assertFalse(game.hasSecret(RunSecretFlag.GOLDEN_DRIFTER_CAUGHT))
        assertFalse(game.found(WorldDiscovery.GOLDEN_DRIFTER))
    }

    @Test
    fun catchingTheGoldenDrifterPaysFortyDataAndItsDiscoveryOncePerRun() {
        val game = released()
        val drifter = game.goldenDrifter()!!
        val matter = game.matter()
        val streams = game.streams()
        game.recordSiteKill(drifter)
        assertEquals(40, game.pickups.count { it.type == PickupType.DATA })
        assertEquals(matter + 25.0 * game.matterGain, game.matter(), 1e-3)
        assertTrue(game.hasSecret(RunSecretFlag.GOLDEN_DRIFTER_CAUGHT))
        assertTrue(game.found(WorldDiscovery.GOLDEN_DRIFTER))
        assertEquals(SiteNotice.GOLDEN_CAUGHT.english, game.message)
        assertEquals(-1, game.goldenDrifterId)
        assertEquals(streams, game.streams(), "the Data scatters from a hash")

        // The kill completes; the next site step closes the empty encounter.
        drifter.dead = true
        game.standAt(game.coreX, game.coreY)
        assertNull(game.encounter(GOLDEN_DRIFTER_TAG))
        // Caught once: no other trigger releases one, and a rebuilt window holds none.
        game.holdWindow(egg(SiteKind.GOLDEN_DRIFTER, 5, game.coreX + 200f, game.coreY))
        game.standAt(game.coreX, game.coreY)
        assertEquals(-1, game.goldenDrifterId)
        assertTrue(SiteKind.GOLDEN_DRIFTER in game().wander(), "the same walk meets Golden Drifter triggers in a fresh run")
        assertFalse(SiteKind.GOLDEN_DRIFTER in game.wander())
    }

    @Test
    fun theArchitectsArrivalTakesAFlyingGoldenDrifterWithoutAKill() {
        val game = released()
        val kills = game.kills
        game.elapsed = game.content.tempo.bossAtSeconds
        game.standAt(game.coreX, game.coreY)
        assertEquals(-1, game.goldenDrifterId)
        assertTrue(game.enemies.isEmpty())
        assertEquals(kills, game.kills)
        assertNull(game.encounter(GOLDEN_DRIFTER_TAG))
    }

    /** A game whose Golden Drifter was just released 600 u from the Core. */
    private fun released(): MutableGameState {
        val game = game()
        game.holdWindow(egg(SiteKind.GOLDEN_DRIFTER, 1, FAR_X + 600f, FAR_Y))
        game.standAt(FAR_X, FAR_Y)
        assertNotNull(game.goldenDrifter())
        return game
    }

    @Test
    fun spinCycleCountsTenTurnsAroundTheSingularityWithinItsWindow() {
        val game = spun(turns = 10.01f, seconds = 9.5f)
        assertTrue(game.hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(game.found(WorldDiscovery.SPIN_CYCLE))
        assertEquals(SiteNotice.SPIN_CYCLE.english, game.message)

        assertFalse(spun(turns = 9.99f, seconds = 9.5f).hasSecret(RunSecretFlag.SPIN_CYCLE), "short of ten turns")
        assertFalse(spun(turns = 10.01f, seconds = 12f).hasSecret(RunSecretFlag.SPIN_CYCLE), "ten turns over twelve seconds")
        assertFalse(spun(turns = 0f, seconds = 9f, alternate = true).hasSecret(RunSecretFlag.SPIN_CYCLE), "eighteen turns back and forth")
        val band = canonicalGameplayContent.sites.secrets
        assertFalse(spun(turns = 12f, seconds = 9f, radius = band.spinMinDistance - 1f).hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertFalse(spun(turns = 12f, seconds = 9f, radius = band.spinMaxDistance + 1f).hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(spun(turns = 12f, seconds = 9f, radius = band.spinMaxDistance - 1f).hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(spun(turns = 10.01f, seconds = 9.5f, clockwise = false).hasSecret(RunSecretFlag.SPIN_CYCLE), "either way round")
    }

    @Test
    fun spinCyclePaysOnlyItsDiscoveryAFlourishAndAtMostFiveMatterAndIsNoEngagement() {
        val before = game()
        val game = spun(turns = 10.01f, seconds = 9.5f)
        assertEquals(before.matter() + SPIN_REWARD_MAX_MATTER * game.matterGain, game.matter(), 1e-3)
        assertEquals(before.overdriveCharge, game.overdriveCharge)
        assertEquals(before.rerollsRemaining, game.rerollsRemaining)
        assertTrue(game.pickups.isEmpty())
        assertTrue(game.pendingDirectedRewards.isEmpty() && game.pendingFloorOffers.isEmpty())
        assertEquals(before.pendingLevelChoices, game.pendingLevelChoices)
        assertFalse(game.optionalSitesTouched(), "Spin Cycle runs in every run, so it is never an opt-in")
        assertEquals(before.streams(), game.streams())

        // Found once: nothing is tracked after it.
        val buckets = game.spinBuckets
        val current = game.spinCurrent
        spin(game, turns = 12f, seconds = 9f, startAngle = 1f)
        assertEquals(buckets, game.spinBuckets)
        assertEquals(current, game.spinCurrent)
        assertEquals(before.matter() + SPIN_REWARD_MAX_MATTER * game.matterGain, game.matter(), 1e-3)
    }

    @Test
    fun spinCycleKeepsAtMostItsBucketsAndADisabledPolicyTracksNothing() {
        val slow = game()
        spin(slow, turns = 3f, seconds = 20f)
        assertEquals(slow.content.sites.secrets.spinBuckets, slow.spinBuckets.size, "only the policy's buckets are kept")
        val four = game(secrets { it.copy(spinBuckets = 4) })
        spin(four, turns = 3f, seconds = 20f)
        assertEquals(4, four.spinBuckets.size)
        // With four buckets the window is at most five seconds long: ten turns in six seconds never fit it.
        assertFalse(spun(turns = 10.01f, seconds = 6f, content = secrets { it.copy(spinBuckets = 4) }).hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(spun(turns = 10.01f, seconds = 4.5f, content = secrets { it.copy(spinBuckets = 4) }).hasSecret(RunSecretFlag.SPIN_CYCLE))

        val off = spun(turns = 12f, seconds = 9f, content = secrets { it.copy(spinEnabled = false) })
        assertFalse(off.hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(off.spinBuckets.isEmpty())
        val sitesOff = canonicalGameplayContent.copy(sites = canonicalGameplayContent.sites.copy(enabled = false))
        assertFalse(spun(turns = 12f, seconds = 9f, content = sitesOff, throughSites = true).hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(spun(turns = 12f, seconds = 9f, throughSites = true).hasSecret(RunSecretFlag.SPIN_CYCLE), "the site step tracks it")
    }

    /** A fresh game spun [turns] times around the singularity over [seconds] (see [spin]). */
    private fun spun(
        turns: Float,
        seconds: Float,
        radius: Float = 300f,
        alternate: Boolean = false,
        clockwise: Boolean = true,
        content: GameplayContentSnapshot = canonicalGameplayContent,
        throughSites: Boolean = false,
    ): MutableGameState = game(content).also { spin(it, turns, seconds, radius, alternate, clockwise, throughSites = throughSites) }

    /**
     * Steps Spin Cycle at 120 Hz with the singularity held at the run-local origin and the Core on a circle of [radius]
     * around it, sweeping [turns] evenly over [seconds]; [alternate] instead swings one turn out and back every second.
     */
    private fun spin(
        game: MutableGameState,
        turns: Float,
        seconds: Float,
        radius: Float = 300f,
        alternate: Boolean = false,
        clockwise: Boolean = true,
        startAngle: Float = 0f,
        throughSites: Boolean = false,
    ) {
        game.pointerX = game.screenWidth * 0.5f
        game.pointerY = game.screenHeight * 0.5f
        game.cameraX = 0f
        game.cameraY = 0f
        val steps = (seconds * 120f).toInt()
        val sign = if (clockwise) 1f else -1f
        for (step in 0..steps) {
            val angle = if (alternate) {
                val phase = (step % 120) / 120f
                startAngle + sign * TAU * (if (phase < 0.5f) phase * 2f else 2f - phase * 2f)
            } else {
                startAngle + sign * TAU * turns * (step.toFloat() / steps)
            }
            game.coreX = cos(angle) * radius
            game.coreY = sin(angle) * radius
            game.previousCoreX = game.coreX
            game.previousCoreY = game.coreY
            if (throughSites) game.updateSites(1f / 120f) else game.updateSpinCycle(1f / 120f)
        }
    }

    @Test
    fun theQuietSignalIsHeardOnceAndOpensTheArchiveRiftAheadOfTheCore() {
        val game = game()
        game.velocityX = 900f
        game.velocityY = 0f
        val signal = egg(SiteKind.QUIET_SIGNAL, 1, FAR_X + 200f, FAR_Y)
        val other = egg(SiteKind.QUIET_SIGNAL, 2, FAR_X - 2_000f, FAR_Y)
        game.holdWindow(signal, other)
        val matter = game.matter()
        val streams = game.streams()
        game.flyStep(FAR_X + 100f, FAR_Y, FAR_X + 300f, FAR_Y)

        assertTrue(game.hasSecret(RunSecretFlag.QUIET_SIGNAL_FOUND))
        assertTrue(game.found(WorldDiscovery.QUIET_SIGNAL))
        assertEquals(SiteNotice.QUIET_SIGNAL.english, game.message, "the signal's words stay over the rift's notice")
        assertEquals(QUIET_NOTICE_SECONDS, game.messageTime)
        assertEquals(matter + 20.0 * game.matterGain, game.matter(), 1e-3)
        assertEquals(listOf(signal.id), game.openedSiteIds)
        assertTrue(game.ambientSites.sites.isEmpty(), "every other Quiet Signal leaves the window")
        val rift = assertNotNull(game.dimensionRun.glitchPortal, "the Archive's glitch rift opens")
        assertEquals(WorldLayer.ARCHIVE, rift.target)
        val ahead = game.content.dimensions.glitchPortalDistance
        assertTrue(length(rift.x - (game.coreX + ahead), rift.y - game.coreY) < 200f, "ahead of the Core: (${rift.x}, ${rift.y})")
        assertEquals(streams, game.streams())
        // The pass through the signal does not carry the Core into the Archive.
        game.updateDimension(0.01f)
        assertEquals(WorldLayer.BASE, game.worldLayer)

        // Too late for the Archive: the signal is still heard, and no rift opens.
        val late = game()
        late.elapsed = late.content.dimensions.latestGlitchRiftAt(late.content.tempo.bossAtSeconds) + 1f
        late.holdWindow(signal)
        late.flyStep(FAR_X + 100f, FAR_Y, FAR_X + 300f, FAR_Y)
        assertTrue(late.found(WorldDiscovery.QUIET_SIGNAL))
        assertEquals(SiteNotice.QUIET_SIGNAL.english, late.message)
        assertNull(late.dimensionRun.glitchPortal)
    }

    @Test
    fun theDoodleIsFoundOncePerRun() {
        val game = game()
        val matter = game.matter()
        game.discoverDoodle()
        assertTrue(game.hasSecret(RunSecretFlag.DOODLE_FOUND))
        assertTrue(game.found(WorldDiscovery.ARCHITECTS_DOODLE))
        assertEquals(SiteNotice.DOODLE.english, game.message)
        assertEquals(matter + 30.0 * game.matterGain, game.matter(), 1e-3)
        game.message = ""
        game.discoverDoodle()
        assertEquals(matter + 30.0 * game.matterGain, game.matter(), 1e-3)
        assertEquals("", game.message)
    }

    @Test
    fun runSecretFlagsFitTheirBitmaskAndOnlyOptInsCountAsEngagement() {
        assertTrue(RunSecretFlag.entries.size <= 31)
        val involuntary = setOf(RunSecretFlag.FAR_TRAVEL, RunSecretFlag.SPIN_CYCLE)
        for (flag in RunSecretFlag.entries) {
            assertEquals(flag !in involuntary, EngagementSecretMask and (1 shl flag.ordinal) != 0, "$flag")
            val game = game()
            game.setSecret(flag)
            assertEquals(flag !in involuntary, game.optionalSitesTouched(), "$flag alone")
        }
    }

    private companion object {
        /** A run-local point far from the spawn (eggs lie at least 9,000 u out). */
        const val FAR_X = 24_000f
        const val FAR_Y = 18_000f
    }
}
