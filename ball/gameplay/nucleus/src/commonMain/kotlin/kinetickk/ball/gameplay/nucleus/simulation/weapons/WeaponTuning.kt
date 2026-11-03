// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*

/** Upper bound of [arcCoilTargetCount]: four, plus two on a Pentagon's beat. */
internal const val MAX_ARC_COIL_TARGETS: Int = 6
internal const val ARC_COIL_FIRST_RANGE: Float = 420f
internal const val ARC_COIL_HOP_RANGE: Float = 220f
internal const val ARC_COIL_FALLOFF: Float = 0.95f

// ---------------------------------------------------------------------------------------------
// Tuning (weapon power 1).

internal const val AIM_ASSIST_CONE: Float = 0.5f
internal const val AIM_ASSIST_RANGE: Float = 700f
internal const val NULL_LANCE_DAMAGE: Float = 61f
internal const val NULL_LANCE_PIERCE: Int = 5
internal const val NULL_LANCE_REAR_SHARE: Float = 0.3f
internal const val MINE_DAMAGE: Float = 68f
internal const val MINE_PROXIMITY: Float = 0.45f
internal const val GRAVITY_MINE_RADIUS: Float = 100f
internal const val MINE_MASS_INTERVAL: Float = 1.5f
internal const val BASE_MINE_LIMIT: Int = 8
internal const val MINE_CHAIN_FUSE: Float = 0.05f
internal const val MINE_CHAIN_REACH: Float = 0.9f
internal const val MINE_WELL_SECONDS: Float = 1.2f
internal const val MINE_WELL_DPS: Float = 15f
internal const val MINE_WELL_REACH: Float = 0.7f
internal const val ION_SWARM_DAMAGE: Float = 4.5f
internal const val ION_SWARM_COOLDOWN: Float = 0.72f
internal const val ION_SWARM_PIERCE: Int = 1
internal const val ION_SWARM_FIRE_RATE_FORM: Float = 1.25f
internal const val ARC_COIL_DAMAGE: Float = 15f
internal const val ARC_COIL_COOLDOWN: Float = 1.15f
internal const val ARC_COIL_CHARGE: Int = 1
internal const val QUASAR_DAMAGE: Float = 59f
internal const val QUASAR_BURST_LIFE: Float = 1.3f
internal const val QUASAR_SIDE_SHARE: Float = 0.1f
internal const val QUASAR_BURST_SHARE: Float = 0.15f
internal const val QUASAR_BURST_RADIUS: Float = 120f
internal const val SPEAR_DAMAGE: Float = 95f
internal const val SPEAR_BLINK_COOLDOWN: Float = 2f
internal const val SPEAR_CROSS_SHARE: Float = 1f
internal const val SPEAR_REACH: Float = 28f
internal const val SPEAR_SCAR_SHARE: Float = 1f
internal const val SPEAR_BLINK_REACH: Float = 320f
internal const val SPEAR_SCAR_FUSE: Float = 0.4f
internal const val SPEAR_SCAR_REACH: Float = 30f
internal const val PRISM_RELAY_DAMAGE: Float = 17.2f
internal const val PRISM_TWIN_SHARE: Float = 0.14f
/** Heading change per dp travelled that counts as a hard turn (60° over 100 dp). */
internal const val FLUX_TURN_CURVATURE: Float = 0.0105f
internal const val FLUX_LONG_LIFE: Float = 4f
internal const val FLUX_SPACING: Float = 22f
internal const val PARRY_TWIST_GUARD: Float = 0.2f

internal const val GYRE_DAMAGE: Float = 28f
internal const val GYRE_COOLDOWN: Float = 1.3f
internal const val GYRE_SPEED: Float = 760f
internal const val GYRE_RETURN_SPEED: Float = 900f
internal const val GYRE_REACH: Float = 420f
internal const val GYRE_RADIUS: Float = 11f
/** The first form's larger disc, as a multiple of [GYRE_RADIUS] (about the size of the disc before the retune). */
internal const val GYRE_RADIUS_FORM: Float = 1.65f
internal const val GYRE_SPREAD: Float = 0.45f
internal const val GYRE_MAX_LIFE: Float = 3f
internal const val GYRE_ORBIT_BONUS: Float = 1.25f
internal const val GYRE_RETURN_BONUS: Float = 1.5f
internal const val GYRE_EXTRA_SHARE: Float = 0.6f

internal const val SCATTER_DAMAGE: Float = 8f
internal const val SCATTER_PIERCE: Int = 2
internal const val SCATTER_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_INTERVAL: Float = 2.4f
internal const val SCATTER_RANGE: Float = 380f
internal const val SCATTER_SPEED: Float = 950f
internal const val SCATTER_CONE: Float = TAU / 6f
internal const val SCATTER_POINT_BLANK: Float = 140f
internal const val SCATTER_PASSIVE_SPREAD: Float = 0.9f

internal const val TETHER_RANGE: Float = 350f
internal const val TETHER_DPS: Float = 6f
internal const val TETHER_RAMP_MAX: Float = 3f
internal const val TETHER_RAMP_FORM: Float = 4f
internal const val TETHER_RAMP_SECONDS: Float = 2.5f
internal const val TETHER_KILL_KEEP: Float = 0.6f
internal const val TETHER_SECOND_SHARE: Float = 0.75f
internal const val TETHER_PULL: Float = 260f

internal const val SENTRY_INTERVAL: Float = 3f
internal const val SENTRY_LIMIT: Int = 3
internal const val SENTRY_LIFE: Float = 10f
internal const val SENTRY_DAMAGE: Float = 4.8f
internal const val SENTRY_FIRE_INTERVAL: Float = 0.6f
internal const val SENTRY_RANGE: Float = 520f
internal const val SENTRY_RADIUS: Float = 12f
internal const val SENTRY_SOURCE: Int = 2
internal const val SENTRY_FOLDED: Int = 1
internal const val SENTRY_BLAST_RADIUS: Float = 120f
internal const val SENTRY_BLAST_DAMAGE: Float = 40f

internal const val HUNTER_DAMAGE: Float = 8.7f
internal const val HUNTER_DECAY_EVERY: Int = 2
internal const val HUNTER_COOLDOWN: Float = 2.2f
internal const val HUNTER_MISSILES: Int = 3
internal const val HUNTER_RANGE: Float = 700f
internal const val HUNTER_LAUNCH_SPEED: Float = 420f
internal const val HUNTER_TOP_SPEED: Float = 780f
internal const val HUNTER_TURN_RATE: Float = 7f
internal const val HUNTER_MISSILE_RADIUS: Float = 7f
internal const val HUNTER_MISSILE_LIFE: Float = 2.6f
internal const val HUNTER_BURST_RADIUS: Float = 110f
internal const val HUNTER_BURST_SHARE: Float = 0.75f
internal const val HUNTER_BURST_FORM: Float = 1.2f

internal const val CONCUSSION_MIN_SPEED: Float = 300f
internal const val CONCUSSION_COOLDOWN: Float = 0.35f
internal const val CONCUSSION_DAMAGE: Float = 24f
internal const val CONCUSSION_IMPACT_SHARE: Float = 0.25f
internal const val CONCUSSION_BASE_RADIUS: Float = 90f
internal const val CONCUSSION_MAX_RADIUS: Float = 260f
internal const val CONCUSSION_KNOCKBACK: Float = 260f
internal const val CONCUSSION_SHIELD_PER_HIT: Float = 4f
internal const val CONCUSSION_GHOST_GATE: Float = 0.4f
/** A wave's damage on a lone enemy, as a share of its full damage. */
internal const val CONCUSSION_LONE_SHARE: Float = 0.7f
/** Extra damage (of the lone share) for each other enemy a wave catches, up to [CONCUSSION_CROWD_CAP] of them. */
internal const val CONCUSSION_CROWD_BONUS: Float = 0.18f
internal const val CONCUSSION_CROWD_CAP: Int = 4

internal const val GLACIER_DAMAGE: Float = 40f
internal const val GLACIER_RADIUS: Float = 240f
internal const val GLACIER_HOLD_SECONDS: Float = 0.3f
internal const val GLACIER_TOLL_COOLDOWN: Float = 0.8f
internal const val GLACIER_PASSIVE_INTERVAL: Float = 3.5f
internal const val GLACIER_PASSIVE_RADIUS: Float = 170f
internal const val GLACIER_PASSIVE_SHARE: Float = 0.6f
internal const val GLACIER_SECOND_TOLL: Float = 0.5f

internal const val LENS_COOLDOWN: Float = 2.8f
internal const val LENS_SPEED: Float = 380f
internal const val LENS_FUSE: Float = 0.9f
internal const val LENS_RADIUS: Float = 14f
internal const val LENS_SPREAD: Float = 0.7f
internal const val LENS_EXTRA_SHARE: Float = 0.25f
internal const val LENS_WELL_RADIUS: Float = 170f
internal const val LENS_WELL_SECONDS: Float = 1.6f
internal const val LENS_WELL_DPS: Float = 13.5f
internal const val LENS_COLLAPSE_DAMAGE: Float = 28.8f
internal const val LENS_IMPLOSION_DAMAGE: Float = 10f
internal const val LENS_PULL: Float = 240f

internal const val SOLAR_LENGTH: Float = 240f
internal const val SOLAR_LENGTH_FORM: Float = 120f
internal const val SOLAR_ANGULAR_SPEED: Float = 2.2f
internal const val SOLAR_DPS: Float = 41f
internal const val SOLAR_ARC_SECTOR: Float = 0.4f
internal const val SOLAR_ARC_SHARE: Float = 0.9f
/** The heat a beam keeps for the enemies behind the nearest one in its path. */
internal const val SOLAR_SHADOW_SHARE: Float = 0.5f

internal const val AEGIS_SHIELD_CAPACITY: Float = 15f
internal const val AEGIS_SHARDS_BASE: Int = 2
internal const val AEGIS_PIERCE: Int = 3
internal const val AEGIS_DAMAGE: Float = 13f
internal const val AEGIS_COOLDOWN: Float = 1.4f
internal const val AEGIS_RANGE: Float = 260f
internal const val AEGIS_BREAK_RANGE: Float = 420f
internal const val AEGIS_ORBIT_RADIUS: Float = 52f
internal const val AEGIS_ORBIT_SPEED: Float = 3.4f
internal const val AEGIS_SHARD_RADIUS: Float = 9f
internal const val AEGIS_SPEED: Float = 820f
internal const val AEGIS_SHARD_LIFE: Float = 0.9f
internal const val AEGIS_RESTORE_COOLDOWN: Float = 60f

internal const val HOLLOW_DAMAGE: Float = 42f
internal const val HOLLOW_REACH: Float = 40f
internal const val HOLLOW_FUSE: Float = 0.55f
internal const val HOLLOW_LINGER: Float = 0.45f
internal const val HOLLOW_PASSIVE_SPEED: Float = 600f
internal const val HOLLOW_PASSIVE_INTERVAL: Float = 2f
internal const val HOLLOW_PASSIVE_SHARE: Float = 30f / 70f
internal const val HOLLOW_BLINK_SCALE: Float = 2f
internal const val HOLLOW_SECOND_SHARE: Float = 0.12f
internal const val HOLLOW_SECOND_DELAY: Float = 0.3f
internal const val HOLLOW_PULL_RADIUS: Float = 120f

internal const val DRUM_DAMAGE: Float = 30f
internal const val DRUM_COOLDOWN: Float = 1.6f
internal const val DRUM_RADIUS: Float = 210f
internal const val DRUM_KNOCKBACK: Float = 240f
internal const val DRUM_BEAT_SCALE: Float = 1.5f
internal const val DRUM_DOUBLE_BEAT: Float = 0.25f
internal const val DRUM_DOUBLE_SHARE: Float = 0.6f

/** Ring variants ([WeaponEntity.phase] of a RING). */
internal const val RING_TOLL: Int = 0
internal const val RING_DRUM: Int = 1
internal const val RING_CONCUSSION: Int = 2
internal const val RING_PASSIVE: Int = 3
internal const val RING_SECONDS: Float = 0.35f

/** Rift steps ([WeaponEntity.phase] of a RIFT). */
internal const val RIFT_GROWING: Int = 0
internal const val RIFT_ARMED: Int = 1
internal const val RIFT_DETONATED: Int = 2
internal const val RIFT_SPEAR_SCAR: Int = 3

internal const val GYRE_CATCH_RADIUS: Float = 30f
/** Seconds a returning disc must run free of rocks before its next glance sparks again. */
internal const val GYRE_GLANCE_SPARK_GAP: Float = 0.2f
internal const val GYRE_SPIN: Float = 18f
internal const val SENTRY_SHOT_SPEED: Float = 900f
internal const val SENTRY_PIERCE: Int = 1
internal const val SENTRY_SPLASH_RADIUS: Float = 40f
internal const val SENTRY_SPLASH_SHARE: Float = 0.32f
internal const val LENS_THROW: Float = 420f
internal const val LENS_MASS_INTERVAL: Float = 3f
internal const val HOLLOW_PULL: Float = 120f
internal const val AEGIS_TURN_RATE: Float = 9f
/** The share of its damage a shard keeps for each enemy it pierces on to. */
internal const val AEGIS_PIERCE_SHARE: Float = 0.9f
internal const val HUNTER_SEEK_RANGE: Float = 900f
internal const val HUNTER_ACCELERATION: Float = 900f
internal const val TETHER_CHARGE_INTERVAL: Float = 0.8f
internal const val TETHER_OVERLOAD_LINKS: Int = 3
internal const val TETHER_OVERLOAD_STACKS: Float = 3f
internal const val SOLAR_REACH: Float = 10f
internal const val SOLAR_SCORCH_INTERVAL: Float = 1.5f
internal const val RIFT_SPENT: Int = 4

internal const val ION_SWARM_CHARGE_EVERY: Int = 2
internal const val QUASAR_KNOCKBACK: Float = 220f
internal const val PRISM_BURST_RADIUS: Float = 60f
internal const val PRISM_BURST_SHARE: Float = 0.12f
internal const val PRISM_LATTICE_SHARE: Float = 0.4f
