// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyRosterVisualsTest {
    @Test
    fun everyKindHasAClosedOutlineOnTheIconGrid() {
        EnemyType.entries.forEach { type ->
            val outline = enemySilhouette(type)
            assertTrue(outline.contours.isNotEmpty(), "$type")
            outline.contours.forEach { contour ->
                assertTrue(contour.size >= 6 && contour.size % 2 == 0, "$type has a polygon")
                assertTrue(contour.all { it in 0f..24f }, "$type stays on the 24 grid")
            }
        }
    }

    @Test
    fun theNewRosterReadsAsDistinctShapes() {
        val newcomers = listOf(
            EnemyType.SWARMLING, EnemyType.BOMBER, EnemyType.LANCER, EnemyType.SNIPER, EnemyType.AEGIS,
            EnemyType.MINELAYER, EnemyType.PHANTOM, EnemyType.BROODMOTHER, EnemyType.REFLECTOR, EnemyType.HERALD,
        )
        val outlines = newcomers.map(::enemySilhouette)
        assertEquals(newcomers.size, outlines.toSet().size, "Each new kind has its own outline")
        val originals = (EnemyType.entries - newcomers.toSet()).map(::enemySilhouette).toSet()
        assertTrue(outlines.none { it in originals }, "No new kind borrows an original outline")
    }

    @Test
    fun aimingKindsPointAlongTheirAimAndOthersTurnOnTheClock() {
        assertEquals(listOf(EnemyType.LANCER, EnemyType.SNIPER, EnemyType.REFLECTOR), EnemyType.entries.filter(::facesAim))
        assertEquals(90f, aimDegrees(0f), 1e-4f, "An outline pointing up turns to face +x")
        assertEquals(180f, aimDegrees(kotlin.math.PI.toFloat() / 2f), 1e-3f)
        assertFalse(enemyHasCoreDot(EnemyType.HERALD))
        assertTrue(enemyRotation(EnemyType.HERALD, 9f, 1) != enemyRotation(EnemyType.HERALD, 0f, 1))
    }
}
