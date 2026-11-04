// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

internal const val DASH_PHASE_SECONDS: Float = 0.24f

/** Apex value indices (the per-shape order of `defaultApexDefinition`) and the forms' own numbers. */
internal const val PERIHELION_ARC = 0
internal const val PERIHELION_CAPTURE_RADIUS = 1
internal const val PERIHELION_HELD_DAMAGE = 2
internal const val PERIHELION_RELEASE_INTERVAL = 3
internal const val PERIHELION_MIN_DISTANCE: Float = 50f
internal const val PERIHELION_MIN_SPEED: Float = 150f
internal const val PERIHELION_SHIFT_GRACE: Float = 0.5f
internal const val PERIHELION_MAX_WAIT: Float = 9f
internal const val PERIHELION_RELEASE_RANGE: Float = 600f
internal const val MONOLITH_REGROWTH_SECONDS = 0
internal const val MONOLITH_IMPACT_BONUS = 1
internal const val MONOLITH_REFRACTION_DAMAGE = 2
internal const val VIVISECT_INTERVAL = 0
internal const val VIVISECT_SPREAD_RADIUS = 1
internal const val VIVISECT_FREE_DASH_GAP = 2
internal const val CORONA_HALO_RADIUS = 0
internal const val CORONA_BAND_DPS = 1
internal const val CORONA_BURST_INTERVAL = 2
internal const val CORONA_BURST_DAMAGE = 3
internal const val MIRROR_SHOT_DAMAGE = 0
internal const val MIRROR_LOCKED_EDGE = 1
internal const val HYPERCUBE_HALF_SIDE = 0
internal const val HYPERCUBE_INSIDE_SPEED = 1
internal const val HYPERCUBE_PULSE_DAMAGE = 2
internal const val HYPERCUBE_PULSE_PER_NODE = 3
internal const val HYPERCUBE_PULSE_SECONDS: Float = 0.6f
internal const val HYPERCUBE_EDGE_REACH: Float = 24f
internal const val HYPERNOVA_CAST_MASS = 0
internal const val HYPERNOVA_COOLDOWN = 1
internal const val HYPERNOVA_KILL_MASS = 2
internal const val HYPERNOVA_END_MASS = 3
internal const val TWIN_ECHO_DELAY = 0
internal const val TWIN_ECHO_STRIKE_SCALE = 1
internal const val TWIN_ECHO_ABSORB_INTERVAL = 2
internal const val TWIN_ECHO_STRIKE_SPEED = 3
internal const val MAGNETAR_FLIP_INTERVAL = 0
internal const val MAGNETAR_REACH_SCALE = 1
internal const val MAGNETAR_THROW_SCALE = 2
internal const val MAGNETAR_SHOT_DAMAGE = 3
internal const val CRESCENDO_BURST_DAMAGE = 1
internal const val CRESCENDO_BURST_RADIUS = 2
internal const val CRESCENDO_BOLTS_PER_BEAT = 3
internal const val ECLIPSE_BLINK_RECHARGE = 0
internal const val ECLIPSE_CUT_RADIUS = 1
internal const val ECLIPSE_PULL_SPEED = 2
internal const val ECLIPSE_SCAR_SCALE = 3
internal const val ECLIPSE_PULL_REACH: Float = 100f
internal const val SWARM_DART_INTERVAL = 0
internal const val SWARM_DART_DAMAGE = 1
internal const val SWARM_DATA_RANGE = 2
internal const val SWARM_DRONE_LIFE = 3
internal const val SWARM_INTERCEPT_SECONDS: Float = 0.6f
internal const val SWARM_INTERCEPT_REACH: Float = 70f
/** Character × skill interactions. */
internal const val PENTAGON_SKILL_REFUND: Float = 0.2f
internal const val CRESCENT_SKILL_BLINK_SECONDS: Float = 0.6f
internal const val DIAMOND_PARRY_SKILL_REFUND: Float = 1f
internal const val STAR_SKILL_KILL_MASS: Int = 1
internal const val FLASH_DECAY: Float = 2.5f
/** No Refit signature moves its constant below this share of the base ([refitSignature]). */
internal const val REFIT_SIGNATURE_FLOOR_SHARE: Float = 0.5f

// Ability damage is calibrated by CharacterBalanceTest: averaged over its seeded 90 s fights (packs at
// random bearings, content bolt damage, exact and jittered timing) every offensive character's ability
// output stays within 40 % of their median at Refit 0 and Refit 10 (Orbit 42, Puncture 180, Halo 12/s and 16,
// Lattice 5 + 2.5 per node, slams 54 + 0.1125 per u/s from 100 u/s, Pulse burst 40 and streak 9 % per stack,
// scars 35, drones 7). The 0.6.0 flight law (grip carves tighter, faster arcs) lifted Orbit and cost the Star
// and the Dipole their pack meetings: Orbit 70 -> 42, the early Supernova from 12 -> 6 Stellar Mass, Repel
// 420 -> 700 with throws of 0.6 -> 1.5 s. Refit's x1.97 at level 10 compounds with kill-fed drones and lifts
// mechanic damage but not the streak's impact share, so Refit 10 put the Hive and the Lattice above the band and
// the Pentagon below it: drones 9 -> 7, Lattice 5.6 + 2.8 -> 5 + 2.5, streak 6 % -> 9 % per stack.

internal const val ORBIT_ARC: Float = 0.698f
internal const val ORBIT_MIN_DISTANCE: Float = 100f
internal const val ORBIT_MIN_SPEED: Float = 200f
internal const val ORBIT_RADIUS: Float = 210f
internal const val ORBIT_DAMAGE: Float = 42f
internal const val ORBIT_STACKS: Int = 2

internal const val FACET_REGEN_SECONDS: Float = 6f
internal const val FACET_PAUSE_SECONDS: Float = 2f
internal const val FACET_CUT_SECONDS: Float = 1f
internal const val FACET_CUT_INTERVAL: Float = 1.2f
internal const val FACET_IMPACT_BONUS: Float = 1.2f
internal const val FACET_BREAK_RADIUS: Float = 180f
internal const val FACET_BREAK_PUSH: Float = 520f
internal const val HEAVY_IMPACT_SHARE: Float = 0.25f
internal const val HEAVY_IMPACT_SPEED: Float = 450f

internal const val MARK_SECONDS: Float = 3f
internal const val PUNCTURE_DAMAGE: Float = 180f
internal const val PUNCTURE_HEAT_REFUND: Float = 12f
internal const val PUNCTURE_INTERVAL: Float = 0.25f

internal const val HALO_SPEED_SPAN: Float = 1_200f
internal const val HALO_EXTRA_RADIUS: Float = 110f
internal const val HALO_BAND: Float = 18f
internal const val HALO_BAND_DPS: Float = 12f
internal const val HALO_MIN_SPEED: Float = 40f
internal const val HALO_EASE: Float = 6f
internal const val HALO_BURST_SPEED: Float = 1_200f
internal const val HALO_BURST_RADIUS: Float = 260f
internal const val HALO_BURST_DAMAGE: Float = 16f
internal const val HALO_BURST_COOLDOWN: Float = 1.2f

internal const val PARRY_MIN_SPEED: Float = 200f
internal const val PARRY_WINDOW_SECONDS: Float = 0.18f
internal const val PARRY_COOLDOWN_SECONDS: Float = 1.2f
internal const val PARRY_RETURN_RANGE: Float = 800f
internal const val PARRY_SHOT_DAMAGE: Float = 65f
internal const val EDGE_DAMAGE_PER_STACK: Float = 0.08f
internal const val EDGE_SECONDS: Float = 6f

internal const val LATTICE_RECORD_DISTANCE: Float = 120f
internal const val LATTICE_CLOSE_DISTANCE: Float = 130f
internal const val LATTICE_SPACING: Float = 150f
internal const val LATTICE_LIFE_SECONDS: Float = 7f
internal const val LATTICE_BASE_DAMAGE: Float = 5f
internal const val LATTICE_NODE_DAMAGE: Float = 2.5f
internal const val LATTICE_INSIDE_MULTIPLIER: Float = 1.5f

internal const val STAR_IMPACT_PER_STACK: Float = 0.03f
internal const val STAR_RADIUS_PER_STACK: Float = 0.015f
internal const val STAR_MASS_PER_STACK: Float = 0.02f
internal const val SUPERNOVA_EARLY_STACKS: Int = 6
internal const val SUPERNOVA_RADIUS: Float = 160f
internal const val SUPERNOVA_RADIUS_PER_STACK: Float = 8f
internal const val SUPERNOVA_DAMAGE: Float = 20f
internal const val SUPERNOVA_DAMAGE_PER_STACK: Float = 9f
internal const val SUPERNOVA_STACKS_PER_ELEMENT: Int = 4
internal const val SUPERNOVA_COOLDOWN: Float = 2f

internal const val GHOST_MIN_SPEED: Float = 300f
internal const val GHOST_IMPACT_SHARE: Float = 0.32f
internal const val GHOST_HIT_INTERVAL: Float = 0.4f
internal const val GHOST_ABSORB_INTERVAL: Float = 1.5f

internal const val POLARITY_FLIP_INTERVAL: Float = 2f
internal const val ATTRACT_RADIUS: Float = 300f
internal const val ATTRACT_ACCELERATION: Float = 260f
internal const val REPEL_RADIUS: Float = 220f
internal const val REPEL_ACCELERATION: Float = 700f
internal const val THROW_SECONDS: Float = 1.5f
internal const val REPEL_THROW_SHARE: Float = 1f
internal const val SLAM_MIN_SPEED: Float = 100f
/** A struck enemy that gains this speed from a slam is thrown on (as fast as a slam needs). */
internal const val THROW_MIN_SPEED: Float = SLAM_MIN_SPEED
internal const val SLAM_BASE_DAMAGE: Float = 54f
internal const val SLAM_DAMAGE_PER_SPEED: Float = 0.1125f
internal const val MAX_SLAMS_PER_STEP: Int = 8

/** Thrown enemies that look for a slam partner in one step (the obstacle pass uses the same bound). */
internal const val MAX_POLARITY_THROWN_CHECKS_PER_STEP: Int = 24

internal const val BEAT_PERIOD: Float = 0.75f
internal const val BEAT_WINDOW: Float = 0.11f
/** An on-beat Dash costs this share of the usual heat (40 % less) and pushes [BEAT_DASH_IMPULSE] harder. */
internal const val BEAT_DASH_HEAT: Float = 0.6f
internal const val BEAT_DASH_IMPULSE: Float = 1.2f
internal const val BEAT_IMPACT_MULTIPLIER: Float = 1.6f
internal const val BEAT_BURST_RADIUS: Float = 80f
internal const val BEAT_BURST_DAMAGE: Float = 40f
internal const val BEAT_ACTIVATION_MULTIPLIER: Float = 1.4f
internal const val STREAK_IMPACT_PER_STACK: Float = 0.09f

internal const val BLINK_RANGE: Float = 240f
internal const val BLINK_SINGULARITY_GAP: Float = 60f
internal const val BLINK_OBSTACLE_STEP: Float = 20f
internal const val BLINK_SPEED_RETENTION: Float = 0.8f
internal const val BLINK_RECHARGE_SECONDS: Float = 2.2f
internal const val BLINK_DRIVE_RANGE_MULTIPLIER: Float = 1.4f
internal const val SCAR_FUSE_SECONDS: Float = 0.4f
internal const val SCAR_FADE_SECONDS: Float = 0.3f
internal const val SCAR_DAMAGE: Float = 35f
internal const val SCAR_REACH: Float = 40f

internal const val DRONE_CHANCE: Float = 0.25f
internal const val DRONE_GUARANTEE: Int = 4
internal const val DRONE_HASH_SALT: Long = 0x4E1F_C0DEL
internal const val DRONE_LIFE_SECONDS: Float = 12f
internal const val DRONE_FIRST_DART_SECONDS: Float = 0.3f
internal const val DRONE_ORBIT_RADIUS: Float = 70f
internal const val DRONE_ORBIT_SPEED: Float = 2.4f
internal const val DRONE_RANGE: Float = 260f
internal const val DRONE_DAMAGE: Float = 7f
internal const val DRONE_INTERVAL: Float = 1f
internal const val DRONE_DART_SECONDS: Float = 0.2f
internal const val DRONE_KILL_HEAL: Float = 0.5f
internal const val DRONE_HEAL_INTERVAL: Float = 1f
