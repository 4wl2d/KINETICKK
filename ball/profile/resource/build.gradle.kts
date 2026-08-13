// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.benchmarkSourceContract

plugins {
    id("kinetickk.kmp-shared")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.profile.api)
            api(projects.ball.content.api)
            implementation(libs.kotlinx.serialization.json)
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlinx.browser)
        }
        desktopTest {
            kotlin.srcDir(rootProject.file("tools/performance/harness/src/main/kotlin"))
        }
    }
}

val profileDesktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("profilePerformanceBenchmark") {
    group = "verification"
    description = "Runs deterministic profile codec and persistence benchmarks and writes raw JSON samples."
    dependsOn(profileDesktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set("kinetickk.ball.profile.resource.performance.ProfilePerformanceBenchmarkKt")
    classpath(
        profileDesktopBenchmarkCompilation.output.allOutputs,
        profileDesktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    benchmarkSourceContract(
        rootProject.layout.projectDirectory.asFile,
        "ball/profile/resource/src/desktopTest/kotlin/kinetickk/ball/profile/resource/performance/ProfilePerformanceBenchmark.kt",
        "ball/profile/resource/build.gradle.kts",
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
        "benchmarkLabel" to project.name,
        "benchmarkRevision" to "unknown",
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
    propertyMappings.forEach { (gradleName, systemName) ->
        val value = providers.gradleProperty(gradleName).orNull ?: defaults[gradleName]
        if (value != null) systemProperty(systemName, value)
    }
}
