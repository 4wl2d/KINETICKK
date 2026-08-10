// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfilePersistenceStatus
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
    val nextResourceOrdinal: Int,
) {
    init {
        require(instanceId == LOCAL_PROFILE_INSTANCE_ID) { "Only the local Profile instance is supported" }
        require(nextResourceOrdinal >= 0) { "Profile Resource ordinal must be non-negative" }
    }

    companion object {
        fun initial(policy: ProfilePolicySnapshot): ProfileState = ProfileState(
            instanceId = LOCAL_PROFILE_INSTANCE_ID,
            revision = ProfileRevision.ZERO,
            profile = defaultPlayerProfile(policy),
            policy = policy,
            bootstrap = ProfileBootstrapStatus.AwaitingResource,
            reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
            persistence = ProfilePersistenceStatus.NotAttempted,
            nextResourceOrdinal = 0,
        )
    }
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
