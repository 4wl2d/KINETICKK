// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.projection.toRenderModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core Stasis through the whole step ([simulateStep]): a Core pinned on a rock or stalled by a pointer parked at the
 * screen edge freezes, the frozen Core fractures on the scorch path and dies with its own cause, invulnerability and a
 * revival answer the fracture, and Easy mode freezes but never fractures. Quiet arenas with no generated obstacles.
 */
class CoreStasisLoopTest {
    @Test
    fun aWallPinnedCoreFreezesThroughTheStep() {
        val state = pinnedState(invulnerable = true)
        val base = state.deepCopy().cooldown(1f)
        var crossed = Float.NaN
        state.stepFor(30f) { t -> if (crossed.isNaN() && state.coreStasisLevel() > 0f) crossed = t }
        assertTrue(state.speed < 5f, "the rock holds the Core (speed ${state.speed})")
        assertEquals(1f, state.coreStasisLevel())
        assertEquals(10f, crossed, 0.1f, "the grace runs out about 10 s after the pin")
        assertEquals(base / 0.35f, state.cooldown(1f), base * 1e-4f, "weapon clocks run at 0.35")
    }

    /**
     * A pointer parked at the screen edge drains the polarity field until the Core stalls into a crawl of a few u/s
     * (measured: below 20 u/s after 14.05 s, full stasis at 36.6 s, 1.5 u/s at 150 s); the crawl drags the leash below
     * the travel speed, so the Core stays frozen even after it has crawled out of the radius.
     */
    @Test
    fun anEdgePinnedPointerStallsAndFreezes() {
        val state = quietState(invulnerable = true).apply {
            pointerX = screenWidth
            pointerY = screenHeight * 0.5f
        }
        var stalledAt = Float.NaN
        var frozenAt = Float.NaN
        state.stepFor(150f) { t ->
            if (stalledAt.isNaN() && t > 1f && state.speed < 20f) stalledAt = t
            if (frozenAt.isNaN() && state.coreStasisLevel() >= 1f) frozenAt = t
        }
        assertTrue(stalledAt < 20f, "the Core stalls (at $stalledAt s)")
        assertTrue(frozenAt < 40f, "and freezes fully (at $frozenAt s)")
        assertEquals(1f, state.coreStasisLevel(), "still frozen at 150 s")
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun aFrozenCoreFracturesAndDiesWithTheFrozenReason() {
        val state = pinnedState(invulnerable = false)
        val maxHp = state.maxHp
        val integrity = state.hp + state.shield
        val ticks = kotlin.math.ceil(integrity / (maxHp * 0.05f) - 1e-4f).toInt()
        var fracturingAt = Float.NaN
        var firstLossAt = Float.NaN
        var endedAt = Float.NaN
        var previous = integrity
        state.stepFor(120f) { t ->
            if (fracturingAt.isNaN() && state.coreStasisFracturing()) fracturingAt = t
            val now = state.hp + state.shield
            if (now < previous) {
                if (firstLossAt.isNaN()) firstLossAt = t
                if (state.phase == GamePhase.RUNNING) assertEquals(maxHp * 0.05f, previous - now, maxHp * 1e-4f, "one tick at $t")
            }
            previous = now
            if (endedAt.isNaN() && state.phase != GamePhase.RUNNING) endedAt = t
        }
        assertEquals(28f, fracturingAt, 0.1f)
        assertEquals(29.5f, firstLossAt, 0.1f)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(CoreStasisNotices.FROZEN, state.message)
        assertEquals(firstLossAt + (ticks - 1) * 1.5f, endedAt, 0.05f, "$ticks ticks from full integrity")
        assertEquals("ЯДРО ЗАМЁРЗЛО", state.message.localizedContent(AppLanguage.Russian))
    }

    @Test
    fun invulnerabilityBlocksTheFracture() {
        val state = pinnedState(invulnerable = true)
        val integrity = state.hp + state.shield
        var clockWrapped = false
        var lastClock = 0f
        state.stepFor(60f) {
            if (state.coreStasis.fractureClock < lastClock) clockWrapped = true
            lastClock = state.coreStasis.fractureClock
        }
        assertEquals(integrity, state.hp + state.shield)
        assertTrue(state.coreStasisFracturing())
        assertTrue(clockWrapped, "the tick clock keeps cycling; blocked ticks are lost")
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    @Test
    fun aRevivalAnswersALethalFracture() {
        val state = pinnedState(invulnerable = false).apply {
            matrix = matrix.copy(secondWind = MatrixEffect.SecondWind(0.3f, 2f))
        }
        var revivedAt = Float.NaN
        var lossAfterRevival = false
        var previous = state.hp
        state.stepFor(120f) { t ->
            if (revivedAt.isNaN() && state.secondWindSpent) {
                revivedAt = t
                assertEquals(GamePhase.RUNNING, state.phase)
                assertEquals(22f, state.coreStasis.stillSeconds, "a revival does not thaw the Core")
            }
            if (!revivedAt.isNaN() && state.hp < previous && state.phase == GamePhase.RUNNING) lossAfterRevival = true
            previous = state.hp
        }
        assertFalse(revivedAt.isNaN(), "Second Wind answered the lethal tick")
        assertTrue(lossAfterRevival, "the revived Core keeps fracturing")
        assertEquals(CoreStasisNotices.FROZEN, state.message)
    }

    @Test
    fun easyModeFreezesButNeverFractures() {
        val state = pinnedState(invulnerable = false, runMode = RunMode.EASY)
        val integrity = state.hp + state.shield
        state.stepFor(120f) {
            assertFalse(state.coreStasisFracturing())
            assertEquals(0f, state.coreStasis.frozenSeconds)
            assertEquals(0f, state.coreStasis.fractureClock)
        }
        assertEquals(1f, state.coreStasisLevel())
        assertEquals(integrity, state.hp + state.shield)
        assertEquals(GamePhase.RUNNING, state.phase)
        val render = state.toRenderModel()
        assertEquals(1f, render.coreStasis)
        assertFalse(render.coreStasisFracturing)
    }
}

private const val STEP = MutableGameState.FIXED_STEP

/** Realspace without structures or generated obstacles. */
private val emptyWorld: GameplayContentSnapshot =
    structuresDisabledContent.copy(obstacles = structuresDisabledContent.obstacles.copy(meanPerChunk = 0f))

/** A quiet started run in [emptyWorld] (no enemies, spawns, sites or obstacles), tier 0, no relics or items. */
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
        placeObstaclesForTesting(testObstacle(coreX + CORE_RADIUS_FOR_PIN + 80f + 1f, coreY, radius = 80f))
        pointerX = screenWidth * 0.5f + 120f
        pointerY = screenHeight * 0.5f
    }

private const val CORE_RADIUS_FOR_PIN = MutableGameState.CORE_RADIUS

/** Runs whole steps for [seconds] (stopping when the run ends), calling [onStep] with the time after each step. */
private fun MutableGameState.stepFor(seconds: Float, onStep: (Float) -> Unit) {
    val steps = (seconds / STEP).roundToInt()
    for (index in 1..steps) {
        if (phase != GamePhase.RUNNING) return
        simulateStep(STEP)
        onStep(index * STEP)
    }
}
