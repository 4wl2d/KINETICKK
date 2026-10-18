// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

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
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkLuminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The second set of epic signature illustrations shows on its card and stays behind the text: in
 * every vision mode, on the board and on a phone, each one visibly paints the card body, and even
 * without the soft text plates it never brings the local background of bone body text (the face
 * averaged over a glyph-sized 5 px box) closer than 3:1 wherever the face alone reads at 4.5:1 (the
 * face's own bright marks, its sparks and glints, are the material's, not the illustration's).
 */
class EpicSignatureArtBTest {
    private val arts = listOf(
        RewardCardArt.PHOENIX_COIL, RewardCardArt.SHATTERGLASS, RewardCardArt.STORM_LATTICE, RewardCardArt.DARK_STAR,
        RewardCardArt.NECROTIC_EXCHANGE, RewardCardArt.DEADEYE_RETICLE, RewardCardArt.PRISMATIC_CORE, RewardCardArt.VOLATILE_COMPOUND,
    )

    @Test
    fun everyIllustrationShowsAndStaysFaintBehindTheText() {
        val content = shippedRewardContent
        val items = arts.map { art -> content.items.single { it.name == art.itemName } }
        val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id) }
        val model = rewardFixtureModel(content = content, choices = choices)
        val cards = choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index) }
        for (palette in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Mono)) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.English, roles = palette)
            for ((width, height, phone) in listOf(Triple(300, 450, false), Triple(195, 254, true))) {
                cards.forEachIndexed { index, card ->
                    val look = rewardCardLook(card, palette)
                    assertEquals(arts[index], look.art, "${card.title} draws its own illustration")
                    val bare = RewardCardLook(look.style, null, look.hue, look.secondHue)
                    val drawn = face(measurer, card, look, width, height, phone)
                    val plain = face(measurer, card, bare, width, height, phone)
                    val scene = "${card.title} at $width x $height in ${palette.name()}"
                    // The body below the name, inside the text column.
                    val x0 = (width * 0.12f).toInt()
                    val x1 = (width * 0.86f).toInt()
                    val y0 = (height * 0.38f).toInt()
                    val y1 = (height * 0.9f).toInt()
                    var changed = 0
                    var lowest = Float.MAX_VALUE
                    var where = ""
                    val drawnLight = localLuminance(drawn)
                    val plainLight = localLuminance(plain)
                    for (y in y0 until y1) for (x in x0 until x1) {
                        val a = drawn[x, y]
                        val b = plain[x, y]
                        val delta = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))
                        if (delta > 4f / 255f) changed++
                        // Only where the face alone keeps bone text comfortably readable: the art must not spoil it.
                        val contrast = contrast(drawnLight[y * width + x])
                        val base = contrast(plainLight[y * width + x])
                        if (base >= 4.5f && contrast < lowest) {
                            lowest = contrast
                            where = "($x, $y), ${"%.2f".format(base)}:1 without it"
                        }
                    }
                    val share = changed.toFloat() / ((x1 - x0) * (y1 - y0))
                    assertTrue(share >= 0.04f, "$scene: the illustration paints ${"%.1f".format(share * 100)} % of the body")
                    assertTrue(lowest >= 3f, "$scene: bone text over the illustration reads at only ${"%.2f".format(lowest)}:1 at $where")
                }
            }
        }
    }

    private fun face(measurer: CanvasTextMeasurer, card: RewardCardPresentation, look: RewardCardLook, width: Int, height: Int, phone: Boolean): PixelMap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawRewardCardFace(measurer, card, card.accent, GlintGone, 0f, -1f, KkPathCache(4), phone, look, RewardArtCache())
        }
        return bitmap.toPixelMap()
    }

    /** Relative luminance of every pixel averaged over the 5 × 5 box around it (clamped at the edges). */
    private fun localLuminance(pixels: PixelMap): FloatArray {
        val width = pixels.width
        val height = pixels.height
        val light = FloatArray(width * height) { kkLuminance(pixels[it % width, it / width]) }
        val out = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            var count = 0
            for (dy in -2..2) for (dx in -2..2) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until width && py in 0 until height) {
                    sum += light[py * width + px]
                    count++
                }
            }
            out[y * width + x] = sum / count
        }
        return out
    }

    private fun contrast(luminance: Float): Float = (kkLuminance(Kk.Bone) + 0.05f) / (luminance + 0.05f)

    private companion object {
        /** A moment of the 3.2 s epic loop with the facet glint between passes, so only the art lights the face. */
        const val GlintGone = 3f
    }

    private fun KkRolePalette.name(): String = when (this) {
        KkRolePalette.Protan -> "Protan"
        KkRolePalette.Mono -> "Mono"
        else -> "Default"
    }
}
