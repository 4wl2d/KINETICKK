// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * The dungeon trial panel's own texts: its label, the rules behind its (!) and the Totem chest's rule;
 * casing is applied by the text style.
 */
internal enum class StructureHudText(
    override val english: String,
    override val russian: String,
) : TextResource {
    Structure("Structure", "Сооружение"),
    Rules(
        "Rooms seal until their waves fall; a guardian holds the last, where the reward waits. Glowing floor strikes. " +
            "Leaving forfeits the reward; a cleared or abandoned structure collapses.",
        "Зал запечатан, пока не падут его волны; последний, с наградой, стережёт страж. Светящийся пол бьёт. " +
            "Уйдя, вы потеряете награду; зачищенное или покинутое сооружение рушится.",
    ),
    /** Joined to [Rules] when the final room pays a Totem chest (its charges); short, as a phone tooltip has no line to spare. */
    TotemChestRule("Totem chest charges: {0}, one Key each.", "Заряды сундука тотема: {0}, по ключу на каждый."),
    AspectFallback(
        "This aspect is already owned or unavailable. Receive {0} base Matter instead; Matter bonuses apply.",
        "Этот аспект уже получен или недоступен. Вместо него вы получите {0} базовой материи с учётом бонусов к её получению.",
    ),
}
