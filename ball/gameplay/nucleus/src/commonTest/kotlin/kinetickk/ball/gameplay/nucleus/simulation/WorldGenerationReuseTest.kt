// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WorldGenerationReuseTest {
    @Test
    fun windowGenerationMatchesIndependentChunksAcrossSeedsLayersAndSuppression() {
        for (seed in listOf(0, 731_991, -42)) {
            val state = MutableGameState(canonicalGameplayContent, seed = seed).apply { startRun() }
            for (layer in WorldLayer.entries) {
                state.worldLayer = layer
                state.obstacleField = ObstacleField.EMPTY
                for ((chunkX, chunkY) in listOf(0 to 0, 1 to 0, 1 to -1, 38 to -21, -39 to 23)) {
                    val chunk = state.content.obstacles.chunkSize
                    state.coreX = (chunkX + 0.25f) * chunk
                    state.coreY = (chunkY + 0.25f) * chunk
                    state.worldKeepOut = WorldKeepOut(layer, state.coreX.toDouble(), state.coreY.toDouble(), 300f)
                    // Changing the arrival suppression rebuilds all chunks, as on a real layer entry.
                    state.obstacleField = ObstacleField.EMPTY
                    state.refreshObstacleWindow()
                    val half = state.content.obstacles.windowChunks / 2
                    val expected = buildList {
                        for (y in chunkY - half..chunkY + half) {
                            for (x in chunkX - half..chunkX + half) addAll(state.generateChunk(x, y))
                        }
                    }
                    assertEquals(expected, state.obstacleField.obstacles, "seed $seed layer $layer chunk $chunkX,$chunkY")
                    val retained = state.obstacleField
                    val before = retained.obstacles.toList()
                    state.coreX += chunk
                    state.refreshObstacleWindow()
                    assertEquals(before, retained.obstacles, "refresh cannot mutate a retained field")
                    for (obstacle in retained.obstacles) {
                        val current = state.obstacleField.obstacles.find { it.id == obstacle.id } ?: continue
                        assertSame(obstacle, current, "overlapping chunks keep their immutable bodies")
                    }
                }
            }
        }
    }

    @Test
    fun templatePoolRemainsEquivalentToPurePlacementAcrossReductionSiblings() {
        for (seed in listOf(0, 731_991, -42)) {
            val original = MutableGameState(canonicalGameplayContent, seed = seed).apply { startRun() }
            val sibling = original.copyForReduction()
            for (state in listOf(original, sibling)) {
                for (layer in WorldLayer.entries) for (x in -7..7) for (y in -7..7) {
                    assertEquals(
                        placeStructure(state.content.structures, seed, layer, x, y),
                        state.structurePlacement(x, y, layer),
                        "seed $seed layer $layer cell $x,$y",
                    )
                }
            }
        }
    }
}
