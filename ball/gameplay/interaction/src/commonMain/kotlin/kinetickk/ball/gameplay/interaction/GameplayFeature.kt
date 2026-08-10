// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.Composable
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.ProfileCommandResult

/** Interaction host for the single currently active GameplayRun. */
interface GameplayFeature {
    fun createRun(
        runId: RunId,
        commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayPort

    fun activeRun(): GameplayPort?

    fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted)

    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    )
}
