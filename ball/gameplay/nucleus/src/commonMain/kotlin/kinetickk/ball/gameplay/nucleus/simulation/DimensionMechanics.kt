// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionPhysics
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableListAppending
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * What a layer does to the run while the Core is in it: the pace a dimension measures flight against, the Zenith's
 * Grace (lighter drag and mending halo rings), the Nadir's Heat (slow flight scorches the Core, Matter burns twice as
 * bright), the Archive's Desync (hostile time follows the Core's speed) and the director's rate and cap. Every value
 * comes from the current layer's content definition; realspace's definition changes nothing, so every function here
 * is the identity there. Nothing draws from a run stream: ambient rings come from stateless hashes.
 */

/** Salt of the ambient halo ring placement (registered in the lane notes). */
internal const val HALO_SALT: Long = 0x0D13_E5A1_0000_0003L

/** Ambient rings appear this far ahead of the Core, plus up to the spread, within the half arc of its heading. */
private const val AMBIENT_HALO_DISTANCE: Float = 900f
private const val AMBIENT_HALO_DISTANCE_SPREAD: Float = 900f
private const val AMBIENT_HALO_HALF_ARC: Float = (70.0 * PI / 180.0).toFloat()
/** An ambient ring this far from the Core is left behind and replaced. */
private const val AMBIENT_HALO_RANGE: Float = 3_000f
/** Ambient ring ids start here; course rings keep the ids below. */
private const val AMBIENT_HALO_FIRST_ID: Int = 100
/** Room kept between a placed ring and any obstacle, beyond the ring's own radius. */
private const val HALO_CLEARANCE: Float = 30f
/** Spark colour of a mending ring (the repair burst's). */
private const val HALO_BURST_COLOR: Int = 2

/**
 * The speed a dimension measures flight against: the steady speed of the pace reach along the viewport's long axis
 * with the base movement stats, never above the pace ceiling. It follows the production steady model, so a retuned
 * flight law moves it too.
 */
internal fun MutableGameState.dimensionPace(): Float {
    val policy = content.dimensions
    return min(policy.paceCeiling, steadyCursorSpeed(policy.paceReach))
}

/**
 * The current layer's change to the Core's drag: the Zenith's Grace multiplies it, but never below the layer's drag
 * floor, and never raises a drag already under that floor (Perpetual Vector, Superconduct).
 */
internal fun MutableGameState.layerDrag(drag: Float): Float {
    val physics = content.dimensions.definition(worldLayer).physics
    return max(drag * physics.dragMultiplier, min(drag, physics.dragFloor))
}

/** The current layer's scale of the director's spawn rate. */
internal fun MutableGameState.layerSpawnRateMultiplier(): Float =
    content.dimensions.definition(worldLayer).spawnRateMultiplier

/** The current layer's scale of the ordinary enemy cap. */
internal fun MutableGameState.layerOrdinaryCapMultiplier(): Float =
    content.dimensions.definition(worldLayer).ordinaryCapMultiplier

/**
 * World time scale for [enemy] (null: hostile projectiles) in the current layer. Under the Archive's Desync it is
 * the Core's speed over the pace, kept between the layer's bounds, so time never stops and runs normally at pace; a
 * boss-class enemy never runs slower than the boss floor. The fixed step and the run clock are untouched.
 */
internal fun MutableGameState.layerTimeScale(enemy: Enemy?): Float {
    val physics = content.dimensions.definition(worldLayer).physics
    if (physics.desyncMinimumScale == 1f && physics.desyncMaximumScale == 1f) return 1f
    val pace = dimensionPace()
    val scale = (if (pace > 0f) speed / pace else 1f).coerceIn(physics.desyncMinimumScale, physics.desyncMaximumScale)
    return if (enemy != null && enemy.isBossClass) max(scale, physics.desyncBossFloor) else scale
}

/** The current layer's scale of Matter earned (the Nadir doubles it). */
internal fun MutableGameState.layerMatterMultiplier(): Float =
    content.dimensions.definition(worldLayer).physics.matterMultiplier

/**
 * One step of the current layer's physics during a visit: halo rings where the layer has them, then the scorch where
 * slow flight burns. A lethal scorch tick can end the run; the caller checks the phase.
 */
internal fun MutableGameState.updateLayerMechanics(delta: Float) {
    val visit = dimensionRun.visit ?: return
    val physics = content.dimensions.definition(worldLayer).physics
    if (physics.ambientHalos > 0 || visit.halos.isNotEmpty()) updateHalos(visit, physics)
    if (physics.scorchSharePerSecond > 0f) updateScorch(delta, physics)
}

/**
 * Halo rings: a ring the Core flies through at pace or faster mends it, charges Overdrive and leaves (once per ring;
 * a slower pass leaves it standing). Ambient rings left far behind go, and while fewer than the layer's ambient count
 * remain one new ring appears ahead of the Core per step. The ring list is rebuilt only on such an event.
 */
private fun MutableGameState.updateHalos(visit: DimensionVisit, physics: DimensionPhysics) {
    val radius = content.dimensions.objectives.haloRadius
    val fast = speed >= dimensionPace()
    val halos = visit.halos
    var kept: ArrayList<HaloRing>? = null
    var ambient = 0
    for (index in halos.indices) {
        val ring = halos[index]
        val passed = fast && segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, ring.x, ring.y, radius)
        val behind = !passed && ring.courseIndex < 0 &&
            distanceSquared(ring.x, ring.y, coreX, coreY) > AMBIENT_HALO_RANGE * AMBIENT_HALO_RANGE
        if (passed || behind) {
            if (kept == null) {
                kept = ArrayList(halos.size)
                for (earlier in 0 until index) kept += halos[earlier]
            }
            if (passed) mendAtHalo(ring, physics)
            continue
        }
        kept?.add(ring)
        if (ring.courseIndex < 0) ambient++
    }
    var next = kept?.toImmutableList() ?: halos
    var serial = visit.haloSerial
    if (ambient < physics.ambientHalos) {
        next = withHaloRing(next, ambientHaloRing(visit, serial))
        serial++
    }
    if (next === halos) return
    dimensionRun = dimensionRun.copy(visit = visit.copy(halos = next, haloSerial = serial))
}

/** The rings with [ring] added; refused before anything changes once [ContentBounds.MAX_HALO_RINGS] are live. */
internal fun withHaloRing(halos: ImmutableList<HaloRing>, ring: HaloRing): ImmutableList<HaloRing> {
    check(halos.size < ContentBounds.MAX_HALO_RINGS) { "At most ${ContentBounds.MAX_HALO_RINGS} halo rings are live" }
    return halos.toImmutableListAppending(ring)
}

/** The visit's [serial]-th ambient ring: ahead of the Core within the half arc, from a hash of the run and visit. */
private fun MutableGameState.ambientHaloRing(visit: DimensionVisit, serial: Int): HaloRing {
    val random = HashRandom(worldHash(runSeed, visit.layer, visit.serial, serial, HALO_SALT))
    val angle = travelHeading() + AMBIENT_HALO_HALF_ARC * (2f * random.nextFloat() - 1f)
    val distance = AMBIENT_HALO_DISTANCE + AMBIENT_HALO_DISTANCE_SPREAD * random.nextFloat()
    val clearance = content.dimensions.objectives.haloRadius + HALO_CLEARANCE
    val (x, y) = nudgedClearOfObstacles(coreX + cos(angle) * distance, coreY + sin(angle) * distance, clearance)
    return HaloRing(AMBIENT_HALO_FIRST_ID + serial, x, y, courseIndex = -1)
}

/** A ring passed at pace: a share of max integrity back as a repair, an Overdrive charge, a chime and sparks. */
private fun MutableGameState.mendAtHalo(ring: HaloRing, physics: DimensionPhysics) {
    heal(maxHp * physics.haloRepairShare, HealSource.REPAIR)
    gainOverdrive(physics.haloOverdrive)
    emitSound(GameplayAudioCue.PICKUP)
    burst(ring.x, ring.y, 10, HALO_BURST_COLOR)
}

/**
 * Heat: below the pace the slow clock runs; past the layer's grace each full tick scorches a share of max integrity
 * as a self hit (no hit i-frames or HURT clauses, damage reduction ignored, shields absorb it, a revival answers a
 * lethal tick). Flying at pace resets both clocks. Baby mode leaves self damage whole, so the scorch applies its
 * incoming-damage factor itself.
 */
private fun MutableGameState.updateScorch(delta: Float, physics: DimensionPhysics) {
    if (speed >= dimensionPace()) {
        dimensionSlowSeconds = 0f
        dimensionScorchClock = 0f
        return
    }
    dimensionSlowSeconds += delta
    if (dimensionSlowSeconds <= physics.scorchGraceSeconds) return
    dimensionScorchClock += delta
    if (dimensionScorchClock < physics.scorchTickSeconds) return
    dimensionScorchClock -= physics.scorchTickSeconds
    takeDamage(
        maxHp * physics.scorchSharePerSecond * physics.scorchTickSeconds * scorchBabyModeFactor(),
        ignoreReduction = true,
        kind = IncomingDamage.SELF,
        lethalReason = DimensionNotices.SCORCHED,
    )
}

/** Baby mode's incoming-damage multiplier in a Baby mode run, 1 otherwise. */
private fun MutableGameState.scorchBabyModeFactor(): Float =
    if (easyMode) content.easyMode.incomingDamageMultiplier else 1f
