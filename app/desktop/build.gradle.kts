// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.WriteDesktopBuildInfoTask
import kinetickk.gradle.IsolateDesktopRuntimeAction
import kinetickk.gradle.DeleteSuccessfulDesktopRuntimeAction
import kinetickk.gradle.BuildVariant
import kinetickk.gradle.gameBuildVariant

plugins {
    id("kinetickk.compose-desktop-application")
}

dependencies {
    implementation(projects.app.shared)
}

if (gameBuildVariant() == BuildVariant.DEBUG) {
    val buildInfo = tasks.register<WriteDesktopBuildInfoTask>("writeDesktopBuildInfo") {
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
        sourceDateEpoch.set(providers.environmentVariable("SOURCE_DATE_EPOCH").orElse(providers.exec {
            workingDir(rootDir)
            commandLine("git", "show", "-s", "--format=%ct", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText).map { it.trim().toLongOrNull() ?: 0L })
        repositoryDirectory.set(rootDir)
        buildSources.from(fileTree(rootDir) {
            include("**/src/**/*.kt", "**/*.gradle.kts", "gradle/libs.versions.toml", "gradle.properties")
            exclude("**/build/**", "**/build-debug/**", "**/.gradle/**")
        })
        outputDirectory.set(layout.buildDirectory.dir("generated/crash-build-info"))
    }

    sourceSets.main { resources.srcDir(buildInfo) }

    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        providers.gradleProperty("crashDir").orNull?.let { systemProperty("kinetickk.crashDir", it) }
        // ./gradlew :app:desktop:run -Pkinetickk.uncapped=true turns V-Sync off (see DesktopFramePacing).
        providers.gradleProperty("kinetickk.uncapped").orNull?.let { systemProperty("kinetickk.uncapped", it) }
        doFirst(IsolateDesktopRuntimeAction(objects.fileCollection()))
        doLast(DeleteSuccessfulDesktopRuntimeAction())
    }
}

tasks.withType<JavaExec>().matching { it.name == "runRelease" }.configureEach {
    providers.gradleProperty("kinetickk.uncapped").orNull?.let { systemProperty("kinetickk.uncapped", it) }
}
