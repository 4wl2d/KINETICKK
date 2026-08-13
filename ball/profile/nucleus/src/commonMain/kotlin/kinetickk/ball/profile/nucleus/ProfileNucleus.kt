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
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultOutput
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure, deterministic authority for every Profile decision and projection. */
object ProfileNucleus {
    fun decide(
        state: ProfileState,
        pulse: ProfileNucleusPulse,
        context: ProfileContext = ProfileContext,
    ): ProfileDecision {
        return when (pulse) {
            is ProfileNucleusPulse.Intent -> {
                mutationGate(state)?.let { return rejected(it) }
                decideMutation(state, pulse.intent, null)
            }
            is ProfileNucleusPulse.ModuleCommand -> decideModuleCommand(state, pulse.pulse)
            is ProfileNucleusPulse.WriteCompleted ->
                decideWrite(state, pulse.effectRef, pulse.result)
        }
    }

    private fun decideModuleCommand(
        state: ProfileState,
        pulse: kinetickk.ball.profile.api.ProfileModuleCommandPulse,
    ): ProfileDecision {
        val completion = ProfileCommandCompletion(
            commandSource = pulse.commandSource,
        )
        mutationGate(state)?.let { return rejected(it) }
        return when (val command = pulse.command) {
            is ProfileModuleCommand.SelectCoreShape ->
                selectCoreShape(state, command.shape, completion)
            ProfileModuleCommand.ToggleMute -> toggleMute(state, completion)
            ProfileModuleCommand.AdvanceRebirth -> advanceRebirth(state, completion)
            is ProfileModuleCommand.ApplyGameplayProgress ->
                applyGameplayProgress(state, command.update, completion)
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
            persistence = state.persistence,
        )

    private fun decideMutation(
        state: ProfileState,
        pulse: ProfilePulse.Business,
        completion: ProfileCommandCompletion?,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.AdjustPreference -> adjustPreference(state, pulse.adjustment, completion)
        is ProfilePulse.PurchaseMetaUpgrade -> purchaseMetaUpgrade(state, pulse.id, completion)
        is ProfilePulse.PurchaseOrEquipWeapon -> purchaseOrEquipWeapon(state, pulse.id, completion)
    }

    private fun adjustPreference(
        state: ProfileState,
        adjustment: ProfilePreferenceAdjustment,
        completion: ProfileCommandCompletion?,
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
                val currentIndex = SIMULATION_SPEED_OPTIONS.indices.minByOrNull { index ->
                    abs(SIMULATION_SPEED_OPTIONS[index] - current.simulationSpeed)
                } ?: DEFAULT_SIMULATION_SPEED_INDEX
                current.copy(
                    simulationSpeed = SIMULATION_SPEED_OPTIONS[
                        (currentIndex + adjustment.direction.delta).coerceIn(SIMULATION_SPEED_OPTIONS.indices)
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
            completion = completion,
            commandResult = ProfileModuleResult.PreferencesChanged(next),
        )
    }

    private fun toggleMute(state: ProfileState, completion: ProfileCommandCompletion?): ProfileDecision {
        val current = state.profile.preferences
        val enable = !current.soundEnabled && !current.musicEnabled
        val next = current.copy(soundEnabled = enable, musicEnabled = enable)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(preferences = next),
            completion = completion,
            commandResult = ProfileModuleResult.PreferencesChanged(next),
        )
    }

    private fun purchaseMetaUpgrade(
        state: ProfileState,
        id: MetaUpgradeId,
        completion: ProfileCommandCompletion?,
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
            completion = completion,
            commandResult = null,
        )
    }

    private fun selectCoreShape(
        state: ProfileState,
        shape: kinetickk.ball.content.api.CoreShape,
        completion: ProfileCommandCompletion?,
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
            completion = completion,
            commandResult = ProfileModuleResult.CoreShapeSelected(shape),
        )
    }

    private fun purchaseOrEquipWeapon(
        state: ProfileState,
        id: kinetickk.ball.content.api.WeaponId,
        completion: ProfileCommandCompletion?,
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
            completion = completion,
            commandResult = null,
        )
    }

    private fun advanceRebirth(
        state: ProfileState,
        completion: ProfileCommandCompletion?,
    ): ProfileDecision {
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
            completion = completion,
            commandResult = ProfileModuleResult.RebirthAdvanced(next),
        )
    }

    private fun applyGameplayProgress(
        state: ProfileState,
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
        completion: ProfileCommandCompletion?,
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
            completion = completion,
            commandResult = ProfileModuleResult.GameplayProgressApplied,
        )
    }

    private fun acceptedMutation(
        state: ProfileState,
        nextProfile: PlayerProfile,
        completion: ProfileCommandCompletion?,
        commandResult: ProfileModuleResult?,
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
        val outputs = completion?.let { command ->
            checkNotNull(commandResult) { "Every admitted Profile command must define a result" }
            immutableListOf(
                persist,
                ProfileOutput.CompleteCommand(
                    ProfileModuleResultOutput(
                        semanticHandle = command.commandSource.semanticHandle,
                        sourceOrdinal = 1,
                        commandSource = command.commandSource,
                        result = commandResult,
                    ),
                ),
            )
        } ?: immutableListOf(persist)
        return accepted(nextState, outputs)
    }

    private fun decideWrite(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileWriteResult,
    ): ProfileDecision {
        val pending = checkNotNull(state.persistence as? ProfilePersistenceStatus.Pending)
        check(pending.effectRef == effectRef)
        val persistence = checkNotNull(result.toPersistenceStatus(pending))
        return accepted(state.copy(revision = state.revision.next(), persistence = persistence))
    }

    private fun mutationGate(state: ProfileState): ProfileRejection? = when (state.bootstrap) {
        ProfileBootstrapStatus.Ready -> null
        is ProfileBootstrapStatus.Blocked -> ProfileRejection.BootstrapNotReady
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

    private fun canAdvanceRebirth(state: ProfileState): Boolean =
        state.bootstrap == ProfileBootstrapStatus.Ready &&
            state.profile.rebirthProgress.level < state.policy.rebirth.maximumLevel &&
            state.profile.rebirthProgress.highestCleared >= state.profile.rebirthProgress.level

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

    private fun ProfileRevision.next(): ProfileRevision {
        check(value < Long.MAX_VALUE) { "Profile revision exhausted" }
        return ProfileRevision(value + 1L)
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

}

private const val PROFILE_RESOURCE_OUTPUT_ORDINAL: Int = 0

private data class ProfileCommandCompletion(
    val commandSource: kinetickk.ball.profile.api.ProfileCommandSourceToken,
)

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

private const val DEFAULT_SIMULATION_SPEED_INDEX: Int = 2
private const val DEFAULT_DAMAGE_THRESHOLD_INDEX: Int = 2
