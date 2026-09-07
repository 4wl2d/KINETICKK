// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.SortedSet

internal data class ProjectEdge(
    val source: String,
    val configuration: String,
    val target: String,
) {
    val encoded: String
        get() = "$source\t$configuration\t$target"

    val isTest: Boolean
        get() = configuration.contains("test", ignoreCase = true)

    companion object {
        fun decode(encoded: String): ProjectEdge {
            val parts = encoded.split('\t', limit = 3)
            require(parts.size == 3) { "Malformed project dependency edge: $encoded" }
            return ProjectEdge(parts[0], parts[1], parts[2])
        }
    }
}

internal data class SourceDocument(
    val relativePath: String,
    val text: String,
)

internal fun SourceDocument.isProductionKotlinSource(): Boolean {
    if (!relativePath.endsWith(".kt")) return false
    val pathSegments = relativePath.split('/')
    if (pathSegments.firstOrNull() !in setOf("app", "ball", "flow", "foundation", "resource")) {
        return false
    }
    val sourceRootIndex = pathSegments.indexOf("src")
    if (sourceRootIndex <= 0) return false
    if (pathSegments.take(sourceRootIndex).any { it == "build" || it == ".gradle" || it == "generated" }) {
        return false
    }
    val sourceSet = pathSegments.getOrNull(sourceRootIndex + 1) ?: return false
    return sourceSet == "main" || sourceSet.endsWith("Main")
}

internal object PokeballBaseline {
    const val CORE_COMMIT = "b4a8219ecb70ae5e81214edd6b509b61d9db0637"
    const val CORE_VERSION = "1.5.0-draft"
    const val CORE_STATUS = "canonical draft"
    const val CORE_FILE_COUNT = 25
    const val CORE_BYTES = 733_764L
    const val CORE_SHA256 = "2ac605e4ff4db406b661356ea9c15a1b2d1683f68e515cd7cfc136c41c28daad"
    const val AGENT_PACK_REVISION = 15
    const val AGENT_PACK_FILE_COUNT = 25
    const val AGENT_PACK_SHA256 = "736220908debbb93a84dd971ce5943efb79b957cb3c6d7c04ad6eba97ae1aa97"
}

private const val GAMEPLAY_BASELINE_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/" +
        "GameplayBaselineCharacterizationTest.kt"
private const val GAMEPLAY_SYSTEMS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/" +
        "GameSystemsTest.kt"
internal const val ANDROID_HOST_ACTIVITY_PATH =
    "app/android/src/main/kotlin/kinetickk/app/shared/MainActivity.kt"

internal val allowedForeignInternalProjectEdges = setOf(
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:content:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:gameplay:impl"),
    ProjectEdge(":app:shared", "commonTestImplementation", ":ball:gameplay:interaction"),
    // Integration tests observe the published render snapshot returned by GameplayInteractionPort.
    ProjectEdge(":app:shared", "commonTestImplementation", ":ball:gameplay:nucleus"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:profile:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:profile:interaction"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":flow:session:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":flow:session:interaction"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":resource:audio:impl"),
    ProjectEdge(":flow:session:impl", "commonMainApi", ":ball:gameplay:interaction"),
    ProjectEdge(":flow:session:interaction", "commonMainApi", ":ball:gameplay:interaction"),
    ProjectEdge(":flow:session:interaction", "commonMainApi", ":ball:profile:interaction"),
    ProjectEdge(":ball:gameplay:nucleus", "commonTestImplementation", ":ball:content:impl"),
)

internal val expectedAndroidHostProductionEdges = setOf(
    ProjectEdge(":app:android", "implementation", ":app:shared"),
)

internal val expectedAndroidHostProductionSources = setOf(ANDROID_HOST_ACTIVITY_PATH)

internal fun findCycle(edges: Collection<Pair<String, String>>): List<String>? {
    val adjacency = edges.groupBy({ it.first }, { it.second }).mapValues { it.value.toSortedSet() }
    val visiting = linkedSetOf<String>()
    val visited = mutableSetOf<String>()

    fun visit(node: String): List<String>? {
        if (node in visiting) {
            val path = visiting.toList()
            return path.dropWhile { it != node } + node
        }
        if (!visited.add(node)) return null
        visiting += node
        adjacency[node].orEmpty().forEach { next ->
            visit(next)?.let { return it }
        }
        visiting -= node
        return null
    }

    return edges.flatMap { listOf(it.first, it.second) }.toSortedSet().firstNotNullOfOrNull(::visit)
}

internal fun parseTableIds(markdown: String, heading: String): SortedSet<String> {
    return parseArchitectureTableRows(markdown, heading)
        .asSequence()
        .map(ArchitectureTableRow::id)
        .toSortedSet()
}

/** A derived view of actual project edges and inspectable owner/wiring sources. */
internal fun resolvedManifestJson(
    leafProjects: Collection<String>,
    edges: Collection<ProjectEdge>,
    sources: Collection<SourceDocument>,
): String {
    val sortedEdges = edges.sortedWith(compareBy(ProjectEdge::source, ProjectEdge::configuration, ProjectEdge::target))
    val production = sources.filter(SourceDocument::isProductionKotlinSource)
    val directControl = resolvedSemanticDirectControl(edges.toSet(), production)
    return buildString {
        appendLine("{")
        appendLine("  \"schema\": \"kinetickk-pokeball-resolved/v2\",")
        appendLine("  \"authority\": \"non-authoritative generated projection; owner-authored Kotlin and runtime wiring remain authoritative\",")
        appendLine("  \"pokeball\": {")
        appendLine("    \"agentPackRevision\": ${PokeballBaseline.AGENT_PACK_REVISION},")
        appendLine("    \"agentPackSha256\": \"${PokeballBaseline.AGENT_PACK_SHA256}\",")
        appendLine("    \"coreCommit\": \"${PokeballBaseline.CORE_COMMIT}\",")
        appendLine("    \"coreSha256\": \"${PokeballBaseline.CORE_SHA256}\",")
        appendLine("    \"coreVersion\": \"${PokeballBaseline.CORE_VERSION}\"")
        appendLine("  },")
        appendJsonStringArray("modules", leafProjects.sorted(), trailingComma = true)
        appendLine("  \"authorities\": [")
        authorityModules.entries.forEachIndexed { index, (authority, modules) ->
            appendLine("    {")
            appendLine("      \"id\": \"${jsonEscape(authority)}\",")
            append("      \"modules\": ")
            appendInlineJsonStringArray(modules.sorted())
            appendLine(",")
            append("      \"sourceEvidence\": ")
            appendInlineJsonStringArray(production.filter { projectPathForSource(it.relativePath) in modules }
                .map(SourceDocument::relativePath).sorted())
            appendLine()
            append("    }")
            appendLine(if (index == authorityModules.size - 1) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"applicationSurfaces\": [")
        applicationSurfaces.entries.forEachIndexed { index, (authority, module) ->
            append("    {\"authority\": \"${jsonEscape(authority)}\", \"module\": \"${jsonEscape(module)}\", \"sources\": ")
            appendInlineJsonStringArray(production.filter { projectPathForSource(it.relativePath) == module }
                .map(SourceDocument::relativePath).sorted())
            append("}")
            appendLine(if (index == applicationSurfaces.size - 1) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"compileEdges\": [")
        sortedEdges.forEachIndexed { index, edge ->
            append("    {\"configuration\": \"${jsonEscape(edge.configuration)}\", \"source\": \"${jsonEscape(edge.source)}\", \"target\": \"${jsonEscape(edge.target)}\"}")
            appendLine(if (index == sortedEdges.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendJsonStringArray("directControlEdges", directControl.edges.sorted(), trailingComma = true)
        appendJsonStringArray("dependencyViolations", directControl.violations, trailingComma = true)
        appendJsonStringArray("behaviorEvidence", runtimeBehaviorEvidence.map { it.className }.sorted(), trailingComma = false)
        appendLine("}")
    }
}

private fun StringBuilder.appendJsonStringArray(
    name: String,
    values: Collection<String>,
    trailingComma: Boolean,
) {
    appendLine("  \"${jsonEscape(name)}\": [")
    values.forEachIndexed { index, value ->
        append("    \"${jsonEscape(value)}\"")
        appendLine(if (index == values.size - 1) "" else ",")
    }
    append("  ]")
    appendLine(if (trailingComma) "," else "")
}

private fun StringBuilder.appendInlineJsonStringArray(values: Collection<String>) {
    append(values.joinToString(prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" })
}

internal fun jsonEscape(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u${character.code.toString(16).padStart(4, '0')}")
            } else {
                append(character)
            }
        }
    }
}

internal fun readUtf8(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)

internal fun String.countOccurrences(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = 0
    while (true) {
        index = indexOf(needle, index)
        if (index < 0) return count
        count++
        index += needle.length
    }
}
