// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

internal const val FREEZE_IMMUNITY_SECONDS: Float = 2.5f
internal const val ELITE_FREEZE_IMMUNITY_SECONDS: Float = 5f
internal const val ARCHITECT_FREEZE_IMMUNITY_SECONDS: Float = 8f
internal const val BREAK_REARM_SECONDS: Float = 1f
internal const val ENTROPY_DAMAGE_PER_STACK: Float = 0.8f
internal const val DOT_PULSES_PER_SECOND: Float = 4f
internal const val MAX_STATUS_CUES_PER_STEP: Int = 6
internal const val BLIGHT_GARDEN_HEAL: Float = 4f
internal const val BLIGHT_GARDEN_HEAL_COOLDOWN: Float = 0.5f

/** Resonance (Sonic): each stack adds this fraction of impact and dash damage taken. */
internal const val RESONANCE_IMPACT_PER_STACK: Float = 0.03f
/** Hollow (Void): each stack remembers this fraction of the damage the enemy takes. */
internal const val HOLLOW_MEMORY_PER_STACK: Float = 0.02f
/** Hollow never cuts an enemy's damage output below this factor. */
internal const val HOLLOW_OUTPUT_FLOOR: Float = 0.5f
/** Delay between an Unmake and the strike that releases the remembered damage. */
internal const val UNMAKE_DELAY_SECONDS: Float = 0.35f
/** An Unmake takes at most this share of the victim's maximum integrity (weighted for bosses). */
internal const val UNMAKE_INTEGRITY_CAP: Float = 0.4f
/** Velocity a Shatter gives the enemies it throws, before their knockback factor. */
internal const val SHATTER_THROW_SPEED: Float = 680f
/** Thunder Choir: radius in which a Shatter looks for Resonant enemies. */
internal const val THUNDER_CHOIR_RADIUS: Float = 260f
/** Thunder Choir: bonus to the knockback you deal, and the extra recoil you take. */
internal const val THUNDER_CHOIR_KNOCKBACK: Float = 0.4f
internal const val THUNDER_CHOIR_RECOIL: Float = 0.15f
/** Null Covenant: share of maximum integrity the liability removes. */
internal const val NULL_COVENANT_INTEGRITY_CUT: Float = 0.05f

/** Cinder Crown's liability: an Ignite near the Core burns this share of maximum integrity. */
internal const val CINDER_SELF_BURN: Float = 0.03f

/** Pandemic Codex's liability: enemies carrying a status move this share faster. */
internal const val PANDEMIC_CARRIER_HASTE: Float = 0.1f
