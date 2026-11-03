// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kotlin.math.PI

/**
 * Indices of the Core-state durations in their reaction's content values: Wildfire's Ablaze, Superconduct's
 * Frictionless and Resonant Arc's Arc Weave seconds.
 */
internal const val ABLAZE_SECONDS_VALUE: Int = 1
internal const val FRICTIONLESS_SECONDS_VALUE: Int = 1
internal const val WEAVE_SECONDS_VALUE: Int = 0

// Indices of the other verb values read outside resolveReaction (BuildCatalog documents every index).
internal const val SPALL_THROW_VALUE: Int = 1
internal const val PLASMA_ACTIVATION_VALUE: Int = 2
internal const val PLASMA_DASH_HEAT_VALUE: Int = 3
internal const val ACCRETION_REACH_VALUE: Int = 0
internal const val ACCRETION_SPEED_VALUE: Int = 1
internal const val ACCRETION_THROW_VALUE: Int = 2
internal const val ABLAZE_STACKS_VALUE: Int = 2
internal const val FRICTIONLESS_DRAG_VALUE: Int = 0
internal const val GLASS_CHARGE_SPEED_VALUE: Int = 0
internal const val ATTRACTOR_PULL_VALUE: Int = 1
internal const val ATTRACTOR_SHOT_REACH_VALUE: Int = 2
internal const val ATTRACTOR_SHOT_TURN_VALUE: Int = 3
internal const val COMPOST_DAMPING_VALUE: Int = 0
internal const val COMPOST_GROWTH_RADIUS_VALUE: Int = 1
internal const val COMPOST_GROWTH_SECONDS_VALUE: Int = 2
internal const val COMPOST_GROWTHS_VALUE: Int = 3
internal const val BLAST_JUMP_IMPULSE_VALUE: Int = 2
internal const val GUARD_ORBIT_VALUE: Int = 1
internal const val WEAVE_LINKS_VALUE: Int = 1
internal const val CRACKED_REACH_VALUE: Int = 1
internal const val CRACKED_POWER_VALUE: Int = 2
internal const val CRACKED_DECAY_VALUE: Int = 3
internal const val HUSH_SILENCE_VALUE: Int = 2
internal const val FLAME_LEAP_REACH_VALUE: Int = 0
internal const val FLAME_LEAP_SHARE_VALUE: Int = 1
internal const val FLAME_LEAP_FLOOR_VALUE: Int = 2
internal const val FLAME_REFUND_VALUE: Int = 3
internal const val BUBBLE_SHOTS_VALUE: Int = 2
internal const val BUBBLE_HASTE_VALUE: Int = 3
internal const val VOID_ANCHOR_CONE_VALUE: Int = 1
internal const val VOID_ANCHOR_REACH_VALUE: Int = 2
internal const val MAW_SHOTS_VALUE: Int = 0
internal const val MAW_POP_VALUE: Int = 1
internal const val MAW_THROW_VALUE: Int = 2

/** Reactions within this distance of the Core feed its verbs (Ablaze, Frictionless, Shard Guard, Arc Weave). */
internal const val CORE_LINK_RADIUS: Float = 480f
internal const val RADIANS_PER_DEGREE: Float = (PI / 180.0).toFloat()

// Base shapes the verbs leave unchanged.
internal const val FUSION_SECONDS: Float = 1.6f
internal const val FUSION_PULL: Float = 220f
/** Wildfire's spread when the reaction has no verb values. */
internal const val WILDFIRE_LEGACY_SPREAD: Int = 4
internal const val SUPERCONDUCT_LINKS: Int = 3
internal const val GLACIER_SHARDS: Int = 5
internal const val GLACIER_REACH: Float = 220f
internal const val EMBRITTLE_SECONDS: Float = 4f
internal const val MAGNETIZE_YANK: Float = 640f
internal const val ROT_WELL_SECONDS: Float = 2f
internal const val ROT_WELL_PULL: Float = 180f
/** Compost never grows a well past this radius. */
internal const val COMPOST_MAX_RADIUS: Float = 200f
/** Accretion latches the pickups this far beyond the star's radius when it implodes. */
internal const val ACCRETION_LATCH_MARGIN: Float = 40f
/** A Time Bubble holds ordinary occupants this long per tick. */
internal const val TIME_BUBBLE_STOP_SECONDS: Float = 0.5f
/** Blast Jump: a Crescent's Blink reaches this much farther. */
internal const val BLAST_JUMP_BLINK_RANGE: Float = 1.4f
internal const val GUARD_SHARD_RADIUS: Float = 6f
/** Shard Guard shards turn this many radians per second around the Core. */
internal const val GUARD_SPIN: Float = 3f
/** Void Anchor: the Core lands this far short of the anchor's rim and never within the singularity gap. */
internal const val VOID_BLINK_CLEARANCE: Float = 8f
internal const val VOID_BLINK_SINGULARITY_GAP: Float = 60f
internal const val MAX_ROCK_ECHOES: Int = 3
internal const val MAX_CRACKED_DETONATIONS: Int = 12
internal const val CRACKED_SHOCKWAVE_SECONDS: Float = 0.25f
internal const val CRACKED_SHOCKWAVE_RADIUS: Float = 60f
internal const val FLAME_LEAP_MIN_SECONDS: Float = 1f
internal const val FLAME_REFUND_GATE_SECONDS: Float = 0.5f
/** At most this many of the newest shot-eating fields test hostile shots each step. */
internal const val MAX_SHOT_EATING_FIELDS: Int = 4
/** At most this many of the newest pickup-pulling fields move pickups each step. */
internal const val MAX_PICKUP_FIELDS: Int = 2

// Keystone forms (one per reaction).
internal const val FLASH_FREEZE_SECONDS: Float = 0.8f
internal const val FLASH_FREEZE_DAMAGE: Float = 0.8f
internal const val ARC_PLASMA_REACH: Float = 220f
internal const val ARC_PLASMA_SHARE: Float = 0.5f
internal const val ARC_PLASMA_STACKS: Int = 1
internal const val DWARF_STAR_SECONDS: Float = 3.2f
internal const val DWARF_STAR_PULL: Float = 440f
internal const val DWARF_STAR_DAMAGE: Float = 0.5f
internal const val FIREWALKER_SECONDS: Float = 6f
internal const val FIREWALKER_IGNITE_MARGIN: Int = 2
internal const val ZERO_RESISTANCE_LINKS: Int = 6
internal const val ZERO_RESISTANCE_FROZEN: Float = 1.5f
internal const val AVALANCHE_FREEZE_CHILL: Int = 4
internal const val GLASS_BURST_EMBRITTLE_SECONDS: Float = 2f
internal const val GLASS_BURST_SHARDS: Int = 3
internal const val GLASS_BURST_SHARE: Float = 0.5f
internal const val GLASS_BURST_REACH: Float = 160f
internal const val GLASS_BURST_SPACING_DEGREES: Float = 25f
internal const val REPULSOR_THROW_SPEED: Float = 900f
internal const val SHORT_CIRCUIT_SECONDS: Float = 1f
internal const val PLAGUE_PIT_SECONDS: Float = 4f
internal const val ROCKET_CLAP_REACH: Float = 220f
internal const val ROCKET_CLAP_SPEED: Float = 560f
internal const val CRYSTAL_CHOIR_SHARDS: Int = 7
internal const val CRYSTAL_CHOIR_RESONANCE: Int = 5
internal const val STANDING_WAVE_SECONDS: Float = 6f
internal const val STANDING_WAVE_LINKS: Int = 2
internal const val STANDING_WAVE_SHARE: Float = 0.35f
internal const val AFTERSHOCK_DELAY_SECONDS: Float = 0.6f
internal const val AFTERSHOCK_SHARE: Float = 0.6f
internal const val CREVASSE_SECONDS: Float = 4f
internal const val DEAD_AIR_RADIUS: Float = 140f
internal const val DEAD_AIR_SECONDS: Float = 3f
internal const val DEAD_AIR_SHOTS: Int = 20
internal const val HELLFIRE_DAMAGE: Float = 1.5f
internal const val REWIND_SHOTS: Int = 12
internal const val REWIND_SHARE: Float = 0.3f
internal const val REWIND_REACH: Float = 600f
internal const val EVENT_STRIKE_ANCHOR_SECONDS: Float = 1.5f
/** Event Strike: the anchor counts as marked for crits this long after the blink. */
internal const val EVENT_STRIKE_CRIT_SECONDS: Float = 0.5f
internal const val HUNGRY_VOID_RADIUS: Float = 120f
internal const val HUNGRY_VOID_SECONDS: Float = 2.4f
internal const val LAST_RITES_LINE: Float = 0.16f
internal const val LAST_RITES_ELITE_LINE: Float = 0.09f

/** Forced and echoed reactions scale their magnitude at most this much. */
internal const val MAX_FORCED_REACTION_SCALE: Float = 1.3f
/** An echo replays a reaction as a cascade at nominal two plus two stacks. */
internal const val ECHO_GENERATION: Int = 1
internal const val ECHO_STACKS: Int = 2

internal const val THUNDERCLAP_THROW_SPEED: Float = 760f
internal const val SHATTERPOINT_SHARDS: Int = 5
internal const val SHATTERPOINT_REACH: Float = 240f
internal const val RESONANT_ARC_LINKS: Int = 4
internal const val RESONANT_ARC_RANGE_PER_STACK: Float = 25f
internal const val SEISMIC_STUN_SECONDS: Float = 0.6f
internal const val SILENCE_SECONDS: Float = 2f
/** Bosses fall silent briefly, so chained Silences cannot keep the Architect from ever attacking. */
internal const val SILENCE_BOSS_SECONDS: Float = 0.75f
/** After a Silence ends, a boss ignores further Silences for this long, whichever enemy sets them off. */
internal const val SILENCE_BOSS_GRACE_SECONDS: Float = 2f
internal const val BLACKFIRE_SECONDS: Float = 3f
internal const val STASIS_STOP_SECONDS: Float = 1f
internal const val NULL_WELL_SECONDS: Float = 1.6f
internal const val NULL_WELL_PULL: Float = 240f
internal const val OBLIVION_LINE: Float = 0.12f
internal const val OBLIVION_ELITE_LINE: Float = 0.06f
