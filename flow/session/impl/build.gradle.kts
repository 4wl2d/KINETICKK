// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.flow.session.api)
            api(projects.ball.gameplay.interaction)

            implementation(projects.foundation.common)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
            implementation(projects.flow.session.nucleus)
        }
    }
}
