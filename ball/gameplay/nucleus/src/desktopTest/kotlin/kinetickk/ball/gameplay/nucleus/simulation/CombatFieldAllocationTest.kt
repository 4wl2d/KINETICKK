// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import java.lang.management.ManagementFactory
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The field table advances, admits, replaces and compacts fields without allocating (0.5.0 combat foundations). */
class CombatFieldAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun sixHundredStepsOverTwelveMixedFieldsAllocateNothingBeyondTheEmptyArena() {
        val fields = arena()
        val empty = arena()
        refill(fields)
        assertEquals(fields.content.build.policy.maximumFields, fields.combatFieldTable.count)

        val fieldBytes = bytesPerRound(fields, withFields = true)
        val emptyBytes = bytesPerRound(empty, withFields = false)
        println("COMBAT-FIELD-ALLOCATION fields=$fieldBytes empty=$emptyBytes steps=$STEPS")
        assertTrue(fields.combatFieldTable.count > 0)
        assertEquals(emptyBytes, fieldBytes, "$STEPS steps over the field table allocated beyond the empty arena")
    }

    @Test
    fun sixHundredStepsOverShotEatingBendingAndPickupFieldsAllocateNothingBeyondTheEmptyArena() {
        val fields = crowdedArena()
        val empty = crowdedArena()
        refillVerbFields(fields)
        assertEquals(fields.content.build.policy.maximumFields, fields.combatFieldTable.count)
        fields.guardShards = MutableGameState.MAX_GUARD_SHARDS
        fields.guardUntil = Float.MAX_VALUE
        fields.guardDamage = 1f

        val fieldBytes = verbBytesPerRound(fields, withFields = true)
        val emptyBytes = verbBytesPerRound(empty, withFields = false)
        println("COMBAT-FIELD-VERB-ALLOCATION fields=$fieldBytes empty=$emptyBytes steps=$STEPS")
        assertEquals(emptyBytes, fieldBytes, "$STEPS steps over shot, bend, pickup and guard passes allocated beyond the empty arena")
    }

    /** The crowd plus 450 hostile bolts, 120 player shots and 200 Data pickups inside the fields' reach. */
    private fun crowdedArena(): MutableGameState = arena().apply {
        repeat(MAX_BOLTS) { index -> hostileBolt(-1_500f + (index % 30) * 8f, 1_500f + (index / 30) * 8f) }
        repeat(PLAYER_SHOTS) { index ->
            projectiles += kinetickk.ball.gameplay.nucleus.model.Projectile(x = 1_500f + (index % 12) * 10f, y = -1_500f + (index / 12) * 10f,
                vx = 300f, vy = 0f, radius = 4f, life = Float.MAX_VALUE, hostile = false, damage = 1f)
        }
        repeat(PICKUPS) { index ->
            pickups += kinetickk.ball.gameplay.nucleus.model.Pickup(kinetickk.ball.gameplay.nucleus.render.PickupType.DATA,
                -1_500f + (index % 20) * 12f, -1_500f + (index / 20) * 12f).also { it.life = Float.MAX_VALUE }
        }
    }

    /**
     * Twelve fields: two Maws and two Time Bubbles over the bolts (the four shot eaters), two attractors over the
     * player shots, two stars over the pickups, a rot well, and one skill field per slot.
     */
    private fun refillVerbFields(state: MutableGameState) {
        val anchor = state.enemies.first().id
        fun reaction(kind: CombatFieldKind, x: Float, y: Float, anchorId: Int = -1) {
            state.addField(kind, FieldSource.REACTION, 0, x = x, y = y, radius = 150f, seconds = 0.4f, damagePerSecond = 0f,
                pull = if (kind == CombatFieldKind.ATTRACTOR) 260f else 0f, anchorId = anchorId)
        }
        reaction(CombatFieldKind.NULL_WELL, -1_400f, 1_560f)
        reaction(CombatFieldKind.NULL_WELL, -1_300f, 1_560f)
        reaction(CombatFieldKind.TIME_BUBBLE, -1_400f, 1_620f)
        reaction(CombatFieldKind.TIME_BUBBLE, -1_300f, 1_620f)
        reaction(CombatFieldKind.ATTRACTOR, 1_560f, -1_450f)
        reaction(CombatFieldKind.ATTRACTOR, 1_600f, -1_450f, anchorId = anchor)
        reaction(CombatFieldKind.FUSION, -1_400f, -1_400f)
        reaction(CombatFieldKind.FUSION, -1_300f, -1_400f)
        reaction(CombatFieldKind.ROT_WELL, 0f, -2_000f)
        for (slot in 0 until state.content.build.policy.maximumSkillFields) {
            state.addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, slot, x = state.coreX + slot * 50f, y = state.coreY,
                radius = 140f, seconds = 1f, damagePerSecond = 5f, pull = 120f)
        }
    }

    private fun verbSteps(state: MutableGameState, withFields: Boolean) {
        for (step in 0 until STEPS) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateCombatFields(MutableGameState.FIXED_STEP)
            state.updateCoreReactionStates()
            if (withFields && step % REFILL_EVERY == 0) refillVerbFields(state)
        }
    }

    private fun verbBytesPerRound(state: MutableGameState, withFields: Boolean): Long {
        repeat(WARMUP_ROUNDS) { verbSteps(state, withFields) }
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(ROUNDS) { verbSteps(state, withFields) }
        return (threads.getThreadAllocatedBytes(thread) - before) / ROUNDS
    }

    /** A quiet arena with a crowd the fields reach with their pull pass but never tick on. */
    private fun arena(): MutableGameState = buildTestState().apply {
        repeat(ENEMIES) { index -> dummy(x = 2_000f + (index % 10) * 40f, y = (index / 10) * 40f) }
    }

    /** Twelve fields: the shared pool in three capped kinds, then one per skill slot; short lives so some expire. */
    private fun refill(state: MutableGameState) {
        val policy = state.content.build.policy
        val pool = policy.maximumFields - policy.maximumSkillFields
        for (index in 0 until pool) {
            val kind = when (index % 3) {
                0 -> CombatFieldKind.FUSION
                1 -> CombatFieldKind.ROT_WELL
                else -> CombatFieldKind.NULL_WELL
            }
            state.addField(kind, FieldSource.REACTION, 0, x = 2_000f + index * 30f, y = 1_500f, radius = 160f,
                seconds = 0.3f + index * 0.1f, damagePerSecond = 5f, pull = 220f)
        }
        for (slot in 0 until policy.maximumSkillFields) {
            state.addField(CombatFieldKind.ECHO_RING, FieldSource.SKILL, slot, x = state.coreX + slot * 50f, y = state.coreY,
                radius = 140f, seconds = 1f, damagePerSecond = 5f, pull = 120f)
        }
    }

    private fun steps(state: MutableGameState, withFields: Boolean) {
        for (step in 0 until STEPS) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateCombatFields(MutableGameState.FIXED_STEP)
            // Re-admission overwrites the oldest field of a capped kind or refills compacted slots.
            if (withFields && step % REFILL_EVERY == 0) refill(state)
        }
    }

    private fun bytesPerRound(state: MutableGameState, withFields: Boolean): Long {
        repeat(WARMUP_ROUNDS) { steps(state, withFields) }
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(ROUNDS) { steps(state, withFields) }
        return (threads.getThreadAllocatedBytes(thread) - before) / ROUNDS
    }

    private companion object {
        const val ENEMIES = 120
        const val STEPS = 600
        const val REFILL_EVERY = 45
        const val WARMUP_ROUNDS = 20
        const val ROUNDS = 10
        const val MAX_BOLTS = 450
        const val PLAYER_SHOTS = 120
        const val PICKUPS = 200
    }
}
