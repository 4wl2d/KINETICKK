// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Totem chest content: the shipped per-template chest table, the policy defaults and bounds, and the Elite resonance
 * knob. Every bound accepts its limit and rejects the next value.
 */
class TotemChestContentTest {
    private val templates = defaultStructureTemplates()

    @Test
    fun theShippedChestTableMatchesTheDesign() {
        val chances = StructureTemplateId.entries.associateWith { id -> templates[id.ordinal].totemChestChance }
        assertEquals(
            mapOf(
                StructureTemplateId.DERELICT_STATION to 0.5f,
                StructureTemplateId.VAULT to 0.8f,
                StructureTemplateId.SPIRE to 0.5f,
                StructureTemplateId.LABYRINTH to 0.6f,
                StructureTemplateId.FOUNDRY to 0.6f,
                StructureTemplateId.RELIQUARY to 1f,
                StructureTemplateId.ARCHITECTS_DOODLE to 0f,
            ),
            chances,
        )
        val minimums = templates.map { it.totemChestMinCharges }
        assertEquals(listOf(1, 2, 1, 1, 1, 2, 1), minimums)
        assertTrue(templates.filter { it.secret }.all { it.totemChestChance == 0f })
    }

    @Test
    fun thePolicyDefaultsHoldTheDesignedRoll() {
        val policy = StructurePolicy().totemChest
        assertTrue(policy.enabled)
        assertEquals(listOf(55f, 33f, 12f), policy.chargeWeights.toList())
        assertEquals(64f, policy.contactRadius)
        assertEquals(0.35f, policy.rearmSeconds)
        assertEquals(0.6f, policy.materializeSeconds)
        assertEquals(40, policy.salvageMatterPerCharge)
        assertEquals(128, ContentBounds.MAX_SPENT_STRUCTURES)
        assertEquals(4, ContentBounds.MAX_TOTEM_CHESTS)
        assertEquals(3, ContentBounds.MAX_TOTEM_CHEST_CHARGES)
    }

    @Test
    fun templateChestFieldsAcceptTheirLimitsAndRejectTheNext() {
        val station = templates[StructureTemplateId.DERELICT_STATION.ordinal]
        station.copy(totemChestChance = 0f)
        station.copy(totemChestChance = 1f)
        assertRejected("Totem chest chance") { station.copy(totemChestChance = 1f.nextAbove()) }
        assertRejected("Totem chest chance") { station.copy(totemChestChance = 0f.nextBelow()) }
        assertRejected("Totem chest chance") { station.copy(totemChestChance = Float.NaN) }
        station.copy(totemChestMinCharges = 1)
        station.copy(totemChestMinCharges = ContentBounds.MAX_TOTEM_CHEST_CHARGES)
        assertRejected("minimum charges") { station.copy(totemChestMinCharges = 0) }
        assertRejected("minimum charges") { station.copy(totemChestMinCharges = ContentBounds.MAX_TOTEM_CHEST_CHARGES + 1) }
        val doodle = templates[StructureTemplateId.ARCHITECTS_DOODLE.ordinal]
        assertRejected("pays no Totem chest") { doodle.copy(totemChestChance = 0.1f) }
    }

    @Test
    fun policyBoundsAcceptTheirLimitsAndRejectTheNext() {
        TotemChestPolicy(chargeWeights = immutableListOf(0f, 0f, 1f))
        assertRejected("charge weights") { TotemChestPolicy(chargeWeights = immutableListOf(1f, 1f)) }
        assertRejected("charge weights") { TotemChestPolicy(chargeWeights = immutableListOf(1f, 1f, 1f, 1f)) }
        assertRejected("not all zero") { TotemChestPolicy(chargeWeights = immutableListOf(0f, 0f, 0f)) }
        assertRejected("not all zero") { TotemChestPolicy(chargeWeights = immutableListOf(1f, -1f, 1f)) }
        assertRejected("not all zero") { TotemChestPolicy(chargeWeights = immutableListOf(1f, Float.NaN, 1f)) }
        TotemChestPolicy(contactRadius = 24f)
        TotemChestPolicy(contactRadius = 160f)
        assertRejected("contact radius") { TotemChestPolicy(contactRadius = 24f.nextBelow()) }
        assertRejected("contact radius") { TotemChestPolicy(contactRadius = 160f.nextAbove()) }
        TotemChestPolicy(rearmSeconds = 0.1f)
        TotemChestPolicy(rearmSeconds = 2f)
        assertRejected("re-arm") { TotemChestPolicy(rearmSeconds = 0.1f.nextBelow()) }
        assertRejected("re-arm") { TotemChestPolicy(rearmSeconds = 2f.nextAbove()) }
        TotemChestPolicy(materializeSeconds = 0f)
        TotemChestPolicy(materializeSeconds = 2f)
        assertRejected("materialise") { TotemChestPolicy(materializeSeconds = 0f.nextBelow()) }
        assertRejected("materialise") { TotemChestPolicy(materializeSeconds = 2f.nextAbove()) }
        TotemChestPolicy(salvageMatterPerCharge = 0)
        TotemChestPolicy(salvageMatterPerCharge = 400)
        assertRejected("salvage") { TotemChestPolicy(salvageMatterPerCharge = -1) }
        assertRejected("salvage") { TotemChestPolicy(salvageMatterPerCharge = 401) }
        StructurePolicy(totemChest = TotemChestPolicy(enabled = false))
    }

    @Test
    fun eliteResonanceLevelsStayWithinZeroToThree() {
        assertEquals(1, RunTempoProfile().eliteResonanceLevels)
        RunTempoProfile(eliteResonanceLevels = 0)
        RunTempoProfile(eliteResonanceLevels = 3)
        assertRejected("Elite resonance") { RunTempoProfile(eliteResonanceLevels = -1) }
        assertRejected("Elite resonance") { RunTempoProfile(eliteResonanceLevels = 4) }
    }

    private fun assertRejected(fragment: String, block: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException> { block() }
        assertTrue(error.message.orEmpty().contains(fragment), "Expected '$fragment' in '${error.message}'")
    }
}
