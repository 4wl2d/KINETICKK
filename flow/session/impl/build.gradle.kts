// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.configureSkikoWasmRuntime
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kinetickk.kmp-shared")
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
// This non-Compose session module exports gameplay.interaction; its own Wasm test binary therefore
// links transitive Compose UI and is an executable Skiko host even though its sources are UI-free.
configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)

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
