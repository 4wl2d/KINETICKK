// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.sqrt
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

    @Test
    fun everyLayerTagsItsIdsAndDrawsItsOwnField() {
        val state = farState(seed = 731)
        val fields = WorldLayer.entries.associateWith { layer ->
            state.switchLayer(layer)
            state.obstacleField.obstacles
        }
        for ((layer, obstacles) in fields) {
            assertTrue(obstacles.isNotEmpty(), "$layer has a field")
            for (obstacle in obstacles) {
                assertEquals(layer.ordinal.toLong(), (obstacle.id ushr 60) and 7, "$layer tags its ids")
                // The low bits are the obstacle's own absolute chunk and slot, as in realspace.
                val chunkX = state.absoluteChunk(obstacle.x + state.worldOriginX)
                val chunkY = state.absoluteChunk(obstacle.y + state.worldOriginY)
                assertEquals(obstacleId(chunkX, chunkY, (obstacle.id and 0xF).toInt(), layer), obstacle.id)
            }
        }
        val ids = fields.values.map { obstacles -> obstacles.map { it.id }.toSet() }
        for (first in ids.indices) for (second in first + 1 until ids.size) {
            assertTrue(ids[first].intersect(ids[second]).isEmpty(), "layers never share an id")
        }
        val placements = fields.values.map { obstacles -> obstacles.map { it.x to it.y }.toSet() }
        assertEquals(placements.size, placements.toSet().size, "every layer draws its own placements")
        // Back in realspace the window is the run's original one.
        state.switchLayer(WorldLayer.BASE)
        assertEquals(farState(seed = 731).obstacleField.obstacles, state.obstacleField.obstacles)
    }

    @Test
    fun layerFieldsAreDeterministicAndOrderIndependent() {
        for (layer in WorldLayer.entries) {
            val direct = farState(seed = 58).apply { switchLayer(layer) }.obstacleField.obstacles
            val again = farState(seed = 58).apply { switchLayer(layer) }.obstacleField.obstacles
            assertEquals(direct, again, "$layer draws the same window twice")
            // Visit another region of the layer first, then come back.
            val detour = farState(seed = 58).apply {
                coreX = FAR_X + 41_000f
                coreY = FAR_Y - 23_000f
                switchLayer(layer)
                coreX = FAR_X
                coreY = FAR_Y
                refreshObstacleWindow()
            }.obstacleField.obstacles
            assertEquals(direct.sortedBy { it.id }, detour.sortedBy { it.id }, "$layer does not depend on the visit order")
            // Chunks drawn in reverse order are the same chunks.
            val state = farState(seed = 58).apply { worldLayer = layer }
            val chunks = (-3..3).flatMap { x -> (-3..3).map { y -> x + 6 to y - 3 } }
            val forward = chunks.associateWith { (x, y) -> state.generateChunk(x, y) }
            val backward = chunks.reversed().associateWith { (x, y) -> state.generateChunk(x, y) }
            assertEquals(forward, backward)
        }
    }

    /**
     * The layout arm (every layer's kind mix empty), where each body reaches its own circle; how a mix widens the clearing
     * is [aKeepOutClearsTheWidestFieldReachWhileTheMixMayPlaceFieldKinds]'s.
     */
    @Test
    fun keepOutSuppressesOnlyInsideItsRadiusAndMovesNothingElse() {
        for (layer in WorldLayer.entries) {
            val state = farState(seed = 4_242, content = withoutObstacleVariants())
            state.switchLayer(layer)
            val open = state.obstacleField.obstacles
            state.switchLayer(layer, keepOutRadius = ARRIVAL_CLEAR)
            val cleared = state.obstacleField.obstacles
            val centreX = state.worldOriginX + FAR_X
            val centreY = state.worldOriginY + FAR_Y
            val inside = open.filter { state.distanceFrom(it, centreX, centreY) < ARRIVAL_CLEAR + it.radius }
            assertTrue(inside.isNotEmpty(), "$layer: the keep-out covers part of the window")
            assertEquals(open - inside.toSet(), cleared, "$layer: everything else keeps its id, place and size")
            // Another layer's keep-out changes nothing here.
            val other = WorldLayer.entries[(layer.ordinal + 1) % WorldLayer.entries.size]
            state.worldKeepOut = WorldKeepOut(other, centreX, centreY, ARRIVAL_CLEAR)
            state.switchWorldWindow()
            assertEquals(open, state.obstacleField.obstacles, "$layer ignores a keep-out of $other")
        }
    }

    @Test
    fun aKeepOutClearsTheWidestFieldReachWhileTheMixMayPlaceFieldKinds() {
        val kinds = ObstacleKindTuning(
            zenith = ObstacleMix(gravityWell = 0.3f, crystal = 0.2f),
            nadir = ObstacleMix(crystal = 0.5f),
        )
        val content = canonicalGameplayContent.copy(obstacles = canonicalGameplayContent.obstacles.copy(kinds = kinds))
        val influence = kinds.maximumInfluence
        /** The obstacles the keep-out removed and how many its circle alone would have removed. */
        fun clearedBy(layer: WorldLayer): Pair<Int, Int> {
            val state = farState(seed = 4_242, content = content)
            state.switchLayer(layer)
            val open = state.obstacleField.obstacles
            state.switchLayer(layer, keepOutRadius = ARRIVAL_CLEAR)
            val removed = open - state.obstacleField.obstacles.toSet()
            assertEquals(open.size - removed.size, state.obstacleField.size, "$layer only removes")
            // The keep-out reads each layout candidate, before the mix rolls its kind and body: the same window without
            // the mix holds every candidate under the same id.
            val candidates = farState(seed = 4_242, content = content.withoutObstacleVariants()).apply { switchLayer(layer) }
                .obstacleField.obstacles.associateBy { it.id }
            val candidate = { obstacle: Obstacle -> candidates.getValue(obstacle.id) }
            val reach = { obstacle: Obstacle ->
                val laid = candidate(obstacle)
                if (layer == WorldLayer.ZENITH && laid.kind == ObstacleKind.ASTEROID) influence else laid.radius
            }
            val expected = open.filter {
                state.distanceFrom(it, state.worldOriginX + FAR_X, state.worldOriginY + FAR_Y) < ARRIVAL_CLEAR + reach(it)
            }
            assertEquals(expected, removed, "$layer clears each candidate by its reach")
            val centreX = state.worldOriginX + FAR_X
            val centreY = state.worldOriginY + FAR_Y
            return removed.size to open.count { state.distanceFrom(it, centreX, centreY) < ARRIVAL_CLEAR + candidate(it).radius }
        }
        // Zenith's mix turns asteroids into gravity knots, so asteroids keep their widest pull outside the keep-out;
        // bumpers never convert and keep their own circle.
        val (zenithRemoved, zenithNarrow) = clearedBy(WorldLayer.ZENITH)
        assertTrue(zenithRemoved > zenithNarrow, "the field reach widens the clearing: $zenithRemoved vs $zenithNarrow")
        // Nadir's mix only places crystals (no field), so its asteroids are cleared by their circle alone.
        val (nadirRemoved, nadirNarrow) = clearedBy(WorldLayer.NADIR)
        assertEquals(nadirNarrow, nadirRemoved)
    }

    @Test
    fun everyDimensionWindowStaysWithinTheObstacleBound() {
        val dense = ObstacleMix(densityScale = 2f)
        val kinds = ObstacleKindTuning(space = dense, zenith = dense, nadir = dense, archive = dense)
        val content = canonicalGameplayContent.copy(obstacles = canonicalGameplayContent.obstacles.copy(kinds = kinds))
        val tuning = content.obstacles
        val state = buildTestState(seed = 99, rebirth = 10, content = content).apply {
            runMutatorMask = runMutatorMask or (1 shl RebirthMutator.FRACTURED_SPACE.ordinal)
        }
        assertTrue(tuning.windowChunks * tuning.windowChunks * tuning.maximumPerChunk <= ContentBounds.MAX_OBSTACLES)
        var busiest = 0
        for (layer in WorldLayer.entries) for (step in 0 until 4) {
            state.coreX = FAR_X + step * 9_700f
            state.coreY = FAR_Y - step * 6_100f
            state.switchLayer(layer)
            val field = state.obstacleField
            assertTrue(field.size <= ContentBounds.MAX_OBSTACLES, "$layer window ${field.size}")
            val perChunk = field.obstacles.groupBy { it.id ushr 4 }.values.maxOf { it.size }
            assertTrue(perChunk <= tuning.maximumPerChunk, "$layer chunk $perChunk")
            busiest = maxOf(busiest, perChunk)
        }
        assertEquals(tuning.maximumPerChunk, busiest, "the densest layers fill a chunk's every slot")
    }

    /** The layout arm, as [keepOutSuppressesOnlyInsideItsRadiusAndMovesNothingElse]. */
    @Test
    fun returningToRealspaceRegeneratesTheSameFieldExceptTheKeepOut() {
        val state = farState(seed = 2_026, content = withoutObstacleVariants())
        val realspace = state.obstacleField.obstacles
        state.switchLayer(WorldLayer.NADIR, keepOutRadius = ARRIVAL_CLEAR)
        assertTrue(state.obstacleField.obstacles.all { (it.id ushr 60) == WorldLayer.NADIR.ordinal.toLong() })
        state.coreX += 300f
        state.switchLayer(WorldLayer.BASE, keepOutRadius = ARRIVAL_CLEAR)
        val centreX = state.worldOriginX + state.coreX
        val centreY = state.worldOriginY + state.coreY
        val back = state.obstacleField.obstacles
        val expected = realspace.filter { state.distanceFrom(it, centreX, centreY) >= ARRIVAL_CLEAR + it.radius }
        assertTrue(expected.size < realspace.size, "the exit point is cleared")
        assertEquals(expected, back)
        // Leaving the region and coming back draws the same field, keep-out included.
        state.coreX += 30_000f
        state.refreshObstacleWindow()
        state.coreX -= 30_000f
        state.refreshObstacleWindow()
        assertEquals(back.sortedBy { it.id }, state.obstacleField.obstacles.sortedBy { it.id })
    }

    @Test
    fun destroyedObstaclesStayGoneInTheirOwnLayerOnly() {
        val state = farState(seed = 313)
        val realspace = state.obstacleField.obstacles
        val gone = realspace.first()
        state.destroyedObstacleIds = listOf(gone.id)
        state.switchLayer(WorldLayer.ZENITH)
        val zenith = state.obstacleField.obstacles
        assertEquals(farState(seed = 313).apply { switchLayer(WorldLayer.ZENITH) }.obstacleField.obstacles, zenith)
        // The same chunk and slot in Zenith is another obstacle; destroying it leaves realspace alone.
        val zenithTwin = obstacleId(0, 0, 0, WorldLayer.ZENITH) or (gone.id and ((1L shl 60) - 1))
        state.destroyedObstacleIds = listOf(gone.id, zenith.first().id, zenithTwin)
        state.switchWorldWindow()
        assertEquals(zenith.filter { it.id != zenith.first().id && it.id != zenithTwin }, state.obstacleField.obstacles)
        state.switchLayer(WorldLayer.BASE)
        assertEquals(realspace - gone, state.obstacleField.obstacles)
    }

    private fun farState(seed: Int, content: GameplayContentSnapshot = canonicalGameplayContent) =
        buildTestState(seed = seed, content = content).apply {
            coreX = FAR_X
            coreY = FAR_Y
            refreshObstacleWindow()
        }

    /** What a layer switch does to generation: the new layer, a keep-out at the Core (or none) and a fresh window. */
    private fun MutableGameState.switchLayer(layer: WorldLayer, keepOutRadius: Float? = null) {
        worldLayer = layer
        worldKeepOut = keepOutRadius?.let { WorldKeepOut(layer, worldOriginX + coreX, worldOriginY + coreY, it) }
        switchWorldWindow()
    }

    private fun MutableGameState.distanceFrom(obstacle: Obstacle, absoluteX: Double, absoluteY: Double): Double {
        val dx = obstacle.x + worldOriginX - absoluteX
        val dy = obstacle.y + worldOriginY - absoluteY
        return sqrt(dx * dx + dy * dy)
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

        /** Far from the run origin, whose own safe radius would hide the keep-out. */
        const val FAR_X: Float = 7_300f
        const val FAR_Y: Float = -4_100f

        /** The shipped arrival clear radius. */
        const val ARRIVAL_CLEAR: Float = 900f
    }
}
