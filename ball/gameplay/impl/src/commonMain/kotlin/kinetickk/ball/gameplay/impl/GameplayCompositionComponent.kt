// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.gameplay.interaction.GameplayRunHost

/** Assembly composite for the active GameplayRun host and its presentation. */
interface GameplayCompositionComponent : GameplayRunHost, GameplayPresentation
