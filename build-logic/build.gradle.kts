// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
    // DesktopTestShardsTest reads every desktop test source of the repository and ci.yml; it reruns when one changes.
    inputs.files(
        fileTree(rootDir.parentFile) {
            include(
                "**/src/commonTest/kotlin/**/*.kt", "**/src/desktopTest/kotlin/**/*.kt",
                "**/src/commonReleaseTest/kotlin/**/*.kt", "**/src/desktopReleaseTest/kotlin/**/*.kt",
            )
            exclude(".claude/**", "**/build/**", "**/build-debug/**", "**/.gradle/**", "build-logic/**")
        },
    )
        .withPropertyName("desktopTestSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootDir.parentFile.resolve(".github/workflows/ci.yml"))
        .withPropertyName("ciWorkflow")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
