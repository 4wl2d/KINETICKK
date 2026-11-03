// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME
import kinetickk.gradle.ARCHITECTURE_EDGE_REPORTS_CONFIGURATION_NAME
import kinetickk.gradle.SourceStructureTask
import kinetickk.gradle.UpdateSourceStructureBaselineTask
import kinetickk.gradle.VerifyArchitectureTask
import kinetickk.gradle.VerifySourceStructureTask

val leafProjects = rootProject.subprojects.filter { it.childProjects.isEmpty() }
val leafProjectPathValues = leafProjects.map { it.path }.toSet()
val architectureEdgeReports = configurations.create(
    ARCHITECTURE_EDGE_REPORTS_CONFIGURATION_NAME,
) {
    description = "Resolved deterministic project-local architecture edge reports."
    isCanBeConsumed = false
    isCanBeResolved = true
    isCanBeDeclared = true
    isTransitive = false
}
dependencies {
    leafProjectPathValues.sorted().forEach { leafProjectPath ->
        architectureEdgeReports(
            project(
                path = leafProjectPath,
                configuration = ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME,
            ),
        )
    }
}

val repositoryRoot = layout.projectDirectory

// Module paths map directly to directories in settings.gradle.kts. Trees are built from path strings
// with the root project's services, without invoking another Project during configuration, and are
// rooted at <leaf>/src so they never walk .claude/, build/ or node_modules/.
fun leafSourceTree(leafPath: String, production: Boolean) =
    rootProject.fileTree(repositoryRoot.dir(leafPath.removePrefix(":").replace(':', '/') + "/src")) {
        if (production) {
            // Every production root, including variant roots such as commonDebug; test source set names end in Test.
            include("*/kotlin/**/*.kt")
            exclude("*Test/**")
        } else {
            include("*Test/kotlin/**/*.kt")
        }
    }

fun SourceStructureTask.useRepositorySources() {
    repositoryDirectory.set(repositoryRoot)
    leafProjectPaths.set(leafProjectPathValues)
    leafProjectPathValues.sorted().forEach { leaf ->
        mainSourceFiles.from(leafSourceTree(leaf, production = true))
        testSourceFiles.from(leafSourceTree(leaf, production = false))
    }
    architectureDocument.set(repositoryRoot.file("docs/project/ARCHITECTURE.md"))
    baselineFile.set(repositoryRoot.file("build-logic/source-structure-baseline.tsv"))
}

val verifySourceStructure = tasks.register<VerifySourceStructureTask>("verifySourceStructure") {
    group = "verification"
    description = "Verifies file, function and package size ratchets, import boundaries and package cycles."
    useRepositorySources()
    reportFile.set(layout.buildDirectory.file("reports/source-structure/report.txt"))
    doNotTrackState("Scans about 1,000 Kotlin files in under a second; always re-verifies so moved files never reuse a stale result")
}

tasks.register<UpdateSourceStructureBaselineTask>("updateSourceStructureBaseline") {
    group = "verification"
    description = "Lowers source-structure ceilings and deletes stale rows; " +
        "-Pkinetickk.sourceStructure.resetBaseline=true rewrites them."
    useRepositorySources()
    resetBaseline.set(
        providers.gradleProperty("kinetickk.sourceStructure.resetBaseline").map(String::toBooleanStrict).orElse(false),
    )
    doNotTrackState("Rewrites the checked-in baseline")
}

val verifyModuleGraph = tasks.register<VerifyArchitectureTask>("verifyModuleGraph") {
    group = "verification"
    description = "Verifies module boundaries, forbidden nucleus and API imports and ambient nucleus randomness " +
        "without resolving classpaths."
    leafProjectPaths.set(leafProjectPathValues)
    architectureEdgeReportFiles.from(architectureEdgeReports)
    rootSourceFiles.from(rootProject.fileTree("src"))
    leafProjectPathValues.filter { it.endsWith(":nucleus") }.forEach { nucleusPath ->
        nucleusSourceFiles.from(leafSourceTree(nucleusPath, production = true))
    }
    leafProjectPathValues.filter { it.endsWith(":api") }.forEach { apiPath ->
        contractSourceFiles.from(leafSourceTree(apiPath, production = true))
    }
}

tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Verifies module boundaries, forbidden imports and the source structure ratchets."
    dependsOn(verifyModuleGraph, verifySourceStructure)
}
