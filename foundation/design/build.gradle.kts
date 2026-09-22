// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
    id("org.jetbrains.compose")
}

compose.resources {
    packageOfResClass = "kinetickk.foundation.design.generated.resources"
}

kotlin {
    android { androidResources.enable = true }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
            api(projects.foundation.common)
            implementation(libs.compose.resources)
        }
        desktopTest.dependencies {
            val os = when {
                System.getProperty("os.name").startsWith("Mac") -> "macos"
                System.getProperty("os.name").startsWith("Windows") -> "windows"
                else -> "linux"
            }
            val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
            runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:${libs.versions.skiko.get()}")
        }
    }
}
