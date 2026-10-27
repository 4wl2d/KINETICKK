// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.drawLayeredGlyph
import kinetickk.foundation.design.kkContrast

/** Gameplay owns the mapping of item semantics to the shared geometric drawing. */
internal fun DrawScope.drawItemIcon(
    item: ItemDefinition,
    center: Offset,
    radius: Float,
    accent: Color,
    stack: Int? = null,
    obscured: Boolean = false,
) {
    val rank = item.rarity.rank.coerceIn(1, 5)
    drawLayeredGlyph(
        primaryStyle = item.primaryGlyphStyle(),
        secondaryStyle = item.secondaryGlyphStyle(),
        center = center,
        radius = radius,
        accent = accent,
        frameSides = if (obscured) 4 else rank + 3,
        markerCount = if (obscured) 1 else rank,
        outerArcDegrees = stack?.coerceIn(0, item.maxStacks)?.let { 360f * it / item.maxStacks } ?: 0f,
        crossedOut = obscured,
    )
}

/**
 * An active item's mark, as its Z/X/C slot draws it: the effect's glyph ([skillGlyph]) in [color] with [maxRank] sheared
 * rank pips under it, [rank] of them filled (empty ones in [activeItemEmptyPipColor] of the [plate] under them), in a
 * square of [side] px centred at [center]. The reward card's icon plate, the pause Build grid and the Codex's Build
 * tiles draw an active this way. Allocation-free.
 */
fun DrawScope.drawActiveItemMark(
    effect: SkillId,
    center: Offset,
    side: Float,
    color: Color,
    rank: Int,
    maxRank: Int,
    plate: Color,
) {
    val top = center.y - side * 0.5f
    drawKkIcon(skillGlyph(effect), Offset(center.x, top + side * 0.44f), side * 0.48f, color)
    val pipHeight = side * 0.07f
    val pipWidth = side * 0.13f
    val gap = side * 0.05f
    val row = maxRank * pipWidth + (maxRank - 1) * gap
    drawKkPips(Offset(center.x - row * 0.5f, top + side * 0.78f), maxRank, rank.coerceIn(0, maxRank), color,
        sizeDp = pipHeight / density, widthDp = pipWidth / density, gapDp = gap / density, emptyColor = activeItemEmptyPipColor(plate))
}

/**
 * An unlit rank pip on [plate]: the Line2 slot of the dark plates, or an Ink one where the plate is light enough to
 * swallow Line2 (a pale hue's tint, a lit Codex tile), whichever stands further from the plate.
 */
fun activeItemEmptyPipColor(plate: Color): Color = if (kkContrast(Kk.Line2, plate) >= kkContrast(Kk.Ink, plate)) Kk.Line2 else Kk.Ink

/** The item's first element when it has one, otherwise its first tag. */
internal fun ItemDefinition.primaryGlyphStyle(): CanvasGlyphStyle =
    elements.firstOrNull()?.toCanvasGlyphStyle() ?: tags.firstOrNull()?.toCanvasGlyphStyle() ?: CanvasGlyphStyle.SUNBURST

/** The first tag behind an element, else the second tag or element; a lone mark repeats itself. */
internal fun ItemDefinition.secondaryGlyphStyle(): CanvasGlyphStyle = when {
    elements.isNotEmpty() -> tags.firstOrNull()?.toCanvasGlyphStyle() ?: elements.getOrNull(1)?.toCanvasGlyphStyle()
    else -> tags.getOrNull(1)?.toCanvasGlyphStyle()
} ?: primaryGlyphStyle()

internal fun StatusElement.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    StatusElement.THERMAL -> CanvasGlyphStyle.SUNBURST
    StatusElement.CRYO -> CanvasGlyphStyle.SNOWFLAKE
    StatusElement.ION -> CanvasGlyphStyle.BOLT
    StatusElement.GRAVITIC -> CanvasGlyphStyle.CONCENTRIC_ORB
    StatusElement.ENTROPY -> CanvasGlyphStyle.LEAF
    StatusElement.SONIC -> CanvasGlyphStyle.SOUND_WAVES
    StatusElement.VOID -> CanvasGlyphStyle.HOLLOW_RING
}

internal fun BuildTag.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    BuildTag.RAM -> CanvasGlyphStyle.TRIPLE_ARROW
    BuildTag.DRIVE -> CanvasGlyphStyle.DOUBLE_CHEVRON
    BuildTag.ANCHOR -> CanvasGlyphStyle.HORSESHOE
    BuildTag.ARSENAL -> CanvasGlyphStyle.SLASH_BARS
    BuildTag.PRECISION -> CanvasGlyphStyle.RETICLE
    BuildTag.BULWARK -> CanvasGlyphStyle.SHIELD
    BuildTag.OVERCLOCK -> CanvasGlyphStyle.THREE_BLADE
    BuildTag.CHAIN -> CanvasGlyphStyle.INTERLOCKING_RINGS
    BuildTag.FORTUNE -> CanvasGlyphStyle.FOUR_LEAF
}
