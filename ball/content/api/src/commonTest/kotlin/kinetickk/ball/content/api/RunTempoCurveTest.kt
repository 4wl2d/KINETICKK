// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunTempoCurveTest {
    @Test
    fun theShippedCurveIsTheFortyTwoLevelTable() {
        val tempo = RunTempoProfile()

        assertEquals(42, tempo.maximumLevel)
        assertEquals(SHIPPED_REQUIREMENTS, tempo.levelRequirements)
        assertEquals(SHIPPED_REQUIREMENTS, defaultLevelRequirements(rampPlateau = 190, topPlateau = 0))
        assertEquals(15_148, tempo.levelRequirements.sum(), "Data from level 1 to the cap")
        assertEquals(746, tempo.dataRequiredForLevel(41))
        assertEquals(746, tempo.dataRequiredForLevel(42), "the capped level only displays the last entry")
        assertEquals(2.5f, tempo.levelIntegrity)
        assertEquals(7.5f, tempo.levelRepair)
        assertEquals(listOf(2, 8, 16), tempo.skillSlotLevels)
        assertEquals(4, tempo.skillRankEveryLevels)
        assertEquals(2, tempo.skillRankGapLevels)
    }

    @Test
    fun earlyLevelsKeepTheLegacyRequirementAndLaterLevelsClimbAtLeastAsSteeply() {
        val shipped = RunTempoProfile()
        val legacy = legacyLevelRequirements(42)

        (1..15).forEach { level ->
            val old = legacy[level - 1]
            val new = shipped.dataRequiredForLevel(level)
            assertEquals(old, new, "level $level keeps today's requirement")
            assertTrue(abs(new - old) <= old * 0.15f, "level $level within ±15 %")
        }
        (16..41).forEach { level ->
            assertTrue(shipped.dataRequiredForLevel(level) >= legacy[level - 1], "level $level never cheaper than today")
        }
        // Past the ramp the per-level increase is never below today's, so the late curve is the steeper one.
        (21..41).forEach { level ->
            val increase = shipped.dataRequiredForLevel(level) - shipped.dataRequiredForLevel(level - 1)
            assertTrue(increase >= legacy[level - 1] - legacy[level - 2], "level $level increase")
        }
    }

    @Test
    fun eachPlateauKnobMovesOnlyItsOwnLevels() {
        val shipped = defaultLevelRequirements()
        val ramp = defaultLevelRequirements(rampPlateau = RunTempoProfile.DEFAULT_RAMP_PLATEAU + 10)
        val top = defaultLevelRequirements(topPlateau = 20)

        (1..15).forEach { level -> assertEquals(shipped[level - 1], ramp[level - 1], "ramp leaves level $level") }
        (1..30).forEach { level -> assertEquals(shipped[level - 1], top[level - 1], "top leaves level $level") }
        assertEquals(shipped[15] + 2, ramp[15], "the ramp adds a fifth of its plateau at level 16")
        assertEquals(shipped[19] + 10, ramp[19], "the full plateau from level 20")
        assertEquals(240, ramp.sum() - shipped.sum(), "rampPlateau moves the cap total by 24 per unit")
        assertEquals(180, top.sum() - shipped.sum(), "topPlateau moves the cap total by 9 per unit")
        assertEquals(98, defaultLevelRequirements(maximumLevel = ContentBounds.MAX_RUN_LEVEL).size)
        assertEquals(shipped, defaultLevelRequirements(maximumLevel = 99).take(41))

        assertFailsWith<IllegalArgumentException> { defaultLevelRequirements(rampPlateau = -1) }
        assertFailsWith<IllegalArgumentException> { defaultLevelRequirements(topPlateau = -1) }
        assertFailsWith<IllegalArgumentException> { defaultLevelRequirements(rampPlateau = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { defaultLevelRequirements(maximumLevel = 1) }
        assertFailsWith<IllegalArgumentException> { defaultLevelRequirements(maximumLevel = ContentBounds.MAX_RUN_LEVEL + 1) }
    }

    @Test
    fun maximumLevelAcceptsNinetyNineAndRejectsOneHundred() {
        val highest = RunTempoProfile(maximumLevel = 99)
        assertEquals(98, highest.levelRequirements.size)
        assertEquals(SHIPPED_REQUIREMENTS, highest.levelRequirements.take(41))

        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 100, levelRequirements = List(99) { 18 }.toImmutableList())
        }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(maximumLevel = 100) }
    }

    @Test
    fun levelRequirementsMustBePositiveAndNonDecreasing() {
        RunTempoProfile(maximumLevel = 42, levelRequirements = SHIPPED_REQUIREMENTS.toImmutableList())

        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 42, levelRequirements = SHIPPED_REQUIREMENTS.dropLast(1).toImmutableList())
        }
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 42, levelRequirements = (SHIPPED_REQUIREMENTS + 746).toImmutableList())
        }
        val decreasing = SHIPPED_REQUIREMENTS.toMutableList().apply { this[30] = this[29] - 1 }.toImmutableList()
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(levelRequirements = decreasing) }
        val zero = SHIPPED_REQUIREMENTS.toMutableList().apply { this[0] = 0 }.toImmutableList()
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(levelRequirements = zero) }
        val equalNeighbours = SHIPPED_REQUIREMENTS.toMutableList().apply { this[30] = this[29] }.toImmutableList()
        RunTempoProfile(levelRequirements = equalNeighbours)
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(levelRequirements = immutableListOf()) }
    }

    private companion object {
        /** economy.md §1.3 with the calibrated rampPlateau 190: Data from level L to L + 1 for L = 1..41. */
        val SHIPPED_REQUIREMENTS: List<Int> = listOf(
            18, 34, 43, 52, 61, 70, 80, 90, 100, 110,
            121, 132, 143, 154, 166, 216, 266, 316, 367, 418,
            431, 444, 458, 472, 486, 500, 515, 530, 545, 560,
            576, 592, 608, 624, 641, 658, 675, 692, 710, 728,
            746,
        )
    }
}
