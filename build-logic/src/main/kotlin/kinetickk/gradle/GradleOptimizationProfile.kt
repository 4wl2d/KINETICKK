// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project

const val ISOLATED_PROJECTS_PROFILE_PROPERTY: String =
    "kinetickk.gradle.isolatedProfile"

/**
 * Omits Kotlin/Wasm targets and browser/NPM packaging from non-Web task graphs.
 *
 * Kotlin JS/Wasm does not yet support Gradle Isolated Projects (KT-80311). Android, Desktop, and
 * architecture builds can still use strict isolation when they opt into this configuration-only
 * profile; the normal profile remains the only one that registers Wasm compilations and executable
 * browser artifacts.
 */
fun Project.isolatedProjectsProfileEnabled(): Boolean =
    providers.gradleProperty(ISOLATED_PROJECTS_PROFILE_PROPERTY)
        .map(::parseIsolatedProjectsProfile)
        .orElse(false)
        .get()

internal fun parseIsolatedProjectsProfile(value: String): Boolean =
    value.toBooleanStrictOrNull()
        ?: throw GradleException(
            "Gradle property '$ISOLATED_PROJECTS_PROFILE_PROPERTY' must be exactly " +
                "'true' or 'false', but was '$value'.",
        )
