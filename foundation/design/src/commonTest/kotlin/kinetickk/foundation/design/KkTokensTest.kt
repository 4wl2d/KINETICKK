// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun rarityColorsFollowRankOrderAndClamp() {
        val expected = listOf(0xFFC9C8BE, 0xFF5CF2A6, 0xFF4DA6FF, 0xFFB777FF, 0xFFFFB627)
        expected.forEachIndexed { index, argb ->
            assertEquals(Color(argb), Kk.rarity(index + 1), "rank ${index + 1}")
            assertEquals(Color(argb), Kk.Rarities[index])
        }
        assertEquals(Kk.RCommon, Kk.rarity(0))
        assertEquals(Kk.RLegend, Kk.rarity(9))
    }

    @Test
    fun aspectColorsFollowRelicAspectOrder() {
        // vector, gravitic, ion, rift, prism, entropy, sovereign
        val expected = listOf(0xFFD8FF3E, 0xFF9B7BFF, 0xFF45E0FF, 0xFFFF4FA3, 0xFFE4F1FF, 0xFFFF6A2B, 0xFFFFC93C)
        assertEquals(7, Kk.Aspects.size)
        expected.forEachIndexed { index, argb ->
            assertEquals(Color(argb), Kk.aspect(index), "aspect $index")
            assertEquals(Color(argb), Kk.Aspects[index])
        }
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
            listOf(0xFFD8FF3E, 0xFFFF8A1F, 0xFF45E0FF, 0xFFFF4FA3, 0xFFA98BFF, 0xFF5CF2A6, 0xFFA3A298).map(::Color),
            KkHomePalette.entries.map { it.accent },
        )
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
        assertEquals(0f, KkDeal.progress(89f, 1))
        assertTrue(KkDeal.progress(200f, 1) > 0f)
        assertEquals(1f, KkEase.Pull.transform(1f), 0.001f)
        assertTrue((1..99).any { KkEase.Pull.transform(it / 100f) > 1f }, "Pull overshoots")
        assertTrue((1..99).none { KkEase.Out.transform(it / 100f) > 1.0001f }, "Out never bounces")
        assertEquals(-12f, KkShape.Shear)
        assertEquals(-27.5f, KkShape.Slash)
    }
}
