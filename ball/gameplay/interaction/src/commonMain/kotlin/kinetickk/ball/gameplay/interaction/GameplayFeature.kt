// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.Composable
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId

/** Creates and exposes the one active GameplayRun. */
interface GameplayRunHost {
    fun createRun(
        runId: RunId,
    ): GameplayRunPort

    fun activeRun(): GameplayRunPort?
}

/** Presentation-only host; neither inter-Ball ingress is representable. */
interface GameplayPresentation {
    fun activePresentation(): GameplayPresentationPort?

    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    )
}
