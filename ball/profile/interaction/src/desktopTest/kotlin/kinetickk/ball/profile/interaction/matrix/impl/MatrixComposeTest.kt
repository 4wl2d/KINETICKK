// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Matrix screen wired to a Profile port: pointer, keyboard and two-press Reset reach Profile as typed pulses. */
@OptIn(ExperimentalTestApi::class)
class MatrixComposeTest {
    private fun tag(id: MatrixNodeId) = "profile-matrix-node-$id"

    private fun run(
        port: MatrixTestPort,
        width: Float = 1440f,
        height: Float = 810f,
        outputs: MutableList<MatrixOutput> = mutableListOf(),
        test: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(Size(width, height), Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    DefaultMatrixFeature(port, defaultMatrixNodes(), SilentAudio).Content(0L) { outputs += it }
                }
            }
        }
        waitForIdle()
        test()
    }

    @Test
    fun aPressOnTheSelectedTileBuysTheNextRank() {
        val port = MatrixTestPort(matter = 10_000L)
        run(port) {
            // Keyboard focus (like hover) selects without buying.
            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).requestFocus()
            waitForIdle()
            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).assertIsSelected()
            assertTrue(port.pulses.isEmpty(), "selection alone buys nothing")

            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).performClick()
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseMatrixNode(MatrixNodeId.WEAPON_CALIBRATION)), port.pulses)
            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).assert(stateContains("Rank 1/3"))
            assertEquals(10_000L - 200L, port.matter)
        }
    }

    @Test
    fun theBuyButtonFollowsTheSelectionAndAlockedNodeCannotBeBought() {
        val port = MatrixTestPort(matter = 10_000L)
        run(port) {
            onNodeWithTag(tag(MatrixNodeId.GLASS_ENGINE)).requestFocus()
            waitForIdle()
            onNodeWithTag("profile-matrix-purchase").assertIsNotEnabled()
            onNodeWithTag("profile-matrix-purchase").assert(stateContains("Reach Rebirth rank 3"))

            onNodeWithTag(tag(MatrixNodeId.REINFORCED_HULL)).requestFocus()
            waitForIdle()
            onNodeWithTag("profile-matrix-purchase").assertIsEnabled().performClick()
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseMatrixNode(MatrixNodeId.REINFORCED_HULL)), port.pulses)
        }
    }

    @Test
    fun arrowKeysMoveTheSelectionAndEnterBuysTheFocusedNode() {
        val port = MatrixTestPort(matter = 10_000L)
        run(port) {
            onNodeWithTag(tag(MatrixNodeId.IMPACT_DRIVER)).requestFocus()
            waitForIdle()
            onNodeWithTag(tag(MatrixNodeId.IMPACT_DRIVER)).assertIsSelected()
            // Weapon Calibration sits to the right of Impact Driver in the Kinetic column.
            onNodeWithTag(tag(MatrixNodeId.IMPACT_DRIVER)).performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            onNode(isFocused()).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Weapon Calibration")))
            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).assertIsSelected()
            onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            onNodeWithTag(tag(MatrixNodeId.ARMED_START)).assertIsSelected()
            onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
            waitForIdle()
            onNodeWithTag(tag(MatrixNodeId.WEAPON_CALIBRATION)).assertIsSelected()
            onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseMatrixNode(MatrixNodeId.WEAPON_CALIBRATION)), port.pulses)
        }
    }

    @Test
    fun resetNeedsTwoPressesAndIsDisabledWithNothingToRefund() {
        val owned = MatrixTestPort(matter = 0L, ranks = mapOf(MatrixNodeId.IMPACT_DRIVER to 2))
        run(owned) {
            onNodeWithTag("profile-matrix-reset").assert(stateContains("550"))
            onNodeWithTag("profile-matrix-reset").performClick()
            waitForIdle()
            assertTrue(owned.pulses.isEmpty(), "the first press only arms")
            onNodeWithTag("profile-matrix-reset").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Confirm")),
            )
            onNodeWithTag("profile-matrix-reset").performClick()
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.ResetMatrix), owned.pulses)
            assertEquals(550L, owned.matter)
            onNodeWithTag("profile-matrix-reset").assertIsNotEnabled()
        }
        run(MatrixTestPort(matter = 10L)) { onNodeWithTag("profile-matrix-reset").assertIsNotEnabled() }
    }

    @Test
    fun phonesScrollTheLastNodeIntoViewWhenItIsSelected() {
        val port = MatrixTestPort(matter = 10_000L)
        run(port, width = 390f, height = 844f) {
            onNodeWithTag(tag(MatrixNodeId.PROSPECTOR)).requestFocus()
            waitForIdle()
            onNodeWithTag(tag(MatrixNodeId.PROSPECTOR)).assertIsSelected()
            val scroll = onNodeWithTag("profile-matrix-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(scroll.value() > 0f, "the grid scrolled to the last branch")
        }
    }

    private fun stateContains(text: String) = SemanticsMatcher("state contains '$text'") { node ->
        node.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }?.contains(text) == true
    }
}

/** An in-memory Profile port that applies Matrix purchases and resets with the content's prices and gates. */
private class MatrixTestPort(
    var matter: Long,
    ranks: Map<MatrixNodeId, Int> = emptyMap(),
    private val nodes: List<MatrixNodeDefinition> = defaultMatrixNodes(),
) : ProfilePort {
    private var ranks: MutableList<Int> = MatrixNodeId.entries.map { ranks[it] ?: 0 }.toMutableList()
    private var revision = ProfileRevision(1L)
    val pulses = mutableListOf<ProfilePulse.Business>()

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        pulses += pulse
        val reason: ProfileRejection? = when (pulse) {
            is ProfilePulse.PurchaseMatrixNode -> {
                val node = nodes.first { it.id == pulse.id }
                val rank = ranks[pulse.id.ordinal]
                when {
                    rank >= node.maxRanks -> ProfileRejection.MatrixNodeMaxRank
                    node.minimumRebirth > 0 -> ProfileRejection.MatrixRebirthLocked
                    node.prerequisites.isNotEmpty() && node.prerequisites.none { ranks[it.ordinal] > 0 } ->
                        ProfileRejection.MatrixPrerequisiteMissing
                    matter < node.cost(rank) -> ProfileRejection.InsufficientMatter
                    else -> {
                        matter -= node.cost(rank)
                        ranks[pulse.id.ordinal] = rank + 1
                        null
                    }
                }
            }
            ProfilePulse.ResetMatrix -> {
                matter += nodes.sumOf { it.spent(ranks[it.id.ordinal]) }
                ranks = MutableList(MatrixNodeId.entries.size) { 0 }
                null
            }
            else -> error("unexpected $pulse")
        }
        if (reason != null) return ProfileAcceptance.Rejected(instanceId, revision, reason)
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = MatrixProgressProjection(

    override fun query(query: ProfileQuery.GetHangar): kinetickk.ball.profile.api.HangarProjection = error("unused")
        instanceId, revision, MatrixProfileSnapshot(PlayerEconomy(matter, maxOf(matter, 100_000L)), MatrixProgress(ranks.toList()), rebirthRank = 0),
    )

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, PlayerPreferences(textScale = 1.25f))

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

private object SilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
