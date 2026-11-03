// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import kinetickk.gradle.BuildVariant
import kinetickk.gradle.gameBuildVariant

plugins {
    id("kinetickk.base")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gameVariant = gameBuildVariant()

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
        versionCode = 6
        versionName = project.version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            // Local launch uses the developer key explicitly; distribution stays unsigned
            // unless a release-signing setup is supplied by the distributor.
            if (providers.gradleProperty("kinetickk.localSigning").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
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

// KMP libraries have one Android compilation, so the graph must agree on one mode.
extensions.configure<ApplicationAndroidComponentsExtension> {
    beforeVariants { variant ->
        variant.enable = if (gameVariant == BuildVariant.DEBUG) {
            variant.buildType == "debug"
        } else {
            variant.buildType != "debug"
        }
    }
}
