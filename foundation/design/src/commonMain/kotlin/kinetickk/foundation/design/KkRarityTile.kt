// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.max
import kotlin.math.min

// The rarity channel away from a card: a material's frame on a small tile and its band as a tag,
// drawn by the card frame's own code (KkCardFrame.kt) at tile scale. Nothing here reads a type hue.

/**
 * The moment of the material loops a still tile or tag shows: the chrome and gold glints cross the
 * upper left of the border, the spectrum and the gold foil stand part-way round.
 */
const val KK_TILE_STILL_TIME = 2.45f

/** How far (dp) a tile's halo reaches past its frame by default: within half of a 6 dp gap between tiles. */
const val KK_TILE_HALO_DP = 3f

/**
 * A tile whose shorter side is this many dp draws its border at [TILE_MAX_SCALE] of a board card's;
 * a smaller one thins it down to [TILE_MIN_SCALE], a phone card's.
 */
private const val TILE_FULL_DP = 96f
private const val TILE_MIN_SCALE = 0.8f
private const val TILE_MAX_SCALE = 0.9f

/**
 * The prismatic rim's share of the border scale on a tile: a card's full bone rim would make an
 * epic tile the heaviest on the grid, above the legendary.
 */
private const val TILE_RIM_SHARE = 0.6f

/**
 * A tile's halo packs a card's light into a quarter of its reach, so each of its strokes shines this much brighter:
 * its outer edge then stands clear of the ink instead of a faint fringe whose pixels come and go with the
 * rasterizer's rounding.
 */
private const val TILE_HALO_GAIN = 1.6f

/** A tile's glow and sparks reach as far into it as a card's into a card [TILE_GLOW_DP] wide. */
private const val TILE_GLOW_DP = 200f
private const val TILE_SPARKS_DP = 150f

/** The whole draw scope as a cached rect: the default tile or band, so a tile's draw allocates nothing. */
private fun DrawScope.scopeBounds(): Rect = KkRects.of(0f, 0f, size.width, size.height)

/** The shorter side of [bounds] in dp. */
private fun DrawScope.sideDp(bounds: Rect): Float = min(bounds.width, bounds.height) / density

/** The scale of the border and the prismatic rim on a tile of [bounds]: 1 is a board card's. */
private fun DrawScope.tileScale(bounds: Rect): Float = (sideDp(bounds) / TILE_FULL_DP).coerceIn(TILE_MIN_SCALE, TILE_MAX_SCALE)

/**
 * How far in px [material]'s tile frame reaches into a tile of [bounds] (its border, the prismatic
 * rim and the ink seam): the type channel's marks on the tile keep inside it.
 */
fun DrawScope.kkRarityTileInset(material: KkCardMaterial, bounds: Rect = scopeBounds()): Float =
    if (material.frame.borderDp <= 0f) 0f else tileScale(bounds).let { frameInnerWidth(material, bounds, it, it * TILE_RIM_SHARE) } + d(1f)

/**
 * [frame] (a rarity material in the preset's gold and spectrum, [KkCardMaterial.frame]) on a small
 * tile over [bounds] (the whole draw scope by default): the card's own rarity channel at tile
 * scale, so a rarity reads the same on a tile as on a card. Iron, steel, chrome, prismatic and gold
 * borders with their seam; the chrome and gold glint; the steel, chrome, spectrum and gold inner
 * glow; the white, spectrum and gold halo outside the frame, reaching [haloDp] (keep it within half
 * the gap to the next tile; 0 draws none); the legendary rays turning behind the glyph; the
 * prismatic and gold halftone and sparks ([seed] moves the sparks). A tile has no band and no room
 * for an echo. The face under it and the glyph over it are the caller's: the type channel. [time] is
 * [KK_TILE_STILL_TIME] for a still tile. Allocation-free once its brushes, rings and strokes exist
 * (every tile of one size shares them).
 */
fun DrawScope.drawKkRarityTile(
    frame: KkCardFrame,
    bounds: Rect = scopeBounds(),
    time: Float = KK_TILE_STILL_TIME,
    seed: Int = 0,
    haloDp: Float = KK_TILE_HALO_DP,
) {
    val material = frame.material
    if (material == KkCardMaterial.CALM) return
    val scale = tileScale(bounds)
    val side = sideDp(bounds)
    val face = KkPathMemo.slab(bounds, 0f)
    if (haloDp > 0f) {
        // Outside the tile only: its inner half would wash over the face the caller painted.
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom, ClipOp.Difference) {
            drawCardHalo(face, bounds, frame, haloDp / KK_CARD_HALO_DP, TILE_HALO_GAIN)
        }
    }
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawCardGlow(face, bounds, frame, side / TILE_GLOW_DP)
        val gold = frame.gold
        if (gold != null) {
            drawKkRays(bounds.center, max(bounds.width, bounds.height) * 0.8f, gold.ray.copy(alpha = 0.24f), 5f, 17f, 0.22f, 0.72f,
                time * 360f / (KkTime.AmbientMin / 1000f))
        }
        if (material == KkCardMaterial.PRISMATIC || material == KkCardMaterial.GOLD) {
            drawCardHalftone(bounds, frame, time)
            drawCardSparks(bounds, frame, time, seed, side / TILE_SPARKS_DP)
        }
    }
    drawCardBorder(bounds, 0f, frame, time, scale, scale * TILE_RIM_SHARE)
}

/**
 * [frame]'s band (the band a card names its rarity on) as a tag outside a card, filling [bounds]
 * (the whole draw scope by default) cut at [cut] px like a slab: matte iron, brushed steel,
 * polished chrome, black prismatic glass with its spectrum edges, gold foil. A label on it reads in
 * the material's own colour, [KkCardFrame.label] (its first colour, or across all of them on the
 * prismatic glass).
 */
fun DrawScope.drawKkRarityBand(frame: KkCardFrame, bounds: Rect = scopeBounds(), cut: Float = d(6f), time: Float = KK_TILE_STILL_TIME) {
    drawCardBandMaterial(KkPathMemo.slab(bounds, cut), bounds, frame, time)
}
