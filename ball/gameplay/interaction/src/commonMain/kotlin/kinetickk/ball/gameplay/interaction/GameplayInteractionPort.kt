// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.engine.GameDispatchResult
import kinetickk.ball.gameplay.nucleus.engine.GameSnapshot
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAction

/** Ball-internal bridge used by Interaction without depending on gameplay impl. */
interface GameplayInteractionPort {
    fun dispatch(action: GameplayAction): GameDispatchResult
    fun snapshot(): GameSnapshot
    fun visualFxSnapshot(): VisualFxProjection
}
