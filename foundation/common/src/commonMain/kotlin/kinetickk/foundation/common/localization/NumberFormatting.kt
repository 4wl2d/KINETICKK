// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.localization

/**
 * Below this magnitude [formatCompactNumber] and [formatGroupedNumber] both write the plain integer, so the two forms
 * of a number are the same text.
 */
const val COMPACT_NUMBER_THRESHOLD: Long = 1_000L

/** Powers of 1,000 the compact form names (Latin in every language): thousand to quintillion. */
private val CompactSuffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi")

private const val MINUS = "−"

/**
 * [value] rounded to what [formatCompactNumber] shows: unchanged below [COMPACT_NUMBER_THRESHOLD] in magnitude, otherwise
 * to three significant digits, half away from zero. Numbers that read the same share this key, so a cache keyed by it
 * builds a new string only when the shown text changes.
 */
fun compactNumberRounded(value: Long): Long {
    if (value > -COMPACT_NUMBER_THRESHOLD && value < COMPACT_NUMBER_THRESHOLD) return value
    val magnitude = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
    var step = 1L
    while (magnitude / step >= COMPACT_NUMBER_THRESHOLD) step *= 10L
    // Three digits remain; the largest magnitude rounds down (922 × 10^16 ≤ Long.MAX_VALUE < 922.5 × 10^16).
    val rounded = (magnitude / step + if (magnitude % step * 2L >= step) 1L else 0L) * step
    return if (value < 0L) -rounded else rounded
}

/**
 * The compact form of a number for every absolute quantity the game shows: below 1,000 the integer, otherwise three
 * significant digits and a Latin suffix (1,234 → 1.23K, 12,345 → 12.3K, 123,456 → 123K, 999,500 → 1M) up to Qi.
 * Trailing zeros are dropped (1,000 → 1K); the decimal separator is the language's (Russian 1,23K).
 */
fun formatCompactNumber(value: Long, language: AppLanguage): String {
    val rounded = compactNumberRounded(value)
    if (rounded > -COMPACT_NUMBER_THRESHOLD && rounded < COMPACT_NUMBER_THRESHOLD) {
        return if (rounded < 0L) MINUS + (-rounded).toString() else rounded.toString()
    }
    val magnitude = kotlin.math.abs(rounded)
    var unit = 1L
    var suffix = 0
    while (magnitude / unit >= COMPACT_NUMBER_THRESHOLD) {
        unit *= COMPACT_NUMBER_THRESHOLD
        suffix++
    }
    val whole = magnitude / unit
    var decimals = when {
        whole >= 100L -> 0
        whole >= 10L -> 1
        else -> 2
    }
    var divisor = unit
    repeat(decimals) { divisor /= 10L }
    var fraction = magnitude % unit / divisor
    while (decimals > 0 && fraction % 10L == 0L) {
        fraction /= 10L
        decimals--
    }
    return buildString {
        if (rounded < 0L) append(MINUS)
        append(whole)
        if (decimals > 0) {
            append(language.decimalSeparator)
            append(fraction.toString().padStart(decimals, '0'))
        }
        append(CompactSuffixes[suffix])
    }
}

/**
 * Every digit of [value], thousands grouped per language: "1,234,567" in English, "1 234 567" (narrow no-break spaces)
 * in Russian.
 */
fun formatGroupedNumber(value: Long, language: AppLanguage): String {
    if (value > -COMPACT_NUMBER_THRESHOLD && value < COMPACT_NUMBER_THRESHOLD) {
        return if (value < 0L) MINUS + (-value).toString() else value.toString()
    }
    val digits = if (value == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(value).toString()
    val separator = if (language == AppLanguage.Russian) ' ' else ','
    return buildString(digits.length + digits.length / 3 + 1) {
        if (value < 0L) append(MINUS)
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(digit)
        }
    }
}

/** The language's decimal separator: a comma in Russian, a dot in English. */
private val AppLanguage.decimalSeparator: Char get() = if (this == AppLanguage.Russian) ',' else '.'
