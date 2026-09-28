// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** World-layer words of the redesign; display casing is applied by the text style. */
internal enum class WorldRedesignText(override val english: String, override val russian: String) : TextResource {
    /** Distance to an off-screen point of interest or totem. */
    Distance("{0}m", "{0} м"),

    /** Stamp on critical damage numbers. */
    CriticalHit("Crit", "Крит"),

    /** Seconds already spent inside the collapsing orbit's ring, in tenths ("5.2s"). */
    OrbitSeconds("{0}s", "{0} с"),
}
