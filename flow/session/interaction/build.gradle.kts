// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        desktopTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            val os = when {
                System.getProperty("os.name").startsWith("Mac") -> "macos"
                System.getProperty("os.name").startsWith("Windows") -> "windows"
                else -> "linux"
            }
            val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
            runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:${libs.versions.skiko.get()}")
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(projects.flow.session.api)
            api(projects.foundation.common)
            api(projects.resource.audio.api)
            api(projects.ball.content.api)
            api(projects.ball.profile.api)
            api(projects.ball.profile.interaction)
            api(projects.ball.gameplay.interaction)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.foundation.design)
        }
    }
}
