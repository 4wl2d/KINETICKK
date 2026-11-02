// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.configureSkikoWasmRuntime
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kinetickk.kmp-shared")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    // The in-game recorder has explicit phases and build identity. General Compose inspector
    // bookkeeping is unused by it and would distort Debug profiling, so omit it in both variants.
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)
