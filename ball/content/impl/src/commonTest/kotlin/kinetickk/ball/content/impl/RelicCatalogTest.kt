// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelicCatalogTest {
    private val content = createContentCatalog().gameplayContent()

    @Test
    fun relicCatalogContainsFortyOrderedUniqueRelicsAcrossSixStandardAspects() {
        val relics = content.relics
        val standardAspects = RelicAspect.entries.filter { aspect ->
            aspect != RelicAspect.SOVEREIGN
        }

        assertEquals(ContentBounds.MAX_RELICS, RelicId.entries.size)
        assertEquals(ContentBounds.MAX_RELICS, relics.size)
        assertEquals(RelicId.entries.toList(), relics.map(RelicDefinition::id))
        assertEquals(relics.size, relics.map(RelicDefinition::id).toSet().size)
        assertEquals(relics.size, relics.map(RelicDefinition::name).toSet().size)
        assertEquals(relics.size, relics.map(RelicDefinition::description).toSet().size)
        assertEquals(relics.size, relics.map(RelicDefinition::rankEffect).toSet().size)

        assertEquals(6, standardAspects.size)
        standardAspects.forEach { aspect ->
            assertEquals(
                6,
                relics.count { relic -> relic.aspect == aspect },
                "$aspect must contain exactly six standard relics",
            )
        }
        assertEquals(4, relics.count(RelicDefinition::isSovereign))

        relics.forEach { relic ->
            assertEquals(relic.aspect == RelicAspect.SOVEREIGN, relic.isSovereign)
            assertEquals(relic, content.relic(relic.id))
        }
    }

    @Test
    fun relicCapacityAndEquippedRankBoundariesAreCapturedPolicy() {
        val policy = content.relicPolicy

        assertEquals(4, policy.maxSlots)
        assertEquals(5, policy.maxRank)
        assertTrue(policy.maxSlots in 1 until content.relics.size)
        assertTrue(policy.maxRank > 1)
        assertTrue(policy.acceptsRank(1))
        assertTrue(policy.acceptsRank(policy.maxRank))
        assertFalse(policy.acceptsRank(0))
        assertFalse(policy.acceptsRank(policy.maxRank + 1))

        val id = RelicId.KINETIC_FLYWHEEL
        assertEquals(1, EquippedRelic(id, rank = 1).rank)
        assertEquals(policy.maxRank, EquippedRelic(id, rank = policy.maxRank).rank)
        assertFailsWith<IllegalArgumentException> { EquippedRelic(id, rank = 0) }
    }
}
