// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kinetickk.kmp-platforms")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = project.path.removePrefix(":").replace(':', '.').let { "kinetickk.$it" }
    compileSdk = 36

    // These KMP leaf libraries contain no Android manifests, resources, or R references. Keeping
    // resource processing enabled creates compile/package/generate-R tasks for every variant with
    // no deliverable output; the Android application host owns the actual resources instead.
    androidResources.enable = false

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
