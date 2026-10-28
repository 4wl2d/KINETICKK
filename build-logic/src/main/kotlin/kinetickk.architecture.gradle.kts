// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME
import kinetickk.gradle.ARCHITECTURE_EDGE_REPORTS_CONFIGURATION_NAME
import kinetickk.gradle.VerifyArchitectureTask

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

tasks.register<VerifyArchitectureTask>("verifyArchitecture") {
    group = "verification"
    description = "Verifies the declared module dependency graph without resolving classpaths."
    leafProjectPaths.set(leafProjectPathValues)
    architectureEdgeReportFiles.from(architectureEdgeReports)
    rootSourceFiles.from(rootProject.fileTree("src"))
}
