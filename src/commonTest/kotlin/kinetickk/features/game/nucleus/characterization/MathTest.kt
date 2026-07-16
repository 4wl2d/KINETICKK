// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathTest {
    @Test
    fun pointToSegmentDistanceUsesClosestPointOnSegment() {
        assertEquals(9f, pointToSegmentDistanceSquared(5f, 3f, 0f, 0f, 10f, 0f), 0.0001f)
        assertEquals(20f, pointToSegmentDistanceSquared(12f, 4f, 0f, 0f, 10f, 0f), 0.0001f)
    }

    @Test
    fun pointToSegmentDistanceHandlesZeroLengthSegment() {
        assertEquals(25f, pointToSegmentDistanceSquared(3f, 4f, 0f, 0f, 0f, 0f), 0.0001f)
    }

    @Test
    fun segmentCircleIncludesCrossingsTangentsAndEndpoints() {
        assertTrue(segmentCircleIntersects(0f, 0f, 10f, 0f, 5f, 0f, 0f))
        assertTrue(segmentCircleIntersects(0f, 0f, 10f, 0f, 5f, 1f, 1f))
        assertTrue(segmentCircleIntersects(0f, 0f, 10f, 0f, 11f, 0f, 1f))
    }

    @Test
    fun segmentCircleRejectsMissesAndNegativeRadii() {
        assertFalse(segmentCircleIntersects(0f, 0f, 10f, 0f, 5f, 1.01f, 1f))
        assertFalse(segmentCircleIntersects(0f, 0f, 10f, 0f, 5f, 0f, -1f))
    }

    @Test
    fun segmentCircleHandlesZeroLengthSegment() {
        assertTrue(segmentCircleIntersects(2f, 2f, 2f, 2f, 3f, 2f, 1f))
        assertFalse(segmentCircleIntersects(2f, 2f, 2f, 2f, 3.01f, 2f, 1f))
    }

    @Test
    fun softVelocityIsContinuousAtKnee() {
        val epsilon = 0.001f
        assertEquals(720f - epsilon, softVelocity(720f - epsilon), 0.0001f)
        assertEquals(720f, softVelocity(720f), 0.0001f)
        assertEquals(720f + epsilon, softVelocity(720f + epsilon), 0.0002f)
    }

    @Test
    fun softVelocityIsMonotonicAndCompressesValuesAboveKnee() {
        val inputs = listOf(0f, 100f, 719f, 720f, 721f, 1_000f, 2_000f, 10_000f)
        val outputs = inputs.map(::softVelocity)

        outputs.zipWithNext().forEach { (left, right) -> assertTrue(right > left) }
        assertEquals(719f, softVelocity(719f), 0f)
        assertTrue(softVelocity(2_000f) in 720f..2_000f)
        assertTrue(softVelocity(10_000f) < 10_000f)
    }

    @Test
    fun abbreviatesIncrementalHudValues() {
        assertEquals("999", abbreviateNumber(999L))
        assertEquals("1K", abbreviateNumber(1_000L))
        assertEquals("1.2K", abbreviateNumber(1_240L))
        assertEquals("-1.2K", abbreviateNumber(-1_240L))
        assertEquals("1M", abbreviateNumber(999_999L))
        assertEquals("9.2Qi", abbreviateNumber(Long.MAX_VALUE))
    }

    @Test
    fun damageNumbersSupportCompactAndFullFormats() {
        assertEquals("999", formatDamageNumber(999L, DamageNumberFormat.COMPACT))
        assertEquals("14K", formatDamageNumber(14_000L, DamageNumberFormat.COMPACT))
        assertEquals("14000", formatDamageNumber(14_000L, DamageNumberFormat.FULL))
        assertEquals(Long.MAX_VALUE.toString(), formatDamageNumber(Long.MAX_VALUE, DamageNumberFormat.FULL))
    }

    @Test
    fun damageNumberHeatTiersChangeAtConfiguredBoundaries() {
        assertEquals(DamageNumberTier.STANDARD, damageNumberTier(49L, firstThreshold = 50))
        assertEquals(DamageNumberTier.STRONG, damageNumberTier(50L, firstThreshold = 50))
        assertEquals(DamageNumberTier.STRONG, damageNumberTier(199L, firstThreshold = 50))
        assertEquals(DamageNumberTier.POWERFUL, damageNumberTier(200L, firstThreshold = 50))
        assertEquals(DamageNumberTier.POWERFUL, damageNumberTier(999L, firstThreshold = 50))
        assertEquals(DamageNumberTier.DEVASTATING, damageNumberTier(1_000L, firstThreshold = 50))

        assertEquals(DamageNumberTier.STRONG, damageNumberTier(399L, firstThreshold = 100))
        assertEquals(DamageNumberTier.POWERFUL, damageNumberTier(400L, firstThreshold = 100))
        assertEquals(DamageNumberTier.DEVASTATING, damageNumberTier(2_000L, firstThreshold = 100))
    }

    @Test
    fun criticalDamageIsAtLeastPowerfulButStillEarnsTheRedTierByMagnitude() {
        assertEquals(DamageNumberTier.POWERFUL, damageNumberTier(10L, firstThreshold = 50, critical = true))
        assertEquals(DamageNumberTier.DEVASTATING, damageNumberTier(1_000L, firstThreshold = 50, critical = true))
    }
}
