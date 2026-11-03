// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.label

/** The key bound to skill slot [slot] (0 Z, 1 X, 2 C) in the player's bindings. */
internal fun GameplayRenderModel.activeSlotKey(slot: Int, language: AppLanguage): String = settings.keyBindings.key(
    when (slot) {
        0 -> GameAction.SKILL_1
        1 -> GameAction.SKILL_2
        else -> GameAction.SKILL_3
    },
).label(language)
