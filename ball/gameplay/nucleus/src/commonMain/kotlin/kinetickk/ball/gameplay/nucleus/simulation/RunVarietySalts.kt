// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * Salts of the run variety's randomness. Every roll is a stateless [runHash] of the run seed under one of these salts
 * (or the omen offer's local stream), so no existing stream gains a draw. The Keynote is the one exception: its two
 * draws come from the draft deck's own stream after the deal.
 */

/** Seed salt of the omen offer's local stream, discarded after the offer. */
internal const val OMEN_SEED_SALT: Int = 0x0BE7_0A11

/** The run's director personality. */
internal const val PERSONALITY_SALT: Long = 0x0D1E_C70E_5EED_0011L

/** The onslaught theme of each surge, keyed by the surge's index. */
internal const val SURGE_THEME_SALT: Long = 0x5E26_E7E3_5EED_0012L

/** An Elite's affix, keyed by its entity id. */
internal const val AFFIX_SALT: Long = 0x0AFF_1C5E_5EED_0013L

/** An ordinary enemy's champion roll, keyed by its entity id. */
internal const val CHAMPION_SALT: Long = 0x0C4A_3910_5EED_0014L

/** An Elite's Keynote aspect bias, keyed by its entity id. */
internal const val ASPECT_BIAS_SALT: Long = 0x0A5B_EC75_5EED_0015L

/** The world event plan: count, kinds and start times. */
internal const val WORLD_EVENT_SALT: Long = 0x0E7E_0175_5EED_0016L

/** The event signal schedule and kinds. */
internal const val SIGNAL_SALT: Long = 0x0516_4A15_5EED_0017L

/** A signal gamble's outcome, keyed by the signal and the option. */
internal const val GAMBLE_SALT: Long = 0x06A3_B1E5_5EED_0018L

/** The Elemental Storm's carried aspect when the Keynote's featured aspect is one no ordinary kind carries. */
internal const val STORM_ASPECT_SALT: Long = 0x057A_A5EC_5EED_0019L
