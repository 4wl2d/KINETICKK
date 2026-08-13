// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot

/** Ball-internal bridge. Interaction has local Intent authority, never Session command authority. */
interface GameplayInteractionPort {
    val instanceId: GameplayInstanceId

    fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance

    fun renderSnapshot(): GameplayRenderSnapshot

    fun visualFxSnapshot(): VisualFxProjection
}
