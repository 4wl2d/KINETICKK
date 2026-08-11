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

/** Stable semantic identity created by an accepted source command frame. */
data class ProfileSemanticHandle(
    val sourceInstance: ProfileCommandSource,
    val sourceRevision: Long,
    val sourceOrdinal: Int,
) {
    init {
        require(sourceRevision >= 0L) { "Command source revision must be non-negative" }
        require(sourceOrdinal >= 0) { "Command source ordinal must be non-negative" }
    }
}

/**
 * Complete accepted-source token for one Profile command route.
 *
 * The causal scope and depth are binding evidence. They are carried across the Ball boundary but
 * never become DecisionContext or a hidden input to Profile business policy.
 */
data class ProfileCommandSourceToken(
    val semanticHandle: ProfileSemanticHandle,
    val targetInstance: ProfileInstanceId,
    val causalScope: Long,
    val causalDepth: Int,
) {
    init {
        require(causalScope >= 0L) { "Command causal scope must be non-negative" }
        require(causalDepth >= 0) { "Command causal depth must be non-negative" }
    }

    val sourceInstance: ProfileCommandSource
        get() = semanticHandle.sourceInstance

    val sourceRevision: Long
        get() = semanticHandle.sourceRevision

    val sourceOrdinal: Int
        get() = semanticHandle.sourceOrdinal
}

/** Accepted target-frame token for a Profile ModuleResultOutput. */
data class ProfileResultSourceToken(
    val semanticHandle: ProfileSemanticHandle,
    val targetInstance: ProfileInstanceId,
    val targetRevision: ProfileRevision,
    val sourceOrdinal: Int,
    val causalScope: Long,
    val causalDepth: Int,
) {
    init {
        require(sourceOrdinal >= 0) { "Result source ordinal must be non-negative" }
        require(causalScope >= 0L) { "Result causal scope must be non-negative" }
        require(causalDepth >= 0) { "Result causal depth must be non-negative" }
    }
}

/** The six exact same-build Profile command/result mappings. */
enum class ProfileEffectiveProtocolIdentity {
    SESSION_CORE_SHAPE,
    SESSION_MUTE,
    SESSION_REBIRTH,
    SESSION_RESET_CONFIRM,
    SESSION_RESET_RETRY,
    GAMEPLAY_PROGRESS,
}

/** Statically verified issuer of a Profile ModuleCommandPulse. */
enum class ProfileCommandIssuerProvenance {
    LOCAL_SESSION_STATIC_BINDING,
    GAMEPLAY_RUN_STATIC_BINDING,
}

/** Statically verified issuer of a Profile ModuleResultPulse. */
enum class ProfileResultIssuerProvenance {
    LOCAL_PROFILE_STATIC_BINDING,
}

/** Provenance of a Profile pre-acceptance carrier. */
data class ProfileTargetBoundaryProvenance(
    val targetInstance: ProfileInstanceId,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
)
