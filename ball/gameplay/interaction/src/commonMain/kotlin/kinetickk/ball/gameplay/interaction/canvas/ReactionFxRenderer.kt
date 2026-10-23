// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.ApexFxSpec
import kinetickk.ball.gameplay.interaction.fx.LinkKind
import kinetickk.ball.gameplay.interaction.fx.LinkLook
import kinetickk.ball.gameplay.interaction.fx.LinkLooks
import kinetickk.ball.gameplay.interaction.fx.LinkTone
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.ball.gameplay.interaction.fx.SkillFxSpecs
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (2.0 * PI).toFloat()
private const val RAD_TO_DEG = 57.29578f
private const val SONIC = 5

/** Live transient looks past which older ones lose their secondary layers. */
private const val CROWDED_BURSTS = 16

/** A look older than this (seconds) is "older" for [CROWDED_BURSTS]. */
private const val OLD_BURST_SECONDS = 0.2f

/** Operations of each Break figure (flash included), by element; see [drawBreakBurst]. */
private val BreakFigureOps = intArrayOf(12, 13, 8, 13, 13, 21, 5)

/** Fills [FxContext]'s Core, run time and role colours for this frame. */
private fun DrawScope.prepareFxContext(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val ctx = FxContext
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    ctx.coreX = core.x
    ctx.coreY = core.y
    ctx.coreRadius = engine.coreRadius
    ctx.elapsed = engine.renderElapsed
    ctx.you = roles.you
    ctx.gold = roles.gold.base
    ctx.goldLight = roles.gold.light
}

/** The level of detail shared by every transient look: the setting and the crowd. */
private fun setDetail(signatureOnly: Boolean, secondaryOff: Boolean) {
    val level = FxFrame.level
    val crowd = FxFrame.crowd
    FxContext.signatureOnly = signatureOnly || level == ReactionEffectsLevel.MINIMAL || crowd > FxFrame.CROWD_SIG_ONLY
    FxContext.secondaryOff = secondaryOff || level == ReactionEffectsLevel.REDUCED || crowd > FxFrame.CROWD_SECONDARY_OFF
}

/**
 * Break and reaction flashes ([kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection]). A
 * Break flashes in its element with the element's own figure; a reaction draws its own look
 * ([ReactionFxSpecs]) in both elements' hues. Merged flashes reach further and burn brighter; echoes
 * and cascades keep their signature only. Ages follow the interpolated time ([fxAge]).
 * Allocation-free.
 */
internal fun DrawScope.drawElementBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.elementBursts
    if (bursts.isEmpty()) return
    prepareFxContext(engine, shakeX, shakeY, roles)
    val ctx = FxContext
    val crowded = bursts.size > CROWDED_BURSTS
    val peak = FxFrame.peakAlpha
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val reach = burst.radius * (1f + 0.12f * (burst.weight - 1))
        val verb = burst.flags and ReactionCueFlags.VERB != 0
        // A burst whose look also plays on the Core stays drawn while the Core is on screen.
        if (!verb && !isOnScreen(center, reach * 1.3f + 24f)) continue
        val age = engine.fxAge(burst.life, burst.maxLife)
        val progress = clamp(age / burst.maxLife, 0f, 1f)
        if (burst.secondary < 0) {
            val element = burst.primary.coerceIn(0, BreakFigureOps.lastIndex)
            // A Break the pool cannot afford keeps its opening flash and ring.
            val full = FxDrawBudget.take(FxPool.TRANSIENT, BreakFigureOps[element], signature = false)
            if (full) {
                drawBreakBurst(element, center, reach, progress, roles.element(burst.primary), burst.weight, peak)
            } else {
                FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
                val out = KkEase.Out.transform(progress)
                drawCircle(roles.element(burst.primary), max(1f, reach * out), center, alpha = (1f - progress) * peak, style = kkStroke(3f))
            }
            continue
        }
        val reaction = burst.reaction.coerceIn(0, ReactionId.entries.size - 1)
        val spec = ReactionFxSpecs[reaction]
        val echo = burst.flags and ReactionCueFlags.ECHO != 0
        val cascade = burst.flags and ReactionCueFlags.CASCADE != 0
        ctx.x = center.x
        ctx.y = center.y
        ctx.reach = reach
        ctx.progress = progress
        ctx.age = age
        ctx.seed = burst.seed
        ctx.theta = ctx.heading(burst.direction, burst.seed)
        ctx.a = roles.element(burst.primary)
        ctx.b = roles.element(burst.secondary)
        ctx.alpha = min(1f, 0.8f + 0.07f * burst.weight) * peak * (if (cascade) 0.7f else 1f)
        ctx.rank = 1
        ctx.verb = verb
        ctx.keystone = burst.flags and ReactionCueFlags.KEYSTONE != 0
        ctx.coreInside = false
        setDetail(signatureOnly = echo || cascade, secondaryOff = crowded && age > OLD_BURST_SECONDS)
        drawFxSpec(spec, FxPool.TRANSIENT)
        if (echo && progress < 0.8f) {
            // A Resonator echo: its signature plus a thin Sonic ring.
            FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
            drawCircle(roles.element(SONIC), max(1f, reach * 0.6f), center, alpha = (1f - progress) * peak, style = kkStroke(1.5f))
        }
    }
}

/**
 * Skill casts and delayed impacts ([SkillFxSpecs]): A is the skill's element colour (the preset's
 * Kinetic for elementless skills), Catalyze bone and the player's colour, Dual Infusion the build's
 * two leading elements. Allocation-free.
 */
internal fun DrawScope.drawSkillBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.skillBursts
    if (bursts.isEmpty()) return
    prepareFxContext(engine, shakeX, shakeY, roles)
    val ctx = FxContext
    val peak = FxFrame.peakAlpha
    for (index in bursts.indices) {
        val burst = bursts[index]
        val spec = SkillFxSpecs.of(burst.skill, burst.phase) ?: continue
        val skill = SkillId.entries[burst.skill]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val age = engine.fxAge(burst.life, burst.maxLife)
        ctx.x = center.x
        ctx.y = center.y
        ctx.reach = burst.radius
        ctx.progress = clamp(age / burst.maxLife, 0f, 1f)
        ctx.age = age
        ctx.seed = burst.seed
        val directed = burst.dirX != 0f || burst.dirY != 0f
        ctx.theta = if (directed) atan2(burst.dirY, burst.dirX) else ctx.heading(Float.NaN, burst.seed)
        applySkillColors(engine, skill, roles)
        ctx.alpha = peak
        ctx.rank = burst.rank
        ctx.verb = false
        ctx.keystone = false
        ctx.coreInside = burst.flags and SkillCueCodes.FLAG_CORE_INSIDE != 0
        setDetail(signatureOnly = false, secondaryOff = false)
        drawFxSpec(spec, FxPool.TRANSIENT)
    }
}

/**
 * Sets [FxContext]'s colours for [skill]'s looks: its element (the preset's Kinetic without one)
 * twice; Catalyze bone and the player's colour; Dual Infusion the build's dominant and secondary
 * elements (Thermal and Cryo before the build has them).
 */
internal fun applySkillColors(engine: GameplayRenderModel, skill: SkillId, roles: KkRolePalette) {
    val ctx = FxContext
    when (skill) {
        SkillId.CATALYZE -> {
            ctx.a = Kk.Bone
            ctx.b = roles.you
        }
        SkillId.DUAL_INFUSION -> {
            val build = engine.build
            val dominant = build.dominant ?: StatusElement.THERMAL
            val secondary = build.secondary ?: if (dominant == StatusElement.CRYO) StatusElement.THERMAL else StatusElement.CRYO
            ctx.a = roles.element(dominant.ordinal)
            ctx.b = roles.element(secondary.ordinal)
        }
        else -> {
            val element = engine.content.skills.getOrNull(skill.ordinal)?.element
            val hue = if (element == null) roles.kinetic else roles.element(element.ordinal)
            ctx.a = hue
            ctx.b = hue
        }
    }
}

/**
 * Apex activations ([ApexFxSpec]): gold rings, spokes and a bone flash, and the character's own
 * silhouette outline growing to three Core radii over the first half (its identity in every preset).
 */
internal fun DrawScope.drawApexBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.apexBursts
    if (bursts.isEmpty()) return
    prepareFxContext(engine, shakeX, shakeY, roles)
    val ctx = FxContext
    val peak = FxFrame.peakAlpha
    val spec = ApexFxSpec.activation
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val age = engine.fxAge(burst.life, burst.maxLife)
        val progress = clamp(age / burst.maxLife, 0f, 1f)
        ctx.x = center.x
        ctx.y = center.y
        ctx.reach = ApexFxSpec.REACH
        ctx.progress = progress
        ctx.age = age
        ctx.seed = burst.seed
        ctx.theta = ctx.heading(Float.NaN, burst.seed)
        ctx.a = roles.gold.base
        ctx.b = roles.gold.light
        ctx.alpha = peak
        ctx.rank = 1
        ctx.verb = false
        ctx.keystone = false
        ctx.coreInside = false
        setDetail(signatureOnly = false, secondaryOff = false)
        drawFxSpec(spec, FxPool.TRANSIENT)
        if (progress < ApexFxSpec.SILHOUETTE_END) {
            FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
            val t = progress / ApexFxSpec.SILHOUETTE_END
            val grow = 1f + 2f * KkEase.Out.transform(t)
            val shape = CoreShape.entries.getOrNull(burst.shape) ?: CoreShape.ORB
            val path = KkFormPaths.of(shape.formIndex(), engine.coreRadius)
            // A 2-unit outline at every scale: the stroke shrinks as the path grows (quarter steps).
            val stroke = kkStroke(((2f / grow) * 4f).toInt().coerceAtLeast(2) / 4f)
            withTransform({
                translate(center.x, center.y)
                scale(grow, grow, Offset.Zero)
            }) {
                drawPath(path, roles.gold.base, alpha = (1f - t) * peak, style = stroke)
            }
        }
    }
}

/**
 * Transient links ([WeaponArcProjection]): the Arc Coil zigzag for weapons and items (style 0), and
 * each reaction, skill, Break and Apex its own look ([LinkLooks]) in its own colours. Over the link
 * budget, or at the minimal reaction effects setting, a styled link is one line in its core colour.
 */
internal fun DrawScope.drawWeaponArcs(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val peak = FxFrame.peakAlpha
    for (index in visualFx.weaponArcs.indices) {
        val arc = visualFx.weaponArcs[index]
        val start = world(engine, arc.fromX, arc.fromY, shakeX, shakeY)
        val end = world(engine, arc.toX, arc.toY, shakeX, shakeY)
        if (arc.style == 0) {
            drawWeaponLink(engine, index, start, end, clamp(arc.life / 0.14f, 0f, 1f), roles)
            continue
        }
        val look = LinkLooks[arc.style]
        val age = engine.fxAge(arc.life, arc.maxLife)
        val progress = clamp(age / max(arc.maxLife, 0.001f), 0f, 1f)
        val alpha = (if (progress < 0.5f) 1f else (1f - progress) * 2f) * peak
        if (alpha <= 0.004f) continue
        linkColors(engine, look, arc.style, roles)
        val first = LinkScratch.first
        val second = LinkScratch.second
        val ops = linkOps(look)
        if (FxFrame.plainLinks || !FxDrawBudget.take(FxPool.LINKS, ops, signature = false)) {
            FxDrawBudget.take(FxPool.LINKS, 1, signature = true)
            drawLine(first, start, end, 2f, alpha = alpha)
            continue
        }
        val seed = (arc.fromX.toRawBits() * 31) xor arc.toY.toRawBits() xor arc.style
        drawStyledLink(look, start, end, progress, alpha, seed, first, second, engine.renderElapsed)
    }
}

/** Arc Coil lightning: a bone zigzag over a soft you-color stroke (`HUD.dc.html`). */
private fun DrawScope.drawWeaponLink(engine: GameplayRenderModel, index: Int, start: Offset, end: Offset, alpha: Float, roles: KkRolePalette) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = max(1f, sqrt(dx * dx + dy * dy))
    val nx = -dy / length
    val ny = dx / length
    var previous = start
    for (segment in 0 until 6) {
        val t = (segment + 1f) / 6f
        val jitter = if (segment == 5) 0f else sin(index * 7f + segment * 4.1f + engine.renderElapsed * 40f) * 8f
        val next = Offset(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
        drawLine(roles.you.copy(alpha = alpha * 0.25f), previous, next, 6f)
        drawLine(Kk.Bone.copy(alpha = alpha), previous, next, 2f)
        previous = next
    }
}

/** Draw-thread scratch for a link's two colours. */
private object LinkScratch {
    var first: Color = Color.White
    var second: Color = Color.White
}

private fun linkColors(engine: GameplayRenderModel, look: LinkLook, style: Int, roles: KkRolePalette) {
    when (look.tone) {
        LinkTone.REACTION -> {
            val reaction = ReactionId.entries.getOrNull(LinkLooks.reaction(style))
            LinkScratch.first = if (reaction == null) Kk.Bone else roles.element(reaction.first.ordinal)
            LinkScratch.second = if (reaction == null) Kk.Bone else roles.element(reaction.second.ordinal)
        }
        LinkTone.SKILL -> {
            val hue = skillLinkHue(engine, LinkLooks.skill(style), roles)
            LinkScratch.first = hue
            LinkScratch.second = hue
        }
        LinkTone.ELEMENT -> {
            val hue = roles.element(LinkLooks.element(style).coerceAtLeast(0))
            LinkScratch.first = hue
            LinkScratch.second = hue
        }
        LinkTone.GOLD -> {
            LinkScratch.first = roles.gold.base
            LinkScratch.second = roles.gold.light
        }
        LinkTone.BONE -> {
            LinkScratch.first = Kk.Bone
            LinkScratch.second = Kk.Bone
        }
    }
}

/** The hue of a skill's links: its element (Arc Hook and Storm Coil Ion, the Resonator Sonic), the preset's Kinetic without one. */
private fun skillLinkHue(engine: GameplayRenderModel, skill: Int, roles: KkRolePalette): Color {
    if (skill !in SkillId.entries.indices) return Kk.Bone
    val element = engine.content.skills.getOrNull(skill)?.element
    return if (element == null) roles.kinetic else roles.element(element.ordinal)
}

/** Draw operations of one styled link. */
internal fun linkOps(look: LinkLook): Int = when (look.kind) {
    LinkKind.WEAPON -> 12
    LinkKind.EMBER -> EMBER_DOTS
    LinkKind.LIGHTNING -> look.segments * (if (look.boneCore) 3 else 2) + (if (look.pits) 3 else 0) + (if (look.bars) 2 else 0) +
        (if (look.endHex) 6 else 0)
    LinkKind.FROST -> 4 + (if (look.endShard) 1 else 0) + (if (look.endRings) 2 else 0)
    LinkKind.BEAM -> 9
    LinkKind.TAUT -> 2 + (if (look.endRing > 0f) 1 else 0)
    LinkKind.SOUND -> SOUND_ARCS
}

private const val EMBER_DOTS = 7
private const val SOUND_ARCS = 6

private fun DrawScope.drawStyledLink(
    look: LinkLook,
    start: Offset,
    end: Offset,
    progress: Float,
    alpha: Float,
    seed: Int,
    first: Color,
    second: Color,
    elapsed: Float,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = max(1f, sqrt(dx * dx + dy * dy))
    val ux = dx / length
    val uy = dy / length
    val nx = -uy
    val ny = ux
    when (look.kind) {
        LinkKind.WEAPON -> Unit
        LinkKind.EMBER -> {
            // Dots hop along an arc bulging to one side; the head leads, the tail trails in B.
            val bulge = length * 0.25f * (if (fxHash(seed, 1) < 0.5f) -1f else 1f)
            val travel = KkEase.Out.transform(progress)
            for (dot in 0 until EMBER_DOTS) {
                val t = (travel - dot * 0.06f).coerceIn(0f, 1f)
                val lift = 4f * t * (1f - t) * bulge
                val point = Offset(start.x + dx * t + nx * lift, start.y + dy * t + ny * lift)
                val head = dot == 0
                val radius = if (head) 5f else 3.5f
                if (look.inkDots) {
                    drawCircle(if (head) first else Kk.Ink, radius, point, alpha = alpha)
                    if (!head) drawCircle(second, radius, point, alpha = alpha, style = kkStroke(1.5f))
                } else {
                    val color = when {
                        head -> Kk.Bone
                        dot == EMBER_DOTS - 1 -> second
                        else -> first
                    }
                    drawCircle(color, radius, point, alpha = alpha)
                }
            }
        }
        LinkKind.LIGHTNING -> {
            val segments = look.segments
            var previous = start
            for (segment in 0 until segments) {
                val t = (segment + 1f) / segments
                val jitter = if (segment == segments - 1) 0f else
                    look.jitter * (2f * fxHash(seed, segment + 2) - 1f + 0.35f * sin(elapsed * 40f + segment * 4.1f))
                val next = Offset(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
                drawLine(second, previous, next, 6f, alpha = alpha * (if (look.pits) 0.25f else 0.3f))
                drawLine(first, previous, next, 2f, alpha = alpha)
                if (look.boneCore) drawLine(Kk.Bone, previous, next, 1f, alpha = alpha)
                previous = next
            }
            if (look.pits) for (pit in 1..3) {
                val t = pit / 4f
                drawCircle(second, 2.5f, Offset(start.x + dx * t, start.y + dy * t), alpha = alpha)
            }
            if (look.bars) for (bar in 1..2) {
                val t = bar / 3f
                val mid = Offset(start.x + dx * t, start.y + dy * t)
                drawLine(second, Offset(mid.x - nx * 5f, mid.y - ny * 5f), Offset(mid.x + nx * 5f, mid.y + ny * 5f), 2f, alpha = alpha)
            }
            if (look.endHex) for (side in 0 until 6) {
                drawLine(first, polar(end, 5f, side * TAU / 6f), polar(end, 5f, (side + 1) * TAU / 6f), 1.5f, alpha = alpha)
            }
        }
        LinkKind.FROST -> {
            // A frost trail brightening toward its end.
            for (segment in 0 until 4) {
                val from = segment / 4f
                val to = (segment + 1) / 4f
                drawLine(first, Offset(start.x + dx * from, start.y + dy * from), Offset(start.x + dx * to, start.y + dy * to), 2.5f,
                    alpha = alpha * (0.2f + 0.7f * to))
            }
            if (look.endShard) {
                withTransform({
                    translate(end.x, end.y)
                    rotate(atan2(uy, ux) * RAD_TO_DEG, Offset.Zero)
                    scale(14f, 14f, Offset.Zero)
                }) {
                    drawPath(WorldUnitShapes.shard, first, alpha = alpha)
                }
            }
            if (look.endRings) {
                drawCircle(second, 4f, end, alpha = alpha, style = kkStroke(1.5f))
                drawCircle(second, 7f, end, alpha = alpha, style = kkStroke(1.5f))
            }
        }
        LinkKind.BEAM -> {
            drawLine(Kk.Ink, start, end, 5f, alpha = alpha)
            drawLine(second, Offset(start.x + nx * 3.5f, start.y + ny * 3.5f), Offset(end.x + nx * 3.5f, end.y + ny * 3.5f), 1.5f, alpha = alpha)
            drawLine(second, Offset(start.x - nx * 3.5f, start.y - ny * 3.5f), Offset(end.x - nx * 3.5f, end.y - ny * 3.5f), 1.5f, alpha = alpha)
            for (spark in 0 until 3) {
                val t = (progress * 1.5f + spark / 3f) % 1f
                drawCircle(first, 2f, Offset(start.x + dx * t, start.y + dy * t), alpha = alpha)
            }
            drawCircle(Kk.Ink, 14f, end, alpha = alpha * 0.85f)
            drawCircle(second, 14f, end, alpha = alpha, style = kkStroke(2f))
            drawCircle(second, 8.4f, end, alpha = alpha, style = kkStroke(1.5f))
        }
        LinkKind.TAUT -> {
            val bone = look.tone == LinkTone.BONE
            val width = when {
                bone -> 1.5f
                look.tone == LinkTone.GOLD -> 2f
                else -> 3f
            }
            drawLine(first, start, end, width, alpha = if (bone) alpha * 0.8f else alpha)
            if (!bone) drawLine(Kk.Bone, start, end, 1f, alpha = alpha)
            if (look.endRing > 0f) drawCircle(first, look.endRing, end, alpha = alpha, style = kkStroke(if (bone) 1.5f else 2f))
        }
        LinkKind.SOUND -> {
            val facing = atan2(uy, ux) * RAD_TO_DEG
            for (arc in 0 until SOUND_ARCS) {
                val t = (arc + 1f) / (SOUND_ARCS + 1f)
                val point = Offset(start.x + dx * t, start.y + dy * t)
                drawArc(first, facing - 35f, 70f, false, Offset(point.x - 6f, point.y - 6f), Size(12f, 12f), alpha = alpha,
                    style = kkStroke(1.5f))
            }
        }
    }
}
