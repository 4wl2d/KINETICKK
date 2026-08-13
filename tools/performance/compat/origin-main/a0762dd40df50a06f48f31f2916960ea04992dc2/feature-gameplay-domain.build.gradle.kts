// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("kinetickk.kmp-shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.audio.api)
            api(projects.core.common)
            api(projects.core.content)
            api(projects.core.profile.api)
        }
        desktopTest {
            kotlin.srcDir(rootProject.file("tools/performance/harness/src/main/kotlin"))
            kotlin.srcDir(
                rootProject.file(
                    "tools/performance/compat/origin-main/" +
                        "a0762dd40df50a06f48f31f2916960ea04992dc2/src/desktopTest/kotlin",
                ),
            )
        }
    }
}

val desktopBenchmarkCompilation = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("test")

tasks.register<JavaExec>("performanceBenchmark") {
    group = "verification"
    description = "Runs the pinned origin/main gameplay compatibility benchmark."
    dependsOn(desktopBenchmarkCompilation.compileTaskProvider)
    mainClass.set("kinetickk.feature.gameplay.domain.performance.GameplayPerformanceBenchmarkKt")
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
            .file("performance/gameplay-origin-main-result.json")
            .get().asFile.absolutePath,
        "benchmarkLabel" to "origin/main",
        "benchmarkRevision" to "a0762dd40df50a06f48f31f2916960ea04992dc2",
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
