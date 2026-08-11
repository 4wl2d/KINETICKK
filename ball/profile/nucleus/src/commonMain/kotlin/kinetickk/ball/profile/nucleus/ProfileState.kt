// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.immutableSetOf

/** Complete immutable snapshot owned and published by the Profile acceptor. */
data class ProfileState(
    val instanceId: ProfileInstanceId,
    val revision: ProfileRevision,
    val profile: PlayerProfile,
    val policy: ProfilePolicySnapshot,
    val bootstrap: ProfileBootstrapStatus,
    val reset: ProfileResetStatus,
    val persistence: ProfilePersistenceStatus,
) {
    init {
        require(instanceId == LOCAL_PROFILE_INSTANCE_ID) { "Only the local Profile instance is supported" }
    }

    companion object {
        fun initial(
            policy: ProfilePolicySnapshot,
            bootstrapResult: ProfileBootstrapResourceResult,
        ): ProfileState = constructInitialProfileState(policy, bootstrapResult)
    }
}

private fun constructInitialProfileState(
    policy: ProfilePolicySnapshot,
    result: ProfileBootstrapResourceResult,
): ProfileState {
    val default = defaultPlayerProfile(policy)

    fun state(
        revision: ProfileRevision,
        profile: PlayerProfile = default,
        bootstrap: ProfileBootstrapStatus,
        reset: ProfileResetStatus,
        persistence: ProfilePersistenceStatus,
    ): ProfileState = ProfileState(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = revision,
        profile = profile,
        policy = policy,
        bootstrap = bootstrap,
        reset = reset,
        persistence = persistence,
    )

    fun resetRequired(
        revision: ProfileRevision,
        profile: PlayerProfile,
        reason: ProfileResetReason,
        legacyKeys: kinetickk.ball.profile.api.ProfileLegacyKeys,
        persistence: ProfilePersistenceStatus,
    ): ProfileState = state(
        revision = revision,
        profile = profile,
        bootstrap = ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResetRequired(reason)),
        reset = ProfileResetStatus.ConfirmationRequired(reason, legacyKeys),
        persistence = persistence,
    )

    return when (result) {
        is ProfileBootstrapResourceResult.ResourceFailure -> state(
            revision = ProfileRevision(1L),
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResourceFailure(result.reason),
            ),
            reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileBootstrapResourceResult.Rejected -> resetRequired(
            revision = ProfileRevision(1L),
            profile = default,
            reason = ProfileResetReason.InvalidV4(result.reason),
            legacyKeys = result.legacyKeys,
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileBootstrapResourceResult.Observed -> {
            val snapshot = result.snapshot
            when {
                snapshot == null && result.legacyKeys.isEmpty -> state(
                    revision = ProfileRevision(1L),
                    bootstrap = ProfileBootstrapStatus.Ready,
                    reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                snapshot == null -> resetRequired(
                    revision = ProfileRevision(1L),
                    profile = default,
                    reason = ProfileResetReason.LegacyDataDetected,
                    legacyKeys = result.legacyKeys,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                snapshot.contentVersion != policy.version -> resetRequired(
                    revision = ProfileRevision(1L),
                    profile = default,
                    reason = ProfileResetReason.ContentVersionMismatch(policy.version, snapshot.contentVersion),
                    legacyKeys = result.legacyKeys,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                snapshot.revision.value == Long.MAX_VALUE ||
                    !isPolicyCompatibleAtConstruction(snapshot.profile, policy) -> resetRequired(
                    revision = ProfileRevision(1L),
                    profile = default,
                    reason = ProfileResetReason.IncompatibleProfile,
                    legacyKeys = result.legacyKeys,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                result.legacyKeys.isEmpty -> state(
                    revision = ProfileRevision(snapshot.revision.value + 1L),
                    profile = snapshot.profile,
                    bootstrap = ProfileBootstrapStatus.Ready,
                    reset = ProfileResetStatus.NotRequired(snapshot.legacyResetConfirmed),
                    persistence = ProfilePersistenceStatus.Persisted(snapshot.revision),
                )
                snapshot.legacyResetConfirmed -> {
                    val purge = ProfileLegacyPurgeResult.Partial(result.legacyKeys)
                    state(
                        revision = ProfileRevision(snapshot.revision.value + 1L),
                        profile = snapshot.profile,
                        bootstrap = ProfileBootstrapStatus.Blocked(
                            ProfileBootstrapBlockReason.ResetNeedsAttention(purge),
                        ),
                        reset = ProfileResetStatus.NeedsAttention(result.legacyKeys, purge),
                        persistence = ProfilePersistenceStatus.Persisted(snapshot.revision),
                    )
                }
                else -> resetRequired(
                    revision = ProfileRevision(snapshot.revision.value + 1L),
                    profile = snapshot.profile,
                    reason = ProfileResetReason.LegacyDataDetected,
                    legacyKeys = result.legacyKeys,
                    persistence = ProfilePersistenceStatus.Persisted(snapshot.revision),
                )
            }
        }
    }
}

private fun isPolicyCompatibleAtConstruction(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
): Boolean {
    val preferences = profile.preferences
    if (
        !preferences.masterVolume.isFinite() ||
        !preferences.simulationSpeed.isFinite() ||
        !preferences.textScale.isFinite() ||
        preferences != preferences.normalized() ||
        preferences.simulationSpeed !in kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS ||
        preferences.damageNumberTierThreshold !in
            kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
    ) return false
    if (profile.economy.matter < 0L || profile.economy.lifetimeMatter < profile.economy.matter) {
        return false
    }
    val allowedShapes = policy.coreShapes.map { it.id }
    val allowedWeapons = policy.weapons.map { it.id }
    if (
        profile.loadout.coreShape !in allowedShapes ||
        profile.economy.lifetimeMatter < policy.coreShape(profile.loadout.coreShape).unlockLifetimeMatter ||
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

internal fun defaultPlayerProfile(policy: ProfilePolicySnapshot): PlayerProfile {
    val defaultWeapon = policy.weapons.first().id
    return PlayerProfile(
        loadout = PlayerLoadout(
            coreShape = policy.coreShapes.first().id,
            selectedWeapon = defaultWeapon,
            unlockedWeapons = immutableSetOf(defaultWeapon),
        ),
        labProgress = LabProgress(List(policy.metaUpgrades.size) { 0 }),
        rebirthProgress = RebirthProgress(
            level = policy.rebirth.minimumLevel,
            highestCleared = -1,
        ),
    )
}
