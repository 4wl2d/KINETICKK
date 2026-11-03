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
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Core: a bone form silhouette ([KkFormGeometry]) with a hard ink rim and a soft glow; you-color
 * in overdrive and heat-tinted while overheated (`HUD.dc.html`). Integrity/heat/shield arcs are
 * the HUD's Core halo. In Baby mode the form wears a diaper ([drawDiaper]).
 */
internal fun DrawScope.drawCore(engine: GameplayRenderModel, center: Offset, roles: KkRolePalette) {
    val overdrive = engine.overdriveTime > 0f
    val fill = when {
        overdrive -> roles.you
        engine.overheated -> kkMix(Kk.Bone, roles.heat, 0.35f)
        else -> Kk.Bone
    }
    val glow = when {
        overdrive -> roles.you
        engine.overheated -> roles.heat
        else -> Kk.Bone
    }
    val glowAlpha = if (overdrive || engine.overheated) 0.26f else 0.12f
    drawCircle(glow.copy(alpha = glowAlpha), 24f, center)
    drawCircle(glow.copy(alpha = glowAlpha * 0.5f), 32f, center)
    if (overdrive) drawCircle(glow.copy(alpha = 0.1f), 44f, center)
    if (engine.braking) {
        val compression = (sin(engine.renderElapsed * 15f) + 1f) * 0.5f
        drawCircle(Kk.Bone.copy(alpha = 0.6f), 30f - compression * 5f, center, style = WorldStrokes.dashedMedium)
    }
    // Phased by Void Step: the Core shows at half strength.
    drawCoreForm(engine, center, if (engine.coreState.phased) fill.copy(alpha = 0.5f) else fill, roles)
    if (engine.easyMode) drawDiaper(engine, center, roles, if (engine.coreState.phased) 0.5f else 1f)
    val ability = engine.characterAbility
    if (ability.charge > 0f && engine.coreShape != CoreShape.RING && engine.coreShape != CoreShape.PRISM) {
        drawArc(roles.you, -90f, 360f * ability.charge.coerceIn(0f, 1f), false,
            Offset(center.x - 25f, center.y - 25f), Size(50f, 50f), style = kkStroke(2f))
    }
    // Parry marks stay inside the HUD's Core halo (radius 38).
    if (ability.parryWindow > 0f) {
        rotate(45f, center) {
            drawRect(Kk.Bone, Offset(center.x - 24f, center.y - 24f), Size(48f, 48f), style = kkStroke(2f))
        }
    }
    if (engine.dashPhaseTime > 0f) drawCircle(roles.you.copy(alpha = 0.8f), 28f + engine.dashPhaseTime * 50f, center, style = kkStroke(2f))
}

private val TesseractVertices = FloatArray(32)

/** A 4D rotating wireframe projected to Canvas and scaled to [radius]; the fixed center dot stays visible. */
internal fun DrawScope.drawTesseractCore(
    center: Offset,
    elapsed: Float,
    color: Color,
    radius: Float = GameplayRenderModel.CORE_RADIUS,
) {
    drawCircle(color.copy(alpha = 0.22f), radius, center, style = kkStroke(1f))
    val scale = radius / GameplayRenderModel.CORE_RADIUS
    val angle = elapsed * 0.65f
    val c = cos(angle)
    val s = sin(angle)
    val c2 = cos(angle * 0.73f)
    val s2 = sin(angle * 0.73f)
    val vertices = TesseractVertices
    for (index in 0 until 16) {
        val x = if (index and 1 == 0) -1f else 1f
        val y = if (index and 2 == 0) -1f else 1f
        val z = if (index and 4 == 0) -1f else 1f
        val w = if (index and 8 == 0) -1f else 1f
        val rotatedX = x * c - w * s
        val rotatedW = x * s + w * c
        val rotatedY = y * c2 - z * s2
        val rotatedZ = y * s2 + z * c2
        val perspective = 24f * scale / (2.8f - rotatedW * 0.55f)
        vertices[index * 2] = center.x + (rotatedX + rotatedZ * 0.35f) * perspective
        vertices[index * 2 + 1] = center.y + (rotatedY + rotatedZ * 0.25f) * perspective
    }
    for (index in 0 until 16) {
        for (axis in 0 until 4) {
            val other = index xor (1 shl axis)
            if (index < other) {
                drawLine(
                    color.copy(alpha = if (axis == 3) 0.5f else 1f),
                    Offset(vertices[index * 2], vertices[index * 2 + 1]),
                    Offset(vertices[other * 2], vertices[other * 2 + 1]),
                    2f,
                )
            }
        }
    }
    drawCircle(Kk.Ink, 6f * scale, center)
    drawCircle(Kk.Bone, 3.5f * scale, center)
}
