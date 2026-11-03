// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max

/**
 * The game's default text size (125 %) renders the boards' reference size: board sizes are
 * multiplied by `setting / PROFILE_TEXT_BASELINE` (PR decision on the text-size setting).
 */
internal const val PROFILE_TEXT_BASELINE = 1.25f

/** The text-size multiplier of the smallest setting (100 %) relative to the boards. */
internal const val PROFILE_SMALLEST_TEXT_SCALE = 1f / PROFILE_TEXT_BASELINE

/** Board-relative text multiplier for the player's text-size [setting] (1.0–1.75). */
internal fun profileTextScale(setting: Float): Float = setting / PROFILE_TEXT_BASELINE

/**
 * Growth of text-holding geometry (rows, tiles, docks) for the board-relative text multiplier
 * [textScale]: [rate] of the text's growth above the board size. It never goes below 1, so the
 * smaller text of the 100 % setting keeps the board's rows and touch targets.
 */
internal fun profileLayoutGrow(textScale: Float, rate: Float): Float = 1f + (textScale.coerceIn(1f, 2f) - 1f) * rate

/**
 * Measures [text] at [size] (sp, before the text-size setting) and shrinks it (down to
 * [minFactor]) until it fits [maxWidth] in [maxLines]; the last resort ellipsizes.
 *
 * Larger text sizes never make a label smaller than it is at the smallest setting: when the
 * label would have to shrink below that, it keeps the size it has at 100 % text.
 */
internal fun fitKkText(
    measurer: CanvasTextMeasurer,
    text: String,
    size: Float,
    maxWidth: Float,
    maxLines: Int = 1,
    uppercase: Boolean = true,
    minFactor: Float = 0.62f,
    style: (Float) -> TextStyle,
): TextLayoutResult {
    val fitted = fitKkTextAt(measurer, text, size, maxWidth, maxLines, uppercase, minFactor, style)
    val smallest = PROFILE_SMALLEST_TEXT_SCALE / measurer.scale
    if (smallest >= 1f) return fitted
    val floor = fitKkTextAt(measurer, text, size * smallest, maxWidth, maxLines, uppercase, minFactor, style)
    return when {
        !fitted.fitsWhole() && floor.fitsWhole() -> floor
        fitted.layoutInput.style.fontSize.value < floor.layoutInput.style.fontSize.value -> floor
        else -> fitted
    }
}

private fun fitKkTextAt(
    measurer: CanvasTextMeasurer,
    text: String,
    size: Float,
    maxWidth: Float,
    maxLines: Int,
    uppercase: Boolean,
    minFactor: Float,
    style: (Float) -> TextStyle,
): TextLayoutResult {
    var factor = 1f
    while (true) {
        val layout = measureKkText(measurer, text, style(size * factor), uppercase, maxWidth.coerceAtLeast(1f), maxLines)
        val cut = layout.isCut()
        val broken = layout.breaksWord()
        when {
            !cut && !broken -> return layout
            // A word never splits across lines: keep shrinking past [minFactor] for that.
            broken && factor > PROFILE_WORD_FLOOR -> factor = max(PROFILE_WORD_FLOOR, factor - 0.06f)
            cut && factor > minFactor -> factor = max(minFactor, factor - 0.08f)
            else -> return layout
        }
    }
}

/** True when the whole text is shown: nothing clipped or ellipsized and no word split across lines. */
internal fun TextLayoutResult.fitsWhole(): Boolean = !isCut() && !breaksWord()

// Skia can ellipsize a single line without reporting visual overflow or isLineEllipsized.
// Match its natural width against the actual integer-pixel box, allowing subpixel rounding.
private fun TextLayoutResult.isCut(): Boolean =
    hasVisualOverflow || (lineCount == 1 && multiParagraph.intrinsics.maxIntrinsicWidth > size.width + 0.5f)

/**
 * Line height for text that may wrap: the boards' tight 0.86–0.9 line boxes let Cyrillic
 * diacritics (Ё, Й) touch the line above, so wrapped names use a full line.
 */
internal const val PROFILE_WRAPPED_LINE_HEIGHT = 1.08f

/** Line limit for wrapped descriptions: enough for every catalog text (their panels scroll). */
internal const val PROFILE_DESCRIPTION_MAX_LINES = 24

/** The smallest share of a style's size [fitKkText] uses to keep every word on one line. */
internal const val PROFILE_WORD_FLOOR = 0.34f

/** The size factor (at most 1) that fits [text] on one line in [maxWidth], down to [minFactor]. */
internal fun fitKkFactor(
    measurer: CanvasTextMeasurer,
    text: String,
    size: Float,
    maxWidth: Float,
    uppercase: Boolean = true,
    minFactor: Float = 0.4f,
    style: (Float) -> TextStyle,
): Float {
    var factor = 1f
    while (factor > minFactor) {
        val width = measureKkText(measurer, text, style(size * factor), uppercase).size.width
        if (width <= maxWidth) return factor
        factor = max(minFactor, factor - 0.05f)
    }
    return minFactor
}

/** True when a soft line break falls inside a word (the word is wider than the line). */
internal fun TextLayoutResult.breaksWord(): Boolean {
    if (lineCount < 2) return false
    val text = layoutInput.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        if (!text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') return true
    }
    return false
}
