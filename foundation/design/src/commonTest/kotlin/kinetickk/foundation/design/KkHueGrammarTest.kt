// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hue grammar checked the way each Color vision preset is seen: Protan, Deutan and Tritan
 * through the Machado 2009 simulation at full severity, Mono as luminance alone, Default as is.
 * Colors are compared in CIEDE2000 (ΔE) and against ink by WCAG contrast.
 *
 * - The six damage tones (Kinetic, the preset's volt, and the five elements) stay 12 ΔE apart.
 *   Fill patterns can tell status fills apart, but damage numbers and thin rims carry hue alone.
 * - Every element stays 15 ΔE from the preset's threat color.
 * - Every tone reaches 4.5:1 on ink, because card effect numbers use it at body size.
 * - Mono elements are pure greys.
 * - Every tone stays 12 ΔE from the preset's gold ([KkRolePalette.gold]): gold is the legendary
 *   material and Rebirth's, so a type mark must never read as it.
 */
class KkHueGrammarTest {
    @Test
    fun defaultHuesKeepTheGrammar() {
        assertEquals(emptyList(), hueGrammarBreaks(KkVisionMode.DEFAULT).map { it.detail })
    }

    @Test
    fun colorVisionPresetsBreakTheGrammarOnlyWhereAPaletteFixIsPending() {
        val breaks = KkVisionMode.entries.flatMap(::hueGrammarBreaks)
        val fresh = breaks.filter { it.key !in PendingPaletteFixes }
        assertTrue(fresh.isEmpty(), "new hue grammar breaks:\n" + fresh.joinToString("\n") { it.detail })
        val resolved = PendingPaletteFixes - breaks.map { it.key }.toSet()
        assertTrue(resolved.isEmpty(), "fixed in KkRolePalette, remove from PendingPaletteFixes: $resolved")
    }

    @Test
    fun perceptualMathMatchesPublishedReferenceValues() {
        // Sharma, Wu, Dalal (2005) CIEDE2000 test pairs 1, 7, 17 and 25.
        assertEquals(2.0425, deltaE2000(Lab(50.0, 2.6772, -79.7751), Lab(50.0, 0.0, -82.7485)), 1e-4)
        assertEquals(2.3669, deltaE2000(Lab(50.0, 0.0, 0.0), Lab(50.0, -1.0, 2.0)), 1e-4)
        assertEquals(27.1492, deltaE2000(Lab(50.0, 2.5, 0.0), Lab(73.0, 25.0, -18.0)), 1e-4)
        assertEquals(1.2644, deltaE2000(Lab(60.2574, -34.0099, 36.2677), Lab(60.4626, -34.1751, 39.4387)), 1e-4)
        // sRGB white is Lab (100, 0, 0) and black on white is 21:1.
        val white = lab(Color.White, Vision.NONE)
        assertEquals(100.0, white.l, 1e-3)
        assertEquals(0.0, abs(white.a) + abs(white.b), 1e-2)
        assertEquals(21.0, wcagContrast(Color.White, Color.Black), 1e-6)
        // Every simulation keeps neutral greys neutral, and dichromats lose the red-green axis.
        Vision.entries.forEach { vision -> assertTrue(deltaE2000(lab(Color(0xFF808080), vision), lab(Color(0xFF808080), Vision.NONE)) < 0.5, "$vision grey") }
        val red = Color(0xFFFF0000)
        val green = Color(0xFF00C000)
        assertTrue(deltaE2000(lab(red, Vision.NONE), lab(green, Vision.NONE)) > 60.0)
        assertTrue(deltaE2000(lab(red, Vision.DEUTAN), lab(green, Vision.DEUTAN)) < deltaE2000(lab(red, Vision.NONE), lab(green, Vision.NONE)) / 2)
    }

    private class Break(val key: String, val detail: String)

    private fun hueGrammarBreaks(mode: KkVisionMode): List<Break> {
        val palette = mode.palette
        val vision = mode.vision()
        val tones = ToneNames.indices.map { palette.tone(it) }
        val seen = tones.map { lab(it, vision) }
        val threat = lab(palette.threat, vision)
        val breaks = mutableListOf<Break>()
        for (a in tones.indices) for (b in a + 1 until tones.size) {
            val distance = deltaE2000(seen[a], seen[b])
            if (distance < MIN_TONE_DELTA_E) {
                breaks += Break("$mode tones ${ToneNames[a]}/${ToneNames[b]}", "$mode ${ToneNames[a]} and ${ToneNames[b]} tones are ${tenths(distance)} ΔE apart")
            }
        }
        for (index in 1 until tones.size) {
            val distance = deltaE2000(seen[index], threat)
            if (distance < MIN_THREAT_DELTA_E) {
                breaks += Break("$mode threat ${ToneNames[index]}", "$mode ${ToneNames[index]} is ${tenths(distance)} ΔE from threat")
            }
            val onInk = wcagContrast(tones[index], Kk.Ink)
            if (onInk < MIN_INK_CONTRAST) {
                breaks += Break("$mode contrast ${ToneNames[index]}", "$mode ${ToneNames[index]} has ${tenths(onInk)}:1 on ink")
            }
            val argb = tones[index].toArgb()
            val grey = (argb shr 16 and 0xFF) == (argb shr 8 and 0xFF) && (argb shr 8 and 0xFF) == (argb and 0xFF)
            if (mode == KkVisionMode.MONO && !grey) {
                breaks += Break("$mode grey ${ToneNames[index]}", "$mode ${ToneNames[index]} is tinted, not a pure grey")
            }
        }
        // Gold: every colour of the preset's gold (the gold frame's foil, seam, stripes and rays).
        val gold = palette.gold
        val golds = listOf(gold.base, gold.light, gold.seam, gold.stripe, gold.ray).map { lab(it, vision) }
        for (index in tones.indices) {
            val distance = golds.minOf { deltaE2000(it, seen[index]) }
            if (distance < MIN_TONE_DELTA_E) {
                breaks += Break("$mode gold ${ToneNames[index]}", "$mode ${ToneNames[index]} is ${tenths(distance)} ΔE from the preset's gold")
            }
        }
        return breaks
    }

    private companion object {
        const val MIN_TONE_DELTA_E = 12.0
        const val MIN_THREAT_DELTA_E = 15.0
        const val MIN_INK_CONTRAST = 4.5
        val ToneNames = listOf("kinetic", "thermal", "cryo", "ion", "gravitic", "entropy")

        /**
         * Breaks the current KkRolePalette still has, each awaiting a palette change by its owner.
         * The test fails when one is fixed, so the entry is removed and the rule then holds.
         *
         * - Tritan heat (thermal) is 14.7 ΔE from the Tritan threat; both are pinned handoff tokens.
         * - Kinetic is each preset's volt (`you`) and Thermal its heat, both pinned handoff tokens:
         *   Protan's yellow volt and amber heat are 9.9 ΔE apart as a protanope sees them, Deutan's
         *   8.4 ΔE. Only a change to those tokens (or a non-hue cue on Kinetic numbers) clears them.
         * - Mono pure greys cannot hold six tones 12 ΔE apart above 4.5:1, so these pairs stay
         *   listed until Mono damage numbers get a non-hue cue and this rule exempts them. Seen as
         *   luminance alone, Mono's gold is one more grey among them (the legendary card is told by its
         *   material's foil, rays and burst there, the type by its pattern).
         */
        val PendingPaletteFixes = setOf(
            "TRITAN threat thermal",
            "PROTAN tones kinetic/thermal", "DEUTAN tones kinetic/thermal",
            "MONO tones kinetic/cryo", "MONO tones kinetic/ion", "MONO tones thermal/ion",
            "MONO tones thermal/gravitic", "MONO tones cryo/ion", "MONO threat thermal",
            "MONO threat cryo", "MONO threat ion", "MONO contrast entropy",
            "MONO grey cryo", "MONO grey ion", "MONO grey gravitic", "MONO grey entropy",
            "MONO gold kinetic", "MONO gold thermal", "MONO gold cryo", "MONO gold ion", "MONO gold entropy",
        )

        fun tenths(value: Double): String = (round(value * 10.0) / 10.0).toString()
    }
}

/** How a color is seen: unaltered, by a full-severity dichromat (Machado 2009) or by luminance alone. */
internal enum class Vision(val matrix: DoubleArray?) {
    NONE(null),
    PROTAN(doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)),
    DEUTAN(doubleArrayOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)),
    TRITAN(doubleArrayOf(1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900)),
    LUMINANCE(doubleArrayOf(0.2126, 0.7152, 0.0722, 0.2126, 0.7152, 0.0722, 0.2126, 0.7152, 0.0722)),
}

internal class Lab(val l: Double, val a: Double, val b: Double) {
    /** Chroma (colourfulness) as seen. */
    val chroma: Double get() = sqrt(a * a + b * b)
}

/** How the people a preset is made for see colour: Protan, Deutan and Tritan as dichromats, Mono as luminance alone. */
internal fun KkVisionMode.vision(): Vision = when (this) {
    KkVisionMode.DEFAULT -> Vision.NONE
    KkVisionMode.PROTAN -> Vision.PROTAN
    KkVisionMode.DEUTAN -> Vision.DEUTAN
    KkVisionMode.TRITAN -> Vision.TRITAN
    KkVisionMode.MONO -> Vision.LUMINANCE
}

private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun luminance(color: Color): Double = 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

/** WCAG 2 contrast ratio. */
internal fun wcagContrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** CIELAB (D65) of [color] as seen under [vision]. */
internal fun lab(color: Color, vision: Vision): Lab {
    val rgb = doubleArrayOf(linear(color.red), linear(color.green), linear(color.blue))
    val seen = vision.matrix?.let { m -> DoubleArray(3) { row -> (m[row * 3] * rgb[0] + m[row * 3 + 1] * rgb[1] + m[row * 3 + 2] * rgb[2]).coerceIn(0.0, 1.0) } } ?: rgb
    val x = (0.4124564 * seen[0] + 0.3575761 * seen[1] + 0.1804375 * seen[2]) / 0.95047
    val y = 0.2126729 * seen[0] + 0.7151522 * seen[1] + 0.0721750 * seen[2]
    val z = (0.0193339 * seen[0] + 0.1191920 * seen[1] + 0.9503041 * seen[2]) / 1.08883
    fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
    return Lab(116.0 * f(y) - 16.0, 500.0 * (f(x) - f(y)), 200.0 * (f(y) - f(z)))
}

/** CIEDE2000 color difference (Sharma, Wu, Dalal 2005). */
internal fun deltaE2000(first: Lab, second: Lab): Double {
    fun rad(degrees: Double) = degrees * PI / 180.0
    fun hue(b: Double, a: Double): Double = if (a == 0.0 && b == 0.0) 0.0 else (atan2(b, a) * 180.0 / PI + 360.0) % 360.0
    val chromaMean = (sqrt(first.a * first.a + first.b * first.b) + sqrt(second.a * second.a + second.b * second.b)) / 2.0
    val g = 0.5 * (1.0 - sqrt(chromaMean.pow(7) / (chromaMean.pow(7) + 25.0.pow(7))))
    val a1 = (1.0 + g) * first.a
    val a2 = (1.0 + g) * second.a
    val c1 = sqrt(a1 * a1 + first.b * first.b)
    val c2 = sqrt(a2 * a2 + second.b * second.b)
    val h1 = hue(first.b, a1)
    val h2 = hue(second.b, a2)
    val deltaL = second.l - first.l
    val deltaC = c2 - c1
    val deltaHue = when {
        c1 * c2 == 0.0 -> 0.0
        abs(h2 - h1) <= 180.0 -> h2 - h1
        h2 - h1 > 180.0 -> h2 - h1 - 360.0
        else -> h2 - h1 + 360.0
    }
    val deltaH = 2.0 * sqrt(c1 * c2) * sin(rad(deltaHue / 2.0))
    val lMean = (first.l + second.l) / 2.0
    val cMean = (c1 + c2) / 2.0
    val hMean = when {
        c1 * c2 == 0.0 -> h1 + h2
        abs(h1 - h2) <= 180.0 -> (h1 + h2) / 2.0
        h1 + h2 < 360.0 -> (h1 + h2 + 360.0) / 2.0
        else -> (h1 + h2 - 360.0) / 2.0
    }
    val t = 1.0 - 0.17 * cos(rad(hMean - 30.0)) + 0.24 * cos(rad(2.0 * hMean)) +
        0.32 * cos(rad(3.0 * hMean + 6.0)) - 0.20 * cos(rad(4.0 * hMean - 63.0))
    val deltaTheta = 30.0 * exp(-((hMean - 275.0) / 25.0).pow(2))
    val rc = 2.0 * sqrt(cMean.pow(7) / (cMean.pow(7) + 25.0.pow(7)))
    val sl = 1.0 + 0.015 * (lMean - 50.0).pow(2) / sqrt(20.0 + (lMean - 50.0).pow(2))
    val sc = 1.0 + 0.045 * cMean
    val sh = 1.0 + 0.015 * cMean * t
    val rt = -sin(rad(2.0 * deltaTheta)) * rc
    val l = deltaL / sl
    val c = deltaC / sc
    val h = deltaH / sh
    return sqrt(l * l + c * c + h * h + rt * c * h)
}
