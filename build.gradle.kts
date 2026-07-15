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
        }
    }
}
