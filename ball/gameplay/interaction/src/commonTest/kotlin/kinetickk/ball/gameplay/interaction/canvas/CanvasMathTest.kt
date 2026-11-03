// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The renderers' private angle constants became [CanvasMath]; every spelling a renderer used is
 * bit-identical to the constant that replaced it, so no drawn coordinate moves.
 */
class CanvasMathTest {
    @Test
    fun everyReplacedFullTurnSpellingIsBitIdenticalToTau() {
        val spellings = listOf(
            (2.0 * PI).toFloat(),
            (PI * 2.0).toFloat(),
            (PI * 2).toFloat(),
            6.2831855f,
        )
        spellings.forEach { assertEquals(CanvasMath.TAU.toRawBits(), it.toRawBits(), "$it") }
    }

    @Test
    fun everyReplacedRadiansToDegreesSpellingIsBitIdentical() {
        assertEquals(CanvasMath.RAD_TO_DEG.toRawBits(), 57.29578f.toRawBits())
        assertEquals(CanvasMath.RAD_TO_DEG.toRawBits(), (180.0 / PI).toFloat().toRawBits())
    }

    @Test
    fun everyReplacedDegreesToRadiansSpellingIsBitIdentical() {
        listOf((PI / 180.0).toFloat(), 0.017453292f).forEach {
            assertEquals(CanvasMath.DEG_TO_RAD.toRawBits(), it.toRawBits(), "$it")
        }
    }
}
