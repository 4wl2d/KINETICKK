// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.CombatFieldTable
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldFlags
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The run's field table: admission, per-kind replacement, compaction, rebase, isolation and projection. */
class CombatFieldTableTest {
    private val sharedKinds = listOf(CombatFieldKind.FUSION, CombatFieldKind.ROT_WELL, CombatFieldKind.DARK_STAR)

    @Test
    fun combatFieldsAcceptTwelveAndRejectTheThirteenthReactionField() {
        val state = buildTestState()
        val policy = state.content.build.policy
        val pool = policy.maximumFields - policy.maximumSkillFields
        assertEquals(9, pool)
        repeat(pool) { index ->
            assertEquals(index, state.addTestField(sharedKinds[index % sharedKinds.size], x = index.toFloat(), y = 0f, radius = 50f))
        }
        // The pool is full: a kind still below its cap is refused, and nothing moved.
        assertEquals(-1, state.addTestField(CombatFieldKind.NULL_WELL, x = 99f, y = 0f, radius = 50f))
        assertEquals(pool, state.combatFieldTable.count)

        // The skill reserve fills the table to the bound, one field per slot.
        repeat(policy.maximumSkillFields) { slot ->
            assertEquals(pool + slot, state.addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, slot, 10f * slot, 0f,
                radius = 80f, seconds = 4f, damagePerSecond = 0f))
        }
        assertEquals(policy.maximumFields, state.combatFieldTable.count)
        assertEquals(12, state.combatFieldTable.count)
        assertEquals(-1, state.addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, policy.maximumSkillFields, 0f, 0f,
            radius = 80f, seconds = 4f, damagePerSecond = 0f), "A fourth skill owner has no reserved field")
        assertEquals(-1, state.addTestField(CombatFieldKind.NULL_WELL, x = 99f, y = 0f, radius = 50f))

        // A recast overwrites its own slot in place.
        val recast = state.addField(CombatFieldKind.STORM_COIL, FieldSource.SKILL, 1, 500f, 600f, radius = 120f, seconds = 6f,
            damagePerSecond = 0f)
        assertEquals(pool + 1, recast)
        assertEquals(12, state.combatFieldTable.count)
        val record = state.fieldRecords[recast]
        assertEquals(CombatFieldKind.STORM_COIL, record.kind)
        assertEquals(500f, record.x)
        assertEquals(6f, record.remaining)
    }

    @Test
    fun aKindAtItsCapReplacesItsOldestFieldInPlace() {
        val state = buildTestState()
        state.addTestField(CombatFieldKind.FUSION, x = 1f, y = 0f, radius = 50f)
        state.addTestField(CombatFieldKind.ROT_WELL, x = 100f, y = 0f, radius = 50f)
        state.addTestField(CombatFieldKind.FUSION, x = 2f, y = 0f, radius = 50f)
        state.addTestField(CombatFieldKind.FUSION, x = 3f, y = 0f, radius = 50f)
        assertEquals(CombatFieldKind.FUSION.cap, state.combatFieldTable.countOf(CombatFieldKind.FUSION))

        assertEquals(0, state.addTestField(CombatFieldKind.FUSION, x = 4f, y = 0f, radius = 50f))
        assertEquals(listOf(4f, 100f, 2f, 3f), state.fieldRecords.map { it.x })
        // The next one replaces the field admitted next, not the slot just written.
        assertEquals(2, state.addTestField(CombatFieldKind.FUSION, x = 5f, y = 0f, radius = 50f))
        assertEquals(listOf(4f, 100f, 5f, 3f), state.fieldRecords.map { it.x })
        assertEquals(4, state.combatFieldTable.count)

        // Every capped kind keeps at most its cap.
        val caps = mapOf(
            CombatFieldKind.FUSION to 3, CombatFieldKind.ROT_WELL to 3, CombatFieldKind.DARK_STAR to 4,
            CombatFieldKind.NULL_WELL to 3, CombatFieldKind.PLASMA to 3, CombatFieldKind.HUSH to 2,
            CombatFieldKind.TIME_BUBBLE to 2, CombatFieldKind.ATTRACTOR to 2,
        )
        assertEquals(caps, CombatFieldKind.entries.filter { it.cap > 0 }.associateWith { it.cap })
        caps.forEach { (kind, cap) ->
            val fresh = buildTestState()
            repeat(cap + 2) { fresh.addTestField(kind, x = it.toFloat(), y = 0f, radius = 10f) }
            assertEquals(cap, fresh.combatFieldTable.count, "$kind")
            assertEquals((2 until cap + 2).map(Int::toFloat).toSet(), fresh.fieldRecords.map { it.x }.toSet(), "$kind")
        }
    }

    @Test
    fun spentFieldsAreCompactedInCreationOrder() {
        val state = buildTestState()
        state.addTestField(CombatFieldKind.FUSION, x = 1f, y = 0f, radius = 10f, seconds = 0.5f)
        state.addTestField(CombatFieldKind.ROT_WELL, x = 2f, y = 0f, radius = 10f, seconds = 2f)
        state.addTestField(CombatFieldKind.DARK_STAR, x = 3f, y = 0f, radius = 10f, seconds = 0.3f)
        state.addTestField(CombatFieldKind.NULL_WELL, x = 4f, y = 0f, radius = 10f, seconds = 2f)
        val revision = state.combatFieldTable.revision

        state.updateCombatFields(0.4f)
        assertEquals(listOf(1f, 2f, 4f), state.fieldRecords.map { it.x })
        assertTrue(state.combatFieldTable.revision > revision)
        listOf(0.1f, 1.6f, 1.6f).zip(state.fieldRecords.map { it.remaining }).forEach { (expected, actual) ->
            assertEquals(expected, actual, 1e-5f)
        }

        state.updateCombatFields(0.4f)
        assertEquals(listOf(2f, 4f), state.fieldRecords.map { it.x })
        assertEquals(listOf(CombatFieldKind.ROT_WELL, CombatFieldKind.NULL_WELL), state.fieldRecords.map { it.kind })
        // A freed slot is admitted again at the end.
        assertEquals(2, state.addTestField(CombatFieldKind.FUSION, x = 5f, y = 0f, radius = 10f))
        assertEquals(listOf(2f, 4f, 5f), state.fieldRecords.map { it.x })
    }

    @Test
    fun aFieldATickReplacesKeepsItsReplacementsLifeAndWaitsForTheNextStep() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        target.status[StatusCell.STACKS + StatusElement.GRAVITIC.ordinal] = 2f
        // The oldest Fusion ticks Thermal into the Gravitic stacks: the Fusion that sets off replaces it.
        state.addTestField(CombatFieldKind.FUSION, x = 400f, y = 0f, radius = 120f, seconds = 1f, damagePerSecond = 1f,
            element = StatusElement.THERMAL, stacksPerTick = 1, generation = 0, tickClock = CombatField.TICK_SECONDS)
        state.addTestField(CombatFieldKind.FUSION, x = -900f, y = 0f, radius = 10f, seconds = 1f)
        state.addTestField(CombatFieldKind.FUSION, x = -800f, y = 0f, radius = 10f, seconds = 1f)

        state.updateCombatFields(0.01f)

        assertEquals(0, target.stacksOf(StatusElement.GRAVITIC), "The tick reacted")
        assertEquals(3, state.combatFieldTable.count)
        val replaced = state.fieldRecords[0]
        assertEquals(ReactionId.FUSION.ordinal, replaced.owner)
        assertEquals(1.6f, replaced.remaining, "The replacement keeps its own life")
        assertEquals(0f, replaced.tickClock)
        assertEquals(listOf(1f - 0.01f, 1f - 0.01f), state.fieldRecords.drop(1).map { it.remaining })
    }

    @Test
    fun rebaseShiftsEveryFieldAndItsSegmentEnd() {
        val state = buildTestState()
        state.addField(CombatFieldKind.FROST_LINE, FieldSource.SKILL, 0, 100f, 200f, radius = 12f, seconds = 3f,
            damagePerSecond = 0f, x2 = 300f, y2 = 400f)
        state.addTestField(CombatFieldKind.FUSION, x = -50f, y = 60f, radius = 10f)
        val revision = state.combatFieldTable.revision

        state.rebaseCombatFields(1_000f, -500f)

        assertEquals(listOf(-900f, -1_050f), state.fieldRecords.map { it.x })
        assertEquals(listOf(700f, 560f), state.fieldRecords.map { it.y })
        assertEquals(-700f, state.fieldRecords[0].x2)
        assertEquals(900f, state.fieldRecords[0].y2)
        assertEquals(-1_050f, state.fieldRecords[1].x2)
        assertTrue(state.combatFieldTable.revision > revision)
    }

    @Test
    fun reductionForksIsolateTheTableAndScalarSiblingsShareIt() {
        val state = buildTestState()
        state.addTestField(CombatFieldKind.ROT_WELL, x = 10f, y = 20f, radius = 30f, seconds = 2f)
        val before = state.fieldRecords

        val scalar = state.copyForScalarInputReduction()
        assertSame(state.combatFieldTable, scalar.combatFieldTable)

        val candidate = state.copyForReduction()
        assertNotSame(state.combatFieldTable, candidate.combatFieldTable)
        assertTrue(candidate.combatFieldTable.sharesStorageWith(state.combatFieldTable), "A fork copies nothing up front")
        candidate.updateCombatFields(0.5f)
        candidate.addTestField(CombatFieldKind.FUSION, x = 1f, y = 1f, radius = 5f)
        assertFalse(candidate.combatFieldTable.sharesStorageWith(state.combatFieldTable))
        assertEquals(before, state.fieldRecords)
        assertEquals(2, candidate.combatFieldTable.count)
        assertEquals(1.5f, candidate.fieldRecords[0].remaining)

        val sibling = state.copyForReduction()
        sibling.combatFieldTable.clear()
        assertEquals(before, state.fieldRecords)
        assertEquals(2, candidate.combatFieldTable.count)

        // A new run starts empty, in the candidate only.
        candidate.startRun()
        assertEquals(0, candidate.combatFieldTable.count)
        assertEquals(before, state.fieldRecords)

        val table = CombatFieldTable()
        assertEquals(0, table.count)
        assertEquals(CombatFieldTable.CAPACITY, 16)
    }

    @Test
    fun theCorePassRecordsTheKindsTheCoreStandsIn() {
        val state = buildTestState()
        state.addTestField(CombatFieldKind.ROT_WELL, x = state.coreX + 30f, y = state.coreY, radius = 50f, seconds = 2f)
        state.addTestField(CombatFieldKind.NULL_WELL, x = state.coreX + 300f, y = state.coreY, radius = 50f, seconds = 2f)

        state.updateCombatFields(MutableGameState.FIXED_STEP)
        assertEquals(1 shl CombatFieldKind.ROT_WELL.ordinal, state.coreFieldMask)
        assertTrue(state.combatFieldTable.hasFlag(0, FieldFlags.CORE_INSIDE))
        assertFalse(state.combatFieldTable.hasFlag(1, FieldFlags.CORE_INSIDE))

        state.coreX += 300f
        state.updateCombatFields(MutableGameState.FIXED_STEP)
        assertEquals(1 shl CombatFieldKind.NULL_WELL.ordinal, state.coreFieldMask)
        assertFalse(state.combatFieldTable.hasFlag(0, FieldFlags.CORE_INSIDE))

        state.combatFieldTable.clear()
        state.updateCombatFields(MutableGameState.FIXED_STEP)
        assertEquals(0, state.coreFieldMask)
    }

    @Test
    fun theRenderModelProjectsLiveFieldsAndReusesThemUntilTheyChange() {
        val state = buildTestState()
        state.elapsed = 10f
        state.addField(CombatFieldKind.FUSION, FieldSource.REACTION, ReactionId.FUSION.ordinal, 100f, 50f, radius = 150f,
            seconds = 1.6f, damagePerSecond = 3f, element = StatusElement.THERMAL)
        state.addField(CombatFieldKind.DARK_STAR, FieldSource.ITEM, 42, -100f, 0f, radius = 90f, seconds = 4f,
            damagePerSecond = 0f)
        state.addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, 2, 0f, 0f, radius = 200f, seconds = 5f,
            damagePerSecond = 0f, anchorId = 7, flags = FieldFlags.FOLLOWS_CORE)

        val model = state.toRenderModel()
        val fields = model.combatFields
        assertEquals(listOf(CombatFieldVisual.FUSION, CombatFieldVisual.DARK_STAR, CombatFieldVisual.ECHO_RING), fields.map { it.kind })
        assertEquals(listOf(CombatFieldSource.REACTION, CombatFieldSource.ITEM, CombatFieldSource.SKILL), fields.map { it.source })
        assertEquals(listOf(ReactionId.FUSION.ordinal, -1, -1), fields.map { it.reaction })
        assertEquals(listOf(StatusElement.THERMAL.ordinal, -1, -1), fields.map { it.element })
        assertEquals(listOf(10f, 10f, 10f), fields.map { it.createdAt })
        assertEquals(listOf(11.6f, 14f, 15f), fields.map { it.expiresAt })
        assertEquals(listOf(100f, -100f, 0f), fields.map { it.x2 })
        assertEquals(listOf(-1, -1, 7), fields.map { it.anchorId })
        assertEquals(listOf(false, false, true), fields.map { it.followsCore })

        // A step that only ages the fields reprojects nothing; a moved field rebuilds the list.
        val aged = state.copyForReduction().apply { updateCombatFields(0.1f) }
        assertSame(fields, aged.toRenderModel(reusableCollections = model, identitySource = state).combatFields)
        assertSame(fields, state.copyForScalarInputReduction().toRenderModel(model, state).combatFields)
        val moved = state.copyForReduction().apply { rebaseCombatFields(10f, 0f) }
        val rebuilt = moved.toRenderModel(reusableCollections = model, identitySource = state).combatFields
        assertNotSame(fields, rebuilt)
        assertEquals(listOf(90f, -110f, -10f), rebuilt.map { it.x })
        assertEquals(immutableListOf(), buildTestState().toRenderModel().combatFields)
    }

    @Test
    fun fieldKindsMirrorTheirPublicVisuals() {
        assertEquals(CombatFieldKind.entries.map { it.name }, CombatFieldVisual.entries.map { it.name })
        assertEquals(FieldSource.entries.map { it.name }, CombatFieldSource.entries.map { it.name })
        assertTrue(FieldCell.STRIDE * CombatFieldTable.CAPACITY > 0)
    }
}
