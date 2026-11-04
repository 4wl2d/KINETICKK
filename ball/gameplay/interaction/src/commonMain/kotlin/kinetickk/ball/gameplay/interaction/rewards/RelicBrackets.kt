// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.gameplay.interaction.canvas.look.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkRelicSlot
import kinetickk.foundation.design.drawKkSynergyBracket
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.kkStroke
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicLink

/** One relic link bracket above the relic row: stacked [level], dashed when it is a [preview]. */
internal class RelicBracket(
    val link: OverlayRelicLink,
    val name: String,
    val level: Int,
    val preview: Boolean,
    val faded: Boolean,
)

/**
 * Brackets for the current links (solid; [faded] when the selected choice breaks them) and the
 * links the selected choice would form (dashed, stacked above). Only named pairings and
 * dissonance take a bracket: harmony reads as its multiplier alone. Links whose slot spans
 * overlap take separate levels.
 */
internal fun relicBrackets(matrix: RewardRelicMatrix, preview: RewardRelicPreview?): List<RelicBracket> {
    val placed = mutableListOf<RelicBracket>()
    fun levelFor(link: OverlayRelicLink, from: Int): Int {
        var level = from
        while (placed.any { it.level == level && it.link.first <= link.last && link.first <= it.link.last }) level++
        return level
    }
    matrix.links.forEachIndexed { index, link ->
        if (!link.bracketed) return@forEachIndexed
        val faded = preview?.removedLinks?.any(link::joinsSame) == true
        placed += RelicBracket(link, link.bracketLabel(matrix.linkNames[index]), levelFor(link, 0), preview = false, faded = faded)
    }
    val base = (placed.maxOfOrNull { it.level } ?: -1) + 1
    preview?.addedLinks?.forEachIndexed { index, link ->
        if (!link.bracketed) return@forEachIndexed
        placed += RelicBracket(link, link.bracketLabel(preview.addedNames.getOrElse(index) { "" }), levelFor(link, base), preview = true, faded = false)
    }
    return placed
}

/** Named pairings and dissonance draw a bracket; harmony shows only as a multiplier. */
internal val OverlayRelicLink.bracketed: Boolean get() = pairing != null || kind == RelicLinkKind.DISSONANCE

/** A named pairing's bracket tag carries its sign ("+ Name", "− ?"); dissonance keeps its word. */
internal fun OverlayRelicLink.bracketLabel(name: String): String = when {
    name.isEmpty() || pairing == null -> name
    kind == RelicLinkKind.DISCORD -> "− $name"
    else -> "+ $name"
}

/** Link tag metrics (dp, board sp): an unmet pairing's "+ ?" takes a larger mark so it never reads as a speck. */
internal const val LinkTagHeightDp = 20f
private const val LinkTagFont = 12f
private const val UnmetLinkTagFont = 15f

internal fun linkTagFont(unmet: Boolean): Float = if (unmet) UnmetLinkTagFont else LinkTagFont

/**
 * A link's tag at [topLeft]: a met pairing on a plate in its [color]; an unmet one ("+ ?", "− ?")
 * in its color on a dark plate inside a dashed outline in the same color. [paths] slot 0 holds the outline.
 */
internal fun DrawScope.drawRelicLinkTag(
    measurer: CanvasTextMeasurer,
    label: String,
    topLeft: Offset,
    color: Color,
    unmet: Boolean,
    dashed: Stroke,
    paths: KkPathCache,
) {
    val rect = drawKkTag(measurer, label, topLeft, heightDp = LinkTagHeightDp, fontSize = linkTagFont(unmet),
        background = if (unmet) Kk.Ink2 else color, foreground = if (unmet) color else Kk.Ink)
    if (unmet) {
        val inset = 0.75f * density
        drawPath(paths.slab(0, rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, 6f * density), color,
            style = Stroke(1.5f * density, cap = StrokeCap.Butt, pathEffect = dashed.pathEffect))
    }
}

/**
 * Draws [brackets] over slots centered at [centers], legs reaching down to [legBottom]. [ticks]
 * marks interference with hatch ticks along its line (left out where brackets stack too closely).
 */
internal fun DrawScope.drawRelicBrackets(
    measurer: CanvasTextMeasurer,
    brackets: List<RelicBracket>,
    centers: FloatArray,
    firstY: Float,
    levelStep: Float,
    legBottom: Float,
    dashed: Stroke,
    paths: KkPathCache? = null,
    ticks: Boolean = true,
) {
    brackets.forEach { bracket ->
        val slots = bracket.link.slots
        if (slots.isEmpty() || slots.last() >= centers.size) return@forEach
        val y = firstY - bracket.level * levelStep
        val color = bracket.link.overlayColor(measurer.roles).let { if (bracket.faded) it.copy(alpha = 0.2f) else it }
        val left = centers[slots.first()]
        val right = centers[slots.last()]
        // An unmet pairing and every preview are dashed; interference carries hatch ticks along its line.
        val unmet = !bracket.link.discovered
        drawKkSynergyBracket(measurer, left, right, y, color, bracket.preview || unmet, null)
        if (ticks && bracket.link.interferes) {
            val tick = 5f * density
            var x = left + 8f * density
            while (x < right - 6f * density) {
                drawLine(color, Offset(x, y + tick * 0.5f), Offset(x + tick * 0.6f, y - tick * 0.5f), 1.5f * density)
                x += 9f * density
            }
        }
        if (bracket.name.isNotEmpty() && paths != null) {
            // Every met link tag sits on a plate in its color (the preview one too: its bracket is
            // dashed, its label stays a solid tag); an unmet one is outlined dashed. Kept inside the canvas.
            val tag = kkTagSize(measurer, bracket.name, density, LinkTagHeightDp, linkTagFont(unmet))
            val x = ((left + right) * 0.5f - tag.width * 0.5f).coerceIn(0f, (size.width - tag.width).coerceAtLeast(0f))
            drawRelicLinkTag(measurer, bracket.name, Offset(x, y - 6f * density - tag.height), color, unmet, dashed, paths)
        }
        val stroke = if (bracket.preview || unmet) dashed else kkStroke(2f * density)
        slots.forEach { slot ->
            val x = centers[slot]
            drawLine(color, Offset(x, y), Offset(x, legBottom), stroke.width, StrokeCap.Butt, stroke.pathEffect)
        }
    }
}

/** A link's hue under the color-vision preset: named concord in bone, interference in the threat role. */
internal fun OverlayRelicLink.overlayColor(roles: KkRolePalette): Color = when (kind) {
    RelicLinkKind.CONCORD -> Kk.Bone
    RelicLinkKind.HARMONY -> Kk.Mute
    RelicLinkKind.DISCORD -> roles.threat
    RelicLinkKind.DISSONANCE -> kkMix(roles.threat, Kk.Ink, 0.25f)
}

/**
 * A bound relic in a matrix slot: its rim and its own rune in the aspect hue under the color-vision
 * preset. The rank reads as text beside the slot, never as markers on the small rune.
 */
internal fun DrawScope.drawRelicSlotRune(
    center: Offset,
    relic: RelicDefinition,
    policy: RelicPolicy,
    sizeDp: Float,
    roles: KkRolePalette,
    time: Float,
    ring: Color = Color.Unspecified,
    alpha: Float = 1f,
) {
    val hue = relic.aspect.overlayColor(roles)
    drawKkRelicSlot(center, hue, null, sizeDp, ring = ring, alpha = alpha)
    drawRelicIcon(relic, policy, center, sizeDp * density * 0.25f, time = time, alpha = alpha, accent = hue)
}

@Composable
internal fun rememberDashedStroke(): Stroke {
    val density = LocalDensity.current.density
    return remember(density) {
        Stroke(2f * density, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density)))
    }
}
