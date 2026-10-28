// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SteeringTuning
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The flight law's piloting targets (0.6.0), flown by the production fixed step (updateCore, then updateCamera) in a
 * quiet, empty world, each against the same flight under [SteeringTuning.LEGACY] (the 0.5.0 law). Every number is
 * printed. Held turns and orbits keep the pointer inside the calm area on both axes: reach 0.79 on a square 1280×1280
 * viewport, and 316 dp (reach 0.79 of the short axis) on the 1280×800 desktop.
 *
 * Measured with the shipped tuning (the 0.5.0 law in brackets):
 * - a held 90° turn keeps 0.994 (square) and 0.993 (desktop) of its speed (0.698, 0.692), a held 135° turn 0.860 and
 *   0.849 (0.377, 0.374);
 * - an orbit at ω = 1 averages 1.051 and 1.047 × straight cruise (0.696, 0.694);
 * - straight cruise at reach 0.79 is 2536 on the desktop (2741, ratio 0.925) and 1851 on the 412×915 phone (1989,
 *   0.931); the steady model reads 0.04 % high;
 * - rest to 1500 u/s at reach 0.8 takes 0.525 s on the desktop (0.817 s, ratio 0.643) and 0.925 s on the phone
 *   (1.417 s, 0.653);
 * - carving at reaches 0.3 to 0.65 peaks at 1.078 × the reach's straight steady speed; at reach 0.6 that is 2111
 *   against the lethal 2324;
 * - the worst low-drag stack (grip and sling at the bound, Overdrive throughout) peaks at 1.588 × its own straight
 *   cruise in an orbit at ω = 2 and reach 0.6 (mean over the last third 1.444 ×);
 * - pointing behind the screen centre halves the speed in 0.350 s (0.383 s); a sideways pointer keeps 0.853 of it
 *   after 0.5 s (0.660).
 */
class CoreFlightCalibrationTest {
    private val step = MutableGameState.FIXED_STEP
    private val piloting = world(canonicalGameplayContent.tempo.steering)
    private val legacy = world(SteeringTuning.LEGACY)

    @Test
    fun aHeldNinetyDegreeTurnKeepsItsSpeed() {
        for ((viewport, offset) in TurnCases) {
            val turn = heldTurn(viewport, offset, 90f, piloting)
            val released = heldTurn(viewport, offset, 90f, legacy)
            report("held 90° $viewport offset $offset", turn, released)
            assertTrue(turn.kept >= 0.85f, "$viewport: a held 90° turn keeps ${turn.kept} of its speed, below 0.85")
            assertTrue(released.kept <= 0.75f, "$viewport: the 0.5.0 law kept ${released.kept}, so the case no longer bleeds")
        }
    }

    @Test
    fun aHeldOneThirtyFiveDegreeTurnKeepsMostOfIt() {
        for ((viewport, offset) in TurnCases) {
            val turn = heldTurn(viewport, offset, 135f, piloting)
            report("held 135° $viewport offset $offset", turn, heldTurn(viewport, offset, 135f, legacy))
            assertTrue(turn.kept >= 0.70f, "$viewport: a held 135° turn keeps ${turn.kept} of its speed, below 0.70")
        }
    }

    @Test
    fun anOrbitAtOneRadianPerSecondIsAtLeastAsFastAsStraight() {
        for ((viewport, offset) in TurnCases) {
            val straight = cruise(viewport, offset, piloting).speed
            val orbit = orbit(viewport, offset, omega = 1f, seconds = 30f, piloting)
            val releasedStraight = cruise(viewport, offset, legacy).speed
            val released = orbit(viewport, offset, omega = 1f, seconds = 30f, legacy)
            println(
                "CORE FLIGHT orbit ω=1 $viewport offset $offset: mean ${orbit.lateMean} = ${orbit.lateMean / straight} × " +
                    "straight $straight (max ${orbit.maximum}); 0.5.0 ${released.lateMean} = " +
                    "${released.lateMean / releasedStraight} × straight $releasedStraight",
            )
            assertTrue(orbit.lateMean >= straight,
                "$viewport: an orbit at ω = 1 averages ${orbit.lateMean}, slower than straight cruise at $straight")
        }
    }

    @Test
    fun straightCruiseIsFiveToFifteenPercentSlower() {
        for (viewport in listOf(Desktop, Phone)) {
            val offset = 0.79f * viewport.longHalfAxis
            val cruise = cruise(viewport, offset, piloting)
            val released = cruise(viewport, offset, legacy).speed
            val model = cruise.steadyCursorSpeed(0.79f)
            val ratio = cruise.speed / released
            println(
                "CORE FLIGHT straight $viewport reach 0.79: ${cruise.speed} against 0.5.0 $released (ratio $ratio); " +
                    "model $model (error ${(cruise.speed - model) / model})",
            )
            assertTrue(ratio in 0.85f..0.95f, "$viewport: straight cruise at reach 0.79 is $ratio of the 0.5.0 speed")
            assertEquals(model, cruise.speed, model * 0.03f, "$viewport: the steady model at reach 0.79")
        }
    }

    @Test
    fun spoolUpIsQuicker() {
        for (viewport in listOf(Desktop, Phone)) {
            val offset = 0.8f * viewport.longHalfAxis
            val spool = secondsToReach(viewport, offset, SPOOL_SPEED, piloting)
            val released = secondsToReach(viewport, offset, SPOOL_SPEED, legacy)
            println("CORE FLIGHT spool-up $viewport reach 0.8: rest to $SPOOL_SPEED in $spool s against 0.5.0 $released s " +
                "(ratio ${spool / released})")
            assertTrue(spool <= released * 0.7f, "$viewport: rest to $SPOOL_SPEED takes $spool s, not within 0.7 × $released s")
        }
    }

    @Test
    fun carvingNeverLiftsABriskCruiseToLethal() {
        for (reach in listOf(0.3f, 0.45f, 0.6f, 0.65f)) {
            val offset = reach * Square.longHalfAxis
            val probe = flight(Square, piloting)
            val steady = probe.steadyCursorSpeed(reach)
            val lethal = probe.obstacleLethalSpeed()
            val turns = listOf(45f, 90f, 120f).associateWith { heldTurn(Square, offset, it, piloting).maximum }
            val orbits = CarveOmegas.associateWith { orbit(Square, offset, it, seconds = 30f, piloting).maximum }
            val fastest = (turns.values + orbits.values).max()
            println(
                "CORE FLIGHT carving reach $reach: steady $steady, fastest ${fastest / steady} × steady; held turns " +
                    turns.entries.joinToString { (degrees, top) -> "$degrees° ${top / steady}" } + "; orbits " +
                    orbits.entries.joinToString { (omega, top) -> "ω=$omega ${top / steady}" } + "; lethal $lethal",
            )
            assertTrue(fastest <= steady * 1.10f, "reach $reach: carving reached $fastest, above 1.10 × steady $steady")
            if (reach == 0.6f) assertTrue(fastest < lethal, "reach 0.6: carving reached $fastest, not below lethal $lethal")
        }
    }

    @Test
    fun lowDragStacksCannotRunAway() {
        for (reach in listOf(0.3f, 0.6f, 0.79f)) {
            val offset = reach * Square.longHalfAxis
            val straight = cruise(Square, offset, piloting, prepare = { lowDragStack() }).speed
            val orbits = RunawayOmegas.associateWith { omega ->
                orbit(Square, offset, omega, seconds = 60f, piloting, prepare = { lowDragStack() })
            }
            val fastest = orbits.values.maxOf { it.maximum }
            println(
                "CORE FLIGHT low-drag stack reach $reach: straight $straight; orbit maximum / straight " +
                    orbits.entries.joinToString { (omega, run) -> "ω=$omega ${run.maximum / straight}" } +
                    "; late mean / straight " +
                    orbits.entries.joinToString { (omega, run) -> "ω=$omega ${run.lateMean / straight}" },
            )
            // The bound holds at every moment of the orbit, not only on average.
            assertTrue(fastest <= straight * 1.6f, "reach $reach: a low-drag orbit reached $fastest, above 1.6 × $straight")
        }
    }

    @Test
    fun counterThrustStillStopsTheCore() {
        val cruiseOffset = 0.79f * Desktop.longHalfAxis
        val backOffset = 0.5f * Desktop.longHalfAxis
        val behind = secondsToHalfSpeed(cruiseOffset, -backOffset, 0f, piloting)
        val releasedBehind = secondsToHalfSpeed(cruiseOffset, -backOffset, 0f, legacy)
        val sideways = speedShareAfter(cruiseOffset, 0f, backOffset, 0.5f, piloting)
        val releasedSideways = speedShareAfter(cruiseOffset, 0f, backOffset, 0.5f, legacy)
        println(
            "CORE FLIGHT counter-thrust $Desktop: pointer behind at reach 0.5 halves the speed in $behind s (0.5.0 " +
                "$releasedBehind s); pointer sideways keeps $sideways of it after 0.5 s (0.5.0 $releasedSideways)",
        )
        assertTrue(behind <= 0.45f, "pointing behind halves the speed in $behind s, not within 0.45 s")
        assertTrue(sideways >= 0.80f, "a sideways pointer keeps $sideways of the speed after 0.5 s, below 0.80")
    }

    @Test
    fun theFlightIsFrameRateIndependent() {
        val traces = FrameRates.associateWith { rate -> frameTrace(rate) }
        val reference = traces.getValue(FrameRates.first())
        assertTrue(reference.size >= SCRIPT_SECONDS * 120 / COMPARE_EVERY_STEPS, "reference trace too short: ${reference.size}")
        traces.forEach { (rate, trace) ->
            assertEquals(reference.keys, trace.keys, "compared step counts at $rate Hz")
            reference.forEach { (steps, fingerprint) ->
                assertEquals(fingerprint, trace.getValue(steps), "flight state after $steps steps at $rate Hz")
            }
        }
    }

    // ---- flights ----

    private class Viewport(val width: Float, val height: Float) {
        val longHalfAxis: Float get() = max(width, height) * 0.5f
        /** The direction (radians) of the long axis on screen: +x, or +y on a portrait viewport. */
        val longAxisAngle: Float get() = if (width >= height) 0f else PI.toFloat() / 2f
        override fun toString(): String = "${width.toInt()}x${height.toInt()}"
    }

    /** A held turn: the speed when the pointer swung and the lowest and highest speed in the next four seconds. */
    private class Turn(val start: Float, val minimum: Float, val maximum: Float) {
        val kept: Float get() = minimum / start
    }

    /** An orbit: its mean speed over the last third and its highest speed anywhere. */
    private class Orbit(val lateMean: Float, val maximum: Float)

    private fun world(steering: SteeringTuning): GameplayContentSnapshot = canonicalGameplayContent.copy(
        // No generated obstacles, and chunks so large that the Core never regenerates the window.
        obstacles = canonicalGameplayContent.obstacles.copy(meanPerChunk = 0f, chunkSize = HUGE_CHUNK),
        tempo = canonicalGameplayContent.tempo.copy(steering = steering),
    )

    /** A quiet run on [viewport]: no enemies, obstacles, sites or Overdrive, and the singularity cannot end it. */
    private fun flight(viewport: Viewport, content: GameplayContentSnapshot): MutableGameState =
        buildTestState(content = content).apply {
            resize(viewport.width, viewport.height)
            startRun()
            quietArenaForTesting()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
            overdriveBurnoutUntil = Float.MAX_VALUE
            worldOriginX = HUGE_CHUNK * 0.5
            worldOriginY = HUGE_CHUNK * 0.5
            refreshObstacleWindow()
        }

    /** Holds the pointer ([x], [y]) dp from the screen centre. */
    private fun MutableGameState.point(x: Float, y: Float) {
        updatePointer(screenWidth * 0.5f + x, screenHeight * 0.5f + y)
    }

    /** One production fixed step; nothing but the Core flies. */
    private fun MutableGameState.flyStep() {
        if (phase != GamePhase.RUNNING) fail("the flight ended: $message")
        simulateStep(step)
        enemies.clear()
        projectiles.clear()
    }

    private fun MutableGameState.fly(seconds: Float, each: (MutableGameState) -> Unit = {}) {
        repeat(stepsIn(seconds)) {
            flyStep()
            each(this)
        }
    }

    private fun stepsIn(seconds: Float): Int = (seconds / step).toInt()

    /** Cruises from rest with the pointer [offset] dp ahead along the viewport's long axis for [seconds]. */
    private fun cruise(
        viewport: Viewport,
        offset: Float,
        content: GameplayContentSnapshot,
        seconds: Float = CRUISE_SECONDS,
        prepare: MutableGameState.() -> Unit = {},
    ): MutableGameState = flight(viewport, content).apply {
        prepare()
        point(offset * cos(viewport.longAxisAngle), offset * sin(viewport.longAxisAngle))
        fly(seconds)
    }

    /** Cruises at [offset] for 12 s, then holds the pointer [degrees] off the heading at the same offset for 4 s. */
    private fun heldTurn(viewport: Viewport, offset: Float, degrees: Float, content: GameplayContentSnapshot): Turn {
        val state = cruise(viewport, offset, content, seconds = TURN_CRUISE_SECONDS)
        val start = state.speed
        val angle = viewport.longAxisAngle + degrees * PI.toFloat() / 180f
        state.point(offset * cos(angle), offset * sin(angle))
        var minimum = start
        var maximum = start
        state.fly(TURN_HOLD_SECONDS) {
            minimum = min(minimum, it.speed)
            maximum = max(maximum, it.speed)
        }
        return Turn(start, minimum, maximum)
    }

    /** From rest, the pointer circles the screen centre at [offset] dp and [omega] rad/s for [seconds]. */
    private fun orbit(
        viewport: Viewport,
        offset: Float,
        omega: Float,
        seconds: Float,
        content: GameplayContentSnapshot,
        prepare: MutableGameState.() -> Unit = {},
    ): Orbit {
        val state = flight(viewport, content).apply(prepare)
        val steps = stepsIn(seconds)
        val lateFrom = steps - steps / 3
        var lateSum = 0.0
        var maximum = 0f
        for (index in 0 until steps) {
            val angle = omega * index * step
            state.point(offset * cos(angle), offset * sin(angle))
            state.flyStep()
            maximum = max(maximum, state.speed)
            if (index >= lateFrom) lateSum += state.speed
        }
        return Orbit((lateSum / (steps - lateFrom)).toFloat(), maximum)
    }

    /** Seconds from rest until the Core flies at [speed] with the pointer [offset] dp ahead along the long axis. */
    private fun secondsToReach(viewport: Viewport, offset: Float, speed: Float, content: GameplayContentSnapshot): Float {
        val state = flight(viewport, content)
        state.point(offset * cos(viewport.longAxisAngle), offset * sin(viewport.longAxisAngle))
        repeat(stepsIn(10f)) { index ->
            state.flyStep()
            if (state.speed >= speed) return (index + 1) * step
        }
        fail("$viewport: the Core never reached $speed from rest")
    }

    /** Seconds until a desktop cruise at [cruiseOffset] halves its speed once the pointer moves to ([x], [y]). */
    private fun secondsToHalfSpeed(cruiseOffset: Float, x: Float, y: Float, content: GameplayContentSnapshot): Float {
        val state = cruise(Desktop, cruiseOffset, content)
        val half = state.speed * 0.5f
        state.point(x, y)
        repeat(stepsIn(5f)) { index ->
            state.flyStep()
            if (state.speed <= half) return (index + 1) * step
        }
        fail("the pointer at ($x, $y) never halved the speed")
    }

    /** The share of a desktop cruise's speed left [seconds] after the pointer moves to ([x], [y]). */
    private fun speedShareAfter(
        cruiseOffset: Float,
        x: Float,
        y: Float,
        seconds: Float,
        content: GameplayContentSnapshot,
    ): Float {
        val state = cruise(Desktop, cruiseOffset, content)
        val start = state.speed
        state.point(x, y)
        state.fly(seconds)
        return state.speed / start
    }

    /** The worst low-drag stack with grip and sling at the content bound and Overdrive that never ends. */
    private fun MutableGameState.lowDragStack() {
        dragCoefficient = LOW_STACK_DRAG
        magnetStrength = LOW_STACK_MAGNETISM
        gripStrength = content.tempo.steering.maximumStatMultiplier
        slingStrength = content.tempo.steering.maximumStatMultiplier
        overdriveTime = Float.MAX_VALUE
    }

    private fun report(label: String, turn: Turn, released: Turn) {
        println(
            "CORE FLIGHT $label: kept ${turn.kept} (start ${turn.start}, min ${turn.minimum}, max ${turn.maximum}); " +
                "0.5.0 kept ${released.kept} (start ${released.start}, min ${released.minimum})",
        )
    }

    // ---- frame rates ----

    /**
     * The flight script through `update` at [rate] Hz: inputs change only at k/6 s, a frame boundary at every compared
     * rate, so each lands after exactly 20·k steps. Fingerprints are taken every [COMPARE_EVERY_STEPS] steps.
     */
    private fun frameTrace(rate: Int): Map<Int, List<Int>> {
        val state = flight(Square, piloting).apply { applyPreferences(settings.copy(simulationSpeed = 1f)) }
        val fingerprints = linkedMapOf(0 to state.flightFingerprint())
        state.scriptedPointer(0)
        var steps = 0
        for (frame in 1..SCRIPT_SECONDS * rate) {
            state.update(1f / rate)
            steps += state.lastTransitionSteps
            assertEquals(GamePhase.RUNNING, state.phase, "the flight ended at $rate Hz after $steps steps")
            if (steps % COMPARE_EVERY_STEPS == 0 && steps !in fingerprints) fingerprints[steps] = state.flightFingerprint()
            if ((frame * INPUT_CHANGES_PER_SECOND) % rate == 0) {
                val change = frame * INPUT_CHANGES_PER_SECOND / rate
                assertEquals(change * STEPS_PER_INPUT_CHANGE, steps, "input change $change at $rate Hz")
                state.scriptedPointer(change)
            }
        }
        return fingerprints
    }

    /** Straight cruise, a held 90° turn, a reversal behind the centre, then a circling pointer. */
    private fun MutableGameState.scriptedPointer(change: Int) {
        val offset = 0.79f * Square.longHalfAxis
        when {
            change < 18 -> point(offset, 0f)
            change < 30 -> point(0f, offset)
            change < 36 -> point(0f, -0.5f * Square.longHalfAxis)
            else -> {
                val angle = change * 0.35f
                point(offset * cos(angle), offset * sin(angle))
            }
        }
    }

    private fun MutableGameState.flightFingerprint(): List<Int> = listOf(
        elapsed, coreX, coreY, velocityX, velocityY, cameraX, cameraY, smoothedVelocityX, smoothedVelocityY,
        coreStraightness, polarityStability,
    ).map { it.toRawBits() }

    private companion object {
        val Desktop = Viewport(1_280f, 800f)
        val Phone = Viewport(412f, 915f)
        /** Reach 0.79 of the long axis fits inside the calm area on both axes only on a square viewport. */
        val Square = Viewport(1_280f, 1_280f)
        /** Held turns and orbits: reach 0.79 on the square viewport and reach 0.79 of the desktop's short axis. */
        val TurnCases = listOf(Square to 0.79f * Square.longHalfAxis, Desktop to 0.79f * Desktop.height * 0.5f)
        val CarveOmegas = listOf(0.25f, 0.5f, 1f, 1.5f, 2f)
        val RunawayOmegas = listOf(0.25f, 0.5f, 1f, 2f, 3f)
        val FrameRates = listOf(30, 60, 144)
        const val HUGE_CHUNK: Float = 10_000_000f
        const val CRUISE_SECONDS = 25f
        const val TURN_CRUISE_SECONDS = 12f
        const val TURN_HOLD_SECONDS = 4f
        const val SPOOL_SPEED = 1_500f
        /** Base drag × 0.6 × Perpetual Vector's 0.15 × Frictionless 0.25. */
        const val LOW_STACK_DRAG = 0.29f * 0.6f * 0.15f * 0.25f
        const val LOW_STACK_MAGNETISM = 4.185f
        const val SCRIPT_SECONDS = 10
        const val INPUT_CHANGES_PER_SECOND = 6
        const val STEPS_PER_INPUT_CHANGE = 20
        const val COMPARE_EVERY_STEPS = 4
    }
}
