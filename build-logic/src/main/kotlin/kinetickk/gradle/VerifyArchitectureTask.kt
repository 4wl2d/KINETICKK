// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyArchitectureTask : DefaultTask() {
    @get:Input
    abstract val leafProjectPaths: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureEdgeReportFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootSourceFiles: ConfigurableFileCollection

    init {
        leafProjectPaths.convention(emptySet())
    }

    @TaskAction
    fun verify() {
        val actualLeafProjects = leafProjectPaths.get()
        val dependencies = loadArchitectureEdges(
            reportFiles = architectureEdgeReportFiles.files,
            expectedSourceProjectPaths = actualLeafProjects,
        )
            .map(DeclaredProjectDependency::decode)
            .sortedWith(compareBy(DeclaredProjectDependency::source, DeclaredProjectDependency::configuration))
        val violations = buildList {
            addModuleSetViolations(actualLeafProjects)
            addDependencyViolations(dependencies)

            val sourceFiles = rootSourceFiles.files
                .filter { it.isFile }
                .sortedBy { it.invariantSeparatorsPath }
            if (sourceFiles.isNotEmpty()) {
                add(
                    "Root project must not contain production sources under src/: " +
                        sourceFiles.joinToString { it.invariantSeparatorsPath },
                )
            }
        }.distinct().sorted()

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Architecture verification failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(" - $it") }
                    append(
                        "Inspected ${dependencies.size} declared ProjectDependency edge(s) " +
                            "without resolving configurations.",
                    )
                },
            )
        }

        logger.lifecycle(
            "Architecture verified: ${actualLeafProjects.size} leaf modules and " +
                "${dependencies.size} declared ProjectDependency edge(s).",
        )
    }
}

private data class DeclaredProjectDependency(
    val source: String,
    val configuration: String,
    val target: String,
) {
    val displayName: String
        get() = "$source [$configuration] -> $target"

    companion object {
        fun decode(encoded: String): DeclaredProjectDependency {
            val parts = encoded.split(EDGE_SEPARATOR, limit = 3)
            require(parts.size == 3) { "Malformed architecture dependency edge: $encoded" }
            return DeclaredProjectDependency(
                source = parts[0],
                configuration = parts[1],
                target = parts[2],
            )
        }
    }
}

private fun MutableList<String>.addModuleSetViolations(actualLeafProjects: Set<String>) {
    val missing = EXPECTED_LEAF_PROJECTS - actualLeafProjects
    if (missing.isNotEmpty()) {
        add("Missing required leaf modules: ${missing.sorted().joinToString()}")
    }

    val unexpected = actualLeafProjects - EXPECTED_LEAF_PROJECTS
    if (unexpected.isNotEmpty()) {
        add("Unexpected leaf modules: ${unexpected.sorted().joinToString()}")
    }

    val legacyModules = actualLeafProjects.filter(::isLegacyModule)
    if (legacyModules.isNotEmpty()) {
        add("Legacy core/feature modules are forbidden: ${legacyModules.sorted().joinToString()}")
    }
}

private fun MutableList<String>.addDependencyViolations(dependencies: List<DeclaredProjectDependency>) {
    HOST_PROJECTS.forEach { host ->
        val targets = dependencies.asSequence()
            .filter { it.source == host }
            .map { it.target }
            .toSet()
        if (targets != setOf(APP_SHARED_PROJECT)) {
            add(
                "$host must have exactly one project dependency target, $APP_SHARED_PROJECT; " +
                    "found ${targets.sorted().joinToString().ifEmpty { "none" }}",
            )
        }
    }

    dependencies.forEach { dependency ->
        if (dependency.source.isImplementationProject() && dependency.target.isImplementationProject()) {
            add("impl -> impl dependency is forbidden: ${dependency.displayName}")
        }

        if (isLegacyModule(dependency.source) || isLegacyModule(dependency.target)) {
            add("Legacy core/feature dependency is forbidden: ${dependency.displayName}")
        }

        if (dependency.source !in EXPECTED_LEAF_PROJECTS || dependency.target !in EXPECTED_LEAF_PROJECTS) {
            add("Dependency endpoint is outside the declared 23-module graph: ${dependency.displayName}")
        }
    }
}

private fun String.isImplementationProject(): Boolean = endsWith(":impl")

private fun isLegacyModule(path: String): Boolean =
    path.startsWith(":core:") || path.startsWith(":feature:")

private const val EDGE_SEPARATOR = '\t'
private const val APP_SHARED_PROJECT = ":app:shared"

private val HOST_PROJECTS = setOf(
    ":app:android",
    ":app:desktop",
    ":app:web",
)

private val EXPECTED_LEAF_PROJECTS = setOf(
    ":app:android",
    ":app:desktop",
    ":app:shared",
    ":app:web",
    ":foundation:common",
    ":foundation:design",
    ":resource:audio:api",
    ":resource:audio:impl",
    ":ball:content:api",
    ":ball:content:impl",
    ":ball:profile:api",
    ":ball:profile:nucleus",
    ":ball:profile:resource",
    ":ball:profile:interaction",
    ":ball:profile:impl",
    ":ball:gameplay:api",
    ":ball:gameplay:nucleus",
    ":ball:gameplay:interaction",
    ":ball:gameplay:impl",
    ":flow:session:api",
    ":flow:session:nucleus",
    ":flow:session:interaction",
    ":flow:session:impl",
)
