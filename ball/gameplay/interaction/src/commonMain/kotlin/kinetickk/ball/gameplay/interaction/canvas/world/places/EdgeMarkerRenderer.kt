// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import kinetickk.foundation.design.*
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.*

/** Edge-marker distances: mono 11 px. */
private val EDGE_DISTANCE_SP = worldLabelSp(11f)

/** Kinds of off-screen targets; each draws a small mark (no arrow shapes). */
internal enum class EdgeMarkerIcon {
    TOTEM, SEALED_ANOMALY, COLLAPSING_ORBIT, RESONANT_CIRCUIT, RACE, CHALLENGE, BOUNTY, CACHE, QUIET, SIGIL, PORTAL, SIGNAL,
    STRUCTURE, HALO, TOTEM_LOCKED,
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
        EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.TOTEM_LOCKED, EdgeMarkerIcon.RESONANT_CIRCUIT -> drawTotemIcon(marker, iconSize, color)
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
        EdgeMarkerIcon.STRUCTURE -> {
            drawSiteGlyph(SiteGlyph.STRUCTURE, marker, iconSize, color)
            drawDangerBrackets(marker, iconSize, roles.threat)
        }
        EdgeMarkerIcon.HALO -> drawHaloEdgeGlyph(marker, iconSize, color)
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

/** Four threat corner brackets round a structure entrance's mark: a dungeon is the most dangerous place of a run. */
private fun DrawScope.drawDangerBrackets(marker: Offset, iconSize: Float, threat: Color) {
    val half = iconSize * 0.72f
    val arm = iconSize * 0.28f
    val width = d(1.5f)
    for (corner in 0 until 4) {
        val sx = if (corner % 2 == 0) -1f else 1f
        val sy = if (corner < 2) -1f else 1f
        val tip = Offset(marker.x + sx * half, marker.y + sy * half)
        drawLine(threat, tip, Offset(tip.x - sx * arm, tip.y), width)
        drawLine(threat, tip, Offset(tip.x, tip.y - sy * arm), width)
    }
}

/**
 * An edge marker's color: volt for the player's own course (an openable Totem chest, circuit, race, the next halo ring), threat
 * only for a bounty, the void hue for the sigil, mute for the quiet ambient sites and a locked chest, bone for rules, rifts,
 * event signals and structure entrances (whose mark the threat brackets frame).
 */
internal fun edgeMarkerColor(icon: EdgeMarkerIcon, roles: KkRolePalette): Color = when (icon) {
    EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT, EdgeMarkerIcon.RACE, EdgeMarkerIcon.HALO -> roles.you
    EdgeMarkerIcon.SEALED_ANOMALY -> Kk.AGravitic
    EdgeMarkerIcon.COLLAPSING_ORBIT, EdgeMarkerIcon.CHALLENGE, EdgeMarkerIcon.PORTAL -> Kk.Bone
    EdgeMarkerIcon.BOUNTY -> roles.threat
    EdgeMarkerIcon.CACHE, EdgeMarkerIcon.QUIET, EdgeMarkerIcon.TOTEM_LOCKED -> Kk.Mute
    EdgeMarkerIcon.SIGIL -> sigilColor(roles)
    // A signal and a structure entrance are bone by their lanes' specs.
    EdgeMarkerIcon.SIGNAL, EdgeMarkerIcon.STRUCTURE -> Kk.Bone
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
    EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.RESONANT_CIRCUIT, EdgeMarkerIcon.RACE, EdgeMarkerIcon.HALO -> 0
    EdgeMarkerIcon.SEALED_ANOMALY -> 1
    EdgeMarkerIcon.COLLAPSING_ORBIT, EdgeMarkerIcon.CHALLENGE, EdgeMarkerIcon.PORTAL, EdgeMarkerIcon.SIGNAL,
    EdgeMarkerIcon.STRUCTURE,
    -> 2
    EdgeMarkerIcon.BOUNTY -> 3
    EdgeMarkerIcon.CACHE, EdgeMarkerIcon.QUIET, EdgeMarkerIcon.TOTEM_LOCKED -> 4
    EdgeMarkerIcon.SIGIL -> 5
}

/** Edge-marker distance: world units rounded to tens, at most the laid-out four digits. */
internal fun roundedDistance(distance: Float): Long =
    (((distance / 10f) + 0.5f).toLong() * 10L).coerceIn(0L, 9_990L)
