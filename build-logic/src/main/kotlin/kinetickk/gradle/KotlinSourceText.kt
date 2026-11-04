// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

/**
 * Blanks comments, string and char literals (templates included) and punctuation inside backtick
 * names. Offsets and newlines survive, so line numbers and brace depth can be read from the result
 * and comments or documentation examples cannot produce imports, functions or braces.
 */
internal fun maskKotlinSource(source: String): String = KotlinMasker(source).mask()

private class KotlinMasker(private val src: String) {
    private val out = src.toCharArray()
    private val n = src.length

    fun mask(): String {
        var i = 0
        while (i < n) {
            val end = when {
                src.startsWith("//", i) -> lineEnd(i)
                src.startsWith("/*", i) -> blockCommentEnd(i)
                src[i] == '"' -> stringEnd(i)
                src[i] == '\'' -> charEnd(i)
                src[i] == '`' -> {
                    val close = backtickEnd(i)
                    for (k in i + 1 until close - 1) if (out[k] in BACKTICK_PUNCTUATION) out[k] = '_'
                    i = close
                    continue
                }
                else -> {
                    i++
                    continue
                }
            }
            for (k in i until end) if (out[k] != '\n' && out[k] != '\r') out[k] = ' '
            i = end
        }
        return out.concatToString()
    }

    private fun lineEnd(i: Int): Int = src.indexOf('\n', i).let { if (it < 0) n else it }

    private fun backtickEnd(i: Int): Int = src.indexOf('`', i + 1).let { if (it < 0) n else it + 1 }

    private fun blockCommentEnd(i: Int): Int {
        var j = i + 2
        var depth = 1
        while (j < n && depth > 0) {
            when {
                src.startsWith("/*", j) -> { depth++; j += 2 }
                src.startsWith("*/", j) -> { depth--; j += 2 }
                else -> j++
            }
        }
        return j
    }

    private fun charEnd(i: Int): Int {
        var j = i + 1
        while (j < n) {
            when (src[j]) {
                '\\' -> { j += 2; continue }
                '\'' -> return j + 1
                '\n' -> return j
            }
            j++
        }
        return n
    }

    /** Ends after the closing quote; an unterminated single-line string ends at the line end (fail-safe). */
    private fun stringEnd(i: Int): Int {
        val raw = src.startsWith("\"\"\"", i)
        var j = if (raw) i + 3 else i + 1
        while (j < n) {
            if (raw && src.startsWith("\"\"\"", j)) {
                j += 3
                while (j < n && src[j] == '"') j++
                return j
            }
            if (!raw) {
                when (src[j]) {
                    '\\' -> { j += 2; continue }
                    '"' -> return j + 1
                    '\n' -> return j
                }
            }
            if (src.startsWith("\${", j)) {
                j = templateEnd(j + 2)
                continue
            }
            j++
        }
        return n
    }

    /** Code inside `${…}`: nested strings, chars, comments and braces until the matching `}`. */
    private fun templateEnd(i: Int): Int {
        var depth = 0
        var j = i
        while (j < n) {
            when {
                src.startsWith("//", j) -> { j = lineEnd(j); continue }
                src.startsWith("/*", j) -> { j = blockCommentEnd(j); continue }
                src[j] == '"' -> { j = stringEnd(j); continue }
                src[j] == '\'' -> { j = charEnd(j); continue }
                src[j] == '`' -> { j = backtickEnd(j); continue }
                src[j] == '{' -> depth++
                src[j] == '}' -> if (depth == 0) return j + 1 else depth--
            }
            j++
        }
        return n
    }

    private companion object {
        const val BACKTICK_PUNCTUATION = "{}()[]\"'=;"
    }
}

/** One `import` directive; [line] is 1-based. */
internal data class KotlinImport(val name: String, val line: Int)

/** Imports of masked text, aliases and wildcards included (`import a.B as C` yields `a.B`). */
internal fun kotlinImports(masked: String): List<KotlinImport> {
    val lines = KotlinLineIndex(masked)
    return KOTLIN_IMPORT.findAll(masked).map { match ->
        KotlinImport(match.groupValues[1], lines.lineOf(match.range.first))
    }.toList()
}

/** The first `package` declaration of masked text, or null when the file has none. */
internal fun kotlinPackage(masked: String): String? = KOTLIN_PACKAGE.find(masked)?.groupValues?.get(1)

/** Equals `wc -l` for text ending in LF; a last line without LF counts too. */
internal fun physicalLineCount(text: String): Int =
    text.count { it == '\n' } + if (text.isNotEmpty() && !text.endsWith('\n')) 1 else 0

/** True when brace depth never drops below zero and ends at zero. */
internal fun bracesBalance(masked: String): Boolean {
    var depth = 0
    for (c in masked) {
        if (c == '{') {
            depth++
        } else if (c == '}') {
            depth--
            if (depth < 0) return false
        }
    }
    return depth == 0
}

/** Maps character offsets to 1-based line numbers (1 + newlines before the offset). */
internal class KotlinLineIndex(text: String) {
    private val starts: IntArray = buildList {
        add(0)
        text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
    }.toIntArray()

    fun lineOf(offset: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= offset) low = mid + 1 else high = mid
        }
        return low
    }
}

internal val KOTLIN_IMPORT = Regex("(?m)^[ \\t]*import[ \\t]+([A-Za-z_][A-Za-z0-9_.*]*)")

private val KOTLIN_PACKAGE = Regex("(?m)^[ \\t]*package[ \\t]+([A-Za-z_][\\w.]*)")
