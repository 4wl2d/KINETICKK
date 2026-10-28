// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreRibbonProjectionTest {
    @Test
    fun aRibbonProjectionHoldsSixtyFourSamplesAndRejectsTheSixtyFifth() {
        val limit = InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES
        assertEquals(64, limit)
        val full = ribbon(limit)
        assertEquals(limit, full.size)
        assertEquals(0f, full.x(0))
        assertEquals(5f * (limit - 1), full.x(limit - 1))
        assertFailsWith<IllegalArgumentException> { ribbon(limit + 1) }
        assertFailsWith<IllegalArgumentException> { CoreRibbonProjection.of(FloatArray(3), FloatArray(2), IntArray(2)) }
        assertFailsWith<IllegalArgumentException> { CoreRibbonProjection.of(FloatArray(4), FloatArray(2), IntArray(1)) }
        assertSame(CoreRibbonProjection.EMPTY, CoreRibbonProjection.of(FloatArray(0), FloatArray(0), IntArray(0)))
        assertEquals(0, CoreRibbonProjection.EMPTY.size)
    }

    @Test
    fun aPublishedRibbonOwnsItsSamples() {
        val positions = floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f)
        val ages = floatArrayOf(0f, 0.1f, 0.2f)
        val flags = intArrayOf(CoreRibbonProjection.DASH, 0, CoreRibbonProjection.START or CoreRibbonProjection.DASH)
        val published = CoreRibbonProjection.of(positions, ages, flags)
        val twin = CoreRibbonProjection.of(positions, ages, flags)
        positions.fill(-1f)
        ages.fill(9f)
        flags.fill(0)
        assertEquals(listOf(10f, 30f, 50f), (0 until published.size).map(published::x))
        assertEquals(listOf(20f, 40f, 60f), (0 until published.size).map(published::y))
        assertEquals(listOf(0f, 0.1f, 0.2f), (0 until published.size).map(published::age))
        assertEquals(listOf(false, false, true), (0 until published.size).map(published::startsChain))
        assertEquals(listOf(true, false, true), (0 until published.size).map(published::dash))
        assertEquals(twin, published)
        assertEquals(twin.hashCode(), published.hashCode())
        assertNotEquals(CoreRibbonProjection.of(positions, ages, flags), published)
        assertFalse(published == CoreRibbonProjection.EMPTY)
        assertTrue(VisualFxProjection.EMPTY.coreRibbon.size == 0, "a fresh visual snapshot has no ribbon")
    }

    @Test
    fun theRibbonKeepsTheWedgeLengthUpToItsSpeedAndCapsBeyondIt() {
        assertEquals(80f, ribbonLength(0f))
        assertEquals(340f, ribbonLength(920f), 0.001f)
        assertEquals(RIBBON_MAX_LENGTH, ribbonLength(2_760f), 0.5f)
        assertEquals(RIBBON_MAX_LENGTH, ribbonLength(6_000f))
        // Spacing is the drawn length in 48 parts, never closer than 4 units.
        assertEquals(RIBBON_MIN_SPACING, ribbonSpacing(0f))
        assertEquals(RIBBON_MIN_SPACING, ribbonSpacing(300f))
        assertEquals(13.44f, ribbonSpacing(2_000f), 0.01f)
        assertEquals(RIBBON_MAX_LENGTH / 48f, ribbonSpacing(6_000f))
    }

    private fun ribbon(size: Int): CoreRibbonProjection =
        CoreRibbonProjection.of(FloatArray(size * 2) { if (it % 2 == 0) 5f * (it / 2) else 0f }, FloatArray(size), IntArray(size))
}
