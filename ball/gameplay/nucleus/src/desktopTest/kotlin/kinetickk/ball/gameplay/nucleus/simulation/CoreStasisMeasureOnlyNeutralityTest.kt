// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.performance.canonicalStateFingerprint
import kinetickk.ball.gameplay.nucleus.performance.exactRenderValueFacts
import kinetickk.ball.gameplay.nucleus.projection.toRenderModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Measure-only Core Stasis cannot bias a probe: the reference NoPoi run (seed 2026) flown for 300 s with the system
 * off and in measure-only mode ends in the same state and the same render facts once the measured run's four detector
 * fields are copied over (its frozen and fracture clocks never move).
 */
class CoreStasisMeasureOnlyNeutralityTest {
    @Test
    fun measureOnlyPlaysExactlyLikeCoreStasisOff() {
        val disabled = fly(canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.DISABLED))
        val measured = fly(canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.MEASURE_ONLY))
        assertEquals(GamePhase.RUNNING, measured.phase)
        assertTrue(measured.coreStasis.speedEma > 0f, "the detector measured the flight")
        assertEquals(0f, measured.coreStasis.frozenSeconds)
        assertEquals(0f, measured.coreStasis.fractureClock)
        assertNotEquals(canonicalStateFingerprint(measured), canonicalStateFingerprint(disabled), "the detector fields are witnessed")
        disabled.coreStasis.anchorX = measured.coreStasis.anchorX
        disabled.coreStasis.anchorY = measured.coreStasis.anchorY
        disabled.coreStasis.speedEma = measured.coreStasis.speedEma
        disabled.coreStasis.stillSeconds = measured.coreStasis.stillSeconds
        assertEquals(canonicalStateFingerprint(measured), canonicalStateFingerprint(disabled))
        // The render facts minus the content snapshot itself (its Core Stasis tuning is what differs).
        fun facts(state: MutableGameState) = exactRenderValueFacts(state.toRenderModel()).filter { it.first != "content" }
        assertEquals(facts(measured), facts(disabled))
    }

    /** NoPoiPlaythroughTest's fresh run on the reference seed, stopped at 300 s. */
    private fun fly(content: GameplayContentSnapshot): MutableGameState {
        val state = MutableGameState(content, seed = 2026)
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.startRun()
        val bot = NoPoiBot()
        while (state.elapsed < 300f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        return state
    }
}
