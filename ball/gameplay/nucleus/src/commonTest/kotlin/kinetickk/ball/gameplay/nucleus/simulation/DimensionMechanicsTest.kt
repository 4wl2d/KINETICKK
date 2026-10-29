// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionPhysics
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** What each layer does to the run: pace, Grace and its halos, Heat, Desync, Matter and the director. */
class DimensionMechanicsTest {
    private val step = MutableGameState.FIXED_STEP
    private val zenith = canonicalGameplayContent.dimensions.definition(WorldLayer.ZENITH)
    private val nadir = canonicalGameplayContent.dimensions.definition(WorldLayer.NADIR)
    private val archive = canonicalGameplayContent.dimensions.definition(WorldLayer.ARCHIVE)

    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 70_701, mode: RunMode = RunMode.STANDARD) =
        MutableGameState(content, seed, runMode = mode).apply {
            resize(1_280f, 800f)
            startRun()
            enemies.clear()
            elapsed = 200f
        }

    /** A run inside [layer] with an objective that places nothing of its own (no course rings, no embers). */
    private fun visiting(layer: WorldLayer, content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 70_701, mode: RunMode = RunMode.STANDARD) =
        game(content, seed, mode).apply {
            val objective = when (layer) {
                WorldLayer.ZENITH -> DimensionObjective.JUDGEMENT
                WorldLayer.NADIR -> DimensionObjective.PYRE
                WorldLayer.ARCHIVE -> DimensionObjective.ANTECEDENT
                WorldLayer.BASE -> error("realspace is no visit")
            }
            enterDimensionForTesting(layer, objective)
        }

    private fun MutableGameState.streams() =
        listOf(gameplayRandom.snapshot(), rewardRandom.snapshot(), spawnRandom.snapshot(), siteRandom.snapshot())

    private fun MutableGameState.halos() = checkNotNull(dimensionRun.visit).halos

    private fun MutableGameState.setHalos(vararg rings: HaloRing) {
        dimensionRun = dimensionRun.copy(visit = checkNotNull(dimensionRun.visit).copy(halos = rings.toList().toImmutableList()))
    }

    private fun MutableGameState.dimensionStepsFor(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds / step).roundToInt()) {
            updateDimension(step)
            each()
        }
    }

    private fun GameplayContentSnapshot.withPhysics(layer: WorldLayer, change: DimensionPhysics.() -> DimensionPhysics) =
        withDimensions {
            copy(definitions = definitions.map { if (it.layer == layer) it.copy(physics = it.physics.change()) else it }.toImmutableList())
        }

    // ---- pace ----

    @Test
    fun paceIsTheSteadySpeedOfItsReachUnderTheCeiling() {
        val policy = canonicalGameplayContent.dimensions
        val desktop = game()
        assertEquals(minOf(desktop.steadyCursorSpeed(policy.paceReach), policy.paceCeiling), desktop.dimensionPace())
        val wide = game().apply { resize(1_920f, 1_080f) }
        assertTrue(wide.steadyCursorSpeed(policy.paceReach) > policy.paceCeiling, "precondition: wide viewport flies above the ceiling")
        assertEquals(policy.paceCeiling, wide.dimensionPace())
        val phone = game().apply { resize(360f, 640f) }
        val steady = phone.steadyCursorSpeed(policy.paceReach)
        assertTrue(steady < policy.paceCeiling, "a small phone's pace reach is slower than the ceiling: $steady")
        assertEquals(steady, phone.dimensionPace())
        val open = game(canonicalGameplayContent.withDimensions { copy(paceCeiling = 2_000f) })
        assertEquals(open.steadyCursorSpeed(policy.paceReach), open.dimensionPace(), "the ceiling only caps")
    }

    // ---- Zenith: Grace ----

    @Test
    fun graceLightensTheDragOnlyInTheZenith() {
        val drags = WorldLayer.entries.associateWith { layer ->
            game().apply {
                worldLayer = layer
                coreStraightness = 0f
            }.steeringDrag()
        }
        val base = drags.getValue(WorldLayer.BASE)
        assertEquals(BASE_DRAG_COEFFICIENT, base)
        assertEquals(base * zenith.physics.dragMultiplier, drags.getValue(WorldLayer.ZENITH), 1e-6f)
        assertEquals(base, drags.getValue(WorldLayer.NADIR))
        assertEquals(base, drags.getValue(WorldLayer.ARCHIVE))
    }

    @Test
    fun graceNeverRaisesAnAlreadyLowerDrag() {
        val floor = zenith.physics.dragFloor
        val state = game().apply { worldLayer = WorldLayer.ZENITH }
        for (drag in listOf(0f, 0.01f, floor * 0.9f, floor, 0.045f, floor / zenith.physics.dragMultiplier, 0.08f, 0.29f, 0.6f)) {
            val graced = state.layerDrag(drag)
            assertTrue(graced <= drag, "Grace raised $drag to $graced")
            assertTrue(graced >= minOf(drag, floor) - 1e-7f, "Grace took $drag below its floor: $graced")
            if (drag * zenith.physics.dragMultiplier >= floor) assertEquals(drag * zenith.physics.dragMultiplier, graced, 1e-7f)
        }
        // Perpetual Vector already flies below the floor's reach: Grace lowers it only to the floor.
        val perpetual = buildTestState(RelicId.PERPETUAL_VECTOR).apply {
            worldLayer = WorldLayer.ZENITH
            coreStraightness = 0f
        }
        assertTrue(perpetual.build.upside(RelicId.PERPETUAL_VECTOR), "precondition: the Perpetual Vector upside is on")
        val lowered = perpetual.dragCoefficient * 0.15f
        assertEquals(maxOf(lowered * zenith.physics.dragMultiplier, minOf(lowered, floor)), perpetual.steeringDrag(), 1e-7f)
        assertTrue(perpetual.steeringDrag() <= lowered)
    }

    @Test
    fun graceRaisesSteadySpeedByAboutTwelvePercent() {
        val base = cruise(WorldLayer.BASE)
        val graced = cruise(WorldLayer.ZENITH)
        val gain = graced / base - 1f
        println("DIMENSIONS Grace: straight cruise at reach $CRUISE_REACH $base -> $graced u/s (gain ${gain * 100f} %)")
        assertTrue(gain in 0.08f..0.16f, "Grace raised the steady speed by ${gain * 100f} %")
    }

    /** The Core's speed after cruising from rest for [CRUISE_SECONDS] with the pointer held ahead, no obstacles or structures. */
    private fun cruise(layer: WorldLayer): Float {
        val huge = 10_000_000f
        val empty = canonicalGameplayContent.copy(obstacles = canonicalGameplayContent.obstacles.copy(meanPerChunk = 0f, chunkSize = huge),
            structures = canonicalGameplayContent.structures.copy(enabled = false))
        val state = buildTestState(content = empty).apply {
            resize(1_280f, 800f)
            startRun()
            quietArenaForTesting()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
            overdriveBurnoutUntil = Float.MAX_VALUE
            worldOriginX = huge * 0.5
            worldOriginY = huge * 0.5
            refreshObstacleWindow()
            worldLayer = layer
            updatePointer(screenWidth * 0.5f + CRUISE_REACH * 640f, screenHeight * 0.5f)
        }
        repeat((CRUISE_SECONDS / step).toInt()) {
            if (state.phase != GamePhase.RUNNING) fail("the cruise ended: ${state.message}")
            state.simulateStep(step)
            state.enemies.clear()
            state.projectiles.clear()
        }
        return state.speed
    }

    // ---- Zenith: halo rings ----

    @Test
    fun aHaloPassedAtPaceMendsAndChargesOverdriveOnce() {
        val game = visiting(WorldLayer.ZENITH)
        game.hp = game.maxHp * 0.5f
        val ring = HaloRing(7, game.coreX + 300f, game.coreY, courseIndex = -1)
        game.setHalos(ring)
        game.setVelocityForTesting(game.dimensionPace(), 0f)
        game.flyThroughForTesting(ring.x, ring.y)
        val overdrive = game.overdriveCharge
        val streams = game.streams()

        game.updateDimension(step)

        assertEquals(game.maxHp * (0.5f + zenith.physics.haloRepairShare), game.hp, 1e-3f)
        assertEquals(overdrive + zenith.physics.haloOverdrive * game.overdriveGainFactor(), game.overdriveCharge, 1e-4f)
        assertTrue(game.halos().none { it.id == ring.id }, "a passed ring leaves")
        assertEquals(streams, game.streams())
        val hp = game.hp
        val charge = game.overdriveCharge
        game.updateDimension(step)
        assertEquals(hp, game.hp, "a ring mends once")
        assertEquals(charge, game.overdriveCharge)
    }

    @Test
    fun aHaloPassedBelowPaceGivesNothingAndStays() {
        val game = visiting(WorldLayer.ZENITH)
        game.hp = game.maxHp * 0.5f
        val ring = HaloRing(7, game.coreX + 300f, game.coreY, courseIndex = -1)
        game.setHalos(ring)
        game.setVelocityForTesting(game.dimensionPace() * 0.98f, 0f)
        game.flyThroughForTesting(ring.x, ring.y)
        val overdrive = game.overdriveCharge

        game.updateDimension(step)

        assertEquals(game.maxHp * 0.5f, game.hp)
        assertEquals(overdrive, game.overdriveCharge)
        assertTrue(ring in game.halos(), "a slow pass leaves the ring standing")
    }

    @Test
    fun ambientHalosAppearOneAStepAheadOfTheCoreUpToTheirCount() {
        fun placed(seed: Int): List<HaloRing> {
            val game = visiting(WorldLayer.ZENITH, seed = seed)
            game.setVelocityForTesting(400f, 0f)
            game.previousCoreX = game.coreX
            game.previousCoreY = game.coreY
            val streams = game.streams()
            repeat(zenith.physics.ambientHalos + 3) { index ->
                game.updateDimension(step)
                assertEquals(minOf(index + 1, zenith.physics.ambientHalos), game.halos().size, "seed $seed after ${index + 1} steps")
            }
            assertEquals(streams, game.streams(), "ambient rings draw from no run stream")
            for (ring in game.halos()) {
                assertEquals(-1, ring.courseIndex)
                val distance = hypot(ring.x - game.coreX, ring.y - game.coreY)
                // Nudging moves a ring straight away from the Core, at most ten 80 u moves.
                assertTrue(distance >= 900f - 0.01f && distance <= 1_800f + 800f + 0.01f, "ring ${ring.id} at $distance u")
                val bearing = atan2(ring.y - game.coreY, ring.x - game.coreX)
                assertTrue(abs(bearing) <= 70f * PI.toFloat() / 180f + 1e-3f, "ring ${ring.id} at $bearing rad off the heading")
            }
            return game.halos()
        }
        val first = placed(70_701)
        assertEquals((100 until 106).toList(), first.map { it.id })
        assertEquals(first, placed(70_701), "the same run and visit place the same rings")
        assertNotEquals(first.map { it.x to it.y }, placed(70_702).map { it.x to it.y })
        // No other layer places rings.
        for (layer in listOf(WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
            val other = visiting(layer)
            other.dimensionStepsFor(0.5f)
            assertTrue(other.halos().isEmpty(), "$layer has no halo rings")
        }
    }

    @Test
    fun ambientHalosLeftFarBehindAreReplacedAndCourseRingsStay() {
        val game = visiting(WorldLayer.ZENITH)
        game.setVelocityForTesting(400f, 0f)
        val course = HaloRing(0, game.coreX - 9_000f, game.coreY, courseIndex = 0)
        game.setHalos(course)
        game.dimensionStepsFor(zenith.physics.ambientHalos * step)
        assertEquals(zenith.physics.ambientHalos + 1, game.halos().size)

        // 4,000 u back along the heading every ambient ring is past the 3,000 u range.
        game.coreX -= 4_000f
        game.previousCoreX = game.coreX
        game.updateDimension(step)

        assertEquals(listOf(course.id, 100 + zenith.physics.ambientHalos), game.halos().map { it.id })
    }

    @Test
    fun haloRingsStayWithinTheirBound() {
        val crowded = canonicalGameplayContent.withPhysics(WorldLayer.ZENITH) { copy(ambientHalos = ContentBounds.MAX_HALO_RINGS - 2) }
        val game = visiting(WorldLayer.ZENITH, crowded)
        game.setHalos(
            HaloRing(0, game.coreX - 2_000f, game.coreY, courseIndex = 0),
            HaloRing(1, game.coreX - 2_600f, game.coreY, courseIndex = 1),
        )
        game.dimensionStepsFor(0.5f)
        assertEquals(ContentBounds.MAX_HALO_RINGS, game.halos().size, "the course and the most ambient rings fill the bound")

        // A thirteenth ring is refused before anything changes.
        game.setHalos(*Array(ContentBounds.MAX_HALO_RINGS) { index -> HaloRing(index, game.coreX - 2_000f - index * 100f, game.coreY, index) })
        val before = game.dimensionRun
        assertFailsWith<IllegalStateException> { game.updateDimension(step) }
        assertSame(before, game.dimensionRun)
        assertFailsWith<IllegalStateException> { withHaloRing(game.halos(), HaloRing(99, 0f, 0f, -1)) }
    }

    // ---- Nadir: Heat ----

    @Test
    fun scorchWaitsOutItsGraceThenBurnsAShareOfMaxIntegrityEachTick() {
        val game = visiting(WorldLayer.NADIR)
        val physics = nadir.physics
        val tick = game.maxHp * physics.scorchSharePerSecond * physics.scorchTickSeconds
        val ticks = mutableListOf<Int>()
        var stepIndex = 0
        var hp = game.hp
        game.dimensionStepsFor(3f) {
            stepIndex++
            if (game.hp < hp) {
                assertEquals(tick, hp - game.hp, 1e-3f, "one tick at step $stepIndex")
                ticks += stepIndex
            }
            hp = game.hp
        }
        val stepsPerTick = (physics.scorchTickSeconds / step).roundToInt()
        val firstTick = ((physics.scorchGraceSeconds + physics.scorchTickSeconds) / step).roundToInt()
        assertEquals(4, ticks.size, "ticks at steps $ticks")
        assertTrue(abs(ticks.first() - firstTick) <= 1, "the first tick at step ${ticks.first()}, expected $firstTick")
        ticks.zipWithNext { a, b -> assertTrue(abs(b - a - stepsPerTick) <= 1, "ticks $a and $b") }
        assertEquals(0f, game.hurtCooldown, "a self hit uses no hit i-frames")
        assertTrue(game.phase == GamePhase.RUNNING)
    }

    @Test
    fun flyingAtPaceResetsTheScorch() {
        val game = visiting(WorldLayer.NADIR)
        val full = game.hp
        game.dimensionStepsFor(1.1f)
        assertEquals(full, game.hp, "no tick yet")
        game.setVelocityForTesting(game.dimensionPace(), 0f)
        game.updateDimension(step)
        assertEquals(0f, game.dimensionSlowSeconds)
        assertEquals(0f, game.dimensionScorchClock)
        game.setVelocityForTesting(0f, 0f)
        // Without the reset the next tick would land within 0.15 s.
        game.dimensionStepsFor(1.2f)
        assertEquals(full, game.hp, "the grace starts over")
    }

    @Test
    fun shieldsAbsorbTheScorchAndDamageReductionDoesNotSoftenIt() {
        val game = visiting(WorldLayer.NADIR)
        val tick = game.maxHp * nadir.physics.scorchSharePerSecond * nadir.physics.scorchTickSeconds
        game.shield = 10f
        game.damageReduction = 0.5f
        val hp = game.hp
        game.dimensionStepsFor(nadir.physics.scorchGraceSeconds + nadir.physics.scorchTickSeconds + 2f * step)
        assertEquals(hp, game.hp, "the shield took the tick")
        assertEquals(10f - tick, game.shield, 1e-4f, "the whole tick, unreduced")
    }

    @Test
    fun aLethalScorchTickEndsTheRunAsScorchedUnlessARevivalAnswers() {
        fun dueToScorch(game: MutableGameState) = game.apply {
            hp = 0.5f
            dimensionSlowSeconds = 2f
            dimensionScorchClock = nadir.physics.scorchTickSeconds - step * 0.5f
            setVelocityForTesting(0f, 0f)
        }
        val dead = dueToScorch(visiting(WorldLayer.NADIR))
        dead.simulateStep(step)
        assertEquals(GamePhase.GAME_OVER, dead.phase)
        assertEquals(DimensionNotices.SCORCHED, dead.message)

        val windy = dueToScorch(visiting(WorldLayer.NADIR))
        windy.matrix = windy.matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        windy.simulateStep(step)
        assertEquals(GamePhase.RUNNING, windy.phase)
        assertTrue(windy.secondWindSpent)
        assertEquals(windy.maxHp * 0.3f, windy.hp, 0.01f)
    }

    @Test
    fun babyModeHalvesTheScorch() {
        val standard = visiting(WorldLayer.NADIR)
        val baby = visiting(WorldLayer.NADIR, mode = RunMode.EASY)
        val seconds = nadir.physics.scorchGraceSeconds + nadir.physics.scorchTickSeconds + 2f * step
        standard.dimensionStepsFor(seconds)
        baby.dimensionStepsFor(seconds)
        val standardLoss = standard.maxHp - standard.hp
        val babyLoss = baby.maxHp - baby.hp
        assertTrue(standardLoss > 0f)
        assertEquals(standardLoss * canonicalGameplayContent.easyMode.incomingDamageMultiplier, babyLoss, 1e-4f)
    }

    @Test
    fun onlyTheNadirScorches() {
        for (layer in listOf(WorldLayer.ZENITH, WorldLayer.ARCHIVE)) {
            val game = visiting(layer)
            game.hp = game.maxHp * 0.5f
            game.dimensionStepsFor(3f)
            assertEquals(game.maxHp * 0.5f, game.hp, "$layer")
            assertEquals(0f, game.dimensionSlowSeconds, "$layer")
        }
    }

    // ---- Archive: Desync ----

    @Test
    fun desyncScalesHostileTimeWithTheCoresSpeed() {
        val game = visiting(WorldLayer.ARCHIVE)
        val physics = archive.physics
        val pace = game.dimensionPace()
        val drifter = game.addEnemyForTesting(400f, 0f)
        val throne = game.addEnemyForTesting(-400f, 0f, type = EnemyType.THRONE)
        fun at(speed: Float): Triple<Float, Float, Float> {
            game.setVelocityForTesting(speed, 0f)
            return Triple(game.layerTimeScale(null), game.layerTimeScale(drifter), game.layerTimeScale(throne))
        }
        assertEquals(Triple(physics.desyncMinimumScale, physics.desyncMinimumScale, physics.desyncBossFloor), at(0f))
        assertEquals(0.5f, at(pace * 0.5f).first, 1e-6f)
        assertEquals(physics.desyncBossFloor, at(pace * 0.5f).third, 1e-6f)
        assertEquals(1f, at(pace).first, 1e-6f)
        assertEquals(physics.desyncMaximumScale, at(pace * 2f).first)
        assertEquals(physics.desyncMaximumScale, at(pace * 2f).third, "the floor never slows a fast boss")
        // Every other layer keeps real time at any speed.
        for (layer in listOf(WorldLayer.BASE, WorldLayer.ZENITH, WorldLayer.NADIR)) {
            game.worldLayer = layer
            assertEquals(Triple(1f, 1f, 1f), at(0f), "$layer")
            assertEquals(Triple(1f, 1f, 1f), at(pace * 2f), "$layer")
        }
    }

    @Test
    fun desyncSlowsEnemiesAndHostileShotsButNeverTheCoresShots() {
        val game = visiting(WorldLayer.ARCHIVE)
        game.setVelocityForTesting(0f, 0f)
        val drifter = game.addEnemyForTesting(400f, 0f)
        assertEquals(archive.physics.desyncMinimumScale, game.enemyMotionScale(drifter))
        val hostile = Projectile(x = 50f, y = 0f, vx = 600f, vy = 0f, radius = 5f, life = 2f, hostile = true)
        val own = Projectile(x = 60f, y = 0f, vx = 600f, vy = 0f, radius = 5f, life = 2f, hostile = false)
        game.projectiles += hostile
        game.projectiles += own

        game.updateProjectiles(step)

        assertEquals(50f + 600f * step * archive.physics.desyncMinimumScale, hostile.x, 1e-4f)
        assertEquals(2f - step * archive.physics.desyncMinimumScale, hostile.life, 1e-6f)
        assertEquals(60f + 600f * step, own.x, 1e-4f)
        assertEquals(2f - step, own.life, 1e-6f)
        val clock = game.dimensionLocalClock
        game.updateDimension(step)
        assertEquals(clock + step * archive.physics.desyncMinimumScale, game.dimensionLocalClock, 1e-6f, "the visit's local clock follows")
    }

    @Test
    fun desyncKeepsTheSimulationIndependentOfTheFrameRate() {
        val traces = DESYNC_FRAME_RATES.associateWith { rate -> archiveTrace(rate) }
        val reference = traces.getValue(DESYNC_FRAME_RATES.first())
        assertTrue(reference.size >= DESYNC_SECONDS * 120 / 4, "reference trace too short: ${reference.size}")
        traces.forEach { (rate, trace) ->
            assertEquals(reference.keys, trace.keys, "compared step counts at $rate Hz")
            reference.forEach { (steps, fingerprint) -> assertEquals(fingerprint, trace.getValue(steps), "after $steps steps at $rate Hz") }
        }
    }

    private fun archiveTrace(rate: Int): Map<Int, List<Any>> {
        val state = visiting(WorldLayer.ARCHIVE, seed = 6_061).apply {
            applyPreferences(settings.copy(simulationSpeed = 1f))
            addEnemyForTesting(500f, 200f, type = EnemyType.SHOOTER)
            addEnemyForTesting(-450f, -300f, type = EnemyType.CIPHER)
            projectiles += Projectile(x = 300f, y = 0f, vx = -500f, vy = 40f, radius = 5f, life = 3f, hostile = true)
        }
        val reducer = GameReducer()
        var engine = EngineState(state)
        var steps = 0
        val fingerprints = linkedMapOf<Int, List<Any>>()
        fingerprints[0] = engine.model.desyncFingerprint()
        for (frame in 1..DESYNC_SECONDS * rate) {
            engine = accepted(reducer, engine, GameplayInteractionPulse.FrameElapsed.fromValidated(1f / rate))
            steps += engine.model.lastTransitionSteps
            val model = engine.model
            assertEquals(GamePhase.RUNNING, model.phase, "run left RUNNING at $rate Hz after $steps steps")
            assertEquals(WorldLayer.ARCHIVE, model.worldLayer)
            if (steps % 4 == 0 && steps !in fingerprints) fingerprints[steps] = model.desyncFingerprint()
            if ((frame * 6) % rate == 0) {
                val change = frame * 6 / rate
                assertEquals(change * 20, steps, "input change $change at $rate Hz")
                val angle = change * 0.9f
                engine = accepted(
                    reducer,
                    engine,
                    GameplayInteractionPulse.PointerMoved.fromValidated(640f + cos(angle) * (160f + (change % 3) * 120f), 400f + sin(angle) * 140f),
                )
            }
        }
        return fingerprints
    }

    private fun accepted(reducer: GameReducer, engine: EngineState, pulse: GameplayInteractionPulse): EngineState =
        assertIs<GameReductionResult.Accepted>(reducer.reduce(engine, pulse)).state

    private fun MutableGameState.desyncFingerprint(): List<Any> = buildList {
        add(elapsed.toRawBits()); add(coreX.toRawBits()); add(coreY.toRawBits())
        add(velocityX.toRawBits()); add(velocityY.toRawBits()); add(hp.toRawBits())
        add(dimensionLocalClock.toRawBits())
        enemies.forEach { add("${it.id}:${it.type}:${it.x.toRawBits()}:${it.y.toRawBits()}:${it.actionTimer.toRawBits()}:${it.hp.toRawBits()}") }
        projectiles.forEach { add("p:${it.hostile}:${it.x.toRawBits()}:${it.y.toRawBits()}:${it.life.toRawBits()}") }
    }

    // ---- Matter ----

    @Test
    fun theNadirDoublesTheMatterEarnedInsideIt() {
        val earned = WorldLayer.entries.associateWith { layer ->
            game().run {
                worldLayer = layer
                val before = runMatter
                grantMatter(10f)
                runMatter - before
            }
        }
        assertEquals(mapOf(WorldLayer.BASE to 10L, WorldLayer.ZENITH to 10L, WorldLayer.NADIR to 20L, WorldLayer.ARCHIVE to 10L), earned)
    }

    @Test
    fun theArchivesMatterIsPaidInRealspace() {
        // Even an Archive that scaled Matter would not scale its own reward: the return pays it in realspace.
        val scaling = canonicalGameplayContent.withPhysics(WorldLayer.ARCHIVE) { copy(matterMultiplier = 3f) }
        val game = visiting(WorldLayer.ARCHIVE, scaling)
        val before = game.runMatter
        game.skipToVisitEndForTesting()
        game.updateDimension(step)
        assertEquals(WorldLayer.BASE, game.worldLayer)
        assertEquals(archive.reward.matterOnFailure.toLong(), game.runMatter - before)
    }

    // ---- director ----

    @Test
    fun eachLayerScalesTheSpawnRateAndTheOrdinaryCap() {
        fun accrued(layer: WorldLayer): Float = game().run {
            worldLayer = layer
            directorPhase = SpawnDirectorPhase.BUILD.ordinal
            directorPhaseUntil = Float.MAX_VALUE
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            spawnBudget = 0f
            spawnWave(step)
            spawnBudget
        }
        fun cap(layer: WorldLayer): Float = game().run {
            worldLayer = layer
            ordinaryEnemyCap()
        }
        val baseRate = accrued(WorldLayer.BASE)
        val baseCap = cap(WorldLayer.BASE)
        assertTrue(baseRate > 0f)
        for (definition in listOf(zenith, nadir, archive)) {
            assertEquals(baseRate * definition.spawnRateMultiplier, accrued(definition.layer), baseRate * 1e-5f, "${definition.layer} rate")
            assertEquals((baseCap * definition.ordinaryCapMultiplier).toInt().toFloat(), cap(definition.layer), "${definition.layer} cap")
        }
        assertTrue(cap(WorldLayer.NADIR) > baseCap && cap(WorldLayer.ZENITH) < baseCap && cap(WorldLayer.ARCHIVE) < baseCap)
    }

    private companion object {
        const val CRUISE_REACH = 0.6f
        const val CRUISE_SECONDS = 25f
        val DESYNC_FRAME_RATES = listOf(30, 60, 144, 240)
        const val DESYNC_SECONDS = 4
    }
}
