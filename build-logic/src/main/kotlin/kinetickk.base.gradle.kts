// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME
import kinetickk.gradle.ARCHITECTURE_EDGE_EXPORT_TASK_NAME
import kinetickk.gradle.ExportArchitectureEdgesTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.BasePluginExtension

// Leaf names such as `api` and `impl` repeat across module families. Giving every
// family its own Maven group prevents Kotlin MPP metadata from conflating those projects.
group = path
    .removePrefix(":")
    .split(':')
    .dropLast(1)
    .filter(String::isNotBlank)
    .joinToString(separator = ".", prefix = "kinetickk.")
    .removeSuffix(".")
version = "0.1.0"

val sourceProjectPathValue = path
val exportArchitectureEdgesTask = tasks.register<ExportArchitectureEdgesTask>(
    ARCHITECTURE_EDGE_EXPORT_TASK_NAME,
) {
    group = "verification"
    description = "Exports this project's declared project-dependency edges deterministically."
    sourceProjectPath.set(sourceProjectPathValue)
    outputFile.set(layout.buildDirectory.file("reports/architecture/declared-project-edges.tsv"))
}

val architectureEdgeElements = configurations.create(
    ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME,
) {
    description = "Consumable deterministic architecture-edge report for this project."
    isCanBeConsumed = true
    isCanBeResolved = false
    isCanBeDeclared = false
}
architectureEdgeElements.outgoing.artifact(
    exportArchitectureEdgesTask.flatMap { it.outputFile },
) {
    builtBy(exportArchitectureEdgesTask)
}

configurations.configureEach {
    val declarationConfiguration = this
    dependencies.withType(ProjectDependency::class.java).configureEach {
        // Android and Kotlin create resolvable classpaths by copying declared project
        // dependencies. Only the original declarable configuration is architectural input.
        if (
            !declarationConfiguration.isCanBeDeclared ||
            declarationConfiguration.name.endsWith("CompileClasspath") ||
            declarationConfiguration.name.endsWith("RuntimeClasspath")
        ) {
            return@configureEach
        }
        val targetProjectPath = path
        exportArchitectureEdgesTask.configure {
            declaredProjectDependencies.add(
                "$sourceProjectPathValue\t${declarationConfiguration.name}\t$targetProjectPath",
            )
        }
    }
}

pluginManager.withPlugin("base") {
    extensions.configure<BasePluginExtension> {
        archivesName.set(path.removePrefix(":").replace(':', '-'))
    }
}
