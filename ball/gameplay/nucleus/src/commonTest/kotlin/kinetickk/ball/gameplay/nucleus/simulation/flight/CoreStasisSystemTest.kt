// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core Freeze rules, driven step by step with a scripted Core (its velocity written before each [updateCoreStasis])
 * against the shipped thresholds: filtered speed (0.3 s) below 100 u/s freezes over 4.2 s, at or above 125 u/s thaws
 * over 3 s, in between holds; a full freeze ends the run as CORE FROZEN. Each test starts past the run-start hold.
 */
class CoreStasisSystemTest {
    @Test
    fun aCoreAtRestFreezesSolidOnTheStepThatCompletesTheFreezeTime() {
        val state = detectorState()
        val steps = (4.2f / STEP).roundToInt()
        state.drive(steps - 1, still())
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals((steps - 1).toFloat() / steps, state.coreStasis.freeze, 1e-4f)
        state.takeSoundCues()
        state.drive(1, still())
        assertEquals(GamePhase.GAME_OVER, state.phase, "frozen solid after exactly $steps steps (4.2 s)")
        assertEquals(CoreStasisNotices.FROZEN, state.message)
        assertEquals(1f, state.coreStasis.freeze)
        assertEquals(CoreStasisMotion.FROZEN, state.coreStasisMotion())
        val sounds = state.takeSoundCues()
        assertTrue(GameplayAudioCue.CORE_FROZEN in sounds && GameplayAudioCue.GAME_OVER in sounds, "sounds $sounds")
    }

    @Test
    fun theFreezeGrowsSteadilyAndThawsGradually() {
        val state = detectorState()
        state.drive(seconds(2.1f), still())
        assertEquals(0.5f, state.coreStasis.freeze, 1e-4f)
        assertEquals(CoreStasisMotion.FREEZING, state.coreStasisMotion())
        var previous = state.coreStasis.freeze
        var clearedAt = Float.NaN
        state.drive(seconds(4f), constant(600f)) { t ->
            val freeze = state.coreStasis.freeze
            // The filtered speed takes 0.07 s to pass 125 u/s from rest.
            if (t > 0.08f) assertTrue(freeze <= previous, "the freeze never grows at 600 u/s (at $t s)")
            assertTrue(previous - freeze <= STEP / 3f + 1e-6f, "it thaws at most 1/3 per second (at $t s)")
            previous = freeze
            if (clearedAt.isNaN() && freeze <= 0f) clearedAt = t
        }
        assertTrue(clearedAt in 1.5f..1.75f, "half a freeze thaws in 1.5 s plus the filter's rise, at $clearedAt s")
        assertEquals(CoreStasisMotion.CLEAR, state.coreStasisMotion())
    }

    /** Hovering around the freeze speed never thaws: the time under it adds up to a full freeze. */
    @Test
    fun anOscillationAroundTheFreezeSpeedStillFreezes() {
        for ((period, latest) in listOf(1f to 8f, 5f to 7.5f)) {
            val state = detectorState()
            var previous = 0f
            val endedAt = state.driveUntilEnd(seconds(30f), square(90f, 110f, period / 2f, period / 2f)) {
                assertTrue(state.coreStasis.freeze >= previous, "90/110 at $period s never thaws")
                previous = state.coreStasis.freeze
            }
            assertTrue(endedAt in 4.2f..latest, "90/110 at $period s froze at $endedAt s")
        }
    }

    /** Fast movement thaws only above the thaw speed and only for the time spent there; the account is by time. */
    @Test
    fun aWideOscillationCountsTimeBelowAgainstTimeAbove() {
        val even = detectorState()
        assertTrue(even.driveUntilEnd(seconds(60f), square(50f, 150f, 1f, 1f)).isNaN(), "50/150 one second each lives a minute")
        assertTrue(even.coreStasis.freeze < 0.6f, "and gathers at most ${even.coreStasis.freeze}")
        val mostlySlow = detectorState()
        val endedAt = mostlySlow.driveUntilEnd(seconds(60f), square(50f, 150f, 2f, 1f))
        assertEquals(8.05f, endedAt, 0.1f, "two seconds slow for one fast freezes")
        val mostlyFast = detectorState()
        assertTrue(mostlyFast.driveUntilEnd(seconds(60f), square(50f, 150f, 1f, 2f)).isNaN())
        assertEquals(0f, mostlyFast.coreStasis.freeze, 0.01f, "one second slow for two fast stays clear")
    }

    /** A jiggle whose filtered speed stays under the freeze speed freezes exactly as fast as standing still. */
    @Test
    fun aJiggleUnderTheFreezeSpeedNeverHelps() {
        val state = detectorState()
        // 20 u at 1 Hz: the speed peaks at 126 u/s, the filtered speed stays within 66..94.
        val endedAt = state.driveUntilEnd(seconds(10f), { t -> abs(20f * TWO_PI * cos(TWO_PI * t)) })
        assertEquals(4.2f, endedAt, STEP * 0.5f)
    }

    /** The owner's rule is speed: a vigorous wiggle (±60 u at 3 Hz, about 720 u/s) is movement. */
    @Test
    fun aVigorousWiggleIsMovement() {
        val state = detectorState()
        val frequency = 3f * TWO_PI
        val endedAt = state.driveUntilEnd(seconds(30f), { t -> abs(60f * frequency * cos(frequency * t)) })
        assertTrue(endedAt.isNaN())
        assertTrue(state.coreStasis.freeze < 0.1f, "only the filter's warm-up counts (${state.coreStasis.freeze})")
    }

    /** A brake-held crawl under 100 u/s freezes; one inside the hold band keeps what it has but never thaws. */
    @Test
    fun aBrakeHeldCrawlFreezesAndTheHoldBandNeverThaws() {
        val crawl = detectorState().apply { coreStasis.speedFilter = 80f }
        assertEquals(4.2f, crawl.driveUntilEnd(seconds(10f), constant(80f)), STEP * 0.5f)
        val band = detectorState().apply {
            coreStasis.speedFilter = 113f
            coreStasis.freeze = 0.4f
        }
        assertTrue(band.driveUntilEnd(seconds(60f), constant(113f)).isNaN())
        assertEquals(0.4f, band.coreStasis.freeze, "113 u/s neither freezes nor thaws")
        assertEquals(CoreStasisMotion.HOLDING, band.coreStasisMotion())
    }

    /** Dash bursts out of a braked crawl (640 u/s braking back to 30 u/s every 1.9 s) do not outrun the freeze. */
    @Test
    fun dashBurstsFromNearStillStillFreeze() {
        val state = detectorState()
        val endedAt = state.driveUntilEnd(seconds(60f), { t -> max(30f, 640f * exp(-5.2f * (t % 1.9f))) })
        assertEquals(11.23f, endedAt, 0.1f)
    }

    @Test
    fun theRunStartHoldsTheFreezeForItsWindow() {
        val state = buildTestState()
        assertEquals(1.5f, state.coreStasis.holdSeconds)
        var firstGrowth = Float.NaN
        val endedAt = state.driveUntilEnd(seconds(10f), still()) { t ->
            if (firstGrowth.isNaN() && state.coreStasis.freeze > 0f) firstGrowth = t
        }
        assertEquals(1.5f + STEP, firstGrowth, STEP * 0.5f)
        assertEquals(5.7f, endedAt, STEP * 1.5f, "the hold, then the whole freeze")
    }

    /** A closed choice, a layer switch and a host without input hold the freeze: it neither grows nor thaws. */
    @Test
    fun everyHoldWindowKeepsTheFreezeWhereItIs() {
        val choice = detectorState().apply { coreStasis.freeze = 0.5f }
        choice.finishChoice(GameplayAudioCue.UI_CLICK)
        assertHeldFor(choice, 0.6f, "a closed choice")
        val layer = detectorState().apply {
            quietArenaForTesting()
            coreStasis.freeze = 0.5f
        }
        layer.enterDimensionForTesting(WorldLayer.ZENITH)
        assertHeldFor(layer, 1f, "a layer switch")
        val input = detectorState().apply { coreStasis.freeze = 0.5f }
        input.setCoreStasisInputHeld(true)
        input.drive(seconds(3f), still())
        input.drive(seconds(3f), constant(600f))
        assertEquals(0.5f, input.coreStasis.freeze, "no input: held while still and while fast")
        assertEquals(CoreStasisMotion.HOLDING, input.coreStasisMotion())
        input.setCoreStasisInputHeld(false)
        input.drive(1, constant(600f))
        assertTrue(input.coreStasis.freeze < 0.5f, "input back: it thaws")
    }

    @Test
    fun aHoldNeverShortensARunningOne() {
        val state = detectorState()
        state.holdCoreStasis(1f)
        state.holdCoreStasis(0.6f)
        assertEquals(1f, state.coreStasis.holdSeconds)
    }

    @Test
    fun pauseAndChoiceFreezeTheFreeze() {
        val state = detectorState()
        state.drive(seconds(2f), still())
        val held = state.coreStasis.freeze
        state.togglePause()
        repeat(100) { state.update(0.1f) }
        state.updateCoreStasis(STEP)
        assertEquals(held, state.coreStasis.freeze, "paused")
        state.togglePause()
        state.phase = GamePhase.CHOICE
        repeat(100) { state.update(0.1f) }
        state.updateCoreStasis(STEP)
        assertEquals(held, state.coreStasis.freeze, "choosing")
        state.phase = GamePhase.RUNNING
        state.drive(1, still())
        assertEquals(held + STEP / 4.2f, state.coreStasis.freeze, 1e-5f, "resumes from where it stopped")
    }

    @Test
    fun everyLayerCountsTheNadirToo() {
        val state = detectorState().apply { quietArenaForTesting() }
        state.enterDimensionForTesting(WorldLayer.NADIR)
        state.coreStasis.holdSeconds = 0f
        assertEquals(4.2f, state.driveUntilEnd(seconds(10f), still()), STEP * 0.5f)
        assertEquals(CoreStasisNotices.FROZEN, state.message)
    }

    @Test
    fun aFullFreezeEndsTheRunPastShieldsInvulnerabilityAndRevivals() {
        val state = detectorState().apply {
            invulnerableUntil = Float.MAX_VALUE
            maxShield = 50f
            shield = 50f
            matrix = matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        }
        val hp = state.hp
        assertEquals(4.2f, state.driveUntilEnd(seconds(10f), still()), STEP * 0.5f)
        assertEquals(CoreStasisNotices.FROZEN, state.message)
        assertFalse(state.secondWindSpent, "no revival answers a freeze")
        assertEquals(hp, state.hp, "the freeze takes no integrity: it ends the run")
        assertEquals(50f, state.shield)
    }

    @Test
    fun easyModeShattersTheIceAndStartsOver() {
        val state = MutableGameState(canonicalGameplayContent, seed = 5_150, initialMatter = 0,
            runMode = kinetickk.ball.gameplay.api.RunMode.EASY).apply {
            resize(1_280f, 720f)
            startRun()
            coreStasis.holdSeconds = 0f
            messageTime = 0f
            takeVisualFxCues()
            takeSoundCues()
        }
        var shatters = 0
        state.drive(seconds(9f), still()) {
            if (state.takeSoundCues().contains(GameplayAudioCue.CORE_FROZEN)) {
                shatters++
                assertEquals(0f, state.coreStasis.freeze, "the ice shatters and the freeze starts over")
                assertEquals("BOOP", state.message)
                val cryo = FxColorCode.elementTone(StatusElement.CRYO)
                assertTrue(state.takeVisualFxCues().any { it is VisualFxCue.Burst && it.colorIndex == cryo }, "ice shards fly")
            }
        }
        assertEquals(2, shatters, "a still Easy Core shatters every 4.2 s")
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun theMotionTellsWhereTheFreezeHeads() {
        val state = detectorState()
        assertEquals(CoreStasisMotion.CLEAR, state.coreStasisMotion())
        state.drive(seconds(1f), still())
        assertEquals(CoreStasisMotion.FREEZING, state.coreStasisMotion())
        state.coreStasis.speedFilter = 110f
        assertEquals(CoreStasisMotion.HOLDING, state.coreStasisMotion())
        state.coreStasis.speedFilter = 125f
        assertEquals(CoreStasisMotion.THAWING, state.coreStasisMotion())
        state.holdCoreStasis(0.5f)
        assertEquals(CoreStasisMotion.HOLDING, state.coreStasisMotion())
    }

    @Test
    fun startRunClearsTheFreezeButKeepsTheHostInput() {
        val state = detectorState()
        state.populateCoreStasisRetainedState()
        state.startRun()
        assertEquals(0f, state.coreStasis.speedFilter)
        assertEquals(0f, state.coreStasis.freeze)
        assertEquals(state.content.coreStasis.runStartHoldSeconds, state.coreStasis.holdSeconds)
        assertTrue(state.coreStasis.inputHeld, "a fact of the host, which a run start does not change")
    }

    @Test
    fun aRebaseLeavesTheFreezeAlone() {
        val state = detectorState()
        state.drive(seconds(1f), still())
        val freeze = state.coreStasis.freeze
        val filter = state.coreStasis.speedFilter
        state.coreX = 250_010f
        state.rebaseWorldIfNeeded()
        assertEquals(0f, state.coreX)
        assertEquals(freeze, state.coreStasis.freeze)
        assertEquals(filter, state.coreStasis.speedFilter)
    }

    @Test
    fun disabledContentMovesNoField() {
        val state = buildTestState(content = canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.DISABLED))
        state.holdCoreStasis(1f)
        state.drive(seconds(30f), still())
        state.drive(seconds(5f), constant(600f))
        assertEquals(0f, state.coreStasis.speedFilter)
        assertEquals(0f, state.coreStasis.freeze)
        assertEquals(0f, state.coreStasis.holdSeconds)
        assertEquals(0f, state.coreStasisLevel())
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun measureOnlyMeasuresButNeverActs() {
        val state = detectorState(canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.MEASURE_ONLY))
        state.drive(seconds(10f), still()) {
            assertEquals(0f, state.coreStasisLevel())
            assertEquals(CoreStasisMotion.CLEAR, state.coreStasisMotion())
        }
        assertEquals(1f, state.coreStasis.freeze, "the freeze runs")
        assertEquals(GamePhase.RUNNING, state.phase)
        state.drive(seconds(4f), constant(600f))
        assertEquals(0f, state.coreStasis.freeze)
        assertTrue(state.takeVisualFxCues().isEmpty(), "measure-only sheds no frost")
        assertTrue(state.takeSoundCues().isEmpty())
    }

    @Test
    fun thawShedsFrostOnQuarterCrossings() {
        val state = detectorState().apply {
            coreStasis.freeze = 0.99f
            coreStasis.speedFilter = 600f
        }
        state.drive(seconds(4f), constant(600f))
        val cryo = FxColorCode.elementTone(StatusElement.CRYO)
        val cues = state.takeVisualFxCues()
        assertEquals(4, cues.count { it is VisualFxCue.Burst && it.colorIndex == cryo }, "one burst per quarter")
        assertEquals(1, cues.count { it is VisualFxCue.ShockwaveAdded && it.colorIndex == cryo }, "one ring at zero")
        assertEquals(5, cues.size)
    }

    @Test
    fun theCollapsingOrbitMayNeverAskForAPaceThatCannotThaw() {
        assertFailsWith<IllegalArgumentException> { canonicalGameplayContent.copy(coreStasis = CoreStasisTuning(thawSpeed = 140f)) }
        canonicalGameplayContent.copy(coreStasis = CoreStasisTuning(enabled = false, thawSpeed = 140f))
    }

    private fun assertHeldFor(state: MutableGameState, seconds: Float, what: String) {
        assertEquals(seconds, state.coreStasis.holdSeconds, 1e-6f, what)
        state.drive(seconds(seconds), still()) { assertEquals(0.5f, state.coreStasis.freeze, "$what holds it") }
        assertEquals(CoreStasisMotion.FREEZING, state.coreStasisMotion())
        state.drive(1, still())
        assertTrue(state.coreStasis.freeze > 0.5f, "$what ends and the freeze grows again")
    }
}

private const val STEP = MutableGameState.FIXED_STEP
private const val TWO_PI = (2.0 * PI).toFloat()

/** A started quiet run past its run-start hold, at rest with a filtered speed of 0. */
private fun detectorState(content: kinetickk.ball.content.api.GameplayContentSnapshot = canonicalGameplayContent): MutableGameState =
    buildTestState(content = content).apply { coreStasis.holdSeconds = 0f }

private fun seconds(value: Float): Int = (value / STEP).roundToInt()

/**
 * Steps the detector [steps] times with the Core at the scalar speed [speed] (time since the segment start to u/s,
 * written as a velocity along +x) and calls [onStep] after each step with that time.
 */
private fun MutableGameState.drive(steps: Int, speed: (Float) -> Float, onStep: (Float) -> Unit = {}) {
    for (index in 1..steps) {
        val t = index * STEP
        velocityX = speed(t)
        velocityY = 0f
        updateCoreStasis(STEP)
        onStep(t)
    }
}

/** [drive] until the run ends; the time it ended, or NaN when it still runs. */
private fun MutableGameState.driveUntilEnd(steps: Int, speed: (Float) -> Float, onStep: (Float) -> Unit = {}): Float {
    var endedAt = Float.NaN
    drive(steps, speed) { t ->
        if (phase == GamePhase.RUNNING) onStep(t) else if (endedAt.isNaN()) endedAt = t
    }
    return endedAt
}

private fun still(): (Float) -> Float = { 0f }

private fun constant(speed: Float): (Float) -> Float = { speed }

/** [low] for [lowSeconds], then [high] for [highSeconds], repeated. */
private fun square(low: Float, high: Float, lowSeconds: Float, highSeconds: Float): (Float) -> Float =
    { t -> if (t % (lowSeconds + highSeconds) < lowSeconds) low else high }
