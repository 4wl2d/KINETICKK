// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.flow.session.api)
            implementation(projects.foundation.common)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
        }
    }
}
