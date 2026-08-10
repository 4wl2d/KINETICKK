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

/** Closed target-owned view of the two instances allowed to command Profile. */
sealed interface ProfileCommandSource {
    val canonicalValue: String

    data object LocalSession : ProfileCommandSource {
        override val canonicalValue: String = "kinetickk.local/AppSession/local-session"
    }

    data class GameplayRun(
        val runId: Long,
    ) : ProfileCommandSource {
        init {
            require(runId >= 0L) { "Gameplay run ID must be non-negative" }
        }

        override val canonicalValue: String
            get() = "kinetickk.local/GameplayRun/$runId"
    }
}

/** Correlation carried by every cross-Ball Profile command and result. */
data class ProfileCommandRef(
    val sourceInstance: ProfileCommandSource,
    val targetInstance: ProfileInstanceId,
    val sourceRevision: Long,
    val ordinal: Int,
) {
    init {
        require(sourceRevision >= 0L) { "Command source revision must be non-negative" }
        require(ordinal >= 0) { "Command ordinal must be non-negative" }
    }
}

/** Evidence supplied by the static binding after reserving the completion path. */
data class ProfileCommandAdmission(
    val commandRef: ProfileCommandRef,
)
