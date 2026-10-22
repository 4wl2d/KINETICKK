// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunTempoLevelTableTest {
    @Test
    fun theLegacyTableReproducesTheQuadraticCurveForEveryLevelBelowTheCap() {
        val tempo = RunTempoProfile(
            maximumLevel = ContentBounds.MAX_RUN_LEVEL,
            levelRequirements = legacyLevelRequirements(ContentBounds.MAX_RUN_LEVEL),
        )

        assertEquals(tempo.maximumLevel - 1, tempo.levelRequirements.size)
        (-1 until tempo.maximumLevel).forEach { level ->
            val legacy = if (level <= 1) 18 else 18 + level * 8 + level * level / 8
            assertEquals(legacy, tempo.dataRequiredForLevel(level), "level $level")
        }
        assertEquals(listOf(18, 34, 43, 52, 61), tempo.levelRequirements.take(5))
    }

    @Test
    fun dataRequiredForLevelIsTotalAndHoldsTheLastEntryAtTheCap() {
        val requirements = (1..41).map { 10 + it }.toImmutableList()
        val tempo = RunTempoProfile(maximumLevel = 42, levelRequirements = requirements)

        assertEquals(11, tempo.dataRequiredForLevel(1))
        assertEquals(51, tempo.dataRequiredForLevel(41))
        assertEquals(51, tempo.dataRequiredForLevel(42))
        assertEquals(51, tempo.dataRequiredForLevel(Int.MAX_VALUE))
        assertEquals(11, tempo.dataRequiredForLevel(Int.MIN_VALUE))
    }

    @Test
    fun maximumLevelAcceptsItsBoundsAndRejectsTheFirstValueOutside() {
        RunTempoProfile(maximumLevel = ContentBounds.MAX_RUN_LEVEL)
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(
                maximumLevel = ContentBounds.MAX_RUN_LEVEL + 1,
                levelRequirements = List(ContentBounds.MAX_RUN_LEVEL) { 18 }.toImmutableList(),
            )
        }
        assertFailsWith<IllegalArgumentException> { legacyLevelRequirements(ContentBounds.MAX_RUN_LEVEL + 1) }

        val smallest = RunTempoProfile(
            maximumLevel = 20,
            levelRequirements = legacyLevelRequirements(20),
            skillSlotLevels = immutableListOf(2, 8, 16),
        )
        assertEquals(19, smallest.levelRequirements.size)
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 1, levelRequirements = immutableListOf())
        }
    }

    @Test
    fun theTableMustListOnePositiveNonDecreasingEntryPerLevelBelowTheCap() {
        val legacy = legacyLevelRequirements(42)
        RunTempoProfile(maximumLevel = 42, levelRequirements = legacy)

        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 42, levelRequirements = legacy.dropLast(1).toImmutableList())
        }
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 42, levelRequirements = (legacy + 900).toImmutableList())
        }
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(maximumLevel = 42, levelRequirements = (listOf(0) + legacy.drop(1)).toImmutableList())
        }
        val decreasing = legacy.toMutableList().apply { this[20] = this[19] - 1 }.toImmutableList()
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(maximumLevel = 42, levelRequirements = decreasing) }
        val flat = legacy.toMutableList().apply { this[20] = this[19] }.toImmutableList()
        RunTempoProfile(maximumLevel = 42, levelRequirements = flat)
    }

    @Test
    fun levelRewardsAndSkillCadenceAreValidated() {
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(levelIntegrity = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(levelRepair = -0.1f) }
        RunTempoProfile(levelIntegrity = 0f, levelRepair = 0f)

        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillSlotLevels = immutableListOf(2, 8)) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillSlotLevels = immutableListOf(2, 8, 16, 24)) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillSlotLevels = immutableListOf(2, 8, 8)) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillSlotLevels = immutableListOf(1, 8, 16)) }
        RunTempoProfile(
            maximumLevel = 17,
            levelRequirements = legacyLevelRequirements(17),
            skillSlotLevels = immutableListOf(2, 8, 16),
        )
        assertFailsWith<IllegalArgumentException> {
            RunTempoProfile(
                maximumLevel = 16,
                levelRequirements = legacyLevelRequirements(16),
                skillSlotLevels = immutableListOf(2, 8, 16),
            )
        }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillRankEveryLevels = 0) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(skillRankGapLevels = -1) }
    }
}
