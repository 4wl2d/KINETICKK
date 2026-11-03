// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.fx.DungeonMomentProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkRays
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.hypot
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * Dungeon moments over the fight (after the enemies and projectiles, under the Core): a seal floods its room with
 * threat and rings out from its centre; a wave's landing flares; the guardian's entrance pulls three rings in onto it
 * under a column of its light; a room's clear bursts light from its centre, sweeps a bright outline out to its walls
 * and ignites its runes one after another round the room; the whole structure's clear adds turning rays. Moments are
 * signature work of FxPool.DUNGEON (few and short); their places, kinds and lives come from the FX projection.
 */

/** Draws every dungeon moment in view. */
internal fun DrawScope.drawDungeonMoments(engine: GameplayRenderModel, visualFx: VisualFxProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val moments = visualFx.dungeonMoments
    if (moments.isEmpty()) return
    for (index in moments.indices) {
        val moment = moments[index]
        val center = world(engine, moment.x, moment.y, shakeX, shakeY)
        val reach = hypot(moment.halfWidth, moment.halfHeight) * 1.4f
        if (center.x < -reach || center.y < -reach || center.x > size.width + reach || center.y > size.height + reach) continue
        val template = engine.content.structures.template(moment.template)
        val accent = structureAccent(template, roles)
        val progress = (1f - moment.life / moment.maxLife).coerceIn(0f, 1f)
        FxDrawBudget.take(FxPool.DUNGEON, MOMENT_OPS, signature = true)
        when (moment.kind) {
            DungeonMomentKind.SEALED -> drawSeal(moment, center, progress, roles.threat)
            DungeonMomentKind.WAVE -> drawWaveLanding(moment, center, progress, roles.threat)
            DungeonMomentKind.GUARDIAN -> drawGuardianEntrance(engine, moment, shakeX, shakeY, progress, kkMix(roles.threat, accent, 0.35f))
            DungeonMomentKind.ROOM_CLEARED, DungeonMomentKind.STRUCTURE_CLEARED ->
                drawClear(engine, moment, center, progress, accent, moment.kind == DungeonMomentKind.STRUCTURE_CLEARED)
        }
    }
}

private const val MOMENT_OPS = 12

/** Stroke widths stay fixed (a cached stroke per width): a wave's room edge and a clear's sweeping outline. */
private const val WAVE_EDGE = 8f
private const val CLEAR_EDGE = 5f

/** The seal: the room flooded in threat, fading fast, and a ring out from its centre to its corners. */
private fun DrawScope.drawSeal(moment: DungeonMomentProjection, center: Offset, progress: Float, threat: Color) {
    val flood = (1f - progress) * (1f - progress)
    drawRect(threat, Offset(center.x - moment.halfWidth, center.y - moment.halfHeight), Size(moment.halfWidth * 2f, moment.halfHeight * 2f),
        alpha = 0.16f * flood)
    val ring = hypot(moment.halfWidth, moment.halfHeight) * KkEase.Out.transform(progress)
    drawCircle(threat, ring, center, alpha = 0.6f * (1f - progress), style = kkStroke(6f))
    drawCircle(kkMix(threat, Kk.Bone, 0.5f), ring * 0.82f, center, alpha = 0.3f * (1f - progress), style = kkStroke(2f))
}

/** A wave landing: a short threat flare over the room. */
private fun DrawScope.drawWaveLanding(moment: DungeonMomentProjection, center: Offset, progress: Float, threat: Color) {
    val fade = 1f - progress
    drawLightPool(center, max(moment.halfWidth, moment.halfHeight) * 1.1f, threat, 0.22f * fade)
    drawRect(threat, Offset(center.x - moment.halfWidth, center.y - moment.halfHeight), Size(moment.halfWidth * 2f, moment.halfHeight * 2f),
        alpha = 0.5f * fade, style = kkStroke(WAVE_EDGE))
}

/** The guardian's entrance: three rings pulled in onto it in turn, under a column of its light. */
private fun DrawScope.drawGuardianEntrance(
    engine: GameplayRenderModel,
    moment: DungeonMomentProjection,
    shakeX: Float,
    shakeY: Float,
    progress: Float,
    color: Color,
) {
    val at = world(engine, moment.focusX, moment.focusY, shakeX, shakeY)
    val fade = 1f - progress
    drawLightPool(at, 420f, color, 0.38f * fade)
    drawLightPool(at, 140f, Kk.Bone, 0.5f * fade * fade)
    for (ring in 0 until 3) {
        val local = ((progress - ring * 0.14f) / 0.6f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val radius = 380f * (1f - KkEase.Out.transform(local)) + 40f
        drawCircle(color, radius, at, alpha = 0.7f * (1f - local), style = kkStroke(5f - ring))
    }
    drawKkRays(at, 360f, color, 8f, 30f, 0.15f, 1f, progress * 40f, alpha = 0.35f * fade)
}

/**
 * A clear: light bursting from the room's centre, a bright outline sweeping out to its walls and its runes igniting
 * one after another round the room; the structure's clear adds turning rays and burns longer.
 */
private fun DrawScope.drawClear(
    engine: GameplayRenderModel,
    moment: DungeonMomentProjection,
    center: Offset,
    progress: Float,
    accent: Color,
    finale: Boolean,
) {
    val light = kkMix(Kk.Bone, accent, 0.3f)
    val fade = 1f - progress
    val burst = KkEase.Out.transform((progress / 0.45f).coerceIn(0f, 1f))
    val span = max(moment.halfWidth, moment.halfHeight)
    drawLightPool(center, span * (0.6f + 1.2f * burst), light, (if (finale) 0.45f else 0.3f) * fade)
    drawRect(light, Offset(center.x - moment.halfWidth * burst, center.y - moment.halfHeight * burst),
        Size(moment.halfWidth * 2f * burst, moment.halfHeight * 2f * burst), alpha = 0.7f * fade, style = kkStroke(CLEAR_EDGE))
    if (finale) drawKkRays(center, span * 1.5f, light, 6f, 30f, 0.1f, 1f, progress * 50f, alpha = 0.28f * fade, scale = 0.6f + 0.5f * burst)
    drawIgnitingRunes(engine, moment, center, progress, light)
}

/** The cleared room's runes lighting round the room as the moment runs (a rune flares, then holds lit). */
private fun DrawScope.drawIgnitingRunes(
    engine: GameplayRenderModel,
    moment: DungeonMomentProjection,
    center: Offset,
    progress: Float,
    light: Color,
) {
    val wall = engine.content.structures.template(moment.template).wallHalfThickness
    // The moment's room as a rect centred on the origin (already turned): its runes, placed at the moment's centre.
    val room = MomentRoom.of(moment.halfWidth, moment.halfHeight)
    var total = 0
    forEachRune(room, wall) { _, _, _ -> total++ }
    if (total == 0) return
    val lit = progress * 1.6f * total
    forEachRune(room, wall) { x, y, index ->
        val order = runeOrder(x, y)
        val age = lit - order * total
        if (age < 0f) return@forEachRune
        val flare = (1f - age / (total * 0.3f)).coerceIn(0f, 1f)
        drawCircle(light, RUNE_SIZE * (0.6f + 1.4f * flare), Offset(center.x + x, center.y + y), alpha = (0.55f + 0.45f * flare) * (1f - progress * 0.5f))
    }
}

/** A rune's place in the ignition's sweep round the room (0..1, clockwise from the top). */
private fun runeOrder(x: Float, y: Float): Float {
    val angle = kotlin.math.atan2(x, -y)
    return ((angle / CanvasMath.TAU) + 1f) % 1f
}

/** A reusable axis-aligned room centred on the origin for the ignition's rune walk (draw-thread scratch). */
private object MomentRoom {
    private var room = kinetickk.ball.content.api.StructureRoom(-200f, -200f, 200f, 200f)

    fun of(halfWidth: Float, halfHeight: Float): kinetickk.ball.content.api.StructureRoom {
        val held = room
        if (held.maxX == halfWidth && held.maxY == halfHeight) return held
        return kinetickk.ball.content.api.StructureRoom(-halfWidth, -halfHeight, halfWidth, halfHeight).also { room = it }
    }
}
