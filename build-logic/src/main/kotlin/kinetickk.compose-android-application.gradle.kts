// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.ApplicationExtension
import kinetickk.gradle.configureSkikoWasmRuntime
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    id("kinetickk.kmp-platforms")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "kinetickk.app.shared"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vladislavtomilov.kinetickk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = project.version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
