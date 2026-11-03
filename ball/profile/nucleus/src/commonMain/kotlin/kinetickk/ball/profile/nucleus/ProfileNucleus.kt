// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.ProfileRebirthTierSelected
import kinetickk.ball.profile.api.rank
import kinetickk.ball.profile.api.rankWithin
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * Pure, deterministic authority for every Profile decision and projection. Queries, preference
 * changes, purchases and gameplay progress are its extensions in the sibling `Profile*.kt` files.
 */
object ProfileNucleus {
    fun decide(
        state: ProfileState,
        pulse: ProfileNucleusPulse,
    ): ProfileDecision {
        return when (pulse) {
            is ProfileNucleusPulse.Intent -> {
                mutationGate(state)?.let { return rejected(it) }
                decideMutation(state, pulse.intent)
            }
            ProfileNucleusPulse.ToggleMute -> {
                mutationGate(state)?.let { return rejected(it) }
                toggleMute(state)
            }
            is ProfileNucleusPulse.SelectCoreShape -> {
                mutationGate(state)?.let { return rejected(it) }
                selectCoreShape(state, pulse.shape)
            }
            is ProfileNucleusPulse.SelectRebirthTier -> {
                mutationGate(state)?.let { return rejected(it) }
                selectRebirthTier(state, pulse.tier)
            }
            is ProfileNucleusPulse.ApplyGameplayProgress -> {
                mutationGate(state)?.let { return rejected(it) }
                applyGameplayProgress(state, pulse.update)
            }
        }
    }

    private fun decideMutation(
        state: ProfileState,
        pulse: ProfilePulse.Business,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.AdjustPreference -> adjustPreference(state, pulse.adjustment)
        is ProfilePulse.PurchaseMatrixNode -> purchaseMatrixNode(state, pulse.id)
        ProfilePulse.ResetMatrix -> resetMatrix(state)
        is ProfilePulse.PurchaseOrEquipWeapon -> purchaseOrEquipWeapon(state, pulse.id)
        is ProfilePulse.MarkCollectionEntryViewed -> markCollectionEntryViewed(state, pulse.entry)
        is ProfilePulse.SelectCoreShape -> selectCoreShape(state, pulse.shape, commandReply = false)
        is ProfilePulse.PurchaseCharacterUpgrade -> purchaseCharacterUpgrade(state, pulse.shape)
        is ProfilePulse.PurchaseWeaponUpgrade -> purchaseWeaponUpgrade(state, pulse.id)
    }

    private fun markCollectionEntryViewed(state: ProfileState, entry: CollectionEntry): ProfileDecision {
        val collection = state.profile.collection
        val next = when (entry) {
            is CollectionEntry.Item -> collection.copy(newItemIds = (collection.newItemIds - entry.id).toImmutableSet())
            is CollectionEntry.Relic -> collection.copy(newRelicIds = (collection.newRelicIds - entry.id).toImmutableSet())
        }
        if (next == collection) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(state, state.profile.copy(collection = next))
    }

    /**
     * Selects [tier] for the next run. Any tier from the policy minimum up to the rank (one above the
     * highest clear) is allowed, lower tiers included, so a player is never stuck at a tier they
     * cannot clear; nothing else changes. The current tier is accepted again (it confirms the tier the
     * next run starts at), so this command always publishes its result.
     */
    private fun selectRebirthTier(
        state: ProfileState,
        tier: Int,
    ): ProfileDecision {
        val progress = state.profile.rebirthProgress
        val rebirth = state.policy.rebirth
        if (tier !in rebirth.minimumLevel..progress.rankWithin(rebirth.minimumLevel, rebirth.maximumLevel)) {
            return rejected(ProfileRejection.RebirthTierLocked)
        }
        val next = progress.copy(level = tier)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(rebirthProgress = next),
            commandOutput = ProfileOutput.RebirthTierSelected(ProfileRebirthTierSelected(state.revision.next(), next)),
        )
    }

    internal fun acceptedMutation(
        state: ProfileState,
        nextProfile: PlayerProfile,
        commandOutput: ProfileOutput? = null,
    ): ProfileDecision {
        check(state.persistence !is ProfilePersistenceStatus.Pending) {
            "Inline Profile cannot accept another mutation while a Resource effect is pending"
        }
        val revision = state.revision.next()
        val effectRef = resourceEffectRef(revision)
        val nextState = state.copy(
            revision = revision,
            profile = nextProfile,
            persistence = ProfilePersistenceStatus.Pending(
                effectRef = effectRef,
                snapshotRevision = revision,
            ),
        )
        val persist = ProfileOutput.PersistSnapshot(
            effectRef = effectRef,
            snapshot = ProfileSnapshot(
                revision = revision,
                profile = nextProfile,
            ),
        )
        val outputs = if (commandOutput == null) {
            immutableListOf(persist)
        } else {
            immutableListOf(persist, commandOutput)
        }
        return accepted(nextState, outputs)
    }

    /**
     * Settles the one committed write. Unlike a request, its completion cannot be rejected.
     * Correlation faults are programming errors at this synchronous, trusted boundary: they
     * commit nothing, and the owner retains the queued completion for diagnosis.
     */
    fun complete(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileWriteResult,
    ): ProfileAcceptedFrame {
        val pending = checkNotNull(state.persistence as? ProfilePersistenceStatus.Pending)
        check(pending.effectRef == effectRef)
        val persistence = checkNotNull(result.toPersistenceStatus(pending))
        return ProfileAcceptedFrame(
            state.copy(revision = state.revision.next(), persistence = persistence),
            immutableListOf(),
        )
    }

    private fun mutationGate(state: ProfileState): ProfileRejection? = when (state.bootstrap) {
        ProfileBootstrapStatus.Ready -> null
        is ProfileBootstrapStatus.Blocked -> ProfileRejection.BootstrapNotReady
    }

    /**
     * True when a new tier waits: the rank is not cleared yet and lies above the selected `level`. An
     * Ascendant profile (every tier cleared) has none, whichever tier it replays.
     */
    internal fun canAdvanceRebirth(state: ProfileState): Boolean {
        val progress = state.profile.rebirthProgress
        val rank = progress.rankWithin(state.policy.rebirth.minimumLevel, state.policy.rebirth.maximumLevel)
        return state.bootstrap == ProfileBootstrapStatus.Ready && rank > progress.level && rank > progress.highestCleared
    }

    private fun ProfileWriteResult.toPersistenceStatus(
        pending: ProfilePersistenceStatus.Pending,
    ): ProfilePersistenceStatus? = when (this) {
        is ProfileWriteResult.Written -> if (revision == pending.snapshotRevision) {
            ProfilePersistenceStatus.Persisted(revision)
        } else {
            null
        }
        is ProfileWriteResult.Rejected ->
            ProfilePersistenceStatus.Rejected(pending.snapshotRevision, reason)
        is ProfileWriteResult.ResourceFailure ->
            ProfilePersistenceStatus.ResourceFailure(pending.snapshotRevision, reason)
        is ProfileWriteResult.OutcomeUnknown ->
            ProfilePersistenceStatus.OutcomeUnknown(pending.snapshotRevision, reason)
    }

    private fun resourceEffectRef(revision: ProfileRevision): ProfileEffectRef =
        ProfileEffectRef(sourceRevision = revision, ordinal = PROFILE_RESOURCE_OUTPUT_ORDINAL)

    internal fun ProfileRevision.next(): ProfileRevision {
        check(value < Long.MAX_VALUE) { "Profile revision exhausted" }
        return ProfileRevision(value + 1L)
    }

    internal fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
        preferences = preferences,
        economy = economy,
        loadout = loadout,
        matrix = matrix,
        collection = collection,
        rebirthProgress = rebirthProgress,
        upgrades = upgrades,
    )

    private fun accepted(
        nextState: ProfileState,
        outputs: ImmutableList<ProfileOutput> = immutableListOf(),
    ): ProfileDecision.Accepted {
        check(outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile semantic output limit exceeded"
        }
        return ProfileDecision.Accepted(ProfileAcceptedFrame(nextState, outputs))
    }

    internal fun rejected(reason: ProfileRejection): ProfileDecision.Rejected =
        ProfileDecision.Rejected(reason)

}

private const val PROFILE_RESOURCE_OUTPUT_ORDINAL: Int = 0

internal fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

/** Matter paid for every owned Matrix rank under [policy] (saturating). */
internal fun matrixSpent(matrix: MatrixProgress, policy: kinetickk.ball.content.api.ProfilePolicySnapshot): Long =
    policy.matrixNodes.fold(0L) { total, definition -> saturatedAdd(total, definition.spent(matrix.rank(definition.id))) }
