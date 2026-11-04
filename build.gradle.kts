// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.gameBuildVariant

plugins {
    id("kinetickk.architecture")
}

// Kotlin/Wasm's shared npm and linker state is also variant-specific.
layout.buildDirectory.set(layout.projectDirectory.dir(gameBuildVariant().directory))
