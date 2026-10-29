// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.length
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Geometry checks isolate obstacle navigation from combat, structures and the movement controller. */
class ObstacleNavigationTest {
    private val openWorld = structuresDisabledContent.withoutObstacleVariants()
    private val crowdedWorld = openWorld.copy(obstacles = openWorld.obstacles.copy(
        meanPerChunk = 2.2f, maximumPerChunk = 5, minimumGap = 90f, safeRadius = 900f,
    ))

    private data class Census(val obstacles: Int, val clearFlights: Int, val flights: Int) {
        val clearShare: Float get() = clearFlights.toFloat() / flights
    }

    private fun census(content: GameplayContentSnapshot): Census {
        var count = 0
        var clear = 0
        var flights = 0
        val chunk = content.obstacles.chunkSize
        for (seed in 31..42) {
            val state = buildTestState(seed = seed, content = content)
            // The outer ring catches bodies beside the sampled flights, including chunk seams.
            val bodies = buildList {
                for (x in 8..21) for (y in -2..11) addAll(state.generateChunk(x, y))
            }
            count += bodies.size
            for (x in 10..19) for (y in 0..9) for (heading in 0..7) {
                val startX = (x + 0.5f) * chunk
                val startY = (y + 0.5f) * chunk
                val angle = heading * 0.7853982f
                val endX = startX + cos(angle) * chunk
                val endY = startY + sin(angle) * chunk
                // A full chunk of straight flight with a 48 u steering margin around the Core.
                if (bodies.none {
                    sweepEntry(startX, startY, endX, endY, it.x, it.y,
                        it.radius + MutableGameState.CORE_RADIUS + 48f) >= 0f
                }) clear++
                flights++
            }
        }
        return Census(count, clear, flights)
    }

    @Test
    fun ordinaryStraightFlightsHaveRoomWithoutRemovingObstaclePlay() {
        val before = census(crowdedWorld)
        val after = census(openWorld)
        println("OBSTACLE NAVIGATION legacy=$before clear=${before.clearShare} shipped=$after clear=${after.clearShare}")
        assertTrue(after.obstacles < before.obstacles * 0.5f, "the field has fewer than half as many bodies")
        assertTrue(after.obstacles > before.obstacles * 0.2f, "obstacles remain a meaningful part of exploration")
        assertTrue(after.clearShare >= 0.75f, "at least three quarters of 1,200 u flights have a wide clear path")
        assertTrue(after.clearShare >= before.clearShare + 0.15f, "straight-flight clearance improves across seeds/headings")
    }

    @Test
    fun wideGapsHoldAcrossChunkSeamsAndRevisits() {
        for (seed in listOf(11, 37, 91)) {
            val state = buildTestState(seed = seed, content = openWorld)
            val bodies = buildList {
                for (x in -8..-3) for (y in 5..10) addAll(state.generateChunk(x, y))
            }
            bodies.forEachIndexed { index, body ->
                for (otherIndex in index + 1 until bodies.size) {
                    val other = bodies[otherIndex]
                    val gap = length(body.x - other.x, body.y - other.y) - body.radius - other.radius
                    assertTrue(gap >= 300f - 0.01f, "seed $seed: a $gap u gap leaves too little maneuvering room")
                }
            }
            state.coreX = -6.5f * openWorld.obstacles.chunkSize
            state.coreY = 7.5f * openWorld.obstacles.chunkSize
            state.refreshObstacleWindow()
            val visited = state.obstacleField.obstacles
            state.coreX += 20f * openWorld.obstacles.chunkSize
            state.refreshObstacleWindow()
            state.coreX -= 20f * openWorld.obstacles.chunkSize
            state.refreshObstacleWindow()
            assertEquals(visited, state.obstacleField.obstacles, "returning keeps every obstacle and its id")
        }
    }
}
