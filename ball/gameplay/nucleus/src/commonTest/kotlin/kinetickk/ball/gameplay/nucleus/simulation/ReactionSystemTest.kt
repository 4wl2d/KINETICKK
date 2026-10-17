// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ReactionSystemTest {
    @Test
    fun everyReactionConsumesItsStacksIsDiscoveredOnceAndLeavesItsResidue() {
        ReactionId.entries.forEach { reaction ->
            val state = buildTestState()
            val target = state.dummy(x = 400f)
            val neighbor = state.dummy(x = 400f, y = 0f).also { placeNeighbor(state, reaction, target, it) }
            val older = reaction.first
            val incoming = reaction.second

            state.applyStatus(target, older, 3, generation = 0)
            state.applyStatus(target, incoming, 1, generation = 0)

            val bit = 1 shl reaction.ordinal
            assertTrue(state.discoveredReactionMask and bit != 0, "$reaction was not discovered")
            assertTrue(state.pendingDiscoveredReactionMask and bit != 0, "$reaction discovery was not published")
            assertEquals(0, target.stacksOf(incoming), "$reaction consumes the incoming stack")
            val cues = state.takeVisualFxCues()
            val triggered = cues.filterIsInstance<VisualFxCue.ReactionTriggered>().single()
            assertEquals(reaction.ordinal, triggered.reaction)
            assertTrue(triggered.firstDiscovery)
            val name = state.content.build.reaction(reaction).name
            assertTrue(cues.any { it is VisualFxCue.BuildChanged && it.title == "REACTION DISCOVERED" && it.details.toList() == listOf("+ $name") })
            assertResidue(state, reaction, target, neighbor)

            // The same pair on another enemy is no longer a first discovery.
            val second = state.dummy(x = -900f)
            state.applyStatus(second, older, 3, generation = 0)
            state.applyStatus(second, incoming, 1, generation = 0)
            val again = state.takeVisualFxCues()
            assertTrue(again.filterIsInstance<VisualFxCue.ReactionTriggered>().single().firstDiscovery.not())
            assertTrue(again.none { it is VisualFxCue.BuildChanged && it.title == "REACTION DISCOVERED" })
        }
    }

    @Test
    fun reactionsNeedTwoStacksOfAnotherElementAndPairWithTheElementHoldingMost() {
        val state = buildTestState()
        val target = state.dummy()
        state.applyStatus(target, StatusElement.THERMAL, 1, generation = 0)
        state.applyStatus(target, StatusElement.CRYO, 1, generation = 0)
        assertEquals(0, state.discoveredReactionMask, "One stack is not enough to react")

        state.applyStatus(target, StatusElement.ION, 3, generation = 0)
        state.applyStatus(target, StatusElement.GRAVITIC, 1, generation = 0)
        // Ion holds the most stacks, so Gravitic pairs with it: Magnetize.
        assertEquals(1 shl ReactionId.MAGNETIZE.ordinal, state.discoveredReactionMask)
        assertEquals(0, target.stacksOf(StatusElement.ION))
        assertEquals(1, target.stacksOf(StatusElement.THERMAL))
        assertEquals(1, target.stacksOf(StatusElement.CRYO))
    }

    @Test
    fun reactionCooldownsHoldEachEnemyForHalfASecondAndEachPairForTwo() {
        val state = buildTestState()
        val target = state.dummy()
        state.elapsed = 10f
        fun shock(): Boolean {
            val before = target.status[StatusCell.REACTION_READY_AT]
            state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
            state.applyStatus(target, StatusElement.CRYO, 1, generation = 0)
            val fired = target.status[StatusCell.REACTION_READY_AT] != before
            target.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 0f
            target.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 0f
            return fired
        }
        fun plasma(): Boolean {
            val before = target.status[StatusCell.REACTION_READY_AT]
            state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
            state.applyStatus(target, StatusElement.ION, 1, generation = 0)
            val fired = target.status[StatusCell.REACTION_READY_AT] != before
            for (element in StatusElement.entries) target.status[StatusCell.STACKS + element.ordinal] = 0f
            return fired
        }

        assertTrue(shock(), "Thermal Shock fires")
        state.elapsed = 10.3f
        assertTrue(!plasma(), "The enemy is held for half a second")
        state.elapsed = 10.6f
        assertTrue(!shock(), "The same pair waits two seconds")
        assertTrue(plasma(), "Another pair may fire after half a second")
        state.elapsed = 11.2f
        assertTrue(!shock(), "Alternating pairs cannot bypass the pair cooldown")
        state.elapsed = 12.05f
        assertTrue(shock())
    }

    @Test
    fun reactionBudgetAdmitsDirectPairsAndDropsCascadingOnesWhenEmpty() {
        val state = buildTestState()
        val cascading = state.dummy(x = 0f)
        val direct = state.dummy(x = 900f)
        state.reactionTokens = 0.5f

        state.applyStatus(cascading, StatusElement.THERMAL, 3, generation = 1)
        state.applyStatus(cascading, StatusElement.CRYO, 1, generation = 0)
        assertEquals(3, cascading.stacksOf(StatusElement.THERMAL), "A generation-one pair is dropped without tokens")
        assertEquals(0, state.discoveredReactionMask)

        state.applyStatus(direct, StatusElement.THERMAL, 3, generation = 0)
        state.applyStatus(direct, StatusElement.CRYO, 1, generation = 0)
        assertEquals(1 shl ReactionId.THERMAL_SHOCK.ordinal, state.discoveredReactionMask)
        assertEquals(0f, state.reactionTokens, 0.0001f, "Direct spending never drives the budget into debt")
        state.updateStatuses(1f)
        assertEquals(MutableGameState.REACTION_BURST, state.reactionTokens)
    }

    @Test
    fun reactionItemsAmplifyLeaveResidueAndRecoilNearTheCore() {
        fun shockDamage(state: MutableGameState, target: Enemy): Float {
            val before = target.hp
            state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
            state.applyStatus(target, StatusElement.CRYO, 1, generation = 0)
            return before - target.hp
        }
        val plain = buildTestState()
        val base = shockDamage(plain, plain.dummy())
        val power = plain.content.build.reaction(ReactionId.THERMAL_SHOCK).power
        assertEquals(power * 4f * plain.runScale(), base, 0.01f)

        val catalyst = buildTestState().apply { own("Catalyst Matrix") }
        assertEquals(base * 1.35f, shockDamage(catalyst, catalyst.dummy()), 0.05f)

        val fork = buildTestState(rebirth = 4).apply { own("Resonance Fork") }
        val forked = fork.dummy()
        shockDamage(fork, forked)
        assertEquals(3, forked.stacksOf(StatusElement.CRYO), "Resonance Fork leaves the incoming element behind")
        assertEquals(1, forked.generationOf(StatusElement.CRYO))

        val volatile = buildTestState().apply { own("Volatile Compound") }
        val far = volatile.dummy(x = 600f)
        shockDamage(volatile, far)
        assertEquals(volatile.maxHp, volatile.hp, "Distant reactions are safe")
        volatile.hurtCooldown = 0f
        shockDamage(volatile, volatile.dummy(x = 100f))
        assertTrue(volatile.hp < volatile.maxHp, "A reaction next to the Core recoils into it")
    }

    @Test
    fun reactionRecoilKeepsLinearRunGrowthInsteadOfTheEnemyCurve() {
        val state = buildTestState().apply { own("Volatile Compound") }
        state.elapsed = state.content.tempo.bossAtSeconds
        state.shield = 0f
        state.maxHp = 1_000f
        state.hp = 1_000f
        val target = state.dummy(x = state.coreX + 100f, y = state.coreY)
        state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
        state.applyStatus(target, StatusElement.CRYO, 1, generation = 0)
        // At the boss clock Volatile Compound's 5 recoil grows by 1 + 1.6, not by the enemy curve's 5.
        val recoil = 5f * 2.6f * state.rebirthProfile.playerPowerMultiplier * state.rebirthProfile.incomingDamageMultiplier
        assertEquals(recoil, 1_000f - state.hp, 0.01f)
    }

    private fun placeNeighbor(state: MutableGameState, reaction: ReactionId, target: Enemy, neighbor: Enemy) {
        val distance = when (reaction) {
            ReactionId.THERMAL_SHOCK, ReactionId.EMBRITTLE, ReactionId.BLACKFIRE, ReactionId.OBLIVION -> 2_000f
            ReactionId.PLASMA_BURST, ReactionId.FRACTURE -> 60f
            ReactionId.FUSION, ReactionId.ROT_WELL, ReactionId.NULL_WELL -> 80f
            ReactionId.WILDFIRE, ReactionId.THUNDERCLAP, ReactionId.SEISMIC, ReactionId.SILENCE, ReactionId.STASIS -> 100f
            ReactionId.SUPERCONDUCT, ReactionId.FEEDBACK, ReactionId.RESONANT_ARC, ReactionId.ANNIHILATE -> 200f
            ReactionId.GLACIER_CRUSH, ReactionId.SHATTERPOINT -> 100f
            ReactionId.MAGNETIZE -> 150f
        }
        // Glacier Crush and Shatterpoint fire their first shard along a direction derived from the target id.
        val angle = when (reaction) {
            ReactionId.GLACIER_CRUSH -> target.id * 0.37f
            ReactionId.SHATTERPOINT -> target.id * 0.53f + 0.3f
            else -> 0f
        }
        neighbor.x = target.x + cos(angle) * distance
        neighbor.y = target.y + sin(angle) * distance
        state.enemies.forEach { it.previousX = it.x; it.previousY = it.y }
    }

    private fun assertResidue(state: MutableGameState, reaction: ReactionId, target: Enemy, neighbor: Enemy) {
        when (reaction) {
            ReactionId.THERMAL_SHOCK -> {
                assertEquals(1f, target.status[StatusCell.BRITTLE])
                assertTrue(target.hp < target.maxHp)
                assertEquals(0, target.stacksOf(StatusElement.THERMAL))
            }
            ReactionId.EMBRITTLE -> {
                assertEquals(state.elapsed + 4f, target.status[StatusCell.EMBRITTLED_UNTIL], 0.0001f)
                assertEquals(4, state.breakThreshold(StatusElement.CRYO, target))
                assertEquals(0, target.stacksOf(StatusElement.CRYO))
            }
            ReactionId.THUNDERCLAP -> {
                assertTrue(neighbor.hp < neighbor.maxHp, "Thunderclap reaches its neighbor")
                assertTrue(neighbor.vx > 0f, "Thunderclap hurls the neighbor away from the target")
            }
            ReactionId.RESONANT_ARC, ReactionId.ANNIHILATE -> {
                assertTrue(target.hp < target.maxHp, "$reaction strikes its target")
                assertTrue(neighbor.hp < neighbor.maxHp, "$reaction reaches its neighbor")
            }
            ReactionId.SEISMIC -> {
                assertTrue(neighbor.hp < neighbor.maxHp)
                assertEquals(state.elapsed + SEISMIC_STUN_SECONDS, neighbor.disabledUntil, 0.0001f)
                assertTrue(!state.canAct(neighbor), "Seismic stuns an ordinary neighbor")
            }
            ReactionId.SILENCE -> {
                assertTrue(neighbor.hp < neighbor.maxHp)
                assertEquals(state.elapsed + SILENCE_SECONDS, neighbor.silencedUntil, 0.0001f)
                assertTrue(state.canAct(neighbor) && !state.canAttack(neighbor), "Silence leaves movement but not attacks")
            }
            ReactionId.STASIS -> {
                assertTrue(neighbor.hp < neighbor.maxHp)
                assertEquals(state.elapsed + STASIS_STOP_SECONDS, neighbor.disabledUntil, 0.0001f)
                assertEquals(0f, neighbor.vx)
                assertEquals(0f, neighbor.vy)
            }
            ReactionId.BLACKFIRE -> {
                assertEquals(state.elapsed + BLACKFIRE_SECONDS, target.status[StatusCell.BLACKFIRE_UNTIL], 0.0001f)
                assertTrue(target.status[StatusCell.BLACKFIRE_DPS] > 0f)
                assertEquals(neighbor.maxHp, neighbor.hp, "Blackfire burns one target")
            }
            ReactionId.OBLIVION -> {
                assertTrue(target.hp < target.maxHp, "A healthy target takes the burst")
                assertTrue(target.alive)
            }
            ReactionId.FUSION, ReactionId.ROT_WELL, ReactionId.NULL_WELL -> {
                val field = state.combatFields.single()
                if (reaction == ReactionId.NULL_WELL) {
                    assertEquals(CombatFieldKind.NULL_WELL, field.kind)
                    state.updateCombatFields(0.4f)
                    assertTrue(neighbor.hp < neighbor.maxHp, "Null Well strips its occupants")
                    assertTrue(neighbor.vx < 0f, "Null Well pulls toward its center")
                    return
                }
                assertEquals(if (reaction == ReactionId.FUSION) CombatFieldKind.FUSION else CombatFieldKind.ROT_WELL, field.kind)
                state.updateCombatFields(0.4f)
                val residue = state.content.build.reaction(reaction).residue ?: fail("$reaction has a residue")
                assertEquals(1, neighbor.stacksOf(residue), "$reaction field seeds its occupants")
                assertEquals(1, neighbor.generationOf(residue))
                assertTrue(neighbor.hp < neighbor.maxHp, "$reaction field damages its occupants")
            }
            else -> {
                val definition = state.content.build.reaction(reaction)
                val residue = definition.residue ?: fail("$reaction has a residue")
                val expected = if (reaction == ReactionId.WILDFIRE) 3 / 2 + 1 else definition.residueStacks
                assertEquals(expected, neighbor.stacksOf(residue), "$reaction residue on its neighbor")
                assertEquals(1, neighbor.generationOf(residue), "$reaction residue is generation one")
                if (reaction != ReactionId.WILDFIRE) assertTrue(neighbor.hp < neighbor.maxHp, "$reaction reaches its neighbor")
                if (reaction == ReactionId.MAGNETIZE) assertTrue(neighbor.vx < 0f, "Magnetize pulls toward the target")
                if (reaction == ReactionId.FEEDBACK) assertTrue(target.hp < target.maxHp * (1f - definition.power) + 1f)
            }
        }
    }
}
