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
    fun coreStasisTuningShipsTheDecidedValues() {
        val shipped = CoreStasisTuning()
        assertTrue(shipped.enabled)
        assertTrue(shipped.applyEffects)
        assertEquals(240f, shipped.anchorRadius)
        assertEquals(120f, shipped.stillSpeed)
        assertEquals(30f, shipped.travelSpeed)
        assertEquals(1.5f, shipped.speedEmaSeconds)
        assertEquals(4f, shipped.decayRate)
        assertEquals(10f, shipped.graceSeconds)
        assertEquals(12f, shipped.rampSeconds)
        assertEquals(22f, shipped.fullStasisSeconds)
        assertEquals(0.65f, shipped.maxWeaponSlow)
        assertEquals(6f, shipped.fractureDelaySeconds)
        assertEquals(0.05f, shipped.fractureShare)
        assertEquals(1.5f, shipped.fractureTickSeconds)
        assertFalse(CoreStasisTuning.DISABLED.enabled)
        assertEquals(shipped.copy(enabled = false), CoreStasisTuning.DISABLED)
        assertTrue(CoreStasisTuning.MEASURE_ONLY.enabled)
        assertFalse(CoreStasisTuning.MEASURE_ONLY.applyEffects)
        assertEquals(shipped.copy(applyEffects = false), CoreStasisTuning.MEASURE_ONLY)
        assertEquals(listOf("CORE FROZEN"), CoreStasisNotices.all)
    }

    @Test
    fun coreStasisTuningAcceptsItsBoundsAndRejectsTheNextValue() {
        // field to (accepted bounds, rejected next values)
        val cases: List<Triple<String, List<Float>, (Float) -> CoreStasisTuning>> = listOf(
            Triple("anchorRadius", listOf(60f, 1_200f, 59.9f, 1_200.1f)) { CoreStasisTuning(anchorRadius = it) },
            Triple("stillSpeed", listOf(20f, 1_000f, 19.9f, 1_000.1f)) { CoreStasisTuning(stillSpeed = it, travelSpeed = 0f) },
            Triple("travelSpeed", listOf(0f, 119.9f, -0.01f, 120f)) { CoreStasisTuning(travelSpeed = it) },
            Triple("speedEmaSeconds", listOf(0.1f, 10f, 0.099f, 10.01f)) { CoreStasisTuning(speedEmaSeconds = it) },
            Triple("decayRate", listOf(1f, 20f, 0.99f, 20.01f)) { CoreStasisTuning(decayRate = it) },
            Triple("graceSeconds", listOf(1f, 120f, 0.99f, 120.01f)) { CoreStasisTuning(graceSeconds = it) },
            Triple("rampSeconds", listOf(0.5f, 120f, 0.49f, 120.01f)) { CoreStasisTuning(rampSeconds = it) },
            Triple("maxWeaponSlow", listOf(0f, 0.9f, -0.01f, 0.91f)) { CoreStasisTuning(maxWeaponSlow = it) },
            Triple("fractureDelaySeconds", listOf(0f, 60f, -0.01f, 60.01f)) { CoreStasisTuning(fractureDelaySeconds = it) },
            Triple("fractureShare", listOf(0f, 0.5f, -0.01f, 0.51f)) { CoreStasisTuning(fractureShare = it) },
            Triple("fractureTickSeconds", listOf(0.1f, 10f, 0.099f, 10.01f)) { CoreStasisTuning(fractureTickSeconds = it) },
        )
        cases.forEach { (name, values, build) ->
            build(values[0])
            build(values[1])
            assertFailsWith<IllegalArgumentException>("$name ${values[2]}") { build(values[2]) }
            assertFailsWith<IllegalArgumentException>("$name ${values[3]}") { build(values[3]) }
            assertFailsWith<IllegalArgumentException>("$name NaN") { build(Float.NaN) }
            assertFailsWith<IllegalArgumentException>("$name infinity") { build(Float.POSITIVE_INFINITY) }
        }
        // The travel floor must stay below the still speed it qualifies.
        assertFailsWith<IllegalArgumentException> { CoreStasisTuning(stillSpeed = 40f, travelSpeed = 40f) }
        CoreStasisTuning(stillSpeed = 20f, travelSpeed = 19.9f)
    }
}
