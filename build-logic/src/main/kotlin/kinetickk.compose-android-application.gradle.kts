// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("kinetickk.base")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

extensions.configure<ApplicationExtension> {
    namespace = "kinetickk.app.shared"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vladislavtomilov.kinetickk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = project.version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            optimization {
                // AGP 9.3's unified switch enables R8 code optimization and optimized resource
                // shrinking together, with the optimized Android defaults included implicitly.
                enable = true

                // Compose Runtime's consumer file hard-codes the default GapBuffer choice. The
                // application owns an equivalent rule set with LinkBuffer selected instead, so
                // exclude only that artifact's consumer rules to leave R8 one unambiguous value.
                keepRules {
                    ignoreFrom("androidx.compose.runtime:runtime-android")
                }
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/AL2.0",
            "/META-INF/LGPL2.1",
        )
    }
}
