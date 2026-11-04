// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.max

/**
 * The Core ribbon of [InteractionFxReducer]: a ring of run-local positions at the start of past fixed steps, their age
 * in simulation seconds and their [CoreRibbonProjection] flags, published newest first as a [CoreRibbonProjection].
 * Presentation memory only; fixed arrays, allocation-free except for the published projection.
 */
internal class CoreRibbonTrack {
    private val ribbonPositions = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES * 2)
    private val ribbonAges = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private val ribbonFlags = IntArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private var ribbonHead = -1 // ring index of the newest sample
    private var ribbonCount = 0
    // The last MotionSample's position and speed (every step, sampled or not): a step's jump is measured from it.
    private var ribbonStepX = Float.NaN
    private var ribbonStepY = Float.NaN
    private var ribbonStepSpeed = 0f
    private var coreRibbonDirty = false
    // Newest-first staging for a published ribbon, which copies only the live prefix.
    private val ribbonStagedPositions = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES * 2)
    private val ribbonStagedAges = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private val ribbonStagedFlags = IntArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)

    /** Whether the ribbon holds no live sample. */
    val isEmpty: Boolean get() = ribbonCount == 0

    /**
     * Records the Core's position at the start of the step on the ribbon once it lies a spacing ([ribbonSpacing]) from
     * the newest sample; a Dash start always samples. A Core at or below [RIBBON_MIN_SPEED] outside a Dash stores
     * nothing. A jump beyond the step's reach (a blink) starts a new chain, so no segment crosses it. Returns whether
     * the ribbon changed: tracking the step alone is not a visible change.
     */
    fun sample(cue: VisualFxCue.MotionSample): Boolean {
        val x = cue.previousCoreX
        val y = cue.previousCoreY
        if (!x.isFinite() || !y.isFinite()) return false
        val lastX = ribbonStepX
        val lastY = ribbonStepY
        val lastSpeed = ribbonStepSpeed
        ribbonStepX = x
        ribbonStepY = y
        ribbonStepSpeed = cue.speed
        val dash = cue.dashPhaseTime > 0f
        if (cue.speed <= RIBBON_MIN_SPEED && !dash) return false
        val dashFlag = if (dash) CoreRibbonProjection.DASH else 0
        val reach = max(lastSpeed, cue.speed) * cue.deltaSeconds * RIBBON_TELEPORT_FACTOR + RIBBON_TELEPORT_SLACK
        val jumped = !lastX.isNaN() && distanceSquared(lastX, lastY, x, y) > reach * reach
        if (ribbonCount == 0 || jumped) {
            appendRibbon(x, y, CoreRibbonProjection.START or dashFlag)
            return true
        }
        val dashStarts = dash && (ribbonFlags[ribbonHead] and CoreRibbonProjection.DASH) == 0
        val spacing = ribbonSpacing(cue.speed)
        val fromNewest = distanceSquared(ribbonPositions[ribbonHead * 2], ribbonPositions[ribbonHead * 2 + 1], x, y)
        if (!dashStarts && fromNewest < spacing * spacing) return false
        appendRibbon(x, y, dashFlag)
        return true
    }

    /** Writes the newest sample at age 0; once the ring is full it takes the oldest sample's slot. */
    private fun appendRibbon(x: Float, y: Float, flags: Int) {
        val index = (ribbonHead + 1) % InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES
        ribbonHead = index
        ribbonPositions[index * 2] = x
        ribbonPositions[index * 2 + 1] = y
        ribbonAges[index] = 0f
        ribbonFlags[index] = flags
        if (ribbonCount < InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES) ribbonCount++
        coreRibbonDirty = true
    }

    /** Ring index of the live sample [newer] places behind the newest (0 is the newest). */
    private fun ribbonIndex(newer: Int): Int =
        (ribbonHead - newer + InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES) % InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES

    /** Ages the live samples in simulation time and drops the oldest past [RIBBON_MAX_AGE]; a pause freezes the ribbon. */
    fun age(delta: Float) {
        if (ribbonCount == 0) return
        for (newer in 0 until ribbonCount) ribbonAges[ribbonIndex(newer)] += delta
        while (ribbonCount > 0 && ribbonAges[ribbonIndex(ribbonCount - 1)] > RIBBON_MAX_AGE) ribbonCount--
        coreRibbonDirty = true
    }

    /**
     * The ribbon and the tracked step move with the world, so a rebase draws no long segment and breaks no chain.
     * Returns whether a live sample moved.
     */
    fun rebase(shiftX: Float, shiftY: Float): Boolean {
        ribbonStepX -= shiftX
        ribbonStepY -= shiftY
        if (ribbonCount == 0) return false
        for (newer in 0 until ribbonCount) {
            val index = ribbonIndex(newer)
            ribbonPositions[index * 2] -= shiftX
            ribbonPositions[index * 2 + 1] -= shiftY
        }
        coreRibbonDirty = true
        return true
    }

    /** Drops every sample and the tracked step; [invalidate] republishes the empty ribbon. */
    fun clear() {
        ribbonCount = 0
        ribbonHead = -1
        ribbonStepX = Float.NaN
        ribbonStepY = Float.NaN
        ribbonStepSpeed = 0f
    }

    /** Marks the ribbon for the next [published] projection. */
    fun invalidate() {
        coreRibbonDirty = true
    }

    /** The ribbon to publish: rebuilt when it changed since the last call, otherwise [previous]. */
    fun published(previous: CoreRibbonProjection): CoreRibbonProjection {
        if (!coreRibbonDirty) return previous
        coreRibbonDirty = false
        return buildCoreRibbon()
    }

    /** Publishes the live samples newest first; the projection copies them out of the staging arrays. */
    private fun buildCoreRibbon(): CoreRibbonProjection {
        for (newer in 0 until ribbonCount) {
            val index = ribbonIndex(newer)
            ribbonStagedPositions[newer * 2] = ribbonPositions[index * 2]
            ribbonStagedPositions[newer * 2 + 1] = ribbonPositions[index * 2 + 1]
            ribbonStagedAges[newer] = ribbonAges[index]
            ribbonStagedFlags[newer] = ribbonFlags[index]
        }
        return CoreRibbonProjection.of(ribbonStagedPositions, ribbonStagedAges, ribbonStagedFlags, ribbonCount)
    }

    private fun distanceSquared(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
        val dx = toX - fromX
        val dy = toY - fromY
        return dx * dx + dy * dy
    }
}
