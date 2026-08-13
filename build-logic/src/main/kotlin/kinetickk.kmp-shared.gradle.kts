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

        // Android-KMP disables resources, Java, and both test components by default. Keep those
        // lean defaults for every leaf; the few modules that need a capability opt in locally.
        androidResources {
            enable = false
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
