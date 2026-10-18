// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kinetickk.kmp-platforms")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = project.path.removePrefix(":").replace(':', '.').let { "kinetickk.$it" }
        compileSdk = 37
        minSdk = 26

        // Include commonTest in Android's host JVM tests. Resources, Java, and device tests
        // stay opt-in for the modules that need them.
        withHostTest {}

        androidResources {
            enable = false
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
