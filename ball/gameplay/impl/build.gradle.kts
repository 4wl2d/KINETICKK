// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.gameplay.interaction)
            api(projects.resource.audio.api)

            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.foundation.design)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.nucleus)
        }
    }
}
