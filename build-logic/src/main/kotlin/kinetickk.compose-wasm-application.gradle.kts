// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.packageLegalDocuments
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "kinetickk"
        browser {
            commonWebpackConfig {
                outputFileName = "kinetickk.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libraries.findLibrary("compose-runtime").get())
            implementation(libraries.findLibrary("compose-foundation").get())
            implementation(libraries.findLibrary("compose-ui").get())
            implementation(libraries.findLibrary("kotlinx-browser").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

packageLegalDocuments()
