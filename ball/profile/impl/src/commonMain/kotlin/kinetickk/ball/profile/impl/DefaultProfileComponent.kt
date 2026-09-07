// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.GameplayProgressUpdate
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
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply

/** Sole owner, acceptor, publisher, and output dispatcher for the local Profile instance. */
internal class DefaultProfileComponent(
    private val resource: ProfileResource,
    policy: ProfilePolicySnapshot,
) : ProfileComponent {
    private val acceptance = InlineAcceptance(profileCompletionDeque<ProfileWorkItem>())
    private var committedState: ProfileState = ProfileState.initial(
        policy = policy,
        snapshotReadResult = resource.readSnapshot(),
    )

    override val instanceId: ProfileInstanceId
        get() = committedState.instanceId

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance =
        dispatchLocal(pulse)

    override fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>) =
        dispatchCommand(ProfileWorkItem.Settings(reply))

    override fun selectCoreShape(shape: CoreShape, reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>) =
        dispatchCommand(ProfileWorkItem.CoreShapeSelection(shape, reply))

    override fun advanceRebirth(reply: InlineReply<ProfileRebirthAdvanced, ProfileRefusal>) =
        dispatchCommand(ProfileWorkItem.RebirthAdvancement(reply))

    override fun applyGameplayProgress(
        update: GameplayProgressUpdate,
        reply: InlineReply<ProfileProgressApplied, ProfileRefusal>,
    ) = dispatchCommand(ProfileWorkItem.ProgressApplication(update, reply))

    private fun <Result : Any> dispatchCommand(item: ProfileWorkItem.Direct<Result>) {
        val reply = item.reply
        reply.checkAvailable()
        if (acceptance.isDispatching || !acceptance.isEmpty) {
            reply.refused(ProfileRefusal.Busy)
            return
        }
        if (!hasProfileCommandRevisionCapacity(committedState.revision)) {
            reply.refused(ProfileRefusal.RevisionCapacityExhausted)
            return
        }
        acceptance.dispatch {
            when (val decision = ProfileNucleus.decide(committedState, item.pulse)) {
                is ProfileDecision.Rejected -> reply.refused(ProfileRefusal.DecisionRejected(decision.reason))
                is ProfileDecision.Accepted -> dispatchAccepted(item, decision.frame)
            }
        }
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

    private fun dispatchLocal(pulse: ProfilePulse.Business): ProfileAcceptance = acceptance.dispatch {
        check(hasProfileCommandRevisionCapacity(committedState.revision)) {
            "Profile local revision capacity exhausted before Intent construction"
        }
        val item = ProfileWorkItem.Local(ProfileNucleusPulse.Intent(pulse))
        when (val decision = ProfileNucleus.decide(committedState, item.pulse)) {
            is ProfileDecision.Rejected -> ProfileAcceptance.Rejected(
                instanceId = committedState.instanceId,
                observedRevision = committedState.revision,
                reason = decision.reason,
            )
            is ProfileDecision.Accepted -> {
                dispatchAccepted(item, decision.frame)
                ProfileAcceptance.Accepted(
                    instanceId = decision.frame.nextState.instanceId,
                    revision = decision.frame.nextState.revision,
                )
            }
        }
    }

    /** One writer and drain path for both ingress kinds and every accepted Resource completion. */
    private fun dispatchAccepted(rootItem: ProfileWorkItem, rootFrame: ProfileAcceptedFrame) {
        acceptance.acceptAndDrain(
            rootItem = rootItem,
            rootFrame = rootFrame,
            outputs = { it.outputs },
            acceptFrame = { item, frame ->
                preflight(committedState, item, frame)
                committedState = frame.nextState
            },
            decideCompletion = { item ->
                when (val decision = ProfileNucleus.decide(committedState, item.pulse)) {
                    is ProfileDecision.Accepted -> decision.frame
                    is ProfileDecision.Rejected -> error(
                        "A trusted Profile Resource completion was rejected: " + decision.reason,
                    )
                }
            },
            execute = { output, item -> execute(output, item) },
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
        check(frame.outputs.count { it is ProfileOutput.SettingsChanged } ==
            if (item is ProfileWorkItem.Settings) 1 else 0
        ) { "Profile settings command must accept its one typed result" }
        check(frame.outputs.count { it is ProfileOutput.CoreShapeSelected } ==
            if (item is ProfileWorkItem.CoreShapeSelection) 1 else 0
        ) { "Profile loadout command must accept its one typed result" }
        check(frame.outputs.count { it is ProfileOutput.RebirthAdvanced } ==
            if (item is ProfileWorkItem.RebirthAdvancement) 1 else 0
        ) { "Profile rebirth command must accept its one typed result" }
        check(frame.outputs.count { it is ProfileOutput.ProgressApplied } ==
            if (item is ProfileWorkItem.ProgressApplication) 1 else 0
        ) { "Profile progress command must accept its one typed result" }
        if (item is ProfileWorkItem.Direct<*>) item.reply.checkAvailable()

        val synchronousCompletions = frame.outputs.count { output ->
            output is ProfileOutput.PersistSnapshot
        }
        requireProfileSynchronousResourceEffectBound(synchronousCompletions)
        if (synchronousCompletions > 0) {
            requireProfileCompletionCapacity(acceptance.remainingCapacity, synchronousCompletions)
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
                is ProfileOutput.SettingsChanged -> {
                    check(index == frame.outputs.lastIndex)
                    check(output.result.revision == next.revision)
                    check(output.result.preferences == next.profile.preferences)
                }
                is ProfileOutput.CoreShapeSelected -> {
                    check(index == frame.outputs.lastIndex)
                    check(output.result.revision == next.revision)
                    check(output.result.shape == next.profile.loadout.coreShape)
                }
                is ProfileOutput.RebirthAdvanced -> {
                    check(index == frame.outputs.lastIndex)
                    check(output.result.revision == next.revision)
                    check(output.result.progress == next.profile.rebirthProgress)
                }
                is ProfileOutput.ProgressApplied -> {
                    check(index == frame.outputs.lastIndex)
                    check(output.result.revision == next.revision)
                }
            }
        }
    }

    private fun execute(output: ProfileOutput, item: ProfileWorkItem) {
        when (output) {
            is ProfileOutput.PersistSnapshot -> {
                val result = resource.writeSnapshot(output.snapshot)
                validateWriteCompletion(output, result)
                val pulse = ProfileNucleusPulse.WriteCompleted(output.effectRef, result)
                acceptance.retainCompletion(ProfileWorkItem.Local(pulse))
            }
            is ProfileOutput.SettingsChanged -> when (item) {
                is ProfileWorkItem.Settings -> item.reply.accepted(output.result)
                else -> error("Profile settings result has no current typed call")
            }
            is ProfileOutput.CoreShapeSelected -> when (item) {
                is ProfileWorkItem.CoreShapeSelection -> item.reply.accepted(output.result)
                else -> error("Profile loadout result has no current typed call")
            }
            is ProfileOutput.RebirthAdvanced -> when (item) {
                is ProfileWorkItem.RebirthAdvancement -> item.reply.accepted(output.result)
                else -> error("Profile rebirth result has no current typed call")
            }
            is ProfileOutput.ProgressApplied -> when (item) {
                is ProfileWorkItem.ProgressApplication -> item.reply.accepted(output.result)
                else -> error("Profile progress result has no current typed call")
            }
        }
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
}

internal fun <T> profileCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(PROFILE_COMPLETION_CAPACITY)

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

private sealed interface ProfileWorkItem {
    val pulse: ProfileNucleusPulse

    sealed interface Direct<Result : Any> : ProfileWorkItem {
        val reply: InlineReply<Result, ProfileRefusal>
    }

    data class Settings(
        override val reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>,
    ) : Direct<ProfileSettingsChanged> {
        override val pulse: ProfileNucleusPulse = ProfileNucleusPulse.ToggleMute
    }

    data class CoreShapeSelection(
        val shape: CoreShape,
        override val reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>,
    ) : Direct<ProfileCoreShapeSelected> {
        override val pulse: ProfileNucleusPulse = ProfileNucleusPulse.SelectCoreShape(shape)
    }

    data class RebirthAdvancement(
        override val reply: InlineReply<ProfileRebirthAdvanced, ProfileRefusal>,
    ) : Direct<ProfileRebirthAdvanced> {
        override val pulse: ProfileNucleusPulse = ProfileNucleusPulse.AdvanceRebirth
    }

    data class ProgressApplication(
        val update: GameplayProgressUpdate,
        override val reply: InlineReply<ProfileProgressApplied, ProfileRefusal>,
    ) : Direct<ProfileProgressApplied> {
        override val pulse: ProfileNucleusPulse = ProfileNucleusPulse.ApplyGameplayProgress(update)
    }

    data class Local(override val pulse: ProfileNucleusPulse) : ProfileWorkItem
}

private const val PROFILE_COMPLETION_CAPACITY: Int = 8
private const val MAX_ORDINARY_REVISIONS_PER_DISPATCH: Long = 2L
