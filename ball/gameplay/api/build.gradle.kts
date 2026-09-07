// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.foundation.common)
            api(projects.ball.content.api)
            api(projects.ball.profile.api)
        }
    }
}
