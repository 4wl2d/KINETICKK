// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

group = "kinetickk"
version = "0.1.0"

kotlin {
    jvm("desktop")

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
        val commonMain by getting
        val commonTest by getting
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "kinetickk.DesktopMainKt"
        nativeDistributions {
            packageName = "KINETICKK"
            packageVersion = "0.1.0"
            description = "KINETICKK physics-action roguelite"
            vendor = "Vladislav Tomilov"
            copyright = "Copyright (c) 2026 Vladislav Tomilov. Licensed under GPL-3.0-or-later."
        }
    }
}

val packagedLegalDocuments = listOf(
    "LICENSE",
    "NOTICE",
    "docs/project/AUTHORS.md",
    "docs/project/CONTRIBUTING.md",
    "docs/project/CONTRIBUTOR_LICENSE_AGREEMENT.md",
    "docs/project/GOVERNANCE.md",
    "docs/project/SOURCE.md",
    "docs/project/TRADEMARKS.md",
    "docs/project/THIRD_PARTY_NOTICES.md",
    "docs/project/ASSET_PROVENANCE.md",
    "docs/project/PRIVACY.md",
    "docs/project/LEGAL.md",
)

tasks.withType<Copy>().configureEach {
    if (name.endsWith("ProcessResources")) {
        from(rootProject.projectDir) {
            include(packagedLegalDocuments)
            into("META-INF")
        }
    }
}
