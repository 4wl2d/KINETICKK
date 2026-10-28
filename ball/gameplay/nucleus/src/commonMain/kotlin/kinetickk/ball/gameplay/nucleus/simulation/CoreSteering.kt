// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.length
import kotlin.jvm.JvmInline
import kotlin.math.atan2
import kotlin.math.max

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

/**
 * The flight law: the Core's acceleration (the Brake's factor included) for the tether
 * ([tetherX], [tetherY]) = singularity − Core, the pointer's offset from the screen centre and the
 * Core's velocity. The pull grows with the tether's length; exhaustion only weakens the part along
 * existing momentum. The pointer offset is part of the law's input, but this law does not read it.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.steeringAcceleration(
    tetherX: Float,
    tetherY: Float,
    offsetX: Float,
    offsetY: Float,
    velocityX: Float,
    velocityY: Float,
): SteeringVector {
    val distance = max(0.001f, length(tetherX, tetherY))
    val directionX = tetherX / distance
    val directionY = tetherY / distance
    val overdriveMultiplier = if (overdriveTime > 0f) 1.3f else 1f
    val pull = (CORE_BASE_PULL + distance * effectiveMagnetism()) * overdriveMultiplier
    val brakeFactor = if (braking) 1.12f else 1f
    val currentSpeed = length(velocityX, velocityY)
    val forwardX = if (currentSpeed > 1f) velocityX / currentSpeed else directionX
    val forwardY = if (currentSpeed > 1f) velocityY / currentSpeed else directionY
    val forwardPull = max(0f, (directionX * forwardX + directionY * forwardY) * pull)
    // Exhaustion only weakens acceleration along existing momentum. Lateral steering,
    // counter-thrust and the Brake damping remain available even at zero stability.
    val suppressedPull = if (currentSpeed > 1f) forwardPull * (1f - tetherAuthority) else 0f
    return steeringVector(
        (directionX * pull - forwardX * suppressedPull) * brakeFactor,
        (directionY * pull - forwardY * suppressedPull) * brakeFactor,
    )
}

/** Exponential drag per second this step (the Brake's damping replaces it while braking). */
internal fun MutableGameState.steeringDrag(): Float {
    var drag = if (build.upside(RelicId.PERPETUAL_VECTOR)) dragCoefficient * 0.15f else dragCoefficient
    // Frictionless: Superconduct strips most of the drag (the Brake still damps as before).
    if (frictionlessUntil > elapsed && hasVerb(ReactionId.SUPERCONDUCT)) {
        drag *= reactionValue(ReactionId.SUPERCONDUCT, FRICTIONLESS_DRAG_VALUE)
    }
    return layerDrag(drag)
}

/**
 * Inverse of [steeringAcceleration] for the current state: the heading and tether length that ask the
 * law for the acceleration ([accelerationX], [accelerationY]). Exact for a Core at full tether
 * authority without Overdrive, Brake or a magnetism liability; pilots clamp the distance themselves.
 */
internal fun MutableGameState.steeringAimFor(accelerationX: Float, accelerationY: Float): SteeringAim =
    steeringAim(
        atan2(accelerationY, accelerationX),
        (length(accelerationX, accelerationY) - CORE_BASE_PULL) / magnetStrength,
    )

/**
 * Steady Core speed that holding the cursor at [reach] along the viewport's long axis sustains with the
 * base movement stats: thrust `pull + magnetism × offset` against drag and the camera's lag
 * `speed / followRate`. The long half-axis is the farthest the cursor sits from the centre along a screen
 * axis (the edge run's axis); measuring there keeps the collision bands independent of the heading, so a
 * turn never moves them and turning a phone leaves them in place.
 */
internal fun MutableGameState.steadyCursorSpeed(reach: Float): Float =
    steadyCursorSpeedAtOffset(reach * max(screenWidth, screenHeight) * 0.5f)

/** Steady Core speed with the cursor held [offset] dp from the screen centre (base movement stats). */
internal fun steadyCursorSpeedAtOffset(offset: Float): Float =
    (CORE_BASE_PULL + BASE_MAGNET_STRENGTH * offset) /
        (BASE_DRAG_COEFFICIENT + BASE_MAGNET_STRENGTH / CAMERA_FOLLOW_RATE)
