// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The level badge's colours as each colour-vision preset's viewers see them (CIEDE2000; Machado 2009
 * full severity; Mono = luminance), with the maths of [KkHueGrammarTest]: the APEX gold reads apart
 * from the volt and the threat, the ink seam separates every face from its echo without hue, and the
 * foil numeral on the ink face stays legible.
 */
class KkLevelBadgeVisionTest {
    @Test
    fun apexGoldStaysApartFromTheVoltAndTheThreatInEveryPreset() {
        KkVisionMode.entries.forEach { mode ->
            val roles = mode.palette
            val vision = mode.vision()
            val gold = lab(roles.gold.base, vision)
            // T6 frame against T4's volt inset (both badges have dark faces: told apart by the frame).
            assertTrue(deltaE2000(gold, lab(roles.you, vision)) >= MIN_GOLD_DELTA, "$mode: APEX gold vs volt")
            // T6 never reads as danger.
            assertTrue(deltaE2000(gold, lab(roles.threat, vision)) >= MIN_GOLD_DELTA, "$mode: APEX gold vs threat")
        }
    }

    @Test
    fun theInkSeamSeparatesEveryFaceWithoutHue() {
        KkVisionMode.entries.forEach { mode ->
            val roles = mode.palette
            val vision = mode.vision()
            val seam = lab(Kk.Ink, vision)
            // T2 bone, T3 volt, T5 foil and the T6 frame (the preset's gold).
            listOf("bone" to Kk.Bone, "volt" to roles.you, "gold" to roles.gold.base).forEach { (name, face) ->
                assertTrue(deltaE2000(seam, lab(face, vision)) >= MIN_SEAM_DELTA, "$mode: ink seam vs $name face")
            }
        }
    }

    @Test
    fun theApexNumeralReadsOnItsInkFace() {
        KkVisionMode.entries.forEach { mode ->
            val gold = mode.palette.gold
            assertTrue(wcagContrast(gold.base, Kk.Ink) >= MIN_NUMERAL_CONTRAST, "$mode: foil base on ink")
            assertTrue(wcagContrast(gold.light, Kk.Ink) >= MIN_NUMERAL_CONTRAST, "$mode: foil light on ink")
        }
    }

    @Test
    fun protanAndDeutanBadgesUseTheDeepGold() {
        // The bright gold is within a few ΔE of those presets' yellow volt, so T5/T6 read roles.gold.
        assertEquals(KkGold.Deep, KkRolePalette.Protan.gold)
        assertEquals(KkGold.Deep, KkRolePalette.Deutan.gold)
        listOf(KkVisionMode.PROTAN, KkVisionMode.DEUTAN).forEach { mode ->
            val vision = mode.vision()
            val bright = deltaE2000(lab(KkGold.Bright.base, vision), lab(mode.palette.you, vision))
            val deep = deltaE2000(lab(KkGold.Deep.base, vision), lab(mode.palette.you, vision))
            assertTrue(bright < MIN_GOLD_DELTA && deep >= MIN_GOLD_DELTA, "$mode: bright gold $bright, deep gold $deep vs volt")
        }
    }

    private companion object {
        const val MIN_GOLD_DELTA = 9.5
        const val MIN_SEAM_DELTA = 40.0
        const val MIN_NUMERAL_CONTRAST = 4.5
    }
}
