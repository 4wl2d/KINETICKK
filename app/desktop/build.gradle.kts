// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.compose-desktop-application")
}

dependencies {
    implementation(projects.feature.game.api)
    implementation(projects.feature.game.impl)
}
