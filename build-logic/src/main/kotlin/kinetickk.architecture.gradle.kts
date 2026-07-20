// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.VerifyArchitectureTask
import org.gradle.api.artifacts.ProjectDependency

val verifyArchitectureTask = tasks.register<VerifyArchitectureTask>("verifyArchitecture") {
    group = "verification"
    description = "Verifies the vertical feature module graph without resolving dependencies."
    leafProjectPaths.set(
        rootProject.subprojects
            .filter { it.childProjects.isEmpty() }
            .map { it.path }
            .toSet(),
    )
    rootSourceFiles.from(rootProject.fileTree("src"))
}

rootProject.allprojects {
    val sourceProjectPath = path
    configurations.configureEach {
        val declarationConfiguration = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            val targetProjectPath = path
            verifyArchitectureTask.configure {
                declaredProjectDependencies.add(
                    "$sourceProjectPath\t$declarationConfiguration\t$targetProjectPath",
                )
            }
        }
    }
}
