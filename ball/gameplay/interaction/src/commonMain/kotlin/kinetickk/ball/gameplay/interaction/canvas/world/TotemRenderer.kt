// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

/**
 * The weapon totem: stacked sheared ink plates with you-color edge lines around a you-color key
 * block (`Totem.dc.html`) inside a slowly turning dashed ring. Off-screen, [collectTotemEdgeMarker]
 * marks it at the screen edge with the distance.
 */
internal fun DrawScope.drawTotem(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val roles = textMeasurer.roles
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    if (!isOnScreen(steady(location, shakeX, shakeY), 60f)) return // decided unshaken, as its edge marker
    val pulse = (sin(totem.pulse) + 1f) * 0.5f
    rotate(engine.renderElapsed * spin(24f), location) {
        drawCircle(roles.you.copy(alpha = 0.45f), 45f, location, style = WorldStrokes.dashedThin)
    }
    drawCircle(roles.you.copy(alpha = 0.05f + pulse * 0.04f), 45f, location)
    drawTotemPlates(location, 1f, roles.you, keyBlock = true, keyIcon = true)
}

/**
 * Adds the off-screen totem to [batch]; [drawEdgeMarkers] draws it with the frame's other markers.
 * Whether it is off screen, and where its marker points, is decided in the unshaken view, as
 * [drawTotem] decides whether to draw it (both in world units; [batch] receives device pixels).
 */
internal fun DrawScope.collectTotemEdgeMarker(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val totem = engine.totem ?: return
    val location = world(engine, totem.x, totem.y, 0f, 0f)
    if (isOnScreen(location, 60f)) return
    val dx = totem.x - engine.coreX
    val dy = totem.y - engine.coreY
    val scale = engine.worldScale
    batch.add(location.x * scale, location.y * scale, sqrt(dx * dx + dy * dy), EdgeMarkerIcon.TOTEM)
}

/**
 * Five stacked plates (60/80/110/80/60 on the board) around a key block, scaled to the world:
 * [scale] 1 is 30 units wide. [keyBlock] fills the middle plate in [accent] ([keyIcon] adds the
 * key); otherwise the middle plate is outlined.
 */
internal fun DrawScope.drawTotemPlates(
    center: Offset,
    scale: Float,
    accent: androidx.compose.ui.graphics.Color,
    keyBlock: Boolean,
    keyIcon: Boolean,
) {
    val width = 30f * scale
    val gap = 2.2f * scale
    val heights = TotemPlateHeights
    var total = 0f
    for (height in heights) total += height * scale
    total += gap * (heights.size - 1)
    var top = center.y - total * 0.5f
    for (index in heights.indices) {
        val height = heights[index] * scale
        val middle = index == heights.size / 2
        val plateTop = top
        withKkShear(plateTop + height * 0.5f) {
            val face = if (middle && keyBlock) accent else Kk.Ink3
            drawRect(face, Offset(center.x - width * 0.5f, plateTop), Size(width, height))
            if (!middle) {
                val edgeY = if (index < heights.size / 2) plateTop + height - 1f * scale else plateTop
                drawRect(accent, Offset(center.x - width * 0.5f, edgeY), Size(width, max(1f, scale)))
            } else if (!keyBlock) {
                drawRect(accent, Offset(center.x - width * 0.5f, plateTop), Size(width, height), style = kkStroke(1.5f))
            }
        }
        if (middle && keyBlock && keyIcon) drawKkIcon(KkIcon.SYSTEM_KEY, Offset(center.x, plateTop + height * 0.5f), 16f * scale, Kk.Ink)
        top += height + gap
    }
}

private val TotemPlateHeights = floatArrayOf(10f, 13f, 18f, 13f, 10f)

/** The stacked-plates mark used by edge markers (the HUD-Elite totem marker). */
internal fun DrawScope.drawTotemIcon(center: Offset, size: Float, color: androidx.compose.ui.graphics.Color) {
    val unit = size / 24f
    drawRect(color, Offset(center.x - 3f * unit, center.y - 9f * unit), Size(6f * unit, 4f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 4f * unit, center.y - 3f * unit), Size(8f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 3f * unit, center.y + 4f * unit), Size(6f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
}
