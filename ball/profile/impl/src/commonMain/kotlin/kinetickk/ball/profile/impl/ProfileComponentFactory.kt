// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.GameplayProfileRoute
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.SessionProfileRoute
import kinetickk.ball.profile.resource.ExactProfilePersistence
import kinetickk.ball.profile.resource.ProfileProviderMutationResult
import kinetickk.ball.profile.resource.ProfileProviderReadResult
import kinetickk.ball.profile.resource.createProfileResource

/** Exact persistence operations supplied by the platform composition broker. */
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
interface ProfileComponent : ProfilePort, SessionProfileRoute, GameplayProfileRoute

/** Closed physical key contract implemented only by platform composition. */
object ProfilePersistenceContract {
    const val DESKTOP_PROFILE_NODE: String = "kinetickk/profile"
    const val DESKTOP_SNAPSHOT: String = "snapshot"

    const val WEB_SNAPSHOT: String = "kinetickk_profile"
}

fun createProfileComponent(
    persistence: ProfilePersistenceCapability,
    policy: ProfilePolicySnapshot,
    commandResultSink: (ProfileModuleResultDelivery) -> Unit = {},
): ProfileComponent =
    DefaultProfileComponent(
        resource = createProfileResource(ProfilePersistenceAdapter(persistence)),
        policy = policy,
        commandResultSink = commandResultSink,
    )

private class ProfilePersistenceAdapter(
    private val capability: ProfilePersistenceCapability,
) : ExactProfilePersistence {
    override fun readSnapshot(): ProfileProviderReadResult =
        capability.readSnapshot().toProviderResult()

    override fun writeSnapshot(payload: String): ProfileProviderMutationResult =
        capability.writeSnapshot(payload).toProviderResult()
}

private fun ProfilePersistenceReadResult.toProviderResult(): ProfileProviderReadResult = when (this) {
    is ProfilePersistenceReadResult.Observed -> ProfileProviderReadResult.Observed(payload)
    ProfilePersistenceReadResult.Failed -> ProfileProviderReadResult.Failed
}

private fun ProfilePersistenceMutationResult.toProviderResult(): ProfileProviderMutationResult = when (this) {
    ProfilePersistenceMutationResult.COMPLETED -> ProfileProviderMutationResult.COMPLETED
    ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION ->
        ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION
    ProfilePersistenceMutationResult.POSSIBLE_EXECUTION -> ProfileProviderMutationResult.POSSIBLE_EXECUTION
}
