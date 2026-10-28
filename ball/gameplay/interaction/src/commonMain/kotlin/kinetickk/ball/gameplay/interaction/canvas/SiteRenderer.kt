// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Half extent of an offer's mark (anomaly diamond with its corner brackets, a site glyph in its brackets). */
private const val OFFER_MARK_HALF = 46f

/** Half extent of a race gate's mark (the gate's center and its order pips; the posts reach past it). */
internal const val RACE_GATE_MARK_HALF = 60f

/** Half extent of a slalom pole's mark (its plate and flag). */
internal const val SLALOM_POLE_MARK_HALF = 40f

/** Size of a site glyph on an offer (the anomaly diamond's 52 less its corner reach). */
private const val OFFER_GLYPH_SIZE = 48f

/** Half extent of the active vault mark (anomaly diamond). */
private const val VAULT_MARK_HALF = 30f

/** Top of a beacon's place pips, below its plates (from the beacon's center). */
private const val BEACON_PIP_TOP = 57f

/** Height of a beacon's place pips. */
private const val BEACON_PIP_HEIGHT = 8f

/**
 * Half extent of a circuit beacon: its dashed target ring (55 + stroke) and the place pips below
 * (to [BEACON_PIP_TOP] + [BEACON_PIP_HEIGHT] = 65).
 */
private const val BEACON_MARK_HALF = 66f

/** Distance from a beacon's center to its timer label, just below the place pips. */
private const val BEACON_TIMER_GAP = 78f

/**
 * The game's default text size. World labels follow the text-size setting and render at the
 * board's size at this default: a label drawn at S px on the board uses S / 1.25 as its style size.
 */
private const val WORLD_TEXT_REFERENCE_SCALE = 1.25f

/** Style size of a world label that is [board] px on the design boards at the default text size. */
internal fun worldLabelSp(board: Float): Float = board / WORLD_TEXT_REFERENCE_SCALE

/** Point timers: mono 11 px on the boards. */
private val POINT_TIMER_SP = worldLabelSp(11f)

/** Collapsing-orbit label (seconds in the ring): cond 24 px on the Anomaly board. */
private val ORBIT_LABEL_SP = worldLabelSp(24f)

/** Offer names: `.t-label` 15 px on the Anomaly board. */
private val POINT_NAME_SP = worldLabelSp(15f)

/** Space between an offer's name and its timer (the board's 4 px column gap). */
private const val POINT_NAME_GAP_DP = 4f

/** Edge-marker distances: mono 11 px. */
private val EDGE_DISTANCE_SP = worldLabelSp(11f)

/** Distance from the collapsing-orbit center to its label (just outside the progress arc). */
internal const val ORBIT_LABEL_OFFSET = 212f

/** HUD regions, the edge-marker table and the frame's off-screen targets (draw-thread confined). */
internal object WorldOverlayScratch {
    val keepOut = WorldHudKeepOut()
    val planner = EdgeMarkerPlanner()
    val markers = EdgeMarkerBatch()
}

/**
 * Boxes of the point marks, names and timers, the orbit label and the edge markers drawn in the
 * current world frame, for tests (fixed arrays; recording allocates nothing). Draw-thread confined.
 */
internal object WorldDrawProbe {
    private const val CAPACITY = 32
    private val kinds = IntArray(CAPACITY)
    private val values = FloatArray(CAPACITY * 4)
    private var count = 0

    fun begin() {
        count = 0
    }

    fun record(kind: WorldDrawn, left: Float, top: Float, right: Float, bottom: Float) {
        if (count >= CAPACITY) return
        kinds[count] = kind.ordinal
        val base = count * 4
        values[base] = left
        values[base + 1] = top
        values[base + 2] = right
        values[base + 3] = bottom
        count++
    }

    /** The boxes of [kind] drawn since [begin] (tests only). */
    fun rects(kind: WorldDrawn): List<Rect> = (0 until count).filter { kinds[it] == kind.ordinal }.map {
        Rect(values[it * 4], values[it * 4 + 1], values[it * 4 + 2], values[it * 4 + 3])
    }
}

/**
 * What [WorldDrawProbe] records: a point's mark (offer, vault, target beacon, a race's next gate), a
 * point timer, an edge marker, the place pips of the target beacon or the next gate as drawn, an
 * offer's name, the collapsing orbit's seconds-in-ring label.
 */
internal enum class WorldDrawn { POINT_MARK, POINT_TIMER, EDGE_MARKER, BEACON_PIPS, POINT_NAME, ORBIT_LABEL }

/**
 * Updates the HUD keep-out for this frame: trial panel while a trial runs, boss bar while a boss lives;
 * [hudDensity] is the density the HUD is drawn at (the interface scale applied).
 */
internal fun DrawScope.worldHudKeepOut(engine: GameplayRenderModel, textMeasurer: TextMeasurer, hudDensity: Float = density): WorldHudKeepOut {
    // The panel shows whichever engagement runs: a site trial, a dungeon or a dimension objective.
    val trial = engine.trial != null
    val boss = bossBlockShown(engine)
    return WorldOverlayScratch.keepOut.update(size.width, size.height, hudDensity, textMeasurer.scale, trial, boss,
        engine.settings.minimapEnabled)
}

/**
 * Points of interest as world markers: the sealed anomaly is a gravitic diamond with threat
 * corner brackets, the collapsing orbit a bone ring with a you-color progress arc, the resonant
 * circuit stacked sheared plates with a you-color key block. An offer carries its name and timer
 * (`Anomaly.png`); an active trial's name and rules live in the HUD's trial panel, so its mark
 * keeps a short timer (the orbit: its seconds in the ring). A mark that would be cut by the screen
 * edge or sit under the HUD is replaced by its edge marker ([collectSiteEdgeMarkers],
 * drawn over the world): both use [markShown] in the unshaken view ([steady]), so exactly one of
 * them draws and screen shake never trades one for the other.
 */
internal fun DrawScope.drawSites(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    // Drawn in world units; the HUD keep-out is screen space, updated by drawWorld for this frame.
    val keepOut = WorldOverlayScratch.keepOut
    val scale = engine.worldScale
    for (index in engine.sites.indices) {
        val point = engine.sites[index]
        val center = world(engine, point.x, point.y, shakeX, shakeY)
        if (point.active) {
            drawActivePoint(engine, point, center, shakeX, shakeY, textMeasurer, keepOut, scale)
        } else if (isOnScreen(center, point.activationRadius + 20f)) {
            drawOfferedPoint(engine, point, center, shakeX, shakeY, textMeasurer, keepOut, scale)
        }
    }
}

/**
 * Adds to [batch] the edge markers of sites whose mark is not shown ([markShown]); [drawEdgeMarkers]
 * draws them with the frame's other markers. The active engagement's target comes first, then the
 * window offers and the sigil (the batch keeps the first [EdgeMarkerBatch.CAPACITY], so order is
 * priority). The mark shakes with the world, but whether it shows, and where its marker points, is
 * decided in the unshaken view. Called in world units like the marks, so both use one predicate;
 * [batch] receives device pixels.
 */
internal fun DrawScope.collectSiteEdgeMarkers(
    engine: GameplayRenderModel,
    @Suppress("UNUSED_PARAMETER") textMeasurer: TextMeasurer,
    batch: EdgeMarkerBatch,
) {
    for (index in engine.sites.indices) if (engine.sites[index].active) collectSiteEdgeMarker(engine, engine.sites[index], batch)
    for (index in engine.sites.indices) if (!engine.sites[index].active) collectSiteEdgeMarker(engine, engine.sites[index], batch)
}

/** One site's marker: an active course leads to its next mark (beacon, gate, pole), every other site to its center. */
private fun DrawScope.collectSiteEdgeMarker(engine: GameplayRenderModel, point: SiteProjection, batch: EdgeMarkerBatch) {
    var targetX = point.x
    var targetY = point.y
    if (point.active) when (point.kind) {
        SiteKind.RESONANT_CIRCUIT -> {
            val beacon = (point.step + 1) % 3
            targetX = point.x + beaconDx(beacon)
            targetY = point.y + beaconDy(beacon)
        }
        SiteKind.GATE_RACE, SiteKind.SLALOM_TRIAL -> if (point.step in point.course.indices) {
            targetX = point.x + point.course[point.step].dx
            targetY = point.y + point.course[point.step].dy
        }
        else -> Unit
    }
    val target = world(engine, targetX, targetY, 0f, 0f)
    if (markShown(target, markHalf(point), WorldOverlayScratch.keepOut, engine.worldScale)) return
    val dx = targetX - engine.coreX
    val dy = targetY - engine.coreY
    batch.add(target.x * engine.worldScale, target.y * engine.worldScale, sqrt(dx * dx + dy * dy), point.kind.edgeIcon())
}

private fun markHalf(point: SiteProjection): Float = when {
    !point.active -> OFFER_MARK_HALF
    point.kind == SiteKind.SEALED_ANOMALY -> VAULT_MARK_HALF
    point.kind == SiteKind.RESONANT_CIRCUIT -> BEACON_MARK_HALF
    point.kind == SiteKind.GATE_RACE -> RACE_GATE_MARK_HALF
    point.kind == SiteKind.SLALOM_TRIAL -> SLALOM_POLE_MARK_HALF
    else -> 0f // rings, arenas and duels reach well past their center
}

/**
 * Sites on the minimap, pinned to its rim when beyond its range: an anomaly a volt square (filled while it runs), a
 * race two volt posts (and its next gate while it runs), a challenge a bone diamond, a bounty a square in threat
 * outline, a rift a square in its layer's hue, the sigil a void ring around its dot; a cache and the quiet signal only
 * within their reveal distance, easter eggs never.
 */
internal fun DrawScope.drawMinimapSites(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val sites = engine.sites
    for (index in sites.indices) drawMinimapSite(engine, sites[index], centerX, centerY, half, scale, dot, roles)
    val ambient = engine.ambientSites
    for (index in ambient.indices) {
        val point = ambient[index]
        if (revealed(engine, point)) drawMinimapSite(engine, point, centerX, centerY, half, scale, dot, roles)
    }
}

/** Whether an ambient site shows off-screen: within its reveal distance of the Core (0 never). */
internal fun revealed(engine: GameplayRenderModel, point: SiteProjection): Boolean {
    if (point.reveal <= 0f) return false
    val dx = point.x - engine.coreX
    val dy = point.y - engine.coreY
    return dx * dx + dy * dy <= point.reveal * point.reveal
}

private fun DrawScope.drawMinimapSite(
    engine: GameplayRenderModel,
    point: SiteProjection,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val dx = (point.x - engine.renderCoreX) * scale
    val dy = (point.y - engine.renderCoreY) * scale
    when (point.kind) {
        SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT ->
            drawMinimapSquare(pinned(centerX, centerY, half, dx, dy, dot * 2.2f), dot * 2.2f, roles.you, filled = point.active)
        SiteKind.GATE_RACE -> {
            val at = pinned(centerX, centerY, half, dx, dy, dot * 2.2f)
            val post = dot * 1.2f
            val apart = dot * 0.8f
            drawLine(roles.you, Offset(at.x - apart, at.y - post), Offset(at.x - apart, at.y + post), 1f)
            drawLine(roles.you, Offset(at.x + apart, at.y - post), Offset(at.x + apart, at.y + post), 1f)
            if (point.active && point.step in point.course.indices) {
                val gate = point.course[point.step]
                drawCircle(roles.you, dot, pinned(centerX, centerY, half, dx + gate.dx * scale, dy + gate.dy * scale, dot))
            }
        }
        SiteKind.BRAKELESS_TRIAL, SiteKind.RAM_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL, SiteKind.SLALOM_TRIAL -> {
            val at = pinned(centerX, centerY, half, dx, dy, dot * 2.4f)
            val reach = dot * 1.7f
            rotate(45f, at) {
                if (point.active) {
                    drawRect(Kk.Bone, Offset(at.x - reach * 0.5f, at.y - reach * 0.5f), Size(reach, reach))
                } else {
                    drawRect(Kk.Bone, Offset(at.x - reach * 0.5f, at.y - reach * 0.5f), Size(reach, reach), style = kkStroke(1f))
                }
            }
        }
        SiteKind.BOUNTY_ARENA -> {
            val at = pinned(centerX, centerY, half, dx, dy, dot * 2.2f)
            drawMinimapSquare(at, dot * 2.2f, Kk.Bone.copy(alpha = if (point.active) 0.9f else 0.5f), filled = true)
            drawMinimapSquare(at, dot * 2.2f, roles.threat, filled = false)
        }
        SiteKind.ZENITH_RIFT -> drawMinimapSquare(pinned(centerX, centerY, half, dx, dy, dot * 2.2f), dot * 2.2f, roles.shield, filled = false)
        SiteKind.NADIR_RIFT -> drawMinimapSquare(pinned(centerX, centerY, half, dx, dy, dot * 2.2f), dot * 2.2f, roles.heat, filled = false)
        SiteKind.FAR_SIGIL -> {
            val at = pinned(centerX, centerY, half, dx, dy, dot * 2f)
            val color = sigilColor(roles)
            drawCircle(color, dot * 1.6f, at, style = kkStroke(1f))
            drawCircle(color, dot * 0.6f, at)
        }
        SiteKind.DRIFT_CACHE -> drawMinimapSquare(pinned(centerX, centerY, half, dx, dy, dot * 1.4f), dot * 1.4f,
            Kk.Bone.copy(alpha = 0.7f), filled = true)
        SiteKind.QUIET_SIGNAL -> drawCircle(Kk.Mute, dot * 1.6f, pinned(centerX, centerY, half, dx, dy, dot * 2f), style = kkStroke(1f))
        // Easter eggs are found, never shown.
        SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER -> Unit
    }
}

private fun DrawScope.drawMinimapSquare(at: Offset, size: Float, color: Color, filled: Boolean) {
    if (filled) {
        drawRect(color, Offset(at.x - size * 0.5f, at.y - size * 0.5f), Size(size, size))
    } else {
        drawRect(color, Offset(at.x - size * 0.5f, at.y - size * 0.5f), Size(size, size), style = kkStroke(1f))
    }
}

/** Whether a mark of half extent [half] around [center] lies fully on screen. */
internal fun DrawScope.markFullyVisible(center: Offset, half: Float): Boolean =
    center.x >= half && center.y >= half && center.x <= size.width - half && center.y <= size.height - half

/**
 * Whether a point's mark of half extent [half] around [center] is drawn: fully on screen with its
 * center outside the HUD (a vault under the relic row would read as one more relic). Otherwise its
 * edge marker stands in. The collapsing orbit (half 0) only needs its center on screen: its ring
 * reaches far past the HUD. In world units, [scale] maps the layer point onto the screen-space [keepOut].
 */
internal fun DrawScope.markShown(center: Offset, half: Float, keepOut: WorldHudKeepOut, scale: Float = 1f): Boolean =
    markFullyVisible(center, half) &&
        (half <= 0f || !keepOut.intersects(center.x * scale, center.y * scale, center.x * scale, center.y * scale))

/**
 * [center], drawn with the screen shaken by ([shakeX], [shakeY]), where it sits in the unshaken
 * view: marks and edge markers are decided there, so shake cannot trade one for the other.
 */
internal fun steady(center: Offset, shakeX: Float, shakeY: Float): Offset = Offset(center.x - shakeX, center.y - shakeY)

internal fun SiteKind.edgeIcon(): EdgeMarkerIcon = when (this) {
    SiteKind.RESONANT_CIRCUIT -> EdgeMarkerIcon.RESONANT_CIRCUIT
    SiteKind.SEALED_ANOMALY -> EdgeMarkerIcon.SEALED_ANOMALY
    SiteKind.COLLAPSING_ORBIT -> EdgeMarkerIcon.COLLAPSING_ORBIT
    SiteKind.GATE_RACE -> EdgeMarkerIcon.RACE
    SiteKind.BRAKELESS_TRIAL, SiteKind.RAM_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL, SiteKind.SLALOM_TRIAL,
    -> EdgeMarkerIcon.CHALLENGE
    SiteKind.BOUNTY_ARENA -> EdgeMarkerIcon.BOUNTY
    SiteKind.DRIFT_CACHE -> EdgeMarkerIcon.CACHE
    SiteKind.QUIET_SIGNAL -> EdgeMarkerIcon.QUIET
    SiteKind.FAR_SIGIL -> EdgeMarkerIcon.SIGIL
    SiteKind.ZENITH_RIFT, SiteKind.NADIR_RIFT -> EdgeMarkerIcon.PORTAL
    // Easter eggs are never revealed off-screen; their icon is never collected.
    SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER -> EdgeMarkerIcon.CACHE
}

/**
 * An offer the Core can still fly into: the kind's mark inside a dashed entry ring, with its name
 * and timer below. The mark is drawn only while it is fully on screen (its edge marker takes over
 * otherwise).
 */
private fun DrawScope.drawOfferedPoint(
    engine: GameplayRenderModel,
    point: SiteProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
    keepOut: WorldHudKeepOut,
    scale: Float,
) {
    val roles = textMeasurer.roles
    val time = engine.renderElapsed
    val pulse = (sin(time * 3.1f) + 1f) * 0.5f
    rotate(time * 15f, center) {
        // The ring a passing Core must cross to take the offer (65 for every window site).
        drawCircle(Kk.Bone.copy(alpha = 0.22f + pulse * 0.1f), point.activationRadius, center, style = WorldStrokes.dashedThin)
    }
    if (!markShown(steady(center, shakeX, shakeY), OFFER_MARK_HALF, keepOut, scale)) return
    recordMark(center, OFFER_MARK_HALF)
    when (point.kind) {
        SiteKind.SEALED_ANOMALY -> {
            drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, 52f, Kk.AGravitic, alpha = 0.75f + pulse * 0.25f, strokeWidth = 1.8f)
            drawCornerBrackets(center, 40f, roles.threat)
        }
        SiteKind.COLLAPSING_ORBIT -> {
            drawCircle(Kk.Bone.copy(alpha = 0.06f), 34f, center)
            drawCircle(Kk.Bone.copy(alpha = 0.55f), 34f, center, style = kkStroke(2f))
            drawCircle(roles.you.copy(alpha = 0.18f), 40f, center, style = kkStroke(4f))
        }
        SiteKind.RESONANT_CIRCUIT -> drawTotemPlates(center, 1.2f, roles.you, keyBlock = true, keyIcon = true)
        // The race is the player's own course; a challenge is a rule (bone) with the player's corner ticks; a bounty
        // is a target in threat brackets.
        SiteKind.GATE_RACE -> drawSiteGlyph(SiteGlyph.RACE, center, OFFER_GLYPH_SIZE, roles.you)
        SiteKind.BRAKELESS_TRIAL, SiteKind.RAM_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL, SiteKind.SLALOM_TRIAL -> {
            drawSiteGlyph(siteGlyph(point.kind), center, OFFER_GLYPH_SIZE, Kk.Bone, alpha = 0.8f + pulse * 0.2f)
            drawCornerBrackets(center, 40f, roles.you)
        }
        SiteKind.BOUNTY_ARENA -> {
            drawSiteGlyph(SiteGlyph.BOUNTY, center, OFFER_GLYPH_SIZE, Kk.Bone, alpha = 0.8f + pulse * 0.2f)
            drawCornerBrackets(center, 40f, roles.threat)
        }
        SiteKind.FAR_SIGIL -> drawSiteGlyph(SiteGlyph.SIGIL, center, OFFER_GLYPH_SIZE, sigilColor(roles))
        SiteKind.ZENITH_RIFT -> drawPortal(center, WorldLayer.ZENITH, locked = false, glitch = false, time = time, roles = roles)
        SiteKind.NADIR_RIFT -> drawPortal(center, WorldLayer.NADIR, locked = false, glitch = false, time = time, roles = roles)
        // Caches and easter eggs lie in the ambient window, never among the offers.
        SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL -> Unit
    }
    inScreenUnits(scale) {
        drawOfferLabels(point, Offset(center.x * scale, center.y * scale), (point.activationRadius + 8f) * scale, textMeasurer, keepOut)
    }
}

private fun DrawScope.drawActivePoint(
    engine: GameplayRenderModel,
    point: SiteProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
    keepOut: WorldHudKeepOut,
    scale: Float,
) {
    val roles = textMeasurer.roles
    val time = engine.renderElapsed
    when (point.kind) {
        SiteKind.RESONANT_CIRCUIT -> {
            // Beacon order 1 is the center; the circuit visits 2, 3 and returns to 1 (step 0..2).
            val targetIndex = (point.step + 1) % 3
            for (index in 0 until 3) {
                val from = Offset(center.x + beaconDx(index), center.y + beaconDy(index))
                val next = (index + 1) % 3
                val to = Offset(center.x + beaconDx(next), center.y + beaconDy(next))
                drawLine(Kk.Bone.copy(alpha = 0.16f), from, to, 1.5f, pathEffect = WorldStrokes.dashEffect)
            }
            for (index in 0 until 3) {
                val beacon = Offset(center.x + beaconDx(index), center.y + beaconDy(index))
                val target = index == targetIndex
                // The target beacon shares its edge marker's predicate; the others may pass the edge.
                if (if (target) !markShown(steady(beacon, shakeX, shakeY), BEACON_MARK_HALF, keepOut, scale) else !isOnScreen(beacon, 80f)) continue
                val visited = !target && visitOrder(index) <= point.step
                if (target) {
                    rotate(time * 30f, beacon) {
                        drawCircle(roles.you.copy(alpha = 0.55f), 55f, beacon, style = WorldStrokes.dashedMedium)
                    }
                }
                drawBeacon(beacon, index, target, visited, roles)
                if (target) {
                    recordMark(beacon, BEACON_MARK_HALF)
                    inScreenUnits(scale) {
                        drawPointTimer(point, Offset(beacon.x * scale, beacon.y * scale), BEACON_TIMER_GAP * scale, textMeasurer, keepOut)
                    }
                }
            }
        }
        SiteKind.SEALED_ANOMALY -> {
            if (isOnScreen(center, 300f)) {
                drawCircle(Kk.AGravitic.copy(alpha = 0.025f), 290f, center)
                drawCircle(Kk.AGravitic.copy(alpha = 0.3f), 290f, center, style = WorldStrokes.dashedHair)
            }
            if (markShown(steady(center, shakeX, shakeY), VAULT_MARK_HALF, keepOut, scale)) {
                val pulse = (sin(time * 3.1f) + 1f) * 0.5f
                drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, 52f, Kk.AGravitic, alpha = 0.7f + pulse * 0.3f, strokeWidth = 1.8f)
                recordMark(center, VAULT_MARK_HALF)
                inScreenUnits(scale) {
                    drawPointTimer(point, Offset(center.x * scale, center.y * scale), 40f * scale, textMeasurer, keepOut)
                }
            }
            drawFoeBrackets(engine, point, shakeX, shakeY, roles)
        }
        SiteKind.COLLAPSING_ORBIT -> {
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
            val breathe = (sin(time * 3.9f) + 1f) * 0.5f
            drawCircle(Kk.Bone.copy(alpha = 0.05f), 155f, center, style = kkStroke(70f))
            drawCircle(Kk.Bone.copy(alpha = 0.55f), 190f, center, style = kkStroke(2f))
            drawCircle(Kk.Bone.copy(alpha = 0.22f + breathe * 0.18f), 120f, center, style = WorldStrokes.dashedThin)
            drawCircle(roles.you.copy(alpha = 0.18f), 198f, center, style = WorldStrokes.arc6)
            val progress = point.progress.coerceIn(0f, 1f)
            if (progress > 0f) {
                drawArc(roles.you, -90f, 360f * progress, false, Offset(center.x - 198f, center.y - 198f),
                    Size(396f, 396f), style = WorldStrokes.arc6)
            }
            inScreenUnits(scale) {
                drawOrbitLabel(engine, point, Offset(center.x * scale, center.y * scale), textMeasurer, keepOut, ORBIT_LABEL_OFFSET * scale)
            }
        }
        // A summoned sigil dims while its duel runs.
        SiteKind.FAR_SIGIL -> if (markShown(steady(center, shakeX, shakeY), OFFER_MARK_HALF, keepOut, scale)) {
            drawSiteGlyph(SiteGlyph.SIGIL, center, OFFER_GLYPH_SIZE, sigilColor(roles), alpha = 0.35f)
        }
        SiteKind.GATE_RACE -> drawRaceCourse(engine, point, center, shakeX, shakeY, keepOut, scale, roles)
        SiteKind.SLALOM_TRIAL -> drawSlalomCourse(point, center, shakeX, shakeY, keepOut, scale, roles)
        SiteKind.BRAKELESS_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL -> drawChallengeRing(engine, point, center, roles)
        // The Ram rite's marked foes carry threat brackets like the vault's defenders.
        SiteKind.RAM_TRIAL -> {
            drawChallengeRing(engine, point, center, roles)
            drawFoeBrackets(engine, point, shakeX, shakeY, roles)
        }
        // The bounty's arena is drawn by its family's step; caches, eggs and rifts never run a trial.
        SiteKind.BOUNTY_ARENA, SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL,
        SiteKind.ZENITH_RIFT, SiteKind.NADIR_RIFT,
        -> Unit
    }
}

/** The secret sigil's hue: the void element's. */
private fun sigilColor(roles: KkRolePalette): Color = roles.element(StatusElement.VOID.ordinal)

/**
 * The collapsing orbit's seconds in the ring ("5.2S", the trial panel's progress value) above the
 * ring or below it, kept on screen horizontally and clear of the HUD (top row, trial panel, bottom
 * clusters). The trial panel shows the same value, so a label that fits nowhere is skipped. The
 * value changes every frame while orbiting, so it is drawn from cached digit layouts.
 */
private fun DrawScope.drawOrbitLabel(
    engine: GameplayRenderModel,
    point: SiteProjection,
    center: Offset,
    textMeasurer: TextMeasurer,
    keepOut: WorldHudKeepOut,
    offset: Float,
) {
    val language = textMeasurer.language
    val style = textMeasurer.typography.condStyle(ORBIT_LABEL_SP, tabular = true, color = Kk.Bone)
    val tenths = orbitRingTenths(point.progress, engine.content.sites.windows.orbitRequiredSeconds)
    val separator = WorldStrings.decimalSeparator(language)
    val unit = WorldStrings.secondsSuffix(language)
    val wholeWidth = kkTabularNumberWidth(textMeasurer, tenths / 10L, style, suffix = separator)
    val width = wholeWidth + kkTabularNumberWidth(textMeasurer, tenths % 10L, style, suffix = unit)
    val digit = measureKkText(textMeasurer, "0", style)
    val half = width * 0.5f
    val edge = d(12f)
    val x = if (size.width > half * 2f + edge * 2f) center.x.coerceIn(half + edge, size.width - half - edge) else size.width * 0.5f
    val boxBottom = digit.size.height - digit.firstBaseline
    val baseline = orbitTimerBaseline(center.y, digit.firstBaseline, size.width, size.height, density, offset) { top, base ->
        !keepOut.intersects(x - half, top, x + half, base + boxBottom)
    }
    if (baseline.isNaN()) return
    drawKkTabularNumber(textMeasurer, tenths / 10L, style, x - half, baseline, Kk.Bone, KkAlign.START, KkVAlign.BASELINE, suffix = separator)
    drawKkTabularNumber(textMeasurer, tenths % 10L, style, x - half + wholeWidth, baseline, Kk.Bone, KkAlign.START, KkVAlign.BASELINE,
        suffix = unit)
    WorldDrawProbe.record(WorldDrawn.ORBIT_LABEL, x - half, baseline - digit.firstBaseline, x + half, baseline + boxBottom)
}

/**
 * Tenths of a second spent inside the collapsing orbit's ring: [progress] of [requiredSeconds],
 * rounded as the trial panel's progress value ([trialProgressText]).
 */
internal fun orbitRingTenths(progress: Float, requiredSeconds: Float): Long =
    (progress.coerceIn(0f, 1f) * requiredSeconds * 10f).roundToInt().toLong()

/** Threat corner brackets around every living foe the site owns (the vault's defenders, the Ram rite's marked foes). */
private fun DrawScope.drawFoeBrackets(engine: GameplayRenderModel, point: SiteProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.enemies.indices) {
        val enemy = engine.enemies[index]
        if (enemy.dead || !point.isDefender(enemy.id)) continue
        val defender = world(
            engine,
            engine.interpolated(enemy.previousX, enemy.x),
            engine.interpolated(enemy.previousY, enemy.y),
            shakeX,
            shakeY,
        )
        drawCornerBrackets(defender, enemy.radius + 12f, roles.threat)
    }
}

/** Index scan without boxing the id (ImmutableList<Int>.contains would box large ids). */
private fun SiteProjection.isDefender(id: Int): Boolean {
    for (index in foeIds.indices) if (foeIds[index] == id) return true
    return false
}

/** Beacon offsets from the circuit center (SiteState CircuitBeacons): order 1 center, 2 right, 3 left. */
private fun beaconDx(index: Int): Float = when (index) {
    1 -> 230f
    2 -> -230f
    else -> 0f
}

private fun beaconDy(index: Int): Float = if (index == 0) 0f else 200f

/** The circuit step from which each beacon counts as reached: the right one from 1, the left from 2, the center from 3. */
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
    val left = center.x - total * 0.5f
    val top = center.y + BEACON_PIP_TOP
    val drawn = drawKkPips(Offset(left, top), pips, pips, accent,
        sizeDp = BEACON_PIP_HEIGHT / density, widthDp = pipWidth / density, gapDp = pipGap / density)
    if (target) {
        // The pips' sheared outline reaches past the row's box by the shear travel on each side.
        val shear = abs(kkShearOffset(BEACON_PIP_HEIGHT))
        WorldDrawProbe.record(WorldDrawn.BEACON_PIPS, left - shear, top, left + drawn + shear, top + BEACON_PIP_HEIGHT)
    }
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

internal fun DrawScope.recordMark(center: Offset, half: Float) =
    WorldDrawProbe.record(WorldDrawn.POINT_MARK, center.x - half, center.y - half, center.x + half, center.y + half)

/**
 * An offer's name (`.t-label` in the kind's color) stacked above its timer [gap] below the mark at
 * [center], or the pair above the mark when the screen edge or the HUD is in the way, as
 * [drawPointTimer] places a timer: skipped when neither side is clear. The pair moves inward to stay
 * on screen, and a name wider than the screen shrinks to fit.
 */
private fun DrawScope.drawOfferLabels(
    point: SiteProjection,
    center: Offset,
    gap: Float,
    textMeasurer: TextMeasurer,
    keepOut: WorldHudKeepOut,
) {
    val typography = textMeasurer.typography
    val color = edgeMarkerColor(point.kind.edgeIcon(), textMeasurer.roles)
    val nameText = WorldStrings.pointName(point.kind, point.name, textMeasurer.language)
    val edge = d(8f)
    val room = size.width - 2f * edge
    var name = measureKkText(textMeasurer, nameText, typography.labelStyle(POINT_NAME_SP, color = color), uppercase = true)
    if (name.size.width > room) {
        val fitted = POINT_NAME_SP * room / name.size.width * 0.98f
        name = measureKkText(textMeasurer, nameText, typography.labelStyle(fitted, color = color), uppercase = true)
    }
    val timer = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), typography.monoStyle(POINT_TIMER_SP, color = Kk.Mute))
    val nameHeight = name.kkBoxHeight
    val spacing = d(POINT_NAME_GAP_DP)
    val width = max(name.size.width, timer.size.width).toFloat()
    val height = nameHeight + spacing + timer.kkBoxHeight
    val half = width * 0.5f
    val x = if (room > width) center.x.coerceIn(edge + half, size.width - edge - half) else size.width * 0.5f
    val top = pointTimerTop(x, center.y, gap, width, height, size.width, size.height, edge, keepOut)
    if (top.isNaN()) return
    drawKkText(name, x, top, color, KkAlign.CENTER, KkVAlign.TOP)
    drawKkText(timer, x, top + nameHeight + spacing, Kk.Mute, KkAlign.CENTER, KkVAlign.TOP)
    val nameHalf = name.size.width * 0.5f
    WorldDrawProbe.record(WorldDrawn.POINT_NAME, x - nameHalf, top, x + nameHalf, top + nameHeight)
    val timerHalf = timer.size.width * 0.5f
    WorldDrawProbe.record(WorldDrawn.POINT_TIMER, x - timerHalf, top + nameHeight + spacing, x + timerHalf, top + height)
}

/**
 * A short mono timer [gap] below the mark at [center], or above it when the screen edge or the HUD
 * is in the way; skipped when neither side is clear (an active trial's panel shows its clock).
 */
private fun DrawScope.drawPointTimer(
    point: SiteProjection,
    center: Offset,
    gap: Float,
    textMeasurer: TextMeasurer,
    keepOut: WorldHudKeepOut,
) {
    val layout = measureKkText(textMeasurer, WorldStrings.timer(point.remaining), textMeasurer.typography.monoStyle(POINT_TIMER_SP, color = Kk.Mute))
    val width = layout.size.width.toFloat()
    val height = layout.kkBoxHeight
    val top = pointTimerTop(center.x, center.y, gap, width, height, size.width, size.height, d(8f), keepOut)
    if (top.isNaN()) return
    drawKkText(layout, center.x, top, Kk.Mute, KkAlign.CENTER, KkVAlign.TOP)
    WorldDrawProbe.record(WorldDrawn.POINT_TIMER, center.x - width * 0.5f, top, center.x + width * 0.5f, top + height)
}

/**
 * Top of a [boxWidth] × [boxHeight] point timer centered under the mark at ([x], [y]) at [gap], or
 * above the mark at the same gap: the first box that stays [edge] inside the screen and clear of
 * the HUD ([keepOut]). NaN when neither does.
 */
internal fun pointTimerTop(
    x: Float,
    y: Float,
    gap: Float,
    boxWidth: Float,
    boxHeight: Float,
    width: Float,
    height: Float,
    edge: Float,
    keepOut: WorldHudKeepOut,
): Float {
    val left = x - boxWidth * 0.5f
    val right = x + boxWidth * 0.5f
    if (left < 0f || right > width) return Float.NaN
    val below = y + gap
    if (below + boxHeight <= height - edge && !keepOut.intersects(left, below, right, below + boxHeight)) return below
    val above = y - gap - boxHeight
    if (above >= edge && !keepOut.intersects(left, above, right, above + boxHeight)) return above
    return Float.NaN
}

/** Kinds of off-screen targets; each draws a small mark (no arrow shapes). */
internal enum class EdgeMarkerIcon {
    TOTEM, SEALED_ANOMALY, COLLAPSING_ORBIT, RESONANT_CIRCUIT, RACE, CHALLENGE, BOUNTY, CACHE, QUIET, SIGIL, PORTAL, SIGNAL,
    STRUCTURE, HALO,
}

/** The widest distance an edge marker is laid out for (world units; four digits). */
private const val EDGE_DISTANCE_LAYOUT_VALUE = 8_880L

/**
 * The frame's off-screen targets ([batch]) at the screen edge along their directions from the
 * screen center: each a small mark and the world distance from the Core (`HUD-Elite.png`), placed
 * together by [EdgeMarkerPlanner.place] so each hugs the edge, stays clear of the HUD and keeps
 * [EDGE_MARKER_SPACING_DP] from the others. The distances are drawn from cached digit layouts.
 */
internal fun DrawScope.drawEdgeMarkers(batch: EdgeMarkerBatch, textMeasurer: TextMeasurer) {
    if (batch.count == 0) {
        WorldOverlayScratch.planner.forget() // markers that come back later are placed afresh
        return
    }
    val roles = textMeasurer.roles
    val typography = textMeasurer.typography
    val suffix = WorldStrings.distanceSuffix(textMeasurer.language)
    // The marker styles differ only in color and a hair of line height, so one laid-out width
    // (the widest distance) sizes every marker box.
    var textWidth = 0f
    for (index in 0 until batch.count) {
        val style = edgeMarkerStyle(typography, batch.icon[index] ?: EdgeMarkerIcon.TOTEM, roles)
        textWidth = max(textWidth, kkTabularNumberWidth(textMeasurer, EDGE_DISTANCE_LAYOUT_VALUE, style, suffix = suffix))
    }
    WorldOverlayScratch.planner.prepare(WorldOverlayScratch.keepOut, size.width, size.height, density, textWidth).place(batch)
    for (index in 0 until batch.count) {
        drawEdgeMarker(batch.markerX[index], batch.markerY[index], batch.distance[index], batch.icon[index] ?: EdgeMarkerIcon.TOTEM,
            textMeasurer, suffix)
    }
}

/** One edge marker centered at ([x], [y]): the kind's mark, and the distance toward the screen center. */
private fun DrawScope.drawEdgeMarker(x: Float, y: Float, distance: Float, icon: EdgeMarkerIcon, textMeasurer: TextMeasurer, suffix: String) {
    val roles = textMeasurer.roles
    val color = edgeMarkerColor(icon, roles)
    val style = edgeMarkerStyle(textMeasurer.typography, icon, roles)
    val marker = Offset(x, y)
    val iconSize = d(EDGE_ICON_DP)
    when (icon) {
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT -> drawTotemIcon(marker, iconSize, color)
        EdgeMarkerIcon.SEALED_ANOMALY -> drawKkIcon(KkIcon.SYSTEM_ANOMALY, marker, iconSize, color)
        EdgeMarkerIcon.COLLAPSING_ORBIT -> {
            drawCircle(color, iconSize * 0.36f, marker, style = kkStroke(d(1.5f)))
            drawArc(roles.you, -90f, 200f, false, Offset(marker.x - iconSize * 0.48f, marker.y - iconSize * 0.48f),
                Size(iconSize * 0.96f, iconSize * 0.96f), style = kkStroke(d(2f)))
        }
        EdgeMarkerIcon.RACE -> drawSiteGlyph(SiteGlyph.RACE, marker, iconSize, color)
        EdgeMarkerIcon.CHALLENGE -> drawSiteGlyph(SiteGlyph.CHALLENGE, marker, iconSize, color)
        EdgeMarkerIcon.BOUNTY -> drawSiteGlyph(SiteGlyph.BOUNTY, marker, iconSize, color)
        EdgeMarkerIcon.CACHE -> drawSiteGlyph(SiteGlyph.CACHE, marker, iconSize, color)
        EdgeMarkerIcon.QUIET -> drawSiteGlyph(SiteGlyph.QUIET, marker, iconSize, color)
        EdgeMarkerIcon.SIGIL -> drawSiteGlyph(SiteGlyph.SIGIL, marker, iconSize, color)
        EdgeMarkerIcon.PORTAL -> drawSiteGlyph(SiteGlyph.PORTAL, marker, iconSize, color)
        EdgeMarkerIcon.SIGNAL -> drawSiteGlyph(SiteGlyph.SIGNAL, marker, iconSize, color)
        EdgeMarkerIcon.STRUCTURE -> drawSiteGlyph(SiteGlyph.STRUCTURE, marker, iconSize, color)
        // The next dimension halo draws its ring once its lane gives it one.
        EdgeMarkerIcon.HALO -> drawKkIcon(KkIcon.SYSTEM_ANOMALY, marker, iconSize, color)
    }
    val onRight = marker.x > size.width * 0.5f
    val textX = if (onRight) marker.x - iconSize * 0.5f - d(EDGE_TEXT_GAP_DP) else marker.x + iconSize * 0.5f + d(EDGE_TEXT_GAP_DP)
    val drawn = drawKkTabularNumber(textMeasurer, roundedDistance(distance), style, textX, marker.y, color,
        if (onRight) KkAlign.END else KkAlign.START, KkVAlign.CENTER, suffix = suffix)
    val half = d(EDGE_HALF_HEIGHT_DP)
    if (onRight) {
        WorldDrawProbe.record(WorldDrawn.EDGE_MARKER, textX - drawn, marker.y - half, marker.x + iconSize * 0.5f, marker.y + half)
    } else {
        WorldDrawProbe.record(WorldDrawn.EDGE_MARKER, marker.x - iconSize * 0.5f, marker.y - half, textX + drawn, marker.y + half)
    }
}

/**
 * An edge marker's color: volt for the player's own course (totem, circuit, race), threat only for a bounty, the void
 * hue for the sigil, mute for the quiet ambient sites, bone for rules and rifts.
 */
internal fun edgeMarkerColor(icon: EdgeMarkerIcon, roles: KkRolePalette): Color = when (icon) {
    EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT, EdgeMarkerIcon.RACE -> roles.you
    EdgeMarkerIcon.SEALED_ANOMALY -> Kk.AGravitic
    EdgeMarkerIcon.COLLAPSING_ORBIT, EdgeMarkerIcon.CHALLENGE, EdgeMarkerIcon.PORTAL -> Kk.Bone
    EdgeMarkerIcon.BOUNTY -> roles.threat
    EdgeMarkerIcon.CACHE, EdgeMarkerIcon.QUIET -> Kk.Mute
    EdgeMarkerIcon.SIGIL -> sigilColor(roles)
    // Their lanes pick their colors; bone until then.
    EdgeMarkerIcon.SIGNAL, EdgeMarkerIcon.STRUCTURE, EdgeMarkerIcon.HALO -> Kk.Bone
}

/** Line height of the edge-marker digits; the HUD's mono digits keep the builder's 1.35. */
private const val EDGE_LINE_HEIGHT_EM = 1.351f

/**
 * The edge-marker distance style for [icon] (mono, drawn in the marker's color). Layouts whose
 * styles differ only in color share one paragraph, and painting it in another color rebuilds it
 * every frame, so every marker color gets its own imperceptibly different line height, and none of
 * them equals the line height of the HUD's mono digits (trial clock), which are drawn in bone.
 */
internal fun edgeMarkerStyle(typography: InterfaceTypography, icon: EdgeMarkerIcon, roles: KkRolePalette): TextStyle {
    val colorGroup = edgeMarkerColorGroup(icon)
    return typography.monoStyle(EDGE_DISTANCE_SP, lineHeightEm = EDGE_LINE_HEIGHT_EM + colorGroup * 0.001f, color = edgeMarkerColor(icon, roles))
}

/** The line-height group of [icon]'s color in [edgeMarkerStyle]: one group per marker color. */
internal fun edgeMarkerColorGroup(icon: EdgeMarkerIcon): Int = when (icon) {
    EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT, EdgeMarkerIcon.RACE -> 0
    EdgeMarkerIcon.SEALED_ANOMALY -> 1
    EdgeMarkerIcon.COLLAPSING_ORBIT, EdgeMarkerIcon.CHALLENGE, EdgeMarkerIcon.PORTAL, EdgeMarkerIcon.SIGNAL,
    EdgeMarkerIcon.STRUCTURE, EdgeMarkerIcon.HALO,
    -> 2
    EdgeMarkerIcon.BOUNTY -> 3
    EdgeMarkerIcon.CACHE, EdgeMarkerIcon.QUIET -> 4
    EdgeMarkerIcon.SIGIL -> 5
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
    offset: Float = ORBIT_LABEL_OFFSET,
    clear: (top: Float, baseline: Float) -> Boolean = { _, _ -> true },
): Float {
    val top = hudSafeTop(height, density)
    val bottom = hudSafeBottom(width, height, density)
    val above = centerY - offset
    if (above - firstBaseline >= top && above <= bottom && clear(above - firstBaseline, above)) return above
    val below = centerY + offset + firstBaseline
    if (below - firstBaseline >= top && below <= bottom && clear(below - firstBaseline, below)) return below
    return Float.NaN
}
