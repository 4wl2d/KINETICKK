// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.flow.session.impl.createAppSessionComponent
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real owners exercise the complete source-target-result path through the production binding. */
class InlineSessionLifecycleTest {
    @Test
    fun settingsClosureAppliesPreferencesToThePausedRunBeforeAudio() {
        val fixture = LifecycleFixture()
        fixture.start()
        val before = fixture.runView().renderSnapshot()
        fixture.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Settings))
        assertEquals(GameplayRunPhase.PAUSED, fixture.runStatus().phase)
        assertEquals(AppDestination.Settings, fixture.shell().active)
        assertIs<ProfileAcceptance.Accepted>(fixture.profile.accept(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetMasterVolume(37)),
        ))
        val expected = fixture.profile.query(ProfileQuery.GetPreferences).preferences
        var audioUpdates = 0
        fixture.onAudio = { preferences ->
            audioUpdates++
            assertEquals(expected, preferences)
            assertEquals(expected, fixture.runView().renderSnapshot().renderModel!!.settings)
            assertEquals(AppDestination.Gameplay, fixture.shell().active)
            assertNull(fixture.shell().pendingWorkflow)
        }

        fixture.accept(SessionInteractionPulse.CloseOverlay)

        assertEquals(1, audioUpdates)
        assertEquals(GameplayRunPhase.PAUSED, fixture.runStatus().phase)
        assertEquals(0.37f, fixture.runView().renderSnapshot().renderModel!!.settings.masterVolume)
        assertEquals(0.65f, before.renderModel!!.settings.masterVolume)
        fixture.assertReady(AppDestination.Gameplay)
    }

    @Test
    fun acceptedExitProgressFinishesBeforeReplacementAndPreservesTheOldSnapshot() {
        val fixture = LifecycleFixture()
        fixture.start()
        val oldRun = fixture.gameplay.activeRun()!!
        val oldView = fixture.runView()
        val progress = fixture.earnProgress()
        val oldRender = oldView.renderSnapshot()
        val beforeMatter = fixture.profile.query(ProfileQuery.GetHomeProgress).economy.matter

        fixture.accept(SessionInteractionPulse.ExitRunRequested)

        assertEquals(GameplayRunPhase.EXITED, oldRun.query(GameplayQuery.GetRunStatus).phase)
        assertEquals(beforeMatter + progress, fixture.profile.query(ProfileQuery.GetHomeProgress).economy.matter)
        fixture.assertReady(AppDestination.Home)
        assertEquals(progress, oldRender.renderModel!!.runMatter)
        val terminalSnapshot = oldView.renderSnapshot()
        assertNotSame(oldRender, terminalSnapshot)

        fixture.start()

        assertEquals(oldRun.instanceId.runId.value + 1L, fixture.gameplay.activeRun()!!.instanceId.runId.value)
        assertNotSame(oldRun, fixture.gameplay.activeRun())
        assertSame(terminalSnapshot, oldView.renderSnapshot())
        fixture.assertReady(AppDestination.Gameplay)
    }

    @Test
    fun saveFaultAfterAcceptedExitProgressStillFinishesSessionBeforeEscaping() {
        val fixture = LifecycleFixture()
        fixture.start()
        val progress = fixture.earnProgress()
        val beforeMatter = fixture.profile.query(ProfileQuery.GetHomeProgress).economy.matter
        val fault = LifecycleFailure("save after accepted exit progress")
        fixture.persistence.failure = fault

        val thrown = assertFailsWith<LifecycleFailure> {
            fixture.session.accept(SessionInteractionPulse.ExitRunRequested)
        }

        assertSame(fault, thrown)
        assertEquals(beforeMatter + progress, fixture.profile.query(ProfileQuery.GetHomeProgress).economy.matter)
        assertEquals(GameplayRunPhase.EXITED, fixture.runStatus().phase)
        assertTrue(!fixture.runStatus().progressPending)
        fixture.assertReady(AppDestination.Home)
        fixture.persistence.failure = null
        fixture.start()
        fixture.assertReady(AppDestination.Gameplay)
    }

    @Test
    fun busyRealProfileRefusesExitProgressAndSessionKeepsItsFailureVisible() {
        val fixture = LifecycleFixture()
        fixture.start()
        fixture.earnProgress()
        val before = fixture.profile.query(ProfileQuery.GetHomeProgress).economy
        var callbacks = 0
        fixture.persistence.onWrite = {
            callbacks++
            // Profile is executing its accepted save output. The nested exit reaches that same
            // real owner while its writer is held, so progress must be refused before acceptance.
            fixture.accept(SessionInteractionPulse.ExitRunRequested)
        }

        assertIs<ProfileAcceptance.Accepted>(fixture.profile.accept(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetMasterVolume(37)),
        ))

        assertEquals(1, callbacks)
        assertEquals(before, fixture.profile.query(ProfileQuery.GetHomeProgress).economy)
        assertEquals(GameplayRunPhase.EXITED, fixture.runStatus().phase)
        assertTrue(!fixture.runStatus().progressPending)
        assertEquals(AppDestination.Gameplay, fixture.shell().active)
        assertNull(fixture.shell().pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.EXIT_PROGRESS_NOT_APPLIED, fixture.shell().workflowFailure)
        assertTrue(fixture.shell().normalInputEnabled)
    }
}

private class LifecycleFixture {
    private val catalog = createContentCatalog()
    val persistence = LifecyclePersistence()
    val profile = createProfileComponent(persistence, catalog.profilePolicy())
    val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, LifecycleSilentAudio())
    var onAudio: (PlayerPreferences) -> Unit = {}
    val session = createAppSessionComponent(
        profilePort = profile,
        profileSettings = profile,
        profileLoadout = profile,
        profileRebirth = profile,
        gameplayRunHost = gameplay,
        updateAudioPreferences = { onAudio(it) },
        playMuteFeedback = {},
        playRebirthAcceptedFeedback = {},
    )

    fun accept(pulse: SessionInteractionPulse) {
        assertIs<SessionAcceptance.Accepted>(session.accept(pulse))
    }

    fun start() {
        accept(SessionInteractionPulse.StartRunRequested)
        assertEquals(GameplayRunPhase.RUNNING, runStatus().phase)
    }

    fun shell() = session.query(AppSessionQuery.GetShell)
    fun runStatus() = gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus)
    fun runView() = gameplay.activeRun() as GameplayInteractionPort

    fun earnProgress(): Long {
        repeat(1_200) {
            val progress = runView().renderSnapshot().renderModel!!.runMatter
            if (progress > 0L) return progress
            assertEquals(GameplayRunPhase.RUNNING, runStatus().phase)
            assertIs<GameplayAcceptance.Accepted>(runView().accept(
                GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            ))
        }
        error("The deterministic run earned no progress within its existing frame budget")
    }

    fun assertReady(destination: AppDestination) {
        assertEquals(destination, shell().active)
        assertNull(shell().pendingWorkflow)
        assertNull(shell().workflowFailure)
        assertTrue(shell().normalInputEnabled)
    }
}

private class LifecyclePersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    var failure: Throwable? = null
    var onWrite: () -> Unit = {}
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        onWrite()
        failure?.let { throw it }
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class LifecycleSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}

private class LifecycleFailure(message: String) : RuntimeException(message)
