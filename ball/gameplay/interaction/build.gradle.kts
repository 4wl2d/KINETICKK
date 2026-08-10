// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(projects.ball.gameplay.api)

            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.foundation.design)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.nucleus)
        }
    }
}
