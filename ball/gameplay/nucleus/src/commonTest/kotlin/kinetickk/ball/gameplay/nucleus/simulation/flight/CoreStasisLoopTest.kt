// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.projection.toRenderModel
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core Freeze through the whole step ([simulateStep]): a Core pinned on a rock, stalled by a pointer parked at the screen
 * edge or crawling under the Brake freezes solid and the run ends as CORE FROZEN past invulnerability and revivals, a
 * Dash out of the pin breaks the freeze, a closed choice holds it, and Easy mode shatters the ice instead. Quiet arenas
 * with no generated obstacles; every run is short enough for the browser test runner.
 */
class CoreStasisLoopTest {
    @Test
    fun aRockPinnedCoreFreezesSolidPastInvulnerability() {
        val state = pinnedState(invulnerable = true)
        var pinnedAt = Float.NaN
        val endedAt = state.stepUntilEnd(12f) { t -> if (pinnedAt.isNaN() && state.speed < 5f) pinnedAt = t }
        assertTrue(pinnedAt < 1f, "the rock holds the Core (at $pinnedAt s)")
        // The filtered speed falls below 100 u/s within a fraction of a second, then 4.2 s of freeze.
        assertTrue(endedAt - pinnedAt in 4.1f..5f, "froze ${endedAt - pinnedAt} s after the pin")
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(CoreStasisNotices.FROZEN, state.message)
        assertEquals("ЯДРО ЗАМЁРЗЛО", state.message.localizedContent(AppLanguage.Russian))
        val render = state.toRenderModel()
        assertEquals(1f, render.coreStasis, "the death frame shows the Core frozen solid")
        assertEquals(CoreStasisMotion.FROZEN, render.coreStasisMotion)
    }

    /**
     * A pointer parked at the screen edge drains the polarity field until the Core stalls into a crawl (measured from
     * full polarity: below 20 u/s after 14 s); from a drained field the stall comes at once and the Core freezes solid.
     */
    @Test
    fun anEdgePinnedPointerStallsAndFreezes() {
        val state = quietState(invulnerable = true).apply {
            pointerX = screenWidth
            pointerY = screenHeight * 0.5f
            polarityStability = 0.15f
            velocityX = 200f
        }
        var stalledAt = Float.NaN
        val endedAt = state.stepUntilEnd(12f) { t -> if (stalledAt.isNaN() && state.speed < 100f) stalledAt = t }
        assertTrue(stalledAt in 0.5f..4f, "the Core stalls (at $stalledAt s)")
        assertTrue(endedAt - stalledAt in 4.2f..5f, "and freezes solid (at $endedAt s)")
        assertEquals(CoreStasisNotices.FROZEN, state.message)
    }

    /** The Brake held with the pointer near the screen centre settles under 100 u/s (about 78 at 60 dp) and freezes. */
    @Test
    fun aBrakeHeldCrawlFreezes() {
        val state = quietState(invulnerable = false).apply {
            pointerX = screenWidth * 0.5f + 60f
            pointerY = screenHeight * 0.5f
            setBrake(true)
        }
        var settled = 0f
        val endedAt = state.stepUntilEnd(12f) { t -> if (t > 2f) settled = maxOf(settled, state.speed) }
        assertTrue(settled < 100f, "the brake holds the Core under 100 u/s (peak $settled after 2 s)")
        assertTrue(endedAt < 7f, "froze at $endedAt s")
        assertEquals(CoreStasisNotices.FROZEN, state.message)
    }

    @Test
    fun aRevivalNeverAnswersAFreeze() {
        val state = pinnedState(invulnerable = false).apply {
            matrix = matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        }
        state.stepUntilEnd(12f)
        assertEquals(CoreStasisNotices.FROZEN, state.message)
        assertFalse(state.secondWindSpent)
    }

    /** A Dash out of the pin carries the Core well above the thaw speed: the freeze recedes instead of finishing. */
    @Test
    fun aDashOutOfThePinThawsTheFreeze() {
        val state = pinnedState(invulnerable = true)
        state.stepUntil { state.coreStasis.freeze >= 0.5f }
        val peak = state.coreStasis.freeze
        // Up and away from the rock, never across the Core (a pointer swept over the Core is a singularity contact).
        state.pointerX = state.screenWidth * 0.5f
        state.pointerY = state.screenHeight * 0.5f - 250f
        state.requestDash()
        state.stepFor(2.5f)
        assertEquals(GamePhase.RUNNING, state.phase, state.message)
        assertTrue(state.coreStasis.freeze < peak - 0.3f, "the freeze fell from $peak to ${state.coreStasis.freeze}")
    }

    /** A choice opened mid-freeze stops every clock; closing it holds the freeze for 0.6 s before it counts again. */
    @Test
    fun aClosedChoiceHoldsTheFreeze() {
        val state = pinnedState(invulnerable = true)
        state.stepUntil { state.coreStasis.freeze >= 0.5f }
        val frozen = state.coreStasis.freeze
        state.phase = GamePhase.CHOICE
        repeat(50) { state.update(0.1f) }
        assertEquals(frozen, state.coreStasis.freeze, "frozen while choosing")
        state.finishChoice(GameplayAudioCue.UI_CLICK)
        state.stepFor(0.6f)
        assertEquals(frozen, state.coreStasis.freeze, "held for 0.6 s after the choice closes")
        state.stepFor(0.1f)
        assertTrue(state.coreStasis.freeze > frozen, "then it counts again")
    }

    @Test
    fun easyModeShattersTheIceAndPlaysOn() {
        val state = pinnedState(invulnerable = false, runMode = RunMode.EASY)
        val integrity = state.hp + state.shield
        var shatters = 0
        state.stepFor(11f) { if (state.takeSoundCues().contains(GameplayAudioCue.CORE_FROZEN)) shatters++ }
        assertEquals(2, shatters, "a pinned Easy Core shatters its ice every 4.2 s")
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(integrity, state.hp + state.shield, "the shatter costs nothing")
        assertTrue(state.coreStasis.freeze < 1f)
    }
}

private const val STEP = MutableGameState.FIXED_STEP

/** Realspace without structures or generated obstacles. */
private val emptyWorld: GameplayContentSnapshot =
    structuresDisabledContent.copy(obstacles = structuresDisabledContent.obstacles.copy(meanPerChunk = 0f))

/** A quiet started run in [emptyWorld] (no enemies, spawns, sites or obstacles), tier 0, past the run-start hold. */
private fun quietState(invulnerable: Boolean, runMode: RunMode = RunMode.STANDARD): MutableGameState =
    MutableGameState(emptyWorld, seed = 5_150, initialMatter = 0, runMode = runMode).apply {
        coreShape = CoreShape.ORB
        unlockWeaponsForTesting(listOf(WeaponId.FLUX_WAKE))
        startingWeapon = WeaponId.FLUX_WAKE
        resize(1_280f, 720f)
        startRun()
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        spawnClock = Float.MAX_VALUE
        nextEliteAt = Float.MAX_VALUE
        runGrace = 0f
        coreStasis.holdSeconds = 0f
        quietArenaForTesting()
        clearObstaclesForTesting()
        if (invulnerable) invulnerableUntil = Float.MAX_VALUE
        takeVisualFxCues()
        takeSoundCues()
        takeProgressUpdate()
    }

/** The Core one unit from a rock on its right, with the pointer 120 dp right of the screen centre pressing into it. */
private fun pinnedState(invulnerable: Boolean, runMode: RunMode = RunMode.STANDARD): MutableGameState =
    quietState(invulnerable, runMode).apply {
        placeObstaclesForTesting(testObstacle(coreX + MutableGameState.CORE_RADIUS + 80f + 1f, coreY, radius = 80f))
        pointerX = screenWidth * 0.5f + 120f
        pointerY = screenHeight * 0.5f
    }

/** Runs whole steps for [seconds] (stopping when the run ends), calling [onStep] with the time after each step. */
private fun MutableGameState.stepFor(seconds: Float, onStep: (Float) -> Unit = {}) {
    val steps = (seconds / STEP).roundToInt()
    for (index in 1..steps) {
        if (phase != GamePhase.RUNNING) return
        simulateStep(STEP)
        onStep(index * STEP)
    }
}

/** [stepFor] at most [seconds]; the time the run ended, or NaN when it still runs. */
private fun MutableGameState.stepUntilEnd(seconds: Float, onStep: (Float) -> Unit = {}): Float {
    var endedAt = Float.NaN
    stepFor(seconds) { t ->
        onStep(t)
        if (endedAt.isNaN() && phase != GamePhase.RUNNING) endedAt = t
    }
    return endedAt
}

/** Steps until [done] holds (at most 10 s). */
private fun MutableGameState.stepUntil(done: () -> Boolean) {
    var steps = 0
    while (!done() && phase == GamePhase.RUNNING && steps < (10f / STEP).toInt()) {
        simulateStep(STEP)
        steps++
    }
    check(done()) { "the condition never held" }
}
