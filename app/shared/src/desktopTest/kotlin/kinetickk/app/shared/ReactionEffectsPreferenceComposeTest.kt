// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The Reaction effects row end to end through the production app: the Settings choice is the Profile's
 * `reactionEffects` (persisted as `reactionEffectsId`), a new run starts with it, and a change made from a paused
 * run reaches that run when Settings closes. The FX layer's use of the run's value is covered in Interaction
 * (`GameplayRenderFactsEndToEndTest`).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeApi::class)
class ReactionEffectsPreferenceComposeTest {
    private val previousLinkBufferFlag = ComposeRuntimeFlags.isLinkBufferComposerEnabled

    @AfterTest
    fun restoreRuntimeFlags() {
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = previousLinkBufferFlag
    }

    @Test
    fun theSettingsRowReachesTheProfileAndTheRun() {
        enableKinetickkComposeRuntimeOptimizations()
        runComposeUiTest(testTimeout = 10.minutes) {
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val persistence = SavedPayload()
            val profile = createProfileComponent(persistence, catalog.profilePolicy())
            val audio = MutedAudio()
            val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio)
            val owner = AppCompositionOwner(contentCatalog = catalog, profileComponent = profile, audioService = audio,
                gameplayComponent = gameplay)
            try {
                setContent { Box(Modifier.requiredSize(1_000.dp, 700.dp).testTag("reaction-effects-app")) { owner.Content() } }
                fun settle() {
                    mainClock.advanceTimeBy(260)
                    waitForIdle()
                }
                fun shell() = owner.sessionPort.query(AppSessionQuery.GetShell)
                fun key(key: Key) {
                    onRoot().performKeyInput { pressKey(key) }
                    settle()
                }
                fun click(tag: String) {
                    onNodeWithTag(tag).performClick()
                    settle()
                }
                fun runLevel() = requireNotNull((gameplay.activeRun() as GameplayInteractionPort).renderSnapshot().renderModel)
                    .settings.reactionEffects
                fun choose(level: ReactionEffectsLevel) {
                    click("kinetickk.settings.group.graphics")
                    val tag = "kinetickk.settings.reactioneffects.${level.name.lowercase()}"
                    var page = 0
                    while (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) click("kinetickk.settings.page.${page++}")
                    click(tag)
                    onNodeWithTag(tag).assertIsSelected()
                    assertEquals(level, profile.query(ProfileQuery.GetPreferences).preferences.reactionEffects)
                }
                settle()
                key(Key.S)
                assertEquals(AppDestination.Settings, shell().overlay)
                choose(ReactionEffectsLevel.MINIMAL)
                assertTrue(persistence.payload!!.contains("\"reactionEffectsId\":\"MINIMAL\""), persistence.payload)
                click("kinetickk.settings.back")
                assertEquals(AppDestination.Home, shell().active)

                // A new run starts with the stored level.
                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                key(Key.Enter)
                assertEquals(AppDestination.Gameplay, shell().active)
                assertEquals(ReactionEffectsLevel.MINIMAL, runLevel())

                // A change from the paused run reaches it when Settings closes; the default is written as no field.
                key(Key.P)
                assertEquals(GameplayRunPhase.PAUSED, gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase)
                click("kinetickk.gameplay.settings")
                assertEquals(AppDestination.Settings, shell().overlay)
                choose(ReactionEffectsLevel.REDUCED)
                assertEquals(ReactionEffectsLevel.MINIMAL, runLevel(), "the run keeps its level while Settings is open")
                click("kinetickk.settings.back")
                assertEquals(ReactionEffectsLevel.REDUCED, runLevel())
                click("kinetickk.gameplay.settings")
                choose(ReactionEffectsLevel.FULL)
                assertTrue(!persistence.payload!!.contains("reactionEffectsId"), persistence.payload)
                click("kinetickk.settings.back")
                assertEquals(ReactionEffectsLevel.FULL, runLevel())
            } finally {
                owner.close()
            }
        }
    }
}

private class SavedPayload : ProfilePersistenceCapability {
    var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class MutedAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
