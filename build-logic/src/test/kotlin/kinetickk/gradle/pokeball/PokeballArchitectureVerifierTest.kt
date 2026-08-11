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
                    "import kinetickk.ball.profile.api.ProfileCommand",
            ),
            productionSource(
                "flow/session/api",
                "import kinetickk.ball.content.api.UiCatalogSnapshot\n" +
                    "import kinetickk.ball.profile.api.ProfileCommand\n" +
                    "import kinetickk.ball.gameplay.api.GameplayCommand",
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
        val extraUse = productionSource(
            "ball/profile/api",
            "import kinetickk.ball.content.api.ContentVersion\n" +
                "import kinetickk.ball.gameplay.api.GameplayCommand",
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
            text = """
                package kinetickk.ball.gameplay.api

                import kinetickk.ball.profile.impl.*
                import kinetickk.ball.profile.nucleus.ProfileState

                typealias LeakedProfileState = ProfileState
            """.trimIndent(),
        )
        val violations = applicationSurfaceViolations(valid.toMutableList().apply { this[1] = invalid })

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
    }

    @Test
    fun typedRouteRowsBindToTargetProtocolsSourceCarriersAndIssuance() {
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
            "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/Unmapped.kt" to SourceDocument(
                "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/Unmapped.kt",
                "ProfileQuery.GetLoadout\nProfilePulse.PurchaseMetaUpgrade(1)",
            ),
        )

        val violations = productionProtocolUseViolations(sources)

        assertTrue(violations.any { "ProfileQuery.GetLoadout" in it })
        assertTrue(violations.any { "ProfilePulse.PurchaseMetaUpgrade" in it })
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
