// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

data class RunId(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Gameplay run ID must be non-negative" }
    }
}

data class GameplayInstanceId(
    val runId: RunId,
) {
    val canonicalValue: String
        get() = "kinetickk.local/GameplayRun/${runId.value}"
}

data class GameplayRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Gameplay revision must be non-negative" }
    }

    companion object {
        val ZERO: GameplayRevision = GameplayRevision(0L)
    }
}

sealed interface GameplayCommandSource {
    val canonicalValue: String

    data object LocalSession : GameplayCommandSource {
        override val canonicalValue: String = "kinetickk.local/AppSession/local-session"
    }
}

data class GameplaySemanticHandle(
    val sourceInstance: GameplayCommandSource,
    val sourceRevision: Long,
    val sourceOrdinal: Int,
) {
    init {
        require(sourceRevision >= 0L) { "Command source revision must be non-negative" }
        require(sourceOrdinal >= 0) { "Command source ordinal must be non-negative" }
    }
}

data class GameplayCommandSourceToken(
    val semanticHandle: GameplaySemanticHandle,
    val targetInstance: GameplayInstanceId,
    val causalScope: Long,
    val causalDepth: Int,
) {
    init {
        require(causalScope >= 0L) { "Command causal scope must be non-negative" }
        require(causalDepth >= 0) { "Command causal depth must be non-negative" }
    }

    val sourceInstance: GameplayCommandSource
        get() = semanticHandle.sourceInstance

    val sourceRevision: Long
        get() = semanticHandle.sourceRevision

    val sourceOrdinal: Int
        get() = semanticHandle.sourceOrdinal
}

data class GameplayResultSourceToken(
    val semanticHandle: GameplaySemanticHandle,
    val targetInstance: GameplayInstanceId,
    val targetRevision: GameplayRevision,
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

enum class GameplayEffectiveProtocolIdentity {
    SESSION_START,
    SESSION_PAUSE,
    SESSION_PREFERENCES,
    SESSION_EXIT,
}

enum class GameplayCommandIssuerProvenance {
    LOCAL_SESSION_STATIC_BINDING,
}

enum class GameplayResultIssuerProvenance {
    GAMEPLAY_RUN_STATIC_BINDING,
}

data class GameplayTargetBoundaryProvenance(
    val targetInstance: GameplayInstanceId,
    val effectiveProtocolIdentity: GameplayEffectiveProtocolIdentity,
)
