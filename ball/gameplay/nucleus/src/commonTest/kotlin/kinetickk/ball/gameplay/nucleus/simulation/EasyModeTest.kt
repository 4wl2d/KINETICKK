// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Baby mode (RunMode.EASY): the rules a run keeps from its start, against the same STANDARD run. */
class EasyModeTest {
    private val tuning = canonicalGameplayContent.easyMode

    @Test
    fun aSingularityContactBoopsTheCoreAwayAndNeverEndsTheRun() {
        // GameScenarioTest.touchingSingularityEndsTheRunAfterGracePeriod: the cursor rests on the Core.
        val standard = cursorOnTheCore(RunMode.STANDARD)
        repeat(40) { standard.update(1f / 24f) }
        assertEquals(GamePhase.GAME_OVER, standard.phase)
        assertEquals("SINGULARITY CONTACT", standard.message)

        val easy = cursorOnTheCore(RunMode.EASY)
        var boops = 0
        repeat(40) { frame ->
            easy.update(1f / 24f)
            assertEquals(GamePhase.RUNNING, easy.phase, "frame $frame")
            assertNotEquals("SINGULARITY CONTACT", easy.message)
            boops += easy.takeVisualFxCues().count { it is VisualFxCue.ShockwaveAdded && it.colorIndex == EASY_SOFT_COLOR_INDEX }
        }
        assertTrue(boops > 0, "the resting cursor keeps booping the Core")

        // One step through the cursor, read against the STANDARD twin that ends on the same step.
        val twin = coreThroughTheCursor(RunMode.STANDARD).apply { updateCore(MutableGameState.FIXED_STEP) }
        assertEquals(GamePhase.GAME_OVER, twin.phase)
        val speedBefore = twin.speed
        val boop = coreThroughTheCursor(RunMode.EASY)
        boop.updateCore(MutableGameState.FIXED_STEP)
        assertEquals(GamePhase.RUNNING, boop.phase)
        val nx = boop.coreX / length(boop.coreX, boop.coreY)
        val ny = boop.coreY / length(boop.coreX, boop.coreY)
        assertTrue(length(boop.coreX, boop.coreY) >= tuning.boopClearance - 0.01f, "the Core rests on the cursor's ring")
        assertTrue(boop.velocityX * nx + boop.velocityY * ny >= tuning.boopMinimumOutwardSpeed, "it leaves outward")
        assertTrue(boop.speed <= max(speedBefore, tuning.boopMinimumOutwardSpeed) + 0.01f, "a boop never adds speed")
        assertEquals(-30f, boop.previousCoreX, "the drawn Core glides from where it was")
        assertEquals(EASY_BOOP_MESSAGE, boop.message)
        assertTrue(GameplayAudioCue.UI_CLICK in boop.takeSoundCues())
        val cues = boop.takeVisualFxCues()
        assertTrue(cues.any { it is VisualFxCue.ShockwaveAdded && it.colorIndex == EASY_SOFT_COLOR_INDEX })
        assertTrue(cues.any { it is VisualFxCue.Burst && it.colorIndex == EASY_SOFT_COLOR_INDEX })

        // The next step starts outside the kill circle and moves away: no second boop.
        boop.updateCore(MutableGameState.FIXED_STEP)
        assertTrue(boop.takeVisualFxCues().none { it is VisualFxCue.ShockwaveAdded && it.colorIndex == EASY_SOFT_COLOR_INDEX })
        assertEquals(GamePhase.RUNNING, boop.phase)
    }

    @Test
    fun aBoopKeepsTheOutwardMinimumAndNeverAddsSpeed() {
        // Slow and mostly sideways: the minimum outward speed holds and the sideways part gives way to the limit.
        boopAt(coreX = 10f, coreY = 0f, velocityX = -50f, velocityY = 300f).run {
            assertEquals(tuning.boopClearance, length(coreX, coreY), 1e-3f)
            assertEquals(tuning.boopMinimumOutwardSpeed, velocityX, 1e-2f)
            assertEquals(length(-50f, 300f), speed, 1e-2f, "exactly the speed it came with")
            assertTrue(velocityY > 0f, "the sideways part keeps its sign")
        }
        // Slower than the minimum: it leaves at the minimum, straight out.
        boopAt(coreX = 10f, coreY = 0f, velocityX = -50f, velocityY = 200f).run {
            assertEquals(tuning.boopMinimumOutwardSpeed, velocityX, 1e-2f)
            assertEquals(tuning.boopMinimumOutwardSpeed, speed, 1e-2f)
        }
        // Fast and head-on: the inward speed returns at the restitution.
        boopAt(coreX = 6f, coreY = 0f, velocityX = -1_500f, velocityY = 0f).run {
            assertEquals(tuning.boopClearance, coreX, 1e-3f)
            assertEquals(1_500f * tuning.boopRestitution, velocityX, 1e-2f)
            assertEquals(0f, velocityY)
        }
        // Dead centre in motion: back the way it came.
        boopAt(coreX = 0f, coreY = 0f, velocityX = 0f, velocityY = -900f).run {
            assertEquals(tuning.boopClearance, coreY, 1e-3f)
            assertEquals(900f * tuning.boopRestitution, velocityY, 1e-2f)
        }
        // Dead centre at rest: against the last aim, at the minimum.
        boopAt(coreX = 0f, coreY = 0f, velocityX = 0f, velocityY = 0f, aimX = 1f, aimY = 0f).run {
            assertEquals(-tuning.boopClearance, coreX, 1e-3f)
            assertEquals(-tuning.boopMinimumOutwardSpeed, velocityX, 1e-2f)
        }
        // Already leaving fast: nothing is reflected and no speed is added.
        boopAt(coreX = 20f, coreY = 0f, velocityX = 1_500f, velocityY = 300f).run {
            assertEquals(tuning.boopClearance, coreX, 1e-3f)
            assertEquals(1_500f, velocityX, 1e-2f)
            assertEquals(300f, velocityY, 1e-2f)
        }
    }

    @Test
    fun theBoopMessageNeverHidesAnotherMessage() {
        val busy = boopAt(coreX = 6f, coreY = 0f, velocityX = -600f, velocityY = 0f, message = "THE ARCHITECT", messageTime = 2f)
        assertEquals("THE ARCHITECT", busy.message)
        assertEquals(2f, busy.messageTime)
        val idle = boopAt(coreX = 6f, coreY = 0f, velocityX = -600f, velocityY = 0f, message = "", messageTime = 0f)
        assertEquals(EASY_BOOP_MESSAGE, idle.message)
        assertEquals(0.9f, idle.messageTime)
    }

    @Test
    fun incomingHostileDamageIsHalvedAndSelfDamageIsNot() {
        for (kind in listOf(IncomingDamage.HIT, IncomingDamage.CONTACT, IncomingDamage.PROJECTILE, IncomingDamage.OBSTACLE)) {
            val ratio = lossRatio(kind)
            assertEquals(tuning.incomingDamageMultiplier, ratio, 1e-4f, "$kind")
        }
        assertEquals(1f, lossRatio(IncomingDamage.SELF), 1e-4f, "self-inflicted damage stays whole")
    }

    @Test
    fun rocksNeverBreakABabyModeCore() {
        val standard = quietRun(RunMode.STANDARD)
        standard.takeLethalObstacleHit()
        assertEquals(GamePhase.GAME_OVER, standard.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, standard.message)

        val easy = quietRun(RunMode.EASY)
        val maxHp = easy.maxHp
        easy.hp = maxHp
        easy.takeLethalObstacleHit()
        // Lethal share 0.5 at the Baby mode factor 0.5 (tier 0): a quarter of max integrity.
        assertEquals(maxHp * 0.75f, easy.hp, 1e-3f)
        assertEquals(GamePhase.RUNNING, easy.phase)
        repeat(10) { easy.takeLethalObstacleHit() }
        assertEquals(tuning.obstacleIntegrityFloor, easy.hp)
        assertEquals(GamePhase.RUNNING, easy.phase)

        // The floor: half a point above it may be lost, at the floor nothing more.
        easy.hp = 1.5f
        easy.takeDamage(500f, kind = IncomingDamage.OBSTACLE)
        assertEquals(1f, easy.hp)
        easy.takeDamage(500f, kind = IncomingDamage.OBSTACLE)
        assertEquals(1f, easy.hp)
        assertEquals(GamePhase.RUNNING, easy.phase)

        // Through the real collision path at a lethal impact.
        val impact = quietRun(RunMode.EASY)
        assertTrue(impact.obstacleLethalSpeed() < 6_000f)
        impact.hp = impact.maxHp
        impact.velocityX = -6_000f
        impact.resolveSolidImpact(surfaceId = 77L, nx = 1f, ny = 0f, contactX = impact.coreX, contactY = impact.coreY)
        assertEquals(GamePhase.RUNNING, impact.phase)
        assertEquals(impact.maxHp * 0.75f, impact.hp, 1e-3f)

        // Hostile damage is only halved: it can still end the run.
        easy.hurtCooldown = 0f
        easy.takeDamage(1_000f, kind = IncomingDamage.HIT)
        assertEquals(GamePhase.GAME_OVER, easy.phase)
        assertEquals("CORE FRACTURED", easy.message)
    }

    @Test
    fun babyModeOffersNoHeirloom() {
        val standard = heirloomRun(RunMode.STANDARD)
        val easy = heirloomRun(RunMode.EASY)
        assertTrue(standard.heirloomPending)
        assertFalse(easy.heirloomPending)
        standard.update(MutableGameState.FIXED_STEP * 1.5f)
        easy.update(MutableGameState.FIXED_STEP * 1.5f)
        assertTrue(standard.choices.any { it.heirloom }, "the STANDARD run opens on its Heirloom")
        assertTrue(easy.choices.none { it.heirloom }, "a Baby mode run never offers one")
    }

    @Test
    fun babyModeDropsEveryPendingProgressFact() {
        val standard = quietRun(RunMode.STANDARD).apply { fillEveryPendingProgressFact() }
        assertTrue(standard.hasPublishableProgress())
        val update = assertNotNull(standard.takeProgressUpdate())
        assertEquals(7L, update.bankedMatter)
        assertEquals(setOf(WorldDiscovery.GATE_RACE), update.discoveredWorldEntries)
        assertEquals(5, update.totalKills)

        val easy = quietRun(RunMode.EASY).apply { fillEveryPendingProgressFact() }
        assertFalse(easy.hasPublishableProgress(), "nothing of a Baby mode run is publishable")
        assertNull(easy.takeProgressUpdate())
        assertFalse(easy.hasUrgentProgress())
        assertFalse(easy.hasPendingAchievementCounters())
        assertFalse(easy.achievementFlushDue)
        assertEquals(0L, easy.pendingBankedMatter)
        assertTrue(easy.pendingDiscoveredItemIds.isEmpty())
        assertEquals(0L, easy.pendingDiscoveredRelicMask)
        assertNull(easy.pendingClearedRebirthLevel)
        assertEquals(0, easy.pendingEliteKills)
        assertEquals(0, easy.pendingDashHits)
        assertEquals(0, easy.pendingCompletedOrbits)
        assertNull(easy.pendingArchitectDefeatedWith)
        assertEquals(0, easy.pendingDiscoveredReactionMask)
        assertEquals(0, easy.pendingDiscoveredPairingMask)
        assertNull(easy.pendingVictoryRelics)
        assertFalse(easy.pendingHeirloomResolved)
        assertEquals(0, easy.pendingTotalKills)
        assertEquals(0, easy.pendingRicochets)
        assertEquals(0, easy.pendingDataPickups)
        assertTrue(easy.pendingAcquiredItemIds.isEmpty())
        assertEquals(0, easy.pendingWorldDiscoveryMask, "the world mask is dropped too")
        assertEquals(WorldDiscovery.GATE_RACE.bit, easy.worldDiscoveryMask and WorldDiscovery.GATE_RACE.bit, "the run still knows it")

        // Banking keeps the run's Matter visible in the run and never reaches the Profile's totals.
        val standardTotal = standard.totalMatter
        val standardLifetime = standard.lifetimeMatter
        standard.runMatter = 40L
        standard.bankRunMatter()
        assertEquals(standardTotal + 40L, standard.totalMatter)
        assertEquals(standardLifetime + 40L, standard.lifetimeMatter)
        assertEquals(40L, standard.pendingBankedMatter)

        val total = easy.totalMatter
        val lifetime = easy.lifetimeMatter
        easy.runMatter = 40L
        easy.bankRunMatter()
        assertEquals(40L, easy.bankedRunMatter)
        assertEquals(total, easy.totalMatter)
        assertEquals(lifetime, easy.lifetimeMatter)
        assertEquals(0L, easy.pendingBankedMatter)
        assertFalse(easy.hasPublishableProgress())
        assertNull(easy.takeProgressUpdate())
    }

    @Test
    fun babyModeSurvivesEveryReductionCopy() {
        val committed = cursorOnTheCore(RunMode.EASY).apply { repeat(12) { update(1f / 24f) } }
        assertTrue(committed.copyForReduction().easyMode)
        assertTrue(committed.copyForScalarInputReduction().easyMode)
        val facts = committed.facts()

        val candidate = committed.copyForReduction()
        candidate.hurtCooldown = 0f
        candidate.takeDamage(10f)
        candidate.boopOffSingularity(candidate.coreX + 3f, candidate.coreY)
        candidate.runMatter = 25L
        candidate.bankRunMatter()
        assertEquals(facts, committed.facts(), "a candidate never writes the committed run")

        val reducer = GameReducer()
        var engine = EngineState(committed)
        repeat(30) {
            val result = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(engine, GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 30f)),
            )
            assertTrue(result.state.model.easyMode)
            assertNull(result.outputs.progressUpdate)
            engine = result.state
        }
        assertEquals(facts, committed.facts())
        assertEquals(GamePhase.RUNNING, engine.model.phase)
    }

    @Test
    fun babyModeRunsAreDeterministic() {
        val first = tracedEasyRun()
        val second = tracedEasyRun()
        assertEquals(first, second)
        assertTrue(first.count { it == "boop" } > 0, "the trace covers boops")
    }

    private fun cursorOnTheCore(mode: RunMode) =
        MutableGameState(canonicalGameplayContent, seed = 3, initialMatter = 0, runMode = mode).apply {
            resize(1280f, 720f)
            startRun()
            updatePointer(640f, 360f)
        }

    /** A Core 30 units left of the cursor at 3,000 u/s straight through it, the cursor at the camera. */
    private fun coreThroughTheCursor(mode: RunMode) = quietRun(mode).apply {
        resize(1280f, 720f)
        runGrace = 0f
        cameraX = 0f
        cameraY = 0f
        pointerX = screenWidth * 0.5f
        pointerY = screenHeight * 0.5f
        previousSingularityX = 0f
        previousSingularityY = 0f
        coreX = -30f
        coreY = 4f
        velocityX = 3_000f
        velocityY = 0f
        messageTime = 0f
        takeVisualFxCues()
        takeSoundCues()
    }

    private fun boopAt(
        coreX: Float,
        coreY: Float,
        velocityX: Float,
        velocityY: Float,
        aimX: Float = 1f,
        aimY: Float = 0f,
        message: String = "",
        messageTime: Float = 0f,
    ) = quietRun(RunMode.EASY).apply {
        this.coreX = coreX
        this.coreY = coreY
        this.velocityX = velocityX
        this.velocityY = velocityY
        lastAimDirectionX = aimX
        lastAimDirectionY = aimY
        this.message = message
        this.messageTime = messageTime
        boopOffSingularity(0f, 0f)
    }

    /** A started run with no enemies, shield or hit windows, so one damage call is the whole loss. */
    private fun quietRun(mode: RunMode) = MutableGameState(canonicalGameplayContent, seed = 5, runMode = mode).apply {
        startRun()
        enemies.clear()
        spawnClock = Float.MAX_VALUE
        shield = 0f
        hurtCooldown = 0f
        invulnerableUntil = 0f
    }

    private fun lossRatio(kind: IncomingDamage): Float {
        val standard = quietRun(RunMode.STANDARD)
        val easy = quietRun(RunMode.EASY)
        val amount = standard.maxHp * 0.2f
        val standardLoss = standard.hp.also { standard.takeDamage(amount, kind = kind) } - standard.hp
        val easyLoss = easy.hp.also { easy.takeDamage(amount, kind = kind) } - easy.hp
        assertTrue(standardLoss > 0f, "$kind hurts a STANDARD run")
        return easyLoss / standardLoss
    }

    private fun heirloomRun(mode: RunMode) = MutableGameState(
        canonicalGameplayContent,
        seed = 4_404,
        bootstrapProgress = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = PlayerCollection(heirloomRelicIds = listOf(RelicId.GYRE_ENGINE).toImmutableList()),
            rebirthProgress = RebirthProgress(level = 1, highestCleared = 0),
        ),
        runMode = mode,
    ).apply {
        startRun()
        enemies.clear()
        spawnClock = Float.MAX_VALUE
    }

    /** Every Profile fact a run can hold for its next progress update (lanes append their new outbox fields here). */
    private fun MutableGameState.fillEveryPendingProgressFact() {
        pendingBankedMatter = 7L
        pendingDiscoveredItemIds += 0
        pendingDiscoveredRelicMask = 1L
        pendingClearedRebirthLevel = 2
        pendingEliteKills = 2
        pendingDashHits = 3
        pendingCompletedOrbits = 4
        pendingArchitectDefeatedWith = CoreShape.PRISM
        pendingDiscoveredReactionMask = 1
        pendingDiscoveredPairingMask = 1
        pendingVictoryRelics = listOf(RelicId.GYRE_ENGINE)
        pendingHeirloomResolved = true
        pendingTotalKills = 5
        pendingRicochets = 6
        pendingDataPickups = 7
        pendingAcquiredItemIds = listOf(3, 5)
        discoverWorld(WorldDiscovery.GATE_RACE)
        achievementFlushDue = true
    }

    private fun MutableGameState.facts(): List<Any?> = listOf(
        phase, coreX.toRawBits(), coreY.toRawBits(), velocityX.toRawBits(), velocityY.toRawBits(), hp.toRawBits(),
        elapsed.toRawBits(), message, messageTime.toRawBits(), runMatter, bankedRunMatter, totalMatter, kills, easyMode,
    )

    /** Fifteen seconds of a Baby mode run: the cursor rests on the Core, then circles it at a fixed reach. */
    private fun tracedEasyRun(): List<Any?> {
        val game = cursorOnTheCore(RunMode.EASY)
        val trace = mutableListOf<Any?>()
        repeat(900) { frame ->
            if (frame >= 300) {
                val angle = frame * 0.02f
                game.updatePointer(640f + 260f * kotlin.math.cos(angle), 360f + 200f * kotlin.math.sin(angle))
            }
            game.update(1f / 60f)
            game.takeVisualFxCues().forEach { cue ->
                if (cue is VisualFxCue.ShockwaveAdded && cue.colorIndex == EASY_SOFT_COLOR_INDEX) trace.add("boop")
                trace.add(cue)
            }
            trace.addAll(game.takeSoundCues())
            if (frame % 30 == 0) trace.addAll(game.facts() + game.enemies.size)
        }
        return trace
    }
}
