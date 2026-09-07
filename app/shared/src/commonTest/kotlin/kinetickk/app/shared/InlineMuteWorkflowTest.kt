// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.flow.session.api.SessionWorkflowPhase
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.impl.createAppSessionComponent
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real Profile, Session and Gameplay writers with failures injected at capability boundaries. */
class InlineMuteWorkflowTest {
    @Test
    fun mutePublishesProfileThenGameplayAndOnlyThenCompletesSession() {
        val fixture = MuteFixture()
        fixture.start()
        val before = fixture.shell()
        val profileRevision = fixture.profile.query(ProfileQuery.GetPreferences).revision
        val gameRevision = fixture.gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus).revision
        fixture.observeNextMute()

        val root = assertIs<SessionAcceptance.Accepted>(fixture.mute())

        fixture.assertApplied()
        assertEquals(before.revision.value + 1, root.revision.value)
        assertEquals(before.revision.value + 3, fixture.shell().revision.value)
        assertEquals(before.activeRunId, fixture.shell().activeRunId)
        assertEquals(profileRevision.value + 2, fixture.profile.query(ProfileQuery.GetPreferences).revision.value)
        assertEquals(gameRevision.value + 1, fixture.gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus).revision.value)
        assertEquals(listOf("target-enter", "persist", "target-exit", "audio", "feedback"), fixture.events)
        assertIs<ProfilePersistenceStatus.Persisted>(fixture.profile.query(ProfileQuery.GetPersistenceStatus).persistence)
        val restored = createProfileComponent(fixture.persistence, fixture.catalog.profilePolicy())
        assertEquals(fixture.preferences(), restored.query(ProfileQuery.GetPreferences).preferences)

        // A closed reply must be rejected before a second target mutation, even through the real binding.
        val finishedRevision = fixture.profile.query(ProfileQuery.GetPreferences).revision
        assertFailsWith<IllegalStateException> { fixture.profile.toggleMute(fixture.lastReply!!) }
        assertEquals(finishedRevision, fixture.profile.query(ProfileQuery.GetPreferences).revision)
        assertEquals(1, fixture.persistence.writes)
    }

    @Test
    fun persistenceFailureAndUnknownOutcomeKeepTheAcceptedMuteAcrossAllOwners() {
        listOf(
            ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
            ProfilePersistenceMutationResult.POSSIBLE_EXECUTION,
        ).forEach { outcome ->
            val fixture = MuteFixture()
            fixture.persistence.outcome = outcome
            fixture.start()
            fixture.observeNextMute()

            assertIs<SessionAcceptance.Accepted>(fixture.mute())

            fixture.assertApplied()
            val status = fixture.profile.query(ProfileQuery.GetPersistenceStatus).persistence
            when (outcome) {
                ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION ->
                    assertIs<ProfilePersistenceStatus.ResourceFailure>(status)
                ProfilePersistenceMutationResult.POSSIBLE_EXECUTION ->
                    assertIs<ProfilePersistenceStatus.OutcomeUnknown>(status)
                ProfilePersistenceMutationResult.COMPLETED -> error("Not a failure fixture")
            }
            assertEquals(1, fixture.persistence.writes)
        }
    }

    @Test
    fun aSaveProgrammingFaultStillDeliversTheAcceptedPreferencesBeforeEscaping() {
        val fixture = MuteFixture()
        val failure = MuteFailure("save")
        fixture.persistence.failure = failure
        fixture.start()
        fixture.observeNextMute()

        assertSame(failure, assertFailsWith<MuteFailure> { fixture.mute() })

        fixture.assertApplied()
        assertIs<ProfilePersistenceStatus.Pending>(fixture.profile.query(ProfileQuery.GetPersistenceStatus).persistence)
        assertEquals(listOf("target-enter", "persist", "target-exit", "audio", "feedback"), fixture.events)
        assertEquals(1, fixture.persistence.writes)
    }

    @Test
    fun aWrapperFaultAfterTheRealTargetResultDrainsWorkflowAndKeepsTheFirstFault() {
        val fixture = MuteFixture()
        val first = MuteFailure("after accepted target result")
        val later = MuteFailure("feedback")
        fixture.afterTargetFailure = first
        fixture.feedbackFailure = later
        fixture.start()
        fixture.observeNextMute()

        assertSame(first, assertFailsWith<MuteFailure> { fixture.mute() })

        fixture.assertApplied()
        assertTrue(first.suppressedExceptions.any { it === later })
        assertEquals(1, fixture.persistence.writes)
        fixture.afterTargetFailure = null
        fixture.feedbackFailure = null
        fixture.observeNextMute()
        assertIs<SessionAcceptance.Accepted>(fixture.mute())
        fixture.assertApplied()
        assertTrue(fixture.preferences().soundEnabled)
        assertTrue(fixture.preferences().musicEnabled)
        assertEquals(2, fixture.persistence.writes)
    }

    @Test
    fun participantRefusalPublishesNeitherProfileNorGameplayChanges() {
        val fixture = MuteFixture()
        fixture.start()
        val profileBefore = fixture.profile.query(ProfileQuery.GetPreferences)
        val gameBefore = fixture.gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus)
        fixture.refuseTarget = true
        fixture.observeNextMute()

        assertIs<SessionAcceptance.Accepted>(fixture.mute())

        assertEquals(profileBefore, fixture.profile.query(ProfileQuery.GetPreferences))
        assertEquals(gameBefore, fixture.gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus))
        assertNull(fixture.shell().pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, fixture.shell().workflowFailure)
        assertEquals(0, fixture.persistence.writes)
        assertEquals(listOf("target-enter", "target-exit", "feedback"), fixture.events)
    }
}

private class MuteFixture {
    val events = mutableListOf<String>()
    val catalog = createContentCatalog()
    val persistence = MutePersistence()
    val profile = createProfileComponent(persistence, catalog.profilePolicy())
    val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, MuteSilentAudio())
    private lateinit var session: AppSessionPort
    private var observing = false
    private var expected = PlayerPreferences()
    private var observeAudio: (PlayerPreferences) -> Unit = {}
    var lastReply: InlineReply<ProfileSettingsChanged, ProfileRefusal>? = null
    var afterTargetFailure: Throwable? = null
    var feedbackFailure: Throwable? = null
    var refuseTarget = false

    init {
        val settings = object : ProfileSettings {
            override fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>) {
                lastReply = reply
                if (observing) {
                    assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell().pendingWorkflow)
                    events += "target-enter"
                }
                try {
                    if (refuseTarget) reply.refused(ProfileRefusal.Busy)
                    else {
                        profile.toggleMute(reply)
                        afterTargetFailure?.let { throw it }
                    }
                } finally {
                    if (observing) {
                        assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell().pendingWorkflow)
                        events += "target-exit"
                    }
                }
            }
        }
        session = createAppSessionComponent(
            profilePort = profile,
            profileSettings = settings,
            profileLoadout = profile,
            profileRebirth = profile,
            gameplayRunHost = gameplay,
            updateAudioPreferences = { preferences -> if (observing) observeAudio(preferences) },
            playMuteFeedback = {
                if (observing) events += "feedback"
                feedbackFailure?.let { throw it }
            },
            playRebirthAcceptedFeedback = {},
        )
    }

    fun start() {
        assertIs<SessionAcceptance.Accepted>(session.accept(SessionInteractionPulse.StartRunRequested))
        assertEquals(GameplayRunPhase.RUNNING, gameplay.activeRun()!!.query(GameplayQuery.GetRunStatus).phase)
    }

    fun observeNextMute() {
        val before = preferences()
        val enabled = !(before.soundEnabled || before.musicEnabled)
        expected = before.copy(soundEnabled = enabled, musicEnabled = enabled)
        val presentation = gameplay.activeRun() as GameplayInteractionPort
        val previousRender = presentation.renderSnapshot()
        events.clear()
        observing = true
        persistence.beforeWrite = {
            events += "persist"
            assertEquals(expected, preferences())
            assertSame(previousRender, presentation.renderSnapshot())
            assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell().pendingWorkflow)
        }
        observeAudio = { preferences ->
            events += "audio"
            assertEquals(expected, preferences)
            assertEquals(preferences, presentation.renderSnapshot().renderModel!!.settings)
            assertEquals(before, previousRender.renderModel!!.settings)
            assertEquals(SessionWorkflowPhase.PROPAGATING_MUTE, shell().pendingWorkflow)
        }
    }

    fun mute() = session.accept(SessionInteractionPulse.ToggleMuteRequested)
    fun shell() = session.query(AppSessionQuery.GetShell)
    fun preferences() = profile.query(ProfileQuery.GetPreferences).preferences

    fun assertApplied() {
        assertEquals(expected, preferences())
        val run = gameplay.activeRun()!!
        assertEquals(expected, (run as GameplayInteractionPort).renderSnapshot().renderModel!!.settings)
        assertEquals(GameplayRunPhase.RUNNING, run.query(GameplayQuery.GetRunStatus).phase)
        assertNull(shell().pendingWorkflow)
        assertNull(shell().workflowFailure)
        assertTrue(shell().normalInputEnabled)
    }
}

private class MutePersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    var writes = 0
    var outcome = ProfilePersistenceMutationResult.COMPLETED
    var failure: Throwable? = null
    var beforeWrite: () -> Unit = {}

    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        writes++
        beforeWrite()
        failure?.let { throw it }
        if (outcome == ProfilePersistenceMutationResult.COMPLETED) this.payload = payload
        return outcome
    }
}

private class MuteSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}

private class MuteFailure(message: String) : RuntimeException(message)
