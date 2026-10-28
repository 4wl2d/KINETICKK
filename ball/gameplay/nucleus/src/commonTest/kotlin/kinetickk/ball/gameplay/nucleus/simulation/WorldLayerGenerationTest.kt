// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldLayerGenerationTest {
    @Test
    fun realspaceSeedsAndIdsAreUnchanged() {
        val seeds = intArrayOf(0, 1, 731_991, -42, Int.MAX_VALUE, Int.MIN_VALUE)
        val chunks = intArrayOf(0, 1, -1, 7, -13, 123_456, -123_456)
        for (seed in seeds) for (x in chunks) for (y in chunks) {
            assertEquals(releasedChunkSeed(seed, x, y), chunkSeed(seed, x, y), "seed $seed chunk $x,$y")
            assertEquals(chunkSeed(seed, x, y), chunkSeed(seed, x, y, WorldLayer.BASE))
            assertEquals(chunkSeed(seed, x, y), worldHash(seed, x, y, RELEASED_OBSTACLE_SALT))
            for (slot in 0..15) assertEquals(releasedObstacleId(x, y, slot), obstacleId(x, y, slot))
        }
        assertEquals(0L, layerSalt(WorldLayer.BASE))

        // A whole realspace window: the generated field is the one the released hash places.
        val state = MutableGameState(canonicalGameplayContent, seed = 4_242).apply { startRun() }
        val field = state.obstacleField
        assertTrue(field.size > 0)
        for (obstacle in field.obstacles) assertEquals(0L, obstacle.id ushr 60, "realspace ids keep bits 60-63 clear")
    }

    @Test
    fun otherLayersSaltTheirSeedsAndTagTheirIds() {
        val layers = WorldLayer.entries
        for (layer in layers) {
            val id = obstacleId(-3, 5, 9, layer)
            assertEquals(layer.ordinal.toLong(), id ushr 60, "$layer tag")
            assertEquals(obstacleId(-3, 5, 9), id and ((1L shl 60) - 1), "$layer keeps the chunk and slot bits")
        }
        val seeds = layers.map { chunkSeed(731_991, 4, -2, it) }
        assertEquals(layers.size, seeds.toSet().size, "every layer draws its own chunk stream")
        assertEquals(layers.size, layers.map(::layerSalt).toSet().size)
    }

    @Test
    fun theCurrentMixFollowsTheWorldLayer() {
        val state = MutableGameState(canonicalGameplayContent).apply { startRun() }
        val kinds = state.content.obstacles.kinds
        assertSame(kinds.space, state.currentObstacleMix())
        state.worldLayer = WorldLayer.ZENITH
        assertSame(kinds.zenith, state.currentObstacleMix())
        state.worldLayer = WorldLayer.NADIR
        assertSame(kinds.nadir, state.currentObstacleMix())
        state.worldLayer = WorldLayer.ARCHIVE
        assertSame(kinds.archive, state.currentObstacleMix())
        // A new run starts in realspace again without a keep-out.
        state.worldKeepOut = WorldKeepOut(WorldLayer.ARCHIVE, 0.0, 0.0, 900f)
        state.startRun()
        assertEquals(WorldLayer.BASE, state.worldLayer)
        assertEquals(null, state.worldKeepOut)
    }

    @Test
    fun aKeepOutCoversItsRadiusPlusReachOnlyInItsOwnLayer() {
        val state = MutableGameState(canonicalGameplayContent).apply { startRun() }
        assertFalse(state.keepOutSuppresses(0.0, 0.0, 10f), "no keep-out, nothing suppressed")
        state.worldKeepOut = WorldKeepOut(WorldLayer.BASE, 1_000.0, -500.0, 900f)
        assertTrue(state.keepOutSuppresses(1_000.0 + 909.0, -500.0, 10f), "inside radius + reach")
        assertFalse(state.keepOutSuppresses(1_000.0 + 910.0, -500.0, 10f), "the edge itself is clear")
        state.worldKeepOut = WorldKeepOut(WorldLayer.NADIR, 1_000.0, -500.0, 900f)
        assertFalse(state.keepOutSuppresses(1_000.0, -500.0, 10f), "another layer's keep-out")
        state.worldLayer = WorldLayer.NADIR
        assertTrue(state.keepOutSuppresses(1_000.0, -500.0, 10f))
    }

    @Test
    fun hashHelpersAreStatelessAndUniform() {
        assertEquals(worldHash(9, 3, 4, 77L), worldHash(9, 3, 4, 77L))
        assertNotEquals(worldHash(9, 3, 4, 77L), worldHash(9, 4, 3, 77L))
        assertEquals(runHash(9, 77L, 2L), runHash(9, 77L, 2L))
        assertNotEquals(runHash(9, 77L, 2L), runHash(9, 77L, 3L))
        assertEquals(0f, hashUnit(0L))
        assertTrue(hashUnit(-1L) < 1f)
        val random = HashRandom(chunkSeed(1, 2, 3))
        repeat(200) {
            val unit = random.nextFloat()
            assertTrue(unit >= 0f && unit < 1f)
            assertTrue(random.nextIndex(7) in 0 until 7)
        }
    }

    private fun releasedChunkSeed(runSeed: Int, chunkX: Int, chunkY: Int): Long {
        var mixed = releasedSplitMix(runSeed.toLong() xor RELEASED_OBSTACLE_SALT)
        mixed = releasedSplitMix(mixed xor chunkX.toLong())
        return releasedSplitMix(mixed xor (chunkY.toLong() shl 1))
    }

    private fun releasedObstacleId(chunkX: Int, chunkY: Int, slot: Int): Long =
        ((chunkX.toLong() and 0x0FFF_FFFF) shl 32) or ((chunkY.toLong() and 0x0FFF_FFFF) shl 4) or (slot.toLong() and 0xF)

    private fun releasedSplitMix(value: Long): Long {
        var z = value + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private companion object {
        const val RELEASED_OBSTACLE_SALT: Long = 0x0B57_AC1E_5EED_0001L
    }
}
