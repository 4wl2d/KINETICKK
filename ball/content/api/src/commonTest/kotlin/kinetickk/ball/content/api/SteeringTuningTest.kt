// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SteeringTuningTest {
    @Test
    fun theTempoShipsThePilotingLawAndLegacyFliesTheReleasedOne() {
        val tuning = RunTempoProfile().steering
        assertEquals(SteeringTuning(), tuning)
        assertEquals(130f, tuning.basePull)
        assertEquals(0.36f, tuning.forwardThrustScale)
        assertEquals(1f, tuning.counterThrustGate)
        assertEquals(0.15f, tuning.counterThrustDeadzoneReach)
        assertEquals(0.7f, tuning.gripGain)
        assertEquals(0.5f, tuning.gripFullReach)
        assertEquals(0.1f, tuning.slingShare)
        assertEquals(0.15f, tuning.slingHeadroom)
        assertEquals(0.7f, tuning.slingKneeShare)
        assertEquals(0.15f, tuning.spoolGain)
        assertEquals(0f, tuning.straightDrag)
        assertEquals(0.6f, tuning.straightCurvatureRate)
        assertEquals(1.2f, tuning.straightRiseSeconds)
        assertEquals(0.2f, tuning.straightFallSeconds)
        assertEquals(3f, tuning.maximumStatMultiplier)

        val legacy = SteeringTuning.LEGACY
        assertEquals(92f, legacy.basePull)
        assertEquals(1f, legacy.forwardThrustScale)
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), listOf(legacy.counterThrustGate, legacy.gripGain, legacy.slingShare, legacy.spoolGain, legacy.straightDrag))
    }

    @Test
    fun everyKnobAcceptsItsBoundsAndRejectsTheNextValue() {
        // knob to (accepted values, rejected values)
        val cases: List<Triple<String, List<Float>, (Float) -> SteeringTuning>> = listOf(
            Triple("basePull", listOf(0f, 1_000f, -0.01f, 1_000.01f)) { SteeringTuning(basePull = it) },
            Triple("forwardThrustScale", listOf(0.001f, 1f, 0f, 1.01f)) { SteeringTuning(forwardThrustScale = it) },
            Triple("counterThrustGate", listOf(0f, 1f, -0.01f, 1.01f)) { SteeringTuning(counterThrustGate = it) },
            Triple("counterThrustDeadzoneReach", listOf(0.001f, 0.999f, 0f, 1f)) { SteeringTuning(counterThrustDeadzoneReach = it) },
            Triple("gripGain", listOf(0f, 2f, -0.01f, 2.01f)) { SteeringTuning(gripGain = it) },
            Triple("gripFullReach", listOf(0.001f, 1f, 0f, 1.01f)) { SteeringTuning(gripFullReach = it) },
            Triple("slingShare", listOf(0f, 0.6f, -0.01f, 0.61f)) { SteeringTuning(slingShare = it) },
            Triple("slingHeadroom", listOf(0f, 0.5f, -0.01f, 0.51f)) { SteeringTuning(slingHeadroom = it) },
            Triple("slingKneeShare", listOf(0f, 0.999f, -0.01f, 1f)) { SteeringTuning(slingKneeShare = it) },
            Triple("spoolGain", listOf(0f, 2f, -0.01f, 2.01f)) { SteeringTuning(spoolGain = it) },
            Triple("straightDrag", listOf(0f, 1.5f, -0.01f, 1.51f)) { SteeringTuning(straightDrag = it) },
            Triple("straightCurvatureRate", listOf(0.001f, 10f, 0f, 10.01f)) { SteeringTuning(straightCurvatureRate = it) },
            Triple("straightRiseSeconds", listOf(0.001f, 60f, 0f, Float.POSITIVE_INFINITY)) { SteeringTuning(straightRiseSeconds = it) },
            Triple("straightFallSeconds", listOf(0.001f, 60f, 0f, Float.NaN)) { SteeringTuning(straightFallSeconds = it) },
            Triple("maximumStatMultiplier", listOf(1f, 5f, 0.99f, 5.01f)) { SteeringTuning(maximumStatMultiplier = it) },
        )
        cases.forEach { (name, values, build) ->
            build(values[0])
            build(values[1])
            assertFailsWith<IllegalArgumentException>("$name ${values[2]}") { build(values[2]) }
            assertFailsWith<IllegalArgumentException>("$name ${values[3]}") { build(values[3]) }
        }
        assertFailsWith<IllegalArgumentException> { SteeringTuning(basePull = Float.NaN) }
    }
}
