// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.gameplay.nucleus.model.ObstacleField

/**
 * The world around the Core: the rebase origin, the obstacle window and destroyed asteroids, the current layer's
 * keep-out, and the obstacle contact gates (hits, Near Miss, Graze, sling, the revive window). Written by the world
 * domain (a layer switch writes the keep-out).
 */
internal class WorldRunState(source: WorldRunState?) {
    /** Accumulated rebase shift: an absolute world position is the run-local position plus this origin. */
    internal var worldOriginX: Double = source?.worldOriginX ?: 0.0
    internal var worldOriginY: Double = source?.worldOriginY ?: 0.0
    /** Generated obstacle window around the Core; an immutable value replaced on change. */
    internal var obstacleField: ObstacleField = source?.obstacleField ?: ObstacleField.EMPTY
    /** Destroyed unstable asteroids, oldest first, at most [ContentBounds.MAX_DESTROYED_OBSTACLES]. */
    internal var destroyedObstacleIds: List<Long> = source?.destroyedObstacleIds ?: emptyList()
    /** The active keep-out of the current layer's generation (null: the origin safe radius applies). */
    internal var worldKeepOut: WorldKeepOut? = source?.worldKeepOut
    /** Run time before which an asteroid collision deals no further damage and fires no ricochet. */
    internal var obstacleHitReadyAt: Float = source?.obstacleHitReadyAt ?: 0f
    /** The solid surface the last hurting collision struck ([NO_SURFACE_ID]: none). */
    internal var obstacleHitSurfaceId: Long = source?.obstacleHitSurfaceId ?: NO_SURFACE_ID
    /** Run time before which Near Miss grants no further Overdrive. */
    internal var nearMissReadyAt: Float = source?.nearMissReadyAt ?: 0f
    /** Gap from the Core's surface to the nearest solid obstacle surface within reach; +∞ when none (NearObstacle). */
    internal var coreObstacleGap: Float = source?.coreObstacleGap ?: Float.POSITIVE_INFINITY
    /** Run time before which GRAZE cannot fire again. */
    internal var grazeReadyAt: Float = source?.grazeReadyAt ?: 0f
    /** The gravity knot whose influence holds the Core ([NO_OBSTACLE_ID] outside) and the heading it entered with. */
    internal var slingWellId: Long = source?.slingWellId ?: NO_OBSTACLE_ID
    internal var slingEntryHeading: Float = source?.slingEntryHeading ?: 0f
    /**
     * End of the invulnerability a revive (Second Wind, Ouroboros) granted: the only window a lethal
     * collision honours; other invulnerability (Glass Comet, Bulwark) does not stop it.
     */
    internal var reviveInvulnerableUntil: Float = source?.reviveInvulnerableUntil ?: 0f
}
