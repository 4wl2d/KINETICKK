// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kotlin.math.min

// The rarity channel of a card: its material frame. Nothing here reads a card's hue (the type
// channel); every colour comes from the material's [KkCardFrame].

/**
 * Card material: the rarity channel. It owns the card frame (the border, the band and its label,
 * the echo slabs, the inner glow and outer halo, the sheen, halftone and sparks, the deal burst)
 * and paints it in its own metal from [frame], never in the card's [KkCardStyle.hue]: the hue
 * owns the face. The ladder rises in brightness and richness: iron, steel, chrome, prismatic, gold.
 */
enum class KkCardMaterial(val rank: Int) {
    /** A deliberately calm card (a decline): an ink band, a muted label, no frame and no effects. */
    CALM(1),

    /** Common: a dark iron frame (a 2 dp mid-grey border), a matte iron band, no glow. */
    IRON(1),

    /** Uncommon: a lighter steel frame, a brushed steel band with a lit bevel, a soft cool-white inner glow. */
    STEEL(2),

    /**
     * Rare: a bright chrome frame with a glint running round it and a soft white halo, a polished
     * chrome band with a specular line, the sheen sweep over band and face, one chrome echo.
     */
    CHROME(3),

    /**
     * Epic: a prismatic frame whose spectrum (the preset's own, [KkRolePalette.spectrum], never led
     * by a hue) turns slowly round the border with a bright bone rim inside it, a spectrum halo and
     * inner glow, a black glass band with a sliding prismatic edge and label,
     * facets a glint travels along, a rising spectrum halftone with spectrum sparks and a double echo
     * (spectrum and a bone rim). Its border, rim and echoes outshine chrome in lightness as well as
     * in colour.
     */
    PRISMATIC(4),

    /**
     * Legendary: a gold foil frame and band, a gold halo and glow, striped gold echoes, rays behind
     * the card, gold halftone and sparks, the sheen and the ring burst when dealt.
     */
    GOLD(5),
    ;

    /** Everything this material paints in the Default preset (bright gold, full spectrum): the same for every card of the rank. */
    val frame: KkCardFrame get() = KkCardFrames.all[ordinal]

    /**
     * Everything this material paints where a preset shows gold as [gold] and the prismatic spectrum
     * as [spectrum]: only the gold material changes with the gold (the deep gold of Protan and
     * Deutan) and only the prismatic one with the spectrum; every other material is the same.
     */
    fun frame(gold: KkGold, spectrum: KkSpectrum = KkSpectrum.Full): KkCardFrame = when {
        this == GOLD && gold === KkGold.Deep -> KkCardFrames.deepGold
        this == PRISMATIC -> KkCardFrames.prisms[spectrum.id]
        else -> KkCardFrames.all[ordinal]
    }

    companion object {
        /** The material of rank 1 (common) .. 5 (legendary); out-of-range ranks are clamped. */
        fun ofRank(rank: Int): KkCardMaterial = when (rank.coerceIn(1, 5)) {
            1 -> IRON
            2 -> STEEL
            3 -> CHROME
            4 -> PRISMATIC
            else -> GOLD
        }
    }
}

/** The effect layers a [KkCardFrame] paints (its [KkCardFrame.richness] is how many). */
enum class KkCardFrameLayer {
    /** A border round the card in the material. */
    BORDER,

    /** A finish on the band: matte hairlines, brushing, polish, glass or foil. */
    BAND_FINISH,

    /** A lit lip along the band's top edge and an edge line between band and face. */
    LIP,

    /** A border that catches the light: a gradient along it, lighter at the top left. */
    BEVEL,

    /** A soft glow inside the frame. */
    GLOW,

    /** A specular line across the band. */
    SPECULAR,

    /** A soft halo outside the frame. */
    HALO,

    /** An echo slab resting behind the card. */
    ECHO,

    /** A second echo behind the first. */
    DOUBLE_ECHO,

    /** A highlight sweeping across band and face. */
    SHEEN,

    /** A glint running round the border. */
    GLINT,

    /** The full spectrum, turning round the border and sliding along the band. */
    SPECTRUM,

    /** Facet lines in the face with a glint travelling along them. */
    FACETS,

    /** A halftone rising from the bottom of the face. */
    HALFTONE,

    /** Sparks rising through the face. */
    SPARKS,

    /** A moving foil on band and frame. */
    FOIL,

    /** Striped echoes. */
    STRIPES,

    /** Rays turning behind the card. */
    RAYS,

    /** A ring burst when the card is dealt. */
    BURST,
}

/**
 * Everything the rarity channel of one [KkCardMaterial] paints, fixed per material (no card's hue
 * reaches it): the [border] stops ([borderDp] wide on a board card), the [band] tones (its bevel,
 * label zone and horizon) and the [labelZone] tones a band label sits on, the leading [label]
 * colours (stops of a gradient when several) and the trailing [labelEnd], the [echo] colours, how
 * many [echoes] rest behind the card, the inner [glow] and outer [halo] colours with their
 * strengths, the [effects] colours (sheen, glints, halftone, sparks, rays, burst) and the [layers]
 * it draws. The gold material paints its [gold]; chrome and gold sweep a [sheen] over the face.
 * The prismatic material paints its [prism] (the preset's spectrum); [spectrum] holds every colour
 * it paints from it at once (its stops and its label's), never one hue.
 */
class KkCardFrame internal constructor(
    val material: KkCardMaterial,
    val gold: KkGold?,
    val prism: KkSpectrum?,
    val sheen: Color,
    val spectrum: Set<Color>,
    val border: List<Color>,
    val borderDp: Float,
    val band: List<Color>,
    val labelZone: List<Color>,
    val label: List<Color>,
    val labelEnd: Color,
    val echo: List<Color>,
    val echoes: Int,
    val glow: List<Color>,
    val glowStrength: Float,
    val halo: List<Color>,
    val haloStrength: Float,
    val effects: List<Color>,
    val layers: Set<KkCardFrameLayer>,
) {
    /** Every colour the frame paints (border, band, labels, echoes, glow, halo, effects and the face sheen). */
    val colors: List<Color> =
        (border + band + labelZone + label + labelEnd + echo + glow + halo + effects + listOfNotNull(sheen.takeIf { it.isSpecified })).distinct()

    /** How many effect layers the material draws: it rises strictly with the rank. */
    val richness: Int get() = layers.size
}

/** The Default preset's prismatic spectrum stops ([KkSpectrum.Full]); a preset's own is [KkRolePalette.spectrum]. */
val KkPrismaticSpectrum: List<Color> get() = KkSpectrum.Full.stops

/** The metals' white: rims, lips, glints and the sheen. */
internal val FrameWhite = Color.White

/** The prismatic frame's bright inner rim (bone, inside the spectrum border): it lifts the epic frame above chrome in lightness too. */
internal val PrismRim = Kk.Bone

/** The inner rim's width (board dp). */
internal const val PRISM_RIM_DP = 3f

// Iron: matte dark metal with a mid-grey edge.
internal val IronBorderTop = Color(0xFF85847C)
internal val IronBorderBottom = Color(0xFF575650)
internal val IronBand = listOf(Color(0xFF3B3C37), Color(0xFF32332F), Color(0xFF2E2F2B), Color(0xFF2A2B27), Color(0xFF262723), Color(0xFF1F201D))
internal val IronLip = Color(0xFF8E8D86)
internal val IronEdge = Color(0xFF6E6D66)
private val IronLabel = Color(0xFFBDBCB3)
private val IronEcho = Color(0xFF2C2D29)

// Steel: cool brushed metal in the middle of the lightness range, lit at the top, an ink label.
private val SteelBorder = listOf(Color(0xFFF2F5F7), Color(0xFFBAC2C8), Color(0xFF8A9299))
internal val SteelBand = listOf(Color(0xFFD6DADE), Color(0xFFA9B1B8), Color(0xFF959DA4), Color(0xFF868E95), Color(0xFF7A8289), Color(0xFF4A5157))
internal val SteelEdge = Color(0xFF3E4449)
private val SteelEcho = Color(0xFF3B4146)
private val SteelGlow = Color(0xFFDADEE2)

// Chrome: polished, bright, with a dark horizon below the label.
private val ChromeBorder = listOf(FrameWhite, Color(0xFFB8C0C6), Color(0xFF6B747B), Color(0xFFF4F7F9), Color(0xFF8E979F), FrameWhite)
internal val ChromeBand = listOf(FrameWhite, Color(0xFFE7EBEE), Color(0xFFD4D9DD), Color(0xFFC1C7CC), Color(0xFFA9B0B6), Color(0xFF59616A), Color(0xFFB6BDC3))
internal val ChromeEcho = listOf(Color(0xFFE0E3E6), Color(0xFF858E95), Color(0xFFC4CACF))
private val ChromeHalo = Color(0xFFEAEDF0)

/** Bone hairlines over iron and brushed steel bands (drawn and modelled from these values). */
internal const val IRON_HAIRLINE = 0.05f
internal const val STEEL_HAIRLINE = 0.12f

/** The sheen's highlight over a chrome band. */
internal const val CHROME_SHEEN = 0.22f

/** The glass of the prismatic band: its reflection and the refraction streak crossing it. */
internal const val PRISM_GLASS_ALPHA = 0.035f
internal const val PRISM_STREAK_ALPHA = 0.13f
internal const val PRISM_STREAK_DEGREES = 24f

/** Minimum contrast of a band label against the band under it. */
const val KK_CARD_LABEL_CONTRAST = 4.5f

/** Share of the band width the leading label may cover (from the leading edge). */
const val KK_CARD_START_LABEL_END = 0.62f

/** Share of the band width where the trailing label (and the element pips) start. */
const val KK_CARD_END_LABEL_START = 0.7f

/** [preferred] when it reads at the label contrast over every one of [backgrounds], else lifted toward bone or ink until it does. */
private fun readableOver(preferred: Color, backgrounds: List<Color>): Color {
    fun worst(color: Color): Float {
        var low = Float.MAX_VALUE
        for (background in backgrounds) low = min(low, kkContrast(color, background))
        return low
    }
    val target = KK_CARD_LABEL_CONTRAST + 0.2f
    if (worst(preferred) >= target) return preferred
    for (step in 1..20) {
        for (toward in arrayOf(Kk.Bone, Kk.Ink)) {
            val candidate = kkMix(preferred, toward, step / 20f)
            if (worst(candidate) >= target) return candidate
        }
    }
    return if (worst(Kk.Bone) >= worst(Kk.Ink)) Kk.Bone else Kk.Ink
}

/** The tones of a band zone [tones] and each under the bone hairlines at [hairline]. */
private fun withHairlines(tones: List<Color>, hairline: Float): List<Color> =
    tones + tones.map { kkMix(it, Kk.Bone, hairline) }

/**
 * The frames of every material in the Default preset (bright gold, full spectrum), in
 * [KkCardMaterial] order, the deep gold's frame and the prismatic frame of every spectrum (by id).
 */
internal object KkCardFrames {
    val all: List<KkCardFrame> = KkCardMaterial.entries.map { frameOf(it, KkGold.Bright, KkSpectrum.Full) }
    val deepGold: KkCardFrame = frameOf(KkCardMaterial.GOLD, KkGold.Deep, KkSpectrum.Full)
    val prisms: List<KkCardFrame> = KkSpectrum.all.map { if (it === KkSpectrum.Full) all[KkCardMaterial.PRISMATIC.ordinal] else frameOf(KkCardMaterial.PRISMATIC, KkGold.Bright, it) }
}

private fun frameOf(material: KkCardMaterial, gold: KkGold, spectrum: KkSpectrum): KkCardFrame = when (material) {
    KkCardMaterial.CALM -> KkCardFrame(
        material, null, null, Color.Unspecified, emptySet(), border = emptyList(), borderDp = 0f, band = listOf(Kk.Ink3, Kk.Line2), labelZone = listOf(Kk.Ink3),
        label = listOf(Kk.Mute), labelEnd = Kk.Mute, echo = emptyList(), echoes = 0, glow = emptyList(), glowStrength = 0f,
        halo = emptyList(), haloStrength = 0f, effects = emptyList(), layers = emptySet(),
    )
    KkCardMaterial.IRON -> {
        val zone = withHairlines(IronBand.subList(2, 5), IRON_HAIRLINE)
        val label = readableOver(IronLabel, zone)
        KkCardFrame(
            material, null, null, Color.Unspecified, emptySet(), border = listOf(IronBorderTop, IronBorderBottom), borderDp = 2f, band = IronBand + IronLip + IronEdge,
            labelZone = zone, label = listOf(label), labelEnd = label, echo = listOf(IronEcho, IronEdge), echoes = 0,
            glow = emptyList(), glowStrength = 0f, halo = emptyList(), haloStrength = 0f, effects = emptyList(),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP),
        )
    }
    KkCardMaterial.STEEL -> {
        val zone = withHairlines(SteelBand.subList(2, 5), STEEL_HAIRLINE)
        val label = readableOver(Kk.Ink, zone)
        KkCardFrame(
            material, null, null, Color.Unspecified, emptySet(), border = SteelBorder, borderDp = 2.5f, band = SteelBand + FrameWhite + SteelEdge,
            labelZone = zone, label = listOf(label), labelEnd = label, echo = listOf(SteelEcho, SteelBorder[1]), echoes = 0,
            glow = listOf(SteelGlow), glowStrength = 0.9f, halo = emptyList(), haloStrength = 0f, effects = emptyList(),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.BEVEL,
                KkCardFrameLayer.GLOW),
        )
    }
    KkCardMaterial.CHROME -> {
        // The label sits on the polished middle (and on it under the sheen); the horizon stays below it.
        val middle = ChromeBand.subList(2, 5)
        val zone = middle + middle.map { kkMix(it, FrameWhite, CHROME_SHEEN) }
        val label = readableOver(Kk.Ink, zone)
        KkCardFrame(
            material, null, null, FrameWhite, emptySet(), border = ChromeBorder, borderDp = 3f, band = ChromeBand, labelZone = zone, label = listOf(label), labelEnd = label,
            echo = ChromeEcho + FrameWhite, echoes = 1, glow = listOf(FrameWhite), glowStrength = 1.25f,
            halo = listOf(ChromeHalo), haloStrength = 1f, effects = listOf(FrameWhite),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.BEVEL,
                KkCardFrameLayer.GLOW, KkCardFrameLayer.SPECULAR, KkCardFrameLayer.HALO, KkCardFrameLayer.ECHO, KkCardFrameLayer.SHEEN,
                KkCardFrameLayer.GLINT),
        )
    }
    KkCardMaterial.PRISMATIC -> {
        val zone = listOf(Kk.Ink, kkMix(Kk.Ink, Kk.Bone, PRISM_GLASS_ALPHA), spectrum.streak)
        val loop = spectrum.loop
        KkCardFrame(
            material, null, spectrum, Color.Unspecified, (loop + spectrum.label).toSet(), border = loop + PrismRim, borderDp = 3.5f,
            band = listOf(Kk.Ink) + loop, labelZone = zone, label = spectrum.label, labelEnd = readableOver(Kk.Bone, zone),
            echo = loop + Kk.Bone, echoes = 2, glow = loop, glowStrength = 1.5f, halo = loop, haloStrength = 1.5f,
            effects = spectrum.stops + Kk.Bone,
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.GLOW,
                KkCardFrameLayer.HALO, KkCardFrameLayer.ECHO, KkCardFrameLayer.DOUBLE_ECHO, KkCardFrameLayer.SPECTRUM,
                KkCardFrameLayer.FACETS, KkCardFrameLayer.HALFTONE, KkCardFrameLayer.SPARKS,
                KkCardFrameLayer.GLINT),
        )
    }
    KkCardMaterial.GOLD -> {
        val zone = listOf(gold.base, gold.light)
        val label = readableOver(Kk.Ink, zone)
        KkCardFrame(
            material, gold, null, FrameWhite, emptySet(), border = listOf(gold.base, gold.light, gold.seam), borderDp = 3f, band = zone,
            labelZone = zone, label = listOf(label), labelEnd = label, echo = listOf(gold.base, gold.stripe), echoes = 1,
            glow = listOf(gold.base), glowStrength = 1f, halo = listOf(gold.base), haloStrength = 1.4f,
            effects = listOf(gold.base, gold.stripe, gold.ray, FrameWhite),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.GLOW,
                KkCardFrameLayer.HALO, KkCardFrameLayer.ECHO, KkCardFrameLayer.STRIPES, KkCardFrameLayer.SHEEN, KkCardFrameLayer.FOIL,
                KkCardFrameLayer.HALFTONE, KkCardFrameLayer.SPARKS, KkCardFrameLayer.RAYS, KkCardFrameLayer.BURST,
                KkCardFrameLayer.GLINT, KkCardFrameLayer.BEVEL),
        )
    }
}
