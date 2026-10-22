// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

/**
 * Lingering area effects. Ordinals are packed into [CombatFieldTable] and mirrored by
 * [kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual], so kinds are only ever appended.
 * [cap] is the most fields of the kind that coexist (0 = no per-kind cap): a new field past it
 * replaces the oldest one of its kind, so a new reaction is always visible.
 */
internal enum class CombatFieldKind(val cap: Int) {
    FUSION(3),
    ROT_WELL(3),
    DARK_STAR(4),
    ECHO_RING(0),
    NULL_WELL(3),
    PLASMA(3),
    HUSH(2),
    TIME_BUBBLE(2),
    ATTRACTOR(2),
    FROST_LINE(0),
    STORM_COIL(0),
    GRAVITY_WELL(0),
}

/** What created a field. Skills own one field per slot; reactions and items share the rest of the bound. */
internal enum class FieldSource { REACTION, ITEM, SKILL }

internal object CombatField {
    /** Fields pull every step and pulse damage (and optionally a status) every [TICK_SECONDS]. */
    const val TICK_SECONDS: Float = 0.4f
}
