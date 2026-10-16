// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
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
import kinetickk.foundation.design.kkContrast
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The first set of epic signature illustrations (the nine Kinetic epics) shows on its card, stays
 * behind the text, holds still and stays cheap: in every vision mode, on the board and on a phone,
 * each visibly paints the card body, no body pixel it lights (beyond what the card itself already
 * lights there) comes closer than 3:1 to bone body text even without the soft text plates, two moments of the loop draw the same picture, and one
 * illustration takes a bounded number of draw operations.
 */
class EpicSignatureArtATest {
    private val arts = listOf(
        RewardCardArt.TUNGSTEN_HEART, RewardCardArt.REDLINE_GOVERNOR, RewardCardArt.ARREST_ENGINE, RewardCardArt.OVERCHARGED_CAPACITOR,
        RewardCardArt.GLASS_EDGE, RewardCardArt.LAST_STAND, RewardCardArt.REDLINE_REACTOR, RewardCardArt.DOMINO_PROTOCOL,
        RewardCardArt.GAMBLERS_LEDGER,
    )
    private val sizes = listOf(Triple(300, 450, false), Triple(195, 254, true))

    @Test
    fun everyIllustrationShowsAndStaysFaintBehindTheText() {
        val cards = cards()
        val failures = mutableListOf<String>()
        for (palette in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Mono)) {
            val measurer = measurer(palette)
            for ((width, height, phone) in sizes) {
                cards.forEachIndexed { index, card ->
                    val look = rewardCardLook(card, palette)
                    assertEquals(arts[index], look.art, "${card.title} draws its own illustration")
                    val bare = RewardCardLook(look.style, null, look.hue, look.secondHue)
                    val drawn = face(measurer, card, look, width, height, phone, RewardArtCache())
                    val plain = face(measurer, card, bare, width, height, phone, RewardArtCache())
                    val scene = "${card.title} at $width x $height in ${palette.label()}"
                    // The body below the name, inside the text column.
                    val x0 = (width * 0.12f).toInt()
                    val x1 = (width * 0.86f).toInt()
                    val y0 = (height * 0.38f).toInt()
                    val y1 = (height * 0.9f).toInt()
                    var changed = 0
                    var lowest = Float.MAX_VALUE
                    var where = ""
                    for (y in y0 until y1) for (x in x0 until x1) {
                        val a = drawn[x, y]
                        val b = plain[x, y]
                        if (max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) > 4f / 255f) {
                            changed++
                            // A pixel the card itself already lights (a rising spark, an ornament, a facet line) is not the art's.
                            if (kkContrast(Kk.Bone, b) < LitByCard) continue
                            val contrast = kkContrast(Kk.Bone, a)
                            if (contrast < lowest) {
                                lowest = contrast
                                where = "($x, $y)"
                            }
                        }
                    }
                    val share = changed.toFloat() / ((x1 - x0) * (y1 - y0))
                    if (share < 0.06f) failures += "$scene: the illustration paints only ${"%.1f".format(share * 100)} % of the body"
                    if (lowest < 3f) failures += "$scene: bone text over the illustration reads at only ${"%.2f".format(lowest)}:1 at $where"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun illustrationsHoldStillAndStayCheap() {
        val cards = cards()
        val palette = KkRolePalette.Default
        val measurer = measurer(palette)
        val draws = mutableListOf<String>()
        val heavy = mutableListOf<String>()
        for ((width, height, phone) in sizes) {
            cards.forEachIndexed { index, card ->
                val look = rewardCardLook(card, palette)
                val cache = RewardArtCache()
                face(measurer, card, look, width, height, phone, cache)
                val frame = assertNotNull(cache.current(look, palette, width.toFloat(), height.toFloat(), 1f, phone), "${card.title}: the art frame is kept")
                val early = art(arts[index], frame, width, height, 0.4f)
                val late = art(arts[index], frame, width, height, 5.7f)
                var differing = 0
                for (y in 0 until height) for (x in 0 until width) if (early.pixels[x, y] != late.pixels[x, y]) differing++
                assertEquals(0, differing, "${card.title} at $width x $height is a still illustration")
                draws += "${card.title} at $width x $height: ${early.draws}"
                if (early.draws !in 1..MaxDraws) heavy += draws.last()
            }
        }
        assertTrue(heavy.isEmpty(), "Draw operations per illustration (at most $MaxDraws): ${draws.joinToString()}")
    }

    private class Drawn(val pixels: PixelMap, val draws: Int)

    private fun cards(): List<RewardCardPresentation> {
        val content = shippedRewardContent
        val items = arts.map { art -> content.items.single { it.name == art.itemName } }
        val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id) }
        val model = rewardFixtureModel(content = content, choices = choices)
        return choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index) }
    }

    private fun measurer(palette: KkRolePalette) =
        CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, AppLanguage.English, roles = palette)

    private fun face(
        measurer: CanvasTextMeasurer,
        card: RewardCardPresentation,
        look: RewardCardLook,
        width: Int,
        height: Int,
        phone: Boolean,
        cache: RewardArtCache,
    ): PixelMap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawRewardCardFace(measurer, card, card.accent, GlintGone, 0f, -1f, KkPathCache(4), phone, look, cache)
        }
        return bitmap.toPixelMap()
    }

    /** The illustration alone over ink at [time], counting its draw operations. */
    private fun art(art: RewardCardArt, frame: RewardArtFrame, width: Int, height: Int, time: Float): Drawn {
        val bitmap = ImageBitmap(width, height)
        val counting = CountingCanvas(Canvas(bitmap))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, counting, Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            counting.draws = 0
            drawRewardCardArt(art, frame, time)
        }
        return Drawn(bitmap.toPixelMap(), counting.draws)
    }

    /** Counts the shape and layer operations reaching [real]. */
    private class CountingCanvas(private val real: Canvas) : Canvas by real {
        var draws = 0

        override fun saveLayer(bounds: Rect, paint: Paint) {
            draws++
            real.saveLayer(bounds, paint)
        }

        override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
            draws++
            real.drawLine(p1, p2, paint)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            draws++
            real.drawRect(left, top, right, bottom, paint)
        }

        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            draws++
            real.drawOval(left, top, right, bottom, paint)
        }

        override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
            draws++
            real.drawCircle(center, radius, paint)
        }

        override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
            draws++
            real.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint)
        }

        override fun drawPath(path: Path, paint: Paint) {
            draws++
            real.drawPath(path, paint)
        }
    }

    private fun KkRolePalette.label(): String = when (this) {
        KkRolePalette.Protan -> "Protan"
        KkRolePalette.Mono -> "Mono"
        else -> "Default"
    }

    private companion object {
        /** A moment of the 3.2 s epic loop with the facet glint between passes, so only the art lights the face. */
        const val GlintGone = 3f

        /** The bare face reads at 8:1 or more against bone even over its scanlines; anything brighter is the card's own effect. */
        const val LitByCard = 6f

        /** Draw operations one illustration may take (the whole card stays near 250). */
        const val MaxDraws = 60
    }
}
