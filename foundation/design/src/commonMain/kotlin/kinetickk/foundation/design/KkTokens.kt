// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import kotlin.math.tan

/**
 * KINETICKK redesign color tokens (`docs/design/redesign/tokens.json`, `kk.css :root`).
 *
 * Structural tokens (ink, line, bone, mute) are fixed. The five role colors [Volt], [Hazard],
 * [Heat], [Shield] and [Pol] are the *Default* palette only: screens must read role colors from
 * [KkRolePalette] (`LocalKkRolePalette` in composables, `CanvasTextMeasurer.roles` in Canvas
 * renderers) so the Color vision setting can remap them.
 */
object Kk {
    // ground & structure
    val Ink = Color(0xFF0A0B0A)
    val Ink1 = Color(0xFF0F110F)
    val Ink2 = Color(0xFF151815)
    val Ink3 = Color(0xFF1D211D)
    val Ink4 = Color(0xFF272C27)
    val Line = Color(0xFF2A2F2A)
    val Line2 = Color(0xFF3C423C)
    val Bone = Color(0xFFF1F0E8)
    val Bone2 = Color(0xFFD6D5CC)
    val Mute = Color(0xFFA3A298)
    val Mute2 = Color(0xFF86857B)

    // roles (Default palette; see KkRolePalette)
    val Volt = Color(0xFFD8FF3E)
    val Volt2 = Color(0xFFBDE82A)
    val VoltInk = Color(0xFF141A00)
    val Hazard = Color(0xFFFF3B6B)
    val Hazard2 = Color(0xFFD61F4F)
    val HazardInk = Color(0xFF2A0510)
    val Heat = Color(0xFFFF8A1F)
    val Shield = Color(0xFF45E0FF)
    val Pol = Color(0xFFA98BFF)

    // rarity
    val RCommon = Color(0xFFC9C8BE)
    val RUncommon = Color(0xFF5CF2A6)
    val RRare = Color(0xFF4DA6FF)
    val REpic = Color(0xFFB777FF)
    val RLegend = Color(0xFFFFB627)

    /** Light end of the legendary foil gradient (`#FFF3C8`). */
    val RLegendFoil = Color(0xFFFFF3C8)

    // relic aspects
    val AVector = Color(0xFFD8FF3E)
    val AGravitic = Color(0xFF9B7BFF)
    val AIon = Color(0xFF45E0FF)
    val ARift = Color(0xFFFF4FA3)
    val APrism = Color(0xFFE4F1FF)
    val AEntropy = Color(0xFFFF6A2B)
    val ASovereign = Color(0xFFFFC93C)

    /** Rarity colors in rank order 1..5: common, uncommon, rare, epic, legendary. */
    val Rarities: List<Color> = listOf(RCommon, RUncommon, RRare, REpic, RLegend)

    /**
     * Aspect colors in order vector, gravitic, ion, rift, prism, entropy, sovereign (the same order
     * as the game's relic aspects; screens map by ordinal).
     */
    val Aspects: List<Color> = listOf(AVector, AGravitic, AIon, ARift, APrism, AEntropy, ASovereign)

    /** Rarity color by rank 1 (common) .. 5 (legendary); out-of-range ranks are clamped. */
    fun rarity(rank: Int): Color = when (rank.coerceIn(1, 5)) {
        1 -> RCommon
        2 -> RUncommon
        3 -> RRare
        4 -> REpic
        else -> RLegend
    }

    /** Aspect color by index 0 (vector) .. 6 (sovereign); out-of-range indices are clamped. */
    fun aspect(index: Int): Color = when (index.coerceIn(0, 6)) {
        0 -> AVector
        1 -> AGravitic
        2 -> AIon
        3 -> ARift
        4 -> APrism
        5 -> AEntropy
        else -> ASovereign
    }
}

/**
 * Mixes [a] toward [b] by [t] (0 = a, 1 = b) per sRGB channel, like CSS `color-mix(in srgb)`:
 * `kkMix(Kk.Ink, accent, 0.07f)` is "accent 7 %, ink 93 %". Allocation-free.
 */
fun kkMix(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f,
    )
}

/** The four motion curves (`kk.css --e-*`). */
object KkEase {
    /** Selection slabs, cards, slams: overshoots (values above 1 are expected). */
    val Pull: Easing = CubicBezierEasing(0.18f, 1.45f, 0.4f, 1f)

    /** Shutters, wipes, tab swaps. */
    val Snap: Easing = CubicBezierEasing(0.75f, 0f, 0.2f, 1f)

    /** Entrances, number drift, meters filling. */
    val Out: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Exits, warnings closing in. */
    val In: Easing = CubicBezierEasing(0.6f, 0f, 0.9f, 0.4f)
}

/** Durations in milliseconds (`tokens.json duration_ms`). */
object KkTime {
    const val Impact = 16
    const val Snap = 90
    const val Pull = 220
    const val Shutter = 420
    const val Drift = 600

    /** Ambient loops never complete a revolution faster than this. */
    const val AmbientMin = 24_000

    /** Menu item selection slab grow time (`.mi`). */
    const val MenuSelect = 240

    /** Hit-ghost drain time for meters. */
    const val GhostDrain = 500

    /** Home palette recolor time (Out easing). */
    const val PaletteShift = 350
}

/** Shape angles in degrees. Negative leans the top edge forward (right), like italic type. */
object KkShape {
    /** Panels, meters, pips, buttons (`skewX(-12deg)`). */
    const val Shear = -12f

    /** Graphic cuts, wipes, shutters. */
    const val Slash = -27.5f

    /** Horizontal offset per unit of height for the [Shear] lean (tan 12°). */
    val ShearRatio: Float = tan(12f * PI_F / 180f)

    /** Horizontal offset per unit of height for the [Slash] lean (tan 27.5°). */
    val SlashRatio: Float = tan(27.5f * PI_F / 180f)
}

internal const val PI_F = 3.1415927f
internal const val TAU_F = 6.2831855f

/**
 * The seven Home palettes (data only). The selected Home item recolors the whole screen: the
 * accent replaces volt on Home, background = [background], halftone = accent @12 %, big word =
 * accent @7 %, transition [KkTime.PaletteShift] ms with [KkEase.Out]. Screens map their own menu
 * targets to these entries.
 */
enum class KkHomePalette(val accent: Color) {
    START(Color(0xFFD8FF3E)),
    ARMORY(Color(0xFFFF8A1F)),
    LAB(Color(0xFF45E0FF)),
    REBIRTH(Color(0xFFFF4FA3)),
    CODEX(Color(0xFFA98BFF)),
    SETTINGS(Color(0xFF5CF2A6)),

    /** Exit/quit item, only if the game has one. */
    EXIT(Color(0xFFA3A298)),
    ;

    /** mix(accent 7 %, ink 93 %). */
    val background: Color get() = kkMix(Kk.Ink, accent, 0.07f)

    /** Halftone dot color (accent @12 %). */
    val halftone: Color get() = accent.copy(alpha = 0.12f)

    /** Big faint background word (accent @7 %). */
    val backgroundWord: Color get() = accent.copy(alpha = 0.07f)
}

/**
 * The eleven Rebirth tier themes (`tokens.json rebirthTiers`, index = target tier 0..10).
 * Tier names in the handoff are placeholders; screens use the game's tier data for words.
 */
object KkRebirthTiers {
    const val MaxTier = 10

    val colors: List<Color> = listOf(
        Color(0xFFD8FF3E),
        Color(0xFFFFB627),
        Color(0xFFFF8A1F),
        Color(0xFFFF6A3D),
        Color(0xFFFF3B8E),
        Color(0xFFE04FFF),
        Color(0xFF9B6BFF),
        Color(0xFF5B7CFF),
        Color(0xFF3FC8FF),
        Color(0xFFBDEBFF),
        Color(0xFFFFFFFF),
    )

    /** Theme color of [tier]; clamped to 0..10. */
    fun color(tier: Int): Color = colors[tier.coerceIn(0, MaxTier)]

    /** Background tint: mix(accent 8 %, ink 92 %). Tier 10 is pure black. */
    fun background(tier: Int): Color =
        if (tier >= MaxTier) Color.Black else kkMix(Kk.Ink, color(tier), 0.08f)

    /** Orbit ring count: 1 + tier / 2, none at tier 10. */
    fun ringCount(tier: Int): Int = if (tier >= MaxTier) 0 else 1 + tier.coerceAtLeast(0) / 2

    /** Hazard band stripes appear from tier 5. */
    fun hasHazardBand(tier: Int): Boolean = tier >= 5

    /** Tier 10 renders in grayscale with the black hole. */
    fun isEventHorizon(tier: Int): Boolean = tier >= MaxTier
}

/** Level badge tiers (`.lvl.t1…t5`). */
enum class KkLevelTier {
    /** Levels 1–9: bone slab. */
    T1,

    /** Levels 10–19: bone slab + volt echo. */
    T2,

    /** Levels 20–29: volt face, bone echo, sheen. */
    T3,

    /** Levels 30–39: ink face, volt text and inset line, striped echo, sheen. */
    T4,

    /** Levels 40+: animated gold foil, bone echo, sheen, rotating rays. */
    T5,
    ;

    companion object {
        /** Tier for a level: 1–9 T1, 10–19 T2, 20–29 T3, 30–39 T4, 40+ T5 (≤ 0 reads as T1). */
        fun of(level: Int): KkLevelTier = when {
            level >= 40 -> T5
            level >= 30 -> T4
            level >= 20 -> T3
            level >= 10 -> T2
            else -> T1
        }
    }
}
