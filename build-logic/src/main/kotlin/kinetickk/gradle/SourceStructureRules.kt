// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

/** One Kotlin source file; [path] is repository-relative with `/` separators. */
internal data class RepositorySource(val path: String, val text: String)

/**
 * The source-structure rules. Limits live only here; docs/project/ARCHITECTURE.md quotes the same
 * numbers. A [baselinable] rule is a ratchet whose current offenders are listed with a ceiling in
 * build-logic/source-structure-baseline.tsv; a [carryable] row follows a moved file or function.
 */
internal enum class StructureRule(
    val id: String,
    val limit: Int,
    val baselinable: Boolean,
    val carryable: Boolean,
    val unit: String,
    val fix: String,
) {
    MAIN_FILE_LINES(
        "main-file-lines", 600, true, true, "lines",
        "Split the file along a cohesive section (a type, a layer, a panel, a data family) into a new file " +
            "in the same package or a new sub-package. Do not raise the ceiling.",
    ),
    TEST_FILE_LINES(
        "test-file-lines", 1000, true, true, "lines",
        "Split the test class along its scenarios into a new class in the same package. If the class is a gate " +
            "listed in DesktopTestShard, list the new class in the same shard. Do not raise the ceiling.",
    ),
    FUNCTION_LINES(
        "function-lines", 100, true, true, "lines",
        "Extract named helpers per phase; split composables into child composables.",
    ),
    PACKAGE_FILES(
        "package-files", 25, true, false, "files",
        "Move a cohesive group of files into a sub-package (the package must match the directory).",
    ),
    RENDERER_NUCLEUS_IMPORTS(
        "renderer-nucleus-imports", 0, true, true, "imports",
        "Add what you need to the render contract, or move a pure helper to :foundation:common.",
    ),
    FEATURE_IMPL_IMPORTS(
        "feature-impl-imports", 0, true, true, "imports",
        "Move the shared symbol to that feature's api or a neutral package.",
    ),
    PACKAGE_CYCLES(
        "package-cycles", 0, true, false, "packages in cycles",
        "Move the shared symbol into a lower package both can import.",
    ),
    PACKAGE_DIRECTORY(
        "package-directory", 0, false, false, "",
        "Move the file or fix the package declaration.",
    ),
    CONTRACT_IMPORTS(
        "contract-imports", 0, false, false, "imports",
        "Move the type into the render contract, or project it in the mapper.",
    ),
    FUNCTION_SCAN(
        "function-scan", 0, false, false, "",
        "Move the literal that hides a brace inside a nested string template out of the template into a val.",
    ),
    RULE_CONFIG(
        "rule-config", 0, false, false, "",
        "Update the constant in SourceStructureRules.kt to the current package name or leaf module.",
    ),
    ARCHITECTURE_DOC_PATHS(
        "architecture-doc-paths", 0, false, false, "",
        "Update docs/project/ARCHITECTURE.md to name a path that exists.",
    ),
    ;

    companion object {
        fun byId(id: String): StructureRule? = entries.firstOrNull { it.id == id }
    }
}

internal data class StructureConfig(
    val contractPackages: List<String> = listOf(
        "kinetickk.ball.gameplay.nucleus.render",
        "kinetickk.ball.gameplay.nucleus.protocol",
    ),
    val cycleTolerantRoots: List<String> = listOf("kinetickk.ball.gameplay.nucleus.simulation"),
    val compositionRootPackage: String = "kinetickk.app",
)

/** A value over the rule's limit (or a hard-rule hit); [line] is 1-based, 0 when not applicable. */
internal data class Measurement(
    val rule: StructureRule,
    val key: String,
    val value: Int,
    val detail: String = "",
    val line: Int = 0,
)

/** Measurements plus the scan totals printed in the success log. */
internal data class StructureScan(val files: Int, val functions: Int, val measurements: List<Measurement>)

internal fun measureSourceStructure(
    sources: List<RepositorySource>,
    leafModuleDirectories: Set<String>,
    architectureDocument: String?,
    pathExists: (String) -> Boolean,
    config: StructureConfig = StructureConfig(),
): List<Measurement> = scanSourceStructure(sources, leafModuleDirectories, architectureDocument, pathExists, config).measurements

internal fun scanSourceStructure(
    sources: List<RepositorySource>,
    leafModuleDirectories: Set<String>,
    architectureDocument: String?,
    pathExists: (String) -> Boolean,
    config: StructureConfig = StructureConfig(),
): StructureScan {
    val scan = SourceTreeScan(config)
    sources.sortedBy { it.path }.forEach { scan.add(it, leafModuleDirectories) }
    val measurements = scan.finish().toMutableList()
    if (architectureDocument != null) measurements += architectureDocPathMeasurements(architectureDocument, pathExists)
    return StructureScan(
        files = scan.files,
        functions = scan.functions,
        measurements = measurements.sortedWith(compareBy({ it.rule.id }, { it.key }, { it.line })),
    )
}

private class ProductionImport(val pkg: String, val imported: String, val path: String, val line: Int)

private class SourceTreeScan(private val config: StructureConfig) {
    var files = 0
    var functions = 0
    private val measurements = mutableListOf<Measurement>()
    private val packageFiles = sortedMapOf<String, Int>()
    private val modulePackages = sortedMapOf<String, MutableSet<String>>()
    private val moduleImports = mutableMapOf<String, MutableList<ProductionImport>>()

    fun add(source: RepositorySource, leafModuleDirectories: Set<String>) {
        val path = source.path
        val srcIndex = path.indexOf("/src/")
        val kotlinIndex = path.indexOf("/kotlin/", startIndex = maxOf(srcIndex, 0))
        val moduleDirectory = if (srcIndex > 0) path.substring(0, srcIndex) else ""
        if (srcIndex <= 0 || kotlinIndex < 0 || moduleDirectory !in leafModuleDirectories) {
            measurements += Measurement(StructureRule.RULE_CONFIG, path, 1, "is not a Kotlin source of a leaf module")
            return
        }
        files++
        val sourceSet = path.substring(srcIndex + 5).substringBefore('/')
        val isTest = sourceSet.endsWith("Test")
        val module = ":" + moduleDirectory.replace('/', ':')
        val lineCount = physicalLineCount(source.text)
        val lineRule = if (isTest) StructureRule.TEST_FILE_LINES else StructureRule.MAIN_FILE_LINES
        if (lineCount > lineRule.limit) measurements += Measurement(lineRule, path, lineCount)

        val masked = maskKotlinSource(source.text)
        val relative = path.substring(kotlinIndex + "/kotlin/".length)
        val expected = if ('/' in relative) relative.substringBeforeLast('/').replace('/', '.') else ""
        val declared = kotlinPackage(masked) ?: ""
        if (declared != expected) {
            measurements += Measurement(
                StructureRule.PACKAGE_DIRECTORY, path, 1,
                "declares package '${declared.ifEmpty { "<none>" }}', directory expects '${expected.ifEmpty { "<none>" }}'",
            )
        }
        if (isTest) return

        packageFiles.merge(path.substringBeforeLast('/'), 1, Int::plus)
        modulePackages.getOrPut(module) { sortedSetOf() } += declared
        if (!bracesBalance(masked)) {
            measurements += Measurement(StructureRule.FUNCTION_SCAN, path, 1, "braces do not balance after masking literals")
            return
        }
        if (!measureFunctions(path, masked)) return
        measureImports(module, declared, path, masked)
    }

    /** Returns false (after recording a function-scan violation) when a body cannot be closed. */
    private fun measureFunctions(path: String, masked: String): Boolean {
        val scanned = try {
            scanKotlinFunctions(masked)
        } catch (failure: FunctionScanException) {
            measurements += Measurement(StructureRule.FUNCTION_SCAN, path, 1, failure.message.orEmpty())
            return false
        }
        functions += scanned.size
        val longest = linkedMapOf<String, ScannedFunction>()
        scanned.filterNot { it.isDataTable }.forEach { function ->
            val current = longest[function.name]
            if (current == null || function.lines > current.lines) longest[function.name] = function
        }
        longest.values.filter { it.lines > StructureRule.FUNCTION_LINES.limit }.forEach { function ->
            measurements += Measurement(StructureRule.FUNCTION_LINES, "$path#${function.name}", function.lines, line = function.line)
        }
        return true
    }

    private fun measureImports(module: String, pkg: String, path: String, masked: String) {
        val renderer = mutableListOf<KotlinImport>()
        val feature = mutableListOf<KotlinImport>()
        val contract = mutableListOf<KotlinImport>()
        val inContractPackage = config.contractPackages.any { pkg.isSameOrUnder(it) }
        val compositionRoot = pkg.isSameOrUnder(config.compositionRootPackage)
        kotlinImports(masked).forEach { import ->
            val imported = import.name
            moduleImports.getOrPut(module) { mutableListOf() } += ProductionImport(pkg, imported, path, import.line)
            val importsContract = config.contractPackages.any { imported.isSameOrUnder(it) }
            if (module.endsWith(":interaction") && NUCLEUS_IMPORT.containsMatchIn(imported) && !importsContract) {
                renderer += import
            }
            if (inContractPackage && imported.startsWith(GAMEPLAY_NUCLEUS_PREFIX) && !importsContract) contract += import
            val segments = imported.split('.')
            // The first segment is `kinetickk`, so the first `impl` is never at index 0 here.
            val implIndex = segments.indexOf("impl")
            if (segments[0] == "kinetickk" && implIndex >= 1) {
                val featureRoot = segments.subList(0, implIndex).joinToString(".")
                if (!pkg.isSameOrUnder(featureRoot) && !compositionRoot) feature += import
            }
        }
        recordImports(StructureRule.RENDERER_NUCLEUS_IMPORTS, path, renderer)
        recordImports(StructureRule.FEATURE_IMPL_IMPORTS, path, feature)
        recordImports(StructureRule.CONTRACT_IMPORTS, path, contract)
    }

    private fun recordImports(rule: StructureRule, path: String, imports: List<KotlinImport>) {
        if (imports.isEmpty()) return
        measurements += Measurement(
            rule, path, imports.size,
            imports.joinToString("\n") { "line ${it.line}: import ${it.name}" },
        )
    }

    fun finish(): List<Measurement> {
        packageFiles.forEach { (directory, count) ->
            if (count > StructureRule.PACKAGE_FILES.limit) measurements += Measurement(StructureRule.PACKAGE_FILES, directory, count)
        }
        modulePackages.forEach { (module, packages) ->
            packageCycleMeasurement(module, packages, moduleImports[module].orEmpty())?.let { measurements += it }
        }
        val allPackages = modulePackages.values.flatten().toSet()
        (config.contractPackages + config.cycleTolerantRoots).forEach { root ->
            if (allPackages.none { it.isSameOrUnder(root) }) {
                measurements += Measurement(StructureRule.RULE_CONFIG, root, 1, "matches no production package")
            }
        }
        return measurements
    }

    private fun collapse(pkg: String): String = config.cycleTolerantRoots.firstOrNull { pkg.isSameOrUnder(it) } ?: pkg

    private fun packageCycleMeasurement(module: String, packages: Set<String>, imports: List<ProductionImport>): Measurement? {
        val graph = sortedMapOf<String, java.util.SortedMap<String, ProductionImport>>()
        imports.forEach { import ->
            val target = resolvePackage(import.imported, packages) ?: return@forEach
            val from = collapse(import.pkg)
            val to = collapse(target)
            if (from != to) graph.getOrPut(from) { sortedMapOf() }.putIfAbsent(to, import)
        }
        val nodes = packages.map(::collapse).toSortedSet()
        val components = stronglyConnectedComponents(nodes, graph.mapValues { it.value.keys.toList() })
        val size = components.sumOf { it.size }
        if (size <= StructureRule.PACKAGE_CYCLES.limit) return null
        val detail = components.joinToString("\n") { component ->
            val members = component.toSet()
            buildString {
                append("cycle: ").append(component.joinToString(", "))
                component.forEach { from ->
                    graph[from].orEmpty().filterKeys { it in members }.forEach { (to, witness) ->
                        append("\n  ").append(from).append(" -> ").append(to).append(": ")
                            .append(witness.path).append(':').append(witness.line).append(" import ").append(witness.imported)
                    }
                }
            }
        }
        return Measurement(StructureRule.PACKAGE_CYCLES, module, size, detail)
    }

    private companion object {
        const val GAMEPLAY_NUCLEUS_PREFIX = "kinetickk.ball.gameplay.nucleus."
        val NUCLEUS_IMPORT = Regex("^kinetickk\\.(?:\\w+\\.)*nucleus(?:\\.|$)")
    }
}

/** Longest declared package of the module that prefixes the import (`a.b.*` tries `a.b` first). */
private fun resolvePackage(imported: String, packages: Set<String>): String? {
    if (imported.endsWith(".*") && imported.dropLast(2) in packages) return imported.dropLast(2)
    val parts = imported.trimEnd('*').trimEnd('.').split('.')
    for (count in parts.size downTo 1) {
        val candidate = parts.subList(0, count).joinToString(".")
        if (candidate in packages) return candidate
    }
    return null
}

/** Tarjan's algorithm over sorted nodes and successors; returns components of two or more nodes, each sorted. */
private fun stronglyConnectedComponents(nodes: Set<String>, graph: Map<String, List<String>>): List<List<String>> {
    val index = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val stack = ArrayDeque<String>()
    val onStack = mutableSetOf<String>()
    val components = mutableListOf<List<String>>()
    var counter = 0

    fun strong(v: String) {
        index[v] = counter
        low[v] = counter
        counter++
        stack.addLast(v)
        onStack += v
        for (w in graph[v].orEmpty().sorted()) {
            if (w !in index) {
                strong(w)
                low[v] = minOf(low.getValue(v), low.getValue(w))
            } else if (w in onStack) {
                low[v] = minOf(low.getValue(v), index.getValue(w))
            }
        }
        if (low[v] == index[v]) {
            val component = mutableListOf<String>()
            do {
                val w = stack.removeLast()
                onStack -= w
                component += w
            } while (w != v)
            if (component.size > 1) components += component.sorted()
        }
    }

    nodes.sorted().forEach { if (it !in index) strong(it) }
    return components
}

private fun String.isSameOrUnder(root: String): Boolean = this == root || startsWith("$root.")

/**
 * `architecture-doc-paths`: every repository path in a backtick span and every relative markdown
 * link of docs/project/ARCHITECTURE.md must exist. Spans with placeholders (`⟨…⟩`, `…`, `*`) are skipped.
 */
internal fun architectureDocPathMeasurements(document: String, pathExists: (String) -> Boolean): List<Measurement> {
    val lines = KotlinLineIndex(document)
    val missing = mutableListOf<Measurement>()
    fun report(path: String, offset: Int) {
        missing += Measurement(
            StructureRule.ARCHITECTURE_DOC_PATHS, path, 1,
            "named in $ARCHITECTURE_DOCUMENT_PATH does not exist", line = lines.lineOf(offset),
        )
    }
    BACKTICK_SPAN.findAll(document).forEach { span ->
        val content = span.groupValues[1]
        if (!DOC_PATH.matches(content) && content !in DOC_ROOT_FILES) return@forEach
        val path = content.replace(LINE_SUFFIX, "").substringBefore('#')
        if (!pathExists(path)) report(path, span.range.first)
    }
    MARKDOWN_LINK.findAll(document).forEach { link ->
        val target = link.groupValues[1].trim()
        if (target.startsWith("http:") || target.startsWith("https:") || target.startsWith("mailto:") || target.startsWith("#")) {
            return@forEach
        }
        val path = normalizeRepositoryPath("docs/project/" + target.substringBefore('#'))
        if (path == null || !pathExists(path)) report(path ?: target, link.range.first)
    }
    return missing
}

private fun normalizeRepositoryPath(path: String): String? {
    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) return null else segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}

internal const val ARCHITECTURE_DOCUMENT_PATH = "docs/project/ARCHITECTURE.md"

private val BACKTICK_SPAN = Regex("`([^`\\n]+)`")
private val DOC_PATH = Regex("^(?:app|ball|flow|foundation|resource|build-logic|docs|tools|gradle|\\.github)/[^\\s<>*{}…⟨⟩]*$")
private val DOC_ROOT_FILES = setOf("AGENTS.md", "README.md", "settings.gradle.kts", "build.gradle.kts")
private val LINE_SUFFIX = Regex(":\\d+(?:-\\d+)?$")
private val MARKDOWN_LINK = Regex("\\]\\(([^)\\s]+)\\)")
