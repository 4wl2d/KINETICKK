// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

/**
 * An import-boundary sensor, not a proof of decider purity. It catches direct imports (including
 * aliases and wildcards) of UI, platform I/O, concurrency, reflection and ambient time APIs.
 * Fully qualified calls, default imports and effects hidden behind allowed helpers still need
 * review and deterministic tests. Seeded Random instances and explicit Duration values are legal.
 * The separate project-edge rules stop production nucleus/API dependencies on implementations.
 */
internal fun nucleusImportViolations(source: String): List<String> {
    val code = maskKotlinCommentsAndLiterals(source)
    return IMPORT.findAll(code).mapNotNull { match ->
        val imported = match.groupValues[1]
        val forbidden = FORBIDDEN_IMPORTS.any { prefix ->
            imported == prefix || imported.startsWith("$prefix.") ||
                (imported.endsWith(".*") && prefix.startsWith(imported.removeSuffix("*")))
        }
        if (!forbidden) return@mapNotNull null
        val line = code.take(match.range.first).count { it == '\n' } + 1
        "$line: forbidden nucleus import '$imported'; keep effects in the owner/adapters"
    }.toList()
}

private val IMPORT = Regex("(?m)^[ \\t]*import[ \\t]+([A-Za-z_][A-Za-z0-9_.*]*)")

private val FORBIDDEN_IMPORTS = listOf(
    "android", "androidx", "java", "javax", "platform",
    "kotlin.io", "kotlin.system", "kotlin.reflect", "kotlin.concurrent",
    "kotlin.native.concurrent", "kotlinx.coroutines", "kotlinx.io", "io.ktor", "okio",
    "kotlin.time.Clock", "kotlin.time.TimeSource", "kotlinx.datetime.Clock",
    "kotlin.random.Random.Default",
    "kinetickk.foundation.design", "kinetickk.foundation.diagnostics",
    "kinetickk.foundation.dispatch",
)

/** Preserve offsets/newlines so comments and documentation examples cannot produce diagnostics. */
private fun maskKotlinCommentsAndLiterals(source: String): String {
    val result = source.toCharArray()
    var index = 0
    fun maskTo(end: Int) {
        while (index < end) {
            if (result[index] != '\n' && result[index] != '\r') result[index] = ' '
            index++
        }
    }
    while (index < source.length) {
        when {
            source.startsWith("//", index) ->
                maskTo(source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length)
            source.startsWith("/*", index) -> {
                var end = index + 2
                var depth = 1
                while (end < source.length && depth > 0) {
                    when {
                        source.startsWith("/*", end) -> { depth++; end += 2 }
                        source.startsWith("*/", end) -> { depth--; end += 2 }
                        else -> end++
                    }
                }
                maskTo(end)
            }
            source.startsWith("\"\"\"", index) -> {
                val close = source.indexOf("\"\"\"", index + 3)
                maskTo(if (close < 0) source.length else close + 3)
            }
            source[index] == '\"' || source[index] == '\'' -> {
                val quote = source[index]
                var end = index + 1
                while (end < source.length) {
                    if (source[end] == '\\') end = (end + 2).coerceAtMost(source.length)
                    else if (source[end++] == quote) break
                }
                maskTo(end)
            }
            else -> index++
        }
    }
    return result.concatToString()
}
