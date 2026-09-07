// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.impl.GameplayCompositionComponent
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.impl.ProfileComponent
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.foundation.collections.immutableListOf
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppCompositionOwnerTest {
    @Test
    fun assemblyCapturesContentOnceAndSessionAllocatesMonotonicRuns() {
        val fixture = assemblyFixture()

        assertEquals(1, fixture.content.profilePolicyCalls)
        assertEquals(1, fixture.content.gameplayContentCalls)
        assertEquals(1, fixture.content.uiCatalogCalls)

        assertIs<SessionAcceptance.Accepted>(
            fixture.owner.sessionPort.accept(SessionInteractionPulse.StartRunRequested),
        )
        fixture.gameplay.finishActiveRun()
        assertIs<SessionAcceptance.Accepted>(
            fixture.owner.sessionPort.accept(SessionInteractionPulse.RestartRunRequested),
        )

        assertEquals(listOf(RunId(0L), RunId(1L)), fixture.gameplay.createdRunIds)
        assertEquals(
            listOf(RunId(0), RunId(1)),
            fixture.gameplay.acceptedCommands,
        )
        assertEquals(1, fixture.content.profilePolicyCalls)
        assertEquals(1, fixture.content.gameplayContentCalls)
        assertEquals(1, fixture.content.uiCatalogCalls)
    }

    @Test
    fun assemblyOwnsTheAudioLifecycle() {
        val fixture = assemblyFixture()

        fixture.owner.close()

        assertEquals(1, fixture.audio.closeCalls)
    }

    @Test
    fun assemblySynchronizesLoadedProfileAudioBeforeAnyPulse() {
        val preferences = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = false,
            masterVolume = 0.4f,
        )
        val fixture = assemblyFixture(PlayerProfile(preferences = preferences))

        assertEquals(
            listOf(
                AudioPreferences(
                    soundEnabled = false,
                    musicEnabled = false,
                    masterVolume = 0.4f,
                ),
            ),
            fixture.audio.preferenceUpdates,
        )
    }
}

private data class AssemblyFixture(
    val owner: AppCompositionOwner,
    val content: CountingContentCatalog,
    val gameplay: RecordingGameplayComponent,
    val audio: RecordingAudioService,
)

private fun assemblyFixture(profileValue: PlayerProfile = PlayerProfile()): AssemblyFixture {
    val content = CountingContentCatalog()
    val profile = ReadyProfileComponent(profileValue)
    val gameplay = RecordingGameplayComponent()
    val audio = RecordingAudioService()
    val owner = AppCompositionOwner(
        contentCatalog = content,
        profileComponent = profile,
        audioService = audio,
        gameplayComponent = gameplay,
    )
    return AssemblyFixture(owner, content, gameplay, audio)
}

private class CountingContentCatalog(
    delegate: ContentCatalog = createContentCatalog(),
) : ContentCatalog {
    private val profilePolicySnapshot: ProfilePolicySnapshot = delegate.profilePolicy()
    val gameplaySnapshot: GameplayContentSnapshot = delegate.gameplayContent()
    private val uiCatalogSnapshot: UiCatalogSnapshot = delegate.uiCatalog()

    override val version = delegate.version

    var profilePolicyCalls: Int = 0
        private set
    var gameplayContentCalls: Int = 0
        private set
    var uiCatalogCalls: Int = 0
        private set

    override fun profilePolicy(): ProfilePolicySnapshot {
        profilePolicyCalls++
        return profilePolicySnapshot
    }

    override fun gameplayContent(): GameplayContentSnapshot {
        gameplayContentCalls++
        return gameplaySnapshot
    }

    override fun uiCatalog(): UiCatalogSnapshot {
        uiCatalogCalls++
        return uiCatalogSnapshot
    }
}

private class ReadyProfileComponent(
    private val profile: PlayerProfile = PlayerProfile(),
) : ProfileComponent {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    private val revision = ProfileRevision(1L)

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance = error("unused")

    override fun applyGameplayProgress(
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
        reply: InlineReply<kinetickk.ball.profile.api.ProfileProgressApplied, ProfileRefusal>,
    ): Unit = error("unused")

    override fun advanceRebirth(
        reply: InlineReply<kinetickk.ball.profile.api.ProfileRebirthAdvanced, ProfileRefusal>,
    ): Unit = error("unused")

    override fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>): Unit = error("unused")

    override fun selectCoreShape(
        shape: kinetickk.ball.content.api.CoreShape,
        reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>,
    ): Unit = error("unused")

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        RunBootstrapProjection(
            instanceId = instanceId,
            revision = revision,
            result = ProfileRunBootstrapResult.Ready(profile.toGameplaySnapshot()),
        )

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, profile.preferences)

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
        PersistenceStatusProjection(
            instanceId = instanceId,
            revision = revision,
            bootstrap = ProfileBootstrapStatus.Ready,
            persistence = ProfilePersistenceStatus.NotAttempted,
        )

    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
}

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)

private class RecordingGameplayComponent : GameplayCompositionComponent {
    val createdRunIds = mutableListOf<RunId>()
    val acceptedCommands = mutableListOf<RunId>()
    private var activeRunValue: RecordingGameplayRun? = null

    fun finishActiveRun() {
        checkNotNull(activeRunValue).phase = GameplayRunPhase.GAME_OVER
    }

    override fun createRun(
        runId: RunId,
    ): GameplayRunPort {
        createdRunIds += runId
        return RecordingGameplayRun(runId, acceptedCommands::add)
            .also { activeRunValue = it }
    }

    override fun activeRun(): GameplayRunPort? = activeRunValue

    override fun activePresentation(): GameplayPresentationPort? = activeRunValue

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) = Unit
}

private class RecordingGameplayRun(
    runId: RunId,
    private val recordCommand: (RunId) -> Unit,
) : GameplayRunPort, GameplayPresentationPort {
    override val instanceId = GameplayInstanceId(runId)
    private var revision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED

    override fun applyPreferences(
        preferences: PlayerPreferences,
        reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ): Unit = error("unused")

    override fun startRun(reply: InlineReply<GameplayRunStarted, GameplayRefusal>) {
        reply.checkAvailable()
        recordCommand(instanceId.runId)
        revision = GameplayRevision(revision.value + 1L)
        phase = GameplayRunPhase.RUNNING
        reply.accepted(GameplayRunStarted(instanceId.runId, revision))
    }

    override fun pauseForOverlay(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>): Unit =
        error("unused")

    override fun exitRun(reply: InlineReply<kinetickk.ball.gameplay.api.GameplayRunExited, GameplayRefusal>): Unit =
        error("unused")

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(
            instanceId = instanceId,
            revision = revision,
            phase = phase,
            progressPending = false,
        )

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(instanceId, revision, weapon = null)

    override fun query(query: GameplayQuery.GetBuildSummary): GameplayBuildSummaryProjection =
        GameplayBuildSummaryProjection(instanceId, revision, immutableListOf())
}

private class RecordingAudioService : AudioService {
    var closeCalls = 0
    val preferenceUpdates = mutableListOf<AudioPreferences>()

    override fun updatePreferences(preferences: AudioPreferences) {
        preferenceUpdates += preferences
    }
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit

    override fun close() {
        closeCalls++
    }
}
