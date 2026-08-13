// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.packageLegalDocuments
import kinetickk.gradle.configureSkikoWasmRuntime
import kinetickk.gradle.isolatedProjectsProfileEnabled
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Delete
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.WasmBinary
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
val isolatedProjectsProfile = isolatedProjectsProfileEnabled()

kotlin {
    if (isolatedProjectsProfile) {
        // KGP requires at least one target. This non-shipping target keeps common metadata and the
        // app:web architecture edge modeled while the strict profile omits unsupported Wasm tasks.
        jvm("isolated") {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    } else {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            val wasmModuleName = "kinetickk"
            outputModuleName = wasmModuleName
            browser {
                commonWebpackConfig {
                    outputFileName = "$wasmModuleName.js"
                }
            }
            val executableBinaries = binaries.executable()
            val productionExecutable = executableBinaries
                .single { it.mode == KotlinJsBinaryMode.PRODUCTION }
            productionExecutable.linkTask.configure {
                // The optimized production module is shipped without source maps. Avoid
                // collecting locations and copying an unusable pre-Binaryen .wasm.map.
                compilerOptions.sourceMap.set(false)
                compilerOptions.sourceMapEmbedSources.unsetConvention()
            }

            val staleSourceMapName = "$wasmModuleName.wasm.map"
            val staleProductionSourceMaps = listOf(
                productionExecutable.outputDirBase.map {
                    it.dir("kotlin").file(staleSourceMapName)
                },
                productionExecutable.outputDirBase.map {
                    it.dir("optimized").file(staleSourceMapName)
                },
                productionExecutable.compilation.npmProject.dist.map {
                    it.file(staleSourceMapName)
                },
            )
            val removeStaleProductionSourceMaps = tasks.register<Delete>(
                "removeWasmJsProductionSourceMaps",
            ) {
                dependsOn(productionExecutable.linkTask)
                delete(staleProductionSourceMaps)
                onlyIf("stale production Wasm source maps exist") {
                    staleProductionSourceMaps.any { it.get().asFile.exists() }
                }
            }
            (productionExecutable as WasmBinary).optimizeTask.configure {
                dependsOn(removeStaleProductionSourceMaps)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libraries.findLibrary("compose-runtime").get())
            implementation(libraries.findLibrary("compose-foundation").get())
            implementation(libraries.findLibrary("compose-ui").get())
            implementation(libraries.findLibrary("kotlinx-browser").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)

if (!isolatedProjectsProfile) {
    tasks.named<KotlinWebpack>("wasmJsBrowserProductionWebpack") {
        // Production source maps are not consumed by the application, but account for
        // roughly 0.85 MiB of the packaged distribution. Keep development maps intact.
        sourceMaps = false
    }
}

packageLegalDocuments()
