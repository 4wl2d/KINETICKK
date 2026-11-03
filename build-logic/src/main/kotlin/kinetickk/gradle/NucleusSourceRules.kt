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
    val code = maskKotlinSource(source)
    return KOTLIN_IMPORT.findAll(code).mapNotNull { match ->
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

private val FORBIDDEN_IMPORTS = listOf(
    "android", "androidx", "java", "javax", "platform",
    "kotlin.io", "kotlin.system", "kotlin.reflect", "kotlin.concurrent",
    "kotlin.native.concurrent", "kotlinx.coroutines", "kotlinx.io", "io.ktor", "okio",
    "kotlin.time.Clock", "kotlin.time.TimeSource", "kotlinx.datetime.Clock",
    "kotlin.random.Random.Default",
    "kinetickk.foundation.design", "kinetickk.foundation.diagnostics",
    "kinetickk.foundation.dispatch",
)
