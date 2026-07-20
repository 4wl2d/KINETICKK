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
    ":app:desktop",
    ":app:web",
    ":core:common",
    ":feature:game:api",
    ":feature:game:data",
    ":feature:game:domain",
    ":feature:game:impl",
    ":feature:game:presentation",
)
