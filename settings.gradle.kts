// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "KINETICKK"

include(
    ":app:shared",
    ":app:desktop",
    ":app:web",
    ":core:audio:api",
    ":core:audio:impl",
    ":core:common",
    ":core:content",
    ":core:profile:api",
    ":core:profile:data",
    ":core:design-system",
    ":feature:gameplay:api",
    ":feature:gameplay:domain",
    ":feature:gameplay:impl",
    ":feature:gameplay:presentation",
    ":feature:home:api",
    ":feature:home:impl",
    ":feature:settings:api",
    ":feature:settings:impl",
    ":feature:lab:api",
    ":feature:lab:impl",
    ":feature:armory:api",
    ":feature:armory:impl",
    ":feature:rebirth:api",
    ":feature:rebirth:impl",
    ":feature:codex:api",
    ":feature:codex:impl",
)
