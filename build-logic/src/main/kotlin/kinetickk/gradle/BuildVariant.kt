// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Action
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.Serializable

/** One source selection for the entire dependency graph, including AGP's single-variant KMP libraries. */
enum class BuildVariant(val directory: String, val sourceSuffix: String) {
    DEBUG("build-debug", "Debug"),
    RELEASE("build", "Release");

    companion object {
        fun parse(value: String): BuildVariant = when (value) {
            "debug" -> DEBUG
            "release" -> RELEASE
            else -> throw GradleException("kinetickk.variant must be debug or release, got '$value'.")
        }
    }
}

fun Project.gameBuildVariant(): BuildVariant =
    BuildVariant.parse(providers.gradleProperty("kinetickk.variant").getOrElse("release"))

/** jpackage rejects a zero macOS package major; keep a monotonic mapping separate from game versions. */
internal fun macOsPackageVersion(gameVersion: String): String {
    val parts = gameVersion.split('.')
    require(parts.size == 3 && parts.all { it.toIntOrNull()?.let { number -> number >= 0 } == true }) {
        "A macOS package requires a numeric three-part game version, got '$gameVersion'."
    }
    return "${parts.first().toInt() + 1}.${parts[1]}.${parts[2]}"
}

fun Project.configureVariantSources() {
    val suffix = gameBuildVariant().sourceSuffix
    extensions.configure(KotlinMultiplatformExtension::class.java) {
        sourceSets.configureEach {
            val variantSourceSet = when {
                name.endsWith("Main") -> name.removeSuffix("Main") + suffix
                name.endsWith("Test") -> name.removeSuffix("Test") + suffix + "Test"
                else -> null
            }
            if (variantSourceSet != null) {
                kotlin.srcDir("src/$variantSourceSet/kotlin")
                resources.srcDir("src/$variantSourceSet/resources")
            }
        }
    }
}

class RejectReleaseInDebugAction : Action<Task>, Serializable {
    override fun execute(task: Task) {
        throw GradleException("${task.path} requires -Pkinetickk.variant=release; Debug sources cannot be packaged as Release.")
    }
}
