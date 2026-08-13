// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.isolatedProjectsProfileEnabled

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    android {
        // The standalone application keeps the historical `kinetickk.app.shared` namespace.
        // AGP 9.3 requires every consumed library to use a distinct namespace even when neither
        // module references an R class, so keep this implementation-only namespace unique.
        namespace = "kinetickk.app.shared.library"

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

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
        if (!isolatedProjectsProfileEnabled()) {
            wasmJsTest.dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}
