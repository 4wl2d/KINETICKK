// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.benchmarkSourceContract

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(projects.ball.gameplay.api)

            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.foundation.design)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.nucleus)
        }
        desktopTest {
            kotlin.srcDir(rootDir.resolve("tools/performance/harness/src/main/kotlin"))
        }
    }
}

val telemetryDesktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("performanceTelemetryBenchmark") {
    group = "verification"
    description = "Measures the disabled and enabled runtime performance telemetry overhead."
    dependsOn(telemetryDesktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set(
        "kinetickk.ball.gameplay.interaction.performance.GameplayTelemetryPerformanceBenchmarkKt",
    )
    classpath(
        telemetryDesktopBenchmarkCompilation.output.allOutputs,
        telemetryDesktopBenchmarkCompilation.runtimeDependencyFiles,
    )
    benchmarkSourceContract(
        rootDir,
        "ball/gameplay/interaction/src/desktopTest/kotlin/kinetickk/ball/gameplay/interaction/performance/GameplayTelemetryPerformanceBenchmark.kt",
        "ball/gameplay/interaction/build.gradle.kts",
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
            "build/performance/gameplay-telemetry-result.json",
        ).absolutePath,
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
