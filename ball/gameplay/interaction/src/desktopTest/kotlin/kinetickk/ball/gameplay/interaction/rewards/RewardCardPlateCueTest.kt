// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVisionMode
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Protan and Deutan see their pale Cryo and slate Ion as near-greys beside the steel and chrome
 * frames (the pairs foundation's KkCardFrameVisionTest leaves to this cue): those cards carry their
 * type by a cue that is not hue. Rendered as the real card, the icon plate is tinted in the hue (a
 * dark patch, visibly not the plain plate) and that tint with the plate's pattern stands 12 ΔE or
 * more, as each preset is seen, from the frame's border and from its band; the body pattern is drawn
 * at its strongest. The cue is drawn for those hues and not for a hue that tells itself apart.
 *
 * The glyph is left out of the measure: a pale Cryo glyph is the hue itself, as near the light metal
 * as the hue is, and the plate around it is what sets the type apart.
 */
@OptIn(ExperimentalTestApi::class)
class RewardCardPlateCueTest {
    private val content = shippedRewardContent

    @Test
    fun nearGreyTypesStandApartFromTheMetalFrameByTheirPlate() {
        val modes = listOf(KkVisionMode.PROTAN, KkVisionMode.DEUTAN)
        val elements = listOf(StatusElement.CRYO, StatusElement.ION)
        // The metal frames whose greys hold these near-greys (iron keeps 12 ΔE from them by its own tones).
        val rarities = listOf(ItemRarity.UNCOMMON, ItemRarity.RARE)
        val items = elements.flatMap { element ->
            rarities.mapNotNull { rarity -> content.items.firstOrNull { it.rarity == rarity && it.elements.firstOrNull() == element } }
        }
        val metals = items.map { KkCardMaterial.ofRank(it.rarity.rank) }.toSet()
        assertTrue(KkCardMaterial.STEEL in metals && KkCardMaterial.CHROME in metals, "Cryo and Ion items on steel and chrome: ${items.map { it.name }}")
        val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel.uppercase(), itemId = it.id) }
        val model = rewardFixtureModel(content = content, choices = choices, discoveredItems = content.items.map { it.id }.toSet())
        val cards = choices.mapIndexed { index, choice -> model.rewardCardPresentation(choice, index, AppLanguage.English) }
        val failures = ArrayList<String>()
        for (mode in modes) {
            val palette = mode.palette
            for (card in cards) {
                val look = buildRewardCardLook(card, palette)
                assertTrue(look.plateCue, "${card.title} in $mode carries the plate cue")
                assertTrue(look.style.patternBoost >= 4f, "${card.title} in $mode draws its body pattern at its strongest (${look.style.patternBoost})")
            }
            val pixels = render(cards, palette)
            cards.forEachIndexed { index, card ->
                val left = (index % Columns) * Cell + Pad
                val top = (index / Columns) * Cell + Pad
                val material = KkCardMaterial.ofRank(card.rank)
                val plate = mean(pixels) { visit ->
                    // The plate's two square corners inside its rim (the slab cuts the other two), clear
                    // of the glyph: its tint under its pattern.
                    for (y in 61 until 75) for (x in 103 until 117) visit(left + x, top + y)
                    for (y in 125 until 139) for (x in 39 until 53) visit(left + x, top + y)
                }
                val border = mean(pixels) { visit ->
                    // The middle of the border on both slanted sides.
                    val width = material.frame.borderDp
                    for (y in 60 until 440) {
                        val inset = width * 0.5f * (1f - 22f / 450f)
                        visit(left + floor(22f * (1f - y / 450f) + inset).toInt(), top + y)
                        visit(left + floor(300f - 22f * (y / 450f) - inset).toInt(), top + y)
                    }
                }
                val band = mean(pixels) { visit -> for (y in 8 until 34) for (x in 40 until 150) visit(left + x, top + y) }
                val vision = mode.seen()
                for ((part, frame) in listOf("border" to border, "band" to band)) {
                    val distance = deltaE(lab(plate, vision), lab(frame, vision))
                    if (distance < MinDeltaE) failures += "${card.title} ($material) in $mode: plate ${hex(plate)} is ${"%.1f".format(distance)} ΔE from the $part ${hex(frame)}"
                }
                // The plate is visibly tinted, not the plain ink plate.
                val plain = DoubleArray(3) { linear(listOf(Kk.Ink3.red, Kk.Ink3.green, Kk.Ink3.blue)[it]) }
                val tint = deltaE(lab(plate, vision), lab(plain, vision))
                if (tint < MinTintDeltaE) failures += "${card.title} ($material) in $mode: plate ${hex(plate)} is only ${"%.1f".format(tint)} ΔE from the plain plate"
            }
        }
        assertTrue(failures.isEmpty(), "plates that read as the frame:\n" + failures.joinToString("\n"))
        // A hue that tells itself apart draws the plain plate.
        val ion = cards.first { it.item!!.elements.first() == StatusElement.ION }
        assertTrue(!buildRewardCardLook(ion, KkRolePalette.Default).plateCue, "Default Ion keeps the plain plate")
    }

    private fun render(cards: List<RewardCardPresentation>, palette: KkRolePalette): PixelMap {
        val rows = (cards.size + Columns - 1) / Columns
        val width = Columns * Cell
        val height = rows * Cell
        var pixels: PixelMap? = null
        runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English, LocalKkRolePalette provides palette) {
                    Box(Modifier.requiredSize(width.dp, height.dp).background(Kk.Ink)) {
                        cards.forEachIndexed { index, card ->
                            Box(Modifier.offset(((index % Columns) * Cell).dp, ((index / Columns) * Cell).dp).requiredSize(Cell.dp).clipToBounds()) {
                                RewardCard(card, index, overlayUiTextScale(1.25f), 0f, true,
                                    Modifier.offset(Pad.dp, Pad.dp).requiredSize(300.dp, 450.dp),
                                    time = remember { mutableFloatStateOf(2.3f) }) {}
                            }
                        }
                    }
                }
            }
            waitForIdle()
            pixels = onRoot().captureToImage().toPixelMap()
        }
        return pixels!!
    }

    /** The mean colour (in linear light) of the pixels [area] visits. */
    private fun mean(pixels: PixelMap, area: ((Int, Int) -> Unit) -> Unit): DoubleArray {
        val sum = DoubleArray(3)
        var count = 0
        area { x, y ->
            val color = pixels[x, y]
            sum[0] += linear(color.red)
            sum[1] += linear(color.green)
            sum[2] += linear(color.blue)
            count++
        }
        return DoubleArray(3) { sum[it] / count }
    }

    private companion object {
        const val Pad = 18
        const val Cell = 486
        const val Columns = 3
        const val MinDeltaE = 12.0
        const val MinTintDeltaE = 5.0

        /** The Machado (2009) full-severity simulations, as foundation's KkHueGrammarTest sees each preset. */
        fun KkVisionMode.seen(): DoubleArray? = when (this) {
            KkVisionMode.PROTAN -> doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)
            KkVisionMode.DEUTAN -> doubleArrayOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)
            else -> null
        }

        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        fun hex(rgb: DoubleArray): String = "#" + rgb.joinToString("") {
            val c = if (it <= 0.0031308) it * 12.92 else 1.055 * it.pow(1.0 / 2.4) - 0.055
            "%02X".format((c * 255.0).let(::round).toInt().coerceIn(0, 255))
        }

        /** CIELAB (D65) of the linear [rgb] as [matrix] sees it. */
        fun lab(rgb: DoubleArray, matrix: DoubleArray?): DoubleArray {
            val seen = matrix?.let { m -> DoubleArray(3) { row -> (m[row * 3] * rgb[0] + m[row * 3 + 1] * rgb[1] + m[row * 3 + 2] * rgb[2]).coerceIn(0.0, 1.0) } } ?: rgb
            val x = (0.4124564 * seen[0] + 0.3575761 * seen[1] + 0.1804375 * seen[2]) / 0.95047
            val y = 0.2126729 * seen[0] + 0.7151522 * seen[1] + 0.0721750 * seen[2]
            val z = (0.0193339 * seen[0] + 0.1191920 * seen[1] + 0.9503041 * seen[2]) / 1.08883
            fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
            return doubleArrayOf(116.0 * f(y) - 16.0, 500.0 * (f(x) - f(y)), 200.0 * (f(y) - f(z)))
        }

        /** CIEDE2000 (Sharma, Wu, Dalal 2005). */
        fun deltaE(first: DoubleArray, second: DoubleArray): Double {
            fun rad(degrees: Double) = degrees * PI / 180.0
            fun hue(b: Double, a: Double): Double = if (a == 0.0 && b == 0.0) 0.0 else (atan2(b, a) * 180.0 / PI + 360.0) % 360.0
            val chromaMean = (sqrt(first[1] * first[1] + first[2] * first[2]) + sqrt(second[1] * second[1] + second[2] * second[2])) / 2.0
            val g = 0.5 * (1.0 - sqrt(chromaMean.pow(7) / (chromaMean.pow(7) + 25.0.pow(7))))
            val a1 = (1.0 + g) * first[1]
            val a2 = (1.0 + g) * second[1]
            val c1 = sqrt(a1 * a1 + first[2] * first[2])
            val c2 = sqrt(a2 * a2 + second[2] * second[2])
            val h1 = hue(first[2], a1)
            val h2 = hue(second[2], a2)
            val deltaHue = when {
                c1 * c2 == 0.0 -> 0.0
                abs(h2 - h1) <= 180.0 -> h2 - h1
                h2 - h1 > 180.0 -> h2 - h1 - 360.0
                else -> h2 - h1 + 360.0
            }
            val deltaH = 2.0 * sqrt(c1 * c2) * sin(rad(deltaHue / 2.0))
            val lMean = (first[0] + second[0]) / 2.0
            val cMean = (c1 + c2) / 2.0
            val hMean = when {
                c1 * c2 == 0.0 -> h1 + h2
                abs(h1 - h2) <= 180.0 -> (h1 + h2) / 2.0
                h1 + h2 < 360.0 -> (h1 + h2 + 360.0) / 2.0
                else -> (h1 + h2 - 360.0) / 2.0
            }
            val t = 1.0 - 0.17 * cos(rad(hMean - 30.0)) + 0.24 * cos(rad(2.0 * hMean)) +
                0.32 * cos(rad(3.0 * hMean + 6.0)) - 0.20 * cos(rad(4.0 * hMean - 63.0))
            val rc = 2.0 * sqrt(cMean.pow(7) / (cMean.pow(7) + 25.0.pow(7)))
            val rt = -sin(rad(2.0 * 30.0 * exp(-((hMean - 275.0) / 25.0).pow(2)))) * rc
            val l = (second[0] - first[0]) / (1.0 + 0.015 * (lMean - 50.0).pow(2) / sqrt(20.0 + (lMean - 50.0).pow(2)))
            val c = (c2 - c1) / (1.0 + 0.045 * cMean)
            val h = deltaH / (1.0 + 0.015 * cMean * t)
            return sqrt(l * l + c * c + h * h + rt * c * h)
        }
    }
}
