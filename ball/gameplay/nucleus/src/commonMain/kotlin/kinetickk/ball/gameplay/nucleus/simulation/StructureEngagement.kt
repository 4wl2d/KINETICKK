// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.TrialProjection

/*
 * Structures and dungeons as an engagement (the structures lane replaces this file). Inert until the lane lands: no
 * structure is placed.
 */

/** True while a dungeon room is sealed. */
internal fun MutableGameState.structureEngagementActive(): Boolean = false

/** True once the run entered or spent a structure. */
internal fun MutableGameState.structureTouched(): Boolean = false

/** True while the Core is inside a structure interior (ambient picks then use the STRUCTURE habitat). */
internal fun MutableGameState.structureInteriorActive(): Boolean = false

/** Appends optional-site marks of the structures in the window; returns the new mark count. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.appendStructureMarks(out: FloatArray, count: Int): Int = count

/** True when a structure footprint inflated by [margin] contains the absolute point. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.structureFootprintContains(absoluteX: Double, absoluteY: Double, margin: Float): Boolean = false

/** The trial panel of an engaged dungeon, or null. */
internal fun MutableGameState.structureTrialProjection(): TrialProjection? = null

/** A world layer switch collapses the dungeon silently. */
internal fun MutableGameState.onStructureLayerChanged() = Unit

/** The director's spawn-rate scale while a room is sealed. */
internal fun MutableGameState.structureDirectorScale(): Float = 1f
