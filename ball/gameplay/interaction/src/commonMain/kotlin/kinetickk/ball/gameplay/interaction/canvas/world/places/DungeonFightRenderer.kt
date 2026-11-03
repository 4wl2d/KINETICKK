// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.gameplay.nucleus.render.DungeonHazardProjection
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * The sealed room's fight on the ground, between the floor and everything that moves: the incoming wave's breach
 * portals closing on their landing points (the guardian's larger, in the element's hue), each hazard's telegraph (a
 * vent's disc filling inside its turning dashed rim, a lance's band narrowing to its emitters' line, both blinking in
 * their last quarter) and its strike (a white-hot disc or beam fading over the strike time), and the living guardian's
 * sigil turning under it. All of it is gameplay-critical, so it draws as signature work of FxPool.DUNGEON; positions
 * and times are the projection's, the clock the run's render time.
 */

/** A breach portal starts this many times its member's radius wide and closes to [BREACH_END]. */
private const val BREACH_START = 3.4f
private const val BREACH_END = 1.15f
private const val GUARDIAN_BREACH = 1.8f

/** Dashes of a vent's rim and of the guardian's sigil, and how fast they turn (rad/s, at most a turn per 24 s here). */
private const val RIM_DASHES = 12
private const val SIGIL_DASHES = 8
private const val SIGIL_TURN = 0.26f

/** Draws the held dungeon's breaches, hazards and guardian sigil when a room of it is sealed. */
internal fun DrawScope.drawDungeonFight(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val dungeon = engine.structures.dungeon ?: return
    if (dungeon.engagedRoom < 0) return
    val structure = heldStructure(engine) ?: return
    val template = engine.content.structures.template(structure.template)
    val accent = structureAccent(template, roles)
    val time = engine.renderElapsed
    drawGuardianSigil(engine, dungeon, shakeX, shakeY, time, accent, roles)
    drawBreaches(engine, dungeon, shakeX, shakeY, time, accent, roles)
    val hazards = dungeon.hazards
    val strike = engine.content.structures.hazards.strikeSeconds
    for (index in hazards.indices) drawHazard(engine, hazards[index], shakeX, shakeY, time, strike, accent, roles)
}

/** The incoming wave's portals while its telegraph runs: rings closing on each landing point over a filling disc. */
private fun DrawScope.drawBreaches(
    engine: GameplayRenderModel,
    dungeon: DungeonProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (dungeon.incomingAt < 0f || dungeon.breaches.isEmpty()) return
    val telegraph = engine.content.structures.waveTelegraphSeconds
    val progress = (1f - (dungeon.incomingAt - time) / telegraph).coerceIn(0f, 1f)
    val breaches = dungeon.breaches
    FxDrawBudget.take(FxPool.DUNGEON, breaches.size * BREACH_OPS, signature = true)
    for (index in breaches.indices) {
        val breach = breaches[index]
        val at = world(engine, breach.x, breach.y, shakeX, shakeY)
        val scale = if (breach.guardian) GUARDIAN_BREACH else 1f
        val color = if (breach.guardian) kkMix(accent, Kk.Bone, 0.2f) else roles.threat
        val radius = breach.radius * scale
        val ring = radius * (BREACH_START + (BREACH_END - BREACH_START) * progress)
        drawLightPool(at, radius * 4f, color, 0.08f + 0.22f * progress)
        drawCircle(color, radius * BREACH_END * progress, at, alpha = 0.18f + 0.3f * progress)
        drawCircle(color, ring, at, alpha = 0.35f + 0.55f * progress, style = kkStroke(if (breach.guardian) 4f else 2.5f))
        // Four cracks turning in toward the point.
        val turn = time * 1.6f + index
        for (crack in 0 until 4) {
            val angle = turn + crack * (CanvasMath.TAU / 4f)
            val from = Offset(at.x + cos(angle) * ring * 1.25f, at.y + sin(angle) * ring * 1.25f)
            val to = Offset(at.x + cos(angle) * ring * 0.95f, at.y + sin(angle) * ring * 0.95f)
            drawLine(color, from, to, 2f, alpha = 0.3f + 0.5f * progress)
        }
    }
}

private const val BREACH_OPS = 7

/** One hazard: its telegraph until it strikes, then its strike for [strike] seconds. */
private fun DrawScope.drawHazard(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    strike: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (time < hazard.armedAt || time > hazard.strikesAt + strike) return
    val a = world(engine, hazard.ax, hazard.ay, shakeX, shakeY)
    val b = world(engine, hazard.bx, hazard.by, shakeX, shakeY)
    val reach = hazard.radius + abs(b.x - a.x) + abs(b.y - a.y)
    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    if (mid.x < -reach || mid.y < -reach || mid.x > size.width + reach || mid.y > size.height + reach) return
    FxDrawBudget.take(FxPool.DUNGEON, HAZARD_OPS, signature = true)
    val threat = roles.threat
    val hot = kkMix(threat, Kk.Bone, 0.65f)
    if (time >= hazard.strikesAt) {
        val fade = 1f - ((time - hazard.strikesAt) / strike).coerceIn(0f, 1f)
        when (hazard.kind) {
            DungeonHazardKind.VENT -> {
                drawLightPool(a, hazard.radius * 2.4f, threat, 0.45f * fade)
                drawCircle(hot, hazard.radius, a, alpha = 0.85f * fade)
                drawCircle(Kk.Bone, hazard.radius * (1f + 0.35f * (1f - fade)), a, alpha = 0.7f * fade, style = kkStroke(4f))
            }
            DungeonHazardKind.LANCE -> {
                drawLine(threat, a, b, hazard.radius * 2f + 34f, alpha = 0.28f * fade, cap = StrokeCap.Butt)
                drawLine(hot, a, b, hazard.radius * 2f, alpha = 0.85f * fade, cap = StrokeCap.Butt)
                drawLine(Kk.Bone, a, b, hazard.radius * 0.6f, alpha = fade, cap = StrokeCap.Butt)
            }
        }
        return
    }
    val progress = ((time - hazard.armedAt) / (hazard.strikesAt - hazard.armedAt)).coerceIn(0f, 1f)
    // The last quarter blinks: the strike is on its way.
    val blink = if (progress > 0.75f) 0.5f + 0.5f * cos((progress - 0.75f) * 60f) else 1f
    val tint = kkMix(threat, accent, 0.2f)
    when (hazard.kind) {
        DungeonHazardKind.VENT -> {
            drawCircle(tint, hazard.radius, a, alpha = 0.06f + 0.16f * progress)
            drawCircle(tint, hazard.radius * progress, a, alpha = 0.18f + 0.22f * progress)
            drawDashedRing(a, hazard.radius, threat, (0.55f + 0.4f * progress) * blink, time * 0.9f, RIM_DASHES, 2.5f)
            drawCircle(hot, 6f + 6f * progress, a, alpha = 0.6f * blink)
        }
        DungeonHazardKind.LANCE -> {
            // The band's edges close from twice the width to the strike's width; the emitters glow at the walls.
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / length
            val ny = dx / length
            val edge = hazard.radius * (2f - progress)
            drawLine(tint, a, b, hazard.radius * 2f, alpha = 0.06f + 0.18f * progress)
            for (side in 0 until 2) {
                val sign = if (side == 0) 1f else -1f
                drawLine(threat, Offset(a.x + nx * edge * sign, a.y + ny * edge * sign), Offset(b.x + nx * edge * sign, b.y + ny * edge * sign),
                    2f, alpha = (0.35f + 0.5f * progress) * blink)
            }
            drawLine(hot, a, b, 1.5f + 2f * progress, alpha = 0.4f * blink)
            drawCircle(hot, 9f + 5f * progress, a, alpha = 0.8f * blink)
            drawCircle(hot, 9f + 5f * progress, b, alpha = 0.8f * blink)
        }
    }
}

private const val HAZARD_OPS = 8

/** The living guardian's sigil: a turning dashed ring and an inner ring under it, and a pool of its light. */
private fun DrawScope.drawGuardianSigil(
    engine: GameplayRenderModel,
    dungeon: DungeonProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (dungeon.guardianId < 0) return
    val guardian = enemyWithId(engine.enemies, dungeon.guardianId) ?: return
    val at = world(engine, engine.interpolated(guardian.previousX, guardian.x), engine.interpolated(guardian.previousY, guardian.y), shakeX, shakeY)
    FxDrawBudget.take(FxPool.DUNGEON, SIGIL_DASHES + 3, signature = true)
    val color = kkMix(roles.threat, accent, 0.35f)
    val radius = guardian.radius + 34f
    val breath = 0.5f + 0.5f * sin(time * 2.2f)
    drawLightPool(at, radius * 2.6f, color, 0.14f + 0.08f * breath)
    drawDashedRing(at, radius, color, 0.7f, time * SIGIL_TURN, SIGIL_DASHES, 3f)
    drawCircle(color, radius * 0.78f, at, alpha = 0.3f + 0.2f * breath, style = kkStroke(KkStrokeWidth.Icon))
    drawCircle(color, radius * 1.18f, at, alpha = 0.18f, style = kkStroke(KkStrokeWidth.Line2))
}

/** [dashes] arcs around [center] covering half of the ring, turned by [turn] radians. */
internal fun DrawScope.drawDashedRing(center: Offset, radius: Float, color: Color, alpha: Float, turn: Float, dashes: Int, width: Float) {
    val sweep = 180f / dashes
    val start = turn * CanvasMath.RAD_TO_DEG
    for (dash in 0 until dashes) {
        drawArc(color, start + dash * (360f / dashes), sweep, false, Offset(center.x - radius, center.y - radius),
            Size(radius * 2f, radius * 2f), alpha = alpha.coerceIn(0f, 1f), style = kkStroke(width))
    }
}

/** The enemy with [id] that is not dead, or null. */
private fun enemyWithId(enemies: List<EnemyProjection>, id: Int): EnemyProjection? {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == id && !enemy.dead) return enemy
    }
    return null
}
