// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayCommandRefusalEvidence
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayResultSourceToken
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionRunPort
import kinetickk.ball.gameplay.api.GameplayTargetBoundaryProvenance
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplaySessionHost
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandRefusalEvidence
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.SessionProfileRoute

internal class AppSessionTestRig(
    val profile: FakeSessionProfileRoute = FakeSessionProfileRoute(),
    val gameplay: FakeSessionGameplayHost = FakeSessionGameplayHost(),
) {
    val audioPreferences = mutableListOf<PlayerPreferences>()
    val effectEvents = mutableListOf<String>()
    var muteFeedbackCount: Int = 0
    var rebirthAcceptedFeedbackCount: Int = 0

    val component: DefaultAppSessionComponent = createAppSessionComponent(
        profileRoute = profile,
        gameplaySessionHost = gameplay,
        updateAudioPreferences = { preferences ->
            effectEvents += "audio"
            audioPreferences += preferences
        },
        playMuteFeedback = {
            effectEvents += "mute"
            muteFeedbackCount += 1
        },
        playRebirthAcceptedFeedback = {
            effectEvents += "rebirth"
            rebirthAcceptedFeedbackCount += 1
        },
    ).let { it as DefaultAppSessionComponent }.also { component ->
        profile.resultSink = component::receiveProfileModuleResult
    }
}

internal data class ProfileCommandCall(
    val request: ProfileModuleCommandRequest,
    val causalScope: Long,
    val causalDepth: Int,
)

internal class FakeSessionProfileRoute(
    var profile: PlayerProfile = PlayerProfile(
        rebirthProgress = RebirthProgress(level = 0, highestCleared = 0),
    ),
) : SessionProfileRoute {
    override val instanceId = kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID

    var revision: ProfileRevision = ProfileRevision(1L)
    var bootstrap: ProfileBootstrapStatus = ProfileBootstrapStatus.Ready
    var reset: ProfileResetStatus = ProfileResetStatus.NotRequired(false)
    var persistence: ProfilePersistenceStatus = ProfilePersistenceStatus.NotAttempted
    var projectionInstanceId = instanceId
    var resultSink: (ProfileModuleResultDelivery) -> Unit = {}
    var commandHandler: ((ProfileCommandCall) -> ProfileCommandIngressResult)? = null
    var onCommandObserved: ((ProfileCommandCall) -> Unit)? = null
    val commands = mutableListOf<ProfileCommandCall>()
    val deliveries = mutableListOf<ProfileModuleResultDelivery>()
    val queries = mutableListOf<String>()

    override fun acceptFromSession(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult {
        val call = ProfileCommandCall(request, causalScope, causalDepth)
        commands += call
        onCommandObserved?.invoke(call)
        return commandHandler?.invoke(call) ?: completeAutomatically(call)
    }

    fun complete(
        call: ProfileCommandCall,
        result: ProfileModuleResult,
        resetPathRevisionDelta: Long? = null,
        deliveryTransform: (ProfileModuleResultDelivery) -> ProfileModuleResultDelivery = { it },
    ): ProfileCommandIngressResult.Accepted {
        val acceptedRevision = ProfileRevision(revision.value + 1L)
        val revisionDelta = resetPathRevisionDelta ?: when (call.request.command) {
            ProfileModuleCommand.ConfirmLegacyReset,
            ProfileModuleCommand.RetryLegacyPurge,
            -> 1L
            else -> 0L
        }
        revision = ProfileRevision(acceptedRevision.value + revisionDelta)
        applyResult(result)
        val resultOrdinal = when (call.request.command) {
            is ProfileModuleCommand.SelectCoreShape,
            ProfileModuleCommand.ToggleMute,
            ProfileModuleCommand.AdvanceRebirth,
            -> 1
            ProfileModuleCommand.ConfirmLegacyReset,
            ProfileModuleCommand.RetryLegacyPurge,
            -> 0
            is ProfileModuleCommand.ApplyGameplayProgress -> error("Not a Session command")
        }
        val resultDepth = call.causalDepth + 1 + revisionDelta.toInt()
        val delivery = deliveryTransform(
            ProfileModuleResultDelivery(
                    commandSource = commandSource(call),
                    resultSource = ProfileResultSourceToken(
                        semanticHandle = call.request.semanticHandle,
                        targetInstance = instanceId,
                        targetRevision = revision,
                        sourceOrdinal = resultOrdinal,
                        causalScope = call.causalScope,
                        causalDepth = resultDepth,
                    ),
                    effectiveProtocolIdentity = call.request.command.effectiveIdentity,
                    result = result,
                    issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
                ),
        )
        deliveries += delivery
        resultSink(delivery)
        return ProfileCommandIngressResult.Accepted(instanceId, acceptedRevision)
    }

    fun refuse(
        call: ProfileCommandCall,
        response: ProfileCommandBoundaryResponse,
    ): ProfileCommandIngressResult.RejectedBeforeAcceptance =
        ProfileCommandIngressResult.RejectedBeforeAcceptance(
            ProfileCommandRefusalEvidence(
                commandSource = commandSource(call),
                effectiveProtocolIdentity = call.request.command.effectiveIdentity,
                boundaryResponse = response,
                targetBoundaryProvenance = ProfileTargetBoundaryProvenance(
                    instanceId,
                    call.request.command.effectiveIdentity,
                ),
            ),
        )

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        queries += "runBootstrap"
        return RunBootstrapProjection(
            projectionInstanceId,
            revision,
            if (bootstrap == ProfileBootstrapStatus.Ready) {
                ProfileRunBootstrapResult.Ready(profile.toGameplaySnapshot())
            } else {
                ProfileRunBootstrapResult.Unavailable(bootstrap)
            },
        )
    }

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection {
        queries += "preferences"
        return PreferencesProjection(projectionInstanceId, revision, profile.preferences)
    }

    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection {
        queries += "rebirthProgress"
        return RebirthProgressProjection(
            projectionInstanceId,
            revision,
            RebirthProfileSnapshot(profile.rebirthProgress),
            canAdvanceRebirth(),
        )
    }

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection {
        queries += "persistenceStatus"
        return PersistenceStatusProjection(projectionInstanceId, revision, bootstrap, reset, persistence)
    }

    private fun completeAutomatically(call: ProfileCommandCall): ProfileCommandIngressResult =
        when (val command = call.request.command) {
            is ProfileModuleCommand.SelectCoreShape -> complete(
                call,
                ProfileModuleResult.CoreShapeSelected(command.shape),
            )
            ProfileModuleCommand.ToggleMute -> {
                val preferences = profile.preferences.copy(
                    soundEnabled = !profile.preferences.soundEnabled,
                    musicEnabled = !profile.preferences.musicEnabled,
                )
                complete(call, ProfileModuleResult.PreferencesChanged(preferences))
            }
            ProfileModuleCommand.AdvanceRebirth -> complete(
                call,
                ProfileModuleResult.RebirthAdvanced(
                    profile.rebirthProgress.copy(level = profile.rebirthProgress.level + 1),
                ),
            )
            ProfileModuleCommand.ConfirmLegacyReset,
            ProfileModuleCommand.RetryLegacyPurge,
            -> complete(call, ProfileModuleResult.ResetCompleted)
            is ProfileModuleCommand.ApplyGameplayProgress -> error("Not a Session command")
        }

    private fun applyResult(result: ProfileModuleResult) {
        when (result) {
            is ProfileModuleResult.CoreShapeSelected ->
                profile = profile.copy(loadout = profile.loadout.copy(coreShape = result.shape))
            is ProfileModuleResult.PreferencesChanged ->
                profile = profile.copy(preferences = result.preferences)
            is ProfileModuleResult.RebirthAdvanced ->
                profile = profile.copy(rebirthProgress = result.progress)
            ProfileModuleResult.ResetCompleted -> {
                profile = PlayerProfile()
                bootstrap = ProfileBootstrapStatus.Ready
                reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = true)
                persistence = ProfilePersistenceStatus.Persisted(revision)
            }
            is ProfileModuleResult.ResetNeedsAttention -> {
                reset = result.status
                bootstrap = ProfileBootstrapStatus.Blocked(
                    kinetickk.ball.profile.api.ProfileBootstrapBlockReason.ResetNeedsAttention(
                        result.status.result,
                    ),
                )
            }
            is ProfileModuleResult.ResetWriteOutcomeUnknown,
            is ProfileModuleResult.ResetWriteResourceFailure,
            is ProfileModuleResult.ResetWriteRejected,
            ProfileModuleResult.GameplayProgressApplied,
            -> Unit
        }
    }

    private fun commandSource(call: ProfileCommandCall): ProfileCommandSourceToken =
        ProfileCommandSourceToken(
            call.request.semanticHandle,
            instanceId,
            call.causalScope,
            call.causalDepth,
        )

    private fun canAdvanceRebirth(): Boolean =
        profile.rebirthProgress.highestCleared >= profile.rebirthProgress.level
}

internal data class GameplayCommandCall(
    val request: GameplayModuleCommandRequest,
    val causalScope: Long,
    val causalDepth: Int,
)

internal class FakeSessionGameplayHost : GameplaySessionHost {
    val createdRunIds = mutableListOf<RunId>()
    var onCreateRun: (RunId) -> Unit = {}
    var configureRun: (FakeGameplaySessionRunPort) -> Unit = {}
    private var active: FakeGameplaySessionRunPort? = null

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayModuleResultDelivery) -> Unit,
    ): GameplaySessionRunPort {
        createdRunIds += runId
        onCreateRun(runId)
        return FakeGameplaySessionRunPort(runId, commandResultSink).also { run ->
            configureRun(run)
            active = run
        }
    }

    override fun activeRun(): GameplaySessionRunPort? = active

    fun activeFakeRun(): FakeGameplaySessionRunPort? = active
}

internal class FakeGameplaySessionRunPort(
    runId: RunId,
    private val resultSink: (GameplayModuleResultDelivery) -> Unit,
) : GameplaySessionRunPort {
    override val instanceId = GameplayInstanceId(runId)

    var revision: GameplayRevision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED
    var profileCommandPending: Boolean = false
    var statusInstanceId: GameplayInstanceId = instanceId
    var commandHandler: ((GameplayCommandCall) -> GameplayCommandIngressResult)? = null
    var onCommandObserved: ((GameplayCommandCall) -> Unit)? = null
    val commands = mutableListOf<GameplayCommandCall>()
    val deliveries = mutableListOf<GameplayModuleResultDelivery>()

    override fun acceptFromSession(
        request: GameplayModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): GameplayCommandIngressResult {
        val call = GameplayCommandCall(request, causalScope, causalDepth)
        commands += call
        onCommandObserved?.invoke(call)
        return commandHandler?.invoke(call) ?: completeAutomatically(call)
    }

    fun complete(
        call: GameplayCommandCall,
        result: GameplayModuleResult,
        nestedExit: Boolean = false,
        deliveryTransform: (GameplayModuleResultDelivery) -> GameplayModuleResultDelivery = { it },
    ): GameplayCommandIngressResult.Accepted {
        val acceptedRevision = GameplayRevision(revision.value + 1L)
        revision = if (nestedExit) {
            GameplayRevision(acceptedRevision.value + 1L)
        } else {
            acceptedRevision
        }
        phase = when (result) {
            GameplayModuleResult.RunStarted -> GameplayRunPhase.RUNNING
            GameplayModuleResult.OverlayPaused -> GameplayRunPhase.PAUSED
            GameplayModuleResult.PreferencesApplied -> phase
            is GameplayModuleResult.RunExited -> GameplayRunPhase.EXITED
        }
        val delivery = deliveryTransform(
            GameplayModuleResultDelivery(
                    commandSource = commandSource(call),
                    resultSource = GameplayResultSourceToken(
                        semanticHandle = call.request.semanticHandle,
                        targetInstance = instanceId,
                        targetRevision = revision,
                        sourceOrdinal = 0,
                        causalScope = call.causalScope,
                        causalDepth = call.causalDepth + if (nestedExit) 3 else 1,
                    ),
                    effectiveProtocolIdentity = call.request.command.effectiveIdentity,
                    result = result,
                    issuerProvenance = GameplayResultIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
                ),
        )
        deliveries += delivery
        resultSink(delivery)
        return GameplayCommandIngressResult.Accepted(instanceId, acceptedRevision)
    }

    fun refuse(
        call: GameplayCommandCall,
        response: GameplayCommandBoundaryResponse,
    ): GameplayCommandIngressResult.RejectedBeforeAcceptance =
        GameplayCommandIngressResult.RejectedBeforeAcceptance(
            GameplayCommandRefusalEvidence(
                commandSource = commandSource(call),
                effectiveProtocolIdentity = call.request.command.effectiveIdentity,
                boundaryResponse = response,
                targetBoundaryProvenance = GameplayTargetBoundaryProvenance(
                    instanceId,
                    call.request.command.effectiveIdentity,
                ),
            ),
        )

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(statusInstanceId, revision, phase, profileCommandPending)

    private fun completeAutomatically(call: GameplayCommandCall): GameplayCommandIngressResult =
        when (call.request.command) {
            GameplayModuleCommand.StartRun -> complete(call, GameplayModuleResult.RunStarted)
            GameplayModuleCommand.PauseForOverlay -> complete(call, GameplayModuleResult.OverlayPaused)
            GameplayModuleCommand.ApplyPreferences -> complete(
                call,
                GameplayModuleResult.PreferencesApplied,
            )
            GameplayModuleCommand.ExitRun -> complete(
                call,
                GameplayModuleResult.RunExited(GameplayExitProgressResult.NoProgress),
            )
        }

    private fun commandSource(call: GameplayCommandCall): GameplayCommandSourceToken =
        GameplayCommandSourceToken(
            call.request.semanticHandle,
            instanceId,
            call.causalScope,
            call.causalDepth,
        )
}

internal fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)

private val ProfileModuleCommand.effectiveIdentity: ProfileEffectiveProtocolIdentity
    get() = when (this) {
        is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
        ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
        ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
        ProfileModuleCommand.ConfirmLegacyReset -> ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM
        ProfileModuleCommand.RetryLegacyPurge -> ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY
        is ProfileModuleCommand.ApplyGameplayProgress -> ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS
    }

private val GameplayModuleCommand.effectiveIdentity: GameplayEffectiveProtocolIdentity
    get() = when (this) {
        GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
        GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
        GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
        GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
    }
