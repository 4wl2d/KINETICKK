// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.TestMatrixNodes
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.matrix.api.MatrixNodeStatus
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatrixReducerTest {
    private fun model(matter: Long = 0L, rebirthRank: Int = 0, vararg ranks: Pair<MatrixNodeId, Int>) =
        MatrixProfileSnapshot(
            PlayerEconomy(matter, matter),
            MatrixProgress(MatrixNodeId.entries.map { id -> ranks.firstOrNull { it.first == id }?.second ?: 0 }),
            rebirthRank,
        ).toRenderModel(TestMatrixNodes)

    @Test
    fun snapshotMapsEveryOrderedNodeWithItsStatusAndExactNextCost() {
        val model = model(
            matter = 500L,
            rebirthRank = 0,
            MatrixNodeId.IMPACT_DRIVER to 1,
            MatrixNodeId.REINFORCED_HULL to 3,
        )

        assertEquals(MatrixNodeId.entries.toList(), model.nodes.map { it.id })
        assertEquals(2, model.ownedNodeCount)
        assertEquals(32, model.nodeCount)
        val impact = model.node(MatrixNodeId.IMPACT_DRIVER)!!
        assertEquals(1, impact.rank)
        assertEquals(400L, impact.nextCost)
        assertEquals(MatrixNodeStatus.AVAILABLE, impact.status)
        assertEquals(MatrixNodeStatus.MAXED, model.node(MatrixNodeId.REINFORCED_HULL)!!.status)
        assertEquals(0L, model.node(MatrixNodeId.REINFORCED_HULL)!!.nextCost)
        // Affordable price at exactly the balance, one Matter short below it.
        assertEquals(MatrixNodeStatus.AVAILABLE, model.node(MatrixNodeId.WEAPON_CALIBRATION)!!.status)
        assertEquals(MatrixNodeStatus.SHORT_OF_MATTER, model(matter = 199L).node(MatrixNodeId.WEAPON_CALIBRATION)!!.status)
        assertEquals(MatrixNodeStatus.AVAILABLE, model(matter = 200L).node(MatrixNodeId.WEAPON_CALIBRATION)!!.status)
        // Critical Lattice has its prerequisite; Armed Start does not.
        assertEquals(MatrixNodeStatus.SHORT_OF_MATTER, model.node(MatrixNodeId.CRITICAL_LATTICE)!!.status)
        assertEquals(MatrixNodeStatus.PREREQUISITE_MISSING, model.node(MatrixNodeId.ARMED_START)!!.status)
        // The Rebirth gate shows before a missing prerequisite.
        assertEquals(MatrixNodeStatus.REBIRTH_LOCKED, model.node(MatrixNodeId.GLASS_ENGINE)!!.status)
        assertEquals(MatrixNodeStatus.PREREQUISITE_MISSING, model(rebirthRank = 3).node(MatrixNodeId.GLASS_ENGINE)!!.status)
        // Refund is the price of every owned rank: 150 + 120 + 350 + 800.
        assertEquals(1_420L, model.refund)
    }

    @Test
    fun lanesFollowPrerequisitesSoLinesDoNotCross() {
        val model = model()
        MatrixBranch.entries.forEach { branch ->
            val inBranch = model.nodes.filter { it.branch == branch }
            (0..3).forEach { row ->
                assertEquals(listOf(0, 1), inBranch.filter { it.row == row }.map { it.lane }.sorted(), "$branch row $row")
            }
            // A node with a single prerequisite sits in that prerequisite's lane when its row allows it.
            inBranch.filter { it.prerequisites.size == 1 }.forEach { node ->
                val parent = model.node(node.prerequisites.single())!!
                val siblings = inBranch.filter { it.row == node.row && it.prerequisites.size == 1 }
                if (siblings.map { model.node(it.prerequisites.single())!!.lane }.distinct().size == siblings.size) {
                    assertEquals(parent.lane, node.lane, "${node.id} under ${parent.id}")
                }
            }
        }
    }

    @Test
    fun firstPressSelectsAndAPressOnTheSelectedTileBuys() {
        val state = MatrixState(model(matter = 10_000L))
        val selected = MatrixReducer.reduce(state, MatrixAction.Activate(MatrixNodeId.CRYO_LOOP))
        assertEquals(MatrixNodeId.CRYO_LOOP, selected.state.selected)
        assertTrue(selected.effects.isEmpty())

        val bought = MatrixReducer.reduce(selected.state, MatrixAction.Activate(MatrixNodeId.CRYO_LOOP))
        assertEquals(listOf(MatrixUiEffect.Purchase(MatrixNodeId.CRYO_LOOP)), bought.effects)

        // With no selection the first node is the selected one, so activating it buys directly.
        val first = MatrixReducer.reduce(state, MatrixAction.Activate(MatrixNodeId.IMPACT_DRIVER))
        assertEquals(listOf(MatrixUiEffect.Purchase(MatrixNodeId.IMPACT_DRIVER)), first.effects)
    }

    @Test
    fun theProfilesAnswerReplacesTheModelAndOnlyAnAcceptedPurchaseFlashes() {
        val state = MatrixState(model(matter = 1_000L), selected = MatrixNodeId.IMPACT_DRIVER)
        val after = model(matter = 850L, rebirthRank = 0, MatrixNodeId.IMPACT_DRIVER to 1)
        val accepted = MatrixReducer.purchased(state, MatrixNodeId.IMPACT_DRIVER, after, accepted = true)
        assertEquals(after, accepted.model)
        assertEquals(MatrixPurchaseFlash(MatrixNodeId.IMPACT_DRIVER, 1), accepted.flash)
        val again = MatrixReducer.purchased(accepted, MatrixNodeId.IMPACT_DRIVER, after, accepted = true)
        assertEquals(2, again.flash?.sequence)
        val refused = MatrixReducer.purchased(state, MatrixNodeId.IMPACT_DRIVER, state.model, accepted = false)
        assertNull(refused.flash)
    }

    @Test
    fun resetNeedsTwoPressesAndAnyOtherActionDisarmsIt() {
        val owned = MatrixState(model(matter = 0L, rebirthRank = 0, MatrixNodeId.IMPACT_DRIVER to 2))
        val armed = MatrixReducer.reduce(owned, MatrixAction.ResetRequested)
        assertTrue(armed.state.resetArmed)
        assertEquals(listOf(MatrixUiEffect.PlayAudio(ProfileAudioCue.UI_CLICK)), armed.effects)

        val confirmed = MatrixReducer.reduce(armed.state, MatrixAction.ResetRequested)
        assertFalse(confirmed.state.resetArmed)
        assertEquals(listOf(MatrixUiEffect.Reset), confirmed.effects)

        val disarmed = MatrixReducer.reduce(armed.state, MatrixAction.Select(MatrixNodeId.DATA_ARCHIVE))
        assertFalse(disarmed.state.resetArmed)
        assertTrue(MatrixReducer.reduce(disarmed.state, MatrixAction.ResetRequested).state.resetArmed)

        // Nothing owned: Reset never arms and never refunds.
        val empty = MatrixReducer.reduce(MatrixState(model()), MatrixAction.ResetRequested)
        assertFalse(empty.state.resetArmed)
        assertTrue(empty.effects.isEmpty())
        assertFalse(MatrixReducer.reset(armed.state, model()).resetArmed)
    }

    @Test
    fun backPlaysTheClickAndLeaves() {
        val back = MatrixReducer.reduce(MatrixState(model()), MatrixAction.Back)
        assertEquals(
            listOf(MatrixUiEffect.PlayAudio(ProfileAudioCue.UI_CLICK), MatrixUiEffect.Emit(MatrixOutput.Back)),
            back.effects,
        )
    }

    @Test
    fun tileAndDetailTextsNameTheLockReason() {
        val model = model(matter = 100L, rebirthRank = 0)
        val english = kinetickk.foundation.common.localization.AppLanguage.English
        assertEquals("Reborn 3", matrixTileStatus(model.node(MatrixNodeId.GLASS_ENGINE)!!, english))
        assertEquals("Locked", matrixTileStatus(model.node(MatrixNodeId.ARMED_START)!!, english))
        assertEquals("150 Matter", matrixTileStatus(model.node(MatrixNodeId.IMPACT_DRIVER)!!, english))
        assertEquals("Requires Critical Lattice or Armed Start.",
            matrixStatusDetail(model.node(MatrixNodeId.CATALYST_CORE)!!, model, english))
        assertEquals("Reach Rebirth rank 3 to open this node.", matrixStatusDetail(model.node(MatrixNodeId.GLASS_ENGINE)!!, model, english))
        assertEquals("Need 100 more matter", matrixStatusDetail(model.node(MatrixNodeId.WEAPON_CALIBRATION)!!, model, english))
    }
}
