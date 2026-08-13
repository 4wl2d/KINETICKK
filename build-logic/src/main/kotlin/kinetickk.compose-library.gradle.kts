// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.configureSkikoWasmRuntime
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kinetickk.kmp-shared")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    // Shipping binaries do not consume Compose tooling source coordinates or
    // profiler trace markers. Excluding both shrinks every platform artifact
    // and removes their compiler/runtime bookkeeping without changing UI state.
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)
