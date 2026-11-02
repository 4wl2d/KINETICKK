// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kinetickk.ball.gameplay.interaction.canvas.HudTestFonts
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.localeList
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Preserve the exact fitting decisions of the original per-word algorithm. */
class OverlayWordFitTest {
    @Test
    fun batchedWordMeasurementPreservesFittingStylesAndFallbacks() {
        var compared = 0
        for (density in listOf(Density(1f), Density(2f, 1.3f))) {
            val reference = measurer(density)
            val candidate = measurer(density)
            for (language in listOf(AppLanguage.English, AppLanguage.Russian)) {
                val typography = HudTestFonts.typography.copy(localeList = language.localeList())
                val styles = listOf(
                    typography.bodyStyle(14f),
                    typography.bodyStyle(25f, weight = FontWeight.Bold).copy(letterSpacing = 0.09.em),
                    typography.condStyle(38f),
                    typography.bodyStyle(18f).copy(textIndent = TextIndent(firstLine = 7.sp, restLine = 23.sp)),
                )
                for (text in texts(language)) for (style in styles) {
                    for (width in listOf(41.5f, 87f, 180f, 360f)) for (lines in listOf(1, 2, Int.MAX_VALUE)) {
                        val expected = referenceFit(reference, text, style, width, lines, 0.7f)
                        val actual = fitTextStyle(candidate, text, style, width, lines, 0.7f)
                        assertEquals(expected, actual, "$language density=$density width=$width lines=$lines text=$text style=$style")
                        compared++
                    }
                }
                val style = typography.bodyStyle(16f)
                for (text in listOf("", "  \n  ", "A", "ё")) for (width in listOf(0f, -1f, Float.POSITIVE_INFINITY, 50f)) {
                    assertEquals(referenceFit(reference, text, style, width, 2, 0.75f),
                        fitTextStyle(candidate, text, style, width, 2, 0.75f), "Boundary '$text', width=$width")
                }
            }
        }
        assertTrue(compared >= 700, "Both scripts, density/font scale, styles, line limits and width fallback must be exercised")
    }

    /** Local CPU/allocation microbenchmark. It excludes composition, drawing, GPU work and frame presentation. */
    @Test
    fun pairedWordFittingMicrobenchmarkWhenRequested() {
        if (System.getenv("KK_WORD_FIT_BENCH") != "1") return
        val cases = buildList {
            for (language in listOf(AppLanguage.English, AppLanguage.Russian)) {
                val typography = HudTestFonts.typography.copy(localeList = language.localeList())
                for (text in texts(language).take(3)) for (width in listOf(87f, 180f, 320f)) {
                    add(FitCase(text, typography.bodyStyle(18f), width, 2))
                    add(FitCase(text.uppercase(), typography.condStyle(32f), width, 2))
                }
            }
        }
        val reference = measurer(Density(1f))
        val candidate = measurer(Density(1f))
        val repeats = 4
        fun run(original: Boolean): Double {
            var witness = 0.0
            repeat(repeats) {
                for (case in cases) {
                    val fitted = if (original) referenceFit(reference, case.text, case.style, case.width, case.lines, 0.7f)
                    else fitTextStyle(candidate, case.text, case.style, case.width, case.lines, 0.7f)
                    witness += fitted.fontSize.value
                }
            }
            return witness
        }
        repeat(3) { assertEquals(run(true), run(false), "Warmup workload outputs must match") }
        val threads = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        val output = System.getenv("KK_WORD_FIT_OUT")?.let(::File)
        output?.parentFile?.mkdirs()
        for (sample in 0 until 6) {
            val order = if (sample % 2 == 0) listOf(true, false) else listOf(false, true)
            val witnesses = mutableListOf<Double>()
            for (original in order) {
                val allocatedBefore = threads?.getThreadAllocatedBytes(thread) ?: -1L
                val started = System.nanoTime()
                val witness = run(original)
                val nanos = System.nanoTime() - started
                val allocatedAfter = threads?.getThreadAllocatedBytes(thread) ?: -1L
                val bytes = if (allocatedBefore < 0 || allocatedAfter < 0) -1L else allocatedAfter - allocatedBefore
                witnesses += witness
                val record = "{\"path\":\"${if (original) "reference" else "candidate"}\",\"sample\":$sample," +
                    "\"calls\":${cases.size * repeats},\"ns\":$nanos,\"jvm_allocated_bytes\":$bytes,\"witness\":$witness}"
                println("WORD_FIT $record")
                output?.appendText("$record\n")
            }
            assertEquals(witnesses[0], witnesses[1], "Sample $sample must perform equivalent fitting")
        }
    }

    private fun measurer(density: Density) = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr, cacheSize = 16)

    private fun texts(language: AppLanguage): List<String> = when (language) {
        AppLanguage.English -> listOf(
            "Each critical hit releases a wave of damage around the Core.",
            "ElectromagneticCountermeasure increases damage and stabilizes polarity.",
            "SHIELD OVERDRIVE\n+42% attack speed  2.5 s",
            "a  muchlongerword\n\nshort words",
        )
        AppLanguage.Russian -> listOf(
            "Каждое критическое попадание выпускает волну урона вокруг Ядра.",
            "Электромагнитнаястабилизация повышает урон и укрепляет полярность.",
            "ПЕРЕГРУЗКА ЩИТА\n+42% скорости атаки  2,5 с",
            "я  сверхпроводимость\n\nкороткие слова",
        )
    }

    private data class FitCase(val text: String, val style: TextStyle, val width: Float, val lines: Int)

    /** Literal pre-optimization implementation: deliberately independent of production's word measurement. */
    private fun referenceFit(
        measurer: TextMeasurer,
        shown: String,
        style: TextStyle,
        maxWidthPx: Float,
        maxLines: Int,
        minScale: Float,
    ): TextStyle {
        if (maxWidthPx <= 0f || maxWidthPx.isInfinite() || shown.isEmpty()) return style
        val words = shown.split(' ', '\n').filter(String::isNotEmpty)
        fun styleAt(scale: Float) = if (scale == 1f) style else style.copy(fontSize = style.fontSize * scale)
        fun widest(candidate: TextStyle) = words.maxOfOrNull { word ->
            measurer.measure(word, candidate, softWrap = false, maxLines = 1).size.width.toFloat()
        } ?: 0f
        fun fits(candidate: TextStyle): Boolean {
            if (widest(candidate) > maxWidthPx) return false
            if (maxLines == Int.MAX_VALUE) return true
            val layout = measurer.measure(shown, candidate, softWrap = maxLines > 1, maxLines = maxLines + 1,
                constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(1)))
            return layout.lineCount <= maxLines && !layout.didOverflowWidth && layout.size.width <= maxWidthPx
        }
        var scale = 1f
        while (scale >= minScale - 0.001f) {
            val candidate = styleAt(scale)
            if (fits(candidate)) return candidate
            scale -= 0.05f
        }
        val minimum = styleAt(max(minScale, 0.05f))
        val widest = widest(minimum)
        return if (widest <= maxWidthPx) minimum else minimum.copy(fontSize = minimum.fontSize * (maxWidthPx / widest) * 0.97f)
    }
}
