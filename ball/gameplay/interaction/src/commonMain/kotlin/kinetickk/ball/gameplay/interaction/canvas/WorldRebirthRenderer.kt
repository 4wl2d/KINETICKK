// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkStroke
import kotlin.math.sin

// World visuals of the Rebirth mutators: Volatile Remains telegraphs (R4) and Armored Vanguard
// plates (R8). Drawn in the world layer; circles and arcs only, so a frame allocates nothing.

/**
 * Volatile Remains (R4): each pending burst shows its blast ring, a threat disc that fills toward the
 * ring as the detonation nears and a quickening pulse on the rim. Continuous animation reads
 * [GameplayRenderModel.renderElapsed].
 */
internal fun DrawScope.drawVolatileBursts(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val bursts = engine.volatileBursts
    if (bursts.isEmpty()) return
    val now = engine.renderElapsed
    val threat = roles.threat
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        if (!isOnScreen(center, burst.radius + 8f)) continue
        val span = (burst.detonatesAt - burst.armedAt).coerceAtLeast(0.01f)
        val progress = ((now - burst.armedAt) / span).coerceIn(0f, 1f)
        val pulse = (sin(now * (14f + 30f * progress)) + 1f) * 0.5f
        drawCircle(threat.copy(alpha = 0.08f + 0.16f * progress), burst.radius * progress, center)
        drawCircle(threat.copy(alpha = 0.45f + 0.4f * pulse), burst.radius, center, style = kkStroke(2f + 1.5f * progress))
        drawCircle(Kk.Bone.copy(alpha = 0.6f), 3f + 3f * progress, center)
    }
}

/**
 * Armored Vanguard (R8): four bone plates around the body, set off by ink seams, distinct from the
 * aspect frames. They vanish when an impact cracks the armor.
 */
internal fun DrawScope.drawArmorPlates(enemy: EnemyProjection, center: Offset) {
    if (!enemy.armored) return
    val radius = enemy.radius * ARMOR_PLATE_RADIUS
    val topLeft = Offset(center.x - radius, center.y - radius)
    val size = Size(radius * 2f, radius * 2f)
    val start = (enemy.id % 4) * 22.5f
    for (plate in 0 until 4) {
        val from = start + plate * 90f + ARMOR_SEAM_DEGREES * 0.5f
        drawArc(Kk.Ink, from - 2f, 90f - ARMOR_SEAM_DEGREES + 4f, false, topLeft, size, style = kkStroke(5.5f))
        drawArc(Kk.Bone, from, 90f - ARMOR_SEAM_DEGREES, false, topLeft, size, style = kkStroke(3.5f))
    }
}

private const val ARMOR_PLATE_RADIUS = 1.32f
private const val ARMOR_SEAM_DEGREES = 26f
