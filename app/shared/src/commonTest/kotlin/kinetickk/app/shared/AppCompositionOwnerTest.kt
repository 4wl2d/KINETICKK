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
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayResultSourceToken
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionRunPort
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
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
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
            listOf<GameplayModuleCommand>(
                GameplayModuleCommand.StartRun,
                GameplayModuleCommand.StartRun,
            ),
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

    override fun acceptFromSession(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult = error("unused")

    override fun acceptFromGameplay(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult = error("unused")

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
    val acceptedCommands = mutableListOf<GameplayModuleCommand>()
    private var activeRunValue: RecordingGameplayRun? = null

    fun finishActiveRun() {
        checkNotNull(activeRunValue).phase = GameplayRunPhase.GAME_OVER
    }

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayModuleResultDelivery) -> Unit,
    ): GameplaySessionRunPort {
        createdRunIds += runId
        return RecordingGameplayRun(runId, commandResultSink, acceptedCommands::add)
            .also { activeRunValue = it }
    }

    override fun activeRun(): GameplaySessionRunPort? = activeRunValue

    override fun activePresentation(): GameplayPresentationPort? = activeRunValue

    override fun receiveProfileModuleResult(delivery: kinetickk.ball.profile.api.ProfileModuleResultDelivery) =
        Unit

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) = Unit
}

private class RecordingGameplayRun(
    runId: RunId,
    private val commandResultSink: (GameplayModuleResultDelivery) -> Unit,
    private val recordCommand: (GameplayModuleCommand) -> Unit,
) : GameplaySessionRunPort, GameplayPresentationPort {
    override val instanceId = GameplayInstanceId(runId)
    private var revision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED

    override fun acceptFromSession(
        request: GameplayModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): GameplayCommandIngressResult {
        assertEquals(instanceId, request.targetInstance)
        assertEquals(GameplayModuleCommand.StartRun, request.command)
        recordCommand(request.command)
        val commandSource = GameplayCommandSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = request.targetInstance,
            causalScope = causalScope,
            causalDepth = causalDepth,
        )
        revision = GameplayRevision(revision.value + 1L)
        phase = GameplayRunPhase.RUNNING
        commandResultSink(
            GameplayModuleResultDelivery(
                commandSource = commandSource,
                resultSource = GameplayResultSourceToken(
                    semanticHandle = request.semanticHandle,
                    targetInstance = instanceId,
                    targetRevision = revision,
                    sourceOrdinal = 0,
                    causalScope = causalScope,
                    causalDepth = causalDepth + 1,
                ),
                effectiveProtocolIdentity = GameplayEffectiveProtocolIdentity.SESSION_START,
                result = GameplayModuleResult.RunStarted,
                issuerProvenance = GameplayResultIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
            ),
        )
        return GameplayCommandIngressResult.Accepted(instanceId, revision)
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(
            instanceId = instanceId,
            revision = revision,
            phase = phase,
            profileCommandPending = false,
        )

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(instanceId, revision, weapon = null)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayCodexStacksProjection(instanceId, revision, immutableListOf())
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
