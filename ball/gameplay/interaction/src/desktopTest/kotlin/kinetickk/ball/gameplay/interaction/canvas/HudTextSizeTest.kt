// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.localeList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * PR decision "Text size": at the game's default 125 % the HUD renders at the `HUD` board's size;
 * display numerals (timer, speed, integrity, chain, level numeral) ignore the setting and UI text
 * follows it relative to the board.
 */
class HudTextSizeTest {
    /** Bone glyph extents measured on `screenshots/boards/HUD.png` (cruise state), px at 1440 × 810. */
    private val board = mapOf(
        Region.TIMER to (148 to 33),
        Region.INTEGRITY to (98 to 33),
        Region.CHAIN to (74 to 39),
        // "412" at the board's scaleX(1.15): 149 px wide; unstretched (velocity tier 0, as here) 129 px.
        Region.SPEED to (129 to 43),
    )

    private enum class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        TIMER(560, 5, 880, 75),
        SPEED(520, 680, 920, 756),
        INTEGRITY(20, 690, 200, 745),
        CHAIN(1_300, 150, 1_420, 212),
    }

    @Test
    fun displayNumeralsMatchTheBoardAtTheDefaultSizeAndIgnoreTheSetting() {
        // The board's cruise state: 07:42, speed 412 (tier 0), integrity 142, chain ×12.
        val model = hudTestModel().with(
            "elapsed" to 462f, "velocityX" to 412f, "velocityY" to 0f, "hp" to 142f, "maxHp" to 180f,
            "combo" to 12, "comboTime" to 1.7f, "comboWindow" to 2.8f, "level" to 14, "keys" to 1, "runMatter" to 486L,
        )
        val extents = listOf(1f, 1.25f, 1.75f).associateWith { textScale -> glyphExtents(render(model, textScale)) }
        Region.entries.forEach { region ->
            val (width, height) = assertNotNull(extents.getValue(1.25f)[region], "$region not drawn")
            val (boardWidth, boardHeight) = board.getValue(region)
            assertTrue(abs(width - boardWidth) <= 3 && abs(height - boardHeight) <= 3,
                "$region at the default size is $width x $height, the board's is $boardWidth x $boardHeight")
            assertEquals(extents.getValue(1.25f)[region], extents.getValue(1f)[region], "$region changes at 100 %")
            assertEquals(extents.getValue(1.25f)[region], extents.getValue(1.75f)[region], "$region changes at 175 %")
        }
    }

    @Test
    fun uiTextFollowsTheSettingRelativeToTheBoard() {
        val model = hudTestModel().with(
            "keys" to 1, "runMatter" to 486L, "weaponLevel" to 7,
            "enemies" to listOf(EnemyProjection(4, EnemyType.ELITE, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                .toImmutableList(),
        )
        for (textScale in listOf(1f, 1.25f, 1.75f)) {
            render(model, textScale)
            val growth = textScale / 1.25f
            // Board sizes at 1440 × 810 (rendered px): chip values 21, elite name 22.
            listOf(HudText.MATTER to 21f, HudText.KEYS to 21f, HudText.BOSS_NAME to 22f).forEach { (slot, boardSize) ->
                val layout = assertNotNull(HudDrawCache.peekLayout(slot), "$slot")
                assertEquals(boardSize * growth, layout.layoutInput.style.fontSize.value, 0.01f, "$slot at x$textScale")
            }
            // Labels on a fixed plate (weapon level 10, Dash 22) grow too, shrinking only as far as their plate needs.
            listOf(HudText.WEAPON_LEVEL to 10f, HudText.DASH_LABEL to 22f).forEach { (slot, boardSize) ->
                val size = assertNotNull(HudDrawCache.peekLayout(slot), "$slot").layoutInput.style.fontSize.value
                if (textScale <= 1.25f) assertEquals(boardSize * growth, size, 0.01f, "$slot at x$textScale")
                else assertTrue(size >= boardSize - 0.01f && size <= boardSize * growth + 0.01f, "$slot at x$textScale: $size")
            }
            // The timer is display type: the board size at any setting.
            assertEquals(44f, assertNotNull(HudDrawCache.peekLayout(HudText.TIMER)).layoutInput.style.fontSize.value, 0.01f)
        }
    }

    private fun render(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel, textScale: Float): PixelMap {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale,
            AppLanguage.English, HudTestFonts.typography.copy(localeList = AppLanguage.English.localeList()))
        val bitmap = ImageBitmap(1_440, 810)
        HudDrawCache.forgetLayouts()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
            drawRect(kinetickk.foundation.design.Kk.Ink)
            drawHud(model, measurer, 1f)
        }
        return bitmap.toPixelMap()
    }

    /** Width and height of the bone (near-white) pixels in each region. */
    private fun glyphExtents(pixels: PixelMap): Map<Region, Pair<Int, Int>?> = Region.entries.associateWith { region ->
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in region.top until region.bottom) for (x in region.left until region.right) {
            val color = pixels[x, y]
            if (color.red > 0.785f && color.green > 0.785f && color.blue > 0.785f) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < 0) null else (right - left + 1) to (bottom - top + 1)
    }
}
