// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandIssuerProvenance
import kinetickk.ball.profile.api.ProfileCommandRefusalEvidence
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileCommandValidationFailureReason
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandPulse
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfileModuleResultOutput
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.nucleus.MAX_PROFILE_OUTPUTS_PER_DECISION
import kinetickk.ball.profile.nucleus.ProfileAcceptedFrame
import kinetickk.ball.profile.nucleus.ProfileDecision
import kinetickk.ball.profile.nucleus.ProfileNucleus
import kinetickk.ball.profile.nucleus.ProfileNucleusPulse
import kinetickk.ball.profile.nucleus.ProfileOutput
import kinetickk.ball.profile.nucleus.ProfileState
import kinetickk.ball.profile.resource.ProfileResource
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, publisher, and output dispatcher for the local Profile instance. */
internal class DefaultProfileComponent(
    private val resource: ProfileResource,
    policy: ProfilePolicySnapshot,
    private val commandResultSink: (ProfileModuleResultDelivery) -> Unit = {},
) : ProfileComponent {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions = profileCompletionDeque<ProfileWorkItem>()
    private var committedState: ProfileState = ProfileState.initial(
        policy = policy,
        snapshotReadResult = readConstructionSnapshot(resource),
    )
    private var activeCommandRoute: ProfileCommandRouteReservation? = null
    private var nextLocalCausalScope: Long = 1L

    override val instanceId: ProfileInstanceId
        get() = committedState.instanceId

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance =
        dispatchLocal(pulse)

    override fun acceptFromSession(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult = acceptCommand(
        request,
        causalScope,
        causalDepth,
        ProfileIngressSource.Session,
    )

    override fun acceptFromGameplay(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult = acceptCommand(
        request,
        causalScope,
        causalDepth,
        ProfileIngressSource.Gameplay,
    )

    private fun acceptCommand(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
        ingressSource: ProfileIngressSource,
    ): ProfileCommandIngressResult {
        require(causalScope >= 0L) { "Trusted Profile route supplied a negative causal scope" }
        require(causalDepth >= 0) { "Trusted Profile route supplied a negative causal depth" }

        val commandSource = ProfileCommandSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = request.targetInstance,
            causalScope = causalScope,
            causalDepth = causalDepth,
        )
        val binding = bindingFor(request, ingressSource)
            ?: return refused(
                commandSource = commandSource,
                effectiveProtocolIdentity = fallbackIdentity(request.command),
                response = ProfileCommandBoundaryResponse.ValidationFailure(
                    ProfileCommandValidationFailureReason.WRONG_SOURCE_KIND,
                ),
            )
        if (request.targetInstance != committedState.instanceId) {
            return refused(
                commandSource = commandSource,
                effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
                response = ProfileCommandBoundaryResponse.ValidationFailure(
                    ProfileCommandValidationFailureReason.WRONG_TARGET,
                ),
            )
        }
        if (
            dispatchGuard.isDispatching ||
            activeCommandRoute != null ||
            !completions.isEmpty
        ) {
            return refused(
                commandSource = commandSource,
                effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
                response = ProfileCommandBoundaryResponse.AdmissionFailure(
                    ProfileCommandAdmissionFailureReason.CompletionCapacityExhausted,
                ),
            )
        }
        if (causalDepth >= MAX_PROFILE_CAUSAL_DEPTH - 1) {
            return refused(
                commandSource = commandSource,
                effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
                response = causalBudgetFailure(commandSource),
            )
        }
        val hasRevisionCapacity = hasProfileCommandRevisionCapacity(committedState.revision)
        if (!hasRevisionCapacity) {
            return refused(
                commandSource = commandSource,
                effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
                response = ProfileCommandBoundaryResponse.AdmissionFailure(
                    ProfileCommandAdmissionFailureReason.RevisionCapacityExhausted,
                ),
            )
        }

        val pulse = ProfileModuleCommandPulse(
            commandSource = commandSource,
            effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
            command = request.command,
            issuerProvenance = binding.issuerProvenance,
        )
        activeCommandRoute = ProfileCommandRouteReservation(
            commandSource = commandSource,
            effectiveProtocolIdentity = binding.effectiveProtocolIdentity,
        )
        return dispatchCommand(pulse)
    }

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetCollection): CollectionProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection =
        ProfileNucleus.query(committedState, query)

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
        ProfileNucleus.query(committedState, query)

    internal fun stateSnapshot(): ProfileState = committedState

    private fun dispatchLocal(pulse: ProfilePulse.Business): ProfileAcceptance = dispatchGuard.dispatch {
        check(activeCommandRoute == null) { "A local Profile intent crossed an active command route" }
        check(completions.isEmpty) { "Profile completion deque leaked across dispatches" }
        check(committedState.revision.value <= Long.MAX_VALUE - MAX_LOCAL_REVISIONS_PER_DISPATCH) {
            "Profile local revision capacity exhausted before Intent construction"
        }
        val causalScope = allocateLocalCausalScope()
        check(
            completions.tryAddLast(
                ProfileWorkItem(ProfileNucleusPulse.Intent(pulse), causalScope, causalDepth = 0),
            ),
        )

        var rootAcceptance: ProfileAcceptance? = null
        var root = true
        var deferredFault: Throwable? = null
        while (!completions.isEmpty) {
            val item = checkNotNull(completions.removeFirstOrNull())
            val before = committedState
            when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                is ProfileDecision.Rejected -> {
                    check(root) { "A trusted Profile Resource completion was rejected: " + decision.reason }
                    rootAcceptance = ProfileAcceptance.Rejected(
                        instanceId = before.instanceId,
                        observedRevision = before.revision,
                        reason = decision.reason,
                    )
                }
                is ProfileDecision.Accepted -> {
                    preflight(before, item, decision.frame)
                    committedState = decision.frame.nextState
                    if (root) {
                        rootAcceptance = ProfileAcceptance.Accepted(
                            instanceId = committedState.instanceId,
                            revision = committedState.revision,
                        )
                    }
                    for (output in decision.frame.outputs) {
                        try {
                            this.execute(output, item)
                        } catch (failure: Throwable) {
                            if (deferredFault == null) deferredFault = failure
                        }
                    }
                }
            }
            root = false
        }
        val failure = deferredFault
        if (failure != null) throw failure
        check(activeCommandRoute == null)
        checkNotNull(rootAcceptance)
    }

    private fun dispatchCommand(pulse: ProfileModuleCommandPulse): ProfileCommandIngressResult =
        dispatchGuard.dispatch {
            check(completions.isEmpty) { "Profile completion deque leaked across dispatches" }
            val targetDepth = pulse.commandSource.causalDepth + 1
            check(
                completions.tryAddLast(
                    ProfileWorkItem(
                        pulse = ProfileNucleusPulse.ModuleCommand(pulse),
                        causalScope = pulse.commandSource.causalScope,
                        causalDepth = targetDepth,
                    ),
                ),
            )

            var acceptedTargetRevision: ProfileRevision? = null
            var root = true
            var deferredFault: Throwable? = null
            while (!completions.isEmpty) {
                val item = checkNotNull(completions.removeFirstOrNull())
                val before = committedState
                when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                    is ProfileDecision.Rejected -> {
                        check(root) {
                            "A trusted Profile Resource completion was rejected: " + decision.reason
                        }
                        activeCommandRoute = null
                        return@dispatch refused(
                            commandSource = pulse.commandSource,
                            effectiveProtocolIdentity = pulse.effectiveProtocolIdentity,
                            response = ProfileCommandBoundaryResponse.DecisionRejected(decision.reason),
                        )
                    }
                    is ProfileDecision.Accepted -> {
                        if (root && deepestReservedLevel(item, decision.frame) >= MAX_PROFILE_CAUSAL_DEPTH) {
                            activeCommandRoute = null
                            return@dispatch refused(
                                commandSource = pulse.commandSource,
                                effectiveProtocolIdentity = pulse.effectiveProtocolIdentity,
                                response = causalBudgetFailure(pulse.commandSource),
                            )
                        }
                        preflight(before, item, decision.frame)
                        committedState = decision.frame.nextState
                        if (root) acceptedTargetRevision = committedState.revision
                        for (output in decision.frame.outputs) {
                            try {
                                this.execute(output, item)
                            } catch (failure: Throwable) {
                                if (deferredFault == null) deferredFault = failure
                            }
                        }
                    }
                }
                root = false
            }

            val failure = deferredFault
            if (failure != null) throw failure
            check(activeCommandRoute == null) {
                "Accepted inline Profile command completed without its one-shot result"
            }
            ProfileCommandIngressResult.Accepted(
                targetInstance = committedState.instanceId,
                targetRevision = checkNotNull(acceptedTargetRevision),
            )
        }

    private fun preflight(
        before: ProfileState,
        item: ProfileWorkItem,
        frame: ProfileAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Profile instance identity changed" }
        check(next.policy === before.policy) { "Captured Profile policy identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Profile revision must advance exactly once"
        }
        check(frame.outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile output limit exceeded"
        }

        val synchronousCompletions = frame.outputs.count { output ->
            output is ProfileOutput.PersistSnapshot
        }
        requireProfileSynchronousResourceEffectBound(synchronousCompletions)
        if (synchronousCompletions > 0) {
            requireProfileCausalDepth(item.causalDepth + 1)
            requireProfileCompletionCapacity(completions.remainingCapacity, synchronousCompletions)
        }

        frame.outputs.forEachIndexed { index, output ->
            when (output) {
                is ProfileOutput.PersistSnapshot -> {
                    check(output.effectRef.sourceRevision == next.revision)
                    check(output.snapshot.revision == next.revision)
                    check(output.effectRef.ordinal == index) {
                        "Profile EffectRequest ordinal must equal its accepted output position"
                    }
                }
                is ProfileOutput.CompleteCommand -> {
                    check(output.result.semanticHandle == output.result.commandSource.semanticHandle)
                    check(output.result.sourceOrdinal == index)
                    check(index == frame.outputs.lastIndex) {
                        "Profile command completion must be the final ordered output"
                    }
                    val route = checkNotNull(activeCommandRoute)
                    check(route.commandSource == output.result.commandSource)
                    check(route.commandSource.causalScope == item.causalScope)
                    check(profileResultMatches(route.effectiveProtocolIdentity, output.result.result)) {
                        "Profile result does not match its effective protocol identity"
                    }
                }
            }
        }
    }

    private fun execute(output: ProfileOutput, item: ProfileWorkItem) {
        when (output) {
            is ProfileOutput.PersistSnapshot -> {
                val result = resource.writeSnapshot(output.snapshot)
                validateWriteCompletion(output, result)
                enqueueCompletion(
                    pulse = ProfileNucleusPulse.WriteCompleted(output.effectRef, result),
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth + 1,
                )
            }
            is ProfileOutput.CompleteCommand -> dispatchCommandResult(output.result, item)
        }
    }

    private fun dispatchCommandResult(
        output: ProfileModuleResultOutput,
        item: ProfileWorkItem,
    ) {
        val route = checkNotNull(activeCommandRoute) {
            "Profile result has no reserved command route"
        }
        check(route.commandSource == output.commandSource) {
            "Profile result command correlation mismatch"
        }
        check(route.commandSource.causalScope == item.causalScope) {
            "Profile result changed its causal scope"
        }
        check(profileResultMatches(route.effectiveProtocolIdentity, output.result)) {
            "Profile result does not match its effective protocol identity"
        }
        activeCommandRoute = null
        commandResultSink(
            ProfileModuleResultDelivery(
                commandSource = output.commandSource,
                resultSource = ProfileResultSourceToken(
                    semanticHandle = output.semanticHandle,
                    targetInstance = committedState.instanceId,
                    targetRevision = committedState.revision,
                    sourceOrdinal = output.sourceOrdinal,
                    causalScope = item.causalScope,
                    causalDepth = item.causalDepth,
                ),
                effectiveProtocolIdentity = route.effectiveProtocolIdentity,
                result = output.result,
                issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
            ),
        )
    }

    /** Trusted Resource boundary validation precedes Fact construction. */
    private fun validateWriteCompletion(
        output: ProfileOutput.PersistSnapshot,
        result: ProfileWriteResult,
    ) {
        val pending = checkNotNull(committedState.persistence as? ProfilePersistenceStatus.Pending) {
            "Profile Resource returned a write completion with no accepted effect"
        }
        check(pending.effectRef == output.effectRef) {
            "Profile Resource write completion effect correlation mismatch"
        }
        if (result is ProfileWriteResult.Written) {
            check(result.revision == pending.snapshotRevision) {
                "Profile Resource write completion revision mismatch"
            }
        }
    }

    private fun enqueueCompletion(
        pulse: ProfileNucleusPulse.Fact,
        causalScope: Long,
        causalDepth: Int,
    ) {
        requireProfileCausalDepth(causalDepth)
        check(
            completions.tryAddLast(ProfileWorkItem(pulse, causalScope, causalDepth)),
        ) { "Pre-reserved Profile completion could not be retained" }
    }

    private fun deepestReservedLevel(
        item: ProfileWorkItem,
        frame: ProfileAcceptedFrame,
    ): Int {
        var deepest = item.causalDepth
        if (frame.outputs.any { it is ProfileOutput.PersistSnapshot }) {
            deepest += 1
        }
        if (frame.outputs.any { it is ProfileOutput.CompleteCommand }) {
            deepest = maxOf(deepest, item.causalDepth + 1)
        }
        return deepest
    }

    private fun bindingFor(
        request: ProfileModuleCommandRequest,
        ingressSource: ProfileIngressSource,
    ): ProfileCommandBinding? = when (ingressSource) {
        ProfileIngressSource.Session -> when (request.command) {
            is ProfileModuleCommand.SelectCoreShape -> request.sessionBinding(
                ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE,
            )
            ProfileModuleCommand.ToggleMute -> request.sessionBinding(
                ProfileEffectiveProtocolIdentity.SESSION_MUTE,
            )
            ProfileModuleCommand.AdvanceRebirth -> request.sessionBinding(
                ProfileEffectiveProtocolIdentity.SESSION_REBIRTH,
            )
            is ProfileModuleCommand.ApplyGameplayProgress -> null
        }
        ProfileIngressSource.Gameplay -> when (request.command) {
            is ProfileModuleCommand.ApplyGameplayProgress ->
                if (request.semanticHandle.sourceInstance is ProfileCommandSource.GameplayRun) {
                    ProfileCommandBinding(
                        ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                        ProfileCommandIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
                    )
                } else null
            is ProfileModuleCommand.SelectCoreShape,
            ProfileModuleCommand.ToggleMute,
            ProfileModuleCommand.AdvanceRebirth,
            -> null
        }
    }

    private fun ProfileModuleCommandRequest.sessionBinding(
        identity: ProfileEffectiveProtocolIdentity,
    ): ProfileCommandBinding? = if (semanticHandle.sourceInstance == ProfileCommandSource.LocalSession) {
        ProfileCommandBinding(identity, ProfileCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING)
    } else {
        null
    }

    private fun fallbackIdentity(command: ProfileModuleCommand): ProfileEffectiveProtocolIdentity =
        when (command) {
            is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
            ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
            ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
            is ProfileModuleCommand.ApplyGameplayProgress -> ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS
        }

    private fun refused(
        commandSource: ProfileCommandSourceToken,
        effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
        response: ProfileCommandBoundaryResponse,
    ): ProfileCommandIngressResult.RejectedBeforeAcceptance =
        ProfileCommandIngressResult.RejectedBeforeAcceptance(
            ProfileCommandRefusalEvidence(
                commandSource = commandSource,
                effectiveProtocolIdentity = effectiveProtocolIdentity,
                boundaryResponse = response,
                targetBoundaryProvenance = ProfileTargetBoundaryProvenance(
                    targetInstance = committedState.instanceId,
                    effectiveProtocolIdentity = effectiveProtocolIdentity,
                ),
            ),
        )

    private fun causalBudgetFailure(
        commandSource: ProfileCommandSourceToken,
    ): ProfileCommandBoundaryResponse.AdmissionFailure =
        ProfileCommandBoundaryResponse.AdmissionFailure(
            ProfileCommandAdmissionFailureReason.CausalBudgetExceeded(
                causalScope = commandSource.causalScope,
                limit = MAX_PROFILE_CAUSAL_DEPTH,
            ),
        )

    private fun allocateLocalCausalScope(): Long {
        check(nextLocalCausalScope < Long.MAX_VALUE) { "Profile local causal scope exhausted" }
        return nextLocalCausalScope++
    }

}

internal fun profileResultMatches(
    identity: ProfileEffectiveProtocolIdentity,
    result: ProfileModuleResult,
): Boolean = when (identity) {
    ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE ->
        result is ProfileModuleResult.CoreShapeSelected
    ProfileEffectiveProtocolIdentity.SESSION_MUTE ->
        result is ProfileModuleResult.PreferencesChanged
    ProfileEffectiveProtocolIdentity.SESSION_REBIRTH ->
        result is ProfileModuleResult.RebirthAdvanced
    ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS ->
        result == ProfileModuleResult.GameplayProgressApplied
}

internal fun <T> profileCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(PROFILE_COMPLETION_CAPACITY)

internal fun requireProfileCausalDepth(causalDepth: Int) {
    check(causalDepth >= 0 && causalDepth < MAX_PROFILE_CAUSAL_DEPTH) {
        "Profile causal depth exhausted before acceptance"
    }
}

internal fun requireProfileSynchronousResourceEffectBound(effectCount: Int) {
    check(effectCount in 0..1) {
        "A Profile Decision may issue at most one synchronous Resource effect"
    }
}

internal fun requireProfileCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Profile completion capacity exhausted before acceptance"
    }
}

internal fun hasProfileCommandRevisionCapacity(
    revision: ProfileRevision,
): Boolean = revision.value <= Long.MAX_VALUE - MAX_ORDINARY_REVISIONS_PER_DISPATCH

private fun readConstructionSnapshot(resource: ProfileResource): ProfileSnapshotReadResult =
    resource.readSnapshot()

private data class ProfileWorkItem(
    val pulse: ProfileNucleusPulse,
    val causalScope: Long,
    val causalDepth: Int,
)

private data class ProfileCommandBinding(
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val issuerProvenance: ProfileCommandIssuerProvenance,
)

private enum class ProfileIngressSource { Session, Gameplay }

private data class ProfileCommandRouteReservation(
    val commandSource: ProfileCommandSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
)

private const val PROFILE_COMPLETION_CAPACITY: Int = 8
private const val MAX_PROFILE_CAUSAL_DEPTH: Int = 8
private const val MAX_LOCAL_REVISIONS_PER_DISPATCH: Long = 2L
private const val MAX_ORDINARY_REVISIONS_PER_DISPATCH: Long = 2L
