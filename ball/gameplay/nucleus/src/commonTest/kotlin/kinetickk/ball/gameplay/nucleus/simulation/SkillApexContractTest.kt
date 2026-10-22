// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutputs
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.CoreStateProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkillApexContractTest {
    private val requests = listOf(
        GameplayInteractionPulse.SkillRequested.fromValidated(0),
        GameplayInteractionPulse.SkillRequested.fromValidated(1),
        GameplayInteractionPulse.SkillRequested.fromValidated(2),
        GameplayInteractionPulse.ApexRequested,
    )

    @Test
    fun castingRequestsAreAcceptedWithoutChangingTheRunWhileNoSkillRuntimeExists() {
        val running = EngineState(buildTestState())
        assertEquals(GamePhase.RUNNING, running.model.phase)
        val paused = EngineState(buildTestState().apply { togglePause() }.also { it.takeSoundCues(); it.takeVisualFxCues() })
        val reducer = GameReducer()

        listOf(running, paused).forEach { state ->
            requests.forEach { pulse ->
                val accepted = assertIs<GameReductionResult.Accepted>(reducer.reduce(state, pulse))
                assertSame(state, accepted.state, "$pulse")
                assertSame(SimulationOutputs.Empty, accepted.outputs, "$pulse")
            }
        }
    }

    @Test
    fun theRenderModelPublishesNeutralSkillApexFieldAndCoreProjections() {
        val state = buildTestState().apply { addEnemyForTesting(x = coreX + 200f, y = coreY) }
        val model = state.toRenderModel()

        assertEquals(state.content.tempo.maximumLevel, model.maxLevel)
        assertSame(SkillBarProjection.EMPTY, model.skillBar)
        assertTrue(model.skillBar.slots.all { slot -> slot.isEmpty && slot.skillId == null && slot.refusedAt == -1f })
        assertSame(ApexProjection.LOCKED, model.apex)
        assertTrue(!model.apex.unlocked && !model.apex.ready)
        assertTrue(model.combatFields.isEmpty())
        assertSame(CoreStateProjection.NEUTRAL, model.coreState)
        assertEquals(listOf(0), model.enemies.map { it.controlBits })

        val reused = state.toRenderModel(reusableCollections = model, identitySource = state)
        assertSame(model.skillBar, reused.skillBar)
        assertSame(model.combatFields, reused.combatFields)
    }

    @Test
    fun projectionShapesAndBitLayoutsAreClosed() {
        assertEquals(3, SkillBarProjection.EMPTY.slots.size)
        assertFailsWith<IllegalArgumentException> {
            SkillBarProjection(immutableListOf(SkillSlotProjection.EMPTY, SkillSlotProjection.EMPTY))
        }
        assertEquals(SkillId.METEOR, SkillSlotProjection(skill = SkillId.METEOR.ordinal, rank = 1).skillId)
        assertNull(SkillSlotProjection(skill = SkillId.entries.size).skillId)

        assertEquals(-1, EnemyControlBits.markKind(0))
        assertEquals(3, EnemyControlBits.markKind((3 + 1) shl EnemyControlBits.MARK_SHIFT or EnemyControlBits.THROWN))
        val singles = listOf(
            EnemyControlBits.STOPPED, EnemyControlBits.SILENCED, EnemyControlBits.THROWN, EnemyControlBits.BLACKFIRE,
            EnemyControlBits.UNMAKE_PENDING, EnemyControlBits.VOID_ANCHOR, EnemyControlBits.ATTRACTOR_ANCHOR,
            EnemyControlBits.APEX_STRIKE_TARGET,
        )
        assertEquals(0, singles.fold(0) { mask, bit -> mask or bit } and EnemyControlBits.MARK_MASK)
        assertEquals(singles.size, singles.toSet().size)
    }

    @Test
    fun newCueFieldsDefaultToTheShippedLooksAndLinkStylesNeverOverlap() {
        val reaction = VisualFxCue.ReactionTriggered(1f, 2f, ReactionId.FUSION.ordinal, 80f, firstDiscovery = false)
        assertTrue(reaction.direction.isNaN())
        assertEquals(0, reaction.flags)
        assertEquals(reaction, VisualFxCue.ReactionTriggered(1f, 2f, ReactionId.FUSION.ordinal, 80f, false))
        assertEquals(LinkStyleCode.WEAPON, VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.1f).style)

        val reactionCodes = ReactionId.entries.map(LinkStyleCode::reaction)
        val skillCodes = SkillId.entries.map(LinkStyleCode::skill)
        val breakCodes = StatusElement.entries.map(LinkStyleCode::elementBreak)
        val apexCodes = CoreShape.entries.map(LinkStyleCode::apex)
        val all = listOf(LinkStyleCode.WEAPON) + reactionCodes + skillCodes + breakCodes + apexCodes
        assertEquals(all.size, all.toSet().size)
        assertTrue(reactionCodes.max() < LinkStyleCode.SKILL_BASE)
        assertTrue(skillCodes.max() < LinkStyleCode.BREAK_BASE)
        assertTrue(breakCodes.max() < LinkStyleCode.APEX_BASE)
    }
}
