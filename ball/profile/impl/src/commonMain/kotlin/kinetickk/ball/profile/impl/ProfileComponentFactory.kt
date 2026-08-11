// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.GameplayProfileRoute
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.SessionProfileRoute
import kinetickk.ball.profile.resource.ExactProfilePersistence
import kinetickk.ball.profile.resource.createProfileResource

/** Exact persistence operations supplied by the platform composition broker. */
interface ProfilePersistenceCapability {
    fun readV4(): String?

    fun writeV4(payload: String)

    fun readLegacyProgressV2(): String?

    fun readLegacyMatter(): String?

    fun removeLegacyProgressV2()

    fun removeLegacyMatter()
}

/** Assembly-only composite implemented by the one application-lifetime Profile component. */
interface ProfileComponent : ProfilePort, SessionProfileRoute, GameplayProfileRoute

/** Closed physical key contract implemented only by platform composition. */
object ProfilePersistenceContract {
    const val DESKTOP_PROFILE_NODE: String = "kinetickk/profile"
    const val DESKTOP_SNAPSHOT_V4: String = "snapshot_v4"
    const val DESKTOP_LEGACY_NODE: String = "kinetickk/progression"
    const val DESKTOP_LEGACY_PROGRESS_V2: String = "progress_v2"
    const val DESKTOP_LEGACY_MATTER: String = "kinetickk_matter"

    const val WEB_SNAPSHOT_V4: String = "kinetickk_profile_v4"
    const val WEB_LEGACY_PROGRESS_V2: String = "kinetickk_progress_v2"
    const val WEB_LEGACY_MATTER: String = "kinetickk_matter"
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
    override fun readV4(): String? = capability.readV4()

    override fun writeV4(payload: String) = capability.writeV4(payload)

    override fun readLegacyProgressV2(): String? = capability.readLegacyProgressV2()

    override fun readLegacyMatter(): String? = capability.readLegacyMatter()

    override fun removeLegacyProgressV2() = capability.removeLegacyProgressV2()

    override fun removeLegacyMatter() = capability.removeLegacyMatter()
}
