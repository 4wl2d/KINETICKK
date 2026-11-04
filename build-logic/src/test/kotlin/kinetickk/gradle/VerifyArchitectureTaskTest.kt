// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

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

    @Test
    fun nucleiAndContractsCanDependOnPublicTypesAndCommonValues() {
        val dependencies = hostEdges + listOf(
            ProjectEdge(":ball:profile:nucleus", "commonMainImplementation", ":ball:profile:api"),
            ProjectEdge(":flow:session:nucleus", "commonMainImplementation", ":ball:gameplay:api"),
            ProjectEdge(":ball:gameplay:api", "commonMainApi", ":foundation:common"),
            ProjectEdge(":ball:gameplay:nucleus", "commonTestImplementation", ":ball:content:impl"),
            ProjectEdge(":flow:session:nucleus", "desktopTestImplementation", ":foundation:design"),
        )
        assertTrue(architectureDependencyViolations(dependencies).isEmpty())
    }

    @Test
    fun productionNucleiAndContractsCannotReachPrivateOwnersOrPresentationThroughAnyConfiguration() {
        val illegalTargets = listOf(
            ":ball:profile:impl", ":ball:profile:resource", ":ball:profile:interaction",
            ":ball:profile:nucleus", ":foundation:design", ":app:shared",
        )
        for (source in listOf(":ball:gameplay:nucleus", ":ball:gameplay:api")) {
            for (target in illegalTargets) {
                for (configuration in listOf("commonMainApi", "desktopMainImplementation", "wasmJsMainCompileOnly")) {
                    val edge = ProjectEdge(source, configuration, target)
                    assertTrue(
                        architectureDependencyViolations(hostEdges + edge).any {
                            "api/nucleus production dependencies" in it && source in it && target in it
                        },
                        "Forbidden production edge was allowed: $edge",
                    )
                }
            }
        }
    }
}
