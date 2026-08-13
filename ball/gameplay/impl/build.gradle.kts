// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.benchmarkSourceContract

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.gameplay.interaction)
            api(projects.resource.audio.api)

            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.foundation.design)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.nucleus)
        }
        desktopTest {
            kotlin.srcDir(rootDir.resolve("tools/performance/harness/src/main/kotlin"))
        }
    }
}

val desktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("componentPerformanceBenchmark") {
    group = "verification"
    description =
        "Runs real GameComponent publication-pipeline benchmarks and writes raw JSON samples."
    dependsOn(desktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set(
        "kinetickk.ball.gameplay.impl.performance.GameplayComponentPerformanceBenchmarkKt",
    )
    classpath(
        desktopBenchmarkCompilation.output.allOutputs,
        desktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    val adapterPath =
        "ball/gameplay/impl/src/desktopTest/kotlin/kinetickk/ball/gameplay/impl/performance/" +
            "GameplayComponentPerformanceBenchmark.kt"
    benchmarkSourceContract(
        rootDir,
        adapterPath,
        "ball/gameplay/impl/build.gradle.kts",
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
        "benchmarkOutput" to rootDir.resolve(
            "build/performance/gameplay-component-result.json",
        ).absolutePath,
        "benchmarkLabel" to project.name,
        "benchmarkRevision" to "unknown",
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
    propertyMappings.forEach { (gradleName, systemName) ->
        val value = providers.gradleProperty(gradleName).orNull ?: defaults[gradleName]
        if (value != null) systemProperty(systemName, value)
    }
}
