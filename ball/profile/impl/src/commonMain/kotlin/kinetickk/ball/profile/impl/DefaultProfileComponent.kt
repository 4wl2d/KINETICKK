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
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.nucleus.MAX_PROFILE_OUTPUTS_PER_DECISION
import kinetickk.ball.profile.nucleus.ProfileAcceptedFrame
import kinetickk.ball.profile.nucleus.ProfileContext
import kinetickk.ball.profile.nucleus.ProfileDecision
import kinetickk.ball.profile.nucleus.ProfileNucleus
import kinetickk.ball.profile.nucleus.ProfileOutput
import kinetickk.ball.profile.nucleus.ProfileState
import kinetickk.ball.profile.resource.ProfileResource
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, publisher, and output dispatcher for the local Profile instance. */
internal class DefaultProfileComponent(
    private val resource: ProfileResource,
    policy: ProfilePolicySnapshot,
    private val commandResultSink: (ProfileCommandResult.Accepted) -> Unit = {},
) : ProfilePort {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions = profileCompletionDeque<ProfileWorkItem>()
    private var committedState: ProfileState = ProfileState.initial(policy)

    override val instanceId: ProfileInstanceId
        get() = committedState.instanceId

    init {
        val bootstrap = try {
            resource.readBootstrap()
        } catch (_: Throwable) {
            ProfileBootstrapResourceResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_READ_FAILED,
            )
        }
        dispatchInternal(ProfilePulse.BootstrapCompleted(bootstrap))
    }

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance =
        requireNotNull(dispatchRoot(pulse, ProfileContext.Local, reportAcceptance = true))

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance = requireNotNull(
        dispatchRoot(
            pulse = command.pulse,
            context = ProfileContext(command = command, admission = admission),
            reportAcceptance = true,
        ),
    )

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

    private fun dispatchInternal(pulse: ProfilePulse.ResourceResult) {
        dispatchRoot(pulse, ProfileContext.Local, reportAcceptance = false)
    }

    private fun dispatchRoot(
        pulse: ProfilePulse,
        context: ProfileContext,
        reportAcceptance: Boolean,
    ): ProfileAcceptance? = dispatchGuard.dispatch {
        check(completions.isEmpty) { "Profile completion deque leaked across dispatches" }
        check(completions.tryAddLast(ProfileWorkItem(pulse, context, causalDepth = 0)))

        var rootAcceptance: ProfileAcceptance? = null
        var root = true
        while (!completions.isEmpty) {
            val item = checkNotNull(completions.removeFirstOrNull())
            val before = committedState
            when (val decision = ProfileNucleus.decide(before, item.pulse, item.context)) {
                is ProfileDecision.Rejected -> {
                    check(root && reportAcceptance) {
                        "A trusted Profile completion was rejected: ${decision.reason}"
                    }
                    rootAcceptance = ProfileAcceptance.Rejected(
                        instanceId = before.instanceId,
                        observedRevision = before.revision,
                        reason = decision.reason,
                    )
                }
                is ProfileDecision.Accepted -> {
                    preflight(before, item, decision.frame)
                    committedState = decision.frame.nextState
                    if (root && reportAcceptance) {
                        rootAcceptance = ProfileAcceptance.Accepted(
                            instanceId = committedState.instanceId,
                            revision = committedState.revision,
                        )
                    }
                    decision.frame.outputs.forEach { output ->
                        execute(output, item.causalDepth)
                    }
                }
            }
            root = false
        }

        if (reportAcceptance) checkNotNull(rootAcceptance) else null
    }

    private fun preflight(
        before: ProfileState,
        item: ProfileWorkItem,
        frame: ProfileAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Profile instance identity changed" }
        check(next.policy === before.policy) { "Captured Profile policy identity changed" }
        if (item.pulse is ProfilePulse.BootstrapCompleted) {
            check(next.revision.value > before.revision.value) {
                "Profile bootstrap revision must advance"
            }
        } else {
            check(before.revision.value < Long.MAX_VALUE)
            check(next.revision.value == before.revision.value + 1L) {
                "Profile revision must advance exactly once"
            }
        }
        check(frame.outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile output limit exceeded"
        }

        val synchronousCompletions = frame.outputs.count { output ->
            output is ProfileOutput.PersistV4Snapshot || output is ProfileOutput.PurgeLegacy
        }
        requireProfileSynchronousResourceEffectBound(synchronousCompletions)
        if (synchronousCompletions > 0) {
            requireProfileCausalDepth(item.causalDepth + 1)
            requireProfileCompletionCapacity(completions.remainingCapacity, synchronousCompletions)
        }

        frame.outputs.forEachIndexed { index, output ->
            when (output) {
                is ProfileOutput.PersistV4Snapshot -> {
                    check(output.effectRef.sourceRevision == next.revision)
                    check(output.snapshot.revision == next.revision)
                    check(output.effectRef.ordinal >= 0)
                }
                is ProfileOutput.PurgeLegacy -> {
                    check(output.effectRef.sourceRevision == next.revision)
                    check(output.effectRef.ordinal >= 0)
                }
                is ProfileOutput.CompleteCommand -> {
                    check(output.result.targetRevision == next.revision)
                    check(index == frame.outputs.lastIndex) {
                        "Profile command completion must be the final ordered output"
                    }
                }
            }
        }
    }

    private fun execute(output: ProfileOutput, sourceDepth: Int) {
        when (output) {
            is ProfileOutput.PersistV4Snapshot -> {
                val result = try {
                    resource.writeV4(output.snapshot)
                } catch (_: Throwable) {
                    ProfileV4WriteResult.OutcomeUnknown(
                        ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                    )
                }
                enqueueCompletion(
                    ProfilePulse.V4WriteCompleted(output.effectRef, result),
                    sourceDepth + 1,
                )
            }
            is ProfileOutput.PurgeLegacy -> {
                val result = try {
                    resource.purgeLegacy()
                } catch (_: Throwable) {
                    ProfileLegacyPurgeResult.OutcomeUnknown(
                        remaining = ProfileLegacyKeys.NONE,
                        unknown = ProfileLegacyKeys.ALL,
                        reason = ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
                    )
                }
                enqueueCompletion(
                    ProfilePulse.LegacyPurgeCompleted(output.effectRef, result),
                    sourceDepth + 1,
                )
            }
            is ProfileOutput.CompleteCommand -> commandResultSink(output.result)
        }
    }

    private fun enqueueCompletion(pulse: ProfilePulse.ResourceResult, causalDepth: Int) {
        requireProfileCausalDepth(causalDepth)
        check(
            completions.tryAddLast(
                ProfileWorkItem(pulse, ProfileContext.Local, causalDepth),
            ),
        ) { "Pre-reserved Profile completion could not be retained" }
    }
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

private data class ProfileWorkItem(
    val pulse: ProfilePulse,
    val context: ProfileContext,
    val causalDepth: Int,
)

private const val PROFILE_COMPLETION_CAPACITY: Int = 8
private const val MAX_PROFILE_CAUSAL_DEPTH: Int = 8
