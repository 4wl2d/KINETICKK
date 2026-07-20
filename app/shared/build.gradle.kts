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
            implementation(projects.core.audio.api)
            implementation(projects.core.audio.impl)
            implementation(projects.core.common)
            implementation(projects.core.content)
            implementation(projects.core.profile.api)
            implementation(projects.core.profile.data)
            implementation(projects.feature.home.api)
            implementation(projects.feature.home.impl)
            implementation(projects.feature.gameplay.api)
            implementation(projects.feature.gameplay.impl)
            implementation(projects.feature.settings.api)
            implementation(projects.feature.settings.impl)
            implementation(projects.feature.lab.api)
            implementation(projects.feature.lab.impl)
            implementation(projects.feature.armory.api)
            implementation(projects.feature.armory.impl)
            implementation(projects.feature.rebirth.api)
            implementation(projects.feature.rebirth.impl)
            implementation(projects.feature.codex.api)
            implementation(projects.feature.codex.impl)
        }
    }
}
