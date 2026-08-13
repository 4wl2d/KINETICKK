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
enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")

rootProject.name = "KINETICKK"

include(
    ":app:android",
    ":app:shared",
    ":app:desktop",
    ":app:web",
    ":foundation:common",
    ":foundation:design",
    ":resource:audio:api",
    ":resource:audio:impl",
    ":ball:content:api",
    ":ball:content:impl",
    ":ball:profile:api",
    ":ball:profile:nucleus",
    ":ball:profile:resource",
    ":ball:profile:interaction",
    ":ball:profile:impl",
    ":ball:gameplay:api",
    ":ball:gameplay:nucleus",
    ":ball:gameplay:interaction",
    ":ball:gameplay:impl",
    ":flow:session:api",
    ":flow:session:nucleus",
    ":flow:session:interaction",
    ":flow:session:impl",
)
