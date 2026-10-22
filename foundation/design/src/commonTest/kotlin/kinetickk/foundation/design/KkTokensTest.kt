// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KkTokensTest {
    @Test
    fun levelBadgeTierChangesExactlyAtEachDecade() {
        val cases = listOf(
            0 to KkLevelTier.T1, 1 to KkLevelTier.T1, 9 to KkLevelTier.T1,
            10 to KkLevelTier.T2, 19 to KkLevelTier.T2,
            20 to KkLevelTier.T3, 29 to KkLevelTier.T3,
            30 to KkLevelTier.T4, 39 to KkLevelTier.T4,
            40 to KkLevelTier.T5, 41 to KkLevelTier.T5, 250 to KkLevelTier.T5,
        )
        cases.forEach { (level, tier) -> assertEquals(tier, KkLevelTier.of(level), "level $level") }
    }

    @Test
    fun levelBadgeTierReachesApexExactlyAtTheCap() {
        // With the content cap 42: 40–41 stay T5, the cap and anything past it is T6.
        val capped = listOf(
            1 to KkLevelTier.T1, 9 to KkLevelTier.T1, 10 to KkLevelTier.T2, 29 to KkLevelTier.T3, 39 to KkLevelTier.T4,
            40 to KkLevelTier.T5, 41 to KkLevelTier.T5, 42 to KkLevelTier.T6, 250 to KkLevelTier.T6,
        )
        capped.forEach { (level, tier) -> assertEquals(tier, KkLevelTier.of(level, 42), "level $level of 42") }
        // Without a cap (the component sheet, screens that show no run) nothing reaches T6.
        assertEquals(KkLevelTier.T5, KkLevelTier.of(41))
        assertEquals(KkLevelTier.T5, KkLevelTier.of(250))
        assertEquals(KkLevelTier.T5, KkLevelTier.of(Int.MAX_VALUE - 1))
        // A lower cap takes precedence over the decades.
        assertEquals(KkLevelTier.T6, KkLevelTier.of(30, 30))
        assertEquals(KkLevelTier.T3, KkLevelTier.of(29, 30))
    }

    @Test
    fun rarityColorsAreMaterialInRankOrderAndClamp() {
        // Material ramp: graphite, bone-grey, bone (sheen), bone (chrome), gold foil.
        val expected = listOf(0xFF86857B, 0xFFC9C8BE, 0xFFF1F0E8, 0xFFF1F0E8, 0xFFFFC93C)
        assertEquals(5, Kk.Rarities.size)
        expected.forEachIndexed { index, argb ->
            assertEquals(Color(argb), Kk.rarity(index + 1), "rank ${index + 1}")
            assertEquals(Color(argb), Kk.Rarities[index])
        }
        assertEquals(Kk.RCommon, Kk.rarity(0))
        assertEquals(Kk.RCommon, Kk.rarity(-4))
        assertEquals(Kk.RLegend, Kk.rarity(9))
        // Gold means Rebirth and legendary; hue belongs to the elements, never to rarity.
        assertEquals(Kk.Gold, Kk.RLegend)
        assertTrue(Kk.Elements.none { it in Kk.Rarities }, "no element hue doubles as a rarity color")
    }

    @Test
    fun elementColorsAndDamageTonesShareOneHuePerElement() {
        // thermal, cryo, ion, gravitic, entropy, sonic, void
        val expected = listOf(0xFFFF8A1F, 0xFFBDEBFF, 0xFF4F7DFF, 0xFFD24CFF, 0xFF46D96A, 0xFF009C84, 0xFFCCC0FC).map(::Color)
        assertEquals(expected, Kk.Elements)
        assertEquals(listOf(Kk.EThermal, Kk.ECryo, Kk.EIon, Kk.EGravitic, Kk.EEntropy, Kk.ESonic, Kk.EVoid), Kk.Elements)
        assertEquals(7, Kk.Elements.toSet().size, "seven distinct element hues")
        expected.forEachIndexed { index, color -> assertEquals(color, Kk.element(index), "element $index") }
        assertEquals(Kk.EThermal, Kk.element(-1), "clamps low")
        assertEquals(Kk.EVoid, Kk.element(9), "clamps high")
        // Thermal is the heat hue: your heat and their burning are one idea.
        assertEquals(Kk.Heat, Kk.EThermal)
        // Damage tones: 0 kinetic (volt, the player color: movement is your weapon), 1..7 the elements,
        // clamped on both ends. Bone stays the neutral text color, never a damage tone.
        assertEquals(Color(0xFFD8FF3E), Kk.EKinetic)
        assertEquals(Kk.Volt, Kk.tone(0))
        assertNotEquals(Kk.Bone, Kk.tone(0), "bone is text, not Kinetic")
        assertEquals(Kk.EKinetic, Kk.tone(-3))
        (1..7).forEach { assertEquals(Kk.element(it - 1), Kk.tone(it), "tone $it") }
        assertEquals(Kk.EVoid, Kk.tone(8))
        assertFalse(Kk.EKinetic in Kk.Elements, "kinetic is not a status element")
    }

    @Test
    fun aspectColorsMirrorTheElementsInRelicAspectOrder() {
        // kinetic, thermal, cryo, ion, gravitic, entropy, sonic, void, sovereign
        val expected = listOf(0xFFD8FF3E, 0xFFFF8A1F, 0xFFBDEBFF, 0xFF4F7DFF, 0xFFD24CFF, 0xFF46D96A, 0xFF009C84, 0xFFCCC0FC, 0xFFFFC93C)
        assertEquals(9, Kk.Aspects.size)
        expected.forEachIndexed { index, argb ->
            assertEquals(Color(argb), Kk.aspect(index), "aspect $index")
            assertEquals(Color(argb), Kk.Aspects[index])
        }
        assertEquals(
            listOf(Kk.AKinetic, Kk.AThermal, Kk.ACryo, Kk.AIon, Kk.AGravitic, Kk.AEntropy, Kk.ASonic, Kk.AVoid, Kk.ASovereign),
            Kk.Aspects,
        )
        assertEquals(Kk.Elements, Kk.Aspects.subList(1, 8), "elemental aspects are the element hues")
        (0..7).forEach { assertEquals(Kk.tone(it), Kk.aspect(it), "aspect $it is damage tone $it") }
        assertEquals(Kk.Gold, Kk.ASovereign)
        assertEquals(Kk.AKinetic, Kk.aspect(-1))
        assertEquals(Kk.ASovereign, Kk.aspect(12))
    }

    @Test
    fun colorVisionPalettesMatchTheHandoffTokens() {
        fun hex(color: Color) = (color.toArgb().toLong() and 0xFFFFFFFFL).toString(16).uppercase()
        val expected = mapOf(
            KkVisionMode.DEFAULT to listOf("FFD8FF3E", "FFFF3B6B", "FFFF8A1F", "FF45E0FF", "FFA98BFF"),
            KkVisionMode.PROTAN to listOf("FFFFE14D", "FF3D8BFF", "FFFFB000", "FF9AD0FF", "FFE0E0E0"),
            KkVisionMode.DEUTAN to listOf("FFFFD84A", "FF2F6BFF", "FFFF9E1A", "FFA6D8FF", "FFD6D6D6"),
            KkVisionMode.TRITAN to listOf("FF5CF2E0", "FFFF3B3B", "FFFF7A9A", "FFE4E4E4", "FFFF9ED8"),
            KkVisionMode.MONO to listOf("FFFFFFFF", "FFFFFFFF", "FFBDBDBD", "FF8A8A8A", "FFD9D9D9"),
        )
        assertEquals(KkVisionMode.entries.toSet(), expected.keys)
        expected.forEach { (mode, colors) ->
            val palette = mode.palette
            assertEquals(colors, listOf(palette.you, palette.threat, palette.heat, palette.shield, palette.pol).map(::hex), "$mode")
            assertEquals(mode == KkVisionMode.MONO, palette.hatchThreats, "$mode hatch")
        }
        assertEquals(KkRolePalette.Default, KkVisionMode.DEFAULT.palette)
        assertEquals(Kk.Volt, KkRolePalette.Default.you)
        assertEquals(Kk.Hazard, KkRolePalette.Default.threat)
    }

    @Test
    fun everyColorVisionPaletteDefinesSevenDistinctElementColors() {
        assertEquals(Kk.Elements, KkRolePalette.Default.elements)
        KkVisionMode.entries.forEach { mode ->
            val palette = mode.palette
            assertEquals(7, palette.elements.size, "$mode element count")
            assertEquals(7, palette.elements.toSet().size, "$mode element colors are distinct")
            // Thermal stays on the preset's heat hue in every mode.
            assertEquals(palette.heat, palette.element(0), "$mode thermal is heat")
            palette.elements.forEachIndexed { index, color -> assertEquals(color, palette.element(index), "$mode element $index") }
            assertEquals(palette.elements.first(), palette.element(-2), "$mode clamps low")
            assertEquals(palette.elements.last(), palette.element(9), "$mode clamps high")
            // Kinetic is the preset's own volt, and a screen that re-tints `you` keeps the player's Kinetic.
            assertEquals(palette.you, palette.kinetic, "$mode kinetic is its volt")
            assertEquals(palette.you, palette.tone(0), "$mode kinetic tone")
            assertEquals(palette.you, palette.tone(-1), "$mode clamps to kinetic")
            assertEquals(palette.you, palette.copy(you = Kk.Gold).tone(0), "$mode re-tinted you keeps Kinetic")
            (1..7).forEach { assertEquals(palette.element(it - 1), palette.tone(it), "$mode tone $it") }
            // Damage numbers carry hue alone, so kinetic joins the set; KkHueGrammarTest checks the distances.
            assertEquals(8, (0..7).map(palette::tone).toSet().size, "$mode damage tones are distinct")
        }
        // Presets replace the hue wheel; only Default uses the full-saturation element hues.
        KkVisionMode.entries.filter { it != KkVisionMode.DEFAULT }.forEach { mode ->
            assertNotEquals(Kk.Elements, mode.palette.elements, "$mode has its own ramp")
        }
    }

    @Test
    fun mixInterpolatesEachSrgbChannel() {
        assertEquals(Kk.Ink, kkMix(Kk.Ink, Kk.Volt, 0f))
        assertEquals(Kk.Volt, kkMix(Kk.Ink, Kk.Volt, 1f))
        assertEquals(Kk.Volt, kkMix(Kk.Ink, Kk.Volt, 3f), "fraction is clamped")
        val mid = kkMix(Color.Black, Color.White, 0.5f)
        listOf(mid.red, mid.green, mid.blue).forEach { assertTrue(abs(it - 0.5f) < 0.004f, "channel $it") }
        // Home background: accent 7 %, ink 93 %.
        val home = KkHomePalette.START.background
        assertTrue(abs(home.red - (Kk.Ink.red * 0.93f + Kk.Volt.red * 0.07f)) < 0.004f)
        assertTrue(abs(home.blue - (Kk.Ink.blue * 0.93f + Kk.Volt.blue * 0.07f)) < 0.004f)
    }

    @Test
    fun homePalettesAndRebirthTiersAreCompleteAndOrdered() {
        assertEquals(
            listOf(0xFFD8FF3E, 0xFFBDE82A, 0xFFFFC93C, 0xFFBDEBFF, 0xFFA3A298, 0xFF86857B).map(::Color),
            KkHomePalette.entries.map { it.accent },
        )
        // Each accent names what the destination means: you, the Workshop's deeper volt (never START's
        // volt), Rebirth gold.
        assertEquals(Kk.Volt, KkHomePalette.START.accent)
        assertEquals(Kk.Volt2, KkHomePalette.WORKSHOP.accent)
        assertNotEquals(KkHomePalette.START.accent, KkHomePalette.WORKSHOP.accent)
        assertEquals(Kk.Gold, KkHomePalette.REBIRTH.accent)
        assertTrue(KkHomePalette.CODEX.accent in Kk.Elements, "the codex accent rests on an element hue")
        assertEquals(KkHomePalette.entries.size, KkHomePalette.entries.map { it.accent }.toSet().size, "accents are distinct")
        assertEquals(11, KkRebirthTiers.colors.size)
        assertEquals(Color(0xFFD8FF3E), KkRebirthTiers.color(0))
        assertEquals(Color(0xFFFFB627), KkRebirthTiers.color(1))
        assertEquals(Color(0xFFBDEBFF), KkRebirthTiers.color(9))
        assertEquals(Color.White, KkRebirthTiers.color(10))
        assertEquals(Color.White, KkRebirthTiers.color(99), "tiers clamp to 10")
        assertEquals(Color.Black, KkRebirthTiers.background(10))
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 0), (0..10).map(KkRebirthTiers::ringCount))
        assertFalse(KkRebirthTiers.hasHazardBand(4))
        assertTrue(KkRebirthTiers.hasHazardBand(5))
    }

    @Test
    fun motionConstantsMatchTheMotionBoard() {
        assertEquals(420, KkShutter.SLAB_MS)
        assertEquals(540, KkShutter.TOTAL_MS)
        assertTrue(KkShutter.covered(KkShutter.SWAP_MS.toFloat()))
        assertFalse(KkShutter.covered(0f))
        assertFalse(KkShutter.covered(KkShutter.TOTAL_MS.toFloat()))
        assertEquals(1.3f, KkSlam.scale(0f))
        assertEquals(1f, KkSlam.scale(1f))
        assertTrue((1..99).any { KkSlam.scale(it / 100f) < 1f }, "Pull overshoots past the resting scale")
        // The Apex unlock slams from 1.6 with an 8° kick; the cast kick grows from 0.92 without one.
        assertEquals(1.6f, KkSlam.scale(0f, from = 1.6f))
        assertEquals(1f, KkSlam.scale(1f, from = 1.6f))
        assertEquals(0.92f, KkSlam.scale(0f, from = 0.92f))
        assertEquals(-8f, KkSlam.rotation(0f, 0f, kick = 8f))
        assertEquals(0f, KkSlam.rotation(1f, 0f, kick = 8f))
        assertEquals(-8f, KkSlam.rotation(0f, -3f), "the default kick stays 5°")
        assertEquals(600, KkTime.TierBurst)
        assertEquals(1_200, KkTime.ApexUnlock)
        assertEquals(420, KkTime.ApexPing)
        assertEquals(0f, KkDeal.progress(89f, 1))
        assertTrue(KkDeal.progress(200f, 1) > 0f)
        assertEquals(1f, KkEase.Pull.transform(1f), 0.001f)
        assertTrue((1..99).any { KkEase.Pull.transform(it / 100f) > 1f }, "Pull overshoots")
        assertTrue((1..99).none { KkEase.Out.transform(it / 100f) > 1.0001f }, "Out never bounces")
        assertEquals(-12f, KkShape.Shear)
        assertEquals(-27.5f, KkShape.Slash)
    }
}
