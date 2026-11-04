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

/** Scale of a Totem chest's plates (66 units wide), shared by the promise plates of a dungeon's final room. */
internal const val TOTEM_CHEST_SCALE: Float = 2.2f

/** Seconds a materialising chest grows into place (the simulation's materialise delay). */
private const val CHEST_ENTRANCE_SECONDS: Float = 0.6f

/**
 * The waiting Totem chests: the stacked plates at [TOTEM_CHEST_SCALE] inside a slowly turning dashed ring of the contact
 * radius, with a pip per charge left. With a Key held a chest is openable (`you` accent, a filled key block and, once
 * armed, a soft pulse); without one it is locked (bone accent, hollow plates and a lock). A new chest grows in over its
 * entrance; a re-arming one dims its ring. Off-screen, [collectTotemChestEdgeMarker] marks the nearest.
 */
internal fun DrawScope.drawTotemChests(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val chests = engine.totemChests
    if (chests.isEmpty()) return
    val openable = engine.keys > 0
    val accent = if (openable) roles.you else Kk.Bone
    val contactRadius = engine.content.structures.totemChest.contactRadius
    for (index in chests.indices) {
        val chest = chests[index]
        val location = world(engine, chest.x, chest.y, shakeX, shakeY)
        // Decided unshaken, as its edge marker.
        if (!isOnScreen(steady(location, shakeX, shakeY), CHEST_VIEW_MARGIN)) continue
        val armed = engine.elapsed >= chest.armedAt
        val grown = ((engine.renderElapsed - chest.bornAt) / CHEST_ENTRANCE_SECONDS).coerceIn(0f, 1f)
        val entrance = grown * grown * (3f - 2f * grown)
        val scale = TOTEM_CHEST_SCALE * (0.6f + 0.4f * entrance)
        val tint = accent.copy(alpha = accent.alpha * entrance)
        rotate(engine.renderElapsed * spin(24f), location) {
            drawCircle(accent.copy(alpha = 0.45f * entrance * (if (armed) 1f else 0.4f)), contactRadius, location, style = WorldStrokes.dashedThin)
        }
        if (openable && armed) {
            val pulse = 0.5f + 0.5f * sin(engine.renderElapsed * 2.5f)
            drawCircle(accent.copy(alpha = (0.05f + 0.04f * pulse) * entrance), contactRadius, location)
        }
        drawTotemPlates(location, scale, tint, keyBlock = openable, keyIcon = openable)
        if (!openable) drawKkIcon(KkIcon.SYSTEM_LOCKED, location, 16f * scale, Kk.Bone.copy(alpha = 0.8f * entrance))
        drawTotemChargePips(location, scale, chest.charges, tint)
    }
}

/**
 * Adds the nearest off-screen Totem chest to [batch] (the batch is small, so one marker stands for them all), openable
 * with a Key ([EdgeMarkerIcon.TOTEM]) or locked ([EdgeMarkerIcon.TOTEM_LOCKED]). Off screen is decided in the unshaken
 * view, as [drawTotemChests] decides whether to draw one (both in world units; [batch] receives device pixels).
 */
internal fun DrawScope.collectTotemChestEdgeMarker(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val chests = engine.totemChests
    var nearest = -1
    var nearestSquared = Float.POSITIVE_INFINITY
    for (index in chests.indices) {
        val chest = chests[index]
        if (isOnScreen(world(engine, chest.x, chest.y, 0f, 0f), CHEST_VIEW_MARGIN)) continue
        val dx = chest.x - engine.coreX
        val dy = chest.y - engine.coreY
        val squared = dx * dx + dy * dy
        if (squared < nearestSquared) {
            nearestSquared = squared
            nearest = index
        }
    }
    if (nearest < 0) return
    val chest = chests[nearest]
    val location = world(engine, chest.x, chest.y, 0f, 0f)
    val scale = engine.worldScale
    val icon = if (engine.keys > 0) EdgeMarkerIcon.TOTEM else EdgeMarkerIcon.TOTEM_LOCKED
    batch.add(location.x * scale, location.y * scale, sqrt(nearestSquared), icon)
}

/**
 * [charges] sheared pips centred under plates drawn by [drawTotemPlates] at [scale] (world units, not dp): each
 * `4 x 2` scaled with a `2` gap.
 */
internal fun DrawScope.drawTotemChargePips(center: Offset, scale: Float, charges: Int, color: androidx.compose.ui.graphics.Color) {
    if (charges <= 0) return
    val width = 4f * scale
    val height = 2f * scale
    val gap = 2f * scale
    val total = charges * width + (charges - 1) * gap
    val top = center.y + totemPlatesHalfHeight(scale) + 5f * scale
    var left = center.x - total * 0.5f
    withKkShear(top + height * 0.5f) {
        for (pip in 0 until charges) {
            drawRect(color, Offset(left, top), Size(width, height))
            left += width + gap
        }
    }
}

/** Half the height of the plate stack [drawTotemPlates] draws at [scale]. */
private fun totemPlatesHalfHeight(scale: Float): Float {
    var total = 0f
    for (height in TotemPlateHeights) total += height * scale
    return (total + 2.2f * scale * (TotemPlateHeights.size - 1)) * 0.5f
}

/** The view margin (world units) inside which a chest draws instead of its edge marker. */
private const val CHEST_VIEW_MARGIN: Float = 120f

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
                drawRect(accent, Offset(center.x - width * 0.5f, plateTop), Size(width, height), style = kkStroke(KkStrokeWidth.Line2))
            }
        }
        if (middle && keyBlock && keyIcon) drawKkIcon(KkIcon.SYSTEM_KEY, Offset(center.x, plateTop + height * 0.5f), 16f * scale, Kk.Ink)
        top += height + gap
    }
}

private val TotemPlateHeights = floatArrayOf(10f, 13f, 18f, 13f, 10f)

/** The stacked-plates mark used by edge markers (a Totem chest, a resonant circuit). */
internal fun DrawScope.drawTotemIcon(center: Offset, size: Float, color: androidx.compose.ui.graphics.Color) {
    val unit = size / 24f
    drawRect(color, Offset(center.x - 3f * unit, center.y - 9f * unit), Size(6f * unit, 4f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 4f * unit, center.y - 3f * unit), Size(8f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 3f * unit, center.y + 4f * unit), Size(6f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
}
