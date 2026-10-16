// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.drawLayeredGlyph

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
