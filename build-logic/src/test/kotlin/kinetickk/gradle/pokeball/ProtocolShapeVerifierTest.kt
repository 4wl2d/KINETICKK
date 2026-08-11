// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class ProtocolShapeVerifierTest {
    @Test
    fun canonicalDataClassShapeRejectsMissingReorderedAndExtraFields() {
        val path = "ball/example/api/src/commonMain/kotlin/kinetickk/ball/example/api/Protocol.kt"
        val shape = CanonicalDataClassShape(
            path = path,
            typeName = "ModuleCommandRequest",
            fields = listOf(
                CanonicalFieldShape("semanticHandle", "SemanticHandle"),
                CanonicalFieldShape("sourceOrdinal", "Int"),
                CanonicalFieldShape("targetInstance", "InstanceId"),
                CanonicalFieldShape("command", "ModuleCommand"),
            ),
        )
        val valid = source(
            path,
            """
                data class ModuleCommandRequest(
                    val semanticHandle: SemanticHandle,
                    val sourceOrdinal: Int,
                    val targetInstance: InstanceId,
                    val command: ModuleCommand,
                )
            """.trimIndent(),
        )
        assertTrue(exactDataClassShapeViolations(mapOf(path to valid), listOf(shape)).isEmpty())

        val reordered = valid.copy(
            text = valid.text.replace(
                "val sourceOrdinal: Int,\n    val targetInstance: InstanceId,",
                "val targetInstance: InstanceId,\n    val sourceOrdinal: Int,",
            ),
        )
        assertTrue(
            exactDataClassShapeViolations(mapOf(path to reordered), listOf(shape))
                .any { "fields must be exactly" in it },
        )

        val withExtra = valid.copy(
            text = valid.text.replace(
                "val command: ModuleCommand,",
                "val command: ModuleCommand,\n    val retryCount: Int,",
            ),
        )
        assertTrue(
            exactDataClassShapeViolations(mapOf(path to withExtra), listOf(shape))
                .any { "retryCount" in it },
        )
        assertTrue(
            exactDataClassShapeViolations(emptyMap(), listOf(shape))
                .any { "missing source" in it },
        )

        val wrongType = valid.copy(text = valid.text.replace("val command: ModuleCommand", "val command: Any"))
        assertTrue(
            exactDataClassShapeViolations(mapOf(path to wrongType), listOf(shape))
                .any { "command: Any" in it },
        )
    }

    @Test
    fun canonicalNestedResourceResultShapeIsScopedToItsOwner() {
        val path = "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileResourceProtocol.kt"
        val source = source(
            path,
            """
                sealed interface ProfileBootstrapResourceResult {
                    data class ResourceFailure(val reason: ProfileReadFailure) : ProfileBootstrapResourceResult
                }

                sealed interface ProfileV4WriteResult {
                    data class ResourceFailure(val reason: ProfileWriteOutcomeUnknownReason) : ProfileV4WriteResult
                }
            """.trimIndent(),
        )
        val shape = CanonicalDataClassShape(
            path = path,
            typeName = "ResourceFailure",
            fields = listOf(CanonicalFieldShape("reason", "ProfileReadFailure")),
            withinDeclaration = "sealed interface ProfileBootstrapResourceResult",
        )
        assertTrue(exactDataClassShapeViolations(mapOf(path to source), listOf(shape)).isEmpty())

        val wrongScope = shape.copy(withinDeclaration = "sealed interface ProfileV4WriteResult")
        assertTrue(
            exactDataClassShapeViolations(mapOf(path to source), listOf(wrongScope))
                .any { "ProfileWriteOutcomeUnknownReason" in it },
        )
    }

    @Test
    fun canonicalEffectiveProtocolIdentityEnumIsExactAndOrdered() {
        val path = "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileIdentity.kt"
        val inventory = CanonicalEnumInventory(
            path = path,
            typeName = "ProfileEffectiveProtocolIdentity",
            entries = listOf("SESSION_CORE_SHAPE", "SESSION_RESET_RETRY", "GAMEPLAY_PROGRESS"),
        )
        val valid = source(
            path,
            """
                enum class ProfileEffectiveProtocolIdentity {
                    SESSION_CORE_SHAPE,
                    SESSION_RESET_RETRY,
                    GAMEPLAY_PROGRESS,
                }
            """.trimIndent(),
        )
        assertTrue(exactEnumInventoryViolations(mapOf(path to valid), listOf(inventory)).isEmpty())

        val extra = valid.copy(
            text = valid.text.replace("GAMEPLAY_PROGRESS,", "SESSION_COMPATIBILITY,\n    GAMEPLAY_PROGRESS,"),
        )
        assertTrue(
            exactEnumInventoryViolations(mapOf(path to extra), listOf(inventory))
                .any { "SESSION_COMPATIBILITY" in it },
        )
    }

    @Test
    fun decisionContextRejectsCommandAdmissionAndRuntimeBudgetFields() {
        val allowed = source(
            "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/Decision.kt",
            """
                data class AppSessionContext(
                    val preferences: PreferencesProjection? = null,
                    val gameplayStatus: GameplayStatusProjection? = null,
                )
            """.trimIndent(),
        )
        assertTrue(decisionContextBoundaryViolations(listOf(allowed)).isEmpty())

        val forbidden = source(
            "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/Decision.kt",
            """
                data class GameplayContext(
                    val command: GameplayModuleCommand? = null,
                    val admission: CommandAdmission? = null,
                    val causalBudget: Int = 0,
                )
            """.trimIndent(),
        )
        val violations = decisionContextBoundaryViolations(listOf(forbidden))
        assertTrue(violations.any { "`command`" in it })
        assertTrue(violations.any { "`admission`" in it })
        assertTrue(violations.any { "`causalBudget`" in it })
    }

    @Test
    fun foreignSurfacePolicyAllowsOnlyClosedOpaqueOrCanonicalReceiverTypes() {
        val policy = ForeignApplicationSurfacePolicy(
            sourceRoot = "ball/gameplay/api/",
            ownPackage = "kinetickk.ball.gameplay.api",
            allowedForeignImports = setOf(
                "kinetickk.ball.content.api.WeaponId",
                "kinetickk.ball.profile.api.GameplayProfileSnapshot",
            ),
        )
        val path = "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/Protocol.kt"
        val valid = source(
            path,
            """
                import kinetickk.ball.content.api.WeaponId
                import kinetickk.ball.profile.api.GameplayProfileSnapshot

                data class StartRun(val profile: GameplayProfileSnapshot, val weaponId: WeaponId)
            """.trimIndent(),
        )
        assertTrue(foreignApplicationSurfaceSignatureViolations(listOf(valid), listOf(policy)).isEmpty())

        val leakedProjection = valid.copy(
            text = valid.text +
                "\nimport kinetickk.ball.profile.api.ProfileCollectionProjection\n" +
                "data class LeakedState(val projection: ProfileCollectionProjection)\n",
        )
        assertTrue(
            foreignApplicationSurfaceSignatureViolations(listOf(leakedProjection), listOf(policy))
                .any { "ProfileCollectionProjection" in it },
        )

        val sourceCompletion = valid.copy(
            text = valid.text +
                "\nimport kinetickk.ball.profile.api.ProfileModuleResultDelivery\n" +
                "data class ProfileCompleted(val delivery: ProfileModuleResultDelivery)\n",
        )
        assertTrue(
            foreignApplicationSurfaceSignatureViolations(listOf(sourceCompletion), listOf(policy))
                .any { "ProfileModuleResultDelivery" in it },
        )

        val fullyQualifiedLeak = valid.copy(
            text = valid.text +
                "\ndata class LeakedState(" +
                "val projection: kinetickk.ball.profile.api.ProfileCollectionProjection)\n",
        )
        assertTrue(
            foreignApplicationSurfaceSignatureViolations(listOf(fullyQualifiedLeak), listOf(policy))
                .any { "ProfileCollectionProjection" in it },
        )
    }

    @Test
    fun resultDeliveryStaysTargetOwnedAndCallerCompletionStaysNucleusInternal() {
        val roots = setOf("ball/profile/api/", "flow/session/api/")
        val targetDelivery = source(
            "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileProtocol.kt",
            "data class ProfileModuleResultDelivery(val result: ProfileModuleResult)",
        )
        val callerNucleusPulse = source(
            "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/ProfileResultPulse.kt",
            "data class SessionProfileModuleResultPulse(val delivery: ProfileModuleResultDelivery)",
        )
        assertTrue(
            publicSourceCompletionWrapperViolations(listOf(targetDelivery, callerNucleusPulse), roots).isEmpty(),
        )

        val publicCompletion = source(
            "flow/session/api/src/commonMain/kotlin/kinetickk/flow/session/api/SessionProtocol.kt",
            "data class SessionProfileCommandCompleted(val result: ProfileModuleResultDelivery)",
        )
        assertTrue(
            publicSourceCompletionWrapperViolations(listOf(targetDelivery, callerNucleusPulse, publicCompletion), roots)
                .any { "SessionProfileCommandCompleted" in it },
        )
    }

    @Test
    fun forbiddenProtocolSymbolsCannotSurviveAsCompatibilityAliases() {
        val path = "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/Protocol.kt"
        val sources = mapOf(path to source(path, "typealias GameplayCommand = GameplayModuleCommand"))
        val violations = forbiddenProtocolSymbolViolations(
            sources,
            mapOf(path to setOf("typealias GameplayCommand", "typealias GameplayCommandResult")),
        )
        assertTrue(violations.any { "typealias GameplayCommand" in it })
    }

    @Test
    fun causalScopeAndDepthEvidenceMustBeAnchoredAcrossTheRoute() {
        val path = "flow/session/impl/src/commonTest/kotlin/kinetickk/flow/session/impl/CommandRouteTest.kt"
        val anchor = BoundAnchor(
            path,
            listOf(
                "assertEquals(commandSource.causalScope, resultSource.causalScope)",
                "assertEquals(commandSource.causalDepth + 1, resultSource.causalDepth)",
            ),
        )
        val valid = source(path, anchor.tokens.joinToString("\n"))
        assertTrue(requiredProtocolEvidenceViolations(mapOf(path to valid), listOf(anchor)).isEmpty())

        val missingDepth = valid.copy(text = anchor.tokens.first())
        assertTrue(
            requiredProtocolEvidenceViolations(mapOf(path to missingDepth), listOf(anchor))
                .any { "causalDepth" in it },
        )
    }

    @Test
    fun localIntentInventoryRejectsSessionOwnedCompatibilityVariants() {
        val path = "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileProtocol.kt"
        val valid = source(
            path,
            """
                sealed interface ProfileIntent {
                    data class AdjustPreference(val value: Int) : ProfileIntent
                    data class PurchaseMetaUpgrade(val id: Int) : ProfileIntent
                    data class PurchaseOrEquipWeapon(val id: Int) : ProfileIntent
                }
            """.trimIndent(),
        )
        val expected = setOf("AdjustPreference", "PurchaseMetaUpgrade", "PurchaseOrEquipWeapon")
        assertTrue(
            closedDirectSubtypeInventoryViolations(
                valid,
                "sealed interface ProfileIntent",
                "ProfileIntent",
                expected,
            ).isEmpty(),
        )

        val invalid = valid.copy(
            text = valid.text.replace(
                "}",
                "    data object RetryLegacyPurge : ProfileIntent\n}",
            ),
        )
        assertTrue(
            closedDirectSubtypeInventoryViolations(
                invalid,
                "sealed interface ProfileIntent",
                "ProfileIntent",
                expected,
            ).any { "RetryLegacyPurge" in it },
        )
    }

    private fun source(path: String, text: String): SourceDocument = SourceDocument(path, text)
}
