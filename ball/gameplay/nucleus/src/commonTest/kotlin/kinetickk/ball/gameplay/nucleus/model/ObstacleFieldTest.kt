// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ObstacleFieldTest {
    private fun obstacle(id: Long, kind: ObstacleKind) =
        Obstacle(id = id, kind = kind, x = id * 10f, y = 0f, radius = 40f, shapeSeed = id.toInt())

    /** The field list the per-step passes walk: every obstacle with an area effect, in window order. */
    private fun ObstacleField.fieldObstacles(): List<Long> = List(fieldCount) { obstacles[fieldIndex(it)].id }

    private fun ObstacleField.expectedFieldObstacles(): List<Long> = obstacles.filter { it.kind.field }.map { it.id }

    @Test
    fun theFieldListFollowsEveryChangeOfTheWindow() {
        var field = ObstacleField.of(
            0, 0, 2,
            listOf(
                listOf(obstacle(1, ObstacleKind.ASTEROID), obstacle(2, ObstacleKind.GRAVITY_WELL)),
                listOf(obstacle(3, ObstacleKind.CRYSTAL), obstacle(4, ObstacleKind.DERELICT_MINE)),
                emptyList(),
                listOf(obstacle(5, ObstacleKind.THERMAL_VENT), obstacle(6, ObstacleKind.ACCEL_GATE), obstacle(7, ObstacleKind.BUMPER)),
            ),
        )
        assertEquals(listOf(2L, 4L, 5L, 6L), field.fieldObstacles())
        assertEquals(0, ObstacleField.EMPTY.fieldCount)
        // A mine's fuse and a gate's rest replace the obstacle in place.
        field = field.replaced(3, field.obstacles[3].copy(fuseAt = 2f))
        assertEquals(field.expectedFieldObstacles(), field.fieldObstacles())
        // A crystal shattering before a field obstacle shifts the indices after it.
        field = field.removed(2)
        assertEquals(listOf(2L, 4L, 5L, 6L), field.fieldObstacles())
        assertEquals(2f, field.obstacles[field.fieldIndex(1)].fuseAt)
        // A mine detonating leaves the list.
        field = field.removed(field.fieldIndex(1))
        assertEquals(listOf(2L, 5L, 6L), field.fieldObstacles())
        // A replacement that changes whether the obstacle has a field rebuilds the list.
        field = field.replaced(0, field.obstacles[0].copy(kind = ObstacleKind.GRAVITY_WELL))
        assertEquals(field.expectedFieldObstacles(), field.fieldObstacles())
        field = field.replaced(1, field.obstacles[1].copy(kind = ObstacleKind.ASTEROID))
        assertEquals(field.expectedFieldObstacles(), field.fieldObstacles())
        // A rebase moves every obstacle and keeps the list.
        field = field.shifted(100f, -50f)
        assertEquals(field.expectedFieldObstacles(), field.fieldObstacles())
    }
}
