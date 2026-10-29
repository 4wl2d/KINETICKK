// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusSystemTest {
    @Test
    fun stacksAccumulateRefreshOnApplicationAndDecayOneAtATime() {
        val state = buildTestState()
        val enemy = state.dummy()
        state.applyStatus(enemy, StatusElement.THERMAL, 3, generation = 0)
        assertEquals(3, enemy.stacksOf(StatusElement.THERMAL))

        state.elapsed = 1.5f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(3, enemy.stacksOf(StatusElement.THERMAL), "Thermal decays after 1.6 s")

        state.elapsed = 1.6f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(2, enemy.stacksOf(StatusElement.THERMAL), "Exactly one stack decays")

        // A new application refreshes the decay clock of the whole element.
        state.elapsed = 3f
        state.applyStatus(enemy, StatusElement.THERMAL, 1, generation = 0)
        state.elapsed = 4.5f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(3, enemy.stacksOf(StatusElement.THERMAL))

        // Kindling stretches Thermal stacks by half; Cinder Crown's liability halves them again.
        val kindled = buildTestState().apply { own("Kindling") }
        assertEquals(2.4f, kindled.decaySeconds(StatusElement.THERMAL), 0.0001f)
        kindled.acquireRelic(RelicId.CINDER_CROWN)
        assertEquals(1.2f, kindled.decaySeconds(StatusElement.THERMAL), 0.0001f)
    }

    @Test
    fun breakConsumesEveryStackAndReArmsAfterOneSecondWhileStacksCapAtThresholdPlusFour() {
        val state = buildTestState()
        val enemy = state.dummy()
        val ion = state.content.build.element(StatusElement.ION)
        assertEquals(7, state.breakThreshold(StatusElement.ION))

        state.applyStatus(enemy, StatusElement.ION, 9, generation = 0)
        assertEquals(0, enemy.stacksOf(StatusElement.ION), "Overload consumes all nine stacks")
        // Overload strikes for power × consumed stacks (no neighbors to chain to here).
        assertEquals(enemy.maxHp - ion.breakPower * 9f, enemy.hp, 0.01f)
        assertTrue(state.takeVisualFxCues().any { it is VisualFxCue.StatusBroken && it.element == StatusElement.ION.ordinal })

        state.elapsed = 0.5f
        state.applyStatus(enemy, StatusElement.ION, 9, generation = 0)
        assertEquals(9, enemy.stacksOf(StatusElement.ION), "The Break is re-arming")
        state.applyStatus(enemy, StatusElement.ION, 9, generation = 0)
        assertEquals(ion.stackCap, enemy.stacksOf(StatusElement.ION))
        assertEquals(11, ion.stackCap)

        state.elapsed = 1f
        val hpBefore = enemy.hp
        state.applyStatus(enemy, StatusElement.ION, 1, generation = 0)
        assertEquals(0, enemy.stacksOf(StatusElement.ION))
        assertEquals(hpBefore - ion.breakPower * ion.stackCap * state.runScale(), enemy.hp, 0.05f)
    }

    @Test
    fun breakThresholdHonorsItemCutsAffinityTierAndTheContentFloor() {
        val state = buildTestState()
        val thermal = state.content.build.element(StatusElement.THERMAL)
        assertEquals(8, state.breakThreshold(StatusElement.THERMAL))

        state.own("Flashpoint")
        assertEquals(6, state.breakThreshold(StatusElement.THERMAL), "Flashpoint cuts two")
        state.own("Flashpoint")
        assertEquals(5, state.breakThreshold(StatusElement.THERMAL), "A second stack cuts one more")
        state.own("Igniter")
        state.own("Kindling")
        assertEquals(2, state.affinityTier(StatusElement.THERMAL))
        // 8 − 3 − 1 would be 4; the floor keeps Ignite at five.
        assertEquals(thermal.breakFloor, state.breakThreshold(StatusElement.THERMAL))

        val cryo = buildTestState().apply { own("Permafrost", stacks = 2) }
        val target = cryo.dummy()
        assertEquals(4, cryo.breakThreshold(StatusElement.CRYO, target))
        target.status[StatusCell.EMBRITTLED_UNTIL] = cryo.elapsed + 4f
        assertEquals(cryo.content.build.element(StatusElement.CRYO).breakFloor, cryo.breakThreshold(StatusElement.CRYO, target))
    }

    @Test
    fun freezeStopsTheTargetThenThawGrantsImmunityAndHalvesIncomingCryo() {
        val state = buildTestState()
        val enemy = state.dummy()
        state.elapsed = 10f
        state.applyStatus(enemy, StatusElement.CRYO, 6, generation = 0)

        assertTrue(state.isFrozen(enemy))
        assertEquals(11.4f, enemy.status[StatusCell.FROZEN_UNTIL], 0.0001f)
        assertEquals(0f, state.enemyMotionScale(enemy))
        assertEquals(1f, enemy.status[StatusCell.BRITTLE])

        // A shatter thaws the target early; immunity and re-arm count from the thaw.
        state.elapsed = 10.5f
        state.thaw(enemy)
        assertFalse(state.isFrozen(enemy))
        assertEquals(10.5f + FREEZE_IMMUNITY_SECONDS, enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL], 0.0001f)

        state.elapsed = 11f
        state.applyStatus(enemy, StatusElement.CRYO, 4, generation = 0)
        assertEquals(2, enemy.stacksOf(StatusElement.CRYO), "Cryo lands at half strength while immune")
        repeat(4) { state.applyStatus(enemy, StatusElement.CRYO, 2, generation = 0) }
        assertEquals(6, enemy.stacksOf(StatusElement.CRYO))
        assertFalse(state.isFrozen(enemy), "An immune target cannot Freeze again")

        state.elapsed = 13.1f
        state.applyStatus(enemy, StatusElement.CRYO, 1, generation = 0)
        assertTrue(state.isFrozen(enemy))
        assertEquals(0, enemy.stacksOf(StatusElement.CRYO))
    }

    @Test
    fun singleStackCryoAlsoLandsAtHalfStrengthWhileImmune() {
        val state = buildTestState()
        val enemy = state.dummy()
        state.applyStatus(enemy, StatusElement.CRYO, 6, generation = 0)
        state.elapsed = 1.5f
        assertFalse(state.isFrozen(enemy))
        assertTrue(enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] > state.elapsed)

        // One stack per hit lands every second hit; the half stack carries between landings.
        val landed = List(6) {
            state.applyStatus(enemy, StatusElement.CRYO, 1, generation = 0)
            enemy.stacksOf(StatusElement.CRYO)
        }
        assertEquals(listOf(0, 1, 1, 2, 2, 3), landed)

        // Once immunity ends Cryo lands whole again.
        state.elapsed = 1.4f + FREEZE_IMMUNITY_SECONDS + 0.1f
        state.applyStatus(enemy, StatusElement.CRYO, 1, generation = 0)
        assertEquals(4, enemy.stacksOf(StatusElement.CRYO))
    }

    @Test
    fun brittleStrengthensOnlyTheNextDirectHit() {
        val state = buildTestState()
        val enemy = state.dummy()
        enemy.status[StatusCell.BRITTLE] = 1f
        val first = state.dealHit(enemy, 100f, HitSource.WEAPON, canCrit = false).amount
        val second = state.dealHit(enemy, 100f, HitSource.WEAPON, canCrit = false).amount
        assertEquals(160f, first, 0.01f)
        assertEquals(100f, second, 0.01f)
        assertEquals(0f, enemy.status[StatusCell.BRITTLE])
    }

    @Test
    fun elitesResistFreezeLongerAndTheArchitectOnlyStaggers() {
        val state = buildTestState()
        val elite = state.dummy(type = EnemyType.ELITE)
        val architect = state.dummy(x = -400f, type = EnemyType.ARCHITECT)
        state.applyStatus(elite, StatusElement.CRYO, 6, generation = 0)
        state.applyStatus(architect, StatusElement.CRYO, 6, generation = 0)

        assertEquals(1.4f + ELITE_FREEZE_IMMUNITY_SECONDS, elite.status[StatusCell.FREEZE_IMMUNE_UNTIL], 0.0001f)
        assertEquals(0.5f, architect.status[StatusCell.FROZEN_UNTIL], 0.0001f)
        assertEquals(0.5f + ARCHITECT_FREEZE_IMMUNITY_SECONDS, architect.status[StatusCell.FREEZE_IMMUNE_UNTIL], 0.0001f)
    }

    @Test
    fun generationTwoOutputsDropTheirStatusesButKeepTheirDamage() {
        val state = buildTestState()
        state.own("Solar Choir")
        val origin = state.dummy(x = 0f)
        val near = state.dummy(x = 60f)
        val far = state.dummy(x = 130f)

        // A direct Ignite spreads three Thermal as generation one.
        state.applyStatus(origin, StatusElement.THERMAL, 8, generation = 0)
        assertEquals(3, near.stacksOf(StatusElement.THERMAL))
        assertEquals(1, near.generationOf(StatusElement.THERMAL))
        assertEquals(3, far.stacksOf(StatusElement.THERMAL))

        // Its own Ignite is generation one, so the spread it would cause is dropped, but it still burns.
        val farBefore = far.hp
        state.applyStatus(near, StatusElement.THERMAL, 5, generation = 0)
        assertEquals(0, near.stacksOf(StatusElement.THERMAL))
        assertEquals(3, far.stacksOf(StatusElement.THERMAL), "Generation-two spread must not land")
        assertTrue(far.hp < farBefore, "Generation-two damage still lands")

        state.applyStatus(far, StatusElement.ION, 3, generation = 2)
        assertEquals(0, far.stacksOf(StatusElement.ION))
    }

    @Test
    fun breakBudgetAlwaysAdmitsDirectBreaksAndDropsCascadesWhenEmpty() {
        val state = buildTestState()
        val cascade = state.dummy(x = 0f)
        val direct = state.dummy(x = 900f)
        state.breakTokens = 0.5f

        state.applyStatus(cascade, StatusElement.ION, 7, generation = 1)
        assertEquals(7, cascade.stacksOf(StatusElement.ION), "A generation-one Break is dropped while the budget is empty")
        assertEquals(cascade.maxHp, cascade.hp)

        state.applyStatus(direct, StatusElement.ION, 7, generation = 0)
        assertEquals(0, direct.stacksOf(StatusElement.ION), "A direct Break is always admitted")
        assertEquals(0f, state.breakTokens, 0.0001f, "Direct spending never drives the budget into debt")

        state.updateStatuses(0.1f)
        assertEquals(4f, state.breakTokens, 0.0001f)
        state.applyStatus(cascade, StatusElement.ION, 1, generation = 1)
        assertEquals(0, cascade.stacksOf(StatusElement.ION), "Cascades resume as soon as the budget refills")
        assertEquals(3f, state.breakTokens, 0.0001f)
        state.updateStatuses(10f)
        assertEquals(MutableGameState.BREAK_BURST, state.breakTokens)
    }

    @Test
    fun ruptureMaxIntegrityDamageTakesTheDesignVictimShareAndRespectsThePerTargetCooldown() {
        val state = buildTestState()
        val elite = state.dummy(hp = 1_000f, type = EnemyType.ELITE)
        val rupture = state.content.build.element(StatusElement.ENTROPY).breakPower

        // Every %max-integrity term shares one victim factor: elites take 15 %, the Architect 4 %.
        val share = rupture * 0.15f
        state.applyStatus(elite, StatusElement.ENTROPY, 10, generation = 0)
        assertEquals(1_000f * (1f - share), elite.hp, 0.01f)

        // The Break re-arms after a second, but an elite's integrity proc waits two.
        state.elapsed = 1.1f
        state.applyStatus(elite, StatusElement.ENTROPY, 10, generation = 0)
        assertEquals(0, elite.stacksOf(StatusElement.ENTROPY))
        assertEquals(1_000f * (1f - share), elite.hp, 0.01f)

        state.elapsed = 2.2f
        state.applyStatus(elite, StatusElement.ENTROPY, 10, generation = 0)
        assertEquals(1_000f * (1f - 2f * share), elite.hp, 0.01f)
        assertEquals(1f, maxHpProcCooldown(state.dummy()))
        assertEquals(4f, maxHpProcCooldown(state.dummy(type = EnemyType.ARCHITECT)))

        val architect = state.dummy(x = -400f, hp = 10_000f, type = EnemyType.ARCHITECT)
        state.applyStatus(architect, StatusElement.ENTROPY, 10, generation = 0)
        assertEquals(10_000f * (1f - rupture * 0.04f), architect.hp, 0.05f)
    }

    @Test
    fun feedbackMaxIntegrityStrikeUsesTheSameVictimShareAsItemProcs() {
        val feedback = ReactionId.FEEDBACK
        val power = buildTestState().content.build.reaction(feedback).power
        listOf(EnemyType.DRIFTER to 1f, EnemyType.ELITE to 0.15f, EnemyType.ARCHITECT to 0.04f).forEach { (type, factor) ->
            val state = buildTestState()
            val target = state.dummy(hp = 10_000f, type = type)
            state.applyStatus(target, feedback.first, 3, generation = 0)
            state.applyStatus(target, feedback.second, 1, generation = 0)
            assertTrue(state.discoveredReactionMask and (1 shl feedback.ordinal) != 0, "$type")
            assertEquals(10_000f * (1f - power * factor), target.hp, 0.05f, "$type")
        }
    }

    @Test
    fun damageOverTimePulsesAtFourHertzWithoutDamageNumbers() {
        val state = buildTestState()
        state.nextEntityId = 7
        val enemy = state.dummy()
        state.applyStatus(enemy, StatusElement.ENTROPY, 4, generation = 0)
        state.takeVisualFxCues()
        var pulses = 0
        repeat(240) {
            val before = enemy.hp
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateStatuses(MutableGameState.FIXED_STEP)
            if (enemy.hp < before) pulses++
        }
        assertEquals(8, pulses)
        assertTrue(state.takeVisualFxCues().none { it is VisualFxCue.DamageNumberAdded })
    }

    @Test
    fun statusBitsPackStackCountsAndStateFlagsForPresentation() {
        val state = buildTestState()
        val enemy = state.dummy()
        enemy.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 3f
        enemy.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 6f
        enemy.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 1f
        enemy.status[StatusCell.BRITTLE] = 1f

        val expected = 3 or (6 shl (StatusElement.ION.ordinal * 4)) or
            STATUS_FROZEN_BIT or STATUS_BRITTLE_BIT or STATUS_PRIMED_BIT
        assertEquals(expected, state.statusBits(enemy))
        assertEquals(expected, state.toRenderModel().enemies.single().statusBits)

        enemy.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 1f
        assertTrue(state.statusBits(enemy) and STATUS_EMBRITTLED_BIT != 0)
    }

    @Test
    fun primedPresentationUsesExactThresholdAndKeepsPackingLaterElements() {
        val state = buildTestState()
        val enemy = state.dummy()
        for (element in StatusElement.entries) {
            enemy.status.fill(0f)
            // A primed early element cannot stop the packing of the following elements.
            for (other in StatusElement.entries) enemy.status[StatusCell.STACKS + other.ordinal] = 1f
            val threshold = state.breakThreshold(element, enemy)
            for (count in listOf(0, threshold - 2, threshold - 1, threshold)) {
                enemy.status[StatusCell.STACKS + element.ordinal] = count.toFloat()
                val bits = state.statusBits(enemy)
                assertEquals(count == threshold - 1, bits and STATUS_PRIMED_BIT != 0, "$element at $count")
                for (other in StatusElement.entries) {
                    assertEquals(
                        if (other == element) count else 1,
                        (bits ushr (other.ordinal * 4)) and 0xF,
                        "$other after packing $element at $count",
                    )
                }
            }
        }
    }

    @Test
    fun primedPresentationTracksBuildChangesConsumedStacksAndExpiredStates() {
        val state = buildTestState()
        val enemy = state.dummy()
        state.applyStatus(enemy, StatusElement.THERMAL, 5, generation = 0)
        assertFalse(state.statusBits(enemy) and STATUS_PRIMED_BIT != 0)
        state.own("Flashpoint")
        assertEquals(6, state.breakThreshold(StatusElement.THERMAL, enemy))
        assertTrue(state.statusBits(enemy) and STATUS_PRIMED_BIT != 0)
        state.forceElementBreak(enemy, StatusElement.THERMAL)
        assertEquals(0, enemy.stacksOf(StatusElement.THERMAL))
        assertEquals(0, state.statusBits(enemy) and STATUS_PRIMED_BIT, "consumed stacks leave no primed flag")

        val normalCryoThreshold = state.breakThreshold(StatusElement.CRYO, enemy)
        enemy.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 1f
        enemy.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 1f
        enemy.status[StatusCell.BRITTLE] = 1f
        val embrittledCryoThreshold = state.breakThreshold(StatusElement.CRYO, enemy)
        assertTrue(embrittledCryoThreshold < normalCryoThreshold, "this fixture must stay above the threshold floor")
        enemy.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] =
            (embrittledCryoThreshold - 1).toFloat()
        assertEquals(
            STATUS_FROZEN_BIT or STATUS_BRITTLE_BIT or STATUS_EMBRITTLED_BIT or STATUS_PRIMED_BIT,
            state.statusBits(enemy) and (0xF shl STATUS_STACK_BITS),
        )
        state.elapsed += 1f
        assertEquals(normalCryoThreshold, state.breakThreshold(StatusElement.CRYO, enemy))
        assertEquals(
            STATUS_BRITTLE_BIT,
            state.statusBits(enemy) and (0xF shl STATUS_STACK_BITS),
            "expired freeze/embrittlement clear their flags and restore the Cryo threshold",
        )
    }
}
