// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.DimensionPhysics
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Ember
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
 * bright), the Archive's Desync (hostile time follows the Core's speed) and the director's rate and cap; and the marks
 * of a visit's objective: the Ascent's course of halo rings and the Harvest's embers. Every value comes from the
 * current layer's content definition; realspace's definition changes nothing, so every function here is the identity
 * there. Nothing draws from a run stream: rings come from stateless hashes and embers drop where burning enemies die.
 */

/** Salt of the ambient halo ring placement (registered in the lane notes). */
internal const val HALO_SALT: Long = 0x0D13_E5A1_0000_0003L
/** Salt of the Ascent course ring placement (registered in the lane notes). */
internal const val COURSE_SALT: Long = 0x0D13_E5A1_0000_0004L

/** An Ascent shows the ring to fly through next and the one after it. */
internal const val ASCENT_RINGS_AHEAD: Int = 2
/** Chance that a course ring turns against the course's alternating side. */
private const val COURSE_TURN_FLIP_CHANCE: Float = 0.3f
private const val DEGREES_TO_RADIANS: Float = (PI / 180.0).toFloat()

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
 * One step of the current layer's physics and the visit's objective marks: halo rings where the layer has them (or
 * the course placed some), embers while any wait, then the scorch where slow flight burns. A lethal scorch tick can
 * end the run; the caller checks the phase.
 */
internal fun MutableGameState.updateLayerMechanics(delta: Float) {
    val visit = dimensionRun.visit ?: return
    val physics = content.dimensions.definition(worldLayer).physics
    if (physics.ambientHalos > 0 || visit.halos.isNotEmpty()) updateHalos(visit, physics)
    dimensionRun.visit?.let { current -> if (current.embers.isNotEmpty()) updateEmbers(current) }
    if (physics.scorchSharePerSecond > 0f) updateScorch(delta, physics)
}

/**
 * Halo rings: a ring the Core flies through at pace or faster mends it, charges Overdrive and leaves (once per ring;
 * a slower pass leaves it standing). A course ring counts only in its turn: passing the next one advances the Ascent
 * and places the ring after the one still ahead, and passing the last one completes it; a course ring flown through
 * out of turn stays. Ambient rings left far behind go, and while fewer than the layer's ambient count remain one new
 * ring appears ahead of the Core per step. The ring list is rebuilt only on such an event.
 */
private fun MutableGameState.updateHalos(visit: DimensionVisit, physics: DimensionPhysics) {
    val radius = content.dimensions.objectives.haloRadius
    val fast = speed >= dimensionPace()
    val halos = visit.halos
    var kept: ArrayList<HaloRing>? = null
    var ambient = 0
    var progress = visit.progress
    for (index in halos.indices) {
        val ring = halos[index]
        val inTurn = ring.courseIndex < 0 || ring.courseIndex == progress && !visit.completed
        val passed = fast && inTurn && segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, ring.x, ring.y, radius)
        val behind = !passed && ring.courseIndex < 0 &&
            distanceSquared(ring.x, ring.y, coreX, coreY) > AMBIENT_HALO_RANGE * AMBIENT_HALO_RANGE
        if (passed || behind) {
            if (kept == null) {
                kept = ArrayList(halos.size)
                for (earlier in 0 until index) kept += halos[earlier]
            }
            if (passed) {
                mendAtHalo(ring, physics)
                if (ring.courseIndex >= 0) progress++
            }
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
    var updated = visit.copy(halos = next, haloSerial = serial, progress = progress)
    // Each course ring passed brings the ring two places after it into view.
    for (passed in visit.progress until progress) {
        val upcoming = passed + ASCENT_RINGS_AHEAD
        if (upcoming < updated.goal) updated = withCourseRing(updated, upcoming)
    }
    dimensionRun = dimensionRun.copy(visit = updated)
    if (progress > visit.progress && progress >= updated.goal) completeObjective()
}

/**
 * [visit] with its Ascent course ring [index] placed from the course anchor: turned off the course heading by the
 * tuning's turn range, to alternating sides unless the hash flips the turn, at the first distance for ring 0 and
 * within the spacing range after it, moved clear of obstacles. The ring becomes the next anchor. Three draws of a
 * stateless hash of the run, the visit and the ring; refused like any ring past [ContentBounds.MAX_HALO_RINGS].
 */
internal fun MutableGameState.withCourseRing(visit: DimensionVisit, index: Int): DimensionVisit {
    val tuning = content.dimensions.objectives
    val random = HashRandom(worldHash(runSeed, visit.layer, visit.serial, index, COURSE_SALT))
    val turn = tuning.ascentMinimumTurnDegrees +
        random.nextFloat() * (tuning.ascentMaximumTurnDegrees - tuning.ascentMinimumTurnDegrees)
    val side = (if (index % 2 == 0) 1f else -1f) * (if (random.nextFloat() < COURSE_TURN_FLIP_CHANCE) -1f else 1f)
    val spacing = tuning.ascentMinimumSpacing + random.nextFloat() * (tuning.ascentMaximumSpacing - tuning.ascentMinimumSpacing)
    val heading = visit.courseHeading + side * turn * DEGREES_TO_RADIANS
    val distance = if (index == 0) tuning.ascentFirstDistance else spacing
    val (x, y) = nudgedClearOfObstacles(
        visit.courseX + cos(heading) * distance, visit.courseY + sin(heading) * distance, tuning.haloRadius + HALO_CLEARANCE,
    )
    return visit.copy(
        halos = withHaloRing(visit.halos, HaloRing(index, x, y, courseIndex = index)),
        courseX = x,
        courseY = y,
        courseHeading = heading,
    )
}

/**
 * Embers: one the Core sweeps through is collected (a chime), counting toward an open Harvest, and one past its life
 * goes out. Collecting the goal's last ember completes the Harvest. The list is rebuilt only when one leaves.
 */
private fun MutableGameState.updateEmbers(visit: DimensionVisit) {
    val radius = content.dimensions.objectives.emberRadius
    val embers = visit.embers
    var kept: ArrayList<Ember>? = null
    var collected = 0
    for (index in embers.indices) {
        val ember = embers[index]
        val expired = elapsed >= ember.expiresAt
        val swept = !expired && segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, ember.x, ember.y, radius)
        if (expired || swept) {
            if (kept == null) {
                kept = ArrayList(embers.size)
                for (earlier in 0 until index) kept += embers[earlier]
            }
            if (swept) collected++
            continue
        }
        kept?.add(ember)
    }
    if (kept == null) return
    if (collected > 0) emitSound(GameplayAudioCue.PICKUP)
    val counting = visit.objective == DimensionObjective.HARVEST && !visit.completed
    val progress = if (counting) min(visit.goal, visit.progress + collected) else visit.progress
    dimensionRun = dimensionRun.copy(visit = visit.copy(embers = kept.toImmutableList(), progress = progress))
    if (counting && progress >= visit.goal) completeObjective()
}

/**
 * A burning enemy died during an open Harvest: an ember waits where it fell for the tuning's life. At most
 * [ContentBounds.MAX_EMBERS] wait; one more puts out the oldest. Draws nothing.
 */
internal fun MutableGameState.dropEmber(x: Float, y: Float) {
    val visit = dimensionRun.visit ?: return
    val ember = Ember(x, y, elapsed + content.dimensions.objectives.emberLifeSeconds)
    val embers = visit.embers
    val next = if (embers.size < ContentBounds.MAX_EMBERS) {
        embers.toImmutableListAppending(ember)
    } else {
        val shifted = ArrayList<Ember>(embers.size)
        for (index in 1 until embers.size) shifted += embers[index]
        shifted += ember
        shifted.toImmutableList()
    }
    dimensionRun = dimensionRun.copy(visit = visit.copy(embers = next))
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
 * lethal tick). Flying at pace resets both clocks. Baby mode scales only incoming kinds, so the scorch applies the
 * run's incoming-damage factor itself.
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
        maxHp * physics.scorchSharePerSecond * physics.scorchTickSeconds * incomingDamageFactor,
        ignoreReduction = true,
        kind = IncomingDamage.SELF,
        lethalReason = DimensionNotices.SCORCHED,
    )
}
