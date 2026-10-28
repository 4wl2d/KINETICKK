// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.jvm.JvmInline

/** Stable identity of the content captured by a Ball at bootstrap. */
@JvmInline
value class ContentVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "Content version must not be blank" }
    }

    override fun toString(): String = value
}

/** Canonical version published by the current Content implementation. */
val KINETICKK_CONTENT_VERSION: ContentVersion = ContentVersion("kinetickk-content-3")

/** Bootstrap limits. Runtime decisions use the corresponding captured snapshot fields. */
object ContentBounds {
    const val MAX_ITEMS: Int = 320
    const val MAX_COMBAT_FIELDS: Int = 16
    const val MAX_WEAPONS: Int = 24
    const val MAX_MATRIX_NODES: Int = 40
    const val MAX_MATRIX_NODE_RANKS: Int = 5
    const val MAX_MATRIX_ROWS: Int = 8
    const val MAX_MATRIX_NODE_EFFECTS: Int = 4
    const val MAX_RELICS: Int = 24
    const val MAX_RELIC_PAIRINGS: Int = 32
    const val MAX_RELIC_VALUES: Int = 4
    const val MIN_REBIRTH_LEVEL: Int = 0
    const val MAX_REBIRTH_LEVEL: Int = 10
    /** Roster size bound; the shipped roster has one definition per [EnemyType] (35 shipped). */
    const val MAX_ENEMY_TYPES: Int = 48
    /** World obstacles held at once (the generated window around the Core). */
    const val MAX_OBSTACLES: Int = 128
    /** Destroyed unstable asteroids remembered so their chunks never regenerate them. */
    const val MAX_DESTROYED_OBSTACLES: Int = 256
    /** Largest area reach of an obstacle kind's field ([ObstacleKindTuning.maximumInfluence]). */
    const val MAX_OBSTACLE_INFLUENCE: Float = 400f

    /** Directed site rewards queued at once (window sites grant at most six per run). */
    const val MAX_DIRECTED_REWARDS: Int = 10
    /** Rarity-floor item offers queued at once. */
    const val MAX_FLOOR_OFFERS: Int = 4
    /** Ambient sites (caches, eggs) held in the window around the Core. */
    const val MAX_AMBIENT_SITES: Int = 48
    /** Gates of one gate race. */
    const val MAX_RACE_GATES: Int = 7
    /** Foes one site trial spawns at once. */
    const val MAX_SITE_FOES: Int = 8
    /** Ambient site ids a run remembers as opened. */
    const val MAX_OPENED_SITES: Int = 64
    /** Angle buckets of the Spin Cycle detector. */
    const val MAX_SPIN_BUCKETS: Int = 9
    /** Persisted world discoveries (one bit each in an Int mask). */
    const val MAX_WORLD_DISCOVERIES: Int = 31

    /** Run omens defined in the catalog. */
    const val MAX_OMENS: Int = 16
    /** Effects on each side of an omen, Blessing or Pact. */
    const val MAX_RUN_MODIFIER_EFFECTS: Int = 3
    /** Enemy-type weight biases of one director personality. */
    const val MAX_DIRECTOR_TYPE_BIASES: Int = 8
    /** Ambient world events planned for one run. */
    const val MAX_WORLD_EVENTS_PER_RUN: Int = 3
    /** Strikes one world event keeps alive at once. */
    const val MAX_EVENT_STRIKES: Int = 4
    /** Event-signal slots of one run. */
    const val MAX_SIGNAL_SLOTS: Int = 5
    /** Event-signal prompts queued at once. */
    const val MAX_PENDING_SIGNAL_PROMPTS: Int = 2
    /** Cards of one event-signal prompt (plus Ignore). */
    const val SIGNAL_OPTIONS: Int = 3

    /** Structure templates defined in the catalog. */
    const val MAX_STRUCTURE_TEMPLATES: Int = 16
    /** Rooms of one structure template. */
    const val MAX_STRUCTURE_ROOMS: Int = 12
    /** Doors of one structure template. */
    const val MAX_STRUCTURE_DOORS: Int = 16
    /** Compiled capsule walls of one structure template. */
    const val MAX_STRUCTURE_TEMPLATE_WALLS: Int = 48
    /** Enemies one structure room garrisons. */
    const val MAX_STRUCTURE_GARRISON: Int = 12
    /** Largest side of a structure footprint. */
    const val MAX_STRUCTURE_FOOTPRINT: Float = 2_400f
    /** Rewards of one structure's final room. */
    const val MAX_STRUCTURE_REWARDS: Int = 6
    /** Structures placed in the generated window around the Core. */
    const val MAX_STRUCTURES_IN_WINDOW: Int = 9
    /** Capsule walls held in the window. */
    const val MAX_STRUCTURE_WALLS: Int = MAX_STRUCTURES_IN_WINDOW * MAX_STRUCTURE_TEMPLATE_WALLS
    /** Structure signals shown as edge markers at once. */
    const val MAX_STRUCTURE_SIGNALS: Int = 4
    /** Spent structure ids a run remembers. */
    const val MAX_SPENT_STRUCTURES: Int = 32

    /** Halo rings of one Zenith course. */
    const val MAX_HALO_RINGS: Int = 12
    /** Embers alive at once in the Nadir. */
    const val MAX_EMBERS: Int = 24
    /** Dimension rewards queued at once. */
    const val MAX_PENDING_DIMENSION_REWARDS: Int = 3
    /** Rift offers of one run. */
    const val MAX_PORTAL_OFFERS: Int = 3
    /** Bits of an obstacle id that carry its world layer. */
    const val OBSTACLE_LAYER_BITS: Int = 3
    /** Blessing and Pact definitions in the catalog. */
    const val MAX_BOON_DEFINITIONS: Int = 32
    /** Blessings and Pacts one run holds. */
    const val MAX_RUN_BOONS: Int = 8

    /** Discoveries persisted before this catalog generation belong to the retired 400-item catalog. */
    const val COLLECTION_GENERATION: Int = 2

    /** Profiles from the retired catalog may list up to this many discovered items before discard. */
    const val LEGACY_MAX_ITEMS: Int = 400
    const val LEGACY_MAX_RELICS: Int = 40

    const val MAX_ITEM_STACKS: Int = 3
    const val MAX_ITEM_STATS: Int = 3
    const val MAX_ITEM_CLAUSES: Int = 2
    const val MAX_ITEM_RULES: Int = 3
    const val MAX_CLAUSE_ACTIONS: Int = 3
    const val MAX_CLAUSE_CONDITIONS: Int = 2
    const val MAX_CHAIN_TARGETS: Int = 6
    const val MAX_SHARDS: Int = 6
    const val MAX_BUFF_STACKS: Int = 3
    const val MAX_EFFECT_RADIUS: Float = 600f
    const val MAX_STATUS_THRESHOLD: Int = 12
    const val RESONANCE_TIERS: Int = 3

    /** Highest run level a tempo profile may set; the shipped cap is [RunTempoProfile.maximumLevel]. */
    const val MAX_RUN_LEVEL: Int = 99

    /** Active-effect table size; the shipped table has one definition per [SkillId], each carried by one item. */
    const val MAX_SKILLS: Int = 24
    /** Slots a run's active items bind their effects to (Z, X, C). */
    const val SKILL_SLOTS: Int = 3
    /** Ranks every active effect defines (I, II, III); an active item stacks to exactly this many. */
    const val MAX_SKILL_RANK: Int = 3
    const val MAX_SKILL_VALUES: Int = 6
    /** No skill rank may recast sooner than this, before any refund (the refund bank keeps the same floor). */
    const val MIN_SKILL_COOLDOWN_SECONDS: Float = 2.5f
    const val MAX_REACTION_VALUES: Int = 4
    /** A reaction's verb cue is a short decision line (first-discovery stamp, Codex, tooltip). */
    const val MAX_REACTION_CUE_CHARS: Int = 28
    const val MAX_APEX_VALUES: Int = 4
    /** Boss strikes one Apex activation may spend. */
    const val MAX_APEX_STRIKES: Int = 12
    /** Hostile bolts one Apex activation may erase. */
    const val MAX_APEX_BOLTS: Int = 96

    /** Highest Refit and Honing level. */
    const val MAX_UPGRADE_LEVEL: Int = 10
    /** Largest base price of an upgrade track, so the last level stays far inside Int. */
    const val MAX_UPGRADE_BASE_COST: Int = 10_000
}
