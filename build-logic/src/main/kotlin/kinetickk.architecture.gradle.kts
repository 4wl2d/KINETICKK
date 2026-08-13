// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.VerifyArchitectureTask
import kinetickk.gradle.pokeball.GeneratePokeballResolvedManifestTask
import kinetickk.gradle.pokeball.VerifyPokeballArchitectureTask
import kinetickk.gradle.pokeball.VerifyPokeballConformanceTask
import kinetickk.gradle.pokeball.VerifyPokeballManifestDriftTask
import kinetickk.gradle.pokeball.VerifyPokeballSnapshotTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Copy

val verifyArchitectureTask = tasks.register<VerifyArchitectureTask>("verifyArchitecture") {
    group = "verification"
    description = "Verifies the declared 22-module Pokeball role graph without resolving dependencies."
    leafProjectPaths.set(
        rootProject.subprojects
            .filter { it.childProjects.isEmpty() }
            .map { it.path }
            .toSet(),
    )
    rootSourceFiles.from(rootProject.fileTree("src"))
}

val generatedManifestFile = rootProject.layout.buildDirectory.file(
    "generated/pokeball/resolved-manifest.json",
)
val checkedManifestFile = rootProject.layout.projectDirectory.file(
    "docs/architecture/pokeball/resolved-manifest.json",
)
val architectureRecords = rootProject.fileTree("docs/architecture/pokeball") {
    include("README.md", "applicability.md", "assembly.md", "authority-map.md", "baseline.md", "policy.md")
}
val architectureSources = rootProject.fileTree(rootProject.projectDir) {
    include("app/**/src/*Main/**/*.kt")
    include("app/**/src/*Test/**/*.kt")
    include("app/**/src/main/**/*.kt")
    include("app/**/src/test/**/*.kt")
    include("ball/**/src/*Main/**/*.kt")
    include("ball/**/src/*Test/**/*.kt")
    include("ball/**/src/main/**/*.kt")
    include("ball/**/src/test/**/*.kt")
    include("flow/**/src/*Main/**/*.kt")
    include("flow/**/src/*Test/**/*.kt")
    include("flow/**/src/main/**/*.kt")
    include("flow/**/src/test/**/*.kt")
    include("foundation/**/src/*Main/**/*.kt")
    include("foundation/**/src/*Test/**/*.kt")
    include("foundation/**/src/main/**/*.kt")
    include("foundation/**/src/test/**/*.kt")
    include("resource/**/src/*Main/**/*.kt")
    include("resource/**/src/*Test/**/*.kt")
    include("resource/**/src/main/**/*.kt")
    include("resource/**/src/test/**/*.kt")
    include("build-logic/src/main/kotlin/kinetickk/gradle/pokeball/CumulativeFanoutPolicy.kt")
    include("build-logic/src/test/kotlin/kinetickk/gradle/pokeball/PokeballArchitectureVerifierTest.kt")
    exclude("**/build/**", "**/.gradle/**")
}

val generatePokeballManifestTask = tasks.register<GeneratePokeballResolvedManifestTask>(
    "generatePokeballResolvedManifest",
) {
    group = "verification"
    description = "Generates the non-authoritative Pokeball architecture projection."
    leafProjectPaths.set(
        rootProject.subprojects
            .filter { it.childProjects.isEmpty() }
            .map { it.path }
            .toSet(),
    )
    assemblyRecord.set(rootProject.layout.projectDirectory.file("docs/architecture/pokeball/assembly.md"))
    outputFile.set(generatedManifestFile)
}

tasks.register<Copy>("updatePokeballResolvedManifest") {
    group = "build setup"
    description = "Updates the checked generated Pokeball projection after explicit review."
    dependsOn(generatePokeballManifestTask)
    from(generatedManifestFile)
    into(rootProject.layout.projectDirectory.dir("docs/architecture/pokeball"))
}

val verifyPokeballManifestTask = tasks.register<VerifyPokeballManifestDriftTask>(
    "verifyPokeballManifestDrift",
) {
    group = "verification"
    description = "Fails when the checked non-authoritative Pokeball projection has drifted."
    dependsOn(generatePokeballManifestTask)
    generatedManifest.set(generatedManifestFile)
    checkedManifest.set(checkedManifestFile)
}

val snapshotPath = providers.gradleProperty("pokeballSnapshotDir")
    .orElse(providers.environmentVariable("POKEBALL_SNAPSHOT_DIR"))
    .orElse(rootProject.layout.projectDirectory.dir("../Pokeball").asFile.absolutePath)
val repositoryDirectory = rootProject.layout.projectDirectory.asFile
val configuredSnapshotPath = java.io.File(snapshotPath.get())
val configuredSnapshotDirectory = if (configuredSnapshotPath.isAbsolute) {
    configuredSnapshotPath
} else {
    repositoryDirectory.resolve(configuredSnapshotPath)
}
val verifyPokeballSnapshotTask = tasks.register<VerifyPokeballSnapshotTask>("verifyPokeballSnapshot") {
    group = "verification"
    description = "Verifies the exact external Pokeball Core and Agent Pack snapshot/digests."
    snapshotDirectory.set(configuredSnapshotDirectory)
    baselineRecord.set(rootProject.layout.projectDirectory.file("docs/architecture/pokeball/baseline.md"))
    reportFile.set(rootProject.layout.buildDirectory.file("reports/pokeball/snapshot-integrity.json"))
}

val verifyPokeballArchitectureTask = tasks.register<VerifyPokeballArchitectureTask>(
    "verifyPokeballArchitecture",
) {
    group = "verification"
    description = "Enforces KINETICKK Pokeball ownership, role, route, DAG, and bound invariants."
    dependsOn(
        verifyArchitectureTask,
        verifyPokeballSnapshotTask,
        verifyPokeballManifestTask,
        gradle.includedBuild("build-logic").task(":test"),
    )
    leafProjectPaths.set(
        rootProject.subprojects
            .filter { it.childProjects.isEmpty() }
            .map { it.path }
            .toSet(),
    )
    productionSourceFiles.from(architectureSources)
    architectureRecordFiles.from(architectureRecords)
    repositoryRoot.set(rootProject.layout.projectDirectory)
    reportFile.set(rootProject.layout.buildDirectory.file("reports/pokeball/architecture.json"))
}

tasks.register<VerifyPokeballConformanceTask>("verifyPokeballConformance") {
    group = "verification"
    description = "Verifies conformance prerequisites or a strict docs-only freeze attestation."
    dependsOn(verifyPokeballArchitectureTask)
    repositoryRoot.set(rootProject.layout.projectDirectory)
    reportFile.set(rootProject.layout.buildDirectory.file("reports/pokeball/conformance.json"))
}

rootProject.allprojects {
    val sourceProjectPath = path
    configurations.configureEach {
        val declarationConfiguration = this
        dependencies.withType(ProjectDependency::class.java).configureEach projectDependency@{
            val targetProjectPath = path
            // Android/AGP realizes resolvable classpaths by copying declared project
            // dependencies. Only the declarable source configuration belongs to the
            // architecture graph; generated classpaths must not make it task-order dependent.
            if (
                !declarationConfiguration.isCanBeDeclared ||
                declarationConfiguration.name.endsWith("CompileClasspath") ||
                declarationConfiguration.name.endsWith("RuntimeClasspath")
            ) {
                return@projectDependency
            }
            verifyArchitectureTask.configure {
                declaredProjectDependencies.add(
                    "$sourceProjectPath\t${declarationConfiguration.name}\t$targetProjectPath",
                )
            }
            generatePokeballManifestTask.configure {
                declaredProjectDependencies.add(
                    "$sourceProjectPath\t${declarationConfiguration.name}\t$targetProjectPath",
                )
            }
            verifyPokeballArchitectureTask.configure {
                declaredProjectDependencies.add(
                    "$sourceProjectPath\t${declarationConfiguration.name}\t$targetProjectPath",
                )
            }
        }
    }
}
