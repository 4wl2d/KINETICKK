// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

/** The only Profile key supported by this application. */
enum class LocalPlayerId(
    val stableValue: String,
) {
    LOCAL_PLAYER("local-player"),
}

/** Stable identity of the application-lifetime local Profile instance. */
data class ProfileInstanceId(
    val playerId: LocalPlayerId,
) {
    val canonicalValue: String
        get() = "kinetickk.local/Profile/${playerId.stableValue}"
}

val LOCAL_PROFILE_INSTANCE_ID: ProfileInstanceId =
    ProfileInstanceId(LocalPlayerId.LOCAL_PLAYER)

/** Monotonic revision of accepted Profile frames. */
data class ProfileRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Profile revision must be non-negative" }
    }

    companion object {
        val ZERO: ProfileRevision = ProfileRevision(0L)
    }
}
