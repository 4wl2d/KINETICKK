// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.content.api.SteeringTuning
import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** A world vector packed in one Long: returned from the law without allocation on every target. */
@JvmInline
internal value class SteeringVector(private val packed: Long) {
    val x: Float get() = Float.fromBits((packed ushr 32).toInt())
    val y: Float get() = Float.fromBits(packed.toInt())
}

internal fun steeringVector(x: Float, y: Float): SteeringVector =
    SteeringVector((x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xFFFF_FFFFL))

/** Where to hold the singularity: [heading] (radians) and [distance] from the Core (dp). */
@JvmInline
internal value class SteeringAim(private val packed: Long) {
    val heading: Float get() = Float.fromBits((packed ushr 32).toInt())
    val distance: Float get() = Float.fromBits(packed.toInt())
}

internal fun steeringAim(heading: Float, distance: Float): SteeringAim =
    SteeringAim((heading.toRawBits().toLong() shl 32) or (distance.toRawBits().toLong() and 0xFFFF_FFFFL))

/** The Brake's factor on the plain law: the Brake keeps full counter-thrust and none of the piloting terms. */
private const val BRAKE_PULL_FACTOR: Float = 1.12f

/**
 * The flight law: the Core's acceleration (dp/s², the Brake's factor included) for the tether
 * ([tetherX], [tetherY]) = singularity − Core, the pointer offset ([offsetX], [offsetY]) = pointer − screen
 * centre and the Core's velocity ([velocityX], [velocityY]). The pull toward the singularity grows with the
 * tether's length and splits into an along-track and a turning part. Backward along-track pull that only
 * camera lag causes is dropped unless the pointer points behind the screen centre; the turning part gains grip
 * with speed; a bounded share of it pumps forward up to a little above the straight steady speed of the
 * pointer's offset; a launch assist adds thrust below the steady speed of the forward offset. Exhaustion
 * removes every positive along-track source alike; lateral control and counter-thrust stay. The Brake flies
 * the plain law. updateCore integrates it; the inverse and the test pilots evaluate it.
 */
internal fun MutableGameState.steeringAcceleration(
    tetherX: Float,
    tetherY: Float,
    offsetX: Float,
    offsetY: Float,
    velocityX: Float,
    velocityY: Float,
): SteeringVector {
    val tuning = content.tempo.steering
    val distance = max(0.001f, length(tetherX, tetherY))
    val directionX = tetherX / distance
    val directionY = tetherY / distance
    val overdriveMultiplier = if (overdriveTime > 0f) 1.3f else 1f
    val magnetism = effectiveMagnetism()
    val pull = (tuning.basePull + distance * magnetism) * overdriveMultiplier
    val currentSpeed = length(velocityX, velocityY)
    val moving = currentSpeed > 1f
    val forwardX = if (moving) velocityX / currentSpeed else directionX
    val forwardY = if (moving) velocityY / currentSpeed else directionY
    var along = (directionX * forwardX + directionY * forwardY) * pull
    var lateralX = directionX * pull - forwardX * along
    var lateralY = directionY * pull - forwardY * along
    if (braking) {
        if (along > 0f && moving) along *= tetherAuthority
        return steeringVector(
            (forwardX * along + lateralX) * BRAKE_PULL_FACTOR,
            (forwardY * along + lateralY) * BRAKE_PULL_FACTOR,
        )
    }
    if (along < 0f) along *= counterThrustShare(tuning, offsetX, offsetY, forwardX, forwardY)
    val grip = gripFactor(tuning, currentSpeed)
    lateralX *= grip
    lateralY *= grip
    along += slingThrust(tuning, length(lateralX, lateralY), length(offsetX, offsetY), currentSpeed)
    along += launchAssist(tuning, magnetism, offsetX * forwardX + offsetY * forwardY, currentSpeed)
    // Exhaustion only weakens acceleration along existing momentum, from every source alike. Lateral
    // steering, counter-thrust and the Brake damping remain available even at zero stability.
    if (along > 0f && moving) along *= tetherAuthority
    return steeringVector(forwardX * along + lateralX, forwardY * along + lateralY)
}

/**
 * Share of a backward along-track pull that acts: all of it when the pointer points straight behind the screen
 * centre, none of it (at a full gate) when the pointer is sideways or ahead, so camera lag no longer brakes a turn.
 * Offsets inside the dead zone carry proportionally less backward intent.
 */
private fun MutableGameState.counterThrustShare(
    tuning: SteeringTuning,
    offsetX: Float,
    offsetY: Float,
    forwardX: Float,
    forwardY: Float,
): Float {
    val reference = max(length(offsetX, offsetY), tuning.counterThrustDeadzoneReach * longHalfAxis())
    val intent = if (reference > 0f) (-(offsetX * forwardX + offsetY * forwardY) / reference).coerceIn(0f, 1f) else 0f
    return (1f - tuning.counterThrustGate) + tuning.counterThrustGate * intent
}

/** Turning-pull factor: grows linearly from 1 at rest to full grip at the steady speed of the full-grip reach. */
private fun MutableGameState.gripFactor(tuning: SteeringTuning, speed: Float): Float {
    val full = steadyCursorSpeed(tuning.gripFullReach)
    val share = if (full > 0f) min(1f, speed / full) else 1f
    return 1f + tuning.gripGain * gripStrength.coerceIn(0f, tuning.maximumStatMultiplier) * share
}

/**
 * Forward thrust the slingshot pump makes of a turning acceleration [lateral]: full below the knee, fading to
 * nothing at the soft speed `slingHeadroom` above the straight steady speed of the pointer's [offset] (base stats),
 * so carving cannot run away on a low-drag build.
 */
private fun MutableGameState.slingThrust(tuning: SteeringTuning, lateral: Float, offset: Float, speed: Float): Float {
    val soft = steadyCursorSpeedAtOffset(offset) * (1f + tuning.slingHeadroom)
    if (soft <= 0f) return 0f
    val fade = ((soft - speed) / (soft * (1f - tuning.slingKneeShare))).coerceIn(0f, 1f)
    return tuning.slingShare * slingStrength.coerceIn(0f, tuning.maximumStatMultiplier) * lateral * fade
}

/**
 * Launch assist for a forward pointer [throttle] (the offset along the heading): proportional to the throttle and
 * to the speed missing to that throttle's straight steady speed; nothing at or above it, sideways or behind.
 */
private fun MutableGameState.launchAssist(tuning: SteeringTuning, magnetism: Float, throttle: Float, speed: Float): Float {
    if (throttle <= 0f) return 0f
    val steady = steadyCursorSpeedAtOffset(throttle)
    if (steady <= 0f) return 0f
    return tuning.spoolGain * magnetism * throttle * max(0f, 1f - speed / steady)
}

private fun MutableGameState.longHalfAxis(): Float = max(screenWidth, screenHeight) * 0.5f

/**
 * Exponential drag per second this step (the Brake's damping replaces it while braking). Straight flight adds
 * up to `straightDrag` of it through [MutableGameState.coreStraightness]; multiplicative, so a low-drag build
 * keeps its identity.
 */
internal fun MutableGameState.steeringDrag(): Float {
    var drag = if (build.upside(RelicId.PERPETUAL_VECTOR)) dragCoefficient * 0.15f else dragCoefficient
    // Frictionless: Superconduct strips most of the drag (the Brake still damps as before).
    if (frictionlessUntil > elapsed && hasVerb(ReactionId.SUPERCONDUCT)) {
        drag *= reactionValue(ReactionId.SUPERCONDUCT, FRICTIONLESS_DRAG_VALUE)
    }
    drag *= 1f + content.tempo.steering.straightDrag * coreStraightness
    return layerDrag(drag)
}

/**
 * Inverse of [steeringAcceleration] for the current state: the heading and tether length that ask the
 * law for the acceleration ([accelerationX], [accelerationY]). The pilots' approximation of the plain law at
 * full tether authority without Overdrive, Brake or a magnetism liability; pilots clamp the distance themselves.
 */
internal fun MutableGameState.steeringAimFor(accelerationX: Float, accelerationY: Float): SteeringAim =
    steeringAim(
        atan2(accelerationY, accelerationX),
        (length(accelerationX, accelerationY) - content.tempo.steering.basePull) / magnetStrength,
    )

/**
 * Light straight-line fatigue: [MutableGameState.coreStraightness] rises toward 1 while the smoothed heading
 * turns slower than `straightCurvatureRate` and falls quickly in curves. [smoothedBeforeX], [smoothedBeforeY]
 * is the smoothed velocity before this step's update. Below the minimum turn speed nothing builds, so
 * spool-up is not taxed. Step-driven and frame-rate independent.
 */
internal fun MutableGameState.updateStraightness(delta: Float, smoothedBeforeX: Float, smoothedBeforeY: Float) {
    if (delta <= 0f) return
    val tuning = content.tempo.steering
    val minimum = content.tempo.fatigue.minimumTurnSpeed
    val target = if (length(smoothedBeforeX, smoothedBeforeY) < minimum ||
        length(smoothedVelocityX, smoothedVelocityY) < minimum
    ) {
        0f
    } else {
        val turned = abs(
            atan2(
                smoothedBeforeX * smoothedVelocityY - smoothedBeforeY * smoothedVelocityX,
                smoothedBeforeX * smoothedVelocityX + smoothedBeforeY * smoothedVelocityY,
            ),
        )
        (1f - turned / delta / tuning.straightCurvatureRate).coerceIn(0f, 1f)
    }
    val seconds = if (target > coreStraightness) tuning.straightRiseSeconds else tuning.straightFallSeconds
    coreStraightness += (target - coreStraightness) * (1f - exp(-delta / seconds))
}

/**
 * Seconds of camera lag per unit of steady speed, exact for the fixed step and the camera's place in it: the
 * camera follows after the Core moves, so a steady lag `L` satisfies `(L + vΔt)·e^(−kΔt) = L` (0.12921 s, not
 * 1/k).
 */
internal val CAMERA_LAG_SECONDS: Float = run {
    val retained = exp(-CAMERA_FOLLOW_RATE * MutableGameState.FIXED_STEP)
    MutableGameState.FIXED_STEP * retained / (1f - retained)
}

/**
 * Steady Core speed that holding the cursor at [reach] along the viewport's long axis sustains with the
 * base movement stats: thrust `pull + magnetism × offset` against drag (straight-line fatigue built) and the
 * camera's step-exact lag [CAMERA_LAG_SECONDS] per unit of speed. The long half-axis is the farthest the cursor
 * sits from the centre along a screen axis (the edge run's axis); measuring there keeps the collision bands
 * independent of the heading, so a turn never moves them and turning a phone leaves them in place.
 */
internal fun MutableGameState.steadyCursorSpeed(reach: Float): Float =
    steadyCursorSpeedAtOffset(reach * longHalfAxis())

/** Straight steady Core speed with the cursor held [offset] dp ahead (base stats, straightness built). */
internal fun MutableGameState.steadyCursorSpeedAtOffset(offset: Float): Float {
    val tuning = content.tempo.steering
    return (tuning.basePull + BASE_MAGNET_STRENGTH * offset) /
        (BASE_DRAG_COEFFICIENT * (1f + tuning.straightDrag) + BASE_MAGNET_STRENGTH * CAMERA_LAG_SECONDS)
}

/**
 * This build's straight steady speed at [reach] along the long axis: its permanent drag (Perpetual Vector
 * included) and magnetism, straightness built, no Overdrive or Frictionless.
 */
internal fun MutableGameState.buildSteadyCursorSpeed(reach: Float): Float {
    val tuning = content.tempo.steering
    val magnetism = effectiveMagnetism()
    val drag = (if (build.upside(RelicId.PERPETUAL_VECTOR)) dragCoefficient * 0.15f else dragCoefficient) *
        (1f + tuning.straightDrag)
    return (tuning.basePull + magnetism * reach * longHalfAxis()) / (drag + magnetism * CAMERA_LAG_SECONDS)
}
