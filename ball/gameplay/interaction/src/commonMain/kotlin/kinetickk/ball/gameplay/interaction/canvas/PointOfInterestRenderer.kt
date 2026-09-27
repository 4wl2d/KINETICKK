// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.nucleus.render.EnemyType
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

/** Half extent of an offer's mark (anomaly diamond with its corner brackets). */
private const val OFFER_MARK_HALF = 46f

/** Half extent of the active vault mark (anomaly diamond) and of a circuit beacon stack. */
private const val VAULT_MARK_HALF = 30f
private const val BEACON_MARK_HALF = 62f

/** Distance from the collapsing-orbit center to its timer label (just outside the progress arc). */
internal const val ORBIT_LABEL_OFFSET = 212f

/** HUD regions and the edge-marker table for the current frame (draw-thread confined). */
internal object WorldOverlayScratch {
    val keepOut = WorldHudKeepOut()
    val planner = EdgeMarkerPlanner()
}

/** Updates the HUD keep-out for this frame: trial panel while a trial runs, boss bar while a boss lives. */
internal fun DrawScope.worldHudKeepOut(engine: GameplayRenderModel, textMeasurer: TextMeasurer): WorldHudKeepOut {
    var trial = false
    val points = engine.pointsOfInterest
    for (index in points.indices) if (points[index].active) trial = true
    var boss = false
    val enemies = engine.enemies
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && (enemy.type == EnemyType.ELITE || enemy.type == EnemyType.ARCHITECT)) boss = true
    }
    return WorldOverlayScratch.keepOut.update(size.width, size.height, density, textMeasurer.scale, trial, boss)
}

/**
 * Points of interest as world markers: the sealed anomaly is a gravitic diamond with threat
 * corner brackets, the collapsing orbit a bone ring with a you-color progress arc, the resonant
 * circuit stacked sheared plates with a you-color key block. Each carries only a short timer; the
 * trial's name and rules live in the HUD's trial panel. A mark that would be cut by the screen
 * edge is replaced by its edge marker ([drawPointOfInterestEdgeMarkers], drawn over the world).
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

/** Edge markers for points of interest whose mark is not fully on screen: a small mark and the distance. */
internal fun DrawScope.drawPointOfInterestEdgeMarkers(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    for (index in engine.pointsOfInterest.indices) {
        val point = engine.pointsOfInterest[index]
        // An active circuit leads to its next beacon; every other point to its center.
        val targetX: Float
        val targetY: Float
        if (point.active && point.kind == PointOfInterestKind.RESONANT_CIRCUIT) {
            val beacon = point.nextBeacon % 3
            targetX = point.x + beaconDx(beacon)
            targetY = point.y + beaconDy(beacon)
        } else {
            targetX = point.x
            targetY = point.y
        }
        val target = world(engine, targetX, targetY, shakeX, shakeY)
        if (markFullyVisible(target, markHalf(point))) continue
        val dx = targetX - engine.coreX
        val dy = targetY - engine.coreY
        drawEdgeMarker(target, sqrt(dx * dx + dy * dy), point.kind.edgeIcon(), textMeasurer)
    }
}

private fun markHalf(point: PointOfInterestProjection): Float = when {
    !point.active -> OFFER_MARK_HALF
    point.kind == PointOfInterestKind.SEALED_ANOMALY -> VAULT_MARK_HALF
    point.kind == PointOfInterestKind.RESONANT_CIRCUIT -> BEACON_MARK_HALF
    else -> 0f // the collapsing orbit's ring reaches well past its center
}

/** Whether a mark of half extent [half] around [center] lies fully on screen. */
internal fun DrawScope.markFullyVisible(center: Offset, half: Float): Boolean =
    center.x >= half && center.y >= half && center.x <= size.width - half && center.y <= size.height - half

private fun PointOfInterestKind.edgeIcon(): EdgeMarkerIcon = when (this) {
    PointOfInterestKind.RESONANT_CIRCUIT -> EdgeMarkerIcon.RESONANT_CIRCUIT
    PointOfInterestKind.SEALED_ANOMALY -> EdgeMarkerIcon.SEALED_ANOMALY
    PointOfInterestKind.COLLAPSING_ORBIT -> EdgeMarkerIcon.COLLAPSING_ORBIT
}

/**
 * An offer the Core can still fly into: the kind's mark inside a dashed entry ring and its timer.
 * The mark is drawn only while it is fully on screen (its edge marker takes over otherwise).
 */
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
    if (!markFullyVisible(center, OFFER_MARK_HALF)) return
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
    drawPointTimer(point, center, OFFER_RADIUS + 8f, textMeasurer)
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
            // Beacon order 1 is the center; the circuit visits 2, 3 and returns to 1 (nextBeacon 1..3).
            val targetIndex = point.nextBeacon % 3
            for (index in 0 until 3) {
                val from = Offset(center.x + beaconDx(index), center.y + beaconDy(index))
                val next = (index + 1) % 3
                val to = Offset(center.x + beaconDx(next), center.y + beaconDy(next))
                drawLine(Kk.Bone.copy(alpha = 0.16f), from, to, 1.5f, pathEffect = WorldStrokes.dashEffect)
            }
            for (index in 0 until 3) {
                val beacon = Offset(center.x + beaconDx(index), center.y + beaconDy(index))
                if (!isOnScreen(beacon, 80f)) continue
                val target = index == targetIndex
                val visited = !target && visitOrder(index) < point.nextBeacon
                if (target) {
                    rotate(engine.elapsed * 30f, beacon) {
                        drawCircle(roles.you.copy(alpha = 0.55f), 55f, beacon, style = WorldStrokes.dashedMedium)
                    }
                }
                drawBeacon(beacon, index, target, visited, roles)
                if (target && markFullyVisible(beacon, BEACON_MARK_HALF)) drawPointTimer(point, beacon, BEACON_MARK_HALF + 16f, textMeasurer)
            }
        }
        PointOfInterestKind.SEALED_ANOMALY -> {
            if (isOnScreen(center, 300f)) {
                drawCircle(Kk.AGravitic.copy(alpha = 0.025f), 290f, center)
                drawCircle(Kk.AGravitic.copy(alpha = 0.3f), 290f, center, style = WorldStrokes.dashedHair)
            }
            if (markFullyVisible(center, VAULT_MARK_HALF)) {
                val pulse = (sin(engine.elapsed * 3.1f) + 1f) * 0.5f
                drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, 52f, Kk.AGravitic, alpha = 0.7f + pulse * 0.3f, strokeWidth = 1.8f)
                drawPointTimer(point, center, 40f, textMeasurer)
            }
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
            drawOrbitTimer(engine, point, center, textMeasurer)
        }
    }
}

/**
 * The collapsing-orbit timer, above the ring or below it, kept on screen horizontally and clear of
 * the HUD (top row, trial panel, bottom clusters). The trial panel already shows the clock, so a
 * label that fits nowhere is skipped.
 */
private fun DrawScope.drawOrbitTimer(engine: GameplayRenderModel, point: PointOfInterestProjection, center: Offset, textMeasurer: TextMeasurer) {
    val layout = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), textMeasurer.typography.condStyle(24f, tabular = true, color = Kk.Bone))
    val half = layout.size.width * 0.5f
    val edge = d(12f)
    val x = if (size.width > half * 2f + edge * 2f) center.x.coerceIn(half + edge, size.width - half - edge) else size.width * 0.5f
    val keepOut = worldHudKeepOut(engine, textMeasurer)
    val boxBottom = layout.size.height - layout.firstBaseline
    val baseline = orbitTimerBaseline(center.y, layout.firstBaseline, size.width, size.height, density) { top, base ->
        !keepOut.intersects(x - half, top, x + half, base + boxBottom)
    }
    if (!baseline.isNaN()) drawKkText(layout, x, baseline, Kk.Bone, KkAlign.CENTER, KkVAlign.BASELINE)
}

/** Index scan without boxing the id (ImmutableList<Int>.contains would box large ids). */
private fun PointOfInterestProjection.isDefender(id: Int): Boolean {
    for (index in defenderIds.indices) if (defenderIds[index] == id) return true
    return false
}

/** Beacon offsets from the circuit center (PointOfInterestState.beacon): order 1 center, 2 right, 3 left. */
private fun beaconDx(index: Int): Float = when (index) {
    1 -> 230f
    2 -> -230f
    else -> 0f
}

private fun beaconDy(index: Int): Float = if (index == 0) 0f else 200f

/** The nextBeacon value at which each beacon has been reached: 2 first, then 3, then back to 1. */
private fun visitOrder(index: Int): Int = if (index == 0) 3 else index

/**
 * A circuit beacon: stacked plates (the target's middle plate is the you-color key block) with its
 * place in the circuit shown as 1–3 sheared pips below, never as a numeral.
 */
private fun DrawScope.drawBeacon(center: Offset, index: Int, target: Boolean, visited: Boolean, roles: KkRolePalette) {
    val accent = when {
        target -> roles.you
        visited -> Kk.Bone
        else -> Kk.Mute
    }
    drawTotemPlates(center, 1.4f, accent, keyBlock = target, keyIcon = target)
    val pips = beaconPips(index)
    val pipWidth = 13f
    val pipGap = 5f
    val total = pips * pipWidth + (pips - 1) * pipGap
    drawKkPips(Offset(center.x - total * 0.5f, center.y + BEACON_MARK_HALF - 5f), pips, pips, accent,
        sizeDp = 8f / density, widthDp = pipWidth / density, gapDp = pipGap / density)
}

/** Pips under a beacon: its place in the circuit (1 at the center, 2 right, 3 left). */
internal fun beaconPips(index: Int): Int = index.coerceIn(0, 2) + 1

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

/** A short mono timer [gap] below the mark at [center], or above it when the screen edge is too close. */
private fun DrawScope.drawPointTimer(point: PointOfInterestProjection, center: Offset, gap: Float, textMeasurer: TextMeasurer) {
    val layout = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), textMeasurer.typography.monoStyle(11f, color = Kk.Mute))
    val below = center.y + gap + layout.kkBoxHeight <= size.height - d(8f)
    if (below) {
        drawKkText(layout, center.x, center.y + gap, Kk.Mute, KkAlign.CENTER, KkVAlign.TOP)
    } else {
        drawKkText(layout, center.x, center.y - gap - layout.kkBoxHeight, Kk.Mute, KkAlign.CENTER, KkVAlign.TOP)
    }
}

/** Kinds of off-screen targets; each draws a small mark (no arrow shapes). */
internal enum class EdgeMarkerIcon { TOTEM, SEALED_ANOMALY, COLLAPSING_ORBIT, RESONANT_CIRCUIT }

/** The widest distance an edge marker is laid out for (world units; four digits). */
private const val EDGE_DISTANCE_LAYOUT_VALUE = 8_880L

/**
 * An off-screen target at the screen edge along the direction from the screen center: a small
 * mark and the world distance from the Core (`HUD-Elite.png`), placed by [EdgeMarkerPlanner] so it
 * hugs the edge and stays clear of the HUD. The distance is drawn from cached digit layouts.
 */
internal fun DrawScope.drawEdgeMarker(
    target: Offset,
    distance: Float,
    icon: EdgeMarkerIcon,
    textMeasurer: TextMeasurer,
) {
    val roles = textMeasurer.roles
    val color = when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> roles.you
        EdgeMarkerIcon.SEALED_ANOMALY -> Kk.AGravitic
        EdgeMarkerIcon.COLLAPSING_ORBIT -> Kk.Bone
    }
    // The style carries the color. Layouts that differ only in color share one paragraph, and
    // painting it in another color rebuilds it every frame, so each marker color also gets its
    // own (imperceptibly different) line height and with it its own digit layouts.
    val colorGroup = when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> 0
        EdgeMarkerIcon.SEALED_ANOMALY -> 1
        EdgeMarkerIcon.COLLAPSING_ORBIT -> 2
    }
    val style = textMeasurer.typography.monoStyle(11f, lineHeightEm = 1.35f + colorGroup * 0.001f, color = color)
    val suffix = WorldStrings.distanceSuffix(textMeasurer.language)
    val textWidth = kkTabularNumberWidth(textMeasurer, EDGE_DISTANCE_LAYOUT_VALUE, style, suffix = suffix)
    val keepOut = WorldOverlayScratch.keepOut
    val marker = WorldOverlayScratch.planner.prepare(keepOut, size.width, size.height, density, textWidth).position(target)
    val iconSize = d(EDGE_ICON_DP)
    when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> drawTotemIcon(marker, iconSize, color)
        EdgeMarkerIcon.SEALED_ANOMALY -> drawKkIcon(KkIcon.SYSTEM_ANOMALY, marker, iconSize, color)
        EdgeMarkerIcon.COLLAPSING_ORBIT -> {
            drawCircle(color, iconSize * 0.36f, marker, style = kkStroke(d(1.5f)))
            drawArc(roles.you, -90f, 200f, false, Offset(marker.x - iconSize * 0.48f, marker.y - iconSize * 0.48f),
                Size(iconSize * 0.96f, iconSize * 0.96f), style = kkStroke(d(2f)))
        }
    }
    val onRight = marker.x > size.width * 0.5f
    val textX = if (onRight) marker.x - iconSize * 0.5f - d(EDGE_TEXT_GAP_DP) else marker.x + iconSize * 0.5f + d(EDGE_TEXT_GAP_DP)
    drawKkTabularNumber(textMeasurer, roundedDistance(distance), style, textX, marker.y, color,
        if (onRight) KkAlign.END else KkAlign.START, KkVAlign.CENTER, suffix = suffix)
}

/** Edge-marker distance: world units rounded to tens, at most the laid-out four digits. */
internal fun roundedDistance(distance: Float): Long =
    (((distance / 10f) + 0.5f).toLong() * 10L).coerceIn(0L, 9_990L)

/** World labels stay below the HUD's top row (level badge, timer, chips, trial panel). */
internal fun hudSafeTop(height: Float, density: Float): Float = max(80f * density, height * 0.16f)

/** World labels stay above the HUD's bottom clusters (integrity, speed, loadout, controls). */
internal fun hudSafeBottom(width: Float, height: Float, density: Float): Float =
    if (height > width) height * 0.66f else height - 110f * density

/**
 * Baseline of the collapsing-orbit timer: above the ring when the whole label clears the HUD's top
 * row, otherwise below the ring; either candidate must lie between the HUD bands and be [clear]
 * of other HUD regions (label top, baseline). NaN when neither fits.
 */
internal inline fun orbitTimerBaseline(
    centerY: Float,
    firstBaseline: Float,
    width: Float,
    height: Float,
    density: Float,
    clear: (top: Float, baseline: Float) -> Boolean = { _, _ -> true },
): Float {
    val top = hudSafeTop(height, density)
    val bottom = hudSafeBottom(width, height, density)
    val above = centerY - ORBIT_LABEL_OFFSET
    if (above - firstBaseline >= top && above <= bottom && clear(above - firstBaseline, above)) return above
    val below = centerY + ORBIT_LABEL_OFFSET + firstBaseline
    if (below - firstBaseline >= top && below <= bottom && clear(below - firstBaseline, below)) return below
    return Float.NaN
}
