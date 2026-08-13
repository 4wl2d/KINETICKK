// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-android-application")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(projects.resource.audio.api)
            implementation(projects.resource.audio.impl)
            implementation(projects.ball.content.api)
            implementation(projects.ball.content.impl)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.profile.interaction)
            implementation(projects.ball.profile.impl)
            implementation(projects.ball.gameplay.impl)
            implementation(projects.flow.session.interaction)
            implementation(projects.flow.session.impl)
        }
        commonTest.dependencies {
            implementation(projects.foundation.common)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.interaction)
            implementation(projects.flow.session.api)
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlinx.browser)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}
