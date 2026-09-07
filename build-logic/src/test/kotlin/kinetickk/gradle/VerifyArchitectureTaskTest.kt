// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kinetickk.gradle.pokeball.ProjectEdge
import kotlin.test.Test
import kotlin.test.assertTrue

class VerifyArchitectureTaskTest {
    private val hostEdges = listOf("android", "desktop", "web").map { platform ->
        ProjectEdge(":app:$platform", "implementation", ":app:shared")
    }

    @Test
    fun hostTestsCanUseAnotherPublicModuleWithoutChangingTheProductionBoundary() {
        val dependencies = hostEdges + listOf(
            ProjectEdge(":app:desktop", "testImplementation", ":foundation:common"),
            ProjectEdge(":app:android", "androidTestImplementation", ":ball:profile:api"),
        )
        assertTrue(architectureDependencyViolations(dependencies).isEmpty())
    }

    @Test
    fun hostProductionDependenciesStillRequireTheSharedApplicationAsTheirOnlyTarget() {
        val extra = ProjectEdge(":app:web", "implementation", ":foundation:common")
        assertTrue(
            architectureDependencyViolations(hostEdges + extra).any {
                ":app:web must have exactly one production project dependency target" in it
            },
        )
        assertTrue(
            architectureDependencyViolations(hostEdges.filterNot { it.source == ":app:web" }).any {
                ":app:web must have exactly one production project dependency target" in it
            },
        )
    }

    @Test
    fun allowingPublicTestDependenciesDoesNotPermitUnknownModulesOrImplToImplEdges() {
        val illegal = listOf(
            ProjectEdge(":app:web", "testImplementation", ":ball:profile:undeclared"),
            ProjectEdge(":ball:profile:impl", "commonTestImplementation", ":ball:gameplay:impl"),
        )
        val violations = architectureDependencyViolations(hostEdges + illegal)
        assertTrue(violations.any { "outside the declared" in it && ":ball:profile:undeclared" in it })
        assertTrue(violations.any { "impl -> impl dependency is forbidden" in it })
    }
}
