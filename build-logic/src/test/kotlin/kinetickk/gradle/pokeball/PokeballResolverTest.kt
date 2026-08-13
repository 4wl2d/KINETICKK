// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PokeballResolverTest {
    @Test
    fun androidApplicationHostIsOneStrictMechanicalEdgeInTheTwentyThreeLeafGraph() {
        val expectedEdge = ProjectEdge(":app:android", "implementation", ":app:shared")
        val activity = SourceDocument(
            ANDROID_HOST_ACTIVITY_PATH,
            "class MainActivity : ComponentActivity() { fun render() = KinetickkApp() }",
        )

        assertEquals(23, expectedLeafProjects.size)
        assertEquals(
            listOf(":app:android", ":app:desktop", ":app:shared", ":app:web"),
            authorityModules.getValue("AppAssembly"),
        )
        assertEquals(setOf(expectedEdge), expectedAndroidHostProductionEdges)
        assertEquals(setOf(ANDROID_HOST_ACTIVITY_PATH), expectedAndroidHostProductionSources)
        assertTrue(androidApplicationHostBoundaryViolations(setOf(expectedEdge)).isEmpty())
        assertTrue(androidApplicationHostSourceViolations(listOf(activity)).isEmpty())

        val missing = androidApplicationHostBoundaryViolations(emptySet())
        assertTrue(missing.any { "missing its exact production edge" in it })

        val wrongConfiguration = expectedEdge.copy(configuration = "api")
        val wrong = androidApplicationHostBoundaryViolations(setOf(wrongConfiguration))
        assertTrue(wrong.any { "missing its exact production edge" in it })
        assertTrue(wrong.any { "unexpected production edge" in it })

        val extra = expectedEdge.copy(target = ":flow:session:impl")
        assertTrue(
            androidApplicationHostBoundaryViolations(setOf(expectedEdge, extra)).any {
                "unexpected production edge" in it
            },
        )
        assertTrue(
            androidApplicationHostBoundaryViolations(
                setOf(expectedEdge, expectedEdge.copy(configuration = "androidTestImplementation")),
            ).isEmpty(),
        )
        assertEquals(
            ":app:android",
            projectPathForSource(ANDROID_HOST_ACTIVITY_PATH),
        )

        val unexpectedSource = SourceDocument(
            "app/android/src/main/kotlin/kinetickk/app/shared/BusinessLogic.kt",
            "class BusinessLogic",
        )
        assertTrue(
            androidApplicationHostSourceViolations(listOf(activity, unexpectedSource)).any {
                "unexpected production source" in it
            },
        )
        val forbiddenImport = activity.copy(
            text = activity.text + "\nimport kinetickk.ball.profile.impl.DefaultProfileComponent",
        )
        assertTrue(
            androidApplicationHostSourceViolations(listOf(forbiddenImport)).any {
                "references forbidden authority detail" in it
            },
        )
    }

    @Test
    fun repositoryOriginsNormalizeAcrossSshAndHttps() {
        assertEquals(
            "github.com/4wl2d/pokeball",
            normalizedRepository("git@github.com:4wl2d/Pokeball.git"),
        )
        assertEquals(
            "github.com/4wl2d/pokeball",
            normalizedRepository("https://github.com/4wl2d/Pokeball.git"),
        )
        assertEquals(
            "github.com/4wl2d/pokeball",
            normalizedRepository("ssh://git@github.com/4wl2d/Pokeball.git/"),
        )
    }

    @Test
    fun graphCycleDetectionIsDeterministicAndAcyclicGraphsPass() {
        assertNull(findCycle(listOf("A" to "B", "B" to "C")))
        assertEquals(
            listOf("A", "B", "C", "A"),
            findCycle(listOf("A" to "B", "B" to "C", "C" to "A")),
        )
    }

    @Test
    fun markdownRouteInventoryIsSectionBoundedAndSorted() {
        val markdown = """
            ## Read dependencies

            | ID | Edge |
            |---|---|
            | `z-read` | Z |
            | `a-read` | A |

            ## Command/result routes

            | ID | Edge |
            |---|---|
            | `command` | C |
        """.trimIndent()

        assertEquals(sortedSetOf("a-read", "z-read"), parseTableIds(markdown, "## Read dependencies"))
        assertEquals(sortedSetOf("command"), parseTableIds(markdown, "## Command/result routes"))
    }

    @Test
    fun generatedProjectionIsStableAndContainsNoEnvironmentPath() {
        val edges = listOf(
            ProjectEdge(":flow:session:nucleus", "commonMainImplementation", ":ball:profile:api"),
            ProjectEdge(":app:desktop", "implementation", ":app:shared"),
        )
        val first = resolvedManifestJson(
            leafProjects = expectedLeafProjects.reversed(),
            edges = edges.reversed(),
            readRoutes = expectedReadRoutes.reversed(),
            commandRoutes = expectedCommandRoutes.reversed(),
        )
        val second = resolvedManifestJson(
            leafProjects = expectedLeafProjects,
            edges = edges,
            readRoutes = expectedReadRoutes,
            commandRoutes = expectedCommandRoutes,
        )

        assertEquals(first, second)
        assertTrue(first.endsWith("}\n"))
        assertTrue("non-authoritative generated projection" in first)
        assertFalse("/Users/" in first)
        assertFalse("generatedAt" in first)
    }

    @Test
    fun productionKotlinSourceScopeIncludesMainAndKmpMainOnly() {
        fun source(path: String) = SourceDocument(path, "")

        assertTrue(source("app/desktop/src/main/kotlin/fixture/Main.kt").isProductionKotlinSource())
        assertTrue(source("app/shared/src/commonMain/kotlin/fixture/Main.kt").isProductionKotlinSource())
        assertFalse(source("app/desktop/src/test/kotlin/fixture/Test.kt").isProductionKotlinSource())
        assertFalse(source("app/shared/src/commonTest/kotlin/fixture/Test.kt").isProductionKotlinSource())
        assertFalse(source("app/desktop/build/generated/src/main/kotlin/fixture/Main.kt").isProductionKotlinSource())
        assertFalse(source("app/desktop/generated/src/main/kotlin/fixture/Main.kt").isProductionKotlinSource())
        assertFalse(
            source("build-logic/src/main/kotlin/kinetickk/gradle/pokeball/Policy.kt")
                .isProductionKotlinSource(),
        )
        assertFalse(source("app/desktop/src/main/kotlin/fixture/NotKotlin.java").isProductionKotlinSource())
    }

    @Test
    fun foundationAndRegistryScanCoversConventionalAndKmpMainOnly() {
        val conventionalMain =
            "foundation/common/src/main/kotlin/kinetickk/foundation/registry/GlobalRegistry.kt"
        val kmpMain =
            "foundation/common/src/commonMain/kotlin/kinetickk/foundation/registry/ServiceLocator.kt"
        val sources = listOf(
            SourceDocument(conventionalMain, "object GlobalRegistry"),
            SourceDocument(kmpMain, "object ServiceLocator"),
            SourceDocument(
                "foundation/common/src/test/kotlin/kinetickk/foundation/registry/GlobalRegistry.kt",
                "object GlobalRegistry",
            ),
            SourceDocument(
                "foundation/common/src/commonTest/kotlin/kinetickk/foundation/registry/GlobalRegistry.kt",
                "object GlobalRegistry",
            ),
            SourceDocument(
                "foundation/common/build/generated/src/main/kotlin/kinetickk/foundation/registry/GlobalRegistry.kt",
                "object GlobalRegistry",
            ),
            SourceDocument(
                "foundation/common/generated/src/main/kotlin/kinetickk/foundation/registry/GlobalRegistry.kt",
                "object GlobalRegistry",
            ),
            SourceDocument(
                "foundation/common/src/main/kotlin/kinetickk/foundation/time/Clock.kt",
                "interface Clock",
            ),
        )

        assertEquals(
            listOf(
                "Dynamic registry/bus/queue token `GlobalRegistry` is forbidden in $conventionalMain",
                "Dynamic registry/bus/queue token `ServiceLocator` is forbidden in $kmpMain",
            ),
            foundationAndRegistryViolations(sources),
        )
    }

}
