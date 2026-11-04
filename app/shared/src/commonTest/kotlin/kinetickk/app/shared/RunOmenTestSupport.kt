// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.assertIs

/**
 * Takes the No omen card when the run shows the omen offer (the production start opens it at the end of the first fixed
 * step). True when it declined one.
 */
internal fun GameplayInteractionPort.declineOmenIfOffered(): Boolean {
    val model = renderSnapshot().renderModel ?: return false
    if (model.phase != GamePhase.CHOICE || model.choiceType != ChoiceType.OMEN) return false
    val decline = GameplayInteractionPulse.ChoiceSelected.fromValidated(model.choices.indexOfFirst { it.omenId == null })
    assertIs<GameplayAcceptance.Accepted>(accept(decline))
    return true
}
