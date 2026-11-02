// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.packageLegalDocuments
import kinetickk.gradle.BuildVariant
import kinetickk.gradle.gameBuildVariant
import kinetickk.gradle.RejectDebugReleaseTask
import kinetickk.gradle.macOsPackageVersion
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gameVariant = gameBuildVariant()
val currentJavaHome = providers.systemProperty("java.home").get()
val packagingJavaHome = providers.gradleProperty("kinetickk.packagingJdk").orElse(
    if (file("$currentJavaHome/jmods/java.base.jmod").isFile) {
        providers.provider { currentJavaHome }
    } else {
        // Some JEP 493 JDK distributions omit jmods. Compose's ProGuard task still
        // requires those library inputs; use the installed LTS toolchain in that case.
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.map { it.metadata.installationPath.asFile.absolutePath }
    },
)

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir("src/${gameVariant.name.lowercase()}/kotlin")
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
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
        javaHome = packagingJavaHome.get()
        mainClass = "kinetickk.app.desktop.DesktopMainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        buildTypes.release.proguard {
            // ProGuard 7.10 adds support for the project's Kotlin 2.4 compiler metadata.
            version.set("7.10.0")
            isEnabled.set(true)
            optimize.set(true)
            obfuscate.set(true)
            joinOutputJars.set(true)
            configurationFiles.from(project.file("release.pro"))
        }

        nativeDistributions {
            packageName = if (gameVariant == BuildVariant.DEBUG) "KINETICKK Debug" else "KINETICKK"
            macOS.bundleID = if (gameVariant == BuildVariant.DEBUG) "kinetickk.app.desktop.debug" else "kinetickk.app.desktop"
            // java.desktop covers AWT, Skiko, fonts and audio; Preferences persists the profile.
            modules("java.desktop", "java.prefs", "jdk.unsupported")
            if (gameVariant == BuildVariant.DEBUG) modules("java.management", "jdk.management")
            packageVersion = project.version.toString()
            macOS {
                // jpackage forbids major zero. This packaging-only mapping keeps 0.x game
                // versions intact while retaining upgrade order when the game reaches 1.x.
                packageVersion = macOsPackageVersion(project.version.toString())
            }
            description = "KINETICKK physics-action roguelite"
            vendor = "Vladislav Tomilov"
            copyright = "Copyright (c) 2026 Vladislav Tomilov. Licensed under GPL-3.0-or-later."
        }
    }
}

// A task called Release must never package the diagnostic source set.
if (gameVariant == BuildVariant.DEBUG) {
    tasks.matching { "Release" in it.name || it.name == "runRelease" }.configureEach {
        doFirst(RejectDebugReleaseTask())
    }
}

packageLegalDocuments()
