// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.FxColor
import kinetickk.ball.gameplay.interaction.fx.FxEase
import kinetickk.ball.gameplay.interaction.fx.FxLayer
import kinetickk.ball.gameplay.interaction.fx.FxPrimitive
import kinetickk.ball.gameplay.nucleus.render.CombatFieldProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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

/** A field grows from 0.6 of its radius over this long (Pull). */
private const val FIELD_SPAWN_SECONDS = 0.15f

/** A field fades over its last quarter second. */
private const val FIELD_FADE_SECONDS = 0.25f

/** The Void Anchor tether shows while its enemy is this close to the Core (world units). */
private const val ANCHOR_TETHER_REACH = 520f

/** The next Dash blinks to the anchor while the pointer is within this angle of it (radians). */
private val ANCHOR_BLINK_ANGLE = 30f * (PI / 180.0).toFloat()

private fun motion(primitive: FxPrimitive, color: FxColor, n: Int, r0: Float, r1: Float, w: Float = 2f, param: Float = 0f, spin: Float = 0f) =
    FxLayer(primitive, 0f, 1f, FxEase.L, color, n, r0, r1, w, spin, param, 0f, 1f, 0)

/** The repeating motions fields draw through the FX painter (progress = the repeat's phase). */
private object FieldMotions {
    val fusionSpiral = motion(FxPrimitive.SPIRAL, FxColor.B, 6, 1f, 0.25f, param = 50f)
    val rotDots = motion(FxPrimitive.DOTS_IN, FxColor.B, 6, 0.95f, 0.2f, param = 2.5f)
    val darkSpiral = motion(FxPrimitive.SPIRAL, FxColor.A, 4, 1f, 0.25f, param = 60f)
    val wellSpiral = motion(FxPrimitive.SPIRAL, FxColor.A, 6, 1f, 0.25f, param = 50f)
}

/** Field colours: the reaction's two elements, else the field's element twice, else the kind's own pair. */
private fun DrawScope.fieldColors(field: CombatFieldProjection, roles: KkRolePalette) {
    val ctx = FxContext
    val reaction = ReactionId.entries.getOrNull(field.reaction)
    when {
        reaction != null -> {
            ctx.a = roles.element(reaction.first.ordinal)
            ctx.b = roles.element(reaction.second.ordinal)
        }
        field.element in 0 until StatusElement.entries.size -> {
            ctx.a = roles.element(field.element)
            ctx.b = ctx.a
        }
        else -> {
            val pair = kindElements(field.kind)
            ctx.a = roles.element(pair / 8)
            ctx.b = roles.element(pair % 8)
        }
    }
}

/** A field kind's own two elements packed as `first · 8 + second` (no pair object per frame). */
private fun kindElements(kind: CombatFieldVisual): Int = when (kind) {
    CombatFieldVisual.FUSION -> THERMAL * 8 + GRAVITIC
    CombatFieldVisual.ROT_WELL -> GRAVITIC * 8 + ENTROPY
    CombatFieldVisual.DARK_STAR -> GRAVITIC * 8 + GRAVITIC
    CombatFieldVisual.ECHO_RING -> SONIC * 8 + SONIC
    CombatFieldVisual.NULL_WELL -> GRAVITIC * 8 + VOID
    CombatFieldVisual.PLASMA -> THERMAL * 8 + ION
    CombatFieldVisual.HUSH -> SONIC * 8 + VOID
    CombatFieldVisual.TIME_BUBBLE -> CRYO * 8 + VOID
    CombatFieldVisual.ATTRACTOR -> ION * 8 + GRAVITIC
    CombatFieldVisual.FROST_LINE -> CRYO * 8 + CRYO
    CombatFieldVisual.STORM_COIL -> ION * 8 + ION
    CombatFieldVisual.GRAVITY_WELL -> GRAVITIC * 8 + GRAVITIC
}

/**
 * Lingering combat fields from the render model (never from cues, so a dropped cue never hides one):
 * a timer rim showing the remaining share, the kind's fill and motion (hud-fx §4.6), four player-colour
 * rim ticks on a skill's field, a spawn growth and a fade over the last quarter second. Fills follow
 * the reaction effects setting; rims never change. Drawn as ground decals under everything that moves.
 */
internal fun DrawScope.drawCombatFields(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val fields = engine.combatFields
    if (fields.isEmpty()) return
    val ctx = FxContext
    val elapsed = engine.renderElapsed
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    ctx.coreX = core.x
    ctx.coreY = core.y
    ctx.coreRadius = engine.coreRadius
    ctx.elapsed = elapsed
    ctx.you = roles.you
    ctx.rank = 1
    ctx.verb = false
    ctx.keystone = false
    ctx.coreInside = false
    ctx.signatureOnly = false
    ctx.secondaryOff = false
    val fillScale = FxFrame.fillScale
    for (index in fields.indices) {
        val field = fields[index]
        val total = max(0.001f, field.expiresAt - field.createdAt)
        val age = (elapsed - field.createdAt).coerceIn(0f, total)
        val left = total - age
        val remaining = (left / total).coerceIn(0f, 1f)
        val fade = min(1f, left / FIELD_FADE_SECONDS)
        if (fade <= 0f) continue
        val center = fieldCenter(engine, field, shakeX, shakeY)
        val grow = if (age < FIELD_SPAWN_SECONDS) 0.6f + 0.4f * KkEase.Pull.transform(age / FIELD_SPAWN_SECONDS) else 1f
        val radius = max(1f, field.radius * grow)
        if (field.kind == CombatFieldVisual.FROST_LINE) {
            drawFrostLine(engine, field, age, left, shakeX, shakeY, roles, fillScale)
            continue
        }
        if (!isOnScreen(center, radius + 24f)) continue
        fieldColors(field, roles)
        val a = ctx.a
        val b = ctx.b
        ctx.x = center.x
        ctx.y = center.y
        ctx.reach = radius
        ctx.alpha = fade
        ctx.age = age
        ctx.seed = index * 977 + field.kind.ordinal
        ctx.theta = 0f
        val inside = engine.coreState.inFieldMask and (1 shl field.kind.ordinal) != 0
        val pulse2 = (sin(elapsed * TAU * 2f) + 1f) * 0.5f
        when (field.kind) {
            CombatFieldVisual.FUSION -> {
                drawFieldTimer(center, radius, remaining, b, 2f, fade, null)
                val ending = left < 0.25f
                if (fieldMotion(4)) {
                    drawCircle(if (ending) Kk.Bone else a, 10f + 4f * pulse2, center, alpha = fade * 0.9f)
                    val turn = elapsed * 40f / RAD_TO_DEG
                    for (spoke in 0 until 6) {
                        val angle = turn + spoke * TAU / 6f
                        drawLine(a, polar(center, 12f, angle), polar(center, 26f, angle), 2f, alpha = fade)
                    }
                }
                if (fieldMotion(6)) {
                    ctx.progress = (age % 0.8f) / 0.8f
                    drawFxLayer(FieldMotions.fusionSpiral, 6)
                }
                if (ending) {
                    val t = 1f - left / 0.25f
                    drawCircle(b, max(1f, radius * (1f - 0.9f * t)), center, alpha = fade, style = kkStroke(4.5f))
                }
                if (left < 0.08f) {
                    val t = 1f - left / 0.08f
                    drawCircle(Kk.Bone, max(1f, radius * (0.1f + 1.1f * t)), center, style = kkStroke(2f))
                }
            }
            CombatFieldVisual.ROT_WELL -> {
                drawCircle(Kk.Ink, radius, center, alpha = 0.30f * fillScale * fade)
                val growths = (field.charge01 * 3f).roundToInt()
                drawFieldTimer(center, radius, remaining, a, if (growths <= 0) 2f else if (growths == 1) 3f else 4.5f, fade, null)
                if (fieldMotion(6)) {
                    ctx.progress = (age % 1f)
                    drawFxLayer(FieldMotions.rotDots, 6)
                }
            }
            CombatFieldVisual.DARK_STAR -> {
                FxDrawBudget.take(FxPool.FIELDS, 1, signature = true)
                drawCircle(a, radius, center, alpha = fade, style = WorldStrokes.dashedMedium)
                if (fieldMotion(5)) {
                    ctx.progress = (age % 1.2f) / 1.2f
                    drawFxLayer(FieldMotions.darkSpiral, 4)
                    drawCircle(Kk.Ink, radius * 0.25f, center, alpha = fade)
                }
            }
            CombatFieldVisual.ECHO_RING -> {
                drawFieldTimer(center, radius, remaining, a, 1.5f, fade, null)
                if (fieldMotion(7)) {
                    drawShearedSquare(center, 8f, a, fade, 2f)
                    val beat = (elapsed % 0.75f) / 0.4f
                    if (beat < 1f) drawCircle(a, radius * (0.6f + 0.4f * beat), center, alpha = 0.6f * (1f - beat) * fade, style = kkStroke(2f))
                    val used = (field.charge01 * ECHO_TICKS).roundToInt()
                    for (tick in 0 until used) {
                        val angle = -PI.toFloat() / 2f + tick * TAU / ECHO_TICKS
                        drawLine(Kk.Bone, polar(center, radius - 4f, angle), polar(center, radius + 4f, angle), 2f, alpha = fade)
                    }
                }
            }
            CombatFieldVisual.NULL_WELL -> {
                val core45 = radius * 0.45f
                drawCircle(Kk.Ink, core45, center, alpha = 0.55f * fillScale * fade)
                val eaten = (field.charge01 * 20f).roundToInt()
                val armed = eaten >= 5
                FxDrawBudget.take(FxPool.FIELDS, 1, signature = true)
                drawCircle(if (armed) Kk.Bone else b, core45, center, alpha = fade, style = kkStroke(3f))
                drawFieldTimer(center, radius, remaining, b, 1.5f, fade, null)
                if (fieldMotion(6 + eaten)) {
                    val turn = -elapsed * 120f
                    for (arc in 0 until 6) {
                        val r = radius * 0.75f
                        drawArc(a, turn + arc * 60f - 15f, 30f, false, Offset(center.x - r, center.y - r), Size(r * 2f, r * 2f), alpha = fade,
                            style = kkStroke(2f))
                    }
                    for (notch in 0 until eaten.coerceAtMost(20)) {
                        val angle = -PI.toFloat() / 2f + notch * 18f / RAD_TO_DEG
                        drawLine(b, polar(center, core45 - 3f, angle), polar(center, core45 + 3f, angle), 1.5f, alpha = fade)
                    }
                }
                if (armed && left < 0.12f) {
                    val t = 1f - left / 0.12f
                    drawCircle(Kk.Bone, max(1f, radius * (0.45f + 0.75f * t)), center, style = kkStroke(2f))
                }
            }
            CombatFieldVisual.PLASMA -> {
                drawCircle(a, radius, center, alpha = 0.10f * fillScale * fade)
                if (inside) drawFieldTimer(center, radius, remaining, a, 2f, fade, null)
                else drawFieldTimer(center, radius, remaining, b, 2f, fade, WorldStrokes.dashedMedium)
                if (fieldMotion(5) && sin(elapsed * TAU * 2f) > 0f) {
                    for (dot in 0 until 5) {
                        val angle = fxHash(ctx.seed, dot) * TAU
                        drawCircle(a, 3f, polar(center, radius * (0.2f + 0.6f * fxHash(ctx.seed, dot + 9)), angle), alpha = fade)
                    }
                }
            }
            CombatFieldVisual.HUSH -> {
                drawCircle(Kk.Ink, radius, center, alpha = 0.18f * fillScale * fade)
                drawFieldTimer(center, radius, remaining, b, 2f, fade, null)
                if (fieldMotion(8)) for (tick in 0 until 8) {
                    val angle = tick * TAU / 8f
                    val point = polar(center, radius, angle)
                    val tx = -sin(angle) * 5f
                    val ty = cos(angle) * 5f
                    drawLine(a, Offset(point.x - tx, point.y - ty), Offset(point.x + tx, point.y + ty), 2f, alpha = 0.5f * fade)
                }
            }
            CombatFieldVisual.TIME_BUBBLE -> {
                drawCircle(a, radius, center, alpha = 0.06f * fillScale * fade)
                drawFieldTimer(center, radius, remaining, a, 2f, fade, null)
                if (fieldMotion(13)) {
                    val mark = if (inside) Kk.Bone else a
                    for (tick in 0 until 12) {
                        val angle = tick * TAU / 12f
                        drawLine(mark, polar(center, radius - 8f, angle), polar(center, radius, angle), 2f, alpha = fade)
                    }
                    val hand = -PI.toFloat() / 2f + TAU * (age / total)
                    drawLine(mark, center, polar(center, radius * 0.8f, hand), 2f, alpha = fade)
                }
            }
            CombatFieldVisual.ATTRACTOR -> {
                FxDrawBudget.take(FxPool.FIELDS, 1, signature = true)
                drawCircle(b, radius, center, alpha = fade, style = WorldStrokes.dashedHair)
                if (fieldMotion(6)) {
                    val turn = elapsed * 90f
                    for (ring in 0..1) {
                        val r = radius * (if (ring == 0) 0.35f else 0.6f)
                        val start = if (ring == 0) turn else -turn
                        for (arc in 0 until 3) {
                            drawArc(if (ring == 0) a else b, start + arc * 120f - 25f, 50f, false, Offset(center.x - r, center.y - r),
                                Size(r * 2f, r * 2f), alpha = fade, style = kkStroke(2f))
                        }
                    }
                }
            }
            CombatFieldVisual.STORM_COIL -> {
                drawFieldTimer(center, radius, remaining, a, 1.5f, fade, WorldStrokes.dashedThin)
                if (fieldMotion(4)) {
                    drawShearedSquare(center, 9f, a, fade, 2f)
                    val tick = (age % 0.5f) / 0.5f
                    if (tick < 0.4f) {
                        val flash = (1f - tick / 0.4f) * fade
                        drawCircle(a, 8f, center, alpha = flash, style = kkStroke(1.5f))
                        drawCircle(a, 12f, center, alpha = flash, style = kkStroke(1.5f))
                        drawCircle(a, 16f, center, alpha = flash, style = kkStroke(1.5f))
                    }
                }
            }
            CombatFieldVisual.GRAVITY_WELL -> {
                drawCircle(Kk.Ink, radius * 0.3f, center, alpha = 0.25f * fillScale * fade)
                drawFieldTimer(center, radius, remaining, a, 2f, fade, null)
                if (fieldMotion(6)) {
                    ctx.progress = (age % 0.8f) / 0.8f
                    drawFxLayer(FieldMotions.wellSpiral, 6)
                }
            }
            CombatFieldVisual.FROST_LINE -> Unit
        }
        if (field.source == CombatFieldSource.SKILL) {
            // A skill's field is the player's zone: four rim ticks in the player's colour.
            FxDrawBudget.take(FxPool.FIELDS, 4, signature = true)
            for (tick in 0 until 4) {
                val angle = (45f + 90f * tick) / RAD_TO_DEG
                drawLine(roles.you, polar(center, radius - 3f, angle), polar(center, radius + 3f, angle), 2f, alpha = fade)
            }
        }
    }
}

private const val ECHO_TICKS = 5

/** Takes [ops] from the field budget for a non-rim layer (rims never go). */
private fun fieldMotion(ops: Int): Boolean = FxDrawBudget.take(FxPool.FIELDS, ops, signature = false)

/** Where a field sits: on the interpolated Core, on its anchor enemy, or at its own point. */
private fun DrawScope.fieldCenter(engine: GameplayRenderModel, field: CombatFieldProjection, shakeX: Float, shakeY: Float): Offset {
    if (field.followsCore) return world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    if (field.kind == CombatFieldVisual.ATTRACTOR && field.anchorId >= 0) {
        val enemies = engine.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.id != field.anchorId) continue
            return world(engine, engine.interpolated(enemy.previousX, enemy.x), engine.interpolated(enemy.previousY, enemy.y), shakeX, shakeY)
        }
    }
    return world(engine, field.x, field.y, shakeX, shakeY)
}

/**
 * The field's timer: a faint full ring (1.5) and the remaining share as an arc ([width]) from the top,
 * or the whole rim [dashed] with the remaining arc solid. Always drawn (rims are signatures).
 */
private fun DrawScope.drawFieldTimer(center: Offset, radius: Float, remaining: Float, color: Color, width: Float, alpha: Float, dashed: Stroke?) {
    FxDrawBudget.take(FxPool.FIELDS, 2, signature = true)
    if (dashed != null) drawCircle(color, radius, center, alpha = alpha * 0.6f, style = dashed)
    else drawCircle(color, radius, center, alpha = alpha * 0.18f, style = kkStroke(1.5f))
    if (remaining > 0f) drawArc(color, -90f, 360f * remaining, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
        alpha = alpha, style = kkStroke(width))
}

/** A −12° sheared square outline of half size [half] at [center]. */
private fun DrawScope.drawShearedSquare(center: Offset, half: Float, color: Color, alpha: Float, width: Float) {
    val lean = half * KkShape.ShearRatio
    val a = Offset(center.x - half + lean, center.y - half)
    val b = Offset(center.x + half + lean, center.y - half)
    val c = Offset(center.x + half - lean, center.y + half)
    val d = Offset(center.x - half - lean, center.y + half)
    drawLine(color, a, b, width, alpha = alpha)
    drawLine(color, b, c, width, alpha = alpha)
    drawLine(color, c, d, width, alpha = alpha)
    drawLine(color, d, a, width, alpha = alpha)
}

/**
 * Frost Line: a faint band between two Cryo lines 24 either side of the segment, thick end caps and
 * tiny hexagons every 32 along it; it grows from the centre over 0.2 s and fades over its last 0.5 s.
 */
private fun DrawScope.drawFrostLine(
    engine: GameplayRenderModel,
    field: CombatFieldProjection,
    age: Float,
    left: Float,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
    fillScale: Float,
) {
    val from = world(engine, field.x, field.y, shakeX, shakeY)
    val to = world(engine, field.x2, field.y2, shakeX, shakeY)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val grow = (age / 0.2f).coerceIn(0f, 1f)
    val fade = (left / 0.5f).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val mid = Offset((from.x + to.x) * 0.5f, (from.y + to.y) * 0.5f)
    if (!isOnScreen(mid, length * 0.5f + 30f)) return
    val half = length * 0.5f * grow
    val ux = dx / length
    val uy = dy / length
    val start = Offset(mid.x - ux * half, mid.y - uy * half)
    val end = Offset(mid.x + ux * half, mid.y + uy * half)
    val nx = -uy * 24f
    val ny = ux * 24f
    val cryo = roles.element(field.element.takeIf { it in 0 until StatusElement.entries.size } ?: CRYO)
    FxDrawBudget.take(FxPool.FIELDS, 5, signature = true)
    drawLine(cryo, start, end, 48f, alpha = 0.08f * fillScale * fade)
    drawLine(cryo, Offset(start.x + nx, start.y + ny), Offset(end.x + nx, end.y + ny), 2f, alpha = fade)
    drawLine(cryo, Offset(start.x - nx, start.y - ny), Offset(end.x - nx, end.y - ny), 2f, alpha = fade)
    drawLine(cryo, Offset(start.x + nx, start.y + ny), Offset(start.x - nx, start.y - ny), 3f, alpha = fade)
    drawLine(cryo, Offset(end.x + nx, end.y + ny), Offset(end.x - nx, end.y - ny), 3f, alpha = fade)
    val hexes = (half * 2f / 32f).toInt()
    if (hexes > 0 && fieldMotion(hexes.coerceAtMost(24))) {
        for (hex in 0 until hexes.coerceAtMost(24)) {
            val t = (hex + 0.5f) * 32f
            val point = Offset(start.x + ux * t, start.y + uy * t)
            for (side in 0 until 6) {
                drawLine(cryo, polar(point, 4f, side * TAU / 6f), polar(point, 4f, (side + 1) * TAU / 6f), 1.5f, alpha = fade)
            }
        }
    }
}

/**
 * Skill zones from the skill bar: the Meteor's landing reticle and falling streak while it is
 * pending, the Void Step rift, and while they run Mass Driver's ring and plates, Dual Infusion's two
 * turning arcs and the Slingshot window's ring.
 */
internal fun DrawScope.drawSkillMarkers(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val slots = engine.skillBar.slots
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val coreRadius = engine.coreRadius
    val elapsed = engine.renderElapsed
    for (index in slots.indices) {
        val slot = slots[index]
        val skill = slot.skillId ?: continue
        when (skill) {
            SkillId.METEOR -> if (slot.pending01 > 0f) {
                val anchor = world(engine, slot.anchorX, slot.anchorY, shakeX, shakeY)
                val thermal = roles.element(THERMAL)
                val landing = engine.content.skills.getOrNull(skill.ordinal)?.rank(slot.rank.coerceAtLeast(1))?.values?.getOrNull(0) ?: 140f
                val flight = 1f - slot.pending01.coerceIn(0f, 1f)
                FxDrawBudget.take(FxPool.FIELDS, 9, signature = true)
                drawCircle(thermal, landing, anchor, alpha = 0.6f, style = kkStroke(2f))
                drawCircle(thermal, max(1f, landing * (1f - 0.75f * flight)), anchor, style = kkStroke(1.5f))
                for (corner in 0 until 4) {
                    val angle = (45f + 90f * corner + 90f * flight) / RAD_TO_DEG
                    val point = polar(anchor, landing, angle)
                    rotate(angle * RAD_TO_DEG + 90f, point) { drawShearedPlate(point, 8f, 5f, thermal) }
                }
                // The meteor falls in from up-left onto the reticle.
                val sky = Offset(anchor.x - 0.45f * 240f, anchor.y - 1.35f * 240f)
                val head = Offset(sky.x + (anchor.x - sky.x) * flight, sky.y + (anchor.y - sky.y) * flight)
                drawLine(thermal, sky, head, 3f)
                drawLine(Kk.Bone, sky, head, 1.5f)
                drawCircle(thermal, 6f, head)
            }
            SkillId.VOID_STEP -> if (slot.pending01 > 0f) {
                val anchor = world(engine, slot.anchorX, slot.anchorY, shakeX, shakeY)
                val void = roles.element(VOID)
                val r = 90f * (0.2f + 0.4f * (1f - slot.pending01.coerceIn(0f, 1f)))
                FxDrawBudget.take(FxPool.FIELDS, 3, signature = true)
                drawCircle(Kk.Ink, r, anchor, alpha = 0.85f)
                drawCircle(void, r, anchor, style = kkStroke(2f))
                drawCircle(void, r * 0.6f, anchor, style = kkStroke(1.5f))
            }
            SkillId.MASS_DRIVER -> if (slot.active01 > 0f) {
                val gravitic = roles.element(GRAVITIC)
                val alpha = slot.active01.coerceIn(0f, 1f)
                val heading = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
                FxDrawBudget.take(FxPool.CORE_STATES, 5, signature = true)
                drawCircle(gravitic, coreRadius + 6f, core, alpha = 0.6f * alpha, style = kkStroke(4.5f))
                for (plate in 0 until 4) {
                    val angle = heading + plate * TAU / 4f
                    val point = polar(core, coreRadius + 14f, angle)
                    rotate(angle * RAD_TO_DEG + 90f, point) { drawShearedPlate(point, 8f, 5f, gravitic.copy(alpha = alpha)) }
                }
            }
            SkillId.DUAL_INFUSION -> if (slot.active01 > 0f) {
                val build = engine.build
                val dominant = build.dominant ?: StatusElement.THERMAL
                val secondary = build.secondary ?: if (dominant == StatusElement.CRYO) StatusElement.THERMAL else StatusElement.CRYO
                val alpha = slot.active01.coerceIn(0f, 1f)
                val r = coreRadius + 5f
                val turn = elapsed * 2f * RAD_TO_DEG
                FxDrawBudget.take(FxPool.CORE_STATES, 2, signature = true)
                drawArc(roles.element(dominant.ordinal), turn - 85f, 170f, false, Offset(core.x - r, core.y - r), Size(r * 2f, r * 2f),
                    alpha = alpha, style = kkStroke(2f))
                drawArc(roles.element(secondary.ordinal), turn + 95f, 170f, false, Offset(core.x - r, core.y - r), Size(r * 2f, r * 2f),
                    alpha = alpha, style = kkStroke(2f))
            }
            SkillId.SLINGSHOT -> if (slot.active01 > 0f) {
                FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = true)
                drawCircle(roles.kinetic, coreRadius + 8f, core, alpha = 0.5f, style = kkStroke(1.5f))
            }
            else -> Unit
        }
    }
}

/** A −12° sheared plate [width] × [height] filled at [center] (screen-aligned; callers rotate it). */
internal fun DrawScope.drawShearedPlate(center: Offset, width: Float, height: Float, color: Color) {
    val path = WorldPaths.shearedPlate(width)
    val scaleY = height / (width * 0.7f)
    withTransform({
        translate(center.x, center.y)
        scale(1f, scaleY, Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

/**
 * Persistent links from the render model: the Void Anchor tether from the Core to each anchored
 * enemy within 520 (dashed Void at 0.35; solid bone with a Void glow and a ring around the enemy
 * while the pointer lies within 30° of it, when the next Dash blinks there).
 */
internal fun DrawScope.drawPersistentLinks(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val enemies = engine.enemies
    var any = false
    for (index in enemies.indices) if (enemies[index].controlBits and EnemyControlBits.VOID_ANCHOR != 0) {
        any = true
        break
    }
    if (!any) return
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    val core = world(engine, coreX, coreY, shakeX, shakeY)
    // The pointer in world units (it is screen dp around the camera).
    val pointerX = engine.renderCameraX + engine.pointerX - engine.screenWidth * 0.5f
    val pointerY = engine.renderCameraY + engine.pointerY - engine.screenHeight * 0.5f
    val aim = atan2(pointerY - coreY, pointerX - coreX)
    val void = roles.element(VOID)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.controlBits and EnemyControlBits.VOID_ANCHOR == 0 || enemy.dead) continue
        val x = engine.interpolated(enemy.previousX, enemy.x)
        val y = engine.interpolated(enemy.previousY, enemy.y)
        val dx = x - coreX
        val dy = y - coreY
        if (dx * dx + dy * dy > ANCHOR_TETHER_REACH * ANCHOR_TETHER_REACH) continue
        val target = world(engine, x, y, shakeX, shakeY)
        var turn = atan2(dy, dx) - aim
        while (turn > PI) turn -= TAU
        while (turn < -PI) turn += TAU
        FxDrawBudget.take(FxPool.LINKS, 3, signature = true)
        if (kotlin.math.abs(turn) <= ANCHOR_BLINK_ANGLE) {
            drawLine(void, core, target, 5f, alpha = 0.3f)
            drawLine(Kk.Bone, core, target, 2f)
            drawCircle(void, enemy.radius + 8f, target, style = kkStroke(2f))
        } else {
            drawLine(void.copy(alpha = 0.35f), core, target, 1.5f, pathEffect = WorldStrokes.dashEffect)
        }
    }
}
