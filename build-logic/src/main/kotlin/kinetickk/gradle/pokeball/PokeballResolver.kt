// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal data class SnapshotVerification(
    val coreFiles: Int,
    val coreBytes: Long,
    val coreSha256: String,
    val agentPackFiles: Int,
    val agentPackSha256: String,
)

internal fun resolveArchitectureViolations(
    leafProjects: Set<String>,
    edges: Set<ProjectEdge>,
    sources: List<SourceDocument>,
    architectureRecords: Map<String, String>,
): List<String> = buildList {
    val sourceByPath = sources.associateBy(SourceDocument::relativePath)

    if (leafProjects != expectedLeafProjects) {
        val missing = expectedLeafProjects - leafProjects
        val unexpected = leafProjects - expectedLeafProjects
        if (missing.isNotEmpty()) add("Missing Pokeball modules: ${missing.joinToString()}")
        if (unexpected.isNotEmpty()) add("Unexpected Pokeball modules: ${unexpected.sorted().joinToString()}")
    }

    addGraphViolations(edges, sources)
    addPackageAndImportViolations(sources)
    addAll(platformCapabilityBoundaryViolations(sources))
    addAll(resourceFaultStageViolations(sources))
    addAll(audioRuntimeFaultStageViolations(sources))
    addAll(
        audioProjectionPolicyViolations(
            policy = architectureRecords["policy.md"].orEmpty(),
            applicability = architectureRecords["applicability.md"].orEmpty(),
        ),
    )
    addApplicationSurfaceViolations(sources)
    addAll(leastAuthorityCompositionViolations(sources))
    addAuthorityViolations(sourceByPath)
    addAll(foundationAndRegistryViolations(sources))
    addAssemblyViolations(sourceByPath)
    addAll(
        auditPolicyViolations(
            policy = architectureRecords["policy.md"].orEmpty(),
            applicability = architectureRecords["applicability.md"].orEmpty(),
            evidenceByPath = sourceByPath.mapValues { (_, source) -> source.text },
        ),
    )
    addRecordViolations(architectureRecords)
}.distinct().sorted()

private fun MutableList<String>.addGraphViolations(
    edges: Set<ProjectEdge>,
    sources: List<SourceDocument>,
) {
    addAll(foreignInternalAccessViolations(edges, sources))
    addAll(androidApplicationHostBoundaryViolations(edges))
    addAll(androidApplicationHostSourceViolations(sources))
    val productionEdges = edges.filterNot(ProjectEdge::isTest)
    findCycle(productionEdges.map { it.source to it.target })?.let { cycle ->
        add("Compile-time project graph contains a cycle: ${cycle.joinToString(" -> ")}")
    }
    val directControl = resolvedSemanticDirectControl(edges, sources)
    addAll(directControl.violations)
    val authorityEdges = directControl.edges.map { encoded ->
        val (source, target) = encoded.split(" -> ", limit = 2)
        source to target
    }
    findCycle(authorityEdges)?.let { cycle ->
        add("Direct-control authority graph contains a cycle: ${cycle.joinToString(" -> ")}")
    }

    val appSharedTargets = productionEdges.filter { it.source == ":app:shared" }.map { it.target }.toSet()
    if (":flow:session:nucleus" in appSharedTargets) {
        add("App Assembly may depend on the Session Application Surface/Impl, not Session Nucleus directly")
    }

    val requiredSurfaceEdges = setOf(
        ":ball:profile:nucleus" to ":ball:content:api",
        ":ball:profile:nucleus" to ":ball:profile:api",
        ":ball:gameplay:nucleus" to ":ball:content:api",
        ":ball:gameplay:nucleus" to ":ball:profile:api",
        ":ball:gameplay:nucleus" to ":ball:gameplay:api",
        ":flow:session:nucleus" to ":ball:content:api",
        ":flow:session:nucleus" to ":ball:profile:api",
        ":flow:session:nucleus" to ":ball:gameplay:api",
        ":flow:session:nucleus" to ":flow:session:api",
    )
    val actualPairs = productionEdges.map { it.source to it.target }.toSet()
    (requiredSurfaceEdges - actualPairs).forEach { (source, target) ->
        add("Required Application Surface import is missing: $source -> $target")
    }
}

internal fun androidApplicationHostBoundaryViolations(
    edges: Set<ProjectEdge>,
): List<String> = buildList {
    val actual = edges
        .filter { edge -> edge.source == ":app:android" && !edge.isTest }
        .toSet()
    val missing = expectedAndroidHostProductionEdges - actual
    val unexpected = actual - expectedAndroidHostProductionEdges
    if (missing.isNotEmpty()) {
        add(
            "Pure Android application host is missing its exact production edge: " +
                missing.sortedBy(ProjectEdge::encoded).joinToString { edge -> edge.encoded },
        )
    }
    if (unexpected.isNotEmpty()) {
        add(
            "Pure Android application host has an unexpected production edge: " +
                unexpected.sortedBy(ProjectEdge::encoded).joinToString { edge -> edge.encoded },
        )
    }
}.distinct().sorted()

internal fun androidApplicationHostSourceViolations(
    sources: List<SourceDocument>,
): List<String> = buildList {
    val hostSources = sources
        .filter(SourceDocument::isProductionKotlinSource)
        .filter { source -> projectPathForSource(source.relativePath) == ":app:android" }
        .associateBy(SourceDocument::relativePath)
    val actualPaths = hostSources.keys
    val missing = expectedAndroidHostProductionSources - actualPaths
    val unexpected = actualPaths - expectedAndroidHostProductionSources
    if (missing.isNotEmpty()) {
        add("Pure Android application host is missing exact source ${missing.sorted().joinToString()}")
    }
    if (unexpected.isNotEmpty()) {
        add("Pure Android application host contains unexpected production source ${unexpected.sorted().joinToString()}")
    }

    hostSources[ANDROID_HOST_ACTIVITY_PATH]?.let { activity ->
        listOf(
            "class MainActivity : ComponentActivity()",
            "KinetickkApp()",
        ).filterNot(activity.text::contains).forEach { token ->
            add("Pure Android application host activity is missing mechanical host token `$token`")
        }
        listOf(
            "kinetickk.ball.",
            "kinetickk.flow.",
            "kinetickk.foundation.",
            "kinetickk.resource.",
        ).filter(activity.text::contains).forEach { token ->
            add("Pure Android application host activity references forbidden authority detail `$token`")
        }
    }
}.distinct().sorted()

internal fun foreignInternalAccessViolations(
    edges: Set<ProjectEdge>,
    sources: List<SourceDocument>,
): List<String> = buildList {
    edges.asSequence()
        .filter { edge -> edge.target in internalProjectPackages }
        .filter { edge ->
            authorityForKnownProject(edge.source) != authorityForKnownProject(edge.target)
        }
        .filterNot(allowedForeignInternalProjectEdges::contains)
        .forEach { edge ->
            add(
                "Foreign internal dependency ${edge.source} --${edge.configuration}--> ${edge.target} " +
                    "is not an exact declared host/test edge",
            )
        }

    sources.asSequence()
        .filter { source -> source.relativePath.endsWith(".kt") }
        .forEach sourceLoop@{ source ->
            val sourceProject = projectPathForSource(source.relativePath) ?: return@sourceLoop
            val sourceAuthority = authorityForKnownProject(sourceProject) ?: return@sourceLoop
            val pathParts = source.relativePath.split('/')
            val sourceSet = pathParts.indexOf("src").takeIf { it >= 0 }
                ?.let { pathParts.getOrNull(it + 1) }
            val isTestSource = sourceSet == "test" || sourceSet?.endsWith("Test") == true
            internalProjectPackages.forEach targetLoop@{ (targetProject, targetPackage) ->
                if (sourceAuthority == authorityForKnownProject(targetProject) ||
                    !referencesPackage(source.text, targetPackage)
                ) {
                    return@targetLoop
                }
                val hasExactEdge = edges.any { edge ->
                    edge.source == sourceProject &&
                        edge.target == targetProject &&
                        edge in allowedForeignInternalProjectEdges &&
                        (isTestSource || !edge.isTest)
                }
                if (!hasExactEdge) {
                    add(
                        "Foreign internal access from $sourceProject to $targetProject in " +
                            "${source.relativePath} lacks an exact declared host/test edge",
                    )
                }
            }
        }
}.distinct().sorted()

internal data class ResolvedDirectControl(
    val edges: Set<String>,
    val violations: List<String>,
)

internal fun resolvedSemanticDirectControl(
    edges: Set<ProjectEdge>,
    sources: List<SourceDocument>,
): ResolvedDirectControl {
    val semanticAuthorities = applicationSurfaces.keys
    val surfaceAuthorityByModule = applicationSurfaces.entries.associate { (authority, module) ->
        module to authority
    }
    val surfacePackageByAuthority = mapOf(
        "ContentCatalog" to "kinetickk.ball.content.api",
        "Profile" to "kinetickk.ball.profile.api",
        "GameplayRun" to "kinetickk.ball.gameplay.api",
        "AppSession" to "kinetickk.flow.session.api",
    )
    val productionEdges = edges.filterNot(ProjectEdge::isTest)
    val dependencyAdjacency = productionEdges.groupBy(ProjectEdge::source, ProjectEdge::target)
    val dependencyUses = productionEdges.mapNotNull { edge ->
        val sourceAuthority = authorityForKnownProject(edge.source) ?: return@mapNotNull null
        val targetAuthority = surfaceAuthorityByModule[edge.target] ?: return@mapNotNull null
        if (sourceAuthority !in semanticAuthorities || sourceAuthority == targetAuthority) return@mapNotNull null
        SurfaceDependencyUse(
            sourceProject = edge.source,
            sourceAuthority = sourceAuthority,
            targetProject = edge.target,
            targetAuthority = targetAuthority,
        )
    }.toSet()
    val sourceUses = sources.asSequence()
        .filter(SourceDocument::isProductionKotlinSource)
        .flatMap { source ->
            val sourceProject = projectPathForSource(source.relativePath) ?: return@flatMap emptySequence()
            val sourceAuthority = authorityForKnownProject(sourceProject) ?: return@flatMap emptySequence()
            if (sourceAuthority !in semanticAuthorities) return@flatMap emptySequence()
            surfacePackageByAuthority.asSequence().mapNotNull { (targetAuthority, packageName) ->
                if (sourceAuthority == targetAuthority || !importsPackage(source.text, packageName)) {
                    return@mapNotNull null
                }
                SurfaceSourceUse(
                    sourceProject = sourceProject,
                    sourceAuthority = sourceAuthority,
                    targetProject = applicationSurfaces.getValue(targetAuthority),
                    targetAuthority = targetAuthority,
                    sourcePath = source.relativePath,
                )
            }
        }
        .toSet()

    val violations = buildList {
        sourceUses.forEach { use ->
            if (!hasDependencyPath(use.sourceProject, use.targetProject, dependencyAdjacency)) {
                add(
                    "Foreign Application Surface use in ${use.sourcePath} lacks a production dependency path " +
                        "${use.sourceProject} -> ${use.targetProject}",
                )
            }
        }
        dependencyUses.forEach { dependency ->
            if (sourceUses.none { use ->
                    use.sourceProject == dependency.sourceProject &&
                        use.targetProject == dependency.targetProject
                }
            ) {
                add(
                    "Foreign Application Surface dependency ${dependency.sourceProject} -> " +
                        "${dependency.targetProject} has no production source use",
                )
            }
        }

        val dependencyAuthorityEdges = dependencyUses.map(SurfaceDependencyUse::authorityEdge).toSortedSet()
        val sourceAuthorityEdges = sourceUses.map(SurfaceSourceUse::authorityEdge).toSortedSet()
        if (dependencyAuthorityEdges != sourceAuthorityEdges) {
            add(
                "Foreign Application Surface dependency/use graph mismatch: dependencies " +
                    "${dependencyAuthorityEdges.joinToString()}, uses ${sourceAuthorityEdges.joinToString()}",
            )
        }

    }.distinct().sorted()
    return ResolvedDirectControl(
        edges = sourceUses.map(SurfaceSourceUse::authorityEdge).toSortedSet(),
        violations = violations,
    )
}

private fun hasDependencyPath(
    source: String,
    target: String,
    adjacency: Map<String, List<String>>,
): Boolean {
    val pending = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    pending.addLast(source)
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (!visited.add(current)) continue
        adjacency[current].orEmpty().forEach { next ->
            if (next == target) return true
            pending.addLast(next)
        }
    }
    return false
}

private data class SurfaceDependencyUse(
    val sourceProject: String,
    val sourceAuthority: String,
    val targetProject: String,
    val targetAuthority: String,
) {
    val authorityEdge: String
        get() = "$sourceAuthority -> $targetAuthority"
}

private data class SurfaceSourceUse(
    val sourceProject: String,
    val sourceAuthority: String,
    val targetProject: String,
    val targetAuthority: String,
    val sourcePath: String,
) {
    val authorityEdge: String
        get() = "$sourceAuthority -> $targetAuthority"
}

private fun importsPackage(text: String, packageName: String): Boolean =
    referencesPackage(text.maskKotlinNonCode(), packageName)

// Forbidden namespaces are a conservative source guard, including string-template expressions.
// Literal/comment mentions can also be reported; this is not a Kotlin semantic analyzer.
private fun referencesPackage(text: String, packageName: String): Boolean =
    Regex("(?m)^import\\s+${Regex.escape(packageName)}(?:\\.|\\s*$)").containsMatchIn(text) ||
        Regex("(?m)^(?!package\\s).*${Regex.escape(packageName)}\\.").containsMatchIn(text)

internal fun projectPathForSource(relativePath: String): String? {
    val parts = relativePath.split('/')
    return when (parts.firstOrNull()) {
        "app", "foundation" -> parts.getOrNull(1)?.let { ":${parts[0]}:$it" }
        "ball", "flow", "resource" -> {
            val owner = parts.getOrNull(1) ?: return null
            val role = parts.getOrNull(2) ?: return null
            ":${parts[0]}:$owner:$role"
        }
        else -> null
    }
}

private fun authorityForKnownProject(projectPath: String): String? = runCatching {
    authorityFor(projectPath)
}.getOrNull()

internal fun MutableList<String>.addPackageAndImportViolations(sources: List<SourceDocument>) {
    sources.forEach { source ->
        val path = source.relativePath
        val text = source.text.maskKotlinNonCode()
        if (path.endsWith(".kt")) {
            val kotlinRoot = Regex("(?:^|/)src/[^/]+/kotlin/").find(path)
            val packageName = Regex("(?m)^package ([A-Za-z0-9_.]+)\\s*$")
                .find(text)
                ?.groupValues
                ?.get(1)
            if (kotlinRoot != null && packageName == null) {
                add("Kotlin source $path has no package declaration")
            } else if (kotlinRoot != null && packageName != null) {
                val expectedDirectory = packageName.replace('.', '/')
                val actualDirectory = path.substring(kotlinRoot.range.last + 1).substringBeforeLast('/')
                if (actualDirectory != expectedDirectory) {
                    add("Package/path mismatch in $path: package $packageName")
                }
            }
        }

        if ("kinetickk.core." in source.text || "kinetickk.feature." in source.text) {
            add("Legacy core/feature namespace is forbidden in $path")
        }

        val isNucleusProduction = path.contains("/nucleus/src/") && source.isProductionKotlinSource()
        if (isNucleusProduction) {
            val forbidden = listOf(
                "androidx.compose",
                "kotlinx.browser",
                "java.awt",
                "java.io",
                "java.nio",
                ".interaction.",
                ".impl.",
                ".resource.",
                "kinetickk.resource.",
                "kinetickk.app.",
                "kinetickk.foundation.dispatch",
            )
            forbidden.filter(source.text::contains).forEach { token ->
                add("Nucleus source $path contains forbidden dependency token `$token`")
            }
        }

    }
}

internal fun applicationSurfaceViolations(sources: List<SourceDocument>): List<String> = buildList {
    val surfaceRoots = linkedMapOf(
        "ball/content/api/" to "kinetickk.ball.content.api",
        "ball/profile/api/" to "kinetickk.ball.profile.api",
        "ball/gameplay/api/" to "kinetickk.ball.gameplay.api",
        "flow/session/api/" to "kinetickk.flow.session.api",
        "resource/audio/api/" to "kinetickk.resource.audio.api",
    )
    val surfaceSources = sources.filter { source ->
        source.relativePath.contains("/src/") &&
            source.isProductionKotlinSource()
    }
    surfaceRoots.forEach { (root, packageName) ->
        if (surfaceSources.none { source -> source.relativePath.startsWith(root) }) {
            add("Missing Application Surface Kotlin source for $packageName under $root")
        }
    }
    surfaceSources.forEach { source ->
        val expectedPackage = surfaceRoots.entries
            .singleOrNull { (root) -> source.relativePath.startsWith(root) }
            ?.value
            ?: return@forEach
        val packageName = Regex("(?m)^package ([A-Za-z0-9_.]+)\\s*$")
            .find(source.text.maskKotlinNonCode())
            ?.groupValues
            ?.get(1)
        if (packageName != expectedPackage) {
            add(
                "Application Surface ${source.relativePath} must declare exact package " +
                    "$expectedPackage; found ${packageName ?: "none"}",
            )
        }

        val forbiddenTokens = listOf(
            "androidx.compose",
            "kotlinx.browser",
            "java.awt",
            "java.io",
            "java.nio",
            ".impl.",
            ".interaction.",
            ".nucleus.",
            "kinetickk.app.",
        )
        forbiddenTokens.filter(source.text::contains).forEach { token ->
            add("Application Surface ${source.relativePath} contains forbidden dependency token `$token`")
        }
        if (Regex("(?m)^\\s*(?:public\\s+)?typealias\\s+").containsMatchIn(source.text.maskKotlinNonCode())) {
            add("Application Surface ${source.relativePath} may not re-export a typealias")
        }
        if (Regex("(?m)^import\\s+[^\\n]+\\.\\*\\s*$").containsMatchIn(source.text.maskKotlinNonCode())) {
            add("Application Surface ${source.relativePath} may not use wildcard imports")
        }
        val sovereignStateNames = listOf("ProfileState", "GameplayState", "AppSessionState")
        sovereignStateNames.forEach { stateName ->
            if (Regex("\\b(?:data\\s+)?(?:class|interface|object|typealias)\\s+$stateName\\b")
                    .containsMatchIn(source.text.maskKotlinNonCode()) ||
                Regex("(?m)^import\\s+[^\\n]*\\.$stateName\\s*$").containsMatchIn(source.text.maskKotlinNonCode())
            ) {
                add("Application Surface ${source.relativePath} exposes foreign sovereign State `$stateName`")
            }
        }
    }
}.distinct().sorted()

private fun MutableList<String>.addApplicationSurfaceViolations(sources: List<SourceDocument>) {
    addAll(applicationSurfaceViolations(sources))
}

private fun MutableList<String>.addAuthorityViolations(sources: Map<String, SourceDocument>) {
    // Ownership is enforced by module/API boundaries. Atomic publication and serialized
    // writes are exercised through the real owner bindings (runtimeBehaviorEvidence),
    // independently of field names, local variables and extracted helpers.
    val contentSurface = sources[
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentSnapshots.kt"
    ]?.text.orEmpty()
    if ("interface ContentCatalog" !in contentSurface) {
        add("ContentCatalog must remain one immutable query-only Application Surface")
    }
}

internal fun foundationAndRegistryViolations(sources: List<SourceDocument>): List<String> = buildList {
    val production = sources.filter(SourceDocument::isProductionKotlinSource)
    production.filter { it.relativePath.startsWith("foundation/") }.forEach { source ->
        val forbidden = listOf(
            "import kinetickk.ball.",
            "import kinetickk.flow.",
            "import kinetickk.resource.",
            "PlayerProfile",
            "ProfilePulse",
            "GameplayPulse",
            "AppSession",
            "ItemEffect",
            "ItemRarity",
            "WeaponId",
            "MetaUpgradeId",
            "RelicId",
        )
        forbidden.filter(source.text.maskKotlinNonCode()::contains).forEach { token ->
            add("Foundation source ${source.relativePath} contains business/domain token `$token`")
        }
    }

    val registryTokens = listOf(
        "ServiceLocator",
        "GlobalRegistry",
        "EventBus",
        "MutableSharedFlow",
        "Channel<",
        "CoroutineScope(",
        "Class.forName(",
    )
    production.forEach { source ->
        registryTokens.filter(source.text.maskKotlinNonCode()::contains).forEach { token ->
            add("Dynamic registry/bus/queue token `$token` is forbidden in ${source.relativePath}")
        }
        if (Regex("(?m)^(?:public |internal |private )?(?:lateinit )?var\\s+").containsMatchIn(source.text.maskKotlinNonCode())) {
            add("Top-level mutable global is forbidden in ${source.relativePath}")
        }
    }
}.distinct().sorted()

private fun MutableList<String>.addAssemblyViolations(sources: Map<String, SourceDocument>) {
    val app = sources[
        "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
    ]?.text.orEmpty()
    if (app.isEmpty()) {
        add("Missing static App Assembly")
    } else {
        listOf(".nucleus.")
            .filter(app::contains)
            .forEach { token ->
            add("App Assembly constructs or imports forbidden business detail `$token`")
        }
    }
}

internal data class ArchitectureTableRow(
    val id: String,
    val cells: List<String>,
) {
    val text: String
        get() = cells.joinToString(" | ")
}

internal fun parseArchitectureTableRows(markdown: String, heading: String): List<ArchitectureTableRow> {
    val section = markdown.substringAfter(heading, missingDelimiterValue = "")
        .substringBefore("\n## ")
    if (section.isEmpty()) return emptyList()
    return section.lineSequence()
        .map(String::trim)
        .dropWhile { line -> !line.startsWith('|') }
        .takeWhile { line -> line.startsWith('|') && line.endsWith('|') }
        .map { line -> line.removePrefix("|").removeSuffix("|").split('|').map(String::trim) }
        .filter { cells ->
            cells.isNotEmpty() &&
                cells.first() !in setOf("ID", "Route ID", "FlowParticipation ID", "Absent scope") &&
                !cells.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } }
        }
        .mapNotNull { cells ->
            val id = cells.first().removeSurrounding("`")
            id.takeIf(String::isNotBlank)?.let { ArchitectureTableRow(it, cells) }
        }
        .toList()
}

private fun MutableList<String>.addRecordViolations(records: Map<String, String>) {
    val requiredRecords = setOf(
        "README.md",
        "applicability.md",
        "assembly.md",
        "authority-map.md",
        "baseline.md",
        "policy.md",
    )
    (requiredRecords - records.keys).forEach { add("Missing architecture record $it") }

    val applicability = records["applicability.md"].orEmpty()
    val expectedAbsenceScopes = listOf(
        "actors, authentication, tenants, grants, secrets, privileged actions",
        "network, remote deployment, IPC, independently versioned endpoints",
        "detached asynchronous semantic delivery",
        "root idempotency or cancellation protocol",
        "dynamic registry or wildcard routing",
        "process/security isolation",
        "durable outbox, event journal, status materializer, or operation-status query",
    )
    expectedAbsenceScopes.filterNot(applicability::contains).forEach { scope ->
        add("Applicability inventory is missing bounded absence scope `$scope`")
    }
    val absenceRows = parseArchitectureTableRows(applicability, "## Absent trigger scopes")
    if (absenceRows.size != expectedAbsenceScopes.size ||
        absenceRows.map(ArchitectureTableRow::id).toSet() != expectedAbsenceScopes.toSet()
    ) {
        add(
            "Applicability must retain exactly seven bounded absence scopes; found " +
                absenceRows.map(ArchitectureTableRow::id).joinToString(),
        )
    }
}

internal fun requireSnapshotCheckout(snapshotRoot: Path) {
    val gitEntry = snapshotRoot.resolve(".git")
    require(Files.isDirectory(gitEntry) || Files.isRegularFile(gitEntry)) {
        "Pokeball snapshot is not a Git checkout: $snapshotRoot"
    }
    val checkoutRoot = Path.of(runGit(snapshotRoot, "rev-parse", "--show-toplevel").trim())
    require(checkoutRoot.toRealPath() == snapshotRoot.toRealPath()) {
        "Pokeball snapshot must name its Git checkout root: $snapshotRoot"
    }
}

internal fun verifySnapshot(snapshotRoot: Path): SnapshotVerification {
    requireSnapshotCheckout(snapshotRoot)
    val head = runGit(snapshotRoot, "rev-parse", "HEAD").trim()
    require(head == PokeballBaseline.CORE_COMMIT) {
        "Pokeball HEAD mismatch: expected ${PokeballBaseline.CORE_COMMIT}, found $head"
    }
    val status = runGit(snapshotRoot, "status", "--porcelain", "--untracked-files=all")
    require(status.isBlank()) { "Pokeball worktree contains tracked or untracked changes" }
    val origin = runGit(snapshotRoot, "remote", "get-url", "origin").trim()
    require(normalizedRepository(origin) == "github.com/4wl2d/pokeball") {
        "Unexpected Pokeball origin: $origin"
    }

    val entrypoint = snapshotRoot.resolve("spec/pokeball-architecture-core.md")
    val entrypointText = readUtf8(entrypoint)
    require(PokeballBaseline.CORE_VERSION in entrypointText) {
        "Core entrypoint does not declare ${PokeballBaseline.CORE_VERSION}"
    }
    require(PokeballBaseline.CORE_STATUS in entrypointText) {
        "Core entrypoint does not declare ${PokeballBaseline.CORE_STATUS}"
    }
    val manifest = entrypointText.substringAfter("## Canonical document set")
        .substringBefore("## Section index")
    val targets = Regex("(?m)^- \\[[^]]+]\\(([^)]+\\.md)\\)$")
        .findAll(manifest)
        .map { it.groupValues[1] }
        .toList()
    val digest = MessageDigest.getInstance("SHA-256")
    var totalBytes = 0L
    targets.forEach { target ->
        val path = snapshotRoot.resolve("spec").resolve(target).normalize()
        require(path.startsWith(snapshotRoot.resolve("spec").normalize()) && Files.isRegularFile(path)) {
            "Core manifest path is missing or escapes spec/: $target"
        }
        val bytes = Files.readAllBytes(path)
        val relative = snapshotRoot.relativize(path).toString().replace('\\', '/')
        digest.update(relative.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
        digest.update(0.toByte())
        totalBytes += bytes.size
    }
    val coreDigest = digest.digest().joinToString("") { "%02x".format(it) }
    require(targets.size == PokeballBaseline.CORE_FILE_COUNT) {
        "Core file count mismatch: ${targets.size}"
    }
    require(totalBytes == PokeballBaseline.CORE_BYTES) {
        "Core byte count mismatch: $totalBytes"
    }
    require(coreDigest == PokeballBaseline.CORE_SHA256) {
        "Core digest mismatch: $coreDigest"
    }

    val agentRoot = snapshotRoot.resolve("docs/agents")
    val siblings = Files.list(agentRoot).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
            .sorted(compareBy<Path> { it.fileName.toString() })
            .toList()
    }
    val baseline = siblings.singleOrNull { it.fileName.toString() == "BASELINE.md" }
        ?: error("Agent Pack BASELINE.md is missing")
    val baselineText = readUtf8(baseline)
    require("packRevision: ${PokeballBaseline.AGENT_PACK_REVISION}" in baselineText) {
        "Agent Pack revision mismatch"
    }
    val packDigest = MessageDigest.getInstance("SHA-256")
    siblings.filter { it != baseline }.forEach { path ->
        packDigest.update(path.fileName.toString().toByteArray(StandardCharsets.UTF_8))
        packDigest.update(0.toByte())
        packDigest.update(Files.readAllBytes(path))
        packDigest.update(0.toByte())
    }
    val agentDigest = packDigest.digest().joinToString("") { "%02x".format(it) }
    require(siblings.size == PokeballBaseline.AGENT_PACK_FILE_COUNT) {
        "Agent Pack file count mismatch: ${siblings.size}"
    }
    require(agentDigest == PokeballBaseline.AGENT_PACK_SHA256) {
        "Agent Pack digest mismatch: $agentDigest"
    }

    return SnapshotVerification(
        coreFiles = targets.size,
        coreBytes = totalBytes,
        coreSha256 = coreDigest,
        agentPackFiles = siblings.size,
        agentPackSha256 = agentDigest,
    )
}

internal fun normalizedRepository(remote: String): String {
    val withoutTransport = remote.trim().trimEnd('/').removeSuffix(".git").let { value ->
        when {
            value.startsWith("ssh://git@") -> value.removePrefix("ssh://git@")
            value.startsWith("git@") -> value.removePrefix("git@")
            value.startsWith("https://") -> value.removePrefix("https://")
            value.startsWith("http://") -> value.removePrefix("http://")
            else -> value
        }
    }
    return withoutTransport
        .replaceFirst(':', '/')
        .trimEnd('/')
        .lowercase()
}

internal fun runGit(root: Path, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git", "-C", root.toString()) + arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    val exit = process.waitFor()
    check(exit == 0) { "git ${arguments.joinToString(" ")} failed ($exit): ${output.trim()}" }
    return output
}

internal fun gitTreeDigest(repositoryRoot: Path, revision: String): String {
    val paths = runGit(repositoryRoot, "ls-tree", "-r", "--name-only", revision)
        .lineSequence()
        .filter(String::isNotBlank)
        .sorted()
        .toList()
    val digest = MessageDigest.getInstance("SHA-256")
    paths.forEach { path ->
        val bytes = gitBlob(repositoryRoot, revision, path)
        digest.update(path.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun gitBlob(repositoryRoot: Path, revision: String, path: String): ByteArray {
    val process = ProcessBuilder("git", "-C", repositoryRoot.toString(), "show", "$revision:$path")
        .start()
    val bytes = process.inputStream.use { it.readBytes() }
    val error = process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    val exit = process.waitFor()
    check(exit == 0) { "git show $revision:$path failed ($exit): ${error.trim()}" }
    return bytes
}
