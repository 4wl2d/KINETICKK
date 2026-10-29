// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** The dungeon trial panel's own texts: its label and the rules behind its (!); casing is applied by the text style. */
internal enum class StructureHudText(
    override val english: String,
    override val russian: String,
) : TextResource {
    Structure("Structure", "Сооружение"),
    Rules(
        "Entering a room seals its doors until its garrison falls. The reward waits in the last room; leaving the " +
            "structure forfeits it.",
        "Войдя в зал, вы запечатываете его двери, пока не падёт гарнизон. Награда ждёт в последнем зале; покинув " +
            "сооружение, вы её потеряете.",
    ),
    AspectFallback(
        "This aspect is already owned or unavailable. Receive {0} base Matter instead; Matter bonuses apply.",
        "Этот аспект уже получен или недоступен. Вместо него вы получите {0} базовой материи с учётом бонусов к её получению.",
    ),
}
