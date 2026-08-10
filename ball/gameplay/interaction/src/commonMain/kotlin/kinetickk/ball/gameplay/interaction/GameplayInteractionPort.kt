// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection

/** Ball-internal bridge used by Interaction without depending on gameplay impl. */
interface GameplayInteractionPort : GameplayPort {
    fun visualFxSnapshot(): VisualFxProjection
}
