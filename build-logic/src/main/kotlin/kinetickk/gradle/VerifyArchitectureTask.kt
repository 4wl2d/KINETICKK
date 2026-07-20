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

    @get:Input
    abstract val declaredProjectDependencies: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootSourceFiles: ConfigurableFileCollection

    init {
        leafProjectPaths.convention(emptySet())
        declaredProjectDependencies.convention(emptySet())
    }

    @TaskAction
    fun verify() {
        val actualLeafProjects = leafProjectPaths.get()
        val dependencies = declaredProjectDependencies.get()
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

    val legacyGameModules = actualLeafProjects.filter(::isLegacyGameModule)
    if (legacyGameModules.isNotEmpty()) {
        add("Legacy feature:game modules are forbidden: ${legacyGameModules.sorted().joinToString()}")
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
        val sourceFeature = dependency.source.featureName()
        val targetFeature = dependency.target.featureName()

        if (
            dependency.source.isCoreProject() &&
            (dependency.target.isFeatureProject() || dependency.target.isAppProject())
        ) {
            add("core -> feature/app dependency is forbidden: ${dependency.displayName}")
        }

        if (dependency.source.isFeatureProject() && dependency.target.isAppProject()) {
            add("feature -> app dependency is forbidden: ${dependency.displayName}")
        }

        if (sourceFeature != null && targetFeature != null && sourceFeature != targetFeature) {
            add("Cross-feature dependency is forbidden: ${dependency.displayName}")
        }

        if (dependency.source.isImplementationProject() && dependency.target.isImplementationProject()) {
            add("impl -> impl dependency is forbidden: ${dependency.displayName}")
        }

        if (isLegacyGameModule(dependency.source) || isLegacyGameModule(dependency.target)) {
            add("Legacy feature:game dependency is forbidden: ${dependency.displayName}")
        }
    }
}

private fun String.isAppProject(): Boolean = startsWith(":app:")

private fun String.isCoreProject(): Boolean = startsWith(":core:")

private fun String.isFeatureProject(): Boolean = startsWith(":feature:")

private fun String.isImplementationProject(): Boolean = endsWith(":impl")

private fun String.featureName(): String? {
    if (!isFeatureProject()) return null
    return removePrefix(":").split(':').getOrNull(1)
}

private fun isLegacyGameModule(path: String): Boolean =
    path == ":feature:game" || path.startsWith(":feature:game:")

private const val EDGE_SEPARATOR = '\t'
private const val APP_SHARED_PROJECT = ":app:shared"

private val HOST_PROJECTS = setOf(
    ":app:desktop",
    ":app:web",
)

private val EXPECTED_LEAF_PROJECTS = setOf(
    ":app:desktop",
    ":app:shared",
    ":app:web",
    ":core:audio:api",
    ":core:audio:impl",
    ":core:common",
    ":core:content",
    ":core:design-system",
    ":core:profile:api",
    ":core:profile:data",
    ":feature:armory:api",
    ":feature:armory:impl",
    ":feature:codex:api",
    ":feature:codex:impl",
    ":feature:gameplay:api",
    ":feature:gameplay:domain",
    ":feature:gameplay:impl",
    ":feature:gameplay:presentation",
    ":feature:home:api",
    ":feature:home:impl",
    ":feature:lab:api",
    ":feature:lab:impl",
    ":feature:rebirth:api",
    ":feature:rebirth:impl",
    ":feature:settings:api",
    ":feature:settings:impl",
)
