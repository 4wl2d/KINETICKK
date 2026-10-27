// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.ln
import kotlin.math.max

internal fun speedVisualRatio(speed: Float): Float {
    val safeSpeed = max(0f, speed)
    return (ln(1f + safeSpeed / 300f) / ln(1f + 5_000f / 300f)).coerceIn(0f, 1f)
}

/**
 * [previous] (a fixed step's start) to [current] (its end) by the frame's interpolation alpha.
 * At alpha 1 (every frame outside a running phase) the current value is returned exactly.
 */
internal fun GameplayRenderModel.interpolated(previous: Float, current: Float): Float {
    val alpha = interpolationAlpha
    return if (alpha >= 1f) current else previous + (current - previous) * alpha
}

/** Drawn Core position: between the last two simulated states. */
internal val GameplayRenderModel.renderCoreX: Float get() = interpolated(previousCoreX, coreX)
internal val GameplayRenderModel.renderCoreY: Float get() = interpolated(previousCoreY, coreY)

/** Drawn camera position: between the last two simulated states. */
internal val GameplayRenderModel.renderCameraX: Float get() = interpolated(previousCameraX, cameraX)
internal val GameplayRenderModel.renderCameraY: Float get() = interpolated(previousCameraY, cameraY)

/**
 * Simulation time for continuous world animations (spins, pulses): advances between fixed steps
 * with the accumulated fraction of a step, so they stay smooth above the 120 Hz step rate. It is
 * the time of the interpolated positions (one step behind [GameplayRenderModel.elapsed] at alpha 0)
 * and exactly `elapsed` at alpha 1.
 */
internal val GameplayRenderModel.renderElapsed: Float
    get() {
        val alpha = interpolationAlpha
        return if (alpha >= 1f) elapsed else elapsed - (1f - alpha) * GameplayRenderModel.FIXED_STEP
    }

/**
 * Age in seconds of a transient look that started with [maxLife] and has [life] left, at the
 * interpolated time: one fixed step behind at alpha 0 (as the interpolated enemies are), exactly
 * `maxLife − life` at alpha 1, never negative. Looks drawn with it stay in step with the world.
 */
internal fun GameplayRenderModel.fxAge(life: Float, maxLife: Float): Float {
    val alpha = interpolationAlpha
    val age = maxLife - life
    return if (alpha >= 1f) max(0f, age) else max(0f, age - (1f - alpha) * GameplayRenderModel.FIXED_STEP)
}

/**
 * Device pixels per world unit. The simulation runs in dp (viewport, pointer and every world
 * length), so the world layer is drawn magnified by the viewport density it accepted.
 */
internal val GameplayRenderModel.worldScale: Float get() = viewportDensity

/** A world point in the current layer: world units inside [withWorldUnits], else device pixels at scale 1. */
internal fun DrawScope.world(
    engine: GameplayRenderModel,
    x: Float,
    y: Float,
    shakeX: Float,
    shakeY: Float,
): Offset = Offset(
    size.width * 0.5f + x - engine.renderCameraX + shakeX,
    size.height * 0.5f + y - engine.renderCameraY + shakeY,
)

/**
 * A world point in device pixels for screen-space layers (HUD, labels, edge markers): the same
 * mapping as [world] inside [withWorldUnits]. [shakeX]/[shakeY] are device pixels.
 */
internal fun DrawScope.worldOnScreen(
    engine: GameplayRenderModel,
    x: Float,
    y: Float,
    shakeX: Float,
    shakeY: Float,
): Offset {
    val scale = engine.worldScale
    if (scale == 1f) return world(engine, x, y, shakeX, shakeY)
    return Offset(
        size.width * 0.5f + (x - engine.renderCameraX) * scale + shakeX,
        size.height * 0.5f + (y - engine.renderCameraY) * scale + shakeY,
    )
}

/**
 * Draws [block] in world units: the canvas is scaled by [scale] about the origin, and the scope's
 * size and density become those of the dp viewport. [world], culling and every world length are
 * then dp, so the frame equals the density-1 world magnified [scale] times. Text measured by the
 * composition is device pixels; draw it through [inScreenUnits].
 */
internal inline fun DrawScope.withWorldUnits(scale: Float, block: DrawScope.() -> Unit) {
    if (scale == 1f) {
        block()
        return
    }
    val context = drawContext
    val screenSize = context.size
    val screenDensity = context.density
    context.canvas.save()
    try {
        context.canvas.scale(scale, scale)
        context.size = Size(screenSize.width / scale, screenSize.height / scale)
        context.density = WorldUnitDensities.world(screenDensity, scale)
        block()
    } finally {
        context.density = screenDensity
        context.size = screenSize
        context.canvas.restore()
    }
}

/**
 * Inside [withWorldUnits], draws [block] in device pixels again (labels anchored to world points).
 * A layer point `p` is the device point `p * scale`.
 */
internal inline fun <T> DrawScope.inScreenUnits(scale: Float, block: DrawScope.() -> T): T {
    if (scale == 1f) return block()
    val context = drawContext
    val worldSize = context.size
    val worldDensity = context.density
    context.canvas.save()
    try {
        context.canvas.scale(1f / scale, 1f / scale)
        context.size = Size(worldSize.width * scale, worldSize.height * scale)
        context.density = WorldUnitDensities.screen(worldDensity, scale)
        return block()
    } finally {
        context.density = worldDensity
        context.size = worldSize
        context.canvas.restore()
    }
}

/**
 * Retained scope densities for the world and screen layers, so switching layers allocates nothing per frame.
 * Two of each: the world itself and the HUD's world-anchored layer, which runs at the interface-scaled density.
 */
internal object WorldUnitDensities {
    private val worldDensities = arrayOfNulls<UnitDensity>(2)
    private val screenDensities = arrayOfNulls<UnitDensity>(2)

    fun world(screen: Density, scale: Float): Density = retained(worldDensities, screen.density / scale, screen.fontScale)

    fun screen(world: Density, scale: Float): Density = retained(screenDensities, world.density * scale, world.fontScale)

    /** The retained density of [value] and [fontScale]; a new one replaces the older of the two. */
    private fun retained(slots: Array<UnitDensity?>, value: Float, fontScale: Float): Density {
        val first = slots[0]
        if (first != null && first.density == value && first.fontScale == fontScale) return first
        val second = slots[1]
        if (second != null && second.density == value && second.fontScale == fontScale) {
            slots[1] = first
            slots[0] = second
            return second
        }
        return UnitDensity(value, fontScale).also {
            slots[1] = first
            slots[0] = it
        }
    }

    private class UnitDensity(override val density: Float, override val fontScale: Float) : Density
}
