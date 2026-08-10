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

data class GameplayCommandRef(
    val sourceInstance: GameplayCommandSource,
    val targetInstance: GameplayInstanceId,
    val sourceRevision: Long,
    val ordinal: Int,
) {
    init {
        require(sourceRevision >= 0L) { "Command source revision must be non-negative" }
        require(ordinal >= 0) { "Command ordinal must be non-negative" }
    }
}

data class GameplayCommandAdmission(
    val commandRef: GameplayCommandRef,
)
