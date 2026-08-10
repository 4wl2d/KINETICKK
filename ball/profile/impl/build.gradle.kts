// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.profile.api)
            api(projects.ball.content.api)
            implementation(projects.ball.profile.nucleus)
            implementation(projects.ball.profile.resource)
        }
    }
}
