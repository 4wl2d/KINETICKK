// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.ball.profile.api.ProfileRebirth
import kinetickk.ball.profile.api.ProfileProgress

/**
 * Exact persistence operations supplied by the platform composition broker.
 *
 * Platform stores, roots, nodes, key selection, bulk clearing, and global acquisition are
 * deliberately absent. The app platform broker is the only place that may hold those powers.
 */
interface ProfilePersistenceCapability {
    fun readSnapshot(): ProfilePersistenceReadResult

    fun writeSnapshot(payload: String): ProfilePersistenceMutationResult
}

/** Closed provider evidence for an exact, non-mutating persistence read. */
sealed interface ProfilePersistenceReadResult {
    data class Observed(val payload: String?) : ProfilePersistenceReadResult

    data object Failed : ProfilePersistenceReadResult
}

/** Closed provider evidence for an exact persistence call that may have mutated storage. */
enum class ProfilePersistenceMutationResult {
    COMPLETED,
    FAILED_BEFORE_EXECUTION,
    POSSIBLE_EXECUTION,
}

/** Assembly-only composite implemented by the one application-lifetime Profile component. */
interface ProfileComponent : ProfilePort, ProfileSettings, ProfileLoadout, ProfileRebirth, ProfileProgress, kinetickk.ball.profile.api.ProfileCollectionVisits

/** Closed physical key contract implemented only by platform composition. */
object ProfilePersistenceContract {
    const val DESKTOP_PROFILE_NODE: String = "kinetickk/profile-v2"
    const val DESKTOP_SNAPSHOT: String = "snapshot"

    const val WEB_SNAPSHOT: String = "kinetickk_profile_v2"
}

fun createProfileComponent(
    persistence: ProfilePersistenceCapability,
    policy: ProfilePolicySnapshot,
): ProfileComponent =
    DefaultProfileComponent(
        resource = createProfileResource(persistence),
        policy = policy,
    )
