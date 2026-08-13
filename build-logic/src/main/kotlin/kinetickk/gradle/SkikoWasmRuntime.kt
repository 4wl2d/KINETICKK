// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.zip.ZipFile

/**
 * Supplies Skiko to executable Kotlin/Wasm binaries without applying the full Compose resources
 * plugin to resource-free modules. Keep [skikoVersion] aligned with the Compose UI dependency.
 */
fun Project.configureSkikoWasmRuntime(skikoVersion: String) {
    if (isolatedProjectsProfileEnabled()) return

    val ownerProjectPathValue = path
    val projectConfigurations = configurations
    val runtimeConfiguration = configurations.create("kinetickkSkikoJsWasmRuntime") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
        defaultDependencies {
            add(project.dependencies.create("org.jetbrains.skiko:skiko-js-wasm-runtime:$skikoVersion"))
        }
    }
    dependencies.registerTransform(ProcessSkikoWasmRuntimeTransform::class.java) {
        from.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            ArtifactTypeDefinition.JAR_TYPE,
        )
        to.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            SKIKO_FILTERED_ARTIFACT_TYPE,
        )
    }
    val processedRuntime = runtimeConfiguration.incoming.artifactView {
        attributes {
            attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                SKIKO_FILTERED_ARTIFACT_TYPE,
            )
        }
    }.artifacts.artifactFiles
    val verifyRuntimeVersion = tasks.register(
        "verifyKinetickkSkikoWasmRuntimeVersion",
        VerifySkikoWasmRuntimeVersionTask::class.java,
    ) {
        group = "verification"
        description =
            "Verifies the exact Skiko archive and its version against Compose UI's dependency graph."
        ownerProjectPath.set(ownerProjectPathValue)
        pinnedSkikoVersion.set(skikoVersion)
        resolvedComposeSkikoVersions.convention(emptySet())
        runtimeFiles.from(runtimeConfiguration)
    }

    tasks.matching { task -> task.name == "check" }.configureEach {
        dependsOn(verifyRuntimeVersion)
    }

    extensions.getByType(KotlinMultiplatformExtension::class.java)
        .targets
        .withType(KotlinJsIrTarget::class.java)
        .configureEach {
            if (wasmTargetType != null) {
                compilations.named("main").configure {
                    sequenceOf(
                        compileDependencyConfigurationName,
                        runtimeDependencyConfigurationName,
                    ).mapNotNull(projectConfigurations::findByName)
                        .forEach { dependencyConfiguration ->
                            val resolvedVersions = dependencyConfiguration
                                .incoming
                                .resolutionResult
                                .rootComponent
                                .map { root -> resolvedSkikoVersions(root) }
                            verifyRuntimeVersion.configure {
                                resolvedComposeSkikoVersions.addAll(resolvedVersions)
                            }
                        }
                }
                compilations.configureEach {
                    binaries.configureEach {
                        linkSyncTask.configure {
                            dependsOn(verifyRuntimeVersion)
                            from.from(processedRuntime)
                        }
                    }
                }
            }
        }
}

@CacheableTransform
abstract class ProcessSkikoWasmRuntimeTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val archive = requireSingleSkikoRuntimeArchive(listOf(inputArtifact.get().asFile))
        ZipFile(archive).use { zipFile ->
            val archiveEntries = mutableListOf<String>()
            val requiredEntries = mutableMapOf<String, java.util.zip.ZipEntry>()
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                archiveEntries += entry.name
                if (!entry.isDirectory && entry.name in SKIKO_WASM_RUNTIME_OUTPUT_PATHS) {
                    requiredEntries[entry.name] = entry
                }
            }
            skikoRuntimeArchiveViolation(archiveEntries)?.let { violation ->
                throw GradleException(
                    "Invalid Skiko JS/Wasm runtime archive ${archive.name}: $violation",
                )
            }

            val outputPaths = buildList {
                SKIKO_WASM_RUNTIME_OUTPUT_PATHS.forEach { outputPath ->
                    val outputFile = outputs.file(outputPath)
                    zipFile.getInputStream(requiredEntries.getValue(outputPath)).use { input ->
                        Files.copy(
                            input,
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    if (!Files.isRegularFile(outputFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        throw GradleException(
                            "Filtered Skiko JS/Wasm runtime contains a non-regular output entry: " +
                                outputPath,
                        )
                    }
                    add(outputPath)
                }
            }
            skikoRuntimeOutputViolation(outputPaths)?.let { violation ->
                throw GradleException(
                    "Invalid filtered Skiko JS/Wasm runtime output: $violation",
                )
            }
        }
    }
}

private fun requireSingleSkikoRuntimeArchive(archives: Collection<java.io.File>): java.io.File {
    val sortedArchives = archives.sortedBy { it.absolutePath }
    skikoRuntimeArchiveSelectionViolation(sortedArchives.map { it.absolutePath })?.let { violation ->
        throw GradleException(violation)
    }
    return sortedArchives.single().also { archive ->
        if (!archive.isFile) {
            throw GradleException(
                "Expected the Skiko JS/Wasm runtime archive to be a regular file: " +
                    archive.absolutePath,
            )
        }
    }
}

internal fun skikoRuntimeArchiveSelectionViolation(archivePaths: List<String>): String? {
    val sortedPaths = archivePaths.sorted()
    if (sortedPaths.size != 1) {
        return "Expected exactly one Skiko JS/Wasm runtime archive, found " +
            "${sortedPaths.size}: ${sortedPaths.joinToString { path -> path.substringAfterLast('/') }}"
    }
    return if (!sortedPaths.single().endsWith(".jar", ignoreCase = true)) {
        "Expected the Skiko JS/Wasm runtime to be a JAR: ${sortedPaths.single()}"
    } else {
        null
    }
}

abstract class VerifySkikoWasmRuntimeVersionTask : DefaultTask() {
    @get:Input
    abstract val ownerProjectPath: Property<String>

    @get:Input
    abstract val pinnedSkikoVersion: Property<String>

    @get:Input
    abstract val resolvedComposeSkikoVersions: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        requireSingleSkikoRuntimeArchive(runtimeFiles.files)
        val pinnedVersion = pinnedSkikoVersion.get()
        val resolvedVersions = resolvedComposeSkikoVersions.get()
        val violation = skikoVersionConsistencyViolation(pinnedVersion, resolvedVersions)
        if (violation != null) {
            throw GradleException(
                "Skiko Wasm runtime consistency check failed for ${ownerProjectPath.get()}: " +
                    "$violation The pinned org.jetbrains.skiko:skiko-js-wasm-runtime version " +
                    "must exactly match every org.jetbrains.skiko component selected by the " +
                    "Compose UI Wasm dependency graph.",
            )
        }

        logger.lifecycle(
            "Skiko Wasm runtime verified for ${ownerProjectPath.get()}: $pinnedVersion.",
        )
    }
}

internal fun skikoVersionConsistencyViolation(
    pinnedVersion: String,
    resolvedVersions: Set<String>,
): String? = when {
    pinnedVersion.isBlank() -> "The pinned Skiko version is blank."
    resolvedVersions.isEmpty() ->
        "Compose UI resolved no org.jetbrains.skiko component for the Wasm main compilation."
    resolvedVersions != setOf(pinnedVersion) ->
        "Pinned version '$pinnedVersion' differs from resolved version(s) " +
            "${resolvedVersions.sorted().joinToString(prefix = "[", postfix = "].")}"
    else -> null
}

private fun resolvedSkikoVersions(root: ResolvedComponentResult): Set<String> {
    val pending = ArrayDeque<ResolvedComponentResult>()
    val visited = mutableSetOf<ComponentIdentifier>()
    val versions = mutableSetOf<String>()
    pending.add(root)

    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (!visited.add(component.id)) continue

        val module = component.id as? ModuleComponentIdentifier
        if (module?.group == SKIKO_GROUP) versions += module.version

        component.dependencies.forEach { dependency ->
            if (dependency is ResolvedDependencyResult) pending.add(dependency.selected)
        }
    }

    return versions
}

internal fun skikoRuntimeArchiveViolation(entryPaths: List<String>): String? {
    val unsafePaths = entryPaths.filter(::isUnsafeArchivePath).distinct().sorted()
    if (unsafePaths.isNotEmpty()) {
        return unsafePaths.joinToString(
            prefix = "unsafe archive entries [",
            postfix = "]",
        )
    }

    val aliases = entryPaths
        .filter { path ->
            path !in SKIKO_WASM_RUNTIME_OUTPUT_PATHS &&
                path.substringAfterLast('/') in SKIKO_WASM_RUNTIME_OUTPUT_PATHS
        }
        .distinct()
        .sorted()
    if (aliases.isNotEmpty()) {
        return "runtime entries must be top-level and exact; found aliases " +
            aliases.joinToString(prefix = "[", postfix = "]")
    }

    val selectedPaths = entryPaths.filter { it in SKIKO_WASM_RUNTIME_OUTPUT_PATHS }
    return skikoRuntimeOutputViolation(selectedPaths)
}

internal fun skikoRuntimeOutputViolation(outputPaths: List<String>): String? {
    val counts = outputPaths.groupingBy { it }.eachCount()
    val missing = SKIKO_WASM_RUNTIME_OUTPUT_PATHS.filter { counts[it] == null }
    val extra = counts.keys.filter { it !in SKIKO_WASM_RUNTIME_OUTPUT_PATHS }.sorted()
    val duplicates = counts.filterValues { it > 1 }.keys.sorted()
    if (missing.isEmpty() && extra.isEmpty() && duplicates.isEmpty()) return null

    return buildList {
        if (missing.isNotEmpty()) add("missing ${missing.joinToString(prefix = "[", postfix = "]")}")
        if (extra.isNotEmpty()) add("extra ${extra.joinToString(prefix = "[", postfix = "]")}")
        if (duplicates.isNotEmpty()) add("duplicates ${duplicates.joinToString(prefix = "[", postfix = "]")}")
    }.joinToString(separator = "; ")
}

private fun isUnsafeArchivePath(path: String): Boolean =
    path.isEmpty() ||
        path.startsWith('/') ||
        path.contains('\\') ||
        (path.length >= 2 && path[1] == ':') ||
        path.split('/').any { segment -> segment == "." || segment == ".." }

private const val SKIKO_GROUP = "org.jetbrains.skiko"
private const val SKIKO_FILTERED_ARTIFACT_TYPE = "kinetickk-skiko-wasm-runtime"
private val SKIKO_WASM_RUNTIME_OUTPUT_PATHS = listOf("skiko.mjs", "skiko.wasm")
