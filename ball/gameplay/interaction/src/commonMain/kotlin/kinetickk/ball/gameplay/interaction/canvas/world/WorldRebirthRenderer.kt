// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.*
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
 * Volatile Remains (R4) and the fire traps of Imps, the Pyre Herald and the Antecedent: each pending burst shows its
 * blast ring with warning teeth, a threat disc that fills toward the ring as the detonation nears, a quickening pulse
 * on the rim and a threat cross on an ink heart (a fire trap never reads as a Harvest ember). Fuses run on the hostile
 * clock, so the telegraph reads the render time less the Archive's hostile lag.
 */
internal fun DrawScope.drawVolatileBursts(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val bursts = engine.volatileBursts
    if (bursts.isEmpty()) return
    val now = engine.renderElapsed - engine.dimension.hostileLag
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
        val tooth = threat.copy(alpha = 0.55f + 0.35f * pulse)
        for (teeth in 0 until TRAP_TEETH) {
            val dx = TrapTeeth[teeth * 2]
            val dy = TrapTeeth[teeth * 2 + 1]
            drawLine(tooth, Offset(center.x + dx * burst.radius, center.y + dy * burst.radius),
                Offset(center.x + dx * (burst.radius - TRAP_TOOTH), center.y + dy * (burst.radius - TRAP_TOOTH)), 2.5f)
        }
        val heart = 5f + 3f * progress
        drawCircle(Kk.Ink, heart + 2f, center)
        drawLine(threat, Offset(center.x - heart, center.y - heart), Offset(center.x + heart, center.y + heart), 2.5f)
        drawLine(threat, Offset(center.x - heart, center.y + heart), Offset(center.x + heart, center.y - heart), 2.5f)
    }
}

/** A fire trap's warning teeth: how many point inward from its rim and how far. */
private const val TRAP_TEETH = 8
private const val TRAP_TOOTH = 9f
private val TrapTeeth = FloatArray(TRAP_TEETH * 2) { index ->
    val angle = (index / 2) * 2f * kotlin.math.PI.toFloat() / TRAP_TEETH
    if (index % 2 == 0) kotlin.math.cos(angle) else sin(angle)
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
