// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.profile.api)
            api(projects.ball.content.api)
            implementation(libs.kotlinx.serialization.json)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
