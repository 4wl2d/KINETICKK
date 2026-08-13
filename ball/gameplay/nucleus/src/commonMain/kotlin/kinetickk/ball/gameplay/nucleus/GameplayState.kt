// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.profile.api.ProfileModuleCommandRequest

data class PendingProfileCommand(
    val request: ProfileModuleCommandRequest,
    val exitCompletion: GameplayCommandSourceToken?,
)

@ConsistentCopyVisibility
data class GameplayState internal constructor(
    val instanceId: GameplayInstanceId,
    val revision: GameplayRevision,
    val phase: GameplayRunPhase,
    val content: GameplayContentSnapshot,
    val engine: EngineState?,
    val pendingProfileCommand: PendingProfileCommand?,
) {
    init {
        require((phase == GameplayRunPhase.CREATED) == (engine == null)) {
            "Only a created GameplayRun may have no simulation engine"
        }
        if (engine != null && phase != GameplayRunPhase.EXITED) {
            require(engine.model.phase.toRunPhase() == phase) {
                "Gameplay lifecycle phase must match the captured simulation phase"
            }
        }
    }

    companion object {
        fun initial(runId: RunId, content: GameplayContentSnapshot): GameplayState = GameplayState(
            instanceId = GameplayInstanceId(runId),
            revision = GameplayRevision.ZERO,
            phase = GameplayRunPhase.CREATED,
            content = content,
            engine = null,
            pendingProfileCommand = null,
        )
    }
}

private fun GamePhase.toRunPhase(): GameplayRunPhase = when (this) {
    GamePhase.RUNNING -> GameplayRunPhase.RUNNING
    GamePhase.PAUSED -> GameplayRunPhase.PAUSED
    GamePhase.CHOICE -> GameplayRunPhase.CHOICE
    GamePhase.GAME_OVER -> GameplayRunPhase.GAME_OVER
    GamePhase.VICTORY -> GameplayRunPhase.VICTORY
}
