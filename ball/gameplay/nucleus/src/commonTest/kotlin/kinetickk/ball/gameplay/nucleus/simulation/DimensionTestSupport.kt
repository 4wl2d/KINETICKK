// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.windowSiteId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Room the game keeps between a placed rift and any obstacle beyond the rift's radius (DimensionSystem's clearance). */
private const val RIFT_TEST_CLEARANCE = 30f

/** Content with the shipped dimension policy changed by [change]. */
internal fun GameplayContentSnapshot.withDimensions(change: DimensionPolicy.() -> DimensionPolicy): GameplayContentSnapshot =
    copy(dimensions = dimensions.change())

/**
 * Opens the rift into [target] [distance] u ahead of the Core and returns its centre: a claimed window site for the
 * Zenith or the Nadir (the only offer on screen), the glitch rift for the Archive. [clear] moves the centre clear of
 * obstacles as the game places its rifts (a played run may have a rock ahead; a test arena keeps the exact point).
 */
internal fun MutableGameState.openPortalForTesting(target: WorldLayer, distance: Float = 560f, clear: Boolean = false): Pair<Float, Float> {
    val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
    val aheadX = coreX + cos(heading) * distance
    val aheadY = coreY + sin(heading) * distance
    val (x, y) = if (clear) nudgedClearOfObstacles(aheadX, aheadY, content.dimensions.portalRadius + RIFT_TEST_CLEARANCE) else aheadX to aheadY
    when (target) {
        WorldLayer.ZENITH, WorldLayer.NADIR -> {
            val kind = if (target == WorldLayer.ZENITH) SiteKind.ZENITH_RIFT else SiteKind.NADIR_RIFT
            sites = listOf(SiteState(windowSiteId(nextSiteWindowIndex, 0), kind, x, y, elapsed + content.sites.windows.offerLifetime))
        }
        WorldLayer.ARCHIVE -> dimensionRun = dimensionRun.copy(
            glitchPortal = DimensionPortal(
                dimensionRun.nextPortalId, x, y, WorldLayer.ARCHIVE, elapsed + content.dimensions.glitchPortalLifetimeSeconds,
            ),
            nextPortalId = dimensionRun.nextPortalId + 1,
        )
        WorldLayer.BASE -> error("Realspace has no rift into itself")
    }
    return x to y
}

/** The Core's last swept move ends at ([x], [y]) after crossing 100 u along +x. */
internal fun MutableGameState.flyThroughForTesting(x: Float, y: Float) {
    previousCoreX = x - 100f
    previousCoreY = y
    coreX = x
    coreY = y
}

/** Enters [target] at the Core as a touched rift would; [objective] replaces the rolled one. */
internal fun MutableGameState.enterDimensionForTesting(target: WorldLayer, objective: DimensionObjective? = null) {
    check(enterDimension(target)) { "entry into $target was refused" }
    if (objective != null) forceObjectiveForTesting(objective)
}

/** Replaces the visit's rolled objective with [objective], as if the visit had just begun with it. */
internal fun MutableGameState.forceObjectiveForTesting(objective: DimensionObjective) {
    val visit = checkNotNull(dimensionRun.visit)
    dimensionRun = dimensionRun.copy(visit = newVisit(visit.layer, objective, visit.serial, visit.endsAt))
}

/** Completes the visit's objective as the game does and moves its exit rift to [distance] u along +x from the Core. */
internal fun MutableGameState.completeObjectiveForTesting(distance: Float = 520f) {
    completeObjective()
    val visit = checkNotNull(dimensionRun.visit)
    val exit = checkNotNull(visit.exitPortal)
    dimensionRun = dimensionRun.copy(visit = visit.copy(exitPortal = exit.copy(x = coreX + distance, y = coreY)))
}

/** Moves the clock to the visit's end, so the next dimension update pulls the Core back. */
internal fun MutableGameState.skipToVisitEndForTesting() {
    elapsed = checkNotNull(dimensionRun.visit).endsAt
}
