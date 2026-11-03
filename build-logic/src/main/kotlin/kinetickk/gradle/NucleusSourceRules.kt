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
internal fun nucleusImportViolations(source: String): List<String> =
    forbiddenImportViolations(source, FORBIDDEN_NUCLEUS_IMPORTS, "nucleus", "keep effects in the owner/adapters")

/** The same sensor for production `:api` sources; api commands may use the dispatch reply types. */
internal fun apiImportViolations(source: String): List<String> =
    forbiddenImportViolations(source, FORBIDDEN_API_IMPORTS, "api", "keep public contracts platform- and effect-free")

internal fun forbiddenImportViolations(
    source: String,
    forbidden: List<String>,
    scope: String,
    advice: String,
): List<String> {
    val code = maskKotlinSource(source)
    return kotlinImports(code).mapNotNull { import ->
        val imported = import.name
        val isForbidden = forbidden.any { prefix ->
            imported == prefix || imported.startsWith("$prefix.") ||
                (imported.endsWith(".*") && prefix.startsWith(imported.removeSuffix("*")))
        }
        if (isForbidden) "${import.line}: forbidden $scope import '$imported'; $advice" else null
    }
}

/**
 * Ambient randomness calls in production nucleus code: `Random.nextX()`, `Random.Default` and the
 * unseeded collection helpers. `import kotlin.random.Random` stays legal for seeded instances, so
 * the import sensor cannot see these calls. Fully qualified calls are a known blind spot.
 */
internal fun ambientRandomnessViolations(source: String): List<String> {
    val code = maskKotlinSource(source)
    val lines = KotlinLineIndex(code)
    return AMBIENT_RANDOMNESS.flatMap { it.findAll(code) }
        .sortedBy { it.range.first }
        .map { match ->
            val call = match.value.replace(WHITESPACE, " ")
            "${lines.lineOf(match.range.first)}: ambient randomness '$call'; draw from a seeded run stream or worldHash"
        }
}

private val FORBIDDEN_NUCLEUS_IMPORTS = listOf(
    "android", "androidx", "java", "javax", "platform",
    "kotlin.io", "kotlin.system", "kotlin.reflect", "kotlin.concurrent",
    "kotlin.native.concurrent", "kotlinx.coroutines", "kotlinx.io", "io.ktor", "okio",
    "kotlin.time.Clock", "kotlin.time.TimeSource", "kotlinx.datetime.Clock",
    "kotlin.random.Random.Default",
    "kinetickk.foundation.design", "kinetickk.foundation.diagnostics",
    "kinetickk.foundation.dispatch",
)

private val FORBIDDEN_API_IMPORTS = FORBIDDEN_NUCLEUS_IMPORTS - "kinetickk.foundation.dispatch"

private val AMBIENT_RANDOMNESS = listOf(
    Regex("""(?<![\w.])Random\s*\.\s*(?:Default\b|next\w*\s*\()"""),
    Regex("""\.\s*(?:random|randomOrNull|shuffled|shuffle)\s*\(\s*\)"""),
)

private val WHITESPACE = Regex("\\s+")
