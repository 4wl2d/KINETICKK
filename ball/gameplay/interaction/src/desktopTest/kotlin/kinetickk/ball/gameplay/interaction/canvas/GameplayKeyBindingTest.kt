// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gameplay key handler maps keys through the player's bindings and consumes a bound key only in the
 * phases where its action applies (meta-ui §4.2): a key it does not consume bubbles to the session's
 * shortcuts (C opens the Codex while paused). Escape and Enter stay fixed aliases; the performance key
 * works in every phase.
 */
@OptIn(ExperimentalTestApi::class)
class GameplayKeyBindingTest {
    @get:Rule
    val compose = createComposeRule()

    private val pulses = mutableListOf<GameplayInteractionPulse>()
    private val bubbled = mutableListOf<Key>()

    /** Z and X hold skills, C is not drafted yet; the Apex is unlocked and cooling. */
    private fun runningModel(bindings: KeyBindings = KeyBindings.DEFAULT): GameplayRenderModel {
        val base = requireNotNull(hudTestSnapshot().renderModel)
        return base.with(*levelOverrides(HUD_TEST_LEVEL_CAP, ApexProjection(unlocked = true, cooldown01 = 0.5f)),
            "skillBar" to MIXED_SKILL_BAR, "settings" to base.settings.copy(keyBindings = bindings))
    }

    private fun start(model: GameplayRenderModel) {
        val snapshot = hudTestSnapshot().withRenderModel(model)
        val port = object : GameplayInteractionPort {
            override val instanceId get() = snapshot.instanceId
            override fun renderSnapshot() = snapshot
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                pulses += pulse
                return GameplayAcceptance.Rejected(snapshot.instanceId, snapshot.revision, GameplayRejection.RunExited)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                // The session shell above the gameplay host: it sees every key the host leaves unconsumed.
                Box(
                    Modifier.requiredSize(1_440.dp, 810.dp).onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) bubbled += event.key
                        false
                    },
                ) {
                    GameplayContent(port, true) {}
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Presses [key] and returns the gameplay pulses it dispatched (frames, gestures and the viewport aside). */
    private fun press(key: Key): List<GameplayInteractionPulse> {
        pulses.clear()
        bubbled.clear()
        compose.onNodeWithTag(GAMEPLAY_ROOT_TAG).performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeByFrame()
        return pulses.filter {
            it !is GameplayInteractionPulse.FrameElapsed && it !is GameplayInteractionPulse.UserGestureObserved &&
                it !is GameplayInteractionPulse.ViewportChanged
        }
    }

    @Test
    fun whileRunningBoundKeysDriveTheRunAndOnlyUnboundOrOutOfPhaseKeysBubble() {
        start(runningModel())
        assertEquals(listOf(GameplayInteractionPulse.SkillRequested.fromValidated(0)), press(Key.Z))
        assertTrue(bubbled.isEmpty(), "Z bubbled while running")
        assertEquals(listOf(GameplayInteractionPulse.SkillRequested.fromValidated(1)), press(Key.X))
        // C's slot is not drafted: the key is consumed (no Codex mid-run) and nothing is cast.
        assertEquals(emptyList(), press(Key.C))
        assertTrue(bubbled.isEmpty(), "C bubbled while running")
        assertEquals(listOf(GameplayInteractionPulse.ApexRequested), press(Key.V))
        assertEquals(listOf(GameplayInteractionPulse.DashRequested), press(Key.Spacebar))
        assertEquals(
            listOf(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, true), GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false)),
            press(Key.ShiftLeft),
        )
        assertEquals(listOf(GameplayInteractionPulse.PauseToggled), press(Key.P))
        assertEquals(listOf(GameplayInteractionPulse.PauseToggled), press(Key.Escape))
        assertTrue(bubbled.isEmpty(), "Escape bubbled")
        // Choice, reroll and restart keys belong to other phases: they reach the session untouched.
        listOf(Key.One, Key.Q, Key.R, Key.W).forEach { key ->
            assertEquals(emptyList(), press(key), "$key while running")
            assertEquals(listOf(key), bubbled, "$key did not bubble")
        }
        // The performance key works in every phase and never reaches the session.
        assertEquals(emptyList(), press(Key.F3))
        assertTrue(bubbled.isEmpty(), "F3 bubbled")
    }

    @Test
    fun whilePausedOnlyPauseAndBrakeReleaseAreConsumedSoCOpensTheCodex() {
        start(runningModel().with("phase" to GamePhase.PAUSED))
        listOf(Key.Z, Key.X, Key.C, Key.V, Key.Spacebar, Key.One).forEach { key ->
            assertEquals(emptyList(), press(key), "$key while paused")
            assertEquals(listOf(key), bubbled, "$key did not bubble while paused")
        }
        assertEquals(listOf(GameplayInteractionPulse.PauseToggled), press(Key.P))
        assertTrue(bubbled.isEmpty())
        // A held Brake key may lift after the pause: the release still reaches the run, the press does not.
        assertEquals(listOf(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false)), press(Key.ShiftLeft))
        assertEquals(listOf(Key.ShiftLeft), bubbled)
        assertEquals(emptyList(), press(Key.F3))
        assertTrue(bubbled.isEmpty(), "F3 bubbled while paused")
    }

    @Test
    fun reboundKeysFollowThePlayersBindings() {
        // Dash on F (Space becomes free) and Skill 1 on C (Z takes Skill 3, still undrafted).
        val bindings = KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.F).with(GameAction.SKILL_1, BindableKey.C)
        start(runningModel(bindings))
        assertEquals(listOf(GameplayInteractionPulse.DashRequested), press(Key.F))
        assertEquals(emptyList(), press(Key.Spacebar))
        assertEquals(listOf(Key.Spacebar), bubbled, "the freed Space did not bubble")
        assertEquals(listOf(GameplayInteractionPulse.SkillRequested.fromValidated(0)), press(Key.C))
        assertEquals(emptyList(), press(Key.Z), "Z now holds the undrafted Skill 3")
        assertTrue(bubbled.isEmpty())
    }
}

private const val GAMEPLAY_ROOT_TAG = "kinetickk.gameplay"

private fun GameplayRenderSnapshot.withRenderModel(model: GameplayRenderModel): GameplayRenderSnapshot {
    val constructor = GameplayRenderSnapshot::class.java.declaredConstructors.first { it.parameterCount == 4 }
    constructor.isAccessible = true
    val identity = GameplayRenderSnapshot::class.java.declaredFields.first { it.name == "projectionSourceIdentity" }
        .also { it.isAccessible = true }.get(this)
    return constructor.newInstance(instanceId, revision, model, identity) as GameplayRenderSnapshot
}
