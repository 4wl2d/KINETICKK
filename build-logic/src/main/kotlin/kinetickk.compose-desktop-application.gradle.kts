// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.packageLegalDocuments
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "kinetickk.app.desktop.DesktopMainKt"

        nativeDistributions {
            packageName = "KINETICKK"
            packageVersion = project.version.toString()
            description = "KINETICKK physics-action roguelite"
            vendor = "Vladislav Tomilov"
            copyright = "Copyright (c) 2026 Vladislav Tomilov. Licensed under GPL-3.0-or-later."
        }
    }
}

packageLegalDocuments()
