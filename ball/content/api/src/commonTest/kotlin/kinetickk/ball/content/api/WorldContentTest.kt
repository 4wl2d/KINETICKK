// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldContentTest {
    @Test
    fun shippedObstacleTuningMatchesTheSpecifiedField() {
        val tuning = ObstacleTuning()
        assertEquals(1_200f, tuning.chunkSize)
        assertEquals(5, tuning.windowChunks)
        assertEquals(2.2f, tuning.meanPerChunk)
        assertEquals(5, tuning.maximumPerChunk)
        assertTrue(tuning.windowChunks * tuning.windowChunks * tuning.maximumPerChunk <= ContentBounds.MAX_OBSTACLES)
        assertEquals(128, ContentBounds.MAX_OBSTACLES)
        assertEquals(256, ContentBounds.MAX_DESTROYED_OBSTACLES)
        assertEquals(46f, tuning.asteroidMinimumRadius)
        assertEquals(150f, tuning.asteroidMaximumRadius)
        assertEquals(34f, tuning.bumperRadius)
        assertEquals(900f, tuning.safeRadius)
        assertEquals(90f, tuning.minimumGap)
        assertEquals(0.6f, tuning.restitution)
        assertEquals(2_200f, tuning.minimapRange)
        assertEquals(1.4f, tuning.minimapViewportFactor)
        assertEquals(0.5f, tuning.blackoutMinimapScale)
    }

    @Test
    fun obstacleTuningRejectsAWindowPastTheBoundAndInvertedReaches() {
        // 5 × 5 × 5 = 125 fits; a sixth slot per chunk (150) does not.
        assertEquals(5, ObstacleTuning(maximumPerChunk = 5).maximumPerChunk)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(maximumPerChunk = 6) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(windowChunks = 4) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(damageStartReach = 0.8f, lethalReach = 0.7f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(restitution = 1.01f) }
        assertEquals(1f, ObstacleTuning(restitution = 1f).restitution)
    }

    @Test
    fun pickupTuningShipsTheWiderMagnetAndRejectsAVacuumSlowerThanTheCore() {
        val tuning = PickupTuning()
        assertEquals(320f, tuning.magnetRadius)
        assertEquals(56f, tuning.collectionRadius)
        assertEquals(950f, tuning.vacuumSpeed)
        assertEquals(1.3f, tuning.vacuumCoreSpeedFactor)
        assertEquals(120f, tuning.minimumMagnetRadius)
        assertFailsWith<IllegalArgumentException> { PickupTuning(vacuumCoreSpeedFactor = 0.99f) }
        assertFailsWith<IllegalArgumentException> { PickupTuning(collectionRadius = 320f) }
        assertFailsWith<IllegalArgumentException> { PickupTuning(minimumMagnetRadius = 330f) }
    }
}
