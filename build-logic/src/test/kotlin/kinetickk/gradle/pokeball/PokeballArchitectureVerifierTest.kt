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
    fun staticCumulativeFanoutCeilingAcceptsExact9840AndRejects9841() {
        assertEquals(
            9_840,
            staticCumulativeFanoutCeiling(
                maxOutputsPerAcceptedDecision = 3,
                maxConsumersPerOutput = 1,
                acceptedCausalDepths = 0..7,
            ),
        )
        assertEquals(null, cumulativeFanoutLimitViolation(9_840))
        assertTrue(cumulativeFanoutLimitViolation(9_841)?.contains("exceeds") == true)
    }

    @Test
    fun cumulativeFanoutResolverCountsClosedBranchRules() {
        val root = "root-operation-1"
        val treeDiamondTerminalAndCoReachable = listOf(
            fanoutBranch(root, "Root", 0, "Root.ToA", "root-a", "A"),
            fanoutBranch(root, "Root", 1, "Root.ToB", "root-b", "B"),
            fanoutBranch(root, "A", 0, "A.ToTerminal", "a-terminal", "Terminal", depth = 1, terminal = true),
            fanoutBranch(root, "B", 0, "B.ToTerminal", "b-terminal", "Terminal", depth = 1, terminal = true),
            fanoutBranch(root, "A", 1, "A.ToLeaf", "a-leaf", "Leaf", depth = 1, terminal = true),
        )
        assertEquals(5, resolveCumulativeFanout(root, treeDiamondTerminalAndCoReachable))

        val duplicate = fanoutBranch(root, "Root", 0, "Root.Once", "route", "Consumer")
        assertEquals(1, resolveCumulativeFanout(root, listOf(duplicate, duplicate)))

        val mutuallyExclusive = listOf(
            fanoutBranch(root, "Root", 0, "Root.Common", "common", "Common"),
            fanoutBranch(root, "Left", 0, "Left.One", "left-1", "L1", exclusionGroup = "choice", alternative = "left"),
            fanoutBranch(root, "Left", 1, "Left.Two", "left-2", "L2", exclusionGroup = "choice", alternative = "left"),
            fanoutBranch(root, "Right", 0, "Right.One", "right-1", "R1", exclusionGroup = "choice", alternative = "right"),
            fanoutBranch(root, "Right", 1, "Right.Two", "right-2", "R2", exclusionGroup = "choice", alternative = "right"),
            fanoutBranch(root, "Right", 2, "Right.Three", "right-3", "R3", exclusionGroup = "choice", alternative = "right"),
        )
        assertEquals(4, resolveCumulativeFanout(root, mutuallyExclusive))

        val secondRoot = "root-operation-2"
        val independentRoots = listOf(
            fanoutBranch(root, "Root", 0, "Root.One", "root-1", "C1"),
            fanoutBranch(root, "Root", 1, "Root.Two", "root-2", "C2"),
            fanoutBranch(secondRoot, "Root", 0, "Root.One", "root-1", "C1"),
            fanoutBranch(secondRoot, "Root", 1, "Root.Two", "root-2", "C2"),
        )
        assertEquals(2, resolveCumulativeFanout(root, independentRoots))
        assertEquals(2, resolveCumulativeFanout(secondRoot, independentRoots))
    }

    @Test
    fun outputExecutorInventoryIsClosedAgainstAllDeclaredVariants() {
        val expected = setOf(
            "ProfileOutput.PersistSnapshot",
            "ProfileOutput.CompleteCommand",
            "GameplayOutput.EmitVisualFx",
            "GameplayOutput.SendProfileCommand",
            "GameplayOutput.AdvanceAudio",
            "GameplayOutput.EnsureAudioUnlocked",
            "GameplayOutput.CompleteCommand",
            "AppSessionOutput.EnsureGameplayRun",
            "AppSessionOutput.SendProfileCommand",
            "AppSessionOutput.SendGameplayCommand",
            "AppSessionOutput.SynchronizeAudioPreferences",
            "AppSessionOutput.PlayMuteFeedback",
            "AppSessionOutput.PlayRebirthAcceptedFeedback",
        )
        assertEquals(expected, outputExecutorInventory.map { it.outputVariant }.toSet())
        assertEquals(outputExecutorInventory.size, outputExecutorInventory.map { it.effectiveRoute }.toSet().size)
        assertTrue(outputExecutorInventory.all { it.consumerOrExecutor.isNotBlank() })
        val profileCompletionConsumers = outputExecutorInventory.filter {
            it.outputVariant == "ProfileOutput.CompleteCommand"
        }
        assertEquals(2, profileCompletionConsumers.size)
        assertEquals(setOf("AppSession Nucleus", "GameplayRun Nucleus"), profileCompletionConsumers.map { it.consumerOrExecutor }.toSet())
        assertEquals(setOf("profile-complete-consumer"), profileCompletionConsumers.map { it.mutualExclusionGroup }.toSet())

        val sources = outputClosureFixtureSources()
        assertTrue(
            compositionLimitViolations(
                sources = sources,
                policy = compositionPolicyFixture(),
                assembly = compositionAssemblyFixture(),
            ).isEmpty(),
        )
        val gameplayPath =
            "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt"
        val extraVariant = sources.toMutableMap().apply {
            this[gameplayPath] = getValue(gameplayPath).copy(
                text = getValue(gameplayPath).text + "\n    data object Undeclared : GameplayOutput",
            )
        }
        assertTrue(
            compositionLimitViolations(
                sources = extraVariant,
                policy = compositionPolicyFixture(),
                assembly = compositionAssemblyFixture(),
            ).any { "closure drift" in it },
        )
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
        assertTrue(invalid.violations.any { "unmapped source-derived edge" in it })
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
    fun assemblyCompositesAndSourceBoundViewsCannotLeakToBroaderProductionRoles() {
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
                        profileRoute = this.profileComponent
                        gameplaySessionHost = this.gameplayComponent
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
        val leakedRoute = SourceDocument(
            "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/RouteLeak.kt",
            "import kinetickk.ball.profile.api.GameplayProfileRoute\nval leaked: GameplayProfileRoute? = null",
        )
        val violations = leastAuthorityCompositionViolations(listOf(assembly, leakedComposite, leakedRoute))
        assertTrue(violations.any { "ProfileComponent" in it && "CompositeLeak.kt" in it })
        assertTrue(violations.any { "GameplayProfileRoute" in it && "RouteLeak.kt" in it })
    }

    @Test
    fun nucleusResultAndCarrierFactoriesAreCallableOnlyFromTrustedImplBoundaries() {
        val sessionDecision = SourceDocument(
            SESSION_DECISION_PATH,
            """
                fun profileModuleResultPulse() = Unit
                fun gameplayModuleResultPulse() = Unit
                fun profileCommandRejectedBeforeAcceptance() = Unit
                fun gameplayCommandRejectedBeforeAcceptance() = Unit
            """.trimIndent(),
        )
        val sessionImpl = SourceDocument(
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
            """
                profileModuleResultPulse()
                gameplayModuleResultPulse()
                profileCommandRejectedBeforeAcceptance()
                gameplayCommandRejectedBeforeAcceptance()
            """.trimIndent(),
        )
        val gameplayImpl = SourceDocument(
            "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt",
            """
                GameplayNucleusPulse.ProfileModuleResultPulse()
                GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance()
            """.trimIndent(),
        )
        val valid = listOf(sessionDecision, sessionImpl, gameplayImpl)
        assertTrue(trustedNucleusInputCallsiteViolations(valid).isEmpty())

        val unauthorized = SourceDocument(
            "app/desktop/src/main/kotlin/kinetickk/app/desktop/Untrusted.kt",
            "profileModuleResultPulse()",
        )
        assertTrue(
            trustedNucleusInputCallsiteViolations(valid + unauthorized).any {
                "profileModuleResultPulse" in it && "Untrusted.kt" in it
            },
        )
        assertTrue(
            trustedNucleusInputCallsiteViolations(valid.filterNot { it === gameplayImpl }).any {
                "ProfileModuleResultPulse" in it && "missing expected source" in it
            },
        )
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
    fun typedRouteRowsBindToTargetProtocolsSourceCarriersAndIssuance() {
        assertEquals(14, readRouteProjections.size)
        assertEquals(8, commandRouteProjections.size)
        assertEquals(expectedReadRoutes, readRouteProjections.map(ReadRouteProjection::id).toSortedSet())
        assertEquals(expectedCommandRoutes, commandRouteProjections.map(CommandRouteProjection::id).toSortedSet())
        assertTrue(commandRouteProjections.all { it.operationToken.contains("ModuleCommand.") })
        assertTrue(commandRouteProjections.all { it.outcomeTokens.all { token -> "ModuleResult." in token } })
        val assembly = typedAssembly()
        val violations = protocolRouteViolations(protocolFixtureSources(), assembly)

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun duplicateAndUntypedRouteRowsCannotHideBehindSetProjection() {
        val valid = typedAssembly()
        val duplicate = valid.replace(
            "\n\n## Command/result routes",
            "\n${readRow(readRouteProjections.first())}\n\n## Command/result routes",
        )
        val duplicateViolations = protocolRouteViolations(protocolFixtureSources(), duplicate)
        assertTrue(duplicateViolations.any { "Duplicate read route ID" in it })
        assertTrue(duplicateViolations.any { "must have exactly one typed Assembly row" in it })

        val route = commandRouteProjections.first()
        val untyped = valid.replace(route.outcomeToken, "generic accepted result")
        val untypedViolations = protocolRouteViolations(protocolFixtureSources(), untyped)
        assertTrue(
            untypedViolations.any {
                "Command route ${route.id} Assembly row is missing exact typed token `${route.outcomeToken}`" in it
            },
        )
    }

    @Test
    fun commandOutcomeFamiliesAreReverseClosedAgainstTypedAssemblyRoutes() {
        val valid = protocolFixtureSources()
        assertTrue(commandOutcomeClosureViolations(valid).isEmpty())

        val profile = commandOutcomeFamilies.single { it.targetAuthority == "Profile" }
        val profileSource = valid.getValue(profile.ownerPath)
        val withUnexpected = valid + (profile.ownerPath to profileSource.copy(
            text = profileSource.text.replace(
                "sealed interface ${profile.supertype} {",
                "sealed interface ${profile.supertype} {\n" +
                    "    object Unexpected : ${profile.supertype}",
            ),
        ))
        assertTrue(
            commandOutcomeClosureViolations(withUnexpected).any {
                "Target outcome `${profile.supertype}.Unexpected` is not mapped" in it
            },
        )

        val routed = commandRouteProjections.first { it.targetAuthority == "Profile" }.outcomeToken
        val routedName = routed.substringAfterLast('.')
        val withoutRouted = valid + (profile.ownerPath to profileSource.copy(
            text = profileSource.text.replace(
                "    data object $routedName : ${profile.supertype}\n",
                "",
            ),
        ))
        assertTrue(
            commandOutcomeClosureViolations(withoutRouted).any {
                "map undeclared target outcome `$routed`" in it
            },
        )
    }

    @Test
    fun reverseProtocolUseClosureRejectsUnmappedForeignQueriesAndCommands() {
        val sources = mapOf(
            PROFILE_QUERY_PATH to SourceDocument(
                PROFILE_QUERY_PATH,
                """
                    sealed interface ProfileQuery {
                        data object GetRunBootstrap : ProfileQuery
                        data object GetLoadout : ProfileQuery
                    }
                """.trimIndent(),
            ),
            PROFILE_PROTOCOL_PATH to SourceDocument(
                PROFILE_PROTOCOL_PATH,
                """
                    sealed interface ProfilePulse {
                        sealed interface Business : ProfilePulse
                        data class PurchaseMetaUpgrade(val id: Int) : Business
                    }
                """.trimIndent(),
            ),
            "app/desktop/src/main/kotlin/kinetickk/app/desktop/Unmapped.kt" to SourceDocument(
                "app/desktop/src/main/kotlin/kinetickk/app/desktop/Unmapped.kt",
                """
                    package kinetickk.app.desktop

                    fun unmapped() {
                        ProfileQuery.GetLoadout
                        ProfilePulse.PurchaseMetaUpgrade(1)
                    }
                """.trimIndent(),
            ),
        )

        val violations = productionProtocolUseViolations(sources)

        assertTrue(violations.any { "ProfileQuery.GetLoadout" in it })
        assertTrue(violations.any { "ProfilePulse.PurchaseMetaUpgrade" in it })
    }

    @Test
    fun gameplayProgressClosedVariantMayAppearInSessionOnlyAsExactGuardedExhaustiveness() {
        val sessionNucleusPath =
            "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleus.kt"
        val sessionImplPath =
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt"
        val profileProtocol = SourceDocument(
            PROFILE_PROTOCOL_PATH,
            """
                sealed interface ProfileModuleCommand {
                    data class ApplyGameplayProgress(val value: Int) : ProfileModuleCommand
                }
            """.trimIndent(),
        )
        val guardedSources = mapOf(
            PROFILE_PROTOCOL_PATH to profileProtocol,
            sessionNucleusPath to SourceDocument(
                sessionNucleusPath,
                """
                    is ProfileModuleCommand.ApplyGameplayProgress
                    error("Gameplay progress is not a Session mapping")
                """.trimIndent(),
            ),
            sessionImplPath to SourceDocument(
                sessionImplPath,
                """
                    is ProfileModuleCommand.ApplyGameplayProgress
                    error("Gameplay progress cannot enter Profile through Session")
                    error("Gameplay progress is not a Session command mapping")
                    is ProfileModuleCommand.ApplyGameplayProgress -> false
                """.trimIndent(),
            ),
        )

        assertTrue(productionProtocolUseViolations(guardedSources).isEmpty())

        val unguardedPath =
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/UnmappedGameplayProgress.kt"
        val withUnguardedUse = guardedSources + (
            unguardedPath to SourceDocument(
                unguardedPath,
                "ProfileModuleCommand.ApplyGameplayProgress(1)",
            )
        )
        assertTrue(
            productionProtocolUseViolations(withUnguardedUse).any { violation ->
                "ProfileModuleCommand.ApplyGameplayProgress" in violation && unguardedPath in violation
            },
        )
    }

    @Test
    fun routeAndFlowParticipationInventoriesRejectExtrasAndMissingReferences() {
        val routes = buildString {
            appendLine("sealed interface AppDestination {")
            expectedRouteInventory.forEach { appendLine("data object $it : AppDestination") }
            appendLine("data object Diagnostics : AppDestination")
            appendLine("}")
        }
        assertTrue(routeInventoryViolations(routes).any { "exactly seven" in it })

        val validAssembly = typedAssembly()
        assertTrue(flowParticipationViolations(validAssembly).isEmpty())
        val missingReference = validAssembly.replace("`session-profile-home`, ", "")
        assertTrue(
            flowParticipationViolations(missingReference).any {
                "app-session-profile" in it && "reference exactly" in it
            },
        )
        val extraParticipation = validAssembly.trimEnd() +
            "\n| `app-session-audio` | AppSession / Audio | exact | none |\n"
        assertTrue(flowParticipationViolations(extraParticipation).any { "contain exactly" in it })
    }

    @Test
    fun participantAuthorityInventoryRejectsThirdSubtypeAndFlowMismatch() {
        val closedState = SourceDocument(
            SESSION_STATE_PATH,
            """
                sealed interface PendingParticipantCommand {
                    data class Profile(
                        val request: ProfileModuleCommandRequest,
                    ) : PendingParticipantCommand

                    data class Gameplay(
                        val request: GameplayModuleCommandRequest,
                    ) : PendingParticipantCommand
                }
            """.trimIndent(),
        )
        val sources = mapOf(SESSION_STATE_PATH to closedState)

        assertTrue(participantAuthorityInventoryViolations(sources, typedAssembly()).isEmpty())

        val withThirdSubtype = sources + (
            SESSION_STATE_PATH to closedState.copy(
                text = closedState.text.replace(
                    "\n}",
                    "\n    data object Content : PendingParticipantCommand\n}",
                ),
            )
        )
        assertTrue(
            participantAuthorityInventoryViolations(withThirdSubtype, typedAssembly()).any {
                "exactly the ordered direct subtype inventory" in it
            },
        )

        val mismatchedFlow = typedAssembly().replace(
            "AppSession / GameplayRun",
            "AppSession / Content",
        )
        assertTrue(
            participantAuthorityInventoryViolations(sources, mismatchedFlow).any {
                "app-session-gameplay" in it && "closed participant authority" in it
            },
        )
    }

    @Test
    fun everyProjectedBoundRequiresAnAnchorAndNamedBoundaryEvidence() {
        val sourceText = mutableMapOf<String, StringBuilder>()
        expectedBounds.forEach { bound ->
            bound.sourceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
            bound.evidenceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
        }
        val sources = sourceText.mapValues { (path, text) -> SourceDocument(path, text.toString()) }
        val policy = requiredPolicyBoundRows.joinToString("\n")
        assertTrue(boundViolations(sources, mapOf("policy.md" to policy)).isEmpty())

        val fixedStep = expectedBounds.single { it.id == "gameplay.fixed-steps-per-render-frame" }
        val evidenceAnchor = fixedStep.evidenceAnchors.single()
        val withoutEvidence = sources.toMutableMap().apply {
            this[evidenceAnchor.path] = getValue(evidenceAnchor.path).copy(text = "")
        }
        val violations = boundViolations(withoutEvidence, mapOf("policy.md" to policy))
        assertTrue(violations.any { evidenceAnchor.tokens.single() in it })
    }

    @Test
    fun alternateGameplayCollectionInsertionGateDriftFailsBoundProjection() {
        val sourceText = mutableMapOf<String, StringBuilder>()
        expectedBounds.forEach { bound ->
            bound.sourceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
            bound.evidenceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
        }
        val sources = sourceText.mapValues { (path, text) -> SourceDocument(path, text.toString()) }
        fun assertBoundTokenDrift(
            boundId: String,
            pathSuffix: String,
            tokenFragment: String,
        ) {
            val bound = expectedBounds.single { it.id == boundId }
            val anchor = bound.sourceAnchors.single { it.path.endsWith(pathSuffix) }
            val token = anchor.tokens.single { tokenFragment in it }
            val drifted = sources.toMutableMap().apply {
                val source = getValue(anchor.path)
                this[anchor.path] = source.copy(text = source.text.replace(token, ""))
            }
            assertTrue(
                boundViolations(
                    drifted,
                    mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
                ).any { violation ->
                    "Bound ${bound.id}=${bound.value}" in violation && token in violation
                },
            )
        }

        val enemies = expectedBounds.single { it.id == "gameplay.enemies" }
        val splitterAnchor = enemies.sourceAnchors.single { it.path.endsWith("/RewardSystem.kt") }
        val splitterGate = splitterAnchor.tokens.single { "enemies.size" in it }
        val withDriftedAlternateGate = sources.toMutableMap().apply {
            val source = getValue(splitterAnchor.path)
            this[splitterAnchor.path] = source.copy(
                text = source.text.replace(splitterGate, "if (enemies.isEmpty()) return@repeat"),
            )
        }

        val violations = boundViolations(
            withDriftedAlternateGate,
            mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
        )

        assertTrue(
            violations.any { violation ->
                "Bound gameplay.enemies=120" in violation &&
                    splitterGate in violation &&
                    splitterAnchor.path in violation
            },
        )

        val rewardChoices = expectedBounds.single { it.id == "gameplay.generated-reward-choices" }
        val rewardAnchor = rewardChoices.sourceAnchors.single()
        val relicLoop = rewardAnchor.tokens.single { "relicChoiceLoop" in it }
        val withDriftedRelicGenerator = sources.toMutableMap().apply {
            val source = getValue(rewardAnchor.path)
            this[rewardAnchor.path] = source.copy(text = source.text.replace(relicLoop, "repeat(1)"))
        }
        assertTrue(
            boundViolations(
                withDriftedRelicGenerator,
                mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
            ).any { violation ->
                "Bound gameplay.generated-reward-choices=3" in violation && relicLoop in violation
            },
        )

        val projectiles = expectedBounds.single { it.id == "gameplay.projectiles" }
        val renderProjectionAnchor = projectiles.sourceAnchors.single {
            it.path.endsWith("/GameplayRenderModelMapper.kt")
        }
        val projectileProjectionCopy = renderProjectionAnchor.tokens.single {
            "projectiles = projectiles.reuseEmptyProjection" in it
        }
        val withDriftedRenderProjection = sources.toMutableMap().apply {
            val source = getValue(renderProjectionAnchor.path)
            this[renderProjectionAnchor.path] = source.copy(
                text = source.text.replace(projectileProjectionCopy, ""),
            )
        }
        assertTrue(
            boundViolations(
                withDriftedRenderProjection,
                mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
            ).any { violation ->
                "Bound gameplay.projectiles=650" in violation && projectileProjectionCopy in violation
            },
        )

        val gameplayOutputs = expectedBounds.single { it.id == "gameplay.outputs-per-decision" }
        val fixedBatchAnchor = gameplayOutputs.sourceAnchors.single {
            it.path.endsWith("/GameProtocol.kt")
        }
        val audioDeltaGuard = fixedBatchAnchor.tokens.single {
            "audioRealDeltaSeconds.toRawBits()" in it
        }
        val withDriftedFixedBatch = sources.toMutableMap().apply {
            val source = getValue(fixedBatchAnchor.path)
            this[fixedBatchAnchor.path] = source.copy(
                text = source.text.replace(audioDeltaGuard, ""),
            )
        }
        assertTrue(
            boundViolations(
                withDriftedFixedBatch,
                mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
            ).any { violation ->
                "Bound gameplay.outputs-per-decision=3" in violation &&
                    audioDeltaGuard in violation
            },
        )

        val particles = expectedBounds.single { it.id == "gameplay.interaction-particles" }
        val particleSnapshotCopy = particles.sourceAnchors.single().tokens.single {
            "particles = if (particlesDirty)" in it
        }
        val withDriftedInteractionSnapshot = sources.toMutableMap().apply {
            val source = getValue(particles.sourceAnchors.single().path)
            this[particles.sourceAnchors.single().path] = source.copy(
                text = source.text.replace(particleSnapshotCopy, ""),
            )
        }
        assertTrue(
            boundViolations(
                withDriftedInteractionSnapshot,
                mapOf("policy.md" to requiredPolicyBoundRows.joinToString("\n")),
            ).any { violation ->
                "Bound gameplay.interaction-particles=700" in violation && particleSnapshotCopy in violation
            },
        )

        assertBoundTokenDrift(
            "gameplay.visual-fx-cues",
            "/VisualFxProtocol.kt",
            "retainedCues.orEmpty().toImmutableListAppending",
        )
        assertBoundTokenDrift(
            "gameplay.sound-cues",
            "/ProgressionSystem.kt",
            "val result = soundCues.toImmutableList",
        )
        assertBoundTokenDrift("content.items", "/DefaultContentCatalog.kt", "private val items =")
        assertBoundTokenDrift("profile.retained-lab-ranks", "/ProfileNucleus.kt", "val ranks = state.profile")
        assertBoundTokenDrift(
            "profile.retained-meta-upgrade-rank",
            "/ProfileNucleus.kt",
            "if (currentRank >= definition.maxRanks)",
        )
        assertBoundTokenDrift(
            "profile.gameplay-discoveries",
            "/ProfileNucleus.kt",
            "update.discoveredItemIds.firstOrNull",
        )
        assertBoundTokenDrift("content.relic-slots", "/ProgressionSystem.kt", "updated[slot] = EquippedRelic")
        assertBoundTokenDrift("audio.desktop-synthesis-bytes", "/PlatformCapabilities.desktop.kt", "ByteArray")
    }

    @Test
    fun mechanicallyDerivedBoundsRequireEveryCopyAnchorAndExactPolicyProjection() {
        val sourceText = mutableMapOf<String, StringBuilder>()
        mechanicallyDerivedBounds.forEach { projection ->
            projection.sourceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
            projection.evidenceAnchors.forEach { anchor ->
                sourceText.getOrPut(anchor.path) { StringBuilder() }.apply {
                    anchor.tokens.forEach { appendLine(it) }
                }
            }
            projection.closedEnumInventories.forEach { inventory ->
                sourceText.getOrPut(inventory.path) { StringBuilder() }.apply {
                    appendLine(
                        "${inventory.declaration} { ${inventory.expectedEntries.joinToString()} }",
                    )
                }
            }
        }
        val sources = sourceText.mapValues { (path, text) -> SourceDocument(path, text.toString()) }
        val policy = mechanicallyDerivedBounds.joinToString(
            separator = "\n",
            transform = MechanicallyDerivedBoundProjection::policyRow,
        )
        assertTrue(mechanicallyDerivedBoundViolations(sources, mapOf("policy.md" to policy)).isEmpty())
        fun assertDerivedTokenDrift(
            projectionId: String,
            pathSuffix: String,
            tokenFragment: String,
        ) {
            val projection = mechanicallyDerivedBounds.single { it.id == projectionId }
            val anchor = projection.sourceAnchors.single { it.path.endsWith(pathSuffix) }
            val token = anchor.tokens.single { tokenFragment in it }
            val drifted = sources.toMutableMap().apply {
                val source = getValue(anchor.path)
                this[anchor.path] = source.copy(text = source.text.replace(token, ""))
            }
            assertTrue(
                mechanicallyDerivedBoundViolations(
                    drifted,
                    mapOf("policy.md" to policy),
                ).any { violation ->
                    projection.id in violation && token in violation
                },
            )
        }

        val reducerCopies = mechanicallyDerivedBounds.single { it.id == "gameplay.reducer-copy-collections" }
        val copyAnchor = reducerCopies.sourceAnchors.single {
            it.path.endsWith("/MutableGameState.kt")
        }
        val projectileCopy = copyAnchor.tokens.single {
            "source.mapTo(ArrayList(source.size), Projectile::isolatedCopy)" in it
        }
        val withDriftedCopy = sources.toMutableMap().apply {
            val source = getValue(copyAnchor.path)
            this[copyAnchor.path] = source.copy(text = source.text.replace(projectileCopy, ""))
        }
        val copyViolations = mechanicallyDerivedBoundViolations(
            withDriftedCopy,
            mapOf("policy.md" to policy),
        )
        assertTrue(
            copyViolations.any { violation ->
                "gameplay.reducer-copy-collections" in violation && projectileCopy in violation
            },
        )

        val profileCodec = mechanicallyDerivedBounds.single {
            it.id == "profile.codec-temporary-collections"
        }
        val profileCodecAnchor = profileCodec.sourceAnchors.single()
        val metaRankAllocation = profileCodecAnchor.tokens.single {
            "val metaRanks = MutableList" in it
        }
        val withDriftedProfileTemporary = sources.toMutableMap().apply {
            val source = getValue(profileCodecAnchor.path)
            this[profileCodecAnchor.path] = source.copy(
                text = source.text.replace(metaRankAllocation, ""),
            )
        }
        assertTrue(
            mechanicallyDerivedBoundViolations(
                withDriftedProfileTemporary,
                mapOf("policy.md" to policy),
            ).any { violation ->
                profileCodec.id in violation && metaRankAllocation in violation
            },
        )

        val weaponState = mechanicallyDerivedBounds.single {
            it.id == "gameplay.weapon-indexed-state"
        }
        val initialWeaponViewAnchor = weaponState.sourceAnchors.single {
            it.path.endsWith("/MutableGameState.kt")
        }
        val initialWeaponView = initialWeaponViewAnchor.tokens.single {
            "unlockedWeaponView" in it
        }
        val withDriftedWeaponView = sources.toMutableMap().apply {
            val source = getValue(initialWeaponViewAnchor.path)
            this[initialWeaponViewAnchor.path] = source.copy(
                text = source.text.replace(initialWeaponView, ""),
            )
        }
        assertTrue(
            mechanicallyDerivedBoundViolations(
                withDriftedWeaponView,
                mapOf("policy.md" to policy),
            ).any { violation ->
                weaponState.id in violation && initialWeaponView in violation
            },
        )

        assertDerivedTokenDrift(
            "gameplay.item-indexed-state",
            "/GameplayNucleus.kt",
            "itemStacks = state.engine",
        )
        assertDerivedTokenDrift(
            "gameplay.render-projection-collections",
            "/GameplayNucleus.kt",
            "projectionSourceIdentity ===",
        )
        assertDerivedTokenDrift(
            "gameplay.render-projection-collections",
            "/GameplayRenderModelMapper.kt",
            "enemies = enemies.reuseIfIdentical",
        )
        assertDerivedTokenDrift(
            "gameplay.render-projection-collections",
            "/CopyOnWriteStorage.kt",
            "current.next() != retained.next()",
        )
        assertDerivedTokenDrift(
            "gameplay.reducer-copy-collections",
            "/MutableGameState.kt",
            "soundCueStorage?.let",
        )
        assertDerivedTokenDrift(
            "gameplay.stable-compaction",
            "/EnemySystem.kt",
            "internal inline fun <Element>",
        )
        assertDerivedTokenDrift(
            "content.closed-ui-catalogs",
            "/DefaultContentCatalog.kt",
            "private val coreShapes =",
        )
        assertDerivedTokenDrift(
            "ui.catalog-backed-sources",
            "/LabState.kt",
            "upgrades = metaUpgrades.map",
        )
        assertDerivedTokenDrift(
            "foundation.immutable-set-copy",
            "/ImmutableCollections.kt",
            "ImmutableList(elements.toList())",
        )

        val missingPolicyRow = policy.replace(mechanicallyDerivedBounds.first().policyRow, "")
        assertTrue(
            mechanicallyDerivedBoundViolations(sources, mapOf("policy.md" to missingPolicyRow)).any {
                mechanicallyDerivedBounds.first().policyRow in it
            },
        )

        val closedCatalogs = mechanicallyDerivedBounds.single { it.id == "content.closed-ui-catalogs" }
        val coreShapes = closedCatalogs.closedEnumInventories.single {
            it.declaration == "enum class CoreShape"
        }
        val withExtraCoreShape = sources.toMutableMap().apply {
            val source = getValue(coreShapes.path)
            this[coreShapes.path] = source.copy(
                text = source.text.replace("ORB, PRISM, SHARD", "ORB, PRISM, SHARD, HEX"),
            )
        }
        assertTrue(
            mechanicallyDerivedBoundViolations(
                withExtraCoreShape,
                mapOf("policy.md" to policy),
            ).any { violation ->
                "content.closed-ui-catalogs" in violation && "declare exactly" in violation
            },
        )
    }

    private fun typedAssembly(): String = buildString {
        appendLine("## Read dependencies")
        appendLine()
        appendLine("| ID | Caller -> target | Target-owned query/result use | Consistency |")
        appendLine("|---|---|---|---|")
        readRouteProjections.forEach { appendLine(readRow(it)) }
        appendLine()
        appendLine("## Command/result routes")
        appendLine()
        appendLine("| Route ID | Source -> target | Target-owned operation | Source completion |")
        appendLine("|---|---|---|---|")
        commandRouteProjections.forEach { appendLine(commandRow(it)) }
        appendLine()
        appendLine("## Flow participations")
        appendLine()
        appendLine("| FlowParticipation ID | Flow / participant | Owned coordination | Dependency references |")
        appendLine("|---|---|---|---|")
        appendLine(flowParticipationRow("app-session-profile", "Profile"))
        appendLine(flowParticipationRow("app-session-gameplay", "GameplayRun"))
    }

    private fun fanoutBranch(
        rootScope: String,
        sourceAuthority: String,
        sourceOrdinal: Int,
        outputVariant: String,
        route: String,
        consumer: String,
        depth: Int = 0,
        terminal: Boolean = false,
        exclusionGroup: String? = null,
        alternative: String? = null,
    ): CumulativeFanoutBranch = CumulativeFanoutBranch(
        identity = CumulativeFanoutBranchIdentity(
            rootScope = rootScope,
            source = AcceptedOutputSourceTuple(
                authority = sourceAuthority,
                instanceId = "$sourceAuthority-instance",
                commitRevision = 1L,
                sourceOrdinal = sourceOrdinal,
                outputVariant = outputVariant,
            ),
            effectiveRoute = route,
            consumerOrExecutor = consumer,
        ),
        acceptedCausalDepth = depth,
        terminal = terminal,
        mutualExclusionGroup = exclusionGroup,
        alternative = alternative,
    )

    private fun outputClosureFixtureSources(): Map<String, SourceDocument> = mapOf(
        "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileDecision.kt" to
            SourceDocument(
                "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileDecision.kt",
                """
                    sealed interface ProfileOutput {
                        data class PersistSnapshot(val value: Int) : ProfileOutput
                        data object CompleteCommand : ProfileOutput
                    }
                """.trimIndent(),
            ),
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt" to
            SourceDocument(
                "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt",
                """
                    sealed interface GameplayOutput {
                        data object EmitVisualFx : GameplayOutput
                        data object SendProfileCommand : GameplayOutput
                        data object AdvanceAudio : GameplayOutput
                        data object EnsureAudioUnlocked : GameplayOutput
                        data object CompleteCommand : GameplayOutput
                    }
                """.trimIndent(),
            ),
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt" to
            SourceDocument(
                "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
                """
                    sealed interface AppSessionOutput {
                        data object EnsureGameplayRun : AppSessionOutput
                        data object SendProfileCommand : AppSessionOutput
                        data object SendGameplayCommand : AppSessionOutput
                        data object SynchronizeAudioPreferences : AppSessionOutput
                        data object PlayMuteFeedback : AppSessionOutput
                        data object PlayRebirthAcceptedFeedback : AppSessionOutput
                    }
                """.trimIndent(),
            ),
    ) + outputExecutorInventory
        .groupBy(OutputExecutorProjection::executorPath)
        .mapValues { (path, projections) ->
            SourceDocument(
                relativePath = path,
                text = projections.flatMap(OutputExecutorProjection::requiredTokens).joinToString("\n"),
            )
        }

    private fun compositionPolicyFixture(): String = """
        | cumulative fan-out per accepted root causal scope | 9840 |
        `maxCumulativeFanout=9840`
        accepted causal depths `0..7`
        `3^1 + 3^2 + ... + 3^8 = 9840`
        No runtime fan-out meter
        No asynchronous semantic handoff exists
    """.trimIndent()

    private fun compositionAssemblyFixture(): String = buildString {
        appendLine("one accepted root causal scope")
        appendLine("complete accepted source tuple")
        appendLine("effective route and consumer/executor")
        appendLine("Terminal branches count")
        appendLine("co-reachable branches")
        appendLine("converging")
        appendLine("Mutually exclusive alternatives")
        appendLine("duplicate traversal record for the same source tuple")
        appendLine("independent root")
        appendLine("No asynchronous semantic handoff exists")
        appendLine("## Closed semantic output executors")
        appendLine("| ID | Output variant | Effective route | Consumer/executor |")
        appendLine("|---|---|---|---|")
        outputExecutorInventory.forEach { projection ->
            appendLine(
                "| `${projection.id}` | `${projection.outputVariant}` | `${projection.effectiveRoute}` | " +
                    "`${projection.consumerOrExecutor}` |",
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

    private fun protocolFixtureSources(): Map<String, SourceDocument> {
        val textByPath = mutableMapOf<String, StringBuilder>()
        commandOutcomeFamilies.forEach { family ->
            textByPath.getOrPut(family.ownerPath) { StringBuilder() }
                .appendLine(outcomeDeclaration(family))
        }
        readRouteProjections.forEach { route ->
            textByPath.getOrPut(route.ownerPath) { StringBuilder() }.apply {
                appendLine(declarationFor(route.queryToken))
                appendLine(declarationFor(route.resultToken))
            }
            textByPath.getOrPut(route.usagePath) { StringBuilder() }.appendLine(route.queryToken)
        }
        commandRouteProjections.forEach { route ->
            textByPath.getOrPut(route.ownerPath) { StringBuilder() }.apply {
                appendLine(declarationFor(route.operationToken))
            }
            textByPath.getOrPut(route.sourcePath) { StringBuilder() }.apply {
                appendLine(declarationFor(route.acceptedCarrierToken))
                appendLine(declarationFor(route.rejectedCarrierToken))
            }
            textByPath.getOrPut(route.usagePath) { StringBuilder() }.apply {
                appendLine(route.operationToken)
                route.outcomeTokens.forEach { appendLine(it) }
            }
        }
        textByPath.getOrPut(SESSION_STATE_PATH) { StringBuilder() }.appendLine(
            """
                sealed interface PendingParticipantCommand {
                    data class Profile(
                        val request: ProfileModuleCommandRequest,
                    ) : PendingParticipantCommand

                    data class Gameplay(
                        val request: GameplayModuleCommandRequest,
                    ) : PendingParticipantCommand
                }
            """.trimIndent(),
        )
        return textByPath.mapValues { (path, text) -> SourceDocument(path, text.toString()) }
    }

    private fun outcomeDeclaration(family: CommandOutcomeFamily): String = buildString {
        appendLine("sealed interface ${family.supertype} {")
        commandRouteProjections.asSequence()
            .filter { route -> route.targetAuthority == family.targetAuthority }
            .flatMap { route -> route.outcomeTokens.asSequence() }
            .distinct()
            .forEach { token ->
                appendLine("    data object ${token.substringAfterLast('.')} : ${family.supertype}")
            }
        append("}")
    }

    private fun declarationFor(token: String): String {
        val name = token.substringAfterLast('.')
        if (token.startsWith("ContentCatalog.")) return "interface ContentCatalog\nfun $name()"
        val parent = token.substringBeforeLast('.', missingDelimiterValue = "")
        return if (parent.isEmpty()) {
            "data object $name"
        } else {
            "sealed interface $parent\ndata object $name"
        }
    }

    private fun readRow(route: ReadRouteProjection): String =
        "| `${route.id}` | ${route.sourceAuthority} -> ${route.targetAuthority} | " +
            "`${route.queryToken}` -> `${route.resultToken}` | exact |"

    private fun commandRow(route: CommandRouteProjection): String =
        "| `${route.id}` | ${route.sourceAuthority} -> ${route.targetAuthority} | " +
            "`${route.operationToken}` -> ${route.outcomeTokens.joinToString(" / ") { "`$it`" }} | " +
            "`${route.acceptedCarrierToken}` / `${route.rejectedCarrierToken}` |"

    private fun flowParticipationRow(id: String, target: String): String {
        val references = (
            readRouteProjections.filter {
                it.sourceAuthority == "AppSession" && it.targetAuthority == target
            }.map(ReadRouteProjection::id) + commandRouteProjections.filter {
                it.sourceAuthority == "AppSession" && it.targetAuthority == target
            }.map(CommandRouteProjection::id)
            ).sorted()
        return "| `$id` | AppSession / $target | exact | " +
            references.joinToString { "`$it`" } + " |"
    }

    private fun decodeAuthorityEdge(encoded: String): Pair<String, String> {
        val (source, target) = encoded.split(" -> ", limit = 2)
        return source to target
    }
}
