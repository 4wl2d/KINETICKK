// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.WriteDesktopBuildInfoTask
import kinetickk.gradle.IsolateDesktopRuntimeAction
import kinetickk.gradle.DeleteSuccessfulDesktopRuntimeAction
import kinetickk.gradle.BuildVariant
import kinetickk.gradle.gameBuildVariant
import kinetickk.gradle.gitOutput
import kinetickk.gradle.sourceIdentityFiles

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
        gitRevision.set(gitOutput("rev-parse", "HEAD"))
        gitStatus.set(gitOutput("status", "--porcelain=v1", "--untracked-files=normal"))
        sourceDateEpoch.set(providers.environmentVariable("SOURCE_DATE_EPOCH")
            .orElse(gitOutput("show", "-s", "--format=%ct", "HEAD")).map { it.trim().toLongOrNull() ?: 0L })
        repositoryDirectory.set(rootDir)
        buildSources.from(sourceIdentityFiles())
        outputDirectory.set(layout.buildDirectory.dir("generated/crash-build-info"))
    }

    sourceSets.main { resources.srcDir(buildInfo) }

    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        providers.gradleProperty("crashDir").orNull?.let { systemProperty("kinetickk.crashDir", it) }
        doFirst(IsolateDesktopRuntimeAction(objects.fileCollection()))
        doLast(DeleteSuccessfulDesktopRuntimeAction())
    }
}

// ./gradlew :app:desktop:run -Pkinetickk.uncapped=true turns V-Sync off (see DesktopFramePacing).
tasks.withType<JavaExec>().matching { it.name == "run" || it.name == "runRelease" }.configureEach {
    providers.gradleProperty("kinetickk.uncapped").orNull?.let { systemProperty("kinetickk.uncapped", it) }
}
