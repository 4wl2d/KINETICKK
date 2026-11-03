// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

// ---------------------------------------------------------------------------------------------
// Constants (nucleus rules; per-rank numbers are content values).

/** No skill recasts sooner than this after its cast, whatever refunds it. */
internal const val MIN_SKILL_RECAST_SECONDS: Float = 2.5f
/** The static cooldown scale (rules and conversions) never goes below this. */
internal const val MIN_SKILL_COOLDOWN_SCALE: Float = 0.5f
/** At most this share of a cast's cooldown comes back through refunds, so no combination more than doubles a cast rate. */
internal const val MAX_REFUND_SHARE: Float = 0.5f
/** The first enemies a cast damages are its skill hits. */
internal const val MAX_SKILL_HITS_PER_CAST: Int = 4
/** SkillAttunement: an element-less cast applies the attuned element to at most this many enemies. */
internal const val MAX_ATTUNED_STACKS_PER_CAST: Int = 8
/** SkillCharges: a slot holds at most this many charges, each recharging on the cooldown × [SKILL_CHARGE_RECHARGE]. */
internal const val MAX_SKILL_CHARGES: Float = 2f
internal const val SKILL_CHARGE_RECHARGE: Float = 1.2f
internal const val CATALYSIS_SECONDS: Float = 0.4f
internal const val CATALYSIS_ELEMENTLESS_SECONDS: Float = 0.2f
internal const val PRISM_SKILL_COOLDOWN: Float = 0.85f
internal const val RING_SKILL_HALO: Float = 110f
internal const val RING_SKILL_RADIUS_CAP: Float = 1.5f
internal const val GHOST_REPEAT_DELAY: Float = 1f
internal const val GHOST_REPEAT_SHARE: Float = 0.35f
/** Pointer distance below which a cast keeps the last aim and an area cast lands on the Core. */
internal const val AIM_DEADZONE: Float = 24f
internal const val HOOK_SINGULARITY_GAP: Float = 90f
/** The hook's line is checked for obstacles this far past its target (the Core flies on after the impact). */
internal const val HOOK_OVERSHOOT: Float = 200f
/** Room the hooked Core keeps from an obstacle surface along that line. */
internal const val HOOK_OBSTACLE_CLEARANCE: Float = 30f
internal const val SKILL_BREAK_MARGIN: Int = 2
/** An unowned Catalyze is offered only while this many elements have affinity. */
internal const val CATALYZE_MIN_ELEMENTS: Int = 2
internal const val MAX_ENEMY_RADIUS_SLACK: Float = 80f

// Value indices, per skill. Those a runtime bound applies to, and those bounds, are internal: a test holds every
// shipped rank value within the bound its cast runs through.
internal const val SOLAR_VENT_CAP = 0
internal const val SOLAR_RADIUS = 1
internal const val SOLAR_DAMAGE = 2
internal const val SOLAR_DAMAGE_PER_HEAT = 3
internal const val SOLAR_SCORCH = 4
internal const val SOLAR_SCORCH_CAP = 6
internal const val SOLAR_HEAT_PER_SCORCH = 15f

internal const val METEOR_RADIUS = 0
internal const val METEOR_DAMAGE = 1
internal const val METEOR_SCORCH = 2
internal const val METEOR_BOOST = 3
internal const val METEOR_DELAY = 4
internal const val METEOR_HURL = 5
internal const val METEOR_MAX_SPEED: Float = 1_800f
internal const val METEOR_ABLAZE_SECONDS: Float = 3f

internal const val FROST_LINE_LENGTH = 0
internal const val FROST_LINE_SECONDS = 1
internal const val FROST_LINE_BOLTS = 2
internal const val FROST_LINE_CHILL = 3
internal const val FROST_LINE_HALF_WIDTH = 4
internal const val FROST_LINE_AHEAD: Float = 160f
internal const val FROST_LINE_DAMPING: Float = 4f
internal const val FROST_LINE_SLAM_CHILL: Int = 2
internal const val FROST_LINE_EMBRITTLE_SECONDS: Float = 1f

internal const val FROST_SNAP_RADIUS = 0
internal const val FROST_SNAP_CHILL = 1
internal const val FROST_SNAP_SHATTER = 2
internal const val FROST_SNAP_COILED_PUSH = 3
internal const val FROST_SNAP_COILED_SECONDS = 4
internal const val FROST_SNAP_MIN_SPEED: Float = 600f

internal const val ARC_HOOK_REACH = 0
internal const val ARC_HOOK_CHARGE = 1
internal const val ARC_HOOK_SPEED = 2
internal const val ARC_HOOK_LINKS = 3
internal const val ARC_HOOK_LINK_DAMAGE = 4
internal const val ARC_HOOK_WINDOW = 5
internal const val ARC_HOOK_LINK_RANGE: Float = 260f
internal const val ARC_HOOK_KILL_WINDOW: Float = 1.5f

internal const val STORM_COIL_RADIUS = 0
internal const val STORM_COIL_SECONDS = 1
internal const val STORM_COIL_TARGETS = 2
internal const val STORM_COIL_DAMAGE = 3
internal const val STORM_COIL_ZAP_SECONDS: Float = 0.5f
internal const val STORM_COIL_MAX_TARGETS: Int = 3

internal const val GRAVITY_WELL_RADIUS = 0
internal const val GRAVITY_WELL_SECONDS = 1
internal const val GRAVITY_WELL_PULL = 2
internal const val GRAVITY_WELL_DPS = 3
internal const val GRAVITY_WELL_DATA_RANGE = 4
internal const val GRAVITY_WELL_PICKUP_SPEED: Float = 400f
internal const val GRAVITY_WELL_COLLAPSE_MASS: Int = 3

internal const val MASS_DRIVER_SECONDS = 0
internal const val MASS_DRIVER_MASS = 1
internal const val MASS_DRIVER_GUARD = 2
internal const val MASS_DRIVER_THROW = 3
internal const val MASS_DRIVER_THRESHOLD = 4
internal const val MASS_DRIVER_IMPACT_MASS: Int = 2

internal const val SPORES_RADIUS = 0
internal const val SPORES_DECAY = 1
internal const val SPORES_SECONDS = 2
internal const val SPORES_DATA_CAP = 3
internal const val SPORES_SPREAD_RADIUS: Float = 130f
internal const val SPORES_SPREAD_DECAY: Int = 2
internal const val SPORES_DATA_SPEED: Float = 60f
internal const val SPORES_SCATTER_ANGLE: Float = 2.39996f
internal const val SPORES_REFUND_SECONDS: Float = 0.5f
internal const val SPORES_REFUND_CAP: Float = 3f

internal const val TIDE_RADIUS = 0
internal const val TIDE_SCALE = 1
internal const val TIDE_HEAL_PER_STACK = 2
internal const val TIDE_HEAL_CAP = 3
internal const val TIDE_HEAT_PER_STACK: Float = 1f

internal const val BOOM_RANGE = 0
internal const val BOOM_DAMAGE = 1
internal const val BOOM_RESONANCE = 2
internal const val BOOM_BOLTS = 3
internal const val BOOM_PUSH = 4
internal const val BOOM_RECOIL = 5
internal const val BOOM_HALF_ANGLE_DEGREES: Float = 35f
internal const val BOOMED_SECONDS: Float = 0.8f

internal const val RESONATOR_RADIUS = 0
internal const val RESONATOR_SECONDS = 1
internal const val RESONATOR_SCALE = 2
internal const val RESONATOR_ECHOES = 3

internal const val VOID_STEP_DISTANCE = 0
internal const val VOID_STEP_RIFT_DAMAGE = 1
internal const val VOID_STEP_HOLLOW = 2
internal const val VOID_STEP_RIFT_RADIUS = 3
internal const val VOID_STEP_PHASE = 4
internal const val VOID_STEP_RIFT_DELAY = 5
internal const val VOID_STEP_SINGULARITY_GAP: Float = 60f
/** Void Step lands at least this far from an obstacle surface (beyond the Core's radius), stepping back by the step. */
internal const val VOID_STEP_OBSTACLE_GAP: Float = 80f
internal const val VOID_STEP_OBSTACLE_STEP: Float = 20f
internal const val VOID_STEP_SPEED_RETENTION: Float = 0.8f

internal const val SHROUD_SECONDS = 0
internal const val SHROUD_STORE = 1
internal const val SHROUD_DAMAGE = 2
internal const val SHROUD_RANGE = 3
internal const val SHROUD_REACH: Float = 44f
internal const val SHROUD_RELEASE_TARGETS: Int = 4
internal const val SHROUD_SHOT_SPEED: Float = 720f

internal const val SLING_SCALE = 0
internal const val SLING_MIN_SPEED = 1
internal const val SLING_MAX_SPEED = 2
internal const val SLING_WINDOW = 3
internal const val SLING_HURL = 4
/** Slingshot's swing keeps this many seconds of its path clear of obstacles. */
internal const val SLING_CLEAR_SECONDS: Float = 0.5f

internal const val FULL_STOP_RADIUS = 0
internal const val FULL_STOP_SCALE = 1
internal const val FULL_STOP_BREAK = 2

internal const val CATALYZE_RADIUS = 0
internal const val CATALYZE_TARGETS = 1
internal const val CATALYZE_SCALE = 2
internal const val CATALYZE_FALLBACK = 3

internal const val INFUSION_SECONDS = 0
internal const val INFUSION_HITS_PER_SECONDARY = 1
internal const val INFUSION_STACK_CAP = 2
