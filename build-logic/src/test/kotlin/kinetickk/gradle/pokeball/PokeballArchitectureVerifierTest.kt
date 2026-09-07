// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PokeballArchitectureVerifierTest {
    @Test
    fun sharedIconGeometryAdmitsMechanicalDrawingAndRejectsDomainMappings() {
        val geometry = SourceDocument(
            "foundation/design/src/commonMain/kotlin/kinetickk/foundation/design/CanvasRunes.kt",
            "enum class CanvasRuneStyle { RING, CROSS }\nfun drawRuneMedallion() = Unit",
        )
        assertTrue(foundationAndRegistryViolations(listOf(geometry)).isEmpty())
        listOf("ItemEffect", "ItemRarity", "RelicId").forEach { domainType ->
            val leaked = geometry.copy(text = geometry.text + "\nfun map(value: $domainType) = Unit")
            assertTrue(foundationAndRegistryViolations(listOf(leaked)).any { domainType in it })
        }
    }

    @Test
    fun modelInventoriesRejectDuplicateKeysBeforeMapOrSetProjection() {
        assertFailsWith<IllegalArgumentException> {
            uniqueLinkedMap(
                "duplicate fixture",
                listOf("ContentCatalog" to 1, "ContentCatalog" to 2),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            requireUniqueKeys("duplicate route fixture", listOf("Codex", "Codex")) { it }
        }
    }

    @Test
    fun semanticDirectControlGraphIsDerivedFromForeignSurfaceDependenciesAndUses() {
        val edges = setOf(
            ProjectEdge(":ball:profile:api", "commonMainApi", ":ball:content:api"),
            ProjectEdge(":ball:gameplay:api", "commonMainApi", ":ball:content:api"),
            ProjectEdge(":ball:gameplay:api", "commonMainApi", ":ball:profile:api"),
            ProjectEdge(":flow:session:api", "commonMainApi", ":ball:content:api"),
            ProjectEdge(":flow:session:api", "commonMainApi", ":ball:profile:api"),
            ProjectEdge(":flow:session:api", "commonMainApi", ":ball:gameplay:api"),
        )
        val sources = listOf(
            productionSource("ball/profile/api", "import kinetickk.ball.content.api.ContentVersion"),
            productionSource(
                "ball/gameplay/api",
                "import kinetickk.ball.content.api.GameplayContentSnapshot\n" +
                    "import kinetickk.ball.profile.api.ProfileModuleCommand",
            ),
            productionSource(
                "flow/session/api",
                "import kinetickk.ball.content.api.UiCatalogSnapshot\n" +
                    "import kinetickk.ball.profile.api.ProfileModuleCommand\n" +
                    "import kinetickk.ball.gameplay.api.GameplayModuleCommand",
            ),
        )
        val resolved = resolvedSemanticDirectControl(edges, sources)

        assertTrue(resolved.violations.isEmpty(), resolved.violations.joinToString("\n"))
        assertEquals(
            sortedSetOf(
                "AppSession -> ContentCatalog",
                "AppSession -> GameplayRun",
                "AppSession -> Profile",
                "GameplayRun -> ContentCatalog",
                "GameplayRun -> Profile",
                "Profile -> ContentCatalog",
            ),
            resolved.edges,
        )
        assertFalse(resolved.edges.any { "Foundation" in it })
        assertFalse(resolved.edges.any { "AudioResource" in it })
        assertFalse(resolved.edges.any { "AppAssembly" in it })
        assertEquals(null, findCycle(resolved.edges.map(::decodeAuthorityEdge)))

        val extraEdge = ProjectEdge(":ball:profile:api", "commonMainApi", ":ball:gameplay:api")
        val extraUse = SourceDocument(
            "ball/profile/api/src/main/kotlin/kinetickk/fixture/Fixture.kt",
            "package kinetickk.fixture\n\n" +
                "import kinetickk.ball.content.api.ContentVersion\n" +
                "import kinetickk.ball.gameplay.api.GameplayModuleCommand\n\n" +
                "sealed interface Fixture",
        )
        val invalid = resolvedSemanticDirectControl(
            edges + extraEdge,
            sources.filterNot { it.relativePath.startsWith("ball/profile/api/") } + extraUse,
        )
        assertTrue(invalid.violations.isEmpty(), invalid.violations.joinToString("\n"))
        assertTrue(findCycle(invalid.edges.map(::decodeAuthorityEdge)) != null)
    }

    @Test
    fun applicationSurfaceScanUsesRepositoryRelativePathsAndFailsClosed() {
        val valid = listOf(
            applicationSurface("ball/content/api", "kinetickk.ball.content.api"),
            applicationSurface("ball/profile/api", "kinetickk.ball.profile.api"),
            applicationSurface("ball/gameplay/api", "kinetickk.ball.gameplay.api"),
            applicationSurface("flow/session/api", "kinetickk.flow.session.api"),
            applicationSurface("resource/audio/api", "kinetickk.resource.audio.api"),
        )
        assertTrue(applicationSurfaceViolations(valid).isEmpty())

        val invalid = valid[1].copy(
            relativePath =
                "ball/profile/api/src/main/kotlin/kinetickk/ball/profile/api/ConventionalLeak.kt",
            text = """
                package kinetickk.ball.gameplay.api

                import kinetickk.ball.profile.impl.*
                import kinetickk.ball.profile.nucleus.ProfileState

                typealias LeakedProfileState = ProfileState
            """.trimIndent(),
        )
        val violations = applicationSurfaceViolations(valid + invalid)

        assertTrue(violations.any { "exact package kinetickk.ball.profile.api" in it })
        assertTrue(violations.any { "forbidden dependency token `.impl.`" in it })
        assertTrue(violations.any { "wildcard imports" in it })
        assertTrue(violations.any { "re-export a typealias" in it })
        assertTrue(violations.any { "foreign sovereign State `ProfileState`" in it })
        assertTrue(
            applicationSurfaceViolations(valid.dropLast(1)).any {
                "Missing Application Surface Kotlin source for kinetickk.resource.audio.api" in it
            },
        )
    }

    @Test
    fun assemblyCompositesAndLocalMutationPortsCannotLeakToBroaderProductionRoles() {
        val assembly = SourceDocument(
            "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt",
            """
                import kinetickk.ball.profile.impl.ProfileComponent
                import kinetickk.ball.gameplay.impl.GameplayCompositionComponent

                class AppCompositionOwner(
                    profileComponent: ProfileComponent? = null,
                    gameplayComponent: GameplayCompositionComponent? = null,
                ) {
                    private val profileComponent: ProfileComponent = profileComponent!!
                    private val gameplayComponent: GameplayCompositionComponent = gameplayComponent!!

                    fun bind() {
                        profileSettings = this.profileComponent
                        gameplayRunHost = this.gameplayComponent
                        gameplayPresentation = gameplayComponent
                    }
                }
            """.trimIndent(),
        )
        assertTrue(leastAuthorityCompositionViolations(listOf(assembly)).isEmpty())

        val leakedComposite = SourceDocument(
            "app/desktop/src/main/kotlin/kinetickk/app/desktop/CompositeLeak.kt",
            "import kinetickk.ball.profile.impl.ProfileComponent\nval leaked: ProfileComponent? = null",
        )
        val leakedLocalMutation = SourceDocument(
            "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/MutationLeak.kt",
            "import kinetickk.ball.profile.api.ProfilePort\nval leaked: ProfilePort? = null",
        )
        val violations = leastAuthorityCompositionViolations(listOf(assembly, leakedComposite, leakedLocalMutation))
        assertTrue(violations.any { "ProfileComponent" in it && "CompositeLeak.kt" in it })
        assertTrue(violations.any { "ProfilePort" in it && "MutationLeak.kt" in it })
    }

    @Test
    fun targetCapabilitiesNeedNoConsumerOrFileNameRegistry() {
        val additionalConsumer = SourceDocument(
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/AdditionalConsumer.kt",
            """
                import kinetickk.ball.profile.api.ProfileReadPort
                import kinetickk.ball.profile.api.ProfileSettings
                import kinetickk.ball.gameplay.api.GameplayPresentationPort
                class AdditionalConsumer(
                    private val profile: ProfileReadPort,
                    private val settings: ProfileSettings,
                    private val presentation: GameplayPresentationPort,
                )
            """.trimIndent(),
        )
        val renamedOwnerFile = SourceDocument(
            "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/RenamedOwner.kt",
            "interface RenamedOwner : ProfileComponent, ProfilePort",
        )
        assertTrue(leastAuthorityCompositionViolations(listOf(additionalConsumer, renamedOwnerFile)).isEmpty())
    }

    @Test
    fun foreignAuthorityInternalsRequireExactHostOrTestEdges() {
        val hostEdge = ProjectEdge(
            ":app:shared",
            "commonMainImplementation",
            ":ball:profile:impl",
        )
        val hostSource = productionSource(
            "app/shared",
            "import kinetickk.ball.profile.impl.DefaultProfileComponent",
        )
        assertTrue(foreignInternalAccessViolations(setOf(hostEdge), listOf(hostSource)).isEmpty())

        val forbiddenEdge = ProjectEdge(
            ":flow:session:impl",
            "commonMainImplementation",
            ":ball:profile:nucleus",
        )
        val forbiddenSource = productionSource(
            "flow/session/impl",
            "import kinetickk.ball.profile.nucleus.ProfileNucleus",
        )
        val forbidden = foreignInternalAccessViolations(
            setOf(forbiddenEdge),
            listOf(forbiddenSource),
        )
        assertTrue(forbidden.any { "Foreign internal dependency" in it })
        assertTrue(forbidden.any { "Foreign internal access" in it })

        val wrongConfiguration = hostEdge.copy(configuration = "commonMainApi")
        assertTrue(
            foreignInternalAccessViolations(setOf(wrongConfiguration), listOf(hostSource)).any {
                "is not an exact declared host/test edge" in it
            },
        )

        val testEdge = ProjectEdge(
            ":ball:gameplay:nucleus",
            "commonTestImplementation",
            ":ball:content:impl",
        )
        val testImport = SourceDocument(
            "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/fixture/Fixture.kt",
            "import kinetickk.ball.content.impl.DefaultContentCatalog",
        )
        assertTrue(foreignInternalAccessViolations(setOf(testEdge), listOf(testImport)).isEmpty())
        val productionImport = testImport.copy(
            relativePath = testImport.relativePath.replace("commonTest", "commonMain"),
        )
        assertTrue(
            foreignInternalAccessViolations(setOf(testEdge), listOf(productionImport)).any {
                "Foreign internal access" in it
            },
        )

        val appSharedInteractionTestEdge = ProjectEdge(
            ":app:shared",
            "commonTestImplementation",
            ":ball:gameplay:interaction",
        )
        val appSharedInteractionTestImport = SourceDocument(
            "app/shared/src/commonTest/kotlin/kinetickk/app/shared/GameplayFixture.kt",
            "import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput",
        )
        assertTrue(
            foreignInternalAccessViolations(
                setOf(appSharedInteractionTestEdge),
                listOf(appSharedInteractionTestImport),
            ).isEmpty(),
        )
        assertTrue(
            foreignInternalAccessViolations(
                setOf(appSharedInteractionTestEdge.copy(configuration = "commonMainImplementation")),
                listOf(appSharedInteractionTestImport),
            ).any { "is not an exact declared host/test edge" in it },
        )
    }

    @Test
    fun renderSnapshotTestDependencyDoesNotPermitProductionOrOtherConsumers() {
        val testEdge = ProjectEdge(":app:shared", "commonTestImplementation", ":ball:gameplay:nucleus")
        val testSource = SourceDocument(
            "app/shared/src/commonTest/kotlin/kinetickk/app/shared/RenderSnapshotFixture.kt",
            "import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot",
        )
        assertTrue(foreignInternalAccessViolations(setOf(testEdge), listOf(testSource)).isEmpty())

        val productionSource = testSource.copy(
            relativePath = testSource.relativePath.replace("commonTest", "commonMain"),
        )
        assertTrue(
            foreignInternalAccessViolations(setOf(testEdge), listOf(productionSource)).any {
                "Foreign internal access" in it
            },
        )
        val productionEdge = testEdge.copy(configuration = "commonMainImplementation")
        assertTrue(
            foreignInternalAccessViolations(setOf(productionEdge), listOf(productionSource)).any {
                "is not an exact declared host/test edge" in it
            },
        )
        val otherConsumer = testEdge.copy(source = ":app:desktop")
        assertTrue(
            foreignInternalAccessViolations(setOf(otherConsumer), emptyList()).any {
                "is not an exact declared host/test edge" in it
            },
        )
    }

    @Test
    fun nestedTestPackageCannotGrantProductionCodeATestDependency() {
        val testEdge = ProjectEdge(":app:shared", "commonTestImplementation", ":ball:gameplay:nucleus")
        fun source(sourceSet: String) = SourceDocument(
            "app/shared/src/$sourceSet/kotlin/kinetickk/app/shared/probeTest/Probe.kt",
            "package kinetickk.app.shared.probeTest\n" +
                "import kinetickk.ball.gameplay.nucleus.GameplayState",
        )
        listOf("main", "commonMain", "desktopMain").forEach { sourceSet ->
            assertTrue(
                foreignInternalAccessViolations(setOf(testEdge), listOf(source(sourceSet))).any {
                    "Foreign internal access" in it
                },
                "Nested package must not turn $sourceSet into test code",
            )
        }
        listOf("test", "commonTest", "desktopTest", "androidDeviceTest").forEach { sourceSet ->
            assertTrue(
                foreignInternalAccessViolations(setOf(testEdge), listOf(source(sourceSet))).isEmpty(),
                "Actual $sourceSet source set must retain its test dependency",
            )
        }
    }

    @Test
    fun anotherTypedOperationNeedsNoRouteInventoryOrSourceSpellingExpectation() {
        val edge = ProjectEdge(":flow:session:api", "commonMainApi", ":ball:profile:api")
        val original = productionSource(
            "flow/session/api", "import kinetickk.ball.profile.api.PlayerPreferences",
        ).copy(text = "package kinetickk.fixture\nimport kinetickk.ball.profile.api.PlayerPreferences\n" +
            "fun preferenceForDisplay(value: PlayerPreferences) = value")
        val extracted = original.copy(text = original.text +
            "\nfun additionalRead(current: PlayerPreferences) = pureHelper(current)\n" +
            "private fun pureHelper(renamed: PlayerPreferences) = renamed")
        val before = resolvedSemanticDirectControl(setOf(edge), listOf(original))
        val after = resolvedSemanticDirectControl(setOf(edge), listOf(extracted))
        assertTrue(before.violations.isEmpty())
        assertEquals(before, after)
        assertEquals(
            resolvedManifestJson(expectedLeafProjects, setOf(edge), listOf(original)),
            resolvedManifestJson(expectedLeafProjects, setOf(edge), listOf(extracted)),
        )
    }

    @Test
    fun aNewAcyclicSurfaceDependencyIsResolvedWithoutADeclaredRouteCount() {
        val profileEdge = ProjectEdge(":ball:profile:api", "commonMainApi", ":ball:content:api")
        val profile = productionSource("ball/profile/api", "import kinetickk.ball.content.api.CoreShape")
        val addedEdge = ProjectEdge(":flow:session:api", "commonMainApi", ":ball:profile:api")
        val addedSource = productionSource("flow/session/api", "import kinetickk.ball.profile.api.PlayerPreferences")
        val resolved = resolvedSemanticDirectControl(setOf(profileEdge, addedEdge), listOf(profile, addedSource))
        assertTrue(resolved.violations.isEmpty(), resolved.violations.joinToString("\n"))
        assertEquals(setOf("Profile -> ContentCatalog", "AppSession -> Profile"), resolved.edges)
        assertEquals(null, findCycle(resolved.edges.map(::decodeAuthorityEdge)))
        val missingDependency = resolvedSemanticDirectControl(setOf(profileEdge), listOf(profile, addedSource))
        assertTrue(missingDependency.violations.any { "lacks a production dependency path" in it })
    }

    @Test
    fun queryOnlyAuthorityImportsStillParticipateInTheDirectControlCycleCheck() {
        val edges = setOf(
            ProjectEdge(":ball:profile:api", "commonMainApi", ":ball:content:api"),
            ProjectEdge(":ball:content:impl", "commonMainImplementation", ":ball:profile:api"),
        )
        val sources = listOf(
            productionSource("ball/profile/api", "import kinetickk.ball.content.api.CoreShape"),
            productionSource("ball/content/impl", "import kinetickk.ball.profile.api.PlayerPreferences"),
        )
        assertEquals(null, findCycle(edges.map { it.source to it.target }))
        val direct = resolvedSemanticDirectControl(edges, sources)
        assertTrue(direct.violations.isEmpty())
        assertTrue("ContentCatalog -> Profile" in direct.edges)
        assertTrue(findCycle(direct.edges.map(::decodeAuthorityEdge)) != null)
    }

    @Test
    fun commentsAndQuotedNamesCannotForgeProductionDependenciesOrForbiddenMechanisms() {
        val source = productionSource("flow/session/api", "").copy(text =
            "package kinetickk.fixture\n// import kinetickk.ball.profile.api.PlayerPreferences\n" +
                "val note = \"kinetickk.ball.profile.api.PlayerPreferences\"")
        val edge = ProjectEdge(":flow:session:api", "commonMainApi", ":ball:profile:api")
        val resolved = resolvedSemanticDirectControl(setOf(edge), listOf(source))
        assertTrue(resolved.edges.isEmpty())
        assertTrue(resolved.violations.any { "has no production source use" in it })
        val mechanical = SourceDocument(
            "foundation/common/src/commonMain/kotlin/kinetickk/foundation/Note.kt",
            "package kinetickk.foundation\nval note = \"GlobalRegistry RelicId\"\n// EventBus",
        )
        assertTrue(foundationAndRegistryViolations(listOf(mechanical)).isEmpty())
    }

    @Test
    fun forbiddenNamespacesRemainVisibleInsideExecutableStringTemplates() {
        val nucleus = SourceDocument(
            "ball/gameplay/nucleus/src/desktopMain/kotlin/kinetickk/ball/gameplay/nucleus/Probe.kt",
            "package kinetickk.ball.gameplay.nucleus\n" +
                "fun probe() = \"${'$'}{java.io.File(\".\").absolutePath}\"",
        )
        assertTrue(
            resolveArchitectureViolations(expectedLeafProjects, emptySet(), listOf(nucleus), emptyMap()).any {
                "Nucleus source ${nucleus.relativePath} contains forbidden dependency token `java.io`" == it
            },
        )

        val internalReference = SourceDocument(
            "app/shared/src/commonMain/kotlin/kinetickk/app/shared/Probe.kt",
            "package kinetickk.app.shared\n" +
                "fun probe() = \"${'$'}{kinetickk.ball.gameplay.interaction.GameplayInteractionPort::class}\"",
        )
        assertTrue(
            foreignInternalAccessViolations(emptySet(), listOf(internalReference)).any {
                "Foreign internal access" in it && ":ball:gameplay:interaction" in it
            },
        )
    }

    @Test
    fun forbiddenNamespaceGuardConservativelyIncludesLiteralAndCommentMentions() {
        val nucleusPath = "ball/gameplay/nucleus/src/desktopMain/kotlin/kinetickk/ball/gameplay/nucleus/Probe.kt"
        listOf("val note = \"java.io.File\"", "// java.io.File").forEach { mention ->
            val source = SourceDocument(nucleusPath, "package kinetickk.ball.gameplay.nucleus\n$mention")
            assertTrue(
                resolveArchitectureViolations(expectedLeafProjects, emptySet(), listOf(source), emptyMap()).any {
                    "contains forbidden dependency token `java.io`" in it
                },
            )
        }
    }

    private fun applicationSurface(root: String, packageName: String): SourceDocument = SourceDocument(
        relativePath = "$root/src/commonMain/kotlin/${packageName.replace('.', '/')}/Protocol.kt",
        text = "package $packageName\n\nsealed interface Protocol",
    )

    private fun productionSource(root: String, imports: String): SourceDocument = SourceDocument(
        relativePath = "$root/src/commonMain/kotlin/kinetickk/fixture/Fixture.kt",
        text = "package kinetickk.fixture\n\n$imports\n\nsealed interface Fixture",
    )

    private fun decodeAuthorityEdge(encoded: String): Pair<String, String> {
        val (source, target) = encoded.split(" -> ", limit = 2)
        return source to target
    }
}
