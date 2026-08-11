// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommandAdmissionFailureReason
import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayCommandRefusalEvidence
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayCommandValidationFailureReason
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayModuleResultOutput
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayResultSourceToken
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayTargetBoundaryProvenance
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplaySessionRunPort
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.MAX_GAMEPLAY_OUTPUTS_PER_DECISION
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.GameplayProfileRoute
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, atomic publisher, and ordered-output dispatcher for one GameplayRun. */
internal class GameComponent private constructor(
    initialState: GameplayState,
    private val profilePort: GameplayProfileRoute,
    private val audioExecutor: GameplayAudioExecutor,
    private val commandResultSink: (GameplayModuleResultDelivery) -> Unit,
    private val seed: Int,
) : GameplaySessionRunPort, GameplayPresentationPort, GameplayInteractionPort {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions = gameplayCompletionDeque<GameplayWorkItem>()
    private var committedState: GameplayState = initialState
    private var interactionFxReducer: InteractionFxReducer? = null
    private var activeGameplayCommandRoute: GameplayCommandRouteReservation? = null
    private var activeProfileCommandRoute: GameplayProfileRouteReservation? = null
    private var nextLocalCausalScope: Long = 1L

    override val instanceId
        get() = committedState.instanceId

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
        dispatchLocal(pulse)

    override fun acceptFromSession(
        request: GameplayModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): GameplayCommandIngressResult {
        require(causalScope >= 0L) { "Trusted Gameplay route supplied a negative causal scope" }
        require(causalDepth >= 0) { "Trusted Gameplay route supplied a negative causal depth" }

        val commandSource = GameplayCommandSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = request.targetInstance,
            causalScope = causalScope,
            causalDepth = causalDepth,
        )
        val identity = identityFor(request.command)
        if (request.semanticHandle.sourceInstance != GameplayCommandSource.LocalSession) {
            return refused(
                commandSource,
                identity,
                GameplayCommandBoundaryResponse.ValidationFailure(
                    GameplayCommandValidationFailureReason.WrongSourceKind,
                ),
            )
        }
        if (request.targetInstance != committedState.instanceId) {
            return refused(
                commandSource,
                identity,
                GameplayCommandBoundaryResponse.ValidationFailure(
                    GameplayCommandValidationFailureReason.WrongTarget,
                ),
            )
        }
        if (
            dispatchGuard.isDispatching ||
            activeGameplayCommandRoute != null ||
            activeProfileCommandRoute != null ||
            !completions.isEmpty
        ) {
            return refused(
                commandSource,
                identity,
                GameplayCommandBoundaryResponse.AdmissionFailure(
                    GameplayCommandAdmissionFailureReason.CompletionCapacityExhausted,
                ),
            )
        }
        if (causalDepth >= MAX_GAMEPLAY_CAUSAL_DEPTH - 1) {
            return refused(commandSource, identity, causalBudgetFailure(commandSource))
        }
        if (!hasGameplayCommandRevisionCapacity(committedState.revision, request.command)) {
            return refused(
                commandSource,
                identity,
                GameplayCommandBoundaryResponse.AdmissionFailure(
                    GameplayCommandAdmissionFailureReason.RevisionCapacityExhausted,
                ),
            )
        }

        val context = trustedContextFor(request.command)
        val pulse = GameplayModuleCommandPulse(
            commandSource = commandSource,
            effectiveProtocolIdentity = identity,
            command = request.command,
            issuerProvenance = GameplayCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING,
        )
        activeGameplayCommandRoute = GameplayCommandRouteReservation(commandSource, identity)
        return dispatchCommand(pulse, context)
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayNucleus.query(committedState, query)

    override fun renderSnapshot(): GameplayRenderSnapshot =
        GameplayNucleus.renderSnapshot(committedState)

    override fun visualFxSnapshot(): VisualFxProjection =
        interactionFxReducer?.snapshot() ?: VisualFxProjection.EMPTY

    /** Trusted binding validation precedes caller-owned ModuleResultPulse construction. */
    internal fun receiveProfileModuleResult(delivery: ProfileModuleResultDelivery) {
        check(dispatchGuard.isDispatching) {
            "Inline Profile completion arrived outside its Gameplay causal scope"
        }
        val route = checkNotNull(activeProfileCommandRoute) {
            "Profile result arrived without a reserved Gameplay route"
        }
        val pending = checkNotNull(committedState.pendingProfileCommand) {
            "Profile result arrived without a pending Gameplay command"
        }
        check(pending.request == route.request)

        val expectedCommandSource = route.commandSource(profilePort)
        check(delivery.commandSource == expectedCommandSource) {
            "Profile result command-source correlation mismatch"
        }
        check(delivery.effectiveProtocolIdentity == ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS)
        check(delivery.issuerProvenance == ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING)
        check(delivery.result == ProfileModuleResult.GameplayProgressApplied)
        check(delivery.resultSource.semanticHandle == route.request.semanticHandle)
        check(delivery.resultSource.targetInstance == profilePort.instanceId)
        check(delivery.resultSource.sourceOrdinal == PROFILE_GAMEPLAY_RESULT_ORDINAL)
        check(delivery.resultSource.causalScope == route.causalScope)
        check(delivery.resultSource.causalDepth == route.sourceDepth + 1)

        check(route.delivery == null) { "Profile result route received more than one delivery" }
        route.delivery = delivery
    }

    internal fun stateSnapshot(): GameplayState = committedState

    private fun trustedContextFor(command: GameplayModuleCommand): GameplayContext = when (command) {
        GameplayModuleCommand.StartRun -> {
            val projection = profilePort.query(ProfileQuery.GetRunBootstrap)
            check(projection.instanceId == profilePort.instanceId) {
                "Profile bootstrap projection came from the wrong instance"
            }
            when (val result = projection.result) {
                is ProfileRunBootstrapResult.Ready -> GameplayContext(
                    start = GameplayStartContext.Ready(
                        GameplayStartInputs(
                            content = committedState.content,
                            profile = result.snapshot,
                            seed = seed,
                        ),
                    ),
                )
                is ProfileRunBootstrapResult.Unavailable -> GameplayContext(
                    start = GameplayStartContext.ProfileUnavailable,
                )
            }
        }
        GameplayModuleCommand.ApplyPreferences -> {
            val projection = profilePort.query(ProfileQuery.GetPreferences)
            check(projection.instanceId == profilePort.instanceId) {
                "Profile preferences projection came from the wrong instance"
            }
            GameplayContext(preferences = projection.preferences)
        }
        GameplayModuleCommand.PauseForOverlay,
        GameplayModuleCommand.ExitRun,
        -> GameplayContext.Empty
    }

    private fun dispatchLocal(pulse: GameplayInteractionPulse): GameplayAcceptance =
        dispatchGuard.dispatch {
            check(activeGameplayCommandRoute == null)
            check(activeProfileCommandRoute == null)
            check(completions.isEmpty) { "Gameplay completion deque leaked across dispatches" }
            check(committedState.revision.value <= Long.MAX_VALUE - MAX_LOCAL_REVISIONS_PER_DISPATCH) {
                "Gameplay local revision capacity exhausted before Intent construction"
            }
            val causalScope = allocateLocalCausalScope()
            check(
                completions.tryAddLast(
                    GameplayWorkItem(
                        pulse = GameplayNucleusPulse.Intent(pulse),
                        context = GameplayContext.Empty,
                        causalScope = causalScope,
                        causalDepth = 0,
                    ),
                ),
            )

            var rootAcceptance: GameplayAcceptance? = null
            var root = true
            var deferredFault: Throwable? = null
            while (!completions.isEmpty) {
                val item = checkNotNull(completions.removeFirstOrNull())
                val before = committedState
                when (val decision = GameplayNucleus.decide(before, item.pulse, item.context)) {
                    is GameplayDecision.Rejected -> {
                        check(root) {
                            "A trusted Gameplay completion was rejected: ${decision.reason}"
                        }
                        rootAcceptance = GameplayAcceptance.Rejected(
                            instanceId = before.instanceId,
                            observedRevision = before.revision,
                            reason = decision.reason,
                        )
                    }
                    is GameplayDecision.Accepted -> {
                        preflight(before, item, decision.frame)
                        committedState = decision.frame.nextState
                        initializeInteractionFxIfStarted(before, item)
                        if (root) {
                            rootAcceptance = GameplayAcceptance.Accepted(
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
            check(activeProfileCommandRoute == null)
            checkNotNull(rootAcceptance)
        }

    private fun dispatchCommand(
        pulse: GameplayModuleCommandPulse,
        context: GameplayContext,
    ): GameplayCommandIngressResult = dispatchGuard.dispatch {
        check(completions.isEmpty) { "Gameplay completion deque leaked across dispatches" }
        val targetDepth = pulse.commandSource.causalDepth + 1
        check(
            completions.tryAddLast(
                GameplayWorkItem(
                    pulse = GameplayNucleusPulse.ModuleCommand(pulse),
                    context = context,
                    causalScope = pulse.commandSource.causalScope,
                    causalDepth = targetDepth,
                ),
            ),
        )

        var acceptedTargetRevision: GameplayRevision? = null
        var root = true
        var deferredFault: Throwable? = null
        while (!completions.isEmpty) {
            val item = checkNotNull(completions.removeFirstOrNull())
            val before = committedState
            when (val decision = GameplayNucleus.decide(before, item.pulse, item.context)) {
                is GameplayDecision.Rejected -> {
                    check(root) {
                        "A trusted Gameplay completion was rejected: ${decision.reason}"
                    }
                    activeGameplayCommandRoute = null
                    return@dispatch refused(
                        pulse.commandSource,
                        pulse.effectiveProtocolIdentity,
                        GameplayCommandBoundaryResponse.DecisionRejected(decision.reason),
                    )
                }
                is GameplayDecision.Accepted -> {
                    if (root && deepestReservedLevel(item, decision.frame) >= MAX_GAMEPLAY_CAUSAL_DEPTH) {
                        activeGameplayCommandRoute = null
                        return@dispatch refused(
                            pulse.commandSource,
                            pulse.effectiveProtocolIdentity,
                            causalBudgetFailure(pulse.commandSource),
                        )
                    }
                    preflight(before, item, decision.frame)
                    committedState = decision.frame.nextState
                    initializeInteractionFxIfStarted(before, item)
                    if (root) acceptedTargetRevision = committedState.revision
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
        check(activeProfileCommandRoute == null) {
            "Accepted Gameplay frame left an unresolved Profile route"
        }
        check(activeGameplayCommandRoute == null) {
            "Accepted inline Gameplay command completed without its one-shot result"
        }
        GameplayCommandIngressResult.Accepted(
            targetInstance = committedState.instanceId,
            targetRevision = checkNotNull(acceptedTargetRevision),
        )
    }

    private fun preflight(
        before: GameplayState,
        item: GameplayWorkItem,
        frame: GameplayAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Gameplay instance identity changed" }
        check(next.content === before.content) { "Captured Gameplay content identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Gameplay revision must advance exactly once"
        }
        val renderSnapshot = GameplayNucleus.renderSnapshot(next)
        check(renderSnapshot.instanceId == next.instanceId)
        check(renderSnapshot.revision == next.revision)
        check((renderSnapshot.renderModel == null) == (next.engine == null))
        renderSnapshot.renderModel?.let { render ->
            check(render.content === next.content)
            val expectedRenderPhase = when (next.phase) {
                GameplayRunPhase.CREATED -> error("Created GameplayRun cannot expose a render model")
                GameplayRunPhase.RUNNING -> GamePhase.RUNNING
                GameplayRunPhase.PAUSED -> GamePhase.PAUSED
                GameplayRunPhase.CHOICE -> GamePhase.CHOICE
                GameplayRunPhase.GAME_OVER -> GamePhase.GAME_OVER
                GameplayRunPhase.VICTORY -> GamePhase.VICTORY
                GameplayRunPhase.EXITED -> GamePhase.PAUSED
            }
            check(render.phase == expectedRenderPhase)
        }
        check(frame.outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION)
        check(frame.outputs.zipWithNext().all { (left, right) ->
            left.dispatchOrder <= right.dispatchOrder
        }) { "Gameplay outputs are not in FX -> Profile -> Audio -> result order" }

        val profileOutputs = frame.outputs.filterIsInstance<GameplayOutput.SendProfileCommand>()
        requireGameplayProfileOutputFanoutBound(profileOutputs.size)
        if (profileOutputs.isNotEmpty()) {
            check(before.pendingProfileCommand == null)
            requireGameplayCompletionCapacity(completions.remainingCapacity, requiredCompletions = 1)
            val output = profileOutputs.single()
            val outputIndex = frame.outputs.indexOf(output)
            val pending = checkNotNull(next.pendingProfileCommand)
            check(pending.request == output.request)
            check(output.request.targetInstance == profilePort.instanceId)
            check(output.request.command is ProfileModuleCommand.ApplyGameplayProgress)
            check(output.request.sourceOrdinal == outputIndex)
            check(output.request.semanticHandle.sourceOrdinal == outputIndex)
            check(output.request.semanticHandle.sourceRevision == next.revision.value)
            check(
                output.request.semanticHandle.sourceInstance ==
                    ProfileCommandSource.GameplayRun(next.instanceId.runId.value),
            )
        } else if (before.pendingProfileCommand == null) {
            check(next.pendingProfileCommand == null)
        }

        frame.outputs.forEachIndexed { index, output ->
            when (output) {
                is GameplayOutput.CompleteCommand -> {
                    val route = checkNotNull(activeGameplayCommandRoute)
                    check(output.result.commandSource == route.commandSource)
                    check(output.result.semanticHandle == route.commandSource.semanticHandle)
                    check(output.result.sourceOrdinal == index)
                    check(resultMatches(route.effectiveProtocolIdentity, output.result.result))
                    check(index == frame.outputs.lastIndex)
                    check(item.causalScope == route.commandSource.causalScope)
                }
                is GameplayOutput.SendProfileCommand -> Unit
                is GameplayOutput.AdvanceAudio,
                is GameplayOutput.EmitVisualFx,
                GameplayOutput.EnsureAudioUnlocked,
                -> Unit
            }
        }
    }

    private fun initializeInteractionFxIfStarted(
        before: GameplayState,
        item: GameplayWorkItem,
    ) {
        if (before.phase != GameplayRunPhase.CREATED) return
        val command = (item.pulse as? GameplayNucleusPulse.ModuleCommand)?.pulse?.command
        if (command != GameplayModuleCommand.StartRun) return
        check(interactionFxReducer == null)
        interactionFxReducer = InteractionFxReducer(
            (checkNotNull(item.context.start) as GameplayStartContext.Ready).inputs.seed,
        )
    }

    private fun execute(output: GameplayOutput, item: GameplayWorkItem) {
        when (output) {
            is GameplayOutput.EmitVisualFx ->
                checkNotNull(interactionFxReducer).apply(output.cues)
            is GameplayOutput.SendProfileCommand -> executeProfileCommand(output, item)
            is GameplayOutput.AdvanceAudio ->
                audioExecutor.advance(output.realDeltaSeconds, output.cues)
            GameplayOutput.EnsureAudioUnlocked ->
                audioExecutor.ensureUnlocked()
            is GameplayOutput.CompleteCommand -> dispatchCommandResult(output.result, item)
        }
    }

    private fun executeProfileCommand(
        output: GameplayOutput.SendProfileCommand,
        item: GameplayWorkItem,
    ) {
        check(activeProfileCommandRoute == null)
        val route = GameplayProfileRouteReservation(
            request = output.request,
            causalScope = item.causalScope,
            sourceDepth = item.causalDepth,
        )
        activeProfileCommandRoute = route
        var invocationFailure: Throwable? = null
        val ingress = try {
            profilePort.acceptFromGameplay(
                request = output.request,
                causalScope = item.causalScope,
                causalDepth = item.causalDepth,
            )
        } catch (failure: Throwable) {
            invocationFailure = failure
            null
        }
        when (ingress) {
            is ProfileCommandIngressResult.Accepted -> {
                check(ingress.targetInstance == profilePort.instanceId)
                val delivery = checkNotNull(route.delivery) {
                    "Accepted inline Profile command returned without its reserved result"
                }
                enqueueProfileResult(route, delivery, ingress.targetRevision)
                check(!completions.isEmpty) {
                    "Accepted inline Profile result was not retained by Gameplay"
                }
            }
            is ProfileCommandIngressResult.RejectedBeforeAcceptance -> {
                check(activeProfileCommandRoute === route)
                check(route.delivery == null) {
                    "Profile route returned a pre-acceptance refusal after delivering a result"
                }
                validateProfileRefusal(route, ingress)
                activeProfileCommandRoute = null
                val refusal = ingress.refusal
                enqueueCompletion(
                    pulse = GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance(
                        commandSource = refusal.commandSource,
                        effectiveProtocolIdentity = refusal.effectiveProtocolIdentity,
                        boundaryResponse = refusal.boundaryResponse,
                        targetBoundaryProvenance = refusal.targetBoundaryProvenance,
                    ),
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth + 1,
                )
            }
            null -> {
                route.delivery?.let { delivery ->
                    // A verified target-owned result frame is authoritative even when the
                    // same-stack invocation faults after delivering it.
                    enqueueProfileResult(route, delivery, acceptedTargetRevision = null)
                }
                throw checkNotNull(invocationFailure)
            }
        }
    }

    private fun enqueueProfileResult(
        route: GameplayProfileRouteReservation,
        delivery: ProfileModuleResultDelivery,
        acceptedTargetRevision: ProfileRevision?,
    ) {
        check(activeProfileCommandRoute === route)
        if (acceptedTargetRevision != null) {
            check(delivery.resultSource.targetRevision == acceptedTargetRevision) {
                "Profile result did not name the accepted target frame"
            }
        }
        activeProfileCommandRoute = null
        enqueueCompletion(
            pulse = GameplayNucleusPulse.ProfileModuleResultPulse(
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

    private fun validateProfileRefusal(
        route: GameplayProfileRouteReservation,
        ingress: ProfileCommandIngressResult.RejectedBeforeAcceptance,
    ) {
        val refusal = ingress.refusal
        check(refusal.commandSource == route.commandSource(profilePort))
        check(refusal.effectiveProtocolIdentity == ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS)
        check(refusal.targetBoundaryProvenance.targetInstance == profilePort.instanceId)
        check(
            refusal.targetBoundaryProvenance.effectiveProtocolIdentity ==
                ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
        )
        val admission = refusal.boundaryResponse as? ProfileCommandBoundaryResponse.AdmissionFailure
        val budget = admission?.reason as? ProfileCommandAdmissionFailureReason.CausalBudgetExceeded
        if (budget != null) check(budget.causalScope == route.causalScope)
    }

    private fun dispatchCommandResult(
        output: GameplayModuleResultOutput,
        item: GameplayWorkItem,
    ) {
        val route = checkNotNull(activeGameplayCommandRoute) {
            "Gameplay result has no reserved command route"
        }
        check(route.commandSource == output.commandSource)
        check(route.commandSource.causalScope == item.causalScope)
        check(resultMatches(route.effectiveProtocolIdentity, output.result))
        activeGameplayCommandRoute = null
        commandResultSink(
            GameplayModuleResultDelivery(
                commandSource = output.commandSource,
                resultSource = GameplayResultSourceToken(
                    semanticHandle = output.semanticHandle,
                    targetInstance = committedState.instanceId,
                    targetRevision = committedState.revision,
                    sourceOrdinal = output.sourceOrdinal,
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth,
                ),
                effectiveProtocolIdentity = route.effectiveProtocolIdentity,
                result = output.result,
                issuerProvenance = GameplayResultIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
            ),
        )
    }

    private fun enqueueCompletion(
        pulse: GameplayNucleusPulse,
        causalScope: Long,
        causalDepth: Int,
    ) {
        requireGameplayCausalDepth(causalDepth)
        check(
            completions.tryAddLast(
                GameplayWorkItem(
                    pulse = pulse,
                    context = GameplayContext.Empty,
                    causalScope = causalScope,
                    causalDepth = causalDepth,
                ),
            ),
        ) { "Pre-reserved Gameplay completion could not be retained" }
    }

    private fun deepestReservedLevel(
        item: GameplayWorkItem,
        frame: GameplayAcceptedFrame,
    ): Int {
        var deepest = item.causalDepth
        if (frame.outputs.any { it is GameplayOutput.CompleteCommand }) {
            deepest = maxOf(deepest, item.causalDepth + 1)
        }
        if (frame.outputs.any { it is GameplayOutput.SendProfileCommand }) {
            val returnsCommandResult = frame.nextState.pendingProfileCommand?.exitCompletion != null
            deepest = maxOf(
                deepest,
                item.causalDepth + if (returnsCommandResult) 3 else 2,
            )
        }
        return deepest
    }

    private fun refused(
        commandSource: GameplayCommandSourceToken,
        effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
        response: GameplayCommandBoundaryResponse,
    ): GameplayCommandIngressResult.RejectedBeforeAcceptance =
        GameplayCommandIngressResult.RejectedBeforeAcceptance(
            GameplayCommandRefusalEvidence(
                commandSource = commandSource,
                effectiveProtocolIdentity = effectiveProtocolIdentity,
                boundaryResponse = response,
                targetBoundaryProvenance = GameplayTargetBoundaryProvenance(
                    targetInstance = committedState.instanceId,
                    effectiveProtocolIdentity = effectiveProtocolIdentity,
                ),
            ),
        )

    private fun causalBudgetFailure(
        commandSource: GameplayCommandSourceToken,
    ): GameplayCommandBoundaryResponse.AdmissionFailure =
        GameplayCommandBoundaryResponse.AdmissionFailure(
            GameplayCommandAdmissionFailureReason.CausalBudgetExceeded(
                causalScope = commandSource.causalScope,
                limit = MAX_GAMEPLAY_CAUSAL_DEPTH,
            ),
        )

    private fun allocateLocalCausalScope(): Long {
        check(nextLocalCausalScope < Long.MAX_VALUE) { "Gameplay local causal scope exhausted" }
        return nextLocalCausalScope++
    }

    companion object {
        fun create(
            runId: RunId,
            content: GameplayContentSnapshot,
            profilePort: GameplayProfileRoute,
            audioExecutor: GameplayAudioExecutor,
            commandResultSink: (GameplayModuleResultDelivery) -> Unit,
            seed: Int,
        ): GameComponent = GameComponent(
            initialState = GameplayState.initial(runId, content),
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            commandResultSink = commandResultSink,
            seed = seed,
        )
    }
}

internal fun <T> gameplayCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(GAMEPLAY_COMPLETION_CAPACITY)

internal fun requireGameplayCausalDepth(causalDepth: Int) {
    check(causalDepth >= 0 && causalDepth < MAX_GAMEPLAY_CAUSAL_DEPTH) {
        "Gameplay causal depth exhausted before acceptance"
    }
}

internal fun requireGameplayProfileOutputFanoutBound(profileCommandCount: Int) {
    check(profileCommandCount in 0..1) {
        "A Gameplay decision may issue at most one Profile command"
    }
}

internal fun requireGameplayCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Gameplay completion capacity exhausted before acceptance"
    }
}

internal fun hasGameplayCommandRevisionCapacity(
    revision: GameplayRevision,
    command: GameplayModuleCommand,
): Boolean {
    val required = if (command == GameplayModuleCommand.ExitRun) {
        MAX_EXIT_REVISIONS_PER_DISPATCH
    } else {
        1L
    }
    return revision.value <= Long.MAX_VALUE - required
}

private fun identityFor(command: GameplayModuleCommand): GameplayEffectiveProtocolIdentity =
    when (command) {
        GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
        GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
        GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
        GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
    }

private fun resultMatches(
    identity: GameplayEffectiveProtocolIdentity,
    result: GameplayModuleResult,
): Boolean = when (identity) {
    GameplayEffectiveProtocolIdentity.SESSION_START -> result == GameplayModuleResult.RunStarted
    GameplayEffectiveProtocolIdentity.SESSION_PAUSE -> result == GameplayModuleResult.OverlayPaused
    GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES ->
        result == GameplayModuleResult.PreferencesApplied
    GameplayEffectiveProtocolIdentity.SESSION_EXIT -> result is GameplayModuleResult.RunExited
}

private data class GameplayWorkItem(
    val pulse: GameplayNucleusPulse,
    val context: GameplayContext,
    val causalScope: Long,
    val causalDepth: Int,
)

private data class GameplayCommandRouteReservation(
    val commandSource: GameplayCommandSourceToken,
    val effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
)

private class GameplayProfileRouteReservation(
    val request: ProfileModuleCommandRequest,
    val causalScope: Long,
    val sourceDepth: Int,
) {
    var delivery: ProfileModuleResultDelivery? = null

    fun commandSource(profilePort: GameplayProfileRoute): ProfileCommandSourceToken =
        ProfileCommandSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = profilePort.instanceId,
            causalScope = causalScope,
            causalDepth = sourceDepth,
        )
}

private val GameplayOutput.dispatchOrder: Int
    get() = when (this) {
        is GameplayOutput.EmitVisualFx -> 0
        is GameplayOutput.SendProfileCommand -> 1
        is GameplayOutput.AdvanceAudio,
        GameplayOutput.EnsureAudioUnlocked,
        -> 2
        is GameplayOutput.CompleteCommand -> 3
    }

private const val GAMEPLAY_COMPLETION_CAPACITY: Int = 8
private const val MAX_GAMEPLAY_CAUSAL_DEPTH: Int = 8
private const val PROFILE_GAMEPLAY_RESULT_ORDINAL: Int = 1
private const val MAX_LOCAL_REVISIONS_PER_DISPATCH: Long = 2L
private const val MAX_EXIT_REVISIONS_PER_DISPATCH: Long = 2L
