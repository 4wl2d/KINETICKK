// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

/**
 * One type role of `docs/design/redesign/tokens.json` `typography.roles`. [family] is the role's family key
 * (`wide`, `cond`, `body` or `mono`, the [InterfaceTypography] families); [sizeSp] is null where the role has no
 * fixed size; [trackingEm] and [lineHeight] are multiples of the font size.
 */
data class KkTypeRole(
    val family: String,
    val weight: Int,
    val sizeSp: Float?,
    val trackingEm: Float,
    val lineHeight: Float,
    val uppercase: Boolean,
    val italic: Boolean,
)

/** The type roles of tokens.json (SPEC section 4), one per [KkTextRole]. */
object KkTypeScale {
    /** `t-wide`: clock, titles, big numerals, speed readout. */
    val Wide = KkTypeRole("wide", 900, null, 0f, 0.9f, uppercase = true, italic = false)

    /** `t-cond`: menus, buttons, headings, callouts, damage numbers. */
    val Cond = KkTypeRole("cond", 900, null, 0.005f, 0.86f, uppercase = true, italic = true)

    /** `t-label`: labels, tabs, tags. */
    val Label = KkTypeRole("cond", 800, 15f, 0.09f, 1f, uppercase = true, italic = false)

    /** `t-body`: descriptions, tooltips. */
    val Body = KkTypeRole("body", 400, 16f, 0f, 1.4f, uppercase = false, italic = false)

    /** `t-mono`: instrument readouts, small data. */
    val Mono = KkTypeRole("mono", 500, 11f, 0.05f, 1.35f, uppercase = true, italic = false)
}
