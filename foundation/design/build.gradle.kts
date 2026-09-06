// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.configureSkikoWasmRuntime

plugins {
    id("kinetickk.kmp-shared")
}

configureSkikoWasmRuntime(libs.versions.skiko.get())

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
            api(projects.foundation.common)
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
