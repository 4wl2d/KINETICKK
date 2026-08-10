// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRefRejection
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetCompletion
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileResourceResultRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WritePurpose
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure, deterministic authority for every Profile decision and projection. */
object ProfileNucleus {
    fun decide(
        state: ProfileState,
        pulse: ProfilePulse,
        context: ProfileContext = ProfileContext.Local,
    ): ProfileDecision {
        validateContext(state, pulse, context)?.let { return rejected(it) }

        if (pulse is ProfilePulse.ResourceResult) {
            return decideResourceResult(state, pulse)
        }
        pulse as ProfilePulse.Business

        return when (pulse) {
            ProfilePulse.ConfirmLegacyReset -> confirmLegacyReset(state, context)
            ProfilePulse.RetryLegacyPurge -> retryLegacyPurge(state, context)
            else -> {
                mutationGate(state)?.let { return rejected(it) }
                decideMutation(state, pulse, context)
            }
        }
    }

    fun query(state: ProfileState, query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        RunBootstrapProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            result = if (state.bootstrap == ProfileBootstrapStatus.Ready) {
                ProfileRunBootstrapResult.Ready(state.profile.toGameplaySnapshot())
            } else {
                ProfileRunBootstrapResult.Unavailable(state.bootstrap)
            },
        )

    fun query(state: ProfileState, query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(state.instanceId, state.revision, state.profile.preferences)

    fun query(state: ProfileState, query: ProfileQuery.GetHomeProgress): HomeProgressProjection =
        HomeProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            economy = state.profile.economy,
            loadout = state.profile.loadout,
            collection = state.profile.collection,
            rebirthProgress = state.profile.rebirthProgress,
            canAdvanceRebirth = canAdvanceRebirth(state),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetLabProgress): LabProgressProjection =
        LabProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = LabProfileSnapshot(state.profile.economy, state.profile.labProgress),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetLoadout): LoadoutProjection =
        LoadoutProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = LoadoutProfileSnapshot(state.profile.economy, state.profile.loadout),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetCollection): CollectionProjection =
        CollectionProjection(state.instanceId, state.revision, state.profile.collection)

    fun query(state: ProfileState, query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection =
        RebirthProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = RebirthProfileSnapshot(state.profile.rebirthProgress),
            canAdvance = canAdvanceRebirth(state),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
        PersistenceStatusProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            bootstrap = state.bootstrap,
            reset = state.reset,
            persistence = state.persistence,
        )

    private fun decideMutation(
        state: ProfileState,
        pulse: ProfilePulse.Business,
        context: ProfileContext,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.AdjustPreference -> adjustPreference(state, pulse.adjustment, context)
        ProfilePulse.ToggleMute -> toggleMute(state, context)
        is ProfilePulse.PurchaseMetaUpgrade -> purchaseMetaUpgrade(state, pulse.id, context)
        is ProfilePulse.SelectCoreShape -> selectCoreShape(state, pulse.shape, context)
        is ProfilePulse.PurchaseOrEquipWeapon -> purchaseOrEquipWeapon(state, pulse.id, context)
        ProfilePulse.AdvanceRebirth -> advanceRebirth(state, context)
        is ProfilePulse.ApplyGameplayProgress -> applyGameplayProgress(state, pulse.update, context)
        ProfilePulse.ConfirmLegacyReset,
        ProfilePulse.RetryLegacyPurge,
        -> error("Reset pulses are decided before the ordinary mutation branch")
    }

    private fun adjustPreference(
        state: ProfileState,
        adjustment: ProfilePreferenceAdjustment,
        context: ProfileContext,
    ): ProfileDecision {
        val current = state.profile.preferences
        val next = when (adjustment) {
            ProfilePreferenceAdjustment.ToggleSoundEffects ->
                current.copy(soundEnabled = !current.soundEnabled)
            ProfilePreferenceAdjustment.ToggleMusic ->
                current.copy(musicEnabled = !current.musicEnabled)
            is ProfilePreferenceAdjustment.StepMasterVolume -> current.copy(
                masterVolume = stepPercentage(current.masterVolume, adjustment.direction, 0f, 1f),
            )
            is ProfilePreferenceAdjustment.StepSimulationSpeed -> {
                val currentIndex = SIMULATION_SPEEDS.indices.minByOrNull { index ->
                    abs(SIMULATION_SPEEDS[index] - current.simulationSpeed)
                } ?: DEFAULT_SIMULATION_SPEED_INDEX
                current.copy(
                    simulationSpeed = SIMULATION_SPEEDS[
                        (currentIndex + adjustment.direction.delta).coerceIn(SIMULATION_SPEEDS.indices)
                    ],
                )
            }
            is ProfilePreferenceAdjustment.StepTextScale -> current.copy(
                textScale = stepPercentage(current.textScale, adjustment.direction, 1f, 1.75f),
            )
            ProfilePreferenceAdjustment.ToggleScreenShake ->
                current.copy(screenShake = !current.screenShake)
            is ProfilePreferenceAdjustment.StepParticleDensity -> current.copy(
                particleDensity = ParticleDensity.entries[
                    (current.particleDensity.ordinal + adjustment.direction.delta)
                        .coerceIn(ParticleDensity.entries.indices)
                ],
            )
            ProfilePreferenceAdjustment.ToggleDamageNumbers ->
                current.copy(damageNumbers = !current.damageNumbers)
            is ProfilePreferenceAdjustment.StepDamageNumberSize -> current.copy(
                damageNumberSize = DamageNumberSize.entries[
                    (current.damageNumberSize.ordinal + adjustment.direction.delta)
                        .coerceIn(DamageNumberSize.entries.indices)
                ],
            )
            is ProfilePreferenceAdjustment.StepDamageNumberFormat -> current.copy(
                damageNumberFormat = DamageNumberFormat.entries[
                    (current.damageNumberFormat.ordinal + adjustment.direction.delta)
                        .coerceIn(DamageNumberFormat.entries.indices)
                ],
            )
            is ProfilePreferenceAdjustment.StepDamageNumberTierThreshold -> {
                val currentIndex = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
                    abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - current.damageNumberTierThreshold)
                } ?: DEFAULT_DAMAGE_THRESHOLD_INDEX
                current.copy(
                    damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[
                        (currentIndex + adjustment.direction.delta)
                            .coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
                    ],
                )
            }
        }.normalized()
        if (next == current) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(preferences = next),
            context = context,
            commandOutcome = ProfileCommandOutcome.PreferencesChanged(next),
        )
    }

    private fun toggleMute(state: ProfileState, context: ProfileContext): ProfileDecision {
        val current = state.profile.preferences
        val enable = !current.soundEnabled && !current.musicEnabled
        val next = current.copy(soundEnabled = enable, musicEnabled = enable)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(preferences = next),
            context = context,
            commandOutcome = ProfileCommandOutcome.PreferencesChanged(next),
        )
    }

    private fun purchaseMetaUpgrade(
        state: ProfileState,
        id: MetaUpgradeId,
        context: ProfileContext,
    ): ProfileDecision {
        val definition = state.policy.metaUpgrade(id)
        val currentRank = state.profile.labProgress.rank(id)
        if (currentRank >= definition.maxRanks) return rejected(ProfileRejection.MetaUpgradeMaxRank)
        val cost = definition.cost(currentRank).toLong()
        if (state.profile.economy.matter < cost) return rejected(ProfileRejection.InsufficientMatter)

        val ranks = state.profile.labProgress.ranks.toMutableList()
        ranks[id.ordinal] = currentRank + 1
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = state.profile.economy.copy(matter = state.profile.economy.matter - cost),
                labProgress = LabProgress(ranks),
            ),
            context = context,
            commandOutcome = null,
        )
    }

    private fun selectCoreShape(
        state: ProfileState,
        shape: kinetickk.ball.content.api.CoreShape,
        context: ProfileContext,
    ): ProfileDecision {
        if (state.profile.economy.lifetimeMatter < state.policy.coreShape(shape).unlockLifetimeMatter) {
            return rejected(ProfileRejection.CoreShapeLocked)
        }
        if (shape == state.profile.loadout.coreShape) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                loadout = state.profile.loadout.copy(coreShape = shape),
            ),
            context = context,
            commandOutcome = null,
        )
    }

    private fun purchaseOrEquipWeapon(
        state: ProfileState,
        id: kinetickk.ball.content.api.WeaponId,
        context: ProfileContext,
    ): ProfileDecision {
        val unlocked = state.profile.loadout.unlockedWeapons.toMutableSet()
        var economy = state.profile.economy
        if (id !in unlocked) {
            val cost = state.policy.weapon(id).permanentUnlockCost.toLong()
            if (economy.matter < cost) return rejected(ProfileRejection.InsufficientMatter)
            economy = economy.copy(matter = economy.matter - cost)
            unlocked += id
        } else if (id == state.profile.loadout.selectedWeapon) {
            return rejected(ProfileRejection.NoChange)
        }
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = economy,
                loadout = PlayerLoadout(
                    coreShape = state.profile.loadout.coreShape,
                    selectedWeapon = id,
                    unlockedWeapons = unlocked,
                ),
            ),
            context = context,
            commandOutcome = null,
        )
    }

    private fun advanceRebirth(state: ProfileState, context: ProfileContext): ProfileDecision {
        val progress = state.profile.rebirthProgress
        if (progress.level >= state.policy.rebirth.maximumLevel) {
            return rejected(ProfileRejection.RebirthMaximumReached)
        }
        if (progress.highestCleared < progress.level) {
            return rejected(ProfileRejection.RebirthLevelNotCleared)
        }
        val next = progress.copy(level = progress.level + 1)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(rebirthProgress = next),
            context = context,
            commandOutcome = ProfileCommandOutcome.RebirthAdvanced(next),
        )
    }

    private fun applyGameplayProgress(
        state: ProfileState,
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
        context: ProfileContext,
    ): ProfileDecision {
        validateGameplayProgress(state, update)?.let {
            return rejected(ProfileRejection.InvalidGameplayProgress(it))
        }

        val economy = if (update.bankedMatter == 0L) {
            state.profile.economy
        } else {
            PlayerEconomy(
                matter = saturatedAdd(state.profile.economy.matter, update.bankedMatter),
                lifetimeMatter = saturatedAdd(state.profile.economy.lifetimeMatter, update.bankedMatter),
            )
        }
        val discoveries = state.profile.collection.discoveredItemIds.toMutableSet().apply {
            addAll(update.discoveredItemIds)
        }
        val rebirth = update.clearedRebirthLevel?.let { cleared ->
            state.profile.rebirthProgress.copy(
                highestCleared = max(state.profile.rebirthProgress.highestCleared, cleared),
            )
        } ?: state.profile.rebirthProgress
        val next = state.profile.copy(
            economy = economy,
            collection = PlayerCollection(discoveries),
            rebirthProgress = rebirth,
        )
        if (next == state.profile) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = next,
            context = context,
            commandOutcome = ProfileCommandOutcome.GameplayProgressApplied,
        )
    }

    private fun acceptedMutation(
        state: ProfileState,
        nextProfile: PlayerProfile,
        context: ProfileContext,
        commandOutcome: ProfileCommandOutcome?,
    ): ProfileDecision {
        check(state.persistence !is ProfilePersistenceStatus.Pending) {
            "Inline Profile cannot accept another mutation while a Resource effect is pending"
        }
        val revision = state.revision.next()
        val effectRef = state.nextEffectRef(revision)
        val reset = state.reset as ProfileResetStatus.NotRequired
        val nextState = state.copy(
            revision = revision,
            profile = nextProfile,
            persistence = ProfilePersistenceStatus.Pending(
                effectRef = effectRef,
                snapshotRevision = revision,
                purpose = ProfileV4WritePurpose.MUTATION,
            ),
            nextResourceOrdinal = state.nextResourceOrdinal.nextOrdinal(),
        )
        val persist = ProfileOutput.PersistV4Snapshot(
            effectRef = effectRef,
            snapshot = ProfileV4Snapshot(
                contentVersion = state.policy.version,
                revision = revision,
                legacyResetConfirmed = reset.legacyResetConfirmed,
                profile = nextProfile,
            ),
        )
        val outputs = context.command?.let { command ->
            checkNotNull(commandOutcome) { "Every admitted Profile command must define an outcome" }
            immutableListOf(
                persist,
                ProfileOutput.CompleteCommand(
                    ProfileCommandResult.Accepted(command.ref, revision, commandOutcome),
                ),
            )
        } ?: immutableListOf(persist)
        return accepted(nextState, outputs)
    }

    private fun confirmLegacyReset(state: ProfileState, context: ProfileContext): ProfileDecision {
        val reset = state.reset
        if (reset !is ProfileResetStatus.ConfirmationRequired) {
            return rejected(resetRejection(reset))
        }
        check(state.persistence !is ProfilePersistenceStatus.Pending)
        val revision = state.revision.next()
        val effectRef = state.nextEffectRef(revision)
        val completion = context.command?.let { ProfileResetCompletion.Command(it.ref) }
            ?: ProfileResetCompletion.Local
        val profile = defaultPlayerProfile(state.policy)
        val nextReset = ProfileResetStatus.WritingFreshV4(
            completion = completion,
            reason = reset.reason,
            effectRef = effectRef,
            legacyKeys = reset.legacyKeys,
        )
        val nextState = state.copy(
            revision = revision,
            profile = profile,
            bootstrap = ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResetInProgress),
            reset = nextReset,
            persistence = ProfilePersistenceStatus.Pending(
                effectRef = effectRef,
                snapshotRevision = revision,
                purpose = ProfileV4WritePurpose.RESET_DEFAULT,
            ),
            nextResourceOrdinal = state.nextResourceOrdinal.nextOrdinal(),
        )
        return accepted(
            nextState,
            immutableListOf(
                ProfileOutput.PersistV4Snapshot(
                    effectRef = effectRef,
                    snapshot = ProfileV4Snapshot(
                        contentVersion = state.policy.version,
                        revision = revision,
                        legacyResetConfirmed = true,
                        profile = profile,
                    ),
                ),
            ),
        )
    }

    private fun retryLegacyPurge(state: ProfileState, context: ProfileContext): ProfileDecision {
        val reset = state.reset
        if (reset !is ProfileResetStatus.NeedsAttention) return rejected(resetRejection(reset))
        val revision = state.revision.next()
        val effectRef = state.nextEffectRef(revision)
        val completion = context.command?.let { ProfileResetCompletion.Command(it.ref) }
            ?: ProfileResetCompletion.Local
        val nextState = state.copy(
            revision = revision,
            bootstrap = ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResetInProgress),
            reset = ProfileResetStatus.PurgingLegacy(
                completion = completion,
                effectRef = effectRef,
                legacyKeys = reset.legacyKeys,
            ),
            nextResourceOrdinal = state.nextResourceOrdinal.nextOrdinal(),
        )
        return accepted(nextState, immutableListOf(ProfileOutput.PurgeLegacy(effectRef)))
    }

    private fun decideResourceResult(
        state: ProfileState,
        pulse: ProfilePulse.ResourceResult,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.BootstrapCompleted -> decideBootstrap(state, pulse.result)
        is ProfilePulse.V4WriteCompleted -> decideV4Write(state, pulse.effectRef, pulse.result)
        is ProfilePulse.LegacyPurgeCompleted -> decideLegacyPurge(state, pulse.effectRef, pulse.result)
    }

    private fun decideBootstrap(
        state: ProfileState,
        result: ProfileBootstrapResourceResult,
    ): ProfileDecision {
        if (state.bootstrap != ProfileBootstrapStatus.AwaitingResource) {
            return unexpected(ProfileResourceResultRejection.BOOTSTRAP_ALREADY_RESOLVED)
        }
        if (
            result is ProfileBootstrapResourceResult.OutcomeUnknown &&
            result.reason != ProfileResourceFailure.PROVIDER_READ_FAILED
        ) {
            return unexpected(ProfileResourceResultRejection.RESULT_KIND_MISMATCH)
        }
        return when (result) {
            is ProfileBootstrapResourceResult.OutcomeUnknown -> {
                val revision = state.revision.next()
                accepted(
                    state.copy(
                        revision = revision,
                        bootstrap = ProfileBootstrapStatus.Blocked(
                            ProfileBootstrapBlockReason.ResourceOutcomeUnknown(result.reason),
                        ),
                    ),
                )
            }
            is ProfileBootstrapResourceResult.Rejected -> bootstrapResetRequired(
                state = state,
                profile = defaultPlayerProfile(state.policy),
                revision = state.revision.next(),
                resetReason = ProfileResetReason.InvalidV4(result.reason),
                legacyKeys = result.legacyKeys,
                persistence = ProfilePersistenceStatus.NotAttempted,
            )
            is ProfileBootstrapResourceResult.Observed -> decideObservedBootstrap(state, result)
        }
    }

    private fun decideObservedBootstrap(
        state: ProfileState,
        observed: ProfileBootstrapResourceResult.Observed,
    ): ProfileDecision {
        val snapshot = observed.snapshot
        if (snapshot == null) {
            return if (observed.legacyKeys.isEmpty) {
                accepted(
                    state.copy(
                        revision = state.revision.next(),
                        profile = defaultPlayerProfile(state.policy),
                        bootstrap = ProfileBootstrapStatus.Ready,
                        reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
                        persistence = ProfilePersistenceStatus.NotAttempted,
                    ),
                )
            } else {
                bootstrapResetRequired(
                    state = state,
                    profile = defaultPlayerProfile(state.policy),
                    revision = state.revision.next(),
                    resetReason = ProfileResetReason.LegacyDataDetected,
                    legacyKeys = observed.legacyKeys,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
            }
        }

        if (snapshot.contentVersion != state.policy.version) {
            return bootstrapResetRequired(
                state = state,
                profile = defaultPlayerProfile(state.policy),
                revision = state.revision.next(),
                resetReason = ProfileResetReason.ContentVersionMismatch(
                    expected = state.policy.version,
                    observed = snapshot.contentVersion,
                ),
                legacyKeys = observed.legacyKeys,
                persistence = ProfilePersistenceStatus.NotAttempted,
            )
        }
        if (snapshot.revision.value == Long.MAX_VALUE || !isPolicyCompatible(snapshot.profile, state)) {
            return bootstrapResetRequired(
                state = state,
                profile = defaultPlayerProfile(state.policy),
                revision = state.revision.next(),
                resetReason = ProfileResetReason.IncompatibleProfile,
                legacyKeys = observed.legacyKeys,
                persistence = ProfilePersistenceStatus.NotAttempted,
            )
        }

        val revision = snapshot.revision.next()
        val persistence = ProfilePersistenceStatus.Persisted(snapshot.revision)
        if (observed.legacyKeys.isEmpty) {
            return accepted(
                state.copy(
                    revision = revision,
                    profile = snapshot.profile,
                    bootstrap = ProfileBootstrapStatus.Ready,
                    reset = ProfileResetStatus.NotRequired(snapshot.legacyResetConfirmed),
                    persistence = persistence,
                ),
            )
        }
        if (snapshot.legacyResetConfirmed) {
            val result = ProfileLegacyPurgeResult.Partial(observed.legacyKeys)
            return accepted(
                state.copy(
                    revision = revision,
                    profile = snapshot.profile,
                    bootstrap = ProfileBootstrapStatus.Blocked(
                        ProfileBootstrapBlockReason.ResetNeedsAttention(result),
                    ),
                    reset = ProfileResetStatus.NeedsAttention(observed.legacyKeys, result),
                    persistence = persistence,
                ),
            )
        }
        return bootstrapResetRequired(
            state = state,
            profile = snapshot.profile,
            revision = revision,
            resetReason = ProfileResetReason.LegacyDataDetected,
            legacyKeys = observed.legacyKeys,
            persistence = persistence,
        )
    }

    private fun decideV4Write(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileV4WriteResult,
    ): ProfileDecision {
        val pending = state.persistence as? ProfilePersistenceStatus.Pending
            ?: return unexpected(ProfileResourceResultRejection.NO_EFFECT_PENDING)
        if (pending.effectRef != effectRef) {
            return unexpected(ProfileResourceResultRejection.EFFECT_REF_MISMATCH)
        }
        if (
            result is ProfileV4WriteResult.OutcomeUnknown &&
            result.reason != ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED
        ) {
            return unexpected(ProfileResourceResultRejection.RESULT_KIND_MISMATCH)
        }
        return when (pending.purpose) {
            ProfileV4WritePurpose.MUTATION -> completeMutationWrite(state, pending, result)
            ProfileV4WritePurpose.RESET_DEFAULT -> completeResetWrite(state, pending, result)
        }
    }

    private fun completeMutationWrite(
        state: ProfileState,
        pending: ProfilePersistenceStatus.Pending,
        result: ProfileV4WriteResult,
    ): ProfileDecision {
        val persistence = result.toPersistenceStatus(pending)
            ?: return unexpected(ProfileResourceResultRejection.WRITTEN_REVISION_MISMATCH)
        return accepted(state.copy(revision = state.revision.next(), persistence = persistence))
    }

    private fun completeResetWrite(
        state: ProfileState,
        pending: ProfilePersistenceStatus.Pending,
        result: ProfileV4WriteResult,
    ): ProfileDecision {
        val reset = state.reset as? ProfileResetStatus.WritingFreshV4
            ?: return unexpected(ProfileResourceResultRejection.RESULT_KIND_MISMATCH)
        if (reset.effectRef != pending.effectRef) {
            return unexpected(ProfileResourceResultRejection.EFFECT_REF_MISMATCH)
        }
        val revision = state.revision.next()
        return when (result) {
            is ProfileV4WriteResult.Written -> {
                if (result.revision != pending.snapshotRevision) {
                    return unexpected(ProfileResourceResultRejection.WRITTEN_REVISION_MISMATCH)
                }
                val persistence = ProfilePersistenceStatus.Persisted(result.revision)
                if (reset.legacyKeys.isEmpty) {
                    val nextState = state.copy(
                        revision = revision,
                        bootstrap = ProfileBootstrapStatus.Ready,
                        reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = true),
                        persistence = persistence,
                    )
                    accepted(nextState, reset.completionOutput(revision, ProfileCommandOutcome.ResetCompleted))
                } else {
                    val purgeRef = state.nextEffectRef(revision)
                    accepted(
                        state.copy(
                            revision = revision,
                            bootstrap = ProfileBootstrapStatus.Blocked(
                                ProfileBootstrapBlockReason.ResetInProgress,
                            ),
                            reset = ProfileResetStatus.PurgingLegacy(
                                completion = reset.completion,
                                effectRef = purgeRef,
                                legacyKeys = reset.legacyKeys,
                            ),
                            persistence = persistence,
                            nextResourceOrdinal = state.nextResourceOrdinal.nextOrdinal(),
                        ),
                        immutableListOf(ProfileOutput.PurgeLegacy(purgeRef)),
                    )
                }
            }
            is ProfileV4WriteResult.Rejected -> {
                val nextState = state.copy(
                    revision = revision,
                    bootstrap = ProfileBootstrapStatus.Blocked(
                        ProfileBootstrapBlockReason.ResetRequired(reset.reason),
                    ),
                    reset = ProfileResetStatus.ConfirmationRequired(reset.reason, reset.legacyKeys),
                    persistence = ProfilePersistenceStatus.Rejected(pending.snapshotRevision, result.reason),
                )
                accepted(
                    nextState,
                    reset.completionOutput(revision, ProfileCommandOutcome.ResetWriteRejected(result.reason)),
                )
            }
            is ProfileV4WriteResult.OutcomeUnknown -> {
                val nextState = state.copy(
                    revision = revision,
                    bootstrap = ProfileBootstrapStatus.Blocked(
                        ProfileBootstrapBlockReason.ResetRequired(reset.reason),
                    ),
                    reset = ProfileResetStatus.ConfirmationRequired(reset.reason, reset.legacyKeys),
                    persistence = ProfilePersistenceStatus.OutcomeUnknown(
                        pending.snapshotRevision,
                        result.reason,
                    ),
                )
                accepted(
                    nextState,
                    reset.completionOutput(
                        revision,
                        ProfileCommandOutcome.ResetWriteOutcomeUnknown(result.reason),
                    ),
                )
            }
        }
    }

    private fun decideLegacyPurge(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileLegacyPurgeResult,
    ): ProfileDecision {
        val reset = state.reset as? ProfileResetStatus.PurgingLegacy
            ?: return unexpected(ProfileResourceResultRejection.NO_EFFECT_PENDING)
        if (reset.effectRef != effectRef) {
            return unexpected(ProfileResourceResultRejection.EFFECT_REF_MISMATCH)
        }
        if (
            result is ProfileLegacyPurgeResult.OutcomeUnknown &&
            result.reason != ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED &&
            result.reason != ProfileResourceFailure.PROVIDER_READ_FAILED
        ) {
            return unexpected(ProfileResourceResultRejection.RESULT_KIND_MISMATCH)
        }
        if (
            result is ProfileLegacyPurgeResult.Partial && result.remaining.isEmpty ||
            result is ProfileLegacyPurgeResult.OutcomeUnknown && result.unknown.isEmpty
        ) {
            return unexpected(ProfileResourceResultRejection.RESULT_KIND_MISMATCH)
        }
        val revision = state.revision.next()
        return when (result) {
            ProfileLegacyPurgeResult.Purged -> {
                val nextState = state.copy(
                    revision = revision,
                    bootstrap = ProfileBootstrapStatus.Ready,
                    reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = true),
                )
                accepted(nextState, reset.completionOutput(revision, ProfileCommandOutcome.ResetCompleted))
            }
            is ProfileLegacyPurgeResult.Partial,
            is ProfileLegacyPurgeResult.OutcomeUnknown,
            is ProfileLegacyPurgeResult.Rejected,
            -> {
                val keys = when (result) {
                    is ProfileLegacyPurgeResult.Partial -> result.remaining
                    is ProfileLegacyPurgeResult.OutcomeUnknown -> result.remaining union result.unknown
                    is ProfileLegacyPurgeResult.Rejected -> reset.legacyKeys
                    ProfileLegacyPurgeResult.Purged -> error("Handled above")
                }
                val status = ProfileResetStatus.NeedsAttention(keys, result)
                val nextState = state.copy(
                    revision = revision,
                    bootstrap = ProfileBootstrapStatus.Blocked(
                        ProfileBootstrapBlockReason.ResetNeedsAttention(result),
                    ),
                    reset = status,
                )
                accepted(
                    nextState,
                    reset.completionOutput(
                        revision,
                        ProfileCommandOutcome.ResetNeedsAttention(status),
                    ),
                )
            }
        }
    }

    private fun bootstrapResetRequired(
        state: ProfileState,
        profile: PlayerProfile,
        revision: ProfileRevision,
        resetReason: ProfileResetReason,
        legacyKeys: ProfileLegacyKeys,
        persistence: ProfilePersistenceStatus,
    ): ProfileDecision = accepted(
        state.copy(
            revision = revision,
            profile = profile,
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResetRequired(resetReason),
            ),
            reset = ProfileResetStatus.ConfirmationRequired(resetReason, legacyKeys),
            persistence = persistence,
        ),
    )

    private fun validateContext(
        state: ProfileState,
        pulse: ProfilePulse,
        context: ProfileContext,
    ): ProfileRejection? {
        val command = context.command
        val admission = context.admission
        if (command == null && admission == null) return null
        if (
            command == null ||
            admission == null ||
            command.pulse != pulse ||
            command.ref != admission.commandRef
        ) {
            return ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.ADMISSION_MISMATCH)
        }
        if (command.ref.targetInstance != state.instanceId) {
            return ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.WRONG_TARGET)
        }
        val sourceAccepted = when (command.ref.sourceInstance) {
            ProfileCommandSource.LocalSession -> command.pulse is ProfilePulse.AdjustPreference ||
                command.pulse == ProfilePulse.ToggleMute ||
                command.pulse == ProfilePulse.AdvanceRebirth ||
                command.pulse == ProfilePulse.ConfirmLegacyReset ||
                command.pulse == ProfilePulse.RetryLegacyPurge
            is ProfileCommandSource.GameplayRun -> command.pulse is ProfilePulse.ApplyGameplayProgress
        }
        return if (sourceAccepted) null else {
            ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.WRONG_SOURCE_KIND)
        }
    }

    private fun mutationGate(state: ProfileState): ProfileRejection? = when (state.bootstrap) {
        ProfileBootstrapStatus.AwaitingResource -> ProfileRejection.BootstrapNotReady
        ProfileBootstrapStatus.Ready -> null
        is ProfileBootstrapStatus.Blocked -> when (state.reset) {
            is ProfileResetStatus.WritingFreshV4,
            is ProfileResetStatus.PurgingLegacy,
            -> ProfileRejection.ResetInProgress
            is ProfileResetStatus.ConfirmationRequired,
            is ProfileResetStatus.NeedsAttention,
            -> ProfileRejection.ResetRequired
            is ProfileResetStatus.NotRequired -> ProfileRejection.BootstrapNotReady
        }
    }

    private fun resetRejection(reset: ProfileResetStatus): ProfileRejection = when (reset) {
        is ProfileResetStatus.WritingFreshV4,
        is ProfileResetStatus.PurgingLegacy,
        -> ProfileRejection.ResetInProgress
        is ProfileResetStatus.ConfirmationRequired,
        is ProfileResetStatus.NeedsAttention,
        is ProfileResetStatus.NotRequired,
        -> ProfileRejection.ResetRequired
    }

    private fun validateGameplayProgress(
        state: ProfileState,
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
    ): ProfileGameplayProgressRejection? {
        if (update.bankedMatter < 0L) return ProfileGameplayProgressRejection.NegativeBankedMatter
        if (update.discoveredItemIds.size > state.policy.itemCount) {
            return ProfileGameplayProgressRejection.TooManyDiscoveries
        }
        update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
            return ProfileGameplayProgressRejection.UnknownItem(it)
        }
        update.clearedRebirthLevel?.let { level ->
            if (level < state.policy.rebirth.minimumLevel) {
                return ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(level)
            }
            if (level > state.profile.rebirthProgress.level) {
                return ProfileGameplayProgressRejection.ClearedLevelAboveCurrent(level)
            }
        }
        return null
    }

    private fun isPolicyCompatible(profile: PlayerProfile, state: ProfileState): Boolean {
        val policy = state.policy
        val preferences = profile.preferences
        if (
            !preferences.masterVolume.isFinite() ||
            !preferences.simulationSpeed.isFinite() ||
            !preferences.textScale.isFinite() ||
            preferences != preferences.normalized() ||
            preferences.simulationSpeed !in SIMULATION_SPEEDS ||
            preferences.damageNumberTierThreshold !in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
        ) return false
        if (profile.economy.matter < 0L || profile.economy.lifetimeMatter < profile.economy.matter) {
            return false
        }
        val allowedShapes = policy.coreShapes.map { it.id }
        val allowedWeapons = policy.weapons.map { it.id }
        if (
            profile.loadout.coreShape !in allowedShapes ||
            profile.economy.lifetimeMatter <
                policy.coreShape(profile.loadout.coreShape).unlockLifetimeMatter ||
            profile.loadout.selectedWeapon !in allowedWeapons ||
            profile.loadout.selectedWeapon !in profile.loadout.unlockedWeapons ||
            policy.weapons.first().id !in profile.loadout.unlockedWeapons ||
            profile.loadout.unlockedWeapons.isEmpty() ||
            profile.loadout.unlockedWeapons.size > policy.weapons.size ||
            profile.loadout.unlockedWeapons.any { it !in allowedWeapons }
        ) return false
        if (profile.labProgress.ranks.size != policy.metaUpgrades.size) return false
        policy.metaUpgrades.forEach { definition ->
            if (profile.labProgress.rank(definition.id) !in 0..definition.maxRanks) return false
        }
        if (
            profile.collection.discoveredItemIds.size > policy.itemCount ||
            profile.collection.discoveredItemIds.any { !policy.containsItem(it) }
        ) return false
        if (
            profile.rebirthProgress.level !in policy.rebirth.minimumLevel..policy.rebirth.maximumLevel ||
            profile.rebirthProgress.highestCleared !in -1..profile.rebirthProgress.level
        ) return false
        return true
    }

    private fun canAdvanceRebirth(state: ProfileState): Boolean =
        state.bootstrap == ProfileBootstrapStatus.Ready &&
            state.profile.rebirthProgress.level < state.policy.rebirth.maximumLevel &&
            state.profile.rebirthProgress.highestCleared >= state.profile.rebirthProgress.level

    private fun ProfileV4WriteResult.toPersistenceStatus(
        pending: ProfilePersistenceStatus.Pending,
    ): ProfilePersistenceStatus? = when (this) {
        is ProfileV4WriteResult.Written -> if (revision == pending.snapshotRevision) {
            ProfilePersistenceStatus.Persisted(revision)
        } else {
            null
        }
        is ProfileV4WriteResult.Rejected ->
            ProfilePersistenceStatus.Rejected(pending.snapshotRevision, reason)
        is ProfileV4WriteResult.OutcomeUnknown ->
            ProfilePersistenceStatus.OutcomeUnknown(pending.snapshotRevision, reason)
    }

    private fun ProfileResetCompletion.output(
        revision: ProfileRevision,
        outcome: ProfileCommandOutcome,
    ): ImmutableList<ProfileOutput> = when (this) {
        ProfileResetCompletion.Local -> immutableListOf()
        is ProfileResetCompletion.Command -> immutableListOf(
            ProfileOutput.CompleteCommand(
                ProfileCommandResult.Accepted(commandRef, revision, outcome),
            ),
        )
    }

    private fun ProfileResetStatus.WritingFreshV4.completionOutput(
        revision: ProfileRevision,
        outcome: ProfileCommandOutcome,
    ): ImmutableList<ProfileOutput> = completion.output(revision, outcome)

    private fun ProfileResetStatus.PurgingLegacy.completionOutput(
        revision: ProfileRevision,
        outcome: ProfileCommandOutcome,
    ): ImmutableList<ProfileOutput> = completion.output(revision, outcome)

    private fun ProfileState.nextEffectRef(revision: ProfileRevision): ProfileEffectRef =
        ProfileEffectRef(sourceRevision = revision, ordinal = nextResourceOrdinal)

    private fun ProfileRevision.next(): ProfileRevision {
        check(value < Long.MAX_VALUE) { "Profile revision exhausted" }
        return ProfileRevision(value + 1L)
    }

    private fun Int.nextOrdinal(): Int {
        check(this < Int.MAX_VALUE) { "Profile Resource ordinal exhausted" }
        return this + 1
    }

    private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
        preferences = preferences,
        economy = economy,
        loadout = loadout,
        labProgress = labProgress,
        collection = collection,
        rebirthProgress = rebirthProgress,
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

    private fun rejected(reason: ProfileRejection): ProfileDecision.Rejected =
        ProfileDecision.Rejected(reason)

    private fun unexpected(reason: ProfileResourceResultRejection): ProfileDecision.Rejected =
        rejected(ProfileRejection.UnexpectedResourceResult(reason))
}

private val PreferenceAdjustmentDirection.delta: Int
    get() = when (this) {
        PreferenceAdjustmentDirection.DECREASE -> -1
        PreferenceAdjustmentDirection.INCREASE -> 1
    }

private fun stepPercentage(
    value: Float,
    direction: PreferenceAdjustmentDirection,
    minimum: Float,
    maximum: Float,
): Float {
    val nextPercent = (value * 100f).roundToInt() + direction.delta
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private val SIMULATION_SPEEDS = listOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)
private const val DEFAULT_SIMULATION_SPEED_INDEX: Int = 2
private const val DEFAULT_DAMAGE_THRESHOLD_INDEX: Int = 2
