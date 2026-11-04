// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ContentBounds

internal object InteractionFxLimits {
    const val MAX_PARTICLES = 700
    const val MAX_MOTION_ECHOES = 36
    const val MAX_SHOCKWAVES = 48
    const val MAX_DAMAGE_NUMBERS = 140
    const val MAX_WEAPON_ARCS = 128

    /** Samples of the Core ribbon ring (presentation memory of the Core's path); the oldest is trimmed on overflow. */
    const val MAX_CORE_RIBBON_SAMPLES = 64

    /** Damage numbers pop, settle and drift away from the Core over 600 ms (`KkTime.Drift`). */
    const val DAMAGE_NUMBER_LIFE_SECONDS = 0.6f

    /** The first particles of every burst are large kill shards; the rest are small sparks. */
    const val SHARDS_PER_BURST = 4

    /** Particles at least this large are drawn as shards. */
    const val SHARD_MIN_SIZE = 4.5f

    /** Build toasts kept at once; a lower-priority toast never evicts a higher one. */
    const val MAX_BUILD_NOTIFICATIONS = 3

    /**
     * Toasts of one mergeable kind and title opened this recently (seconds) take the next lines
     * instead; the window runs from the toast's opening, so a chain of finds never keeps one open.
     */
    const val NOTICE_MERGE_SECONDS = 1.5f

    /** Name lines a merged toast holds; the next find opens a toast of its own. */
    const val NOTICE_MERGED_LINES = 3

    /** Concurrent Break and reaction flashes (the budget admits far more events than read as one). */
    const val MAX_ELEMENT_BURSTS = 24

    /** Flashes of the same kind closer than this (world units) merge into one heavier flash. */
    const val BURST_MERGE_DISTANCE = 80f

    /** Events a merged flash can carry; each adds to its reach and brightness. */
    const val MAX_BURST_WEIGHT = 4

    /**
     * Live flashes of one reaction: a fifth one outside the merge distance replaces that reaction's
     * oldest, so one chain-happy reaction never takes every slot.
     */
    const val MAX_BURSTS_PER_REACTION = 4

    /** Concurrent skill cast and impact looks (three casts and three impacts a step, by construction). */
    const val MAX_SKILL_BURSTS = 8

    /** Concurrent Apex activation looks. */
    const val MAX_APEX_BURSTS = 2

    const val BREAK_BURST_SECONDS = 0.5f

    /** The longest reaction look ([ReactionFxSpecs]); each reaction lives its own spec's life. */
    val REACTION_BURST_SECONDS: Float = ReactionFxSpecs.maxLife

    /** The once-per-profile reaction name stamp (`cond`, −6°, 900 ms). */
    const val REACTION_STAMP_SECONDS = 0.9f
    const val MAX_REACTION_STAMPS = 3

    /** Concurrent structure disintegrations: a whole window shatters at once when the Architect arrives. */
    const val MAX_DISINTEGRATIONS = ContentBounds.MAX_STRUCTURES_IN_WINDOW

    /** How long a spent structure takes to shatter and fade. */
    const val DISINTEGRATION_SECONDS = 1.6f

    /** Dungeon moments playing at once: a clear can follow a wave and a guardian within a moment. */
    const val MAX_DUNGEON_MOMENTS = 6
}
