// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayCommandAdmissionFailureReason
import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.interaction.GameplaySessionHost
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.SessionProfileRoute
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionInstanceId
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.flow.session.nucleus.AppSessionAcceptedFrame
import kinetickk.flow.session.nucleus.AppSessionContext
import kinetickk.flow.session.nucleus.AppSessionDecision
import kinetickk.flow.session.nucleus.AppSessionNucleus
import kinetickk.flow.session.nucleus.AppSessionNucleusPulse
import kinetickk.flow.session.nucleus.AppSessionOutput
import kinetickk.flow.session.nucleus.AppSessionState
import kinetickk.flow.session.nucleus.MAX_SESSION_OUTPUTS_PER_DECISION
import kinetickk.flow.session.nucleus.PendingParticipantCommand
import kinetickk.flow.session.nucleus.PendingWorkflow
import kinetickk.flow.session.nucleus.gameplayCommandRejectedBeforeAcceptance
import kinetickk.flow.session.nucleus.gameplayModuleResultPulse
import kinetickk.flow.session.nucleus.profileCommandRejectedBeforeAcceptance
import kinetickk.flow.session.nucleus.profileModuleResultPulse
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, publisher, and ordered-output dispatcher for AppSession. */
internal class DefaultAppSessionComponent private constructor(
    initialState: AppSessionState,
    private val profileRoute: SessionProfileRoute,
    private val gameplaySessionHost: GameplaySessionHost,
    private val updateAudioPreferences: (PlayerPreferences) -> Unit,
    private val playMuteFeedback: () -> Unit,
    private val playRebirthAcceptedFeedback: () -> Unit,
) : AppSessionComponent {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions = sessionCompletionDeque<SessionWorkItem>()
    private var committedState: AppSessionState = initialState
    private var activeProfileRoute: ProfileRouteReservation? = null
    private var activeGameplayRoute: GameplayRouteReservation? = null
    private var observedProfileDelivery: ProfileModuleResultDelivery? = null
    private var observedGameplayDelivery: GameplayModuleResultDelivery? = null
    private var nextLocalCausalScope: Long = 1L

    override val instanceId: AppSessionInstanceId
        get() = committedState.instanceId

    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance =
        dispatchLocal(pulse)

    override fun query(query: AppSessionQuery.GetShell): AppShellProjection =
        AppSessionNucleus.query(committedState, query)

    /** Raw target evidence is validated and retained; no trusted Nucleus pulse exists yet. */
    override fun receiveProfileModuleResult(delivery: ProfileModuleResultDelivery) {
        check(dispatchGuard.isDispatching) {
            "Inline Profile completion arrived outside its Session causal scope"
        }
        val route = checkNotNull(activeProfileRoute) {
            "Profile result arrived without a reserved Session route"
        }
        check(observedProfileDelivery == null) {
            "Profile emitted more than one result for one Session command"
        }
        validateProfileDelivery(route, delivery)
        observedProfileDelivery = delivery
    }

    /** Raw target evidence is validated and retained; no trusted Nucleus pulse exists yet. */
    override fun receiveGameplayModuleResult(delivery: GameplayModuleResultDelivery) {
        check(dispatchGuard.isDispatching) {
            "Inline Gameplay completion arrived outside its Session causal scope"
        }
        val route = checkNotNull(activeGameplayRoute) {
            "Gameplay result arrived without a reserved Session route"
        }
        check(observedGameplayDelivery == null) {
            "Gameplay emitted more than one result for one Session command"
        }
        validateGameplayDelivery(route, delivery)
        observedGameplayDelivery = delivery
    }

    internal fun stateSnapshot(): AppSessionState = committedState

    private fun dispatchLocal(intent: SessionInteractionPulse): SessionAcceptance =
        dispatchGuard.dispatch {
            check(activeProfileRoute == null && activeGameplayRoute == null)
            check(completions.isEmpty) { "Session completion deque leaked across dispatches" }
            val causalScope = allocateLocalCausalScope()
            check(
                completions.tryAddLast(
                    SessionWorkItem(
                        pulse = AppSessionNucleusPulse.Intent(intent),
                        causalScope = causalScope,
                        causalDepth = 0,
                    ),
                ),
            )

            var rootAcceptance: SessionAcceptance? = null
            var root = true
            var deferredFault: Throwable? = null
            while (!completions.isEmpty) {
                val item = checkNotNull(completions.removeFirstOrNull())
                val before = committedState
                val context = readContext(before, item.pulse)
                when (val decision = AppSessionNucleus.decide(before, item.pulse, context)) {
                    is AppSessionDecision.Rejected -> {
                        check(root) {
                            "A trusted Session completion was rejected: ${decision.reason}"
                        }
                        rootAcceptance = SessionAcceptance.Rejected(
                            instanceId = before.instanceId,
                            observedRevision = before.revision,
                            reason = decision.reason,
                        )
                    }
                    is AppSessionDecision.Accepted -> {
                        preflight(before, item, decision.frame)
                        committedState = decision.frame.nextState
                        if (root) {
                            rootAcceptance = SessionAcceptance.Accepted(
                                instanceId = committedState.instanceId,
                                revision = committedState.revision,
                            )
                        }
                        decision.frame.outputs.forEach { output ->
                            try {
                                execute(output, item)
                            } catch (failure: Throwable) {
                                if (deferredFault == null) deferredFault = failure
                            }
                        }
                    }
                }
                root = false
            }

            deferredFault?.let { throw it }
            check(activeProfileRoute == null && activeGameplayRoute == null)
            checkNotNull(rootAcceptance)
        }

    private fun preflight(
        before: AppSessionState,
        item: SessionWorkItem,
        frame: AppSessionAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Session instance identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Session revision must advance exactly once"
        }
        val shellProjection = AppSessionNucleus.query(next, AppSessionQuery.GetShell)
        check(
            shellProjection.instanceId == next.instanceId &&
                shellProjection.revision == next.revision &&
                shellProjection.routeRevision == next.routeRevision,
        ) {
            "Session shell projection must derive from the accepted next State"
        }
        check(frame.outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION) {
            "Session output limit exceeded"
        }
        check(frame.outputs.zipWithNext().all { (left, right) ->
            left.dispatchOrder <= right.dispatchOrder
        }) { "Session outputs are not in ensure -> participant -> feedback order" }

        val participantOutputs = frame.outputs.filter { it.isParticipantCommand }
        val ensureOutputs = frame.outputs.filterIsInstance<AppSessionOutput.EnsureGameplayRun>()
        requireSessionOutputFanoutBounds(participantOutputs.size, ensureOutputs.size)
        participantOutputs.singleOrNull()?.let { output ->
            requireSessionCausalDepth(item.causalDepth + 1)
            requireSessionCompletionCapacity(completions.remainingCapacity, 1)
            when (output) {
                is AppSessionOutput.SendProfileCommand -> preflightProfileCommand(next, output.request)
                is AppSessionOutput.SendGameplayCommand -> preflightGameplayCommand(
                    next,
                    output.request,
                    ensureOutputs.singleOrNull(),
                )
                else -> error("Filtered Session participant output changed kind")
            }
        } ?: check(next.pendingWorkflow == null) {
            "Session retained a participant command without emitting it"
        }

        ensureOutputs.singleOrNull()?.let { ensure ->
            val gameplay = participantOutputs.singleOrNull() as? AppSessionOutput.SendGameplayCommand
            check(gameplay?.request?.targetInstance?.runId == ensure.runId) {
                "Ensured GameplayRun does not match the emitted command target"
            }
        }
    }

    private fun preflightProfileCommand(
        next: AppSessionState,
        request: ProfileModuleCommandRequest,
    ) {
        val pending = next.pendingWorkflow?.participant as? PendingParticipantCommand.Profile
        checkNotNull(pending) { "Session emitted Profile command without retaining it" }
        check(pending.request == request) { "Session retained a different Profile command" }
        check(request.semanticHandle.sourceInstance == ProfileCommandSource.LocalSession)
        check(request.semanticHandle.sourceRevision == next.revision.value)
        check(request.targetInstance == profileRoute.instanceId)
    }

    private fun preflightGameplayCommand(
        next: AppSessionState,
        request: GameplayModuleCommandRequest,
        ensure: AppSessionOutput.EnsureGameplayRun?,
    ) {
        val pending = next.pendingWorkflow?.participant as? PendingParticipantCommand.Gameplay
        checkNotNull(pending) { "Session emitted Gameplay command without retaining it" }
        check(pending.request == request) { "Session retained a different Gameplay command" }
        check(request.semanticHandle.sourceInstance == GameplayCommandSource.LocalSession)
        check(request.semanticHandle.sourceRevision == next.revision.value)
        check(request.targetInstance.runId == next.activeRunId)
        if (ensure == null) {
            val active = checkNotNull(gameplaySessionHost.activeRun()) {
                "Session emitted a Gameplay command without a bound active run"
            }
            check(request.targetInstance == active.instanceId) {
                "Session Gameplay command does not target the bound active run"
            }
        }
    }

    private fun execute(output: AppSessionOutput, item: SessionWorkItem) {
        when (output) {
            is AppSessionOutput.EnsureGameplayRun -> ensureGameplayRun(output)
            is AppSessionOutput.SendProfileCommand -> executeProfileCommand(output, item)
            is AppSessionOutput.SendGameplayCommand -> executeGameplayCommand(output, item)
            is AppSessionOutput.SynchronizeAudioPreferences ->
                updateAudioPreferences(output.preferences)
            AppSessionOutput.PlayMuteFeedback -> playMuteFeedback()
            AppSessionOutput.PlayRebirthAcceptedFeedback -> playRebirthAcceptedFeedback()
        }
    }

    private fun ensureGameplayRun(output: AppSessionOutput.EnsureGameplayRun) {
        val active = gameplaySessionHost.activeRun()
        val run = if (active?.instanceId?.runId == output.runId) {
            active
        } else {
            gameplaySessionHost.createRun(output.runId, ::receiveGameplayModuleResult)
        }
        check(run.instanceId.runId == output.runId) {
            "GameplaySessionHost created a different RunId than Session reserved"
        }
        check(gameplaySessionHost.activeRun() === run) {
            "GameplaySessionHost did not retain the ensured GameplayRun"
        }
    }

    private fun executeProfileCommand(
        output: AppSessionOutput.SendProfileCommand,
        item: SessionWorkItem,
    ) {
        check(activeProfileRoute == null)
        val route = ProfileRouteReservation(output.request, item.causalScope, item.causalDepth)
        activeProfileRoute = route
        observedProfileDelivery = null
        try {
            val ingress = try {
                profileRoute.acceptFromSession(
                    request = output.request,
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth,
                )
            } catch (failure: Throwable) {
                observedProfileDelivery?.let(::enqueueProfileDelivery)
                throw failure
            }
            when (ingress) {
                is ProfileCommandIngressResult.Accepted -> {
                    check(ingress.targetInstance == output.request.targetInstance)
                    val delivery = checkNotNull(observedProfileDelivery) {
                        "Accepted inline Profile command returned without its reserved result"
                    }
                    validateAcceptedProfileRevision(route, delivery, ingress)
                    enqueueProfileDelivery(delivery)
                }
                is ProfileCommandIngressResult.RejectedBeforeAcceptance -> {
                    check(observedProfileDelivery == null) {
                        "Profile command both completed and rejected before acceptance"
                    }
                    validateProfileRefusal(route, ingress)
                    val refusal = ingress.refusal
                    enqueueCompletion(
                        pulse = profileCommandRejectedBeforeAcceptance(
                            commandSource = refusal.commandSource,
                            effectiveProtocolIdentity = refusal.effectiveProtocolIdentity,
                            boundaryResponse = refusal.boundaryResponse,
                            targetBoundaryProvenance = refusal.targetBoundaryProvenance,
                        ),
                        causalScope = refusal.commandSource.causalScope,
                        causalDepth = refusal.commandSource.causalDepth + 1,
                    )
                }
            }
        } finally {
            activeProfileRoute = null
            observedProfileDelivery = null
        }
    }

    private fun executeGameplayCommand(
        output: AppSessionOutput.SendGameplayCommand,
        item: SessionWorkItem,
    ) {
        val target = checkNotNull(gameplaySessionHost.activeRun()) {
            "Session cannot command Gameplay before ensuring a run"
        }
        check(target.instanceId == output.request.targetInstance) {
            "Session Gameplay command target is not the bound active run"
        }
        check(activeGameplayRoute == null)
        val route = GameplayRouteReservation(output.request, item.causalScope, item.causalDepth)
        activeGameplayRoute = route
        observedGameplayDelivery = null
        try {
            val ingress = try {
                target.acceptFromSession(
                    request = output.request,
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth,
                )
            } catch (failure: Throwable) {
                observedGameplayDelivery?.let(::enqueueGameplayDelivery)
                throw failure
            }
            when (ingress) {
                is GameplayCommandIngressResult.Accepted -> {
                    check(ingress.targetInstance == output.request.targetInstance)
                    val delivery = checkNotNull(observedGameplayDelivery) {
                        "Accepted inline Gameplay command returned without its reserved result"
                    }
                    validateAcceptedGameplayRevision(route, delivery, ingress)
                    enqueueGameplayDelivery(delivery)
                }
                is GameplayCommandIngressResult.RejectedBeforeAcceptance -> {
                    check(observedGameplayDelivery == null) {
                        "Gameplay command both completed and rejected before acceptance"
                    }
                    validateGameplayRefusal(route, ingress)
                    val refusal = ingress.refusal
                    enqueueCompletion(
                        pulse = gameplayCommandRejectedBeforeAcceptance(
                            commandSource = refusal.commandSource,
                            effectiveProtocolIdentity = refusal.effectiveProtocolIdentity,
                            boundaryResponse = refusal.boundaryResponse,
                            targetBoundaryProvenance = refusal.targetBoundaryProvenance,
                        ),
                        causalScope = refusal.commandSource.causalScope,
                        causalDepth = refusal.commandSource.causalDepth + 1,
                    )
                }
            }
        } finally {
            activeGameplayRoute = null
            observedGameplayDelivery = null
        }
    }

    private fun enqueueProfileDelivery(delivery: ProfileModuleResultDelivery) {
        enqueueCompletion(
            pulse = profileModuleResultPulse(
                commandSource = delivery.commandSource,
                resultSource = delivery.resultSource,
                effectiveProtocolIdentity = delivery.effectiveProtocolIdentity,
                result = delivery.result,
                issuerProvenance = delivery.issuerProvenance,
            ),
            causalScope = delivery.resultSource.causalScope,
            causalDepth = delivery.resultSource.causalDepth + 1,
        )
    }

    private fun enqueueGameplayDelivery(delivery: GameplayModuleResultDelivery) {
        enqueueCompletion(
            pulse = gameplayModuleResultPulse(
                commandSource = delivery.commandSource,
                resultSource = delivery.resultSource,
                effectiveProtocolIdentity = delivery.effectiveProtocolIdentity,
                result = delivery.result,
                issuerProvenance = delivery.issuerProvenance,
            ),
            causalScope = delivery.resultSource.causalScope,
            causalDepth = delivery.resultSource.causalDepth + 1,
        )
    }

    private fun validateProfileDelivery(
        route: ProfileRouteReservation,
        delivery: ProfileModuleResultDelivery,
    ) {
        check(delivery.commandSource == route.commandSource(profileRoute)) {
            "Profile result command-source correlation mismatch"
        }
        check(delivery.effectiveProtocolIdentity == route.request.command.effectiveIdentity)
        check(delivery.issuerProvenance == ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING)
        check(delivery.resultSource.semanticHandle == route.request.semanticHandle)
        check(delivery.resultSource.targetInstance == route.request.targetInstance)
        check(delivery.resultSource.causalScope == route.causalScope)
        check(delivery.resultSource.sourceOrdinal == route.request.command.expectedResultOrdinal)
        check(delivery.result.matches(route.request.command)) {
            "Profile result payload contradicted the closed Session mapping"
        }
    }

    private fun validateAcceptedProfileRevision(
        route: ProfileRouteReservation,
        delivery: ProfileModuleResultDelivery,
        ingress: ProfileCommandIngressResult.Accepted,
    ) {
        val revisionDelta = delivery.resultSource.targetRevision.value - ingress.targetRevision.value
        val depthDelta = delivery.resultSource.causalDepth - route.sourceDepth
        when (route.request.command) {
            is ProfileModuleCommand.SelectCoreShape,
            ProfileModuleCommand.ToggleMute,
            ProfileModuleCommand.AdvanceRebirth,
            -> {
                check(revisionDelta == 0L)
                check(depthDelta == 1)
            }
            ProfileModuleCommand.ConfirmLegacyReset -> when (delivery.result) {
                is ProfileModuleResult.ResetWriteRejected,
                is ProfileModuleResult.ResetWriteResourceFailure,
                is ProfileModuleResult.ResetWriteOutcomeUnknown,
                -> {
                    check(revisionDelta == 1L)
                    check(depthDelta == 2)
                }
                is ProfileModuleResult.ResetNeedsAttention -> {
                    check(revisionDelta == 2L)
                    check(depthDelta == 3)
                }
                ProfileModuleResult.ResetCompleted -> {
                    check(revisionDelta in 1L..2L)
                    check(depthDelta == revisionDelta.toInt() + 1)
                }
                else -> error("Profile reset result mapping changed")
            }
            ProfileModuleCommand.RetryLegacyPurge -> {
                check(revisionDelta == 1L)
                check(depthDelta == 2)
            }
            is ProfileModuleCommand.ApplyGameplayProgress ->
                error("Gameplay progress cannot enter Profile through Session")
        }
    }

    private fun validateGameplayDelivery(
        route: GameplayRouteReservation,
        delivery: GameplayModuleResultDelivery,
    ) {
        check(delivery.commandSource == route.commandSource()) {
            "Gameplay result command-source correlation mismatch"
        }
        check(delivery.effectiveProtocolIdentity == route.request.command.effectiveIdentity)
        check(delivery.issuerProvenance == GameplayResultIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING)
        check(delivery.resultSource.semanticHandle == route.request.semanticHandle)
        check(delivery.resultSource.targetInstance == route.request.targetInstance)
        check(delivery.resultSource.sourceOrdinal == 0)
        check(delivery.resultSource.causalScope == route.causalScope)
        check(delivery.result.matches(route.request.command)) {
            "Gameplay result payload contradicted the closed Session mapping"
        }
    }

    private fun validateAcceptedGameplayRevision(
        route: GameplayRouteReservation,
        delivery: GameplayModuleResultDelivery,
        ingress: GameplayCommandIngressResult.Accepted,
    ) {
        val nestedExit = (delivery.result as? GameplayModuleResult.RunExited)?.progress
            ?.let { it != GameplayExitProgressResult.NoProgress } == true
        val expectedRevisionDelta = if (nestedExit) 1L else 0L
        val expectedDepthDelta = if (nestedExit) 3 else 1
        check(
            delivery.resultSource.targetRevision.value - ingress.targetRevision.value ==
                expectedRevisionDelta,
        )
        check(delivery.resultSource.causalDepth - route.sourceDepth == expectedDepthDelta)
    }

    private fun validateProfileRefusal(
        route: ProfileRouteReservation,
        ingress: ProfileCommandIngressResult.RejectedBeforeAcceptance,
    ) {
        val refusal = ingress.refusal
        check(refusal.commandSource == route.commandSource(profileRoute))
        check(refusal.effectiveProtocolIdentity == route.request.command.effectiveIdentity)
        check(refusal.targetBoundaryProvenance.targetInstance == profileRoute.instanceId)
        check(
            refusal.targetBoundaryProvenance.effectiveProtocolIdentity ==
                refusal.effectiveProtocolIdentity,
        )
        val admission = refusal.boundaryResponse as? ProfileCommandBoundaryResponse.AdmissionFailure
        val budget = admission?.reason as? ProfileCommandAdmissionFailureReason.CausalBudgetExceeded
        if (budget != null) check(budget.causalScope == route.causalScope)
    }

    private fun validateGameplayRefusal(
        route: GameplayRouteReservation,
        ingress: GameplayCommandIngressResult.RejectedBeforeAcceptance,
    ) {
        val refusal = ingress.refusal
        check(refusal.commandSource == route.commandSource())
        check(refusal.effectiveProtocolIdentity == route.request.command.effectiveIdentity)
        check(refusal.targetBoundaryProvenance.targetInstance == route.request.targetInstance)
        check(
            refusal.targetBoundaryProvenance.effectiveProtocolIdentity ==
                refusal.effectiveProtocolIdentity,
        )
        val admission = refusal.boundaryResponse as? GameplayCommandBoundaryResponse.AdmissionFailure
        val budget = admission?.reason as? GameplayCommandAdmissionFailureReason.CausalBudgetExceeded
        if (budget != null) check(budget.causalScope == route.causalScope)
    }

    private fun enqueueCompletion(
        pulse: AppSessionNucleusPulse,
        causalScope: Long,
        causalDepth: Int,
    ) {
        requireSessionCausalDepth(causalDepth)
        check(
            completions.tryAddLast(SessionWorkItem(pulse, causalScope, causalDepth)),
        ) { "Pre-reserved Session completion could not be retained" }
    }

    private fun readContext(
        state: AppSessionState,
        pulse: AppSessionNucleusPulse,
    ): AppSessionContext {
        if (pulse is AppSessionNucleusPulse.Intent) {
            if (state.pendingWorkflow != null) return AppSessionContext.Empty
            val resetAction = pulse.intent == SessionInteractionPulse.ResetCancelled ||
                pulse.intent == SessionInteractionPulse.ResetConfirmed ||
                pulse.intent == SessionInteractionPulse.ResetRetryRequested
            if (state.resetLifecycle != SessionResetLifecycle.READY && !resetAction) {
                return AppSessionContext.Empty
            }
        }

        var runBootstrap = false
        var preferences = false
        var rebirthProgress = false
        var persistenceStatus = false
        var gameplayStatus = false

        fun requestOpenOverlayContext(destination: AppDestination) {
            if (!destination.isOverlayDestination()) return
            gameplayStatus = state.activeRunId != null
            preferences = state.overlay == AppDestination.Settings &&
                destination != AppDestination.Settings
        }

        fun requestCloseOverlayContext() {
            if (state.overlay == null) return
            gameplayStatus = state.activeRunId != null
            preferences = state.overlay == AppDestination.Settings
        }

        when (pulse) {
            is AppSessionNucleusPulse.Intent -> when (val intent = pulse.intent) {
                SessionInteractionPulse.StartRunRequested,
                SessionInteractionPulse.RestartRunRequested,
                -> {
                    runBootstrap = true
                    gameplayStatus = state.overlay == null && state.activeRunId != null
                }
                SessionInteractionPulse.ExitRunRequested ->
                    gameplayStatus = state.base == AppDestination.Gameplay &&
                        state.overlay == null &&
                        state.activeRunId != null
                is SessionInteractionPulse.OpenOverlay ->
                    requestOpenOverlayContext(intent.destination)
                SessionInteractionPulse.CloseOverlay -> requestCloseOverlayContext()
                is SessionInteractionPulse.ShortcutObserved -> when (intent.shortcut) {
                    SessionShortcut.SETTINGS -> requestOpenOverlayContext(AppDestination.Settings)
                    SessionShortcut.LAB -> requestOpenOverlayContext(AppDestination.Lab)
                    SessionShortcut.ARMORY -> requestOpenOverlayContext(AppDestination.Armory)
                    SessionShortcut.REBIRTH -> requestOpenOverlayContext(AppDestination.Rebirth)
                    SessionShortcut.CODEX -> requestOpenOverlayContext(AppDestination.Codex)
                    SessionShortcut.BACK -> requestCloseOverlayContext()
                    SessionShortcut.ENTER -> if (state.overlay != null) {
                        requestCloseOverlayContext()
                    } else if (state.base == AppDestination.Home) {
                        runBootstrap = true
                        gameplayStatus = state.activeRunId != null
                    }
                    SessionShortcut.MUTE -> Unit
                }
                SessionInteractionPulse.RebirthRequested -> {
                    rebirthProgress = state.overlay == AppDestination.Rebirth &&
                        (state.base == AppDestination.Home ||
                            state.gameplayPhase == kinetickk.ball.gameplay.api.GameplayRunPhase.VICTORY)
                }
                SessionInteractionPulse.ResetConfirmed -> {
                    persistenceStatus =
                        state.resetLifecycle == SessionResetLifecycle.CONFIRMATION_REQUIRED
                }
                SessionInteractionPulse.ResetRetryRequested -> {
                    persistenceStatus =
                        state.resetLifecycle == SessionResetLifecycle.PURGE_NEEDS_ATTENTION
                }
                SessionInteractionPulse.ToggleMuteRequested,
                is SessionInteractionPulse.SelectCoreShapeRequested,
                SessionInteractionPulse.ResetCancelled,
                -> Unit
            }
            is AppSessionNucleusPulse.ModuleResultPulse -> when (state.pendingWorkflow) {
                is PendingWorkflow.AdvancingRebirth -> runBootstrap = true
                is PendingWorkflow.ConfirmingReset,
                is PendingWorkflow.RetryingPurge,
                -> {
                    persistenceStatus = true
                    preferences = true
                }
                else -> Unit
            }
            is AppSessionNucleusPulse.ControlPulse -> Unit
        }

        return AppSessionContext(
            runBootstrap = if (runBootstrap) readRunBootstrap() else null,
            preferences = if (preferences) readPreferences() else null,
            rebirthProgress = if (rebirthProgress) readRebirthProgress() else null,
            persistenceStatus = if (persistenceStatus) readPersistenceStatus() else null,
            gameplayStatus = if (gameplayStatus) readGameplayStatus(state) else null,
        )
    }

    private fun readRunBootstrap(): RunBootstrapProjection =
        profileRoute.query(ProfileQuery.GetRunBootstrap).also(::validateProfileProjection)

    private fun readPreferences(): PreferencesProjection =
        profileRoute.query(ProfileQuery.GetPreferences).also(::validateProfileProjection)

    private fun readRebirthProgress(): RebirthProgressProjection =
        profileRoute.query(ProfileQuery.GetRebirthProgress).also(::validateProfileProjection)

    private fun readPersistenceStatus(): PersistenceStatusProjection =
        profileRoute.query(ProfileQuery.GetPersistenceStatus).also(::validateProfileProjection)

    private fun validateProfileProjection(projection: kinetickk.ball.profile.api.ProfileProjection) {
        check(projection.instanceId == profileRoute.instanceId) {
            "Profile projection came from the wrong instance"
        }
    }

    private fun readGameplayStatus(state: AppSessionState) =
        checkNotNull(gameplaySessionHost.activeRun()) {
            "Session retained an active RunId without a bound GameplayRun"
        }.let { run ->
            check(run.instanceId.runId == state.activeRunId) {
                "Session active GameplayRun identity mismatch"
            }
            run.query(GameplayQuery.GetRunStatus).also { projection ->
                check(projection.instanceId == run.instanceId) {
                    "Gameplay status projection came from the wrong run"
                }
            }
        }

    private fun allocateLocalCausalScope(): Long {
        check(nextLocalCausalScope < Long.MAX_VALUE) { "Session local causal scope exhausted" }
        return nextLocalCausalScope++
    }

    companion object {
        fun create(
            profileRoute: SessionProfileRoute,
            gameplaySessionHost: GameplaySessionHost,
            updateAudioPreferences: (PlayerPreferences) -> Unit,
            playMuteFeedback: () -> Unit,
            playRebirthAcceptedFeedback: () -> Unit,
        ): DefaultAppSessionComponent {
            check(profileRoute.instanceId == LOCAL_PROFILE_INSTANCE_ID) {
                "AppSession must bind the application-lifetime local Profile"
            }
            val persistence = profileRoute.query(ProfileQuery.GetPersistenceStatus)
            val preferences = profileRoute.query(ProfileQuery.GetPreferences)
            check(persistence.instanceId == profileRoute.instanceId) {
                "Session construction bootstrap came from the wrong Profile"
            }
            check(preferences.instanceId == profileRoute.instanceId) {
                "Session construction preferences came from the wrong Profile"
            }
            check(preferences.revision == persistence.revision) {
                "Session construction Profile projections do not share one revision"
            }
            return DefaultAppSessionComponent(
                initialState = AppSessionState.initial(persistence),
                profileRoute = profileRoute,
                gameplaySessionHost = gameplaySessionHost,
                updateAudioPreferences = updateAudioPreferences,
                playMuteFeedback = playMuteFeedback,
                playRebirthAcceptedFeedback = playRebirthAcceptedFeedback,
            ).also {
                updateAudioPreferences(preferences.preferences)
            }
        }
    }
}

internal fun <T> sessionCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(SESSION_COMPLETION_CAPACITY)

internal fun requireSessionCausalDepth(causalDepth: Int) {
    check(causalDepth in 0 until MAX_SESSION_CAUSAL_DEPTH) {
        "Session causal depth exhausted before acceptance"
    }
}

internal fun requireSessionOutputFanoutBounds(participantCount: Int, ensureCount: Int) {
    check(participantCount in 0..1) {
        "A Session decision may issue at most one participant command"
    }
    check(ensureCount in 0..1) {
        "A Session decision may ensure at most one GameplayRun"
    }
}

internal fun requireSessionCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Session completion capacity exhausted before acceptance"
    }
}

private data class SessionWorkItem(
    val pulse: AppSessionNucleusPulse,
    val causalScope: Long,
    val causalDepth: Int,
)

private data class ProfileRouteReservation(
    val request: ProfileModuleCommandRequest,
    val causalScope: Long,
    val sourceDepth: Int,
) {
    fun commandSource(profileRoute: SessionProfileRoute): ProfileCommandSourceToken =
        ProfileCommandSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = profileRoute.instanceId,
            causalScope = causalScope,
            causalDepth = sourceDepth,
        )
}

private data class GameplayRouteReservation(
    val request: GameplayModuleCommandRequest,
    val causalScope: Long,
    val sourceDepth: Int,
) {
    fun commandSource(): GameplayCommandSourceToken = GameplayCommandSourceToken(
        semanticHandle = request.semanticHandle,
        targetInstance = request.targetInstance,
        causalScope = causalScope,
        causalDepth = sourceDepth,
    )
}

private val ProfileModuleCommand.effectiveIdentity: ProfileEffectiveProtocolIdentity
    get() = when (this) {
        is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
        ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
        ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
        ProfileModuleCommand.ConfirmLegacyReset -> ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM
        ProfileModuleCommand.RetryLegacyPurge -> ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY
        is ProfileModuleCommand.ApplyGameplayProgress ->
            error("Gameplay progress is not a Session command mapping")
    }

private val ProfileModuleCommand.expectedResultOrdinal: Int
    get() = when (this) {
        is ProfileModuleCommand.SelectCoreShape,
        ProfileModuleCommand.ToggleMute,
        ProfileModuleCommand.AdvanceRebirth,
        -> 1
        ProfileModuleCommand.ConfirmLegacyReset,
        ProfileModuleCommand.RetryLegacyPurge,
        -> 0
        is ProfileModuleCommand.ApplyGameplayProgress ->
            error("Gameplay progress is not a Session command mapping")
    }

private fun ProfileModuleResult.matches(command: ProfileModuleCommand): Boolean = when (command) {
    is ProfileModuleCommand.SelectCoreShape ->
        this is ProfileModuleResult.CoreShapeSelected && shape == command.shape
    ProfileModuleCommand.ToggleMute -> this is ProfileModuleResult.PreferencesChanged
    ProfileModuleCommand.AdvanceRebirth -> this is ProfileModuleResult.RebirthAdvanced
    ProfileModuleCommand.ConfirmLegacyReset ->
        this == ProfileModuleResult.ResetCompleted ||
            this is ProfileModuleResult.ResetWriteRejected ||
            this is ProfileModuleResult.ResetWriteResourceFailure ||
            this is ProfileModuleResult.ResetWriteOutcomeUnknown ||
            this is ProfileModuleResult.ResetNeedsAttention
    ProfileModuleCommand.RetryLegacyPurge ->
        this == ProfileModuleResult.ResetCompleted || this is ProfileModuleResult.ResetNeedsAttention
    is ProfileModuleCommand.ApplyGameplayProgress -> false
}

private val GameplayModuleCommand.effectiveIdentity: GameplayEffectiveProtocolIdentity
    get() = when (this) {
        GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
        GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
        GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
        GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
    }

private fun GameplayModuleResult.matches(command: GameplayModuleCommand): Boolean = when (command) {
    GameplayModuleCommand.StartRun -> this == GameplayModuleResult.RunStarted
    GameplayModuleCommand.PauseForOverlay -> this == GameplayModuleResult.OverlayPaused
    GameplayModuleCommand.ApplyPreferences -> this == GameplayModuleResult.PreferencesApplied
    GameplayModuleCommand.ExitRun -> this is GameplayModuleResult.RunExited
}

private val AppSessionOutput.dispatchOrder: Int
    get() = when (this) {
        is AppSessionOutput.EnsureGameplayRun -> 0
        is AppSessionOutput.SendProfileCommand,
        is AppSessionOutput.SendGameplayCommand,
        -> 1
        is AppSessionOutput.SynchronizeAudioPreferences,
        AppSessionOutput.PlayMuteFeedback,
        AppSessionOutput.PlayRebirthAcceptedFeedback,
        -> 2
    }

private val AppSessionOutput.isParticipantCommand: Boolean
    get() = this is AppSessionOutput.SendProfileCommand ||
        this is AppSessionOutput.SendGameplayCommand

private const val SESSION_COMPLETION_CAPACITY: Int = 8
private const val MAX_SESSION_CAUSAL_DEPTH: Int = 8
