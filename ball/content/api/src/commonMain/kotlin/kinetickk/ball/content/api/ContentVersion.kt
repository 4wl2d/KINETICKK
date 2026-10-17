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
    const val MAX_ITEMS: Int = 96
    const val MAX_COMBAT_FIELDS: Int = 16
    const val MAX_WEAPONS: Int = 12
    const val MAX_MATRIX_NODES: Int = 40
    const val MAX_MATRIX_NODE_RANKS: Int = 5
    const val MAX_MATRIX_ROWS: Int = 8
    const val MAX_MATRIX_NODE_EFFECTS: Int = 4
    const val MAX_RELICS: Int = 24
    const val MAX_RELIC_PAIRINGS: Int = 32
    const val MAX_RELIC_VALUES: Int = 4
    const val MIN_REBIRTH_LEVEL: Int = 0
    const val MAX_REBIRTH_LEVEL: Int = 10
    /** Roster size bound; the shipped roster has one definition per [EnemyType]. */
    const val MAX_ENEMY_TYPES: Int = 24
    /** World obstacles held at once (the generated window around the Core). */
    const val MAX_OBSTACLES: Int = 128
    /** Destroyed unstable asteroids remembered so their chunks never regenerate them. */
    const val MAX_DESTROYED_OBSTACLES: Int = 256

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
}
