// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (2.0 * PI).toFloat()
private const val RAD_TO_DEG = 57.29578f

private const val THERMAL = 0
private const val CRYO = 1
private const val ION = 2
private const val GRAVITIC = 3
private const val ENTROPY = 4
private const val SONIC = 5
private const val VOID = 6

/** Mark kind ordinals (`MarkKind`, combat-final §2.2). */
private const val MARK_SPALL = 0
private const val MARK_CRACKED = 1
private const val MARK_SPORED = 2
private const val MARK_BOOMED = 3
private const val MARK_SUPPRESSED = 4

/** A Cracked mark is armed for Breakpoint while the Core brakes this close (world units). */
private const val CRACKED_ARMED_REACH = 320f

/** 0..1 pulse with [period] seconds at run time [time]. */
private fun pulse(time: Float, period: Float): Float = (sin(time * TAU / period) + 1f) * 0.5f

/**
 * Control and mark glyphs around an enemy (screen space, never turned; hud-fx §4.6): the daze ticks,
 * the silence slash, throw streaks, Blackfire flames, the pending Unmake ring, its mark, the Void
 * Anchor rings, the attractor bars and the Apex strike brackets. A crowded screen keeps the mark as
 * a dot, one flame, one tick and the anchor and strike marks; a packed one only those two and a dot.
 */
internal fun DrawScope.drawEnemyControl(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    ring: Float,
    roles: KkRolePalette,
) {
    val bits = enemy.controlBits
    if (bits == 0) return
    val time = engine.renderElapsed
    val crowd = FxFrame.crowd
    val packed = crowd > FxFrame.CROWD_SIG_ONLY
    val crowded = crowd > FxFrame.CROWD_SECONDARY_OFF
    val radius = enemy.radius
    if (bits and EnemyControlBits.VOID_ANCHOR != 0 && FxDrawBudget.take(FxPool.GLYPHS, 8, signature = true)) {
        val void = roles.element(VOID)
        for (circle in 0..1) {
            val r = ring + if (circle == 0) 10f else 16f
            val turn = (if (circle == 0) 90f else -90f) * time
            for (arc in 0 until 4) {
                drawArc(void, turn + arc * 90f, 50f, false, Offset(center.x - r, center.y - r), Size(r * 2f, r * 2f), alpha = 0.85f,
                    style = kkStroke(1.5f))
            }
        }
    }
    if (bits and EnemyControlBits.APEX_STRIKE_TARGET != 0 && FxDrawBudget.take(FxPool.GLYPHS, 4, signature = true)) {
        val gold = roles.gold.base
        val alpha = 0.6f + 0.4f * pulse(time, 1.2f)
        val reach = radius + 14f
        for (corner in 0 until 4) {
            val sx = if (corner and 1 == 0) -1f else 1f
            val sy = if (corner and 2 == 0) -1f else 1f
            val point = Offset(center.x + sx * reach, center.y + sy * reach)
            drawLine(gold, point, Offset(point.x - sx * 10f, point.y), 2f, alpha = alpha)
            drawLine(gold, point, Offset(point.x, point.y - sy * 10f), 2f, alpha = alpha)
        }
    }
    val blackfire = bits and EnemyControlBits.BLACKFIRE != 0
    if (packed) {
        if (blackfire && FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) {
            drawCircle(roles.element(VOID), 3f, Offset(center.x, center.y - radius * 0.5f))
        }
        return
    }
    if (blackfire && FxDrawBudget.take(FxPool.GLYPHS, if (crowded) 3 else 9, signature = false)) {
        for (flame in 0 until (if (crowded) 1 else 3)) {
            val angle = (-90f + (flame - (if (crowded) 0 else 1)) * 25f) / RAD_TO_DEG
            val flicker = 0.85f + 0.15f * sin(time * TAU * 8f + flame * 2f)
            val length = radius * 0.45f * flicker
            val base = polar(center, radius * 0.45f, angle)
            withTransform({
                translate(base.x, base.y)
                rotate(angle * RAD_TO_DEG, Offset.Zero)
                scale(length, length, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.flame, roles.element(VOID))
                scale(0.65f, 0.65f, Offset.Zero) { drawPath(WorldUnitShapes.flame, Kk.Ink) }
            }
            drawCircle(roles.element(THERMAL), 2f, polar(base, length, angle))
        }
    }
    if (bits and EnemyControlBits.STOPPED != 0 && FxDrawBudget.take(FxPool.GLYPHS, if (crowded) 1 else 3, signature = false)) {
        // Daze ticks orbit an ellipse above the enemy, one turn every 0.8 s.
        val above = Offset(center.x, center.y - ring - 10f)
        for (tick in 0 until (if (crowded) 1 else 3)) {
            val angle = time * TAU / 0.8f + tick * TAU / 3f
            val point = Offset(above.x + cos(angle) * 14f, above.y + sin(angle) * 5f)
            drawLine(Kk.Bone, Offset(point.x, point.y - 2.5f), Offset(point.x, point.y + 2.5f), 1.5f)
        }
    }
    val mark = EnemyControlBits.markKind(bits)
    if (mark >= 0 && FxDrawBudget.take(FxPool.GLYPHS, 3, signature = false)) {
        val at = Offset(center.x - ring - 6f, center.y)
        if (crowded) drawCircle(markHue(mark, roles), 3f, at) else drawMark(engine, enemy, mark, at, roles)
    }
    if (crowded) return
    if (bits and EnemyControlBits.SILENCED != 0 && FxDrawBudget.take(FxPool.GLYPHS, 3, signature = false)) {
        val at = polar(center, ring + 6f, -0.75f * PI.toFloat())
        val sonic = roles.element(SONIC)
        drawArc(sonic, -125f, 70f, false, Offset(at.x - 6f, at.y - 6f), Size(12f, 12f), style = kkStroke(1.5f))
        drawArc(sonic, -125f, 70f, false, Offset(at.x - 10f, at.y - 10f), Size(20f, 20f), style = kkStroke(1.5f))
        drawLine(roles.element(VOID), Offset(at.x - 7f + 7f * KkShape.ShearRatio, at.y + 7f), Offset(at.x + 7f - 7f * KkShape.ShearRatio, at.y - 7f), 2f)
    }
    if (bits and EnemyControlBits.THROWN != 0 && FxDrawBudget.take(FxPool.GLYPHS, 3, signature = false)) {
        val speed = sqrt(enemy.vx * enemy.vx + enemy.vy * enemy.vy)
        if (speed > 1f) {
            val bx = -enemy.vx / speed
            val by = -enemy.vy / speed
            for (streak in 0 until 3) {
                val side = (streak - 1) * 6f
                val length = ThrowStreaks[streak]
                val from = Offset(center.x + bx * (radius + 4f) - by * side, center.y + by * (radius + 4f) + bx * side)
                drawLine(Kk.Bone, from, Offset(from.x + bx * length, from.y + by * length), 2f, alpha = 0.5f)
            }
        }
    }
    if (bits and EnemyControlBits.UNMAKE_PENDING != 0 && FxDrawBudget.take(FxPool.GLYPHS, 2, signature = false)) {
        val phase = (time % 0.4f) / 0.4f
        drawCircle(roles.element(VOID), radius + 12f - 10f * phase, center, style = WorldStrokes.dashedThin)
        drawCircle(Kk.Ink, 3f, center)
    }
    if (bits and EnemyControlBits.ATTRACTOR_ANCHOR != 0 && FxDrawBudget.take(FxPool.GLYPHS, 2, signature = false)) {
        val below = Offset(center.x, center.y + ring + 8f)
        drawRect(roles.element(ION), Offset(below.x - 4f, below.y - 3f), Size(8f, 2f))
        drawRect(roles.element(GRAVITIC), Offset(below.x - 4f, below.y + 1f), Size(8f, 2f))
    }
}

private val ThrowStreaks = floatArrayOf(30f, 22f, 16f)

/** A mark's element hue (its crowd dot). */
private fun markHue(mark: Int, roles: KkRolePalette): Color = when (mark) {
    MARK_SPALL -> roles.element(THERMAL)
    MARK_BOOMED -> roles.element(SONIC)
    else -> roles.element(ENTROPY)
}

private fun DrawScope.drawMark(engine: GameplayRenderModel, enemy: EnemyProjection, mark: Int, at: Offset, roles: KkRolePalette) {
    when (mark) {
        MARK_SPALL -> {
            drawShearedPlate(at, 10f, 6f, roles.element(THERMAL))
            val lean = 3f * KkShape.ShearRatio
            val ink = Kk.Ink
            drawLine(ink, Offset(at.x - 5f + lean, at.y - 3f), Offset(at.x + 5f + lean, at.y - 3f), 1f)
            drawLine(ink, Offset(at.x - 5f - lean, at.y + 3f), Offset(at.x + 5f - lean, at.y + 3f), 1f)
        }
        MARK_CRACKED -> {
            // Armed for Breakpoint while the Core brakes nearby: brighter and thicker.
            val dx = enemy.x - engine.coreX
            val dy = enemy.y - engine.coreY
            val armed = engine.braking && dx * dx + dy * dy <= CRACKED_ARMED_REACH * CRACKED_ARMED_REACH
            val color = roles.element(ENTROPY)
            val width = if (armed) 2.5f else 1.5f
            val alpha = if (armed) 1f else 0.7f
            drawLine(color, Offset(at.x - 6f, at.y), Offset(at.x - 2f, at.y - 3f), width, alpha = alpha)
            drawLine(color, Offset(at.x - 2f, at.y - 3f), Offset(at.x + 2f, at.y + 3f), width, alpha = alpha)
            drawLine(color, Offset(at.x + 2f, at.y + 3f), Offset(at.x + 6f, at.y), width, alpha = alpha)
            drawLine(Kk.Bone, Offset(at.x + 6f, at.y - 2.5f), Offset(at.x + 6f, at.y + 2.5f), 1.5f, alpha = alpha)
        }
        MARK_SPORED -> {
            val color = roles.element(ENTROPY)
            for (dot in 0 until 3) drawCircle(color, 2.2f, polar(at, 4f, -PI.toFloat() / 2f + dot * TAU / 3f))
        }
        MARK_BOOMED -> {
            val sonic = roles.element(SONIC)
            drawArc(sonic, -35f, 70f, false, Offset(at.x - 4f, at.y - 4f), Size(8f, 8f), style = kkStroke(1.5f))
            drawArc(sonic, -35f, 70f, false, Offset(at.x - 7f, at.y - 7f), Size(14f, 14f), style = kkStroke(1.5f))
        }
        MARK_SUPPRESSED -> {
            drawCircle(Kk.Ink, 5f, at)
            drawLine(Kk.Bone, Offset(at.x - 3.5f, at.y + 3.5f), Offset(at.x + 3.5f, at.y - 3.5f), 1.5f)
        }
        else -> Unit
    }
}

/**
 * Lingering reaction and skill states on the Core ([GameplayRenderModel.coreState], hud-fx §4.6):
 * Ablaze flames, Frictionless skate lines, the Blast Jump rings, the Arc Weave orbit, guard shards,
 * Null Shroud beads, primed and coiled Dashes, and the fields the Core stands in.
 */
internal fun DrawScope.drawCoreStates(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val state = engine.coreState
    val radius = engine.coreRadius
    val time = engine.renderElapsed
    if (state.ablaze01 > 0f && FxDrawBudget.take(FxPool.CORE_STATES, 8, signature = false)) {
        val thermal = roles.element(THERMAL)
        val alpha = 0.5f + 0.5f * state.ablaze01.coerceIn(0f, 1f)
        for (flame in 0 until 8) {
            val angle = flame * TAU / 8f
            val length = 10f + 6f * pulse(time + flame * 0.07f, 0.125f)
            val base = polar(core, radius + 6f, angle)
            withTransform({
                translate(base.x, base.y)
                rotate(angle * RAD_TO_DEG, Offset.Zero)
                scale(length, length, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.flame, thermal, alpha = alpha)
            }
        }
    }
    if (state.frictionless01 > 0f && engine.speed > 1f && FxDrawBudget.take(FxPool.CORE_STATES, 3, signature = false)) {
        val cryo = roles.element(CRYO)
        val speed = engine.speed
        val bx = -engine.velocityX / speed
        val by = -engine.velocityY / speed
        for (line in 0 until 3) {
            val side = (line - 1) * 6f
            val length = SkateLines[line]
            val from = Offset(core.x + bx * radius - by * side, core.y + by * radius + bx * side)
            drawLine(cryo, from, Offset(from.x + bx * length, from.y + by * length), 2f, alpha = state.frictionless01.coerceIn(0f, 1f))
        }
    }
    if (state.blastJumpReady && FxDrawBudget.take(FxPool.CORE_STATES, 10, signature = false)) {
        val sonic = roles.element(SONIC)
        val alpha = 0.5f + 0.5f * pulse(time, 0.15f)
        drawCircle(sonic, radius + 14f, core, alpha = alpha, style = kkStroke(2f))
        drawCircle(sonic, radius + 20f, core, alpha = alpha, style = kkStroke(1.5f))
        val thermal = roles.element(THERMAL)
        for (tick in 0 until 8) {
            val angle = tick * TAU / 8f
            drawLine(thermal, polar(core, radius + 17f, angle), polar(core, radius + 23f, angle), 1.5f, alpha = alpha)
        }
    }
    if (state.weave01 > 0f && FxDrawBudget.take(FxPool.CORE_STATES, 4, signature = false)) {
        drawCircle(roles.element(SONIC), radius + 18f, core, alpha = 0.3f, style = kkStroke(1.5f))
        val ion = roles.element(ION)
        for (dot in 0 until 3) drawCircle(ion, 2.5f, polar(core, radius + 18f, time * 6f + dot * TAU / 3f))
    }
    val shards = state.guardShards
    if (shards > 0 && FxDrawBudget.take(FxPool.CORE_STATES, shards * 2, signature = false)) {
        val cryo = roles.element(CRYO)
        for (shard in 0 until shards) {
            val angle = time * 3f + shard * TAU / shards
            val point = polar(core, radius + 74f, angle)
            withTransform({
                translate(point.x, point.y)
                rotate(angle * RAD_TO_DEG + 90f, Offset.Zero)
                scale(16f, 16f, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.shard, cryo)
                scale(0.55f, 0.55f, Offset.Zero) { drawPath(WorldUnitShapes.shard, Kk.Bone) }
            }
        }
    }
    val stored = state.shroudStored
    if (stored > 0 && FxDrawBudget.take(FxPool.CORE_STATES, 2 + stored.coerceAtMost(8), signature = false)) {
        val void = roles.element(VOID)
        drawCircle(Kk.Ink, radius + 44f, core, alpha = 0.25f)
        drawCircle(void, radius + 44f, core, style = kkStroke(2f))
        for (bead in 0 until stored.coerceAtMost(24)) drawCircle(void, 3f, polar(core, radius + 44f, time * 2f + bead * TAU / stored))
    }
    if (state.phased && FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = false)) {
        drawCircle(roles.element(VOID), radius + 3f, core, style = kkStroke(2f))
    }
    if (state.primedDash && FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = false)) {
        drawCircle(roles.you, radius + 10f, core, style = WorldStrokes.dashedHair)
    }
    if (state.coiledDash && FxDrawBudget.take(FxPool.CORE_STATES, 3, signature = false)) {
        val cryo = roles.element(CRYO)
        val turn = time * 3f * RAD_TO_DEG
        for (coil in 0 until 3) {
            val r = radius + 10f + coil * 4f
            drawArc(cryo, turn + coil * 120f, 90f, false, Offset(core.x - r, core.y - r), Size(r * 2f, r * 2f), style = kkStroke(1.5f))
        }
    }
    val mask = state.inFieldMask
    if (mask and (1 shl CombatFieldVisual.PLASMA.ordinal) != 0 && FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = false)) {
        drawCircle(roles.element(THERMAL), radius + 4f, core, alpha = 0.5f + 0.5f * pulse(time, 0.5f), style = kkStroke(2f))
    }
    if (mask and (1 shl CombatFieldVisual.TIME_BUBBLE.ordinal) != 0 && FxDrawBudget.take(FxPool.CORE_STATES, 8, signature = false)) {
        val cryo = roles.element(CRYO)
        for (tick in 0 until 8) {
            val angle = tick * TAU / 8f
            drawLine(cryo, polar(core, radius + 6f, angle), polar(core, radius + 10f, angle), 1.5f)
        }
    }
}

private val SkateLines = floatArrayOf(40f, 30f, 20f)

/**
 * The running Apex around the Core (hud-fx §4.8): six gold arcs turning once per 24 s with a
 * 1.2 s pulse and the remaining share as a gold timer arc, then the character's own active detail.
 */
internal fun DrawScope.drawApexActive(engine: GameplayRenderModel, core: Offset, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val apex = engine.apex
    if (apex.active01 <= 0f) return
    val gold = roles.gold.base
    val light = roles.gold.light
    val radius = engine.coreRadius
    val time = engine.renderElapsed
    val r = radius + 30f
    val turn = time * 15f
    val alpha = 0.35f + 0.25f * pulse(time, 1.2f)
    FxDrawBudget.take(FxPool.CORE_STATES, 7, signature = true)
    for (arc in 0 until 6) {
        drawArc(gold, turn + arc * 60f - 20f, 40f, false, Offset(core.x - r, core.y - r), Size(r * 2f, r * 2f), alpha = alpha, style = kkStroke(2f))
    }
    val timer = radius + 36f
    drawArc(light, -90f, 360f * apex.active01.coerceIn(0f, 1f), false, Offset(core.x - timer, core.y - timer), Size(timer * 2f, timer * 2f),
        style = kkStroke(1.5f))
    val ability = engine.characterAbility
    when (engine.coreShape) {
        CoreShape.ORB -> for (bead in 0 until apex.held.coerceIn(0, 24)) {
            drawCircle(gold, 3f, polar(core, radius + 52f, time * 2f + bead * TAU / max(1, apex.held)))
        }
        CoreShape.PRISM -> for (facet in 0 until ability.facets.coerceIn(0, 4)) {
            val angle = time * 0.6f + facet * 1.5707964f
            val point = Offset(core.x + cos(angle) * 27f, core.y + sin(angle) * 27f)
            rotate(45f + angle * RAD_TO_DEG, point) {
                drawRect(gold, Offset(point.x - 6f, point.y - 6f), Size(12f, 12f), style = kkStroke(2f))
            }
        }
        CoreShape.RING -> {
            val band = ability.ringRadius
            drawCircle(gold, band, core, alpha = 0.5f, style = kkStroke(4.5f))
            for (tick in 0 until 24) {
                val angle = tick * TAU / 24f + time * 0.2f
                drawLine(gold, polar(core, band - 6f, angle), polar(core, band + 6f, angle), 1.5f, alpha = 0.6f)
            }
        }
        CoreShape.DIAMOND -> if (engine.braking) {
            // The mirror stands on the Core-to-pointer side.
            val pointerX = engine.renderCameraX + engine.pointerX - engine.screenWidth * 0.5f
            val pointerY = engine.renderCameraY + engine.pointerY - engine.screenHeight * 0.5f
            val angle = atan2(pointerY - engine.renderCoreY, pointerX - engine.renderCoreX)
            val at = polar(core, radius + 30f, angle)
            rotate(45f + angle * RAD_TO_DEG, at) {
                drawRect(gold, Offset(at.x - 31f, at.y - 31f), Size(62f, 62f), style = kkStroke(2f))
            }
        }
        CoreShape.TESSERACT -> drawHypercube(engine, shakeX, shakeY, gold, light, time)
        CoreShape.STAR -> {
            if (ability.stellarMass > 0) drawCircle(gold, radius * (1.6f + ability.stellarMass / 24f), core, style = kkStroke(2f))
            if (ability.novaFlash > 0f) {
                drawCircle(gold, radius + (1f - ability.novaFlash) * 140f, core, alpha = ability.novaFlash, style = kkStroke(3f))
            }
        }
        CoreShape.SPIRAL -> if (ability.ghostActive) {
            val ghost = world(engine, engine.interpolated(ability.previousGhostX, ability.ghostX),
                engine.interpolated(ability.previousGhostY, ability.ghostY), shakeX, shakeY)
            drawCircle(gold, radius * 1.4f, ghost, style = kkStroke(2f))
        }
        CoreShape.DIPOLE -> {
            val reach = if (ability.polarity == 1) 308f else 420f
            drawCircle(gold, reach, core, alpha = 0.8f, style = kkStroke(2f))
            val flip = time % 1f
            if (flip < 0.12f) drawCircle(gold, reach, core, alpha = 1f - flip / 0.12f, style = kkStroke(4.5f))
        }
        CoreShape.PENTAGON -> {
            val lit = (apex.active01 * 12f).roundToInt()
            for (pip in 0 until 12) {
                val point = polar(core, radius + 40f, -PI.toFloat() / 2f + pip * TAU / 12f)
                drawCircle(Kk.Ink, 3.5f, point)
                drawCircle(if (pip < lit) gold else Kk.Mute2, 2.5f, point)
            }
            val period = ability.beatPeriod
            if (period > 0f) {
                val phase = (time % period) / period
                drawCircle(gold, max(1f, 140f * phase), core, alpha = 1f - phase, style = kkStroke(2f))
            }
        }
        CoreShape.CRESCENT -> {
            val scars = ability.scars
            for (index in scars.indices) {
                val scar = scars[index]
                val start = world(engine, scar.x0, scar.y0, shakeX, shakeY)
                val end = world(engine, scar.x1, scar.y1, shakeX, shakeY)
                val fade = if (scar.fuse < 1f) 1f else (1f - scar.fade / 0.3f).coerceIn(0f, 1f)
                drawLine(gold, start, end, 3f, alpha = fade)
            }
        }
        CoreShape.HEXAGON -> {
            val drones = ability.drones
            for (index in drones.indices) {
                val drone = drones[index]
                val center = world(engine, engine.interpolated(drone.previousX, drone.x), engine.interpolated(drone.previousY, drone.y),
                    shakeX, shakeY)
                drawCircle(gold, 8f, center, style = kkStroke(2f))
            }
            drawNearestDataLines(engine, core, shakeX, shakeY, gold)
        }
        CoreShape.SHARD -> Unit
    }
}

/** Thin gold lines from the Core to up to 16 nearest Data pickups in its pickup reach (Swarm Queen). */
private fun DrawScope.drawNearestDataLines(engine: GameplayRenderModel, core: Offset, shakeX: Float, shakeY: Float, gold: Color) {
    val pickups = engine.pickups
    val reach = engine.pickupRadius * engine.pickupRadius
    var drawn = 0
    for (index in pickups.indices) {
        if (drawn >= 16) return
        val pickup = pickups[index]
        if (pickup.type != PickupType.DATA) continue
        val x = engine.interpolated(pickup.previousX, pickup.x)
        val y = engine.interpolated(pickup.previousY, pickup.y)
        val dx = x - engine.renderCoreX
        val dy = y - engine.renderCoreY
        if (dx * dx + dy * dy > reach) continue
        drawLine(gold, core, world(engine, x, y, shakeX, shakeY), 1f, alpha = 0.4f)
        drawn++
    }
}

/** The Hypercube cage: gold edges with a light offset edge between the lattice nodes, brightening each second. */
private fun DrawScope.drawHypercube(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, gold: Color, light: Color, time: Float) {
    val lattice = engine.characterAbility.lattice
    if (lattice.size < 2) return
    val bright = if (time % 1f < 0.2f) 1f else 0.75f
    val path = HypercubeScratch.path
    path.rewind()
    for (index in lattice.indices) {
        val point = lattice[index]
        val position = world(engine, point.x, point.y, shakeX, shakeY)
        if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
    }
    path.close()
    if (lattice.size >= 3) drawKkPattern(path, KkFillKind.HATCH, gold, 0.06f)
    for (index in lattice.indices) {
        val from = lattice[index]
        val to = lattice[(index + 1) % lattice.size]
        val a = world(engine, from.x, from.y, shakeX, shakeY)
        val b = world(engine, to.x, to.y, shakeX, shakeY)
        drawLine(gold, a, b, 3f, alpha = bright)
        drawLine(light, Offset(a.x, a.y + 4f), Offset(b.x, b.y + 4f), 1.5f, alpha = bright * 0.8f)
    }
}

/** One reused path for the Hypercube's hatched interior (draw-thread confined). */
private object HypercubeScratch {
    val path: Path by lazy { Path() }
}
