// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.ui)
            implementation(projects.core.common)
            implementation(projects.feature.game.domain)
        }
    }
}
