// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.profile.api)
            implementation(projects.core.common)
            implementation(projects.core.content)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
