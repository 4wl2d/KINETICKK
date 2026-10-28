// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionalSitesTest {
    private fun game() = MutableGameState(canonicalGameplayContent).apply { startRun(); enemies.clear() }

    /** A run with its first window offered: two points, neither entered. */
    private fun offered() = game().apply {
        elapsed = 45f
        updatePointsOfInterest(0.01f)
        check(pointsOfInterest.size == 2)
    }

    /** Flies the Core past the first offered point, [offset] from its centre, and reports whether it activated. */
    private fun passActivates(offset: Float): Boolean {
        val game = offered()
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        game.optionalSiteMarks(marks)
        game.previousCoreX = marks[0] - 40f
        game.previousCoreY = marks[1] + marks[2] + offset
        game.coreX = marks[0] + 40f
        game.coreY = marks[1] + marks[2] + offset
        game.updatePointsOfInterest(0.01f)
        return game.pointsOfInterest.any { it.active }
    }

    @Test
    fun everyOfferedPointIsOneMarkInListOrder() {
        val game = offered()
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)

        assertEquals(2, game.optionalSiteMarks(marks))
        game.pointsOfInterest.forEachIndexed { index, point ->
            assertEquals(point.x, marks[index * 3])
            assertEquals(point.y, marks[index * 3 + 1])
        }
    }

    @Test
    fun aMarkRadiusIsTheCircleThatActivatesItsPoint() {
        assertTrue(passActivates(offset = -0.5f), "A pass just inside the mark enters the point")
        assertFalse(passActivates(offset = 0.5f), "A pass just outside the mark leaves it offered")
    }

    @Test
    fun marksStopAtTheBoundAndAShortBufferIsRefused() {
        val game = game()
        val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        fun points(count: Int) = List(count) { index ->
            PointOfInterestState(index, SiteKind.RESONANT_CIRCUIT, index * 10f, 0f, 85f)
        }

        game.pointsOfInterest = points(MAX_OPTIONAL_SITE_MARKS)
        assertEquals(MAX_OPTIONAL_SITE_MARKS, game.optionalSiteMarks(marks))
        game.pointsOfInterest = points(MAX_OPTIONAL_SITE_MARKS + 1)
        assertEquals(MAX_OPTIONAL_SITE_MARKS, game.optionalSiteMarks(marks))
        assertEquals((MAX_OPTIONAL_SITE_MARKS - 1) * 10f, marks[(MAX_OPTIONAL_SITE_MARKS - 1) * 3])
        assertFailsWith<IllegalArgumentException> { game.optionalSiteMarks(FloatArray(MAX_OPTIONAL_SITE_MARKS * 3 - 1)) }
    }

    @Test
    fun engagementCoversAnEnteredPointAndEveryDirectedReward() {
        val skipped = offered()
        assertFalse(skipped.optionalSitesTouched(), "An offer the Core has not entered is not engagement")
        skipped.activatePointOfInterest(skipped.pointsOfInterest.first())
        assertTrue(skipped.optionalSitesTouched())

        val pending = game().apply { pendingDirectedRewards = listOf(DirectedReward.WEAPON) }
        assertTrue(pending.optionalSitesTouched())
        val choosing = game().apply { directedReward = DirectedReward.WEAPON }
        assertTrue(choosing.optionalSitesTouched())
        assertFalse(game().optionalSitesTouched())
    }
}
