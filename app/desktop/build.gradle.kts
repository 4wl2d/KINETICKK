// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.WriteDesktopBuildInfoTask
import kinetickk.gradle.IsolateDesktopRuntimeAction
import kinetickk.gradle.DeleteSuccessfulDesktopRuntimeAction

plugins {
    id("kinetickk.compose-desktop-application")
}

dependencies {
    implementation(projects.app.shared)
}

val buildInfo = tasks.register<WriteDesktopBuildInfoTask>("writeDesktopBuildInfo") {
    mustRunAfter(":updatePokeballResolvedManifest")
    gameVersion.set(project.version.toString())
    kotlinVersion.set(libs.versions.kotlin)
    composeVersion.set(libs.versions.compose)
    gitRevision.set(providers.exec {
        workingDir(rootDir)
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    gitStatus.set(providers.exec {
        workingDir(rootDir)
        commandLine("git", "status", "--porcelain=v1", "--untracked-files=normal")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    repositoryDirectory.set(rootDir)
    buildSources.from(fileTree(rootDir) {
        include("**/src/**/*.kt", "**/*.gradle.kts", "gradle/libs.versions.toml", "gradle.properties")
        exclude("**/build/**", "**/.gradle/**")
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/crash-build-info"))
}

sourceSets.main { resources.srcDir(buildInfo) }

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    providers.gradleProperty("crashDir").orNull?.let { systemProperty("kinetickk.crashDir", it) }
    doFirst(IsolateDesktopRuntimeAction(objects.fileCollection()))
    doLast(DeleteSuccessfulDesktopRuntimeAction())
}
