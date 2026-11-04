// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.Composable
import kinetickk.ball.gameplay.api.GameplayPresentationPort

/** Presentation-only host; neither inter-Ball ingress is representable. */
interface GameplayPresentation {
    fun activePresentation(): GameplayPresentationPort?

    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    )
}
