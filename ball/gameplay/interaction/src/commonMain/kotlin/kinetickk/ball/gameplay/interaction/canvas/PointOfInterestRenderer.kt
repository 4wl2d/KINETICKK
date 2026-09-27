// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.design.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Radius inside which a passing Core accepts an offered point (PointOfInterestSystem). */
private const val OFFER_RADIUS = 65f

/**
 * Points of interest as world markers: the sealed anomaly is a gravitic diamond with threat
 * corner brackets, the collapsing orbit a bone ring with a you-color progress arc, the resonant
 * circuit stacked sheared plates with a you-color key block. Each carries only a short timer; the
 * trial's name and rules live in the HUD's trial panel. Off-screen points are marked at the
 * screen edge by [drawPointOfInterestEdgeMarkers], drawn over the world.
 */
internal fun DrawScope.drawPointsOfInterest(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    for (index in engine.pointsOfInterest.indices) {
        val point = engine.pointsOfInterest[index]
        val center = world(engine, point.x, point.y, shakeX, shakeY)
        if (point.active) {
            drawActivePoint(engine, point, center, shakeX, shakeY, textMeasurer)
        } else if (isOnScreen(center, OFFER_RADIUS + 20f)) {
            drawOfferedPoint(engine, point, center, textMeasurer)
        }
    }
}

/** Edge markers for points of interest whose center is off-screen: a small mark and the distance. */
internal fun DrawScope.drawPointOfInterestEdgeMarkers(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    for (index in engine.pointsOfInterest.indices) {
        val point = engine.pointsOfInterest[index]
        val center = world(engine, point.x, point.y, shakeX, shakeY)
        if (isOnScreen(center, 30f)) continue
        val dx = point.x - engine.coreX
        val dy = point.y - engine.coreY
        drawEdgeMarker(center, sqrt(dx * dx + dy * dy), point.kind.edgeIcon(), textMeasurer)
    }
}

private fun PointOfInterestKind.edgeIcon(): EdgeMarkerIcon = when (this) {
    PointOfInterestKind.RESONANT_CIRCUIT -> EdgeMarkerIcon.RESONANT_CIRCUIT
    PointOfInterestKind.SEALED_ANOMALY -> EdgeMarkerIcon.SEALED_ANOMALY
    PointOfInterestKind.COLLAPSING_ORBIT -> EdgeMarkerIcon.COLLAPSING_ORBIT
}

/** An offer the Core can still fly into: the kind's mark inside a dashed entry ring and its timer. */
private fun DrawScope.drawOfferedPoint(
    engine: GameplayRenderModel,
    point: PointOfInterestProjection,
    center: Offset,
    textMeasurer: TextMeasurer,
) {
    val roles = textMeasurer.roles
    val pulse = (sin(engine.elapsed * 3.1f) + 1f) * 0.5f
    rotate(engine.elapsed * 15f, center) {
        drawCircle(Kk.Bone.copy(alpha = 0.22f + pulse * 0.1f), OFFER_RADIUS, center, style = WorldStrokes.dashedThin)
    }
    when (point.kind) {
        PointOfInterestKind.SEALED_ANOMALY -> {
            drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, 52f, Kk.AGravitic, alpha = 0.75f + pulse * 0.25f, strokeWidth = 1.8f)
            drawCornerBrackets(center, 40f, roles.threat)
        }
        PointOfInterestKind.COLLAPSING_ORBIT -> {
            drawCircle(Kk.Bone.copy(alpha = 0.06f), 34f, center)
            drawCircle(Kk.Bone.copy(alpha = 0.55f), 34f, center, style = kkStroke(2f))
            drawCircle(roles.you.copy(alpha = 0.18f), 40f, center, style = kkStroke(4f))
        }
        PointOfInterestKind.RESONANT_CIRCUIT -> drawTotemPlates(center, 1.2f, roles.you, keyBlock = true, keyIcon = true)
    }
    drawPointTimer(point, center.x, center.y + OFFER_RADIUS + 8f, textMeasurer, KkVAlign.TOP)
}

private fun DrawScope.drawActivePoint(
    engine: GameplayRenderModel,
    point: PointOfInterestProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val roles = textMeasurer.roles
    when (point.kind) {
        PointOfInterestKind.RESONANT_CIRCUIT -> {
            // Beacon 1 is the center; the circuit visits 2, 3 and returns to 1 (nextBeacon 1..3).
            val targetIndex = point.nextBeacon % 3
            for (index in 0 until 3) {
                val from = beaconOffset(center, index)
                val to = beaconOffset(center, (index + 1) % 3)
                drawLine(Kk.Bone.copy(alpha = 0.16f), from, to, 1.5f, pathEffect = WorldStrokes.dashEffect)
            }
            for (index in 0 until 3) {
                val beacon = beaconOffset(center, index)
                if (!isOnScreen(beacon, 80f)) continue
                val target = index == targetIndex
                val visited = !target && visitOrder(index) < point.nextBeacon
                if (target) {
                    rotate(engine.elapsed * 30f, beacon) {
                        drawCircle(roles.you.copy(alpha = 0.55f), 55f, beacon, style = WorldStrokes.dashedMedium)
                    }
                }
                drawBeacon(beacon, index, target, visited, textMeasurer)
                if (target) drawPointTimer(point, beacon.x, beacon.y + 62f, textMeasurer, KkVAlign.TOP)
            }
        }
        PointOfInterestKind.SEALED_ANOMALY -> {
            if (isOnScreen(center, 300f)) {
                drawCircle(Kk.AGravitic.copy(alpha = 0.025f), 290f, center)
                drawCircle(Kk.AGravitic.copy(alpha = 0.3f), 290f, center, style = WorldStrokes.dashedHair)
            }
            val pulse = (sin(engine.elapsed * 3.1f) + 1f) * 0.5f
            drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, 52f, Kk.AGravitic, alpha = 0.7f + pulse * 0.3f, strokeWidth = 1.8f)
            drawPointTimer(point, center.x, center.y + 40f, textMeasurer, KkVAlign.TOP)
            for (index in engine.enemies.indices) {
                val enemy = engine.enemies[index]
                if (enemy.dead || !point.isDefender(enemy.id)) continue
                drawCornerBrackets(world(engine, enemy.x, enemy.y, shakeX, shakeY), enemy.radius + 12f, roles.threat)
            }
        }
        PointOfInterestKind.COLLAPSING_ORBIT -> {
            if (point.warningRemaining > 0f) {
                val cosine = cos(point.volleyAngle)
                val sine = sin(point.volleyAngle)
                val alpha = min(1f, 0.35f + point.warningRemaining)
                for (lane in 0 until 3) {
                    val lateral = (lane - 1) * 60f
                    val offsetX = -sine * lateral
                    val offsetY = cosine * lateral
                    drawLine(
                        roles.threat.copy(alpha = 0.9f * alpha),
                        Offset(center.x + offsetX - cosine * 320f, center.y + offsetY - sine * 320f),
                        Offset(center.x + offsetX + cosine * 320f, center.y + offsetY + sine * 320f),
                        3f,
                        pathEffect = WorldStrokes.dashEffect,
                    )
                }
            }
            if (!isOnScreen(center, 210f)) return
            val breathe = (sin(engine.elapsed * 3.9f) + 1f) * 0.5f
            drawCircle(Kk.Bone.copy(alpha = 0.05f), 155f, center, style = kkStroke(70f))
            drawCircle(Kk.Bone.copy(alpha = 0.55f), 190f, center, style = kkStroke(2f))
            drawCircle(Kk.Bone.copy(alpha = 0.22f + breathe * 0.18f), 120f, center, style = WorldStrokes.dashedThin)
            drawCircle(roles.you.copy(alpha = 0.18f), 198f, center, style = WorldStrokes.arc6)
            val progress = point.progress.coerceIn(0f, 1f)
            if (progress > 0f) {
                drawArc(roles.you, -90f, 360f * progress, false, Offset(center.x - 198f, center.y - 198f),
                    Size(396f, 396f), style = WorldStrokes.arc6)
            }
            val layout = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), textMeasurer.typography.condStyle(24f, tabular = true, color = Kk.Bone))
            // Above the ring, but kept on screen when the ring fills a short phone screen.
            drawKkText(layout, center.x, max(center.y - 212f, d(12f) + layout.firstBaseline), Kk.Bone, KkAlign.CENTER, KkVAlign.BASELINE)
        }
    }
}

/** Index scan without boxing the id (ImmutableList<Int>.contains would box large ids). */
private fun PointOfInterestProjection.isDefender(id: Int): Boolean {
    for (index in defenderIds.indices) if (defenderIds[index] == id) return true
    return false
}

/** Beacon positions relative to the circuit center (PointOfInterestState.beacon). */
private fun beaconOffset(center: Offset, index: Int): Offset = when (index) {
    1 -> Offset(center.x + 230f, center.y + 200f)
    2 -> Offset(center.x - 230f, center.y + 200f)
    else -> center
}

/** The nextBeacon value at which each beacon has been reached: 2 first, then 3, then back to 1. */
private fun visitOrder(index: Int): Int = if (index == 0) 3 else index

/** A circuit beacon: stacked plates whose middle block carries the beacon's number. */
private fun DrawScope.drawBeacon(center: Offset, index: Int, target: Boolean, visited: Boolean, textMeasurer: TextMeasurer) {
    val roles = textMeasurer.roles
    val accent = when {
        target -> roles.you
        visited -> Kk.Bone
        else -> Kk.Mute2
    }
    drawTotemPlates(center, 1.4f, accent, keyBlock = target, keyIcon = false)
    val numberColor = if (target) Kk.Ink else accent
    val layout = measureKkText(textMeasurer, kkIntString(index + 1), textMeasurer.typography.wideStyle(15f, tabular = true, color = numberColor))
    drawKkText(layout, center.x, center.y, numberColor, KkAlign.CENTER, KkVAlign.CENTER)
}

/** `.brackets`: two opposite 14 px corner brackets around a square of half size [half]. */
private fun DrawScope.drawCornerBrackets(center: Offset, half: Float, color: Color) {
    val arm = 14f
    val left = center.x - half
    val top = center.y - half
    val right = center.x + half
    val bottom = center.y + half
    drawRect(color, Offset(left, top), Size(arm, 2f))
    drawRect(color, Offset(left, top), Size(2f, arm))
    drawRect(color, Offset(right - arm, bottom - 2f), Size(arm, 2f))
    drawRect(color, Offset(right - 2f, bottom - arm), Size(2f, arm))
}

private fun DrawScope.drawPointTimer(point: PointOfInterestProjection, x: Float, y: Float, textMeasurer: TextMeasurer, valign: KkVAlign) {
    val layout = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), textMeasurer.typography.monoStyle(11f, color = Kk.Mute))
    drawKkText(layout, x, y, Kk.Mute, KkAlign.CENTER, valign)
}

/** Kinds of off-screen targets; each draws a small mark (no arrow shapes). */
internal enum class EdgeMarkerIcon { TOTEM, SEALED_ANOMALY, COLLAPSING_ORBIT, RESONANT_CIRCUIT }

/**
 * An off-screen target at the screen edge along the direction from the screen center: a small
 * mark and the world distance from the Core, kept clear of the HUD corners (`HUD-Elite.png`).
 */
internal fun DrawScope.drawEdgeMarker(
    target: Offset,
    distance: Float,
    icon: EdgeMarkerIcon,
    textMeasurer: TextMeasurer,
) {
    val roles = textMeasurer.roles
    val marker = edgeMarkerPosition(target, size.width, size.height, density)
    val color = when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> roles.you
        EdgeMarkerIcon.SEALED_ANOMALY -> Kk.AGravitic
        EdgeMarkerIcon.COLLAPSING_ORBIT -> Kk.Bone
    }
    val iconSize = d(18f)
    when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> drawTotemIcon(marker, iconSize, color)
        EdgeMarkerIcon.SEALED_ANOMALY -> drawKkIcon(KkIcon.SYSTEM_ANOMALY, marker, iconSize, color)
        EdgeMarkerIcon.COLLAPSING_ORBIT -> {
            drawCircle(color, iconSize * 0.36f, marker, style = kkStroke(d(1.5f)))
            drawArc(roles.you, -90f, 200f, false, Offset(marker.x - iconSize * 0.48f, marker.y - iconSize * 0.48f),
                Size(iconSize * 0.96f, iconSize * 0.96f), style = kkStroke(d(2f)))
        }
    }
    // The style carries the color: a layout repainted in another color is rebuilt.
    val layout = measureKkText(textMeasurer, WorldStrings.distance(distance, textMeasurer.language),
        textMeasurer.typography.monoStyle(11f, color = color), uppercase = true)
    val onRight = marker.x > size.width * 0.5f
    val textX = if (onRight) marker.x - iconSize * 0.5f - d(8f) else marker.x + iconSize * 0.5f + d(8f)
    drawKkText(layout, textX, marker.y, color, if (onRight) KkAlign.END else KkAlign.START, KkVAlign.CENTER)
}

/**
 * Where an off-screen [target] is marked: the ray from the screen center to the target meets a
 * rectangle inset from the edges, then slides along that edge out of the HUD corners (top row,
 * bottom clusters). Pure geometry for tests.
 */
internal fun edgeMarkerPosition(target: Offset, width: Float, height: Float, density: Float): Offset {
    val portrait = height > width
    val left = 24f * density
    val right = width - 24f * density
    val top = max(80f * density, height * 0.16f)
    val bottom = if (portrait) height * 0.66f else height - 110f * density
    val centerX = width * 0.5f
    val centerY = height * 0.5f
    val dx = target.x - centerX
    val dy = target.y - centerY
    val tx = when {
        dx > 0f -> (right - centerX) / dx
        dx < 0f -> (left - centerX) / dx
        else -> Float.POSITIVE_INFINITY
    }
    val ty = when {
        dy > 0f -> (bottom - centerY) / dy
        dy < 0f -> (top - centerY) / dy
        else -> Float.POSITIVE_INFINITY
    }
    val t = min(tx, ty).coerceAtLeast(0f)
    val x = centerX + dx * t
    val y = centerY + dy * t
    return if (tx <= ty) {
        Offset(x.coerceIn(left, right), y.coerceIn(max(top, height * 0.2f), min(bottom, if (portrait) height * 0.66f else height * 0.74f)))
    } else {
        Offset(x.coerceIn(max(left, width * 0.26f), min(right, width * 0.74f)), y.coerceIn(top, bottom))
    }
}
