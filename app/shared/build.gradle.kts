// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.isolatedProjectsProfileEnabled
import kinetickk.gradle.BuildVariant
import kinetickk.gradle.gameBuildVariant
import kinetickk.gradle.WriteDebugBuildIdentityTask
import kinetickk.gradle.gitOutput
import kinetickk.gradle.sourceIdentityFiles

plugins {
    id("kinetickk.compose-library")
}

if (gameBuildVariant() == BuildVariant.DEBUG) {
    val debugBuildIdentity = tasks.register<WriteDebugBuildIdentityTask>("writeDebugBuildIdentity") {
        gameVersion.set(project.version.toString())
        gitRevision.set(gitOutput("rev-parse", "HEAD"))
        gitDirty.set(gitOutput("status", "--porcelain=v1", "--untracked-files=normal").map { it.isNotBlank() })
        repositoryDirectory.set(rootDir)
        buildSources.from(sourceIdentityFiles())
        outputDirectory.set(layout.buildDirectory.dir("generated/debug-build-identity"))
    }
    kotlin.sourceSets.named("commonMain") { kotlin.srcDir(debugBuildIdentity) }
}

kotlin {
    android {
        // The standalone application keeps the historical `kinetickk.app.shared` namespace.
        // AGP 9.3 requires every consumed library to use a distinct namespace even when neither
        // module references an R class, so keep this implementation-only namespace unique.
        namespace = "kinetickk.app.shared.library"

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.foundation.common)
            implementation(libs.compose.foundation)
            implementation(projects.foundation.design)
            implementation(libs.compose.runtime)
            implementation(projects.resource.audio.api)
            implementation(projects.resource.audio.impl)
            implementation(projects.ball.content.api)
            implementation(projects.ball.content.impl)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.profile.interaction)
            implementation(projects.ball.profile.impl)
            implementation(projects.ball.gameplay.impl)
            implementation(projects.flow.session.interaction)
            implementation(projects.flow.session.impl)
        }
        commonTest.dependencies {
            implementation(projects.foundation.common)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.interaction)
            implementation(projects.ball.gameplay.nucleus)
            implementation(projects.flow.session.api)
        }
        desktopTest.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui.test.junit4)
            val os = when {
                System.getProperty("os.name").startsWith("Mac") -> "macos"
                System.getProperty("os.name").startsWith("Windows") -> "windows"
                else -> "linux"
            }
            val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
            runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:${libs.versions.skiko.get()}")
        }
        if (!isolatedProjectsProfileEnabled()) {
            // The web display host reads the browser's fullscreen API.
            wasmJsMain.dependencies {
                implementation(libs.kotlinx.browser)
            }
            wasmJsTest.dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}
