// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The five role colors remapped by the Color vision setting (`tokens.json colorVision`).
 *
 * This is the one way screens read the role colors: `you` (volt: player, confirm), `threat`
 * (hazard: enemies, danger), `heat`, `shield` and `pol` (polarity/cursor), plus `soft` (the easy
 * run mode's harmless cursor) and the damage tones
 * ([kinetic] and the [elements]). Composables read [LocalKkRolePalette]; Canvas renderers read
 * `CanvasTextMeasurer.roles`. Structural tokens (ink/bone/mute/line) and rarity do not change with
 * Color vision; [gold] does only where a deficiency would see the bright gold as the preset's volt,
 * and the prismatic [spectrum] does so it never reads as the preset's threat, volt or gold.
 *
 * [hatchThreats] is set for [Mono]: every threat (enemies, hostile projectiles, hazard meters)
 * additionally gets [drawKkThreatHatch].
 */
@Immutable
data class KkRolePalette(
    val you: Color,
    val threat: Color,
    val heat: Color,
    val shield: Color,
    val pol: Color,
    val hatchThreats: Boolean = false,
    /**
     * Status element colors (thermal, cryo, ion, gravitic, entropy, sonic, void) for this vision mode. Color-vision
     * presets keep Thermal on their heat hue and keep the others near their own hue but distinct as
     * that deficiency sees them; Mono uses a lightness ramp. Element glyphs and fill patterns carry
     * identity redundantly.
     */
    val elements: List<Color> = Kk.Elements,
    /**
     * Kinetic, the hue of everything without an element (impact items, Kinetic relics, Kinetic
     * damage numbers): the preset's own volt. Movement is your weapon, so impact shares the player
     * color. A screen that re-tints [you] with `copy` (a Home accent, a Rebirth tier) keeps this.
     */
    val kinetic: Color = you,
    /**
     * Gold (the legendary material, Rebirth, Reborn and Heirloom) as this preset shows it: the
     * bright gold, or the deep gold where the deficiency sees the bright one as this preset's volt.
     */
    val gold: KkGold = KkGold.Bright,
    /**
     * The spectrum of the prismatic (epic) material as this preset shows it: many colours at once,
     * every one of them and every blend between neighbours kept clear of this preset's threat, volt
     * and gold as its viewers see them.
     */
    val spectrum: KkSpectrum = KkSpectrum.Full,
    /**
     * Soft: the harmless cursor of the easy run mode. Kept 15 ΔE from the preset's threat and 12 ΔE from its volt, shield,
     * Entropy and gold as its viewers see them, and 4.5:1 on ink (KkHueGrammarTest); a pure grey in Mono.
     */
    val soft: Color = Color(0xFF9CF7C4),
) {
    /** Element color by index 0 (thermal) .. 6 (void). */
    fun element(index: Int): Color = elements[index.coerceIn(0, elements.lastIndex)]

    /** Damage tone: 0 is [kinetic] (the preset's volt), 1..7 the status elements. */
    fun tone(index: Int): Color = if (index <= 0) kinetic else element(index - 1)

    companion object {
        val Default = KkRolePalette(
            you = Color(0xFFD8FF3E),
            threat = Color(0xFFFF3B6B),
            heat = Color(0xFFFF8A1F),
            shield = Color(0xFF45E0FF),
            pol = Color(0xFFA98BFF),
        )
        val Protan = KkRolePalette(
            you = Color(0xFFFFE14D),
            threat = Color(0xFF3D8BFF),
            heat = Color(0xFFFFB000),
            shield = Color(0xFF9AD0FF),
            pol = Color(0xFFE0E0E0),
            elements = visionElements(0xFFFFB000, 0xFFBDEBFF, 0xFF728AA1, 0xFFE82CAF, 0xFF63E9AF, 0xFF5AB478, 0xFFD2BAFC),
            gold = KkGold.Deep,
            spectrum = KkSpectrum.Protan,
            soft = Color(0xFF6CA69A),
        )
        val Deutan = KkRolePalette(
            you = Color(0xFFFFD84A),
            threat = Color(0xFF2F6BFF),
            heat = Color(0xFFFF9E1A),
            shield = Color(0xFFA6D8FF),
            pol = Color(0xFFD6D6D6),
            elements = visionElements(0xFFFF9E1A, 0xFFBDEBFF, 0xFF627F93, 0xFFCC80FF, 0xFF2CE88A, 0xFF428A4E, 0xFFF08AE4),
            gold = KkGold.Deep,
            spectrum = KkSpectrum.Deutan,
            soft = Color(0xFF6CA69A),
        )
        val Tritan = KkRolePalette(
            you = Color(0xFF5CF2E0),
            threat = Color(0xFFFF3B3B),
            heat = Color(0xFFFF7A9A),
            shield = Color(0xFFE4E4E4),
            pol = Color(0xFFFF9ED8),
            elements = visionElements(0xFFFF7A9A, 0xFFCAEBFC, 0xFF427BFF, 0xFFBF55EC, 0xFF55BD51, 0xFF3C8A42, 0xFF9654FC),
            spectrum = KkSpectrum.Tritan,
            soft = Color(0xFF919E77),
        )
        val Mono = KkRolePalette(
            you = Color(0xFFFFFFFF),
            threat = Color(0xFFFFFFFF),
            heat = Color(0xFFBDBDBD),
            shield = Color(0xFF8A8A8A),
            pol = Color(0xFFD9D9D9),
            hatchThreats = true,
            elements = visionRamp(Color(0xFFBDBDBD)),
            spectrum = KkSpectrum.Light,
            soft = Color(0xFF9E9E9E),
        )

        /**
         * Thermal keeps the preset's heat hue; the other elements stay as close to their own hue as the
         * deficiency allows while remaining distinct from each other, from the Kinetic volt (`you`) and
         * from threat: Protan Entropy leans mint and Tritan Cryo paler, Tritan Entropy deeper, so each
         * keeps 12 ΔE from the preset's volt as that deficiency sees it. Sonic and Void take the hues each
         * deficiency still tells apart from the other six tones, the threat, the gold and the card frames.
         */
        private fun visionElements(vararg argb: Long): List<Color> = argb.map { Color(it) }

        /** Mono: Thermal keeps the heat grey; the other six elements become a lightness ramp. */
        private fun visionRamp(thermal: Color): List<Color> = listOf(
            thermal,
            Color(0xFFF4F8FF),
            Color(0xFFC2CAD2),
            Color(0xFF8E979F),
            Color(0xFF646C72),
            Color(0xFFA6A6A6),
            Color(0xFF7A7A7A),
        )
    }
}

/**
 * Gold, the one colour of the legendary material, Rebirth, Reborn and Heirloom, as a preset shows it:
 * the [base] gold (marks, text, the body of a foil), the foil's [light], the dark [seam] inside a gold
 * frame, the light [stripe] between the gold ones of a striped echo and the [ray] colour behind a
 * gold card.
 *
 * [Bright] is the design's gold. [Deep] is an antique gold with a warm white glint for Protan and
 * Deutan: those presets' volt is a yellow their deficiency sees as the bright gold (2 to 5 ΔE), so a
 * Kinetic card would look gold-trimmed. Every Deep tone keeps 12 ΔE or more from those presets'
 * damage tones as seen, and ink labels read on it.
 */
@Immutable
class KkGold private constructor(
    val base: Color,
    val light: Color,
    val seam: Color,
    val stripe: Color,
    val ray: Color,
) {
    companion object {
        val Bright = KkGold(Kk.RLegend, Kk.RLegendFoil, Color(0xFF7A5A12), Color(0xFFFFE59A), Color(0xFFFFB627))
        val Deep = KkGold(Color(0xFFB0843A), Color(0xFFFFFDF5), Color(0xFF5C4214), Color(0xFFFFFDF5), Color(0xFFB0843A))
    }
}

/**
 * The spectrum of the prismatic (epic) material as a preset shows it: its [stops] in order round a
 * closed loop, many colours at once so it never reads as one element. In every colour-vision preset
 * but Mono (whose threat and volt are white) every stop and every blend between neighbouring stops
 * (the label's lifted ones too) keeps 12 ΔE from the preset's threat, volt and gold as its viewers
 * see them, so an epic frame never reads as a cost, as Kinetic or as the legendary gold.
 *
 * - [Full] (Default): coral, green, mint, sky, blue, violet, magenta and a pale peach seam that
 *   carries the loop back to coral round the threat's pink red; it leaves out yellow (the volt's)
 *   and amber (the gold's), the blend from coral to green passing through a muted yellow.
 * - [Protan] and [Deutan]: those viewers see a blue to yellow axis and lightness, so the stops are
 *   set apart in lightness along it (a deep indigo, then pale creams, peaches, lilacs and whites,
 *   mint and teal for Deutan), away from the mid blue of their threat and the yellow of their volt.
 * - [Tritan]: those viewers see a red to cyan axis, so blues, violets, lilacs and a pink, away from
 *   the red threat, the cyan volt and the gold.
 * - [Light] (Mono): the Default hues lifted to pastels at L* 80 or more, so seen as luminance alone
 *   the prismatic frame stays brighter than the chrome below it.
 */
@Immutable
class KkSpectrum private constructor(
    /** Index of this spectrum in [all] (brush caches key on it). */
    internal val id: Int,
    val stops: List<Color>,
) {
    /** The stops closed into a loop (the first again at the end), for sweeps and repeating gradients. */
    internal val loop: List<Color> = stops + stops.first()

    /** The lightest colour the prismatic band's refraction streak lays over its black glass. */
    internal val streak: Color = run {
        var brightest = stops[0]
        for (color in stops) if (kkLuminance(color) > kkLuminance(brightest)) brightest = color
        kkMix(Kk.Ink, brightest, PRISM_STREAK_ALPHA)
    }

    /** The label and band edge stops: every stop and the halfway blend to the next, each lifted to read on the glass. */
    internal val label: List<Color> = buildList {
        for (index in stops.indices) {
            val a = stops[index]
            val b = stops[(index + 1) % stops.size]
            add(kkLiftContrast(a, streak, KK_CARD_LABEL_CONTRAST + 0.5f))
            add(kkLiftContrast(kkMix(a, b, 0.5f), streak, KK_CARD_LABEL_CONTRAST + 0.5f))
        }
        add(first())
    }

    /** The stop at [index], wrapping round the loop. */
    fun stop(index: Int): Color = stops[index.mod(stops.size)]

    companion object {
        val Full = KkSpectrum(0, colors(0xFFFF7458, 0xFF6EEE86, 0xFF48EEC4, 0xFF4CC4FF, 0xFF6E7BFF, 0xFFB36EFF, 0xFFF26EE4, 0xFFFFB0A0))
        val Protan = KkSpectrum(1, colors(0xFF28206E, 0xFFF5E6B0, 0xFFFFB0A0, 0xFFE0B0FF, 0xFFE8E8FF, 0xFFF0C8FF, 0xFFFF9AA0))
        val Deutan = KkSpectrum(2, colors(0xFF28206E, 0xFF60D0C0, 0xFFF0C8FF, 0xFFE8E8FF, 0xFFE0B0FF, 0xFF70E0A0, 0xFFF5E6B0))
        val Tritan = KkSpectrum(3, colors(0xFF6E7BFF, 0xFFB36EFF, 0xFFC8A0FF, 0xFF80B0FF, 0xFFA0A0FF, 0xFFD060FF, 0xFFFF60C0))
        val Light = KkSpectrum(4, colors(0xFFFFB7A8, 0xFF6EEE86, 0xFF48EEC4, 0xFF77D2FF, 0xFFBFC5FF, 0xFFDBB9FF, 0xFFF8B1F0))

        /** Every spectrum, by [id]. */
        val all: List<KkSpectrum> = listOf(Full, Protan, Deutan, Tritan, Light)

        private fun colors(vararg argb: Long): List<Color> = argb.map { Color(it) }
    }
}

/**
 * Presentation-level Color vision mode. The profile preference owns the persisted choice; the
 * app maps it to this enum and provides [palette] through [LocalKkRolePalette] and
 * `CanvasTextMeasurer.roles`.
 */
enum class KkVisionMode(val palette: KkRolePalette) {
    DEFAULT(KkRolePalette.Default),
    PROTAN(KkRolePalette.Protan),
    DEUTAN(KkRolePalette.Deutan),
    TRITAN(KkRolePalette.Tritan),
    MONO(KkRolePalette.Mono),
}

/** Role palette for composables. Canvas renderers use `CanvasTextMeasurer.roles` instead. */
val LocalKkRolePalette = staticCompositionLocalOf { KkRolePalette.Default }

/**
 * Draws the MONO threat hatch (45° lines) inside [path]; a no-op unless [roles] hatches threats.
 *
 * Use [tone] = `roles.threat` (default) over ink-filled/outlined threats such as enemies, and
 * `Kk.Ink` over threat-filled shapes (projectiles, hazard meters). Allocation-free after the
 * first call per tone and density (cached shader tile).
 */
fun DrawScope.drawKkThreatHatch(path: Path, roles: KkRolePalette, tone: Color = roles.threat) {
    if (!roles.hatchThreats) return
    drawPath(path, kkFillBrush(KkFillKind.THREAT_HATCH, tone))
}

/** Rect overload of [drawKkThreatHatch]. */
fun DrawScope.drawKkThreatHatch(rect: Rect, roles: KkRolePalette, tone: Color = roles.threat) {
    if (!roles.hatchThreats) return
    drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, tone), rect.topLeft, rect.size)
}
