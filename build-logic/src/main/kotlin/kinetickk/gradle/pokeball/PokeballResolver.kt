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
    addAll(exactDataClassShapeViolations(sourceByPath, canonicalProtocolDataClassShapes))
    addAll(exactEnumInventoryViolations(sourceByPath, canonicalProtocolEnumInventories))
    addAll(closedDirectSubtypeInventoryViolations(sourceByPath, canonicalClosedProtocolInventories))
    addAll(decisionContextBoundaryViolations(sources))
    addAll(foreignApplicationSurfaceSignatureViolations(sources, foreignApplicationSurfacePolicies))
    addAll(
        publicSourceCompletionWrapperViolations(
            sources,
            setOf("ball/profile/api/", "ball/gameplay/api/", "flow/session/api/"),
        ),
    )
    addAll(forbiddenProtocolSymbolViolations(sourceByPath, forbiddenCompatibilityProtocolSymbols))
    addAll(requiredProtocolEvidenceViolations(sourceByPath, canonicalProtocolEvidenceAnchors))
    addAll(leastAuthorityCompositionViolations(sources))
    addAll(trustedNucleusInputCallsiteViolations(sources))
    addAuthorityViolations(sourceByPath)
    addAll(foundationAndRegistryViolations(sources))
    addAssemblyViolations(sourceByPath)
    addProtocolRouteViolations(sourceByPath, architectureRecords)
    addBoundViolations(sourceByPath, architectureRecords)
    addAll(
        compositionLimitViolations(
            sources = sourceByPath,
            policy = architectureRecords["policy.md"].orEmpty(),
            assembly = architectureRecords["assembly.md"].orEmpty(),
        ),
    )
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
            val isTestSource = source.relativePath.contains("Test/")
            internalProjectPackages.forEach targetLoop@{ (targetProject, targetPackage) ->
                if (sourceAuthority == authorityForKnownProject(targetProject) ||
                    !importsPackage(source.text, targetPackage)
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
    val semanticAuthorities = setOf("AppSession", "GameplayRun", "Profile")
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
        if (sourceAuthorityEdges != expectedSemanticDirectControlEdges) {
            val missing = expectedSemanticDirectControlEdges - sourceAuthorityEdges
            val extra = sourceAuthorityEdges - expectedSemanticDirectControlEdges
            if (missing.isNotEmpty()) {
                add("Direct-control graph is missing source-derived edges: ${missing.joinToString()}")
            }
            if (extra.isNotEmpty()) {
                add("Direct-control graph contains unmapped source-derived edges: ${extra.joinToString()}")
            }
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

private fun MutableList<String>.addPackageAndImportViolations(sources: List<SourceDocument>) {
    sources.forEach { source ->
        val path = source.relativePath
        val text = source.text
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

        if ("kinetickk.core." in text || "kinetickk.feature." in text) {
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
            )
            forbidden.filter(text::contains).forEach { token ->
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
            .find(source.text)
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
        if (Regex("(?m)^\\s*(?:public\\s+)?typealias\\s+").containsMatchIn(source.text)) {
            add("Application Surface ${source.relativePath} may not re-export a typealias")
        }
        if (Regex("(?m)^import\\s+[^\\n]+\\.\\*\\s*$").containsMatchIn(source.text)) {
            add("Application Surface ${source.relativePath} may not use wildcard imports")
        }
        val sovereignStateNames = listOf("ProfileState", "GameplayState", "AppSessionState")
        sovereignStateNames.forEach { stateName ->
            if (Regex("\\b(?:data\\s+)?(?:class|interface|object|typealias)\\s+$stateName\\b")
                    .containsMatchIn(source.text) ||
                Regex("(?m)^import\\s+[^\\n]*\\.$stateName\\s*$").containsMatchIn(source.text)
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
    data class WriterRule(
        val path: String,
        val stateType: String,
        val nucleusPath: String,
        val nucleusToken: String,
        val writerDeclaration: String = "private var committedState: $stateType",
        val requiredWriterTokens: List<String> = emptyList(),
        val forbiddenWriterTokens: List<String> = emptyList(),
    )
    val rules = listOf(
        WriterRule(
            "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt",
            "ProfileState",
            "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleus.kt",
            "object ProfileNucleus",
        ),
        WriterRule(
            "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt",
            "GameplayState",
            "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleus.kt",
            "object GameplayNucleus",
            writerDeclaration = "private var committedFrame: CommittedGameplayFrame",
            requiredWriterTokens = listOf(
                "private val committedState: GameplayState\n        get() = committedFrame.state",
                "private val committedRenderSnapshot: GameplayRenderSnapshot\n" +
                    "        get() = committedFrame.renderSnapshot",
                "private fun publish(state: GameplayState, renderSnapshot: GameplayRenderSnapshot) {\n" +
                    "        committedFrame = CommittedGameplayFrame(state, renderSnapshot)\n    }",
                "private data class CommittedGameplayFrame(\n" +
                    "    val state: GameplayState,\n" +
                    "    val renderSnapshot: GameplayRenderSnapshot,\n)",
            ),
            forbiddenWriterTokens = listOf(
                "private var committedState: GameplayState",
                "private var committedRenderSnapshot: GameplayRenderSnapshot",
            ),
        ),
        WriterRule(
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
            "AppSessionState",
            "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleus.kt",
            "object AppSessionNucleus",
        ),
    )
    rules.forEach { rule ->
        val writer = sources[rule.path]?.text
        if (writer == null) {
            add("Missing sole writer source ${rule.path}")
        } else {
            if (writer.countOccurrences(rule.writerDeclaration) != 1) {
                add(
                    "${rule.stateType} must have exactly one `${rule.writerDeclaration}` " +
                        "semantic writer in ${rule.path}",
                )
            }
            rule.requiredWriterTokens.forEach { token ->
                if (writer.countOccurrences(token) != 1) {
                    add(
                        "${rule.stateType} atomic writer in ${rule.path} must contain exactly one `$token`",
                    )
                }
            }
            rule.forbiddenWriterTokens.forEach { token ->
                if (token in writer) {
                    add("${rule.stateType} atomic writer in ${rule.path} must not contain `$token`")
                }
            }
        }
        val nucleus = sources[rule.nucleusPath]?.text
        if (nucleus == null || nucleus.countOccurrences(rule.nucleusToken) != 1 || "fun decide(" !in nucleus) {
            add("Missing closed pure Nucleus entry in ${rule.nucleusPath}")
        }
    }

    val committedWriterCount = sources.values.sumOf {
        Regex("private var committedState: (ProfileState|GameplayState|AppSessionState)").findAll(it.text).count()
    }
    if (committedWriterCount != 2) {
        add("Expected exactly two direct mutable semantic committedState writers; found $committedWriterCount")
    }
    val atomicGameplayWriterCount = sources.values.sumOf {
        Regex("private var committedFrame: CommittedGameplayFrame").findAll(it.text).count()
    }
    if (atomicGameplayWriterCount != 1) {
        add(
            "Expected exactly one atomic Gameplay state/render semantic writer; " +
                "found $atomicGameplayWriterCount",
        )
    }

    val contentSurface = sources[
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentSnapshots.kt"
    ]?.text.orEmpty()
    if ("interface ContentCatalog" !in contentSurface || Regex("(?m)^\\s*fun (profilePolicy|gameplayContent|uiCatalog)\\(")
            .findAll(contentSurface).count() != 3
    ) {
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
        forbidden.filter(source.text::contains).forEach { token ->
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
        registryTokens.filter(source.text::contains).forEach { token ->
            add("Dynamic registry/bus/queue token `$token` is forbidden in ${source.relativePath}")
        }
        if (Regex("(?m)^(?:public |internal |private )?(?:lateinit )?var\\s+").containsMatchIn(source.text)) {
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
        listOf(".nucleus.", "ProfileModuleCommand(", "GameplayModuleCommand(")
            .filter(app::contains)
            .forEach { token ->
            add("App Assembly constructs or imports forbidden business detail `$token`")
        }
    }
    val router = sources[
        "app/shared/src/commonMain/kotlin/kinetickk/app/shared/ProfileModuleResultRouter.kt"
    ]?.text.orEmpty()
    if (router.isEmpty() ||
        "delivery.result" in router ||
        "when (delivery.commandSource.sourceInstance)" !in router
    ) {
        add("Profile result router must transport exact owner-created results by source identity only")
    }

    val routes = sources[
        "flow/session/api/src/commonMain/kotlin/kinetickk/flow/session/api/SessionRoutes.kt"
    ]?.text.orEmpty()
    addAll(routeInventoryViolations(routes))
}

internal fun routeInventoryViolations(routes: String): List<String> = buildList {
    val declaredRoutes = directSubtypeDeclarationNames(
        routes,
        "sealed interface AppDestination",
        "AppDestination",
    )
    if (declaredRoutes != expectedRouteInventory) {
        add(
            "Closed route protocol must declare exactly seven ordered destinations: " +
                expectedRouteInventory.joinToString(),
        )
    }
    if (declaredRoutes.toSet().size != declaredRoutes.size) {
        add("Closed route protocol contains duplicate AppDestination declarations")
    }
}.distinct().sorted()

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

internal fun protocolRouteViolations(
    sources: Map<String, SourceDocument>,
    assembly: String,
): List<String> = buildList {
    val readRows = parseArchitectureTableRows(assembly, "## Read dependencies")
    val commandRows = parseArchitectureTableRows(assembly, "## Command/result routes")
    addDuplicateRowViolations("read", readRows)
    addDuplicateRowViolations("command", commandRows)
    if (readRows.map(ArchitectureTableRow::id).toSortedSet() != expectedReadRoutes) {
        add("Typed Assembly read table must contain exactly the closed read-route inventory")
    }
    if (commandRows.map(ArchitectureTableRow::id).toSortedSet() != expectedCommandRoutes) {
        add("Typed Assembly command table must contain exactly the closed command-route inventory")
    }

    val readsById = readRows.groupBy(ArchitectureTableRow::id)
    readRouteProjections.forEach { route ->
        val row = readsById[route.id]?.singleOrNull()
        if (row == null) {
            add("Read route ${route.id} must have exactly one typed Assembly row")
        } else {
            val edge = "${route.sourceAuthority} -> ${route.targetAuthority}"
            listOf(edge, route.queryToken, route.resultToken).forEach { token ->
                if (token !in row.text) {
                    add("Read route ${route.id} Assembly row is missing exact typed token `$token`")
                }
            }
        }

        val owner = sources[route.ownerPath]?.text
        if (owner == null) {
            add("Read route ${route.id} is missing target protocol source ${route.ownerPath}")
        } else {
            if (!declaresSymbol(owner, route.queryToken)) {
                add("Read route ${route.id} target protocol does not declare `${route.queryToken}`")
            }
            if (!declaresSymbol(owner, route.resultToken)) {
                add("Read route ${route.id} target protocol does not declare `${route.resultToken}`")
            }
        }
        val usage = sources[route.usagePath]?.text
        if (usage == null || !usesSymbol(usage, route.queryToken)) {
            add("Read route ${route.id} has no typed `${route.queryToken}` use in ${route.usagePath}")
        }
    }

    val commandsById = commandRows.groupBy(ArchitectureTableRow::id)
    commandRouteProjections.forEach { route ->
        val row = commandsById[route.id]?.singleOrNull()
        if (row == null) {
            add("Command route ${route.id} must have exactly one typed Assembly row")
        } else {
            val edge = "${route.sourceAuthority} -> ${route.targetAuthority}"
            (listOf(
                edge,
                route.operationToken,
                route.acceptedCarrierToken,
                route.rejectedCarrierToken,
            ) + route.outcomeTokens).forEach { token ->
                if (token !in row.text) {
                    add("Command route ${route.id} Assembly row is missing exact typed token `$token`")
                }
            }
        }

        val owner = sources[route.ownerPath]?.text
        if (owner == null) {
            add("Command route ${route.id} is missing target protocol source ${route.ownerPath}")
        } else {
            if (!declaresSymbol(owner, route.operationToken)) {
                add("Command route ${route.id} target protocol does not declare `${route.operationToken}`")
            }
            route.outcomeTokens.forEach { outcomeToken ->
                if (!declaresSymbol(owner, outcomeToken)) {
                    add("Command route ${route.id} target protocol does not declare `$outcomeToken`")
                }
            }
        }
        val sourceProtocol = sources[route.sourcePath]?.text
        if (sourceProtocol == null) {
            add("Command route ${route.id} is missing source protocol ${route.sourcePath}")
        } else {
            if (!declaresSymbol(sourceProtocol, route.acceptedCarrierToken)) {
                add("Command route ${route.id} source protocol does not declare `${route.acceptedCarrierToken}`")
            }
            if (!declaresSymbol(sourceProtocol, route.rejectedCarrierToken)) {
                add("Command route ${route.id} source protocol does not declare `${route.rejectedCarrierToken}`")
            }
        }
        val usage = sources[route.usagePath]?.text
        if (usage == null || !usesSymbol(usage, route.operationToken)) {
            add("Command route ${route.id} has no typed `${route.operationToken}` issuance in ${route.usagePath}")
        }
        route.outcomeTokens.forEach { outcomeToken ->
            if (usage == null || !usesSymbol(usage, outcomeToken)) {
                add("Command route ${route.id} has no typed `$outcomeToken` handling in ${route.usagePath}")
            }
        }
    }

    addAll(commandOutcomeClosureViolations(sources))
    addAll(productionProtocolUseViolations(sources))
    addAll(flowParticipationViolations(assembly))
    addAll(participantAuthorityInventoryViolations(sources, assembly))
}.distinct().sorted()

internal fun commandOutcomeClosureViolations(
    sources: Map<String, SourceDocument>,
): List<String> = buildList {
    commandOutcomeFamilies.forEach { family ->
        val owner = sources[family.ownerPath]?.text
        if (owner == null) {
            add(
                "Command outcome family ${family.supertype} is missing target protocol source " +
                    family.ownerPath,
            )
            return@forEach
        }
        val names = directSubtypeDeclarationNames(owner, family.declaration, family.supertype)
        if (names.size != names.toSet().size) {
            add("Command outcome family ${family.supertype} contains duplicate direct variants")
        }
        val declaredTokens = names.map { name -> "${family.supertype}.$name" }.toSet()
        val routedTokens = commandRouteProjections.asSequence()
            .filter { route -> route.targetAuthority == family.targetAuthority }
            .flatMap { route -> route.outcomeTokens.asSequence() }
            .filter { token -> token.startsWith("${family.supertype}.") }
            .toSet()
        (declaredTokens - routedTokens).sorted().forEach { token ->
            add("Target outcome `$token` is not mapped by any typed Assembly command route")
        }
        (routedTokens - declaredTokens).sorted().forEach { token ->
            add("Typed Assembly command routes map undeclared target outcome `$token`")
        }
    }
}.distinct().sorted()

internal fun productionProtocolUseViolations(
    sources: Map<String, SourceDocument>,
): List<String> = buildList {
    val operations = declaredTargetOperations(sources)
    val routes = readRouteProjections.map { route ->
        ProtocolRouteUse(
            sourceAuthority = route.sourceAuthority,
            targetAuthority = route.targetAuthority,
            token = route.queryToken,
            usagePath = route.usagePath,
        )
    } + commandRouteProjections.map { route ->
        ProtocolRouteUse(
            sourceAuthority = route.sourceAuthority,
            targetAuthority = route.targetAuthority,
            token = route.operationToken,
            usagePath = route.usagePath,
        )
    }
    val packageByAuthority = mapOf(
        "ContentCatalog" to "kinetickk.ball.content.api",
        "Profile" to "kinetickk.ball.profile.api",
        "GameplayRun" to "kinetickk.ball.gameplay.api",
    )

    sources.values.asSequence()
        .filter(SourceDocument::isProductionKotlinSource)
        .forEach { source ->
            val project = projectPathForSource(source.relativePath) ?: return@forEach
            val sourceAuthority = authorityForKnownProject(project) ?: return@forEach
            if (sourceAuthority !in setOf("AppAssembly", "AppSession", "GameplayRun", "Profile")) {
                return@forEach
            }
            packageByAuthority.forEach { (targetAuthority, packageName) ->
                if (sourceAuthority != targetAuthority &&
                    Regex("(?m)^import\\s+${Regex.escape(packageName)}\\.\\*\\s*$")
                        .containsMatchIn(source.text)
                ) {
                    add(
                        "Foreign Application Surface wildcard import in ${source.relativePath} " +
                            "cannot be closed against Assembly routes",
                    )
                }
            }
            operations.forEach operationLoop@{ operation ->
                if (sourceAuthority == operation.targetAuthority ||
                    !usesSymbol(source.text, operation.token)
                ) {
                    return@operationLoop
                }
                val route = routes.singleOrNull { candidate ->
                    candidate.targetAuthority == operation.targetAuthority &&
                        candidate.token == operation.token &&
                        if (sourceAuthority == "AppAssembly") {
                            candidate.usagePath == source.relativePath
                        } else {
                            candidate.sourceAuthority == sourceAuthority
                        }
                }
                val guardedException = closedForeignOperationUseExceptions.singleOrNull { exception ->
                    exception.sourceAuthority == sourceAuthority &&
                        exception.targetAuthority == operation.targetAuthority &&
                        exception.operationToken == operation.token
                }?.requiredGuardTokensByPath?.get(source.relativePath)?.all(source.text::contains) == true
                if (route == null && !guardedException) {
                    add(
                        "Foreign target operation `${operation.token}` in ${source.relativePath} " +
                            "has no exact Assembly route for $sourceAuthority -> ${operation.targetAuthority}",
                    )
                }
            }
        }
}.distinct().sorted()

private data class TargetOperation(
    val targetAuthority: String,
    val token: String,
)

private data class ProtocolRouteUse(
    val sourceAuthority: String,
    val targetAuthority: String,
    val token: String,
    val usagePath: String,
)

private fun declaredTargetOperations(sources: Map<String, SourceDocument>): Set<TargetOperation> = buildSet {
    val profileQueries = sources[PROFILE_QUERY_PATH]?.text.orEmpty()
    directSubtypeNames(profileQueries, "sealed interface ProfileQuery", "ProfileQuery").forEach { name ->
        add(TargetOperation("Profile", "ProfileQuery.$name"))
    }
    val profileProtocol = sources[PROFILE_PROTOCOL_PATH]?.text.orEmpty()
    directSubtypeNames(profileProtocol, "sealed interface ProfilePulse", "Business").forEach { name ->
        add(TargetOperation("Profile", "ProfilePulse.$name"))
    }
    directSubtypeNames(
        profileProtocol,
        "sealed interface ProfileModuleCommand",
        "ProfileModuleCommand",
    ).forEach { name ->
        add(TargetOperation("Profile", "ProfileModuleCommand.$name"))
    }
    val gameplayQueries = sources[GAMEPLAY_QUERY_PATH]?.text.orEmpty()
    directSubtypeNames(gameplayQueries, "sealed interface GameplayQuery", "GameplayQuery").forEach { name ->
        add(TargetOperation("GameplayRun", "GameplayQuery.$name"))
    }
    val gameplayProtocol = sources[GAMEPLAY_PROTOCOL_PATH]?.text.orEmpty()
    directSubtypeNames(
        gameplayProtocol,
        "sealed interface GameplayInteractionPulse",
        "GameplayInteractionPulse",
    ).forEach { name ->
        add(TargetOperation("GameplayRun", "GameplayInteractionPulse.$name"))
    }
    directSubtypeNames(
        gameplayProtocol,
        "sealed interface GameplayModuleCommand",
        "GameplayModuleCommand",
    ).forEach { name ->
        add(TargetOperation("GameplayRun", "GameplayModuleCommand.$name"))
    }
    val content = sources[CONTENT_SURFACE_PATH]?.text.orEmpty()
    declarationBody(content, "interface ContentCatalog")
        ?.let { body ->
            Regex("(?m)^\\s*fun\\s+([A-Za-z0-9_]+)\\s*\\(").findAll(body).forEach { match ->
                add(TargetOperation("ContentCatalog", "ContentCatalog.${match.groupValues[1]}"))
            }
        }
}

private fun directSubtypeNames(
    text: String,
    declaration: String,
    supertype: String,
): Set<String> {
    return directSubtypeDeclarationNames(text, declaration, supertype).toSet()
}

private fun directSubtypeDeclarationNames(
    text: String,
    declaration: String,
    supertype: String,
): List<String> {
    val body = declarationBody(text, declaration) ?: return emptyList()
    val declarations = Regex(
        "(?m)^\\s*(?:(?:public|internal|private|protected)\\s+)?" +
            "(?:(?:data|value|enum|sealed)\\s+)?(?:class|object|interface)\\s+([A-Za-z0-9_]+)\\b",
    )
        .findAll(body)
        .toList()
    return declarations.mapIndexedNotNull { index, match ->
        val end = declarations.getOrNull(index + 1)?.range?.first ?: body.length
        val segment = body.substring(match.range.first, end)
        match.groupValues[1].takeIf {
            Regex(":\\s*${Regex.escape(supertype)}\\b").containsMatchIn(segment)
        }
    }
}

private fun declarationBody(text: String, declaration: String): String? {
    val declarationIndex = text.indexOf(declaration)
    if (declarationIndex < 0) return null
    val open = text.indexOf('{', startIndex = declarationIndex + declaration.length)
    if (open < 0) return null
    var depth = 1
    for (index in open + 1 until text.length) {
        when (text[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(open + 1, index)
            }
        }
    }
    return null
}

internal fun flowParticipationViolations(assembly: String): List<String> = buildList {
    val rows = parseArchitectureTableRows(assembly, "## Flow participations")
    addDuplicateRowViolations("FlowParticipation", rows)
    val profileReferences = (
        readRouteProjections.filter {
            it.sourceAuthority == "AppSession" && it.targetAuthority == "Profile"
        }.map(ReadRouteProjection::id) + commandRouteProjections.filter {
            it.sourceAuthority == "AppSession" && it.targetAuthority == "Profile"
        }.map(CommandRouteProjection::id)
        ).toSortedSet()
    val gameplayReferences = (
        readRouteProjections.filter {
            it.sourceAuthority == "AppSession" && it.targetAuthority == "GameplayRun"
        }.map(ReadRouteProjection::id) + commandRouteProjections.filter {
            it.sourceAuthority == "AppSession" && it.targetAuthority == "GameplayRun"
        }.map(CommandRouteProjection::id)
        ).toSortedSet()
    val expected = mapOf(
        "app-session-profile" to profileReferences,
        "app-session-gameplay" to gameplayReferences,
    )
    val rowsById = rows.groupBy(ArchitectureTableRow::id)
    if (rowsById.keys != expected.keys) {
        add(
            "FlowParticipation inventory must contain exactly " +
                expected.keys.sorted().joinToString(),
        )
    }
    val allRouteIds = expectedReadRoutes + expectedCommandRoutes
    expected.forEach { (id, expectedReferences) ->
        val row = rowsById[id]?.singleOrNull() ?: return@forEach
        val actualReferences = Regex("`([^`]+)`").findAll(row.text)
            .map { match -> match.groupValues[1] }
            .filter { reference -> reference in allRouteIds }
            .toSortedSet()
        if (actualReferences != expectedReferences) {
            add(
                "FlowParticipation `$id` must reference exactly " +
                    expectedReferences.joinToString(),
            )
        }
    }
}.distinct().sorted()

internal fun participantAuthorityInventoryViolations(
    sources: Map<String, SourceDocument>,
    assembly: String,
): List<String> = buildList {
    val expectedSubtypes = listOf("Profile", "Gameplay")
    val state = sources[SESSION_STATE_PATH]?.text.orEmpty()
    val actualSubtypes = directSubtypeDeclarationNames(
        state,
        "sealed interface PendingParticipantCommand",
        "PendingParticipantCommand",
    )
    if (actualSubtypes != expectedSubtypes) {
        add(
            "PendingParticipantCommand must declare exactly the ordered direct subtype inventory " +
                expectedSubtypes.joinToString(),
        )
    }

    val expectedRows = linkedMapOf(
        "app-session-profile" to "AppSession / Profile",
        "app-session-gameplay" to "AppSession / GameplayRun",
    )
    val rows = parseArchitectureTableRows(assembly, "## Flow participations")
    val rowsById = rows.groupBy(ArchitectureTableRow::id)
    if (rowsById.keys != expectedRows.keys) {
        add("Participant-authority closure requires exactly the Profile and GameplayRun FlowParticipation rows")
    }
    expectedRows.forEach { (id, participantCell) ->
        val row = rowsById[id]?.singleOrNull()
        if (row == null || row.cells.getOrNull(1) != participantCell) {
            add("FlowParticipation `$id` must bind the closed participant authority `$participantCell`")
        }
    }
}.distinct().sorted()

private fun MutableList<String>.addDuplicateRowViolations(
    kind: String,
    rows: List<ArchitectureTableRow>,
) {
    rows.groupingBy(ArchitectureTableRow::id)
        .eachCount()
        .filterValues { count -> count != 1 }
        .toSortedMap()
        .forEach { (id, count) -> add("Duplicate $kind route ID `$id` appears $count times") }
}

private fun declaresSymbol(text: String, qualifiedToken: String): Boolean {
    val name = qualifiedToken.substringAfterLast('.')
    return if (qualifiedToken.startsWith("ContentCatalog.")) {
        Regex("\\binterface\\s+ContentCatalog\\b").containsMatchIn(text) &&
            Regex("\\bfun\\s+${Regex.escape(name)}\\s*\\(").containsMatchIn(text)
    } else {
        val parent = qualifiedToken.substringBeforeLast('.', missingDelimiterValue = "")
        val parentIsClosed = parent.isEmpty() ||
            Regex("\\bsealed\\s+interface\\s+${Regex.escape(parent)}\\b").containsMatchIn(text)
        parentIsClosed && Regex(
            "\\b(?:data\\s+)?(?:class|object|interface|enum\\s+class)\\s+${Regex.escape(name)}\\b",
        ).containsMatchIn(text)
    }
}

private fun usesSymbol(text: String, qualifiedToken: String): Boolean {
    if (qualifiedToken in text) return true
    if (qualifiedToken.startsWith("ContentCatalog.")) {
        val name = qualifiedToken.substringAfterLast('.')
        return Regex("\\.${Regex.escape(name)}\\s*\\(").containsMatchIn(text)
    }
    return false
}

private fun MutableList<String>.addProtocolRouteViolations(
    sources: Map<String, SourceDocument>,
    records: Map<String, String>,
) {
    addAll(protocolRouteViolations(sources, records["assembly.md"].orEmpty()))
}

internal fun boundViolations(
    sources: Map<String, SourceDocument>,
    records: Map<String, String>,
): List<String> = buildList {
    expectedBounds.forEach { bound ->
        bound.sourceAnchors.forEach { anchor ->
            val text = sources[anchor.path]?.text
            anchor.tokens.forEach { token ->
                if (text == null || token !in text) {
                    add(
                        "Bound ${bound.id}=${bound.value} is not anchored by `$token` " +
                            "in ${anchor.path}",
                    )
                }
            }
        }
        bound.evidenceAnchors.forEach { anchor ->
            val evidence = sources[anchor.path]?.text
            anchor.tokens.forEach { token ->
                if (evidence == null || token !in evidence) {
                    add(
                        "Bound ${bound.id}=${bound.value} lacks named boundary evidence `$token` " +
                            "in ${anchor.path}",
                    )
                }
            }
        }
    }
    val policy = records["policy.md"].orEmpty()
    requiredPolicyBoundRows.filterNot(policy::contains).forEach { row ->
        add("Policy bound projection is missing exact row `$row`")
    }
}.distinct().sorted()

internal fun mechanicallyDerivedBoundViolations(
    sources: Map<String, SourceDocument>,
    records: Map<String, String>,
): List<String> = buildList {
    mechanicallyDerivedBounds.forEach { projection ->
        projection.sourceAnchors.forEach { anchor ->
            val text = sources[anchor.path]?.text
            anchor.tokens.forEach { token ->
                if (text == null || token !in text) {
                    add(
                        "Mechanically derived bound ${projection.id}=${projection.value} " +
                            "is not anchored by `$token` in ${anchor.path}",
                    )
                }
            }
        }
        projection.evidenceAnchors.forEach { anchor ->
            val evidence = sources[anchor.path]?.text
            anchor.tokens.forEach { token ->
                if (evidence == null || token !in evidence) {
                    add(
                        "Mechanically derived bound ${projection.id}=${projection.value} " +
                            "lacks named derivation evidence `$token` in ${anchor.path}",
                    )
                }
            }
        }
        projection.closedEnumInventories.forEach { inventory ->
            val actualEntries = enumEntryNames(
                sources[inventory.path]?.text.orEmpty(),
                inventory.declaration,
            )
            if (actualEntries != inventory.expectedEntries) {
                add(
                    "Mechanically derived bound ${projection.id} requires `${inventory.declaration}` " +
                        "to declare exactly ${inventory.expectedEntries.joinToString()}",
                )
            }
        }
    }
    val policy = records["policy.md"].orEmpty()
    mechanicallyDerivedBounds.filterNot { projection -> projection.policyRow in policy }
        .forEach { projection ->
            add("Policy mechanically-derived projection is missing exact row `${projection.policyRow}`")
        }
}.distinct().sorted()

private fun enumEntryNames(text: String, declaration: String): List<String> {
    val body = declarationBody(text, declaration) ?: return emptyList()
    return Regex("(?m)(?:^|,)\\s*([A-Z][A-Z0-9_]*)\\s*(?=\\(|,|$)")
        .findAll(body.substringBefore(';'))
        .map { match -> match.groupValues[1] }
        .toList()
}

internal val requiredPolicyBoundRows = listOf(
    "| Profile semantic outputs per accepted Decision | 2 |",
    "| Gameplay semantic outputs per accepted Decision | 3 |",
    "| Session semantic outputs per accepted Decision | 3 |",
    "| Profile Resource effects per accepted Decision | 1 |",
    "| Gameplay Profile-command outputs per accepted Decision | 1 |",
    "| Session participant-command / ensure-run outputs per accepted Decision | 1 / 1 |",
    "| Session participant commands at one time | 1 |",
    "| cross-authority read / command-result routes | 14 / 10 |",
    "| Profile / Gameplay / Session completion deque capacity | 8 / 8 / 8 |",
    "| Session participant authorities | 2 |",
    "| same-stack causal depth | 8 |",
    "| cumulative fan-out per accepted root causal scope | 9840 |",
    "| active GameplayRun instances | 1 |",
    "| Gameplay fixed steps per render frame | 48 |",
    "| Gameplay simulation raw-delta / accumulator cap seconds | `0.1` / `0.3` |",
    "| enemies / projectiles / pickups / trail | 120 / 650 / 420 / 110 |",
    "| delayed Relic hits | 256 |",
    "| Relic chain work / visited IDs | 5 / 6 |",
    "| projectile hit-history IDs | 120 |",
    "| Gameplay sound cues / weapon nodes / orbitals / choices | 32 / 8 / 8 / 4 |",
    "| Arc Coil targets / generated item, weapon, or Relic reward choices | 6 / 3 |",
    "| Gameplay trail samples per update | 32 |",
    "| visual-FX cues per projection | 2048 |",
    "| Interaction particles / motion echoes / shockwaves | 700 / 36 / 48 |",
    "| Interaction damage numbers / weapon arcs | 140 / 128 |",
    "| Interaction frame delta seconds | `0..1` |",
    "| Interaction viewport pixels / density | `1..32768` / `0.5..8` |",
    "| Interaction pointer representation / choice index | finite / `0..3` |",
    "| authoritative Gameplay frame delta seconds | `0..1` |",
    "| authoritative Gameplay viewport pixels / density | `1..32768` / `0.5..8` |",
    "| authoritative Gameplay pointer | `0..current viewport` |",
    "| Gameplay / Home / Armory presentation delta seconds | `0.1` / `0.1` / `0.1` |",
    "| Codex / Armory visible page slice | 10 / 3 |",
    "| accepted caller effect ToneRequests per advance | 32 |",
    "| caller effect ToneRequests selected per advance | 3 |",
    "| music clock advance delta seconds | `0.1` |",
    "| ToneRequest frequency Hz / duration seconds / gain | `20..20000` / `0.001..1` / `0..1` |",
    "| Desktop audio workers / queued tasks | 1 / 24 |",
    "| Desktop synthesis samples / PCM bytes per tone | 22050 / 44100 |",
    "| items / weapons / upgrades / relics | 400 / 12 / 8 / 40 |",
    "| Rebirth level | `0..10` |",
    "| equipped Relic slots / rank | 4 / `1..5` |",
    "| Profile retained Lab rank slots / each rank | captured upgrade count (at most 8) / `0..captured maxRanks` |",
    "| Profile retained discoveries | captured `itemCount` (at most 400) |",
    "| Profile master volume / text scale | `0..1` / `1..1.75` |",
    "| Profile simulation speed / damage-tier threshold | exact declared option sets |",
    "| Profile Gameplay discoveries per Pulse | captured `itemCount` (at most 400) |",
    "| Profile v4 UTF-8 payload | 65536 bytes |",
    "| Desktop Preferences value length | 8192 UTF-16 code units |",
    "| Desktop Preferences key names admitted per exact node read | 64 |",
)

private fun MutableList<String>.addBoundViolations(
    sources: Map<String, SourceDocument>,
    records: Map<String, String>,
) {
    addAll(boundViolations(sources, records))
    addAll(mechanicallyDerivedBoundViolations(sources, records))
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

    val assembly = records["assembly.md"].orEmpty()
    val reads = parseTableIds(assembly, "## Read dependencies")
    val commands = parseTableIds(assembly, "## Command/result routes")
    if (reads != expectedReadRoutes) {
        add("Read route inventory drift: expected ${expectedReadRoutes.joinToString()}, found ${reads.joinToString()}")
    }
    if (commands != expectedCommandRoutes) {
        add("Command route inventory drift: expected ${expectedCommandRoutes.joinToString()}, found ${commands.joinToString()}")
    }
    if ("repository has exactly fourteen typed read routes" !in assembly ||
        "AppSession has exactly nine command/result route" !in assembly ||
        "repository has ten" !in assembly
    ) {
        add(
            "Assembly must resolve fourteen repository reads, nine AppSession command mappings, " +
                "and ten repository command mappings",
        )
    }

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

internal fun verifySnapshot(snapshotRoot: Path): SnapshotVerification {
    require(Files.isDirectory(snapshotRoot.resolve(".git"))) {
        "Pokeball snapshot is not a Git checkout: $snapshotRoot"
    }
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
