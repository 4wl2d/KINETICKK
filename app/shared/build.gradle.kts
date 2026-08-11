// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.resource.audio.api)
            implementation(projects.resource.audio.impl)
            implementation(projects.ball.content.api)
            implementation(projects.ball.content.impl)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.profile.interaction)
            implementation(projects.ball.profile.impl)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.interaction)
            implementation(projects.ball.gameplay.impl)
            implementation(projects.flow.session.api)
            implementation(projects.flow.session.interaction)
            implementation(projects.flow.session.impl)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
