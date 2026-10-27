// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.impl.GameplayCompositionComponent
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.foundation.common.input.BindableKey
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Key precedence end to end (meta-ui §4.2) through the production shell, Session, Settings, Workshop, Codex and a real
 * run: while a run is RUNNING its bound keys (Z/X/C/V) go to gameplay first, C included; keys whose action does not
 * apply fall back to the shell shortcuts (C opens the Codex while paused); W, L and A open the Workshop over the run
 * (H only over Home); and a key rebound in Settings during the run applies as soon as Settings closes.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeApi::class)
class GameplayKeyPrecedenceComposeTest {
    private val previousLinkBufferFlag = ComposeRuntimeFlags.isLinkBufferComposerEnabled

    @AfterTest
    fun restoreRuntimeFlags() {
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = previousLinkBufferFlag
    }

    @Test
    fun boundKeysDriveTheRunAndFallBackToShellShortcutsAndARebindAppliesAtOnce() {
        enableKinetickkComposeRuntimeOptimizations()
        runComposeUiTest(testTimeout = 10.minutes) {
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val profile = createProfileComponent(MemoryPersistence(), catalog.profilePolicy())
            val audio = QuietAudio()
            val gameplay = RecordingGameplay(DefaultGameplayFeature(activesEarly(catalog.gameplayContent()), profile, profile, audio))
            val owner = AppCompositionOwner(
                contentCatalog = catalog,
                profileComponent = profile,
                audioService = audio,
                gameplayComponent = gameplay,
            )
            try {
                setContent { Box(Modifier.requiredSize(1_000.dp, 700.dp).testTag(APP_TAG)) { owner.Content() } }
                fun settle() {
                    mainClock.advanceTimeBy(260)
                    waitForIdle()
                }
                fun shell() = owner.sessionPort.query(AppSessionQuery.GetShell)
                fun phase() = gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase
                fun press(key: Key): List<GameplayInteractionPulse> {
                    gameplay.pulses.clear()
                    onRoot().performKeyInput { pressKey(key) }
                    settle()
                    return gameplay.pulses.filter { it.isCommand() }
                }
                settle()
                assertEquals(AppDestination.Home, shell().active)
                // Over Home, H opens the Workshop on the characters.
                press(Key.H)
                assertEquals(AppDestination.Workshop, shell().overlay)
                onNodeWithTag("kinetickk.workshop.tab.characters").assertIsSelected()
                press(Key.Escape)
                assertEquals(AppDestination.Home, shell().active)

                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                press(Key.Enter)
                assertEquals(AppDestination.Gameplay, shell().active)
                assertEquals(GameplayRunPhase.RUNNING, phase())
                // Play the real run to its first active item (Z holds its effect, C's slot is still empty).
                val run = gameplay.activeRun() as GameplayInteractionPort
                RunPilot(run).playUntilFirstSkill()
                settle()
                val model = requireNotNull(run.renderSnapshot().renderModel)
                assertTrue(model.skillBar.slots[0].skill >= 0, "Z holds a skill")
                assertTrue(model.skillBar.slots[2].skill < 0, "C's slot is empty")

                // RUNNING: Z casts; C (an empty slot) and V (the locked Apex) are consumed by the run: no Codex, no overlay.
                assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.SkillRequested.fromValidated(0)), press(Key.Z))
                assertEquals(emptyList(), press(Key.C))
                assertNull(shell().overlay, "C opened ${shell().overlay} during a run")
                assertEquals(emptyList(), press(Key.V))
                assertNull(shell().overlay)
                assertEquals(GameplayRunPhase.RUNNING, phase())
                // H is a Workshop alias over Home only.
                press(Key.H)
                assertNull(shell().overlay)

                // Unbound shell keys still work in a run: L opens the Workshop on the Matrix (the run pauses), A switches tabs.
                press(Key.L)
                assertEquals(AppDestination.Workshop, shell().overlay)
                onNodeWithTag("kinetickk.workshop.tab.matrix").assertIsSelected()
                assertEquals(GameplayRunPhase.PAUSED, phase())
                press(Key.A)
                onNodeWithTag("kinetickk.workshop.tab.weapons").assertIsSelected()
                press(Key.W)
                assertNull(shell().overlay, "W closes the Workshop")
                assertEquals(GameplayRunPhase.PAUSED, phase())

                // PAUSED: the skill keys do not apply, so C falls back to the Codex shortcut.
                assertEquals(emptyList(), press(Key.C))
                assertEquals(AppDestination.Codex, shell().overlay)
                onNodeWithTag("codex-close").performClick()
                settle()
                assertNull(shell().overlay)
                // The pause menu's Workshop entry opens the Workshop over the paused run.
                onNodeWithTag("kinetickk.gameplay.workshop").performClick()
                settle()
                assertEquals(AppDestination.Workshop, shell().overlay)
                press(Key.Escape)
                assertNull(shell().overlay)

                // Rebind Skill 1 to C in Settings mid-run (Z takes C's Skill 3): it applies when Settings closes.
                press(Key.S)
                assertEquals(AppDestination.Settings, shell().overlay)
                onNodeWithTag("kinetickk.settings.group.controls").performClick()
                settle()
                val cell = "kinetickk.settings.bind.skill_1.key"
                if (onAllNodesWithTag(cell).fetchSemanticsNodes().isEmpty()) {
                    var page = 0
                    while (onAllNodesWithTag(cell).fetchSemanticsNodes().isEmpty()) {
                        onNodeWithTag("kinetickk.settings.page.${page++}").performClick()
                        settle()
                    }
                }
                onNodeWithTag(cell).performClick()
                settle()
                press(Key.C)
                val bindings = profile.query(ProfileQuery.GetPreferences).preferences.keyBindings
                assertEquals(BindableKey.C, bindings.key(GameAction.SKILL_1))
                assertEquals(BindableKey.Z, bindings.key(GameAction.SKILL_3))
                onNodeWithTag("kinetickk.settings.back").performClick()
                settle()
                assertNull(shell().overlay)
                assertEquals(BindableKey.C, requireNotNull(run.renderSnapshot().renderModel).settings.keyBindings.key(GameAction.SKILL_1),
                    "the paused run received the new bindings")
                press(Key.P)
                assertEquals(GameplayRunPhase.RUNNING, phase())
                // Without a restart: C now casts Skill 1; Z holds the still-closed Skill 3 and stays in the run.
                assertEquals(listOf<GameplayInteractionPulse>(GameplayInteractionPulse.SkillRequested.fromValidated(0)), press(Key.C))
                assertNull(shell().overlay)
                assertEquals(emptyList(), press(Key.Z))
                assertNull(shell().overlay)
            } finally {
                owner.close()
            }
        }
    }
}

private const val APP_TAG = "key-precedence-app"

/** Pulses that ask the run for something (frames, pointer motion, gestures and viewports aside). */
private fun GameplayInteractionPulse.isCommand(): Boolean =
    this !is GameplayInteractionPulse.FrameElapsed && this !is GameplayInteractionPulse.PointerMoved &&
        this !is GameplayInteractionPulse.UserGestureObserved && this !is GameplayInteractionPulse.ViewportChanged

/** The production gameplay host, with the pulses its presentation dispatches recorded on the way to the real run. */
private class RecordingGameplay(private val real: DefaultGameplayFeature) : GameplayCompositionComponent by real {
    val pulses = ArrayList<GameplayInteractionPulse>()

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) {
        val run = real.activeRun() as? GameplayInteractionPort
        if (run == null) {
            real.Content(inputEnabled, onOutput)
            return
        }
        val recording = remember(run) {
            object : GameplayInteractionPort {
                override val instanceId get() = run.instanceId
                override fun renderSnapshot() = run.renderSnapshot()
                override fun visualFxSnapshot() = run.visualFxSnapshot()
                override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
                    pulses += pulse
                    return run.accept(pulse)
                }
            }
        }
        GameplayContent(recording, inputEnabled, onOutput)
    }
}

/**
 * [content] whose offers bring active items from the first offer: every active unlocked at level 1, the whole catalog
 * dealt and a high active weight, so the pilot meets one within a few offers whatever the seed.
 */
private fun activesEarly(content: GameplayContentSnapshot): GameplayContentSnapshot {
    val policy = content.build.policy
    return content.copy(
        items = content.items.map { if (it.isActive) it.copy(unlockLevel = 1) else it }.toImmutableList(),
        build = content.build.copy(
            policy = policy.copy(draftDeck = DraftDeckPolicy.FULL, offerLanes = policy.offerLanes.copy(activeWeight = 1_000f)),
        ),
    )
}

/**
 * Plays the real run through its port between UI frames: the singularity sweeps around the Core toward the
 * nearest enemy (never across it) and offers take an active item card, until Z holds an effect and the run is running.
 */
private class RunPilot(private val run: GameplayInteractionPort) {
    private var cursorAngle = 0f

    private fun model(): GameplayRenderModel = requireNotNull(run.renderSnapshot().renderModel)

    fun playUntilFirstSkill() {
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val model = model()
            when (model.phase) {
                GamePhase.CHOICE -> {
                    val choices = model.choices
                    val index = (0 until minOf(choices.size, 4)).firstOrNull {
                        choices[it].type == ChoiceType.ITEM && choices[it].skillId != null && !choices[it].replacesActive
                    } ?: (0 until minOf(choices.size, 4)).firstOrNull { choices[it].rewardFocus == null } ?: 0
                    run.accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(index))
                }
                GamePhase.RUNNING -> {
                    if (model.skillBar.slots[0].skill >= 0) return
                    steer(model)
                    run.accept(GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f))
                }
                else -> error("the run ended (${model.phase}) before its first active item")
            }
        }
        error("no active item taken in $MAX_STEPS steps")
    }

    private fun steer(model: GameplayRenderModel) {
        var desired = model.elapsed * 0.7f
        var nearest = Float.MAX_VALUE
        for (enemy in model.enemies) {
            if (enemy.dead) continue
            val dx = enemy.x - model.coreX
            val dy = enemy.y - model.coreY
            if (dx * dx + dy * dy < nearest) {
                nearest = dx * dx + dy * dy
                desired = atan2(dy, dx)
            }
        }
        val offsetX = model.coreX - model.cameraX
        val offsetY = model.coreY - model.cameraY
        if (kotlin.math.abs(offsetX) > model.screenWidth * 0.2f || kotlin.math.abs(offsetY) > model.screenHeight * 0.2f) {
            desired = atan2(-offsetY, -offsetX)
        }
        val turn = atan2(sin(desired - cursorAngle), cos(desired - cursorAngle))
        cursorAngle += turn.coerceIn(-0.12f, 0.12f)
        run.accept(GameplayInteractionPulse.PointerMoved.fromValidated(
            (model.screenWidth * 0.5f + offsetX + cos(cursorAngle) * 150f).coerceIn(0f, model.screenWidth),
            (model.screenHeight * 0.5f + offsetY + sin(cursorAngle) * 150f).coerceIn(0f, model.screenHeight),
        ))
    }

    private companion object {
        const val MAX_STEPS = 20_000
    }
}

private class MemoryPersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class QuietAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
