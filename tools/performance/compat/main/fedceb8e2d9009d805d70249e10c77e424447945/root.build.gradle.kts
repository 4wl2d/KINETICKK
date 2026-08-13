// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

group = "kinetickk"
version = "0.1.0"

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "kinetickk"
        browser {
            commonWebpackConfig {
                outputFileName = "kinetickk.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val desktopMain by getting
        val desktopTest by getting {
            kotlin.srcDir(rootProject.file("tools/performance/harness/src/main/kotlin"))
            kotlin.srcDir(
                rootProject.file(
                    "tools/performance/compat/main/" +
                        "fedceb8e2d9009d805d70249e10c77e424447945/src/desktopTest/kotlin",
                ),
            )
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "kinetickk.DesktopMainKt"
        nativeDistributions {
            packageName = "KINETICKK"
            packageVersion = "0.1.0"
            description = "KINETICKK physics-action roguelite"
            vendor = "Vladislav Tomilov"
            copyright = "Copyright (c) 2026 Vladislav Tomilov. Licensed under GPL-3.0-or-later."
        }
    }
}

val packagedLegalDocuments = listOf(
    "LICENSE",
    "NOTICE",
    "docs/project/AUTHORS.md",
    "docs/project/CONTRIBUTING.md",
    "docs/project/CONTRIBUTOR_LICENSE_AGREEMENT.md",
    "docs/project/GOVERNANCE.md",
    "docs/project/SOURCE.md",
    "docs/project/TRADEMARKS.md",
    "docs/project/THIRD_PARTY_NOTICES.md",
    "docs/project/ASSET_PROVENANCE.md",
    "docs/project/PRIVACY.md",
    "docs/project/LEGAL.md",
)

tasks.withType<Copy>().configureEach {
    if (name.endsWith("ProcessResources")) {
        from(rootProject.projectDir) {
            include(packagedLegalDocuments)
            into("META-INF")
        }
    }
}

val desktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("performanceBenchmark") {
    group = "verification"
    description = "Runs deterministic main compatibility gameplay performance benchmarks."
    dependsOn(desktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set("kinetickk.features.game.nucleus.performance.GameplayPerformanceBenchmarkKt")
    classpath(
        desktopBenchmarkCompilation.output.allOutputs,
        desktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    jvmArgs(
        "-Xms1g",
        "-Xmx1g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC",
        "-Dfile.encoding=UTF-8",
    )

    val defaults = mapOf(
        "benchmarkProfile" to "standard",
        "benchmarkOutput" to rootProject.layout.buildDirectory
            .file("performance/gameplay-result.json")
            .get().asFile.absolutePath,
        "benchmarkLabel" to "main",
        "benchmarkRevision" to "fedceb8e2d9009d805d70249e10c77e424447945",
        "benchmarkDirty" to "false",
        "benchmarkFork" to "1",
        "benchmarkSeed" to "731991",
    )
    val propertyMappings = mapOf(
        "benchmarkProfile" to "kinetickk.benchmark.profile",
        "benchmarkOutput" to "kinetickk.benchmark.output",
        "benchmarkLabel" to "kinetickk.benchmark.label",
        "benchmarkRevision" to "kinetickk.benchmark.revision",
        "benchmarkDirty" to "kinetickk.benchmark.dirty",
        "benchmarkFork" to "kinetickk.benchmark.fork",
        "benchmarkSeed" to "kinetickk.benchmark.seed",
        "benchmarkScenarios" to "kinetickk.benchmark.scenarios",
        "benchmarkWarmups" to "kinetickk.benchmark.warmups",
        "benchmarkMeasurements" to "kinetickk.benchmark.measurements",
        "benchmarkIterationMillis" to "kinetickk.benchmark.iterationMillis",
    )
    doFirst {
        propertyMappings.forEach { (gradleName, systemName) ->
            val value = providers.gradleProperty(gradleName).orNull ?: defaults[gradleName]
            if (value != null) systemProperty(systemName, value)
        }
    }
}

tasks.register<JavaExec>("profilePerformanceBenchmark") {
    group = "verification"
    description = "Runs branch-native legacy-v3 profile codec benchmarks for pinned main."
    dependsOn(desktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set("kinetickk.features.game.resources.progress.performance.ProfilePerformanceBenchmarkKt")
    classpath(
        desktopBenchmarkCompilation.output.allOutputs,
        desktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    jvmArgs(
        "-Xms1g",
        "-Xmx1g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC",
        "-Dfile.encoding=UTF-8",
    )

    val defaults = mapOf(
        "benchmarkProfile" to "standard",
        "benchmarkOutput" to rootProject.layout.buildDirectory
            .file("performance/profile-result.json")
            .get().asFile.absolutePath,
        "benchmarkLabel" to "main",
        "benchmarkRevision" to "fedceb8e2d9009d805d70249e10c77e424447945",
        "benchmarkDirty" to "false",
        "benchmarkFork" to "1",
    )
    val propertyMappings = mapOf(
        "benchmarkProfile" to "kinetickk.benchmark.profile",
        "benchmarkOutput" to "kinetickk.benchmark.output",
        "benchmarkLabel" to "kinetickk.benchmark.label",
        "benchmarkRevision" to "kinetickk.benchmark.revision",
        "benchmarkDirty" to "kinetickk.benchmark.dirty",
        "benchmarkFork" to "kinetickk.benchmark.fork",
        "benchmarkScenarios" to "kinetickk.benchmark.scenarios",
        "benchmarkWarmups" to "kinetickk.benchmark.warmups",
        "benchmarkMeasurements" to "kinetickk.benchmark.measurements",
        "benchmarkIterationMillis" to "kinetickk.benchmark.iterationMillis",
    )
    doFirst {
        propertyMappings.forEach { (gradleName, systemName) ->
            val value = providers.gradleProperty(gradleName).orNull ?: defaults[gradleName]
            if (value != null) systemProperty(systemName, value)
        }
    }
}
