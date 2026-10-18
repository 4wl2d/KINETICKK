// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kinetickk.gradle.isolatedProjectsProfileEnabled

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.multiplatform")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// Desktop tests render real frames with the bundled fonts; spread their classes over up to four JVMs.
tasks.withType<Test>().configureEach {
    if (name == "desktopTest") {
        maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
    // Keep the full assertion in the log as well as the archived CI test reports.
    testLogging {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Keep the JDK API surface at the supported runtime baseline, even on newer build JDKs.
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }

    if (!isolatedProjectsProfileEnabled()) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
