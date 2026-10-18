// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Card styles in two channels: the rarity material paints the frame and never the hue, its ladder
 * rises from iron to gold, its labels read on every band; the hue's pattern, ornament, watermark and
 * seed make every face its own; the default style is memoized and a hand of styled cards animates
 * without allocating.
 */
class KkCardStyleTest {
    private val measurer = kkTestMeasurer()
    private val bounds = Rect(20f, 20f, 320f, 470f)

    /** The rarity materials in ladder order (common to legendary). */
    private val ladder = listOf(KkCardMaterial.IRON, KkCardMaterial.STEEL, KkCardMaterial.CHROME, KkCardMaterial.PRISMATIC, KkCardMaterial.GOLD)

    private fun card(style: KkCardStyle, time: Float = 1.3f, labels: Boolean = true) = kkRender(360, 500) {
        drawKkCard(measurer, bounds, style.material.rank, time, 0f, if (labels) "Rarity" else null, if (labels) "Stack 1/2" else null, style = style)
    }

    @Test
    fun theDefaultStyleIsMemoized() {
        assertSame(KkCardStyle.forRank(3, Kk.RRare), KkCardStyle.forRank(3, Kk.RRare))
        assertEquals(KkCardMaterial.PRISMATIC, KkCardStyle.forRank(4).material)
        assertEquals(KkCardMaterial.IRON, KkCardStyle.forRank(-2).material)
        assertEquals(ladder, (1..5).map(KkCardMaterial::ofRank))
    }

    @Test
    fun theRarityFrameNeverTakesTheHue() {
        // Without type marks (pattern, ornament, watermark) a card draws nothing in its hue: every
        // material renders the very same pixels whatever hue the style carries.
        val hues = listOf(Kk.EThermal, Kk.EIon, KkRolePalette.Default.kinetic, KkRolePalette.Mono.you)
        for (material in KkCardMaterial.entries) {
            val images = hues.map { hue -> card(KkCardStyle(material, hue, seed = 7)).argb().toList() }
            assertEquals(1, images.toSet().size, "$material: the frame changes with the hue")
        }
        // The model says the same: the frame colours are the material's own (in the preset's gold), the
        // same whatever the hue. How far they keep from every type tone, as each preset is seen, is
        // KkCardFrameVisionTest's.
        for (material in KkCardMaterial.entries) {
            val frame = material.frame
            for (hue in hues) {
                assertEquals(frame.colors, KkCardStyle(material, hue).rarityColors(), "$material frame colours")
                assertEquals(material.frame(KkGold.Deep).colors, KkCardStyle(material, hue, gold = KkGold.Deep).rarityColors(),
                    "$material frame colours in the deep gold")
            }
            when (material) {
                // Gold is the legendary's own (and Rebirth's); the spectrum is every colour at once.
                KkCardMaterial.GOLD -> Unit
                KkCardMaterial.PRISMATIC -> {
                    // The Default spectrum as painted (its stops and the gradient between them) covers the wheel;
                    // how every preset's spectrum is seen is KkCardFrameVisionTest's.
                    val stops = KkSpectrum.Full.stops
                    val painted = stops.indices.flatMap { index ->
                        val next = stops[(index + 1) % stops.size]
                        listOf(0f, 0.25f, 0.5f, 0.75f).map { kkMix(stops[index], next, it) }
                    }
                    val angles = painted.map(::hueDegrees).sorted()
                    val gaps = angles.zipWithNext { a, b -> b - a } + (360f - angles.last() + angles.first())
                    assertTrue(gaps.max() < 75f, "the spectrum covers the wheel (largest gap ${gaps.max()}°)")
                    assertTrue(stops.size >= 7)
                }
                // Iron, steel and chrome are neutral metals: no hue at all.
                else -> for (color in frame.colors) assertTrue(chroma(color) <= 0.1f, "$material paints a coloured $color")
            }
        }
    }

    @Test
    fun theRarityLadderRisesFromIronToGold() {
        // Richness: each material layers more effects than the one below it.
        val richness = ladder.map { it.frame.richness }
        assertEquals(richness.sorted(), richness)
        assertEquals(richness.size, richness.toSet().size, "every step adds effects: $richness")
        // The metals brighten: iron, steel and chrome borders and bands rise in luminance.
        val metals = ladder.take(3)
        val borders = metals.map { material -> material.frame.border.map(::kkLuminance).average() }
        val bands = metals.map { material -> material.frame.labelZone.map(::kkLuminance).average() }
        assertEquals(borders.sorted(), borders, "border luminance $borders")
        assertEquals(bands.sorted(), bands, "band luminance $bands")
        // On screen: the frame's salience (how far its pixels stand from the ink, in lightness and colour,
        // as each preset is seen: luminance alone in Mono) rises by a tenth or more with every step, on a
        // board card and on a phone card, and only the top three rest echoes and halos outside the card.
        val failures = ArrayList<String>()
        val sizes = listOf(Triple("board", bounds, 42f), Triple("phone", Rect(20f, 20f, 215f, 274f), 30f))
        for (mode in KkVisionMode.entries) for ((name, area, band) in sizes) {
            val palette = mode.palette
            val measurer = kkTestMeasurer(palette)
            val salience = ladder.map { material ->
                val style = KkCardStyle(material, palette.element(2), gold = palette.gold, spectrum = palette.spectrum)
                val image = kkRender(360, 500) {
                    drawKkCard(measurer, area, material.rank, 0.7f, 0f, null, null, band, style = style)
                }
                salience(image.argb(), mode.vision())
            }
            for (index in 1 until salience.size) {
                if (salience[index] <= salience[index - 1] * 1.1f) {
                    failures += "$mode $name: ${ladder[index]} (${salience[index].toInt()}) under ${ladder[index - 1]} (${salience[index - 1].toInt()}) + 10 %"
                }
            }
        }
        assertTrue(failures.isEmpty(), "the rarity ladder does not rise:\n" + failures.joinToString("\n"))
        val outside = ladder.map { material -> outsideShare(card(KkCardStyle(material, Kk.EIon), labels = false).argb()) }
        assertEquals(0f, outside[0], "iron rests flat")
        assertEquals(0f, outside[1], "steel rests flat")
        for (index in 2 until outside.size) assertTrue(outside[index] > 0.01f, "${ladder[index]} shows an echo or halo (${outside[index]})")
    }

    /**
     * Σ over the pixels of how far each stands from the ink as [vision] sees it: its luminance step plus
     * its chroma (luminance alone when the viewer sees luminance alone).
     */
    private fun salience(argb: IntArray, vision: Vision = Vision.NONE): Float {
        val ink = kkLuminance(seen(Kk.Ink, vision))
        var total = 0f
        for (pixel in argb) {
            val color = seen(Color(pixel), vision)
            total += kotlin.math.abs(kkLuminance(color) - ink) + if (vision == Vision.LUMINANCE) 0f else chroma(color)
        }
        return total
    }

    /** [color] as [vision] sees it, back in sRGB (the simulation runs on linear light, as KkHueGrammarTest's). */
    private fun seen(color: Color, vision: Vision): Color {
        val m = vision.matrix ?: return color
        fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        fun gamma(c: Double): Float = (if (c <= 0.0031308) c * 12.92 else 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055).toFloat().coerceIn(0f, 1f)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        fun row(i: Int) = (m[i * 3] * r + m[i * 3 + 1] * g + m[i * 3 + 2] * b).coerceIn(0.0, 1.0)
        return Color(gamma(row(0)), gamma(row(1)), gamma(row(2)))
    }

    /** Share of the strip just outside the card's right and bottom edges that is not the ink background. */
    private fun outsideShare(argb: IntArray): Float {
        var lit = 0
        var total = 0
        val ink = Kk.Ink.toArgb()
        for (y in bounds.top.toInt() + 40 until bounds.bottom.toInt() + 14) for (dx in 2 until 12) {
            val edge = bounds.right - 22f * ((y - bounds.top).coerceIn(0f, bounds.height) / bounds.height)
            val x = (edge + dx).toInt().coerceAtMost(359)
            total++
            if (colorDistance(argb[y * 360 + x], ink) > 10) lit++
        }
        return lit.toFloat() / total
    }

    @Test
    fun everyMaterialAndOrnamentDrawsItsOwnLook() {
        val materials = KkCardMaterial.entries.map { material ->
            card(KkCardStyle(material, Kk.EIon, pattern = KkFillKind.ZIGZAG, watermark = KkIcon.ASPECT_ION, seed = 7)).argb().toList()
        }
        assertEquals(materials.size, materials.toSet().size, "each material looks different")
        val ornaments = KkCardOrnament.entries.map { ornament ->
            card(KkCardStyle(KkCardMaterial.IRON, Kk.EThermal, pattern = KkFillKind.SHIMMER, ornament = ornament)).argb().toList()
        }
        assertEquals(ornaments.size, ornaments.toSet().size, "each ornament looks different")
    }

    @Test
    fun theWatermarkKeepsBelowTheText() {
        // Above a measured text bottom the face is the same with or without the watermark, whatever the seed.
        for (seed in 0 until 12) {
            val textBottom = bounds.top + bounds.height * (0.45f + 0.03f * seed)
            fun face(watermark: KkIcon?) = kkRender(360, 500) {
                drawKkCardBack(bounds, 0f, 0f, KkCardStyle(KkCardMaterial.STEEL, Kk.Volt, watermark = watermark, seed = 0x1000 + seed), textBottom)
            }.argb()
            val plain = face(null)
            val marked = face(KkIcon.WEAPONS_GRAVITY_MINES)
            val above = differingShare(plain, marked, 360, Rect(bounds.left, bounds.top + 42f, bounds.right, textBottom))
            assertEquals(0f, above, "seed $seed: the watermark reaches above the text bottom")
            val below = differingShare(plain, marked, 360, Rect(bounds.left, textBottom, bounds.right, bounds.bottom))
            assertTrue(below > 0.01f, "seed $seed: the watermark still shows below the text (${"%.3f".format(below)})")
        }
    }

    @Test
    fun theBumperKeepsClearOfTheTextColumn() {
        for ((width, height) in listOf(300f to 450f, 195f to 254f, 220f to 256f)) {
            val card = Rect(20f, 20f, 20f + width, 20f + height)
            val textLeft = card.left + (if (width < 240f) 22f * width / 220f else 34f)
            fun front(ornament: KkCardOrnament) = kkRender(360, 500) {
                drawKkCardFront(measurer, card, 0f, 0f, null, null, 42f, -1f,
                    KkCardStyle(KkCardMaterial.IRON, Kk.Volt, ornament = ornament, seed = 7), textLeft = textLeft)
            }.argb()
            val plain = front(KkCardOrnament.NONE)
            val bumper = front(KkCardOrnament.BUMPER)
            val beside = differingShare(plain, bumper, 360, Rect(textLeft - 4f, card.top + 42f, card.right - 4f, card.bottom))
            assertEquals(0f, beside, "$width x $height: the bumper reaches into the text column")
            val drawn = differingShare(plain, bumper, 360, Rect(card.left, card.top + 42f, textLeft - 4f, card.bottom))
            assertTrue(drawn > 0.01f, "$width x $height: the bumper still shows in the margin")
        }
    }

    @Test
    fun theSeedAloneMakesAVisiblyDifferentCard() {
        // Same material, hue, pattern, ornament and glyph: the seed moves, sizes, mirrors and turns the
        // watermark, and that difference must show over a real share of the face, not a pixel or two.
        val styles = (0 until 6).map { index ->
            KkCardStyle(KkCardMaterial.STEEL, Kk.Volt, pattern = KkFillKind.SLASH, ornament = KkCardOrnament.BUMPER,
                watermark = KkIcon.SYSTEM_DASH, seed = 0x1000 + index)
        }
        val faces = styles.map { card(it).argb() }
        for (i in faces.indices) for (j in i + 1 until faces.size) {
            val share = differingShare(faces[i], faces[j], 360, Rect(40f, 80f, 300f, 460f))
            assertTrue(share >= 0.015f, "seeds ${styles[i].seed} and ${styles[j].seed} differ over only ${"%.3f".format(share)} of the face")
        }
    }

    /** Share of the pixels in [area] whose colours differ by more than a faint step between [a] and [b]. */
    private fun differingShare(a: IntArray, b: IntArray, width: Int, area: Rect): Float {
        var differing = 0
        var total = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            total++
            if (colorDistance(a[y * width + x], b[y * width + x]) > 6) differing++
        }
        return differing.toFloat() / total
    }

    @Test
    fun metalBandsKeepTheirLabelZoneInTone() {
        // Iron, steel and chrome keep their bevels, lips and horizons outside the band's label zone: inside
        // it every pixel a label could cover reads the material's label at 4.5:1 (no label is drawn here,
        // so the band itself shows), at every band height a card uses.
        for (material in ladder.take(3)) for (height in listOf(24f, 30f, 36f, 42f)) {
            val style = KkCardStyle(material, Kk.EThermal)
            val image = kkRender(360, 500) {
                drawKkCard(measurer, bounds, material.rank, 0f, 0f, null, null, height, style = style)
            }.argb()
            val label = style.bandLabelColors().first.single()
            val center = bounds.top + height * 0.5f
            val left = bounds.left + 18f + 14f
            val right = bounds.right - 34f
            var worst = Float.MAX_VALUE
            for (y in (center - 6f).toInt()..(center + 6f).toInt()) for (x in left.toInt()..right.toInt() step 3) {
                worst = minOf(worst, kkContrast(label, Color(image[y * 360 + x])))
            }
            assertTrue(worst >= KK_CARD_LABEL_CONTRAST, "$material band $height dp: label zone reads only ${"%.2f".format(worst)}:1")
        }
    }

    @Test
    fun bandLabelsReadOnEveryBand() {
        // The label colours come from the material (whatever the hue) and read on every tone under them.
        for (material in KkCardMaterial.entries) {
            val style = KkCardStyle(material, Kk.EGravitic)
            val (start, end) = style.bandLabelColors()
            for (step in 0..10) {
                val leading = KK_CARD_START_LABEL_END * step / 10f
                val trailing = KK_CARD_END_LABEL_START + (0.98f - KK_CARD_END_LABEL_START) * step / 10f
                for (background in style.bandBackgroundsAt(leading)) for (color in start) {
                    assertTrue(kkContrast(color, background) >= KK_CARD_LABEL_CONTRAST, "$material: leading label ${kkContrast(color, background)}")
                }
                for (background in style.bandBackgroundsAt(trailing)) {
                    assertTrue(kkContrast(end, background) >= KK_CARD_LABEL_CONTRAST, "$material: trailing label ${kkContrast(end, background)}")
                }
            }
        }
        // The deep gold's band reads its label too.
        val deep = KkCardStyle(KkCardMaterial.GOLD, Kk.EGravitic, gold = KkGold.Deep)
        val (start, end) = deep.bandLabelColors()
        for (background in deep.bandBackgroundsAt(0.3f)) {
            for (color in start + end) assertTrue(kkContrast(color, background) >= KK_CARD_LABEL_CONTRAST, "deep gold label ${kkContrast(color, background)}")
        }
    }

    @Test
    fun ornamentsKeepClearOfTheRarityFrame() {
        // In the band 6 dp wide just inside the frame's inner seam, along the left, right and bottom
        // edges, an ornament changes at most 5 % of the pixels: it never runs along the frame, so the
        // border stays the rarity's alone.
        for ((width, height) in listOf(300f to 450f, 195f to 254f)) for (material in listOf(KkCardMaterial.IRON, KkCardMaterial.CHROME)) {
            val card = Rect(20f, 20f, 20f + width, 20f + height)
            val k = (width / 300f).coerceIn(0.6f, 1.25f)
            val seam = material.frame.borderDp * maxOf(k, 0.8f) + 1f
            val clearance = KK_ORNAMENT_FRAME_CLEARANCE_DP * k
            val bandBottom = card.top + 42f
            fun front(ornament: KkCardOrnament) = kkRender(360, 500) {
                drawKkCardFront(measurer, card, 0f, 0f, null, null, 42f, -1f,
                    KkCardStyle(material, Kk.Volt, pattern = KkFillKind.SLASH, ornament = ornament, seed = 7), textLeft = card.left + 34f * k)
            }.argb()
            val plain = front(KkCardOrnament.NONE)
            for (ornament in KkCardOrnament.entries.filter { it != KkCardOrnament.NONE }) {
                val marked = front(ornament)
                val shares = FloatArray(3)
                val totals = IntArray(3)
                for (y in bandBottom.toInt() until card.bottom.toInt()) for (x in card.left.toInt() until card.right.toInt()) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val left = card.left + 22f * (1f - (py - card.top) / card.height)
                    val right = card.right - 22f * ((py - card.top) / card.height)
                    val insets = floatArrayOf(px - left, right - px, card.bottom - py)
                    for (edge in 0 until 3) {
                        if (insets[edge] < seam || insets[edge] >= seam + clearance) continue
                        // The bottom band runs between the side bands' outer edges.
                        if (edge == 2 && (px - left < seam || right - px < seam)) continue
                        totals[edge]++
                        if (colorDistance(plain[y * 360 + x], marked[y * 360 + x]) > 6) shares[edge]++
                    }
                }
                for (edge in 0 until 3) {
                    val share = shares[edge] / totals[edge]
                    assertTrue(share <= 0.05f, "$width x $height $material $ornament covers ${"%.3f".format(share)} of the ${listOf("left", "right", "bottom")[edge]} frame band")
                }
                val shown = differingShare(plain, marked, 360, Rect(card.left, bandBottom, card.right, card.bottom)) * card.width * (card.bottom - bandBottom)
                assertTrue(shown > 40f, "$width x $height $material $ornament still shows (${shown.toInt()} px)")
            }
        }
    }

    @Test
    fun contrastHelpersFollowWcag() {
        assertEquals(21f, kkContrast(Color.Black, Color.White), 0.01f)
        assertEquals(1f, kkContrast(Kk.EIon, Kk.EIon), 0.001f)
        val lifted = kkLiftContrast(Color(0xFF646C72), Kk.Ink)
        assertTrue(kkContrast(lifted, Kk.Ink) >= KK_CARD_LABEL_CONTRAST)
    }

    @Test
    fun theNewFillsRepeatSeamlessly() {
        for (kind in listOf(KkFillKind.HAIRLINE, KkFillKind.SLASH, KkFillKind.SCANLINE)) {
            val image = kkRender(216, 144) { drawKkPattern(Rect(0f, 0f, 216f, 144f), kind, Color.White) }
            val tileWidth = Math.round(if (kind == KkFillKind.HAIRLINE) 24f else kind.periodDp * kotlin.math.sqrt(5f))
            val tileHeight = Math.round(if (kind == KkFillKind.HAIRLINE) kind.periodDp else kind.periodDp * kotlin.math.sqrt(5f))
            for (y in listOf(20, 61, 97)) {
                assertEquals((0 until 150).map { image.at(it, y) }, (tileWidth until tileWidth + 150).map { image.at(it, y) }, "$kind along row $y")
            }
            for (x in listOf(13, 50, 111)) {
                assertEquals((0 until 60).map { image.at(x, it) }, (tileHeight until tileHeight + 60).map { image.at(x, it) }, "$kind down column $x")
            }
            val lit = image.argb().count { colorDistance(it, Kk.Ink.toArgb()) > 60 }
            assertTrue(lit in 216 * 144 / 30..216 * 144 / 2, "$kind covers a readable share ($lit px)")
        }
    }

    @Test
    fun aHandOfStyledCardsAllocatesNothingPerFrame() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val hues = listOf(Kk.EThermal, Kk.ECryo, Kk.EIon, Kk.EGravitic, Kk.EEntropy, Kk.Volt)
        // Every material with every ornament, each with its own hue, pattern, watermark and seed, in
        // both golds and every preset's spectrum; every other card hovered (its selection tweening up
        // and down, so its echo grows).
        val styles = KkCardMaterial.entries.flatMap { material ->
            KkCardOrnament.entries.mapIndexed { index, ornament ->
                val hue = hues[(material.ordinal + index) % hues.size]
                KkCardStyle(material, hue, pattern = KkFillKind.entries[KkFillKind.SHIMMER.ordinal + index % 5], ornament = ornament,
                    watermark = KkIcon.Tags[index % KkIcon.Tags.size], seed = 0x1000 + index * 7, bodyEffects = index % 2 == 0,
                    patternBoost = if (index % 3 == 0) 2.2f else 1f, ornamentLeft = index % 4 == 1,
                    gold = if (index % 2 == 0) KkGold.Bright else KkGold.Deep, spectrum = KkSpectrum.all[index % KkSpectrum.all.size])
            }
        }
        val card = Size(200f, 300f)
        val cardBounds = Rect(0f, 0f, card.width, card.height)
        val columns = 11
        val size = Size(card.width * columns, card.height * ((styles.size + columns - 1) / columns))
        val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt())
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val labels = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary")
        fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            val time = index / 60f
            // A 220 ms hover tween at 60 fps, up and back down.
            val phase = (index % 26) / 13f
            val tween = if (phase <= 1f) phase else 2f - phase
            for (i in styles.indices) {
                translate((i % columns) * card.width, (i / columns) * card.height) {
                    val style = styles[i]
                    drawKkCard(measurer, cardBounds, style.material.rank, time, if (i % 2 == 1) tween else 0f,
                        labels[style.material.rank - 1], "Stack 1/2", 30f, burst = (index % 90) / 90f, style = style)
                }
            }
        }
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        // 400 frames (6.7 s) by default sampled every 7th: 7 is coprime with the 26-frame hover tween and the 90-frame
        // burst, so the 58 samples land on every tween step and 58 burst steps across every animated layer's loop.
        // `KK_FULL_SWEEPS=1` draws all 400. Warm at those frames, then measure the same ones; a short pass can catch a
        // stray one-off allocation, so a failing pass is measured again (an allocation every frame fails every pass).
        val frames = if (System.getenv("KK_FULL_SWEEPS") == "1") IntArray(400) { it } else IntArray(58) { it * 7 }
        for (index in frames) { frame(index); empty() }
        val id = Thread.currentThread().id
        var perFrame = Long.MAX_VALUE
        for (attempt in 0 until 3) {
            val emptyStart = threads.getThreadAllocatedBytes(id)
            repeat(frames.size) { empty() }
            val emptyBytes = threads.getThreadAllocatedBytes(id) - emptyStart
            val start = threads.getThreadAllocatedBytes(id)
            for (index in frames) frame(index)
            perFrame = minOf(perFrame, (threads.getThreadAllocatedBytes(id) - start - emptyBytes) / frames.size)
            if (perFrame <= 64) break
        }
        assertTrue(perFrame <= 64, "${styles.size} styled cards allocate $perFrame bytes per frame")
    }

    private fun chroma(color: Color): Float = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)

    private fun hueDegrees(color: Color): Float {
        val high = maxOf(color.red, color.green, color.blue)
        val delta = high - minOf(color.red, color.green, color.blue)
        val sector = when (high) {
            color.red -> ((color.green - color.blue) / delta).let { if (it < 0f) it + 6f else it }
            color.green -> (color.blue - color.red) / delta + 2f
            else -> (color.red - color.green) / delta + 4f
        }
        return sector * 60f
    }
}
