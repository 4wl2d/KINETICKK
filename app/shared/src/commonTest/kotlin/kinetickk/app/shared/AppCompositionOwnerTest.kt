// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.foundation.collections.immutableListOf
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AppCompositionOwnerTest {
    @Test
    fun assemblyCapturesContentOnceAndSessionReusesGameplaySnapshotAcrossRuns() {
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
        assertEquals(2, fixture.gameplay.startConfigurations.size)
        fixture.gameplay.startConfigurations.forEach { configuration ->
            assertSame(fixture.content.gameplaySnapshot, configuration.content)
        }
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
}

private data class AssemblyFixture(
    val owner: AppCompositionOwner,
    val content: CountingContentCatalog,
    val gameplay: RecordingGameplayFeature,
    val audio: RecordingAudioService,
)

private fun assemblyFixture(): AssemblyFixture {
    val content = CountingContentCatalog()
    val profile = ReadyProfilePort()
    val gameplay = RecordingGameplayFeature()
    val audio = RecordingAudioService()
    val owner = AppCompositionOwner(
        contentCatalog = content,
        profilePort = profile,
        audioService = audio,
        gameplayFeature = gameplay,
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

private class ReadyProfilePort(
    private val profile: PlayerProfile = PlayerProfile(),
) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    private val revision = ProfileRevision(1L)

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance = error("unused")

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance = error("unused")

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
            reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
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

private class RecordingGameplayFeature : GameplayFeature {
    val createdRunIds = mutableListOf<RunId>()
    val startConfigurations = mutableListOf<RunConfiguration>()
    private var activeRunValue: RecordingGameplayRun? = null

    fun finishActiveRun() {
        checkNotNull(activeRunValue).phase = GameplayRunPhase.GAME_OVER
    }

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayPort {
        createdRunIds += runId
        return RecordingGameplayRun(runId, commandResultSink, startConfigurations::add)
            .also { activeRunValue = it }
    }

    override fun activeRun(): GameplayPort? = activeRunValue

    override fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted) = Unit

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) = Unit
}

private class RecordingGameplayRun(
    runId: RunId,
    private val commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    private val recordStart: (RunConfiguration) -> Unit,
) : GameplayPort {
    override val instanceId = GameplayInstanceId(runId)
    private var revision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance = error("unused")

    override fun accept(
        command: GameplayCommand,
        admission: GameplayCommandAdmission,
    ): GameplayAcceptance {
        assertEquals(instanceId, command.ref.targetInstance)
        assertEquals(command.ref, admission.commandRef)
        val start = assertIs<GameplaySessionPulse.StartRun>(command.pulse)
        revision = GameplayRevision(revision.value + 1L)
        phase = GameplayRunPhase.RUNNING
        recordStart(start.configuration)
        commandResultSink(
            GameplayCommandResult.Accepted(
                commandRef = command.ref,
                targetRevision = revision,
                outcome = GameplayCommandOutcome.RunStarted,
            ),
        )
        return GameplayAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(
            instanceId = instanceId,
            revision = revision,
            phase = phase,
            profileCommandPending = false,
        )

    override fun query(query: GameplayQuery.GetRender): GameplayRenderProjection =
        GameplayRenderProjection(instanceId, revision, renderModel = null)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(instanceId, revision, weapon = null)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayCodexStacksProjection(instanceId, revision, immutableListOf())
}

private class RecordingAudioService : AudioService {
    var closeCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit

    override fun close() {
        closeCalls++
    }
}
