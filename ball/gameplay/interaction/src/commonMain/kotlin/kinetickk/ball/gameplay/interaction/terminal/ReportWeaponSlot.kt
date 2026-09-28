// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkWeaponSlot
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle

/** Where a weapon slot puts its icon and its "Lvl N" label (px). */
internal class WeaponSlotPlacement(
    val iconCenter: Offset,
    val iconSize: Float,
    val label: TextLayoutResult?,
    /** End of the label (it is end-aligned) and its baseline. */
    val labelRight: Float,
    val labelBaseline: Float,
    /** Top of the label's tallest glyph ("l" in "Lvl"). */
    val labelInkTop: Float,
)

/**
 * A weapon slot (`.wslot`) at any size, with its icon and "Lvl N" placed from the slot size. The
 * foundation slot centres a fixed icon and hangs the mono 10 px label 9 dp from the right and
 * 5 dp from the bottom; below the board's size that label runs past the sheared left edge ("Lvl 10"
 * is 42 px wide) and meets the icon's lowest stroke. The face, glow and colors still come from
 * the foundation; here the label keeps the board anchor and shrinks only when it would leave the
 * face, and the icon (24/50 of the slot as on the Report board, 26/50 on Mobile-Pause) rises just
 * enough to stay [SlotIconGapDp] above the label.
 */
internal fun DrawScope.drawPlacedWeaponSlot(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon,
    levelText: String?,
    maxLevel: Boolean,
    ready: Boolean = false,
    placement: WeaponSlotPlacement = weaponSlotPlacement(measurer, bounds, icon, levelText),
) {
    drawKkWeaponSlot(measurer, bounds, icon = null, ready = ready, maxLevel = maxLevel, empty = false)
    drawKkIcon(icon, placement.iconCenter, placement.iconSize, if (maxLevel) Kk.RLegend else measurer.roles.you)
    val label = placement.label ?: return
    drawKkText(label, placement.labelRight, placement.labelBaseline, if (maxLevel) Kk.RLegend else Kk.Bone,
        align = KkAlign.END, valign = KkVAlign.BASELINE)
}

/** The foundation slot's shear (its face runs from x + 9 dp at the top to x at the bottom). */
private const val SlotCutDp = 9f

/** Board `.lv` anchor: right 9 px, bottom 5 px, mono 700 10 px. */
private const val LabelRightDp = 9f
private const val LabelBottomDp = 5f
private const val LabelSizeSp = 10f

/** Height of the label's tallest glyph over its baseline (Martian Mono "l" is 0.848 em). */
private const val LabelAscentEm = 0.85f

/**
 * Glyphs land up to about a pixel above the reported baseline at small sizes (the renderer snaps
 * the baseline to whole device pixels), so the label's ink top is taken this much higher.
 */
private const val LabelSnapPx = 1.5f

/** Clear room between the label and the face's sheared left edge. */
private const val LabelInsetDp = 1f

/** Clear room between the icon's lowest stroke and the label's tallest glyph. */
internal const val SlotIconGapDp = 3f

/** Icon size relative to the slot (24 px icon in the board's 50 px slot). */
private const val IconShare = 24f / 50f

/** Clear room above the icon inside the slot. */
private const val IconTopDp = 3f

internal fun Density.weaponSlotPlacement(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon,
    levelText: String?,
    iconShare: Float = IconShare,
): WeaponSlotPlacement {
    val cut = SlotCutDp * density
    val height = bounds.height
    // Left edge of the sheared face at height y.
    fun faceLeft(y: Float): Float = bounds.left + cut * ((bounds.bottom - y) / height).coerceIn(0f, 1f)
    val right = bounds.right - LabelRightDp * density
    val baseline = bounds.bottom - LabelBottomDp * density
    var label: TextLayoutResult? = null
    var inkTop = baseline
    if (levelText != null) {
        var size = LabelSizeSp
        var attempts = 0
        while (attempts++ < 8) {
            val layout = measureKkText(measurer, levelText, measurer.typography.monoStyle(size, FontWeight.Bold, 0f, 1f))
            // The measured style already carries the measurer's scale.
            val top = baseline - layout.layoutInput.style.fontSize.toPx() * LabelAscentEm - LabelSnapPx
            val room = right - faceLeft(top) - LabelInsetDp * density
            label = layout
            inkTop = top
            if (layout.size.width <= room || room <= 0f) break
            size *= room / layout.size.width * 0.98f
        }
    }
    // The icon keeps the board's share of the slot and its centre, and rises only as far as the
    // label needs; a slot too short for both shrinks the icon instead.
    val ink = IconInk.of(icon)
    var iconSize = bounds.width * iconShare
    val floor = inkTop - SlotIconGapDp * density
    val ceiling = bounds.top + IconTopDp * density
    var centerY = bounds.center.y
    if (centerY + iconSize * ink.below > floor) centerY = floor - iconSize * ink.below
    if (centerY - iconSize * ink.above < ceiling) {
        iconSize = ((floor - ceiling) / (ink.above + ink.below)).coerceAtLeast(0f)
        centerY = ceiling + iconSize * ink.above
    }
    // The face's own centre at that height (the shear moves it right as it rises).
    val centerX = (faceLeft(centerY) + bounds.right - cut * ((centerY - bounds.top) / height).coerceIn(0f, 1f)) * 0.5f
    return WeaponSlotPlacement(Offset(centerX, centerY), iconSize, label, right, baseline, inkTop)
}

/**
 * [weaponSlotPlacement] for a slot redrawn every frame with the same inputs (the paused build):
 * placed once, then reused until an input changes.
 */
internal object WeaponSlotPlacementMemo {
    private var measurer: CanvasTextMeasurer? = null
    private var icon: KkIcon? = null
    private var text: String? = null
    private val key = FloatArray(6) { Float.NaN }
    private var placement: WeaponSlotPlacement? = null

    fun of(density: Density, measurer: CanvasTextMeasurer, bounds: Rect, icon: KkIcon, levelText: String?, iconShare: Float): WeaponSlotPlacement {
        val cached = placement
        if (cached != null && this.measurer === measurer && this.icon == icon && this.text == levelText && key[0] == bounds.left &&
            key[1] == bounds.top && key[2] == bounds.right && key[3] == bounds.bottom && key[4] == iconShare && key[5] == density.density
        ) return cached
        this.measurer = measurer
        this.icon = icon
        text = levelText
        key[0] = bounds.left
        key[1] = bounds.top
        key[2] = bounds.right
        key[3] = bounds.bottom
        key[4] = iconShare
        key[5] = density.density
        return density.weaponSlotPlacement(measurer, bounds, icon, levelText, iconShare).also { placement = it }
    }
}

/** How far an icon's ink reaches above and below its centre, as a share of its drawn size. */
private class IconInkExtent(val above: Float, val below: Float)

/**
 * Ink extents of the icons as drawn: each icon is rendered once, at 48 px, into a small offscreen
 * bitmap on first use, so miter joins (the Arc Coil's zigzag), square caps and dashes count
 * exactly as the renderer draws them.
 */
private object IconInk {
    private const val SIDE = 96
    private const val ICON = 48f
    private val cache = arrayOfNulls<IconInkExtent>(KkIcon.entries.size)

    fun of(icon: KkIcon): IconInkExtent = cache[icon.ordinal] ?: measure(icon).also { cache[icon.ordinal] = it }

    private fun measure(icon: KkIcon): IconInkExtent {
        val image = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(SIDE.toFloat(), SIDE.toFloat())) {
            drawKkIcon(icon, Offset(SIDE * 0.5f, SIDE * 0.5f), ICON, Color.White)
        }
        val pixels = IntArray(SIDE * SIDE)
        image.readPixels(pixels)
        var top = SIDE
        var bottom = -1
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            if ((pixels[y * SIDE + x] ushr 24) > 8) {
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (bottom < 0) return IconInkExtent(0.5f, 0.5f)
        return IconInkExtent((SIDE * 0.5f - top) / ICON, (bottom + 1 - SIDE * 0.5f) / ICON)
    }
}
