// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.gameplay.api)
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(projects.core.designSystem)
            implementation(projects.core.profile.api)
            implementation(projects.feature.gameplay.domain)
            implementation(projects.feature.gameplay.presentation)
        }
    }
}
