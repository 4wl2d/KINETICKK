// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.drawLayeredGlyph

/** Session owns the mapping of item semantics to the shared geometric drawing. */
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
        primaryStyle = item.primary.effect.toCanvasGlyphStyle(),
        secondaryStyle = item.secondary.effect.toCanvasGlyphStyle(),
        center = center,
        radius = radius,
        accent = accent,
        frameSides = if (obscured) 4 else rank + 3,
        markerCount = if (obscured) 1 else rank,
        outerArcDegrees = stack?.coerceIn(0, item.maxStacks)?.let { 360f * it / item.maxStacks } ?: 0f,
        crossedOut = obscured,
    )
}

private fun ItemEffect.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    ItemEffect.IMPACT_DAMAGE -> CanvasGlyphStyle.SUNBURST
    ItemEffect.WEAPON_POWER -> CanvasGlyphStyle.TRIPLE_ARROW
    ItemEffect.MASS -> CanvasGlyphStyle.CONCENTRIC_ORB
    ItemEffect.MAGNETISM -> CanvasGlyphStyle.HORSESHOE
    ItemEffect.COOLING -> CanvasGlyphStyle.SNOWFLAKE
    ItemEffect.MAX_INTEGRITY -> CanvasGlyphStyle.HEX_CROSS
    ItemEffect.REGEN -> CanvasGlyphStyle.LEAF
    ItemEffect.DASH_POWER -> CanvasGlyphStyle.DOUBLE_CHEVRON
    ItemEffect.DASH_EFFICIENCY -> CanvasGlyphStyle.ORBIT_ARROWS
    ItemEffect.CRIT_CHANCE -> CanvasGlyphStyle.RETICLE
    ItemEffect.CRIT_DAMAGE -> CanvasGlyphStyle.BOLT
    ItemEffect.PICKUP_RADIUS -> CanvasGlyphStyle.RADIAL_NODES
    ItemEffect.LUCK -> CanvasGlyphStyle.FOUR_LEAF
    ItemEffect.DATA_GAIN -> CanvasGlyphStyle.CIRCUIT_LINES
    ItemEffect.MATTER_GAIN -> CanvasGlyphStyle.CRYSTAL
    ItemEffect.ATTACK_SPEED -> CanvasGlyphStyle.SLASH_BARS
    ItemEffect.SHIELD_CAPACITY -> CanvasGlyphStyle.SHIELD
    ItemEffect.DAMAGE_REDUCTION -> CanvasGlyphStyle.BRICK_LINES
    ItemEffect.COMBO_WINDOW -> CanvasGlyphStyle.INTERLOCKING_RINGS
    ItemEffect.OVERDRIVE_GAIN -> CanvasGlyphStyle.THREE_BLADE
}
