// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.immutableSetOf

/** Complete immutable snapshot owned and published by the Profile acceptor. */
data class ProfileState(
    val instanceId: ProfileInstanceId,
    val revision: ProfileRevision,
    val profile: PlayerProfile,
    val policy: ProfilePolicySnapshot,
    val bootstrap: ProfileBootstrapStatus,
    val persistence: ProfilePersistenceStatus,
) {
    init {
        require(instanceId == LOCAL_PROFILE_INSTANCE_ID) { "Only the local Profile instance is supported" }
    }

    companion object {
        fun initial(
            policy: ProfilePolicySnapshot,
            snapshotReadResult: ProfileSnapshotReadResult,
        ): ProfileState = constructInitialProfileState(policy, snapshotReadResult)
    }
}

private fun constructInitialProfileState(
    policy: ProfilePolicySnapshot,
    result: ProfileSnapshotReadResult,
): ProfileState {
    val default = defaultPlayerProfile(policy)

    fun state(
        revision: ProfileRevision,
        profile: PlayerProfile = default,
        bootstrap: ProfileBootstrapStatus,
        persistence: ProfilePersistenceStatus,
    ): ProfileState = ProfileState(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = revision,
        profile = profile,
        policy = policy,
        bootstrap = bootstrap,
        persistence = persistence,
    )

    return when (result) {
        is ProfileSnapshotReadResult.ResourceFailure -> state(
            revision = ProfileRevision(1L),
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResourceFailure(result.reason),
            ),
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileSnapshotReadResult.Rejected -> state(
            revision = ProfileRevision(1L),
            bootstrap = ProfileBootstrapStatus.Ready,
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileSnapshotReadResult.Observed -> {
            val snapshot = result.snapshot
            when {
                snapshot == null -> state(
                    revision = ProfileRevision(1L),
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                snapshot.revision.value > MAX_LOADABLE_SNAPSHOT_REVISION ||
                    !isPolicyCompatibleAtConstruction(snapshot.profile, policy) -> state(
                    revision = ProfileRevision(1L),
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                else -> state(
                    revision = ProfileRevision(snapshot.revision.value + 1L),
                    profile = snapshot.profile,
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.Persisted(snapshot.revision),
                )
            }
        }
    }
}

/**
 * Loading advances once, and one complete mutation advances for acceptance and write completion.
 * Snapshots above this boundary cannot support that smallest useful current-schema lifecycle.
 */
private const val MAX_LOADABLE_SNAPSHOT_REVISION: Long = Long.MAX_VALUE - 3L

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
