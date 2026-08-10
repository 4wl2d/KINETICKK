// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.Composable
import kinetickk.ball.gameplay.api.GameplayController
import kinetickk.ball.gameplay.api.GameplayOutput

/** Compose-facing gameplay facade. Semantic lifecycle control remains in gameplay API. */
interface GameplayFeature : GameplayController {
    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayOutput) -> Unit,
    )
}
