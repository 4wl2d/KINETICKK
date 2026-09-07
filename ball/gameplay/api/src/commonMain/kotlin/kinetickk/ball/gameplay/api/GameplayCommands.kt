// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.dispatch.InlineReply

/** Settings capability of one existing GameplayRun. */
interface GameplaySettings {
    fun applyPreferences(
        preferences: PlayerPreferences,
        reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    )
}

data class GameplaySettingsApplied(
    val runId: RunId,
    val revision: GameplayRevision,
)

interface GameplayLifecycle {
    fun startRun(reply: InlineReply<GameplayRunStarted, GameplayRefusal>)
    fun pauseForOverlay(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>)
    fun exitRun(reply: InlineReply<GameplayRunExited, GameplayRefusal>)
}

data class GameplayRunStarted(val runId: RunId, val revision: GameplayRevision)
data class GameplayOverlayPaused(val runId: RunId, val revision: GameplayRevision)
data class GameplayRunExited(
    val runId: RunId,
    val revision: GameplayRevision,
    val progress: GameplayExitProgressResult,
)

sealed interface GameplayExitProgressResult {
    data object NoProgress : GameplayExitProgressResult
    data object Applied : GameplayExitProgressResult
    data object NotApplied : GameplayExitProgressResult
}

sealed interface GameplayRefusal {
    data object Busy : GameplayRefusal
    data object RevisionCapacityExhausted : GameplayRefusal
    data class DecisionRejected(val reason: GameplayRejection) : GameplayRefusal
}
