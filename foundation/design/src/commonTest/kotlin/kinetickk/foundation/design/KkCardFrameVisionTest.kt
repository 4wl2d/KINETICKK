// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Rarity, type and threat are separate as each colour-vision preset is seen (CIEDE2000, through the
 * same simulations as [KkHueGrammarTest]); exact colour membership would let a colour one unit off a
 * tone pass, so this compares what is seen.
 *
 * - Every colour a rarity frame paints apart from its spectrum ([KkCardFrame.colors], in the preset's
 *   gold) keeps 12 ΔE from every type tone and from the threat of that preset, so no type mark on a
 *   card's face reads as its frame and no frame reads as a cost.
 * - The prismatic spectrum (the preset's [KkRolePalette.spectrum]: its stops, the blends between
 *   neighbouring stops and its label's lifted ones) keeps 12 ΔE from the preset's threat, its volt
 *   and its gold, so an epic frame never reads as a cost, as Kinetic or as the legendary gold. The
 *   element tones are exempt: the spectrum is every colour at once, never one of them.
 *
 * Exempt, by rule:
 * - the legendary gold beside the Sovereign aspect: one gold, the Rebirth meaning, and a Sovereign
 *   relic never wears the gold frame;
 * - Mono, seen as luminance alone: the metal ladder spans the greys every Mono type tone is, and its
 *   threat and volt are white, so there the type is told by its pattern, a threat by its hatch and
 *   the rarity by its material's richness;
 * - [PlateCuedPairs]: Protan and Deutan see their pale Cryo and slate Ion as near-greys (chroma under
 *   20 as seen), and the steel and chrome metals (and the prismatic rim) are greys from dark to white:
 *   no metal holds 12 ΔE from a near-grey inside its range. Those cards carry their type by a cue that
 *   is not hue (a tinted icon plate with its pattern at full strength and a stronger body pattern),
 *   which the reward card plate test checks as rendered.
 *
 * [PendingFramePairs] lists the pairs a palette change has yet to separate; both lists fail the test
 * when a pair no longer breaks, so the entry is removed and the rule then holds.
 */
class KkCardFrameVisionTest {
    @Test
    fun rarityFramesKeepClearOfEveryTypeToneAndTheThreatAsEachPresetIsSeen() {
        val breaks = KkVisionMode.entries.filter { it != KkVisionMode.MONO }.flatMap(::frameBreaks)
        val fresh = breaks.filter { it.key !in PendingFramePairs && it.key !in PlateCuedPairs }
        assertTrue(fresh.isEmpty(), "rarity frames painting a type tone or the threat:\n" + fresh.joinToString("\n") { it.detail })
        val resolved = (PendingFramePairs + PlateCuedPairs) - breaks.map { it.key }.toSet()
        assertTrue(resolved.isEmpty(), "separated now, remove from PendingFramePairs or PlateCuedPairs: $resolved")
    }

    @Test
    fun theSpectrumKeepsClearOfThreatVoltAndGoldAsEachPresetIsSeen() {
        val breaks = ArrayList<String>()
        for (mode in KkVisionMode.entries.filter { it != KkVisionMode.MONO }) {
            val palette = mode.palette
            val vision = mode.vision()
            val gold = palette.gold
            val reserved = listOf("threat" to palette.threat, "volt" to palette.you, "gold" to gold.base, "gold light" to gold.light,
                "gold seam" to gold.seam, "gold stripe" to gold.stripe, "gold ray" to gold.ray)
            val frame = KkCardMaterial.PRISMATIC.frame(gold, palette.spectrum)
            assertSame(palette.spectrum, frame.prism, "$mode: the prismatic frame paints the preset's spectrum")
            for ((part, stops) in listOf("stops" to palette.spectrum.stops, "label" to frame.label)) {
                for (color in paintedBlends(stops)) {
                    val seen = lab(color, vision)
                    for ((name, tone) in reserved) {
                        val distance = deltaE2000(seen, lab(tone, vision))
                        if (distance < MIN_DELTA_E) breaks += "$mode spectrum $part paints $color, ${tenths(distance)} ΔE from the $name $tone"
                    }
                }
            }
        }
        assertTrue(breaks.isEmpty(), "the spectrum reads as a reserved colour:\n" + breaks.joinToString("\n"))
    }

    @Test
    fun everyPresetPaintsItsOwnSpectrum() {
        // Each preset's spectrum is many colours at once as its viewers see them: at least seven stops,
        // spread in lightness (Protan and Deutan see a blue to yellow axis, Tritan a red to cyan one), and
        // in Mono pale enough that the prismatic frame stays brighter than chrome seen as luminance alone.
        assertEquals(KkSpectrum.all.size, KkSpectrum.all.toSet().size)
        assertEquals(KkSpectrum.all.indices.toList(), KkSpectrum.all.map { it.id })
        assertEquals(KkSpectrum.Full, KkRolePalette.Default.spectrum)
        assertEquals(KkSpectrum.all.size, KkVisionMode.entries.map { it.palette.spectrum }.toSet().size, "one spectrum per preset")
        for (mode in KkVisionMode.entries) {
            val stops = mode.palette.spectrum.stops
            assertTrue(stops.size >= 7, "$mode spectrum has ${stops.size} stops")
            val seen = stops.map { lab(it, mode.vision()) }
            val lightness = seen.maxOf { it.l } - seen.minOf { it.l }
            if (mode == KkVisionMode.MONO) {
                for (blend in paintedBlends(stops)) assertTrue(lab(blend, Vision.LUMINANCE).l >= MONO_MIN_LIGHTNESS, "Mono spectrum blend $blend is too dark")
            } else {
                // Neighbouring stops stand apart as seen, and the loop spans light and dark or many hues.
                val apart = stops.indices.count { deltaE2000(seen[it], seen[(it + 1) % stops.size]) >= MIN_DELTA_E }
                assertTrue(apart >= stops.size / 2, "$mode spectrum: only $apart neighbouring stops stand apart")
                val chromatic = seen.count { it.chroma >= 20.0 }
                assertTrue(lightness >= 25.0 || chromatic >= 5, "$mode spectrum spans ${tenths(lightness)} L* with $chromatic coloured stops")
            }
        }
    }

    @Test
    fun protanAndDeutanTakeTheDeepGoldAndOnlyTheGoldMaterialChanges() {
        for (mode in KkVisionMode.entries) {
            val deep = mode == KkVisionMode.PROTAN || mode == KkVisionMode.DEUTAN
            assertSame(if (deep) KkGold.Deep else KkGold.Bright, mode.palette.gold, "$mode gold")
            for (material in KkCardMaterial.entries) {
                val frame = material.frame(mode.palette.gold, mode.palette.spectrum)
                when (material) {
                    KkCardMaterial.GOLD -> {
                        assertSame(mode.palette.gold, frame.gold, "$mode: the gold material paints the preset's gold")
                        assertTrue(mode.palette.gold.base in frame.colors)
                    }
                    KkCardMaterial.PRISMATIC -> assertSame(mode.palette.spectrum, frame.prism, "$mode: the prismatic material paints the preset's spectrum")
                    else -> assertSame(material.frame, frame, "$mode $material: no gold or spectrum reaches another material")
                }
            }
        }
        assertNotEquals(KkCardMaterial.GOLD.frame(KkGold.Deep).colors, KkCardMaterial.GOLD.frame.colors)
        // The Sovereign aspect is the bright gold itself: the one pair the frame test allows.
        assertEquals(KkGold.Bright.base, Kk.ASovereign)
    }

    private class Break(val key: String, val detail: String)

    private fun frameBreaks(mode: KkVisionMode): List<Break> {
        val palette = mode.palette
        val vision = mode.vision()
        val tones = (0..5).map { ToneNames[it] to palette.tone(it) } + ("sovereign" to Kk.ASovereign) + ("threat" to palette.threat)
        val breaks = mutableListOf<Break>()
        for (material in KkCardMaterial.entries) {
            val frame = material.frame(palette.gold, palette.spectrum)
            for ((name, tone) in tones) {
                if (material == KkCardMaterial.GOLD && name == "sovereign" && palette.gold === KkGold.Bright) continue
                val seenTone = lab(tone, vision)
                var nearest = Double.MAX_VALUE
                var culprit = Color.Unspecified
                for (color in frame.colors) {
                    // The spectrum has its own rule (theSpectrumKeepsClearOfThreatVoltAndGoldAsEachPresetIsSeen).
                    if (color in frame.spectrum) continue
                    val distance = deltaE2000(lab(color, vision), seenTone)
                    if (distance < nearest) {
                        nearest = distance
                        culprit = color
                    }
                }
                if (nearest < MIN_DELTA_E) {
                    breaks += Break("$mode $material $name", "$mode $material paints $culprit, ${tenths(nearest)} ΔE from the $name tone $tone")
                }
            }
        }
        return breaks
    }

    private companion object {
        const val MIN_DELTA_E = 12.0

        /** The lightest a Mono spectrum blend may be seen at L* (luminance alone): above every chrome band tone's middle. */
        const val MONO_MIN_LIGHTNESS = 78.0
        val ToneNames = listOf("kinetic", "thermal", "cryo", "ion", "gravitic", "entropy")

        /** Every colour a gradient through [stops] (a closed loop) paints, in quarter steps between neighbours. */
        fun paintedBlends(stops: List<Color>): List<Color> = stops.indices.flatMap { index ->
            val next = stops[(index + 1) % stops.size]
            listOf(stops[index], kkMix(stops[index], next, 0.25f), kkMix(stops[index], next, 0.5f), kkMix(stops[index], next, 0.75f))
        }

        /** Near-grey type tones on the metal frames, carried by the plate cue instead of hue (see the class doc). */
        val PlateCuedPairs = setOf(
            "PROTAN STEEL cryo", "PROTAN STEEL ion", "PROTAN CHROME cryo", "PROTAN CHROME ion",
            "DEUTAN STEEL cryo", "DEUTAN STEEL ion", "DEUTAN CHROME cryo", "DEUTAN CHROME ion",
        )

        /** Pairs a palette change has yet to separate. */
        val PendingFramePairs = setOf(
            // The white glint every polished metal sweeps (chrome's too), over the deep gold.
            "PROTAN GOLD cryo",
        )

        fun tenths(value: Double): String = (round(value * 10.0) / 10.0).toString()
    }
}
