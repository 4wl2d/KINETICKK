// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.RelicValue
import kinetickk.ball.content.api.StatusElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelicCatalogTest {
    private val content = createContentCatalog().gameplayContent()

    @Test
    fun relicCatalogContainsTwentyTwoOrderedRuleRewritesEachWithALiability() {
        val relics = content.relics

        assertEquals(22, RelicId.entries.size)
        assertEquals(RelicId.entries.size, relics.size)
        assertTrue(relics.size <= ContentBounds.MAX_RELICS)
        assertEquals(RelicId.entries.toList(), relics.map(RelicDefinition::id))
        assertEquals(relics.size, relics.map(RelicDefinition::name).toSet().size)
        assertEquals(relics.size, relics.map(RelicDefinition::upside).toSet().size)
        assertEquals(relics.size, relics.map(RelicDefinition::liability).toSet().size)

        relics.forEach { relic ->
            assertTrue(relic.liability.isNotBlank(), "${relic.name} must cost something")
            assertTrue(relic.keys.size in 1..2)
            val placeholders = Regex("\\{([0-9]+)\\}").findAll(relic.upside).map { it.groupValues[1].toInt() }.toSet()
            assertEquals(relic.values.indices.toSet(), placeholders, "${relic.name} references exactly its values")
            assertEquals(relic.aspect == RelicAspect.SOVEREIGN, relic.isSovereign)
            assertEquals(relic, content.relic(relic.id))
        }
        StatusElement.entries.forEach { element ->
            assertTrue(relics.any { it.aspect.element == element }, "$element needs an aspect Relic")
        }
        assertEquals(6, relics.count { it.aspect == RelicAspect.KINETIC })
    }

    @Test
    fun sonicAndVoidEachDrawOnOneAspectRelicWithTheirKeysAndRankValues() {
        val choir = content.relic(RelicId.THUNDER_CHOIR)
        val covenant = content.relic(RelicId.NULL_COVENANT)

        assertEquals(RelicAspect.SONIC, choir.aspect)
        assertEquals(StatusElement.SONIC, choir.aspect.element)
        assertEquals(listOf(RelicKey.ECHO, RelicKey.SHIELD), choir.keys.toList())
        assertEquals(listOf(kinetickk.ball.content.api.BuildTag.RAM, kinetickk.ball.content.api.BuildTag.ANCHOR), choir.attunedTags.toList())
        assertEquals(listOf(2f, 3f, 4f), (1..3).map { choir.value(0, it) })
        assertEquals(listOf(60f, 80f, 100f), (1..3).map { choir.value(1, it) })

        assertEquals(RelicAspect.VOID, covenant.aspect)
        assertEquals(StatusElement.VOID, covenant.aspect.element)
        assertEquals(listOf(RelicKey.VOID, RelicKey.SHIELD), covenant.keys.toList())
        assertEquals(listOf(kinetickk.ball.content.api.BuildTag.PRECISION, kinetickk.ball.content.api.BuildTag.CHAIN), covenant.attunedTags.toList())
        assertEquals(listOf(40f, 60f, 80f), (1..3).map { covenant.value(0, it) })

        listOf(choir, covenant).forEach { relic ->
            assertEquals(0, relic.minimumRebirth)
            assertTrue(relic.meldable)
            assertFalse(relic.isSovereign)
        }
        // Elemental aspects follow element order and Sovereign stays last for ordinal-indexed colors.
        assertEquals(StatusElement.entries.toList(), RelicAspect.entries.drop(1).dropLast(1).map { it.element })
        assertEquals(RelicAspect.SOVEREIGN, RelicAspect.entries.last())
    }

    @Test
    fun sovereignRelicsAreRebirthGatedAndHollowCrownCannotRankUp() {
        val sovereign = content.relics.filter(RelicDefinition::isSovereign)

        assertEquals(
            mapOf(RelicId.AGONY_SCEPTER to 1, RelicId.HOLLOW_CROWN to 3, RelicId.OUROBOROS_SEAL to 5),
            sovereign.associate { it.id to it.minimumRebirth },
        )
        assertTrue(content.relics.filterNot(RelicDefinition::isSovereign).all { it.minimumRebirth == 0 })
        assertEquals(listOf(RelicId.HOLLOW_CROWN), content.relics.filterNot { it.meldable }.map { it.id })
        assertTrue(sovereign.all { it.minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL })
    }

    @Test
    fun relicValuesGrowLinearlyWithRankAndNeverShrinkBelowTheFirstRank() {
        val value = RelicValue(12f, 3f)

        assertEquals(12f, value.at(0))
        assertEquals(12f, value.at(1))
        assertEquals(15f, value.at(2))
        assertEquals(18f, value.at(3))
        assertEquals(110f, content.relic(RelicId.BLINK_DRIVE).value(0, 1))
        assertEquals(170f, content.relic(RelicId.BLINK_DRIVE).value(0, 3))
        assertEquals(260f, content.relic(RelicId.BLINK_DRIVE).value(1, 3))
        assertFailsWith<IllegalArgumentException> { RelicValue(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { RelicValue(1f, Float.POSITIVE_INFINITY) }
    }

    @Test
    fun sharedKeysHarmonizeAndOpposedKeysInterfereInBothDirections() {
        val perpetual = content.relic(RelicId.PERPETUAL_VECTOR)
        val gyre = content.relic(RelicId.GYRE_ENGINE)
        val stasis = content.relic(RelicId.STASIS_ANCHOR)
        val furnace = content.relic(RelicId.FURNACE_COVENANT)
        val zero = content.relic(RelicId.ABSOLUTE_ZERO)
        val glass = content.relic(RelicId.GLASS_COMET)
        val aegis = content.relic(RelicId.AEGIS_INVERSION)
        val echo = content.relic(RelicId.ECHO_CHAMBER)

        assertTrue(perpetual.harmonizes(gyre) && gyre.harmonizes(perpetual))
        assertTrue(perpetual.dissonates(stasis) && stasis.dissonates(perpetual))
        assertFalse(perpetual.harmonizes(stasis))
        assertTrue(furnace.dissonates(zero) && zero.dissonates(furnace))
        assertTrue(glass.dissonates(aegis), "Fragile opposes Life")
        assertFalse(perpetual.harmonizes(echo) || perpetual.dissonates(echo))

        RelicKey.entries.forEach { a ->
            RelicKey.entries.forEach { b -> assertEquals(RelicKey.opposed(a, b), RelicKey.opposed(b, a)) }
            assertFalse(RelicKey.opposed(a, a))
        }
    }

    @Test
    fun namedPairingsJoinCatalogRelicsAndAgreeWithTheirPublicKeys() {
        val pairings = content.build.pairings

        assertEquals(20, pairings.size)
        assertEquals(pairings.size, pairings.map { setOf(it.first, it.second) }.toSet().size)
        pairings.forEach { pairing ->
            val first = content.relic(pairing.first)
            val second = content.relic(pairing.second)
            when (pairing.kind) {
                RelicPairingKind.CONCORD -> assertTrue(first.harmonizes(second), "${pairing.name} must share a key without opposed keys")
                RelicPairingKind.DISCORD -> assertTrue(first.dissonates(second), "${pairing.name} must join opposed keys")
            }
            assertEquals(pairing, content.pairing(pairing.first, pairing.second))
            assertEquals(pairing, content.pairing(pairing.second, pairing.first))
            assertTrue(pairing.involves(pairing.first) && pairing.involves(pairing.second))
        }
        assertNull(content.pairing(RelicId.PERPETUAL_VECTOR, RelicId.ECHO_CHAMBER))
        assertTrue(pairings.count { it.kind == RelicPairingKind.DISCORD } >= 5, "Interference is a real constraint")
    }

    @Test
    fun relicCapacityAndEquippedRankBoundariesAreCapturedPolicy() {
        val policy = content.relicPolicy

        assertEquals(4, policy.maxSlots)
        assertEquals(3, policy.maxRank)
        assertTrue(policy.maxSlots in 1 until content.relics.size)
        assertTrue(policy.maxRank > 1)
        assertTrue(policy.acceptsRank(1))
        assertTrue(policy.acceptsRank(policy.maxRank))
        assertFalse(policy.acceptsRank(0))
        assertFalse(policy.acceptsRank(policy.maxRank + 1))

        val id = RelicId.PERPETUAL_VECTOR
        assertEquals(1, EquippedRelic(id, rank = 1).rank)
        assertEquals(policy.maxRank, EquippedRelic(id, rank = policy.maxRank).rank)
        assertFailsWith<IllegalArgumentException> { EquippedRelic(id, rank = 0) }
    }
}
