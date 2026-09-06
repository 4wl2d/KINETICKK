// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kinetickk.gradle.pokeball.ProjectEdge
import kinetickk.gradle.pokeball.expectedLeafProjects
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
            .map(ProjectEdge::decode)
            .sortedWith(compareBy(ProjectEdge::source, ProjectEdge::configuration))
        val violations = buildList {
            addModuleSetViolations(actualLeafProjects)
            addAll(architectureDependencyViolations(dependencies))

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

private val ProjectEdge.displayName: String
    get() = "$source [$configuration] -> $target"

private fun MutableList<String>.addModuleSetViolations(actualLeafProjects: Set<String>) {
    val missing = expectedLeafProjects - actualLeafProjects
    if (missing.isNotEmpty()) {
        add("Missing required leaf modules: ${missing.sorted().joinToString()}")
    }

    val unexpected = actualLeafProjects - expectedLeafProjects
    if (unexpected.isNotEmpty()) {
        add("Unexpected leaf modules: ${unexpected.sorted().joinToString()}")
    }

    val legacyModules = actualLeafProjects.filter(::isLegacyModule)
    if (legacyModules.isNotEmpty()) {
        add("Legacy core/feature modules are forbidden: ${legacyModules.sorted().joinToString()}")
    }
}

internal fun architectureDependencyViolations(dependencies: List<ProjectEdge>): List<String> = buildList {
    HOST_PROJECTS.forEach { host ->
        val targets = dependencies.asSequence()
            .filter { it.source == host && !it.isTest }
            .map { it.target }
            .toSet()
        if (targets != setOf(APP_SHARED_PROJECT)) {
            add(
                "$host must have exactly one production project dependency target, $APP_SHARED_PROJECT; " +
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

        if (dependency.source !in expectedLeafProjects || dependency.target !in expectedLeafProjects) {
            add(
                "Dependency endpoint is outside the declared ${expectedLeafProjects.size}-module graph: " +
                    dependency.displayName,
            )
        }
    }
}

private fun String.isImplementationProject(): Boolean = endsWith(":impl")

private fun isLegacyModule(path: String): Boolean =
    path.startsWith(":core:") || path.startsWith(":feature:")

private const val APP_SHARED_PROJECT = ":app:shared"

private val HOST_PROJECTS = setOf(
    ":app:android",
    ":app:desktop",
    ":app:web",
)
