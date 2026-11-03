// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreStasisTuningTest {
    @Test
    fun coreFreezeShipsTheDecidedValues() {
        val shipped = CoreStasisTuning()
        assertTrue(shipped.enabled)
        assertTrue(shipped.applyEffects)
        assertEquals(100f, shipped.freezeSpeed, "the HUD's 100")
        assertEquals(125f, shipped.thawSpeed)
        assertEquals(0.30f, shipped.speedFilterSeconds)
        assertEquals(4.2f, shipped.freezeSeconds)
        assertEquals(3f, shipped.thawSeconds)
        assertEquals(1.5f, shipped.runStartHoldSeconds)
        assertEquals(0.6f, shipped.choiceHoldSeconds)
        assertEquals(1f, shipped.layerSwitchHoldSeconds)
        assertFalse(CoreStasisTuning.DISABLED.enabled)
        assertEquals(shipped.copy(enabled = false), CoreStasisTuning.DISABLED)
        assertTrue(CoreStasisTuning.MEASURE_ONLY.enabled)
        assertFalse(CoreStasisTuning.MEASURE_ONLY.applyEffects)
        assertEquals(shipped.copy(applyEffects = false), CoreStasisTuning.MEASURE_ONLY)
        assertEquals(listOf("CORE FROZEN"), CoreStasisNotices.all)
    }

    @Test
    fun coreFreezeTuningAcceptsItsBoundsAndRejectsTheNextValue() {
        // field to (accepted bounds, rejected next values)
        val cases: List<Triple<String, List<Float>, (Float) -> CoreStasisTuning>> = listOf(
            Triple("freezeSpeed", listOf(20f, 1_000f, 19.9f, 1_000.1f)) { CoreStasisTuning(freezeSpeed = it, thawSpeed = 1_000f) },
            Triple("thawSpeed", listOf(100f, 1_500f, 99.9f, 1_500.1f)) { CoreStasisTuning(thawSpeed = it) },
            Triple("speedFilterSeconds", listOf(0.02f, 5f, 0.019f, 5.01f)) { CoreStasisTuning(speedFilterSeconds = it) },
            Triple("freezeSeconds", listOf(0.5f, 60f, 0.49f, 60.01f)) { CoreStasisTuning(freezeSeconds = it) },
            Triple("thawSeconds", listOf(0.5f, 60f, 0.49f, 60.01f)) { CoreStasisTuning(thawSeconds = it) },
            Triple("runStartHoldSeconds", listOf(0f, 10f, -0.01f, 10.01f)) { CoreStasisTuning(runStartHoldSeconds = it) },
            Triple("choiceHoldSeconds", listOf(0f, 10f, -0.01f, 10.01f)) { CoreStasisTuning(choiceHoldSeconds = it) },
            Triple("layerSwitchHoldSeconds", listOf(0f, 10f, -0.01f, 10.01f)) { CoreStasisTuning(layerSwitchHoldSeconds = it) },
        )
        cases.forEach { (name, values, build) ->
            build(values[0])
            build(values[1])
            assertFailsWith<IllegalArgumentException>("$name ${values[2]}") { build(values[2]) }
            assertFailsWith<IllegalArgumentException>("$name ${values[3]}") { build(values[3]) }
            assertFailsWith<IllegalArgumentException>("$name NaN") { build(Float.NaN) }
            assertFailsWith<IllegalArgumentException>("$name infinity") { build(Float.POSITIVE_INFINITY) }
        }
        // The thaw speed is the top of the hold band, never below the freeze speed it releases.
        assertFailsWith<IllegalArgumentException> { CoreStasisTuning(freezeSpeed = 130f, thawSpeed = 125f) }
        CoreStasisTuning(freezeSpeed = 125f, thawSpeed = 125f)
    }

    /** The collapsing orbit never asks for a pace at which the Core cannot thaw (the snapshot refuses it). */
    @Test
    fun theCollapsingOrbitPaceStaysAboveTheThawSpeed() {
        val windows = SiteWindowPolicy()
        assertEquals(140f, windows.orbitMinimumSpeed)
        assertTrue(windows.orbitMinimumSpeed > CoreStasisTuning().thawSpeed)
        assertFailsWith<IllegalArgumentException> { SiteWindowPolicy(orbitMinimumSpeed = -1f) }
        assertFailsWith<IllegalArgumentException> { SiteWindowPolicy(orbitMinimumSpeed = Float.NaN) }
    }
}
