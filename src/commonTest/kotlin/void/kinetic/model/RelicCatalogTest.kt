package void.kinetic.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelicCatalogTest {
    @Test
    fun relicCatalogContainsFortyOrderedUniqueRelicsAcrossSixStandardAspects() {
        val relics = RelicCatalog.all
        val standardAspects = RelicAspect.entries.filter { it != RelicAspect.SOVEREIGN }

        assertEquals(40, RelicCatalog.RELIC_COUNT)
        assertEquals(RelicCatalog.RELIC_COUNT, RelicId.entries.size)
        assertEquals(RelicCatalog.RELIC_COUNT, relics.size)
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
            assertTrue(relic.name.isNotBlank(), "${relic.id} has a blank name")
            assertTrue(relic.description.isNotBlank(), "${relic.id} has a blank description")
            assertTrue(relic.rankEffect.isNotBlank(), "${relic.id} has a blank rank effect")
            assertEquals(relic.aspect == RelicAspect.SOVEREIGN, relic.isSovereign)
            assertEquals(relic, RelicCatalog.byId(relic.id))
        }
    }

    @Test
    fun relicCapacityAndEquippedRankBoundariesAreFixedAndValidated() {
        assertEquals(4, RelicCatalog.MAX_SLOTS)
        assertEquals(5, RelicCatalog.MAX_RANK)
        assertTrue(RelicCatalog.MAX_SLOTS in 1 until RelicCatalog.RELIC_COUNT)
        assertTrue(RelicCatalog.MAX_RANK > 1)

        val id = RelicId.KINETIC_FLYWHEEL
        assertEquals(1, EquippedRelic(id, rank = 1).rank)
        assertEquals(RelicCatalog.MAX_RANK, EquippedRelic(id, rank = RelicCatalog.MAX_RANK).rank)
        assertFailsWith<IllegalArgumentException> { EquippedRelic(id, rank = 0) }
        assertFailsWith<IllegalArgumentException> { EquippedRelic(id, rank = RelicCatalog.MAX_RANK + 1) }
    }
}
