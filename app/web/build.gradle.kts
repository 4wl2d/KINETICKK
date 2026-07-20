// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-wasm-application")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.game.api)
            implementation(projects.feature.game.impl)
        }
    }
}
