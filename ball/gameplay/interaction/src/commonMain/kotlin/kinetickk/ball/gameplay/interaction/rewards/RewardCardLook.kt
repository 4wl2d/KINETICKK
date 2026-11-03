// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardStyle
import kinetickk.foundation.design.kkLuminance
import kinetickk.foundation.design.kkMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The look of one reward card under one palette, in two channels that never mix: the rarity
 * channel is the [style]'s material alone (the frame: border, band, echo, glow, effects), the type
 * channel is the face (the [hue] and pattern by element, the preset's volt for Kinetic; the ornament
 * by primary tag, the watermark and seed, the bespoke [art] of an epic or legendary item).
 */
internal class RewardCardLook(
    val style: KkCardStyle,
    val art: RewardCardArt?,
    /** The type hue: the element's (the preset's volt for Kinetic, `you` for weapons) under the colour-vision preset. */
    val hue: Color,
    /** The second element hue, or [hue] when the card has one. */
    val secondHue: Color,
) {
    /** Whether anything is drawn behind the text, so the text rows need the soft face plates. */
    val needsPlates: Boolean get() = art != null || style.drawsBehindText

    /**
     * The icon plate's tint when the type hue tells little apart by colour ([neutralHue]: a pale or
     * greyish hue, as Protan and Deutan show Ion and Cryo, near the steel and chrome greys), else
     * unspecified. The type then carries a cue that is not hue: the plate tinted in the hue (a dark
     * patch, [plateCueTint]), its pattern stronger ([plateCuePattern]) and the body pattern at its
     * strongest.
     */
    val plateTint: Color = if (neutralHue(hue)) plateCueTint(hue) else Color.Unspecified

    /** Whether the card carries the plate cue ([plateTint]). */
    val plateCue: Boolean get() = plateTint.isSpecified
}

/**
 * [hue] over the plate's ink, as strong as it goes (up to [PLATE_TINT_MAX]) while the plate stays a
 * dark patch (luminance up to [PLATE_TINT_LUMINANCE]): a slate Ion tints it by about 30 %, a pale Cryo
 * by less, and the plate always stands apart from the mid and light greys of the metal frames.
 */
internal fun plateCueTint(hue: Color): Color {
    var tint = Kk.Ink3
    var step = 1
    while (step * PLATE_TINT_STEP <= PLATE_TINT_MAX) {
        val next = kkMix(Kk.Ink3, hue, step * PLATE_TINT_STEP)
        if (kkLuminance(next) > PLATE_TINT_LUMINANCE) break
        tint = next
        step++
    }
    return tint
}

private const val PLATE_TINT_STEP = 0.02f
private const val PLATE_TINT_MAX = 0.3f
private const val PLATE_TINT_LUMINANCE = 0.05f

/** Whether [hue] is pale or greyish: little chroma, or close to bone or mute (it tells little apart by colour). */
internal fun neutralHue(hue: Color): Boolean {
    val chroma = max(hue.red, max(hue.green, hue.blue)) - min(hue.red, min(hue.green, hue.blue))
    return chroma < LOW_CHROMA || rgbDistance(hue, Kk.Bone) < GREYISH || rgbDistance(hue, Kk.Mute) < GREYISH
}

internal fun rgbDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt(dr * dr + dg * dg + db * db)
}

private const val GREYISH = 0.25f
private const val LOW_CHROMA = 0.28f
