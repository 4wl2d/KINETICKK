// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

/** Presentation-owned requests delivered to the AppSession host. */
sealed interface GameplayInteractionOutput {
    data object OpenSettings : GameplayInteractionOutput
    data object OpenRebirth : GameplayInteractionOutput
    data object ExitToHome : GameplayInteractionOutput
    data object RestartRun : GameplayInteractionOutput
}
