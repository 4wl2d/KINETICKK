// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(projects.flow.session.api)
            api(projects.foundation.common)
            api(projects.resource.audio.api)
            api(projects.ball.content.api)
            api(projects.ball.profile.api)
            api(projects.ball.profile.interaction)
            api(projects.ball.gameplay.interaction)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.foundation.design)
        }
    }
}
