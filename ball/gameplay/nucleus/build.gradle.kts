// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.benchmarkSourceContract

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.foundation.common)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
        }
        commonTest.dependencies {
            implementation(projects.ball.content.impl)
        }
        desktopTest {
            kotlin.srcDir(rootProject.file("tools/performance/harness/src/main/kotlin"))
        }
    }
}

val desktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("performanceBenchmark") {
    group = "verification"
    description = "Runs deterministic gameplay performance benchmarks and writes raw JSON samples."
    dependsOn(desktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set("kinetickk.ball.gameplay.nucleus.performance.GameplayPerformanceBenchmarkKt")
    classpath(
        desktopBenchmarkCompilation.output.allOutputs,
        desktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    benchmarkSourceContract(
        rootProject.layout.projectDirectory.asFile,
        "ball/gameplay/nucleus/src/desktopTest/kotlin/kinetickk/ball/gameplay/nucleus/performance/GameplayPerformanceBenchmark.kt",
        "ball/gameplay/nucleus/build.gradle.kts",
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
