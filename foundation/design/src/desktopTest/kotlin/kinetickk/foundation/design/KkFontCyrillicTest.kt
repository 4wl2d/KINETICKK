// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sofia Sans ships Bulgarian Cyrillic forms by default; the bundled copies map Russian letters to
 * the forms of the font's own `locl` RUS lookup (`tools/fonts/russian_cyrillic_defaults.py`), because
 * desktop and web text shaping ignores the text locale.
 */
class KkFontCyrillicTest {
    @Test
    fun sofiaSansDrawsRussianLetterformsByDefault() {
        val russianBody = mapOf('Д' to 399, 'Л' to 400, 'в' to 452, 'г' to 453, 'д' to 456, 'и' to 459, 'л' to 464, 'п' to 465, 'т' to 466)
        listOf("kk_body_regular", "kk_body_medium", "kk_body_bold", "kk_cond_black", "kk_cond_extrabold").forEach { font ->
            assertGlyphs(font, russianBody)
        }
        assertGlyphs("kk_cond_black_italic", mapOf('Д' to 398, 'Л' to 399, 'в' to 451, 'д' to 452, 'к' to 455, 'л' to 457))
    }

    private fun assertGlyphs(font: String, expected: Map<Char, Int>) {
        val bytes = File("src/commonMain/composeResources/font/$font.ttf").readBytes()
        val typeface = requireNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes))) { "$font does not load" }
        expected.forEach { (letter, glyph) ->
            assertEquals(glyph, typeface.getUTF32Glyph(letter.code).toInt(), "$font maps $letter to its Russian form")
        }
    }
}
