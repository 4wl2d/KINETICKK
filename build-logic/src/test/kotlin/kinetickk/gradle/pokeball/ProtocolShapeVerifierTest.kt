// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class ProtocolShapeVerifierTest {
    @Test
    fun acceptedSnapshotFramesRejectProjectionAndConstructorDrift() {
        val frameTypeNames = setOf(
            "ProfileAcceptedFrame",
            "GameplayAcceptedFrame",
            "AppSessionAcceptedFrame",
        )
        val shapes = canonicalProtocolDataClassShapes.filter { shape -> shape.typeName in frameTypeNames }
        assertTrue(
            shapes.size == frameTypeNames.size &&
                shapes.map(CanonicalDataClassShape::typeName).toSet() == frameTypeNames,
        )

        val validSources = shapes.associate { shape ->
            val text = when (shape.typeName) {
                "ProfileAcceptedFrame" -> """
                    public data class ProfileAcceptedFrame(
                        val nextState: ProfileState,
                        val outputs: ImmutableList<ProfileOutput>,
                    )
                """.trimIndent()
                "GameplayAcceptedFrame" -> """
                    public data class GameplayAcceptedFrame(
                        val nextState: GameplayState,
                        val outputs: ImmutableList<GameplayOutput>,
                    ) {
                        init {
                            val localOutputCount = outputs.size
                            require(localOutputCount >= 0)
                        }
                    }
                """.trimIndent()
                "AppSessionAcceptedFrame" -> """
                    public data class AppSessionAcceptedFrame(
                        val nextState: AppSessionState,
                        val outputs: ImmutableList<AppSessionOutput>,
                    )
                """.trimIndent()
                else -> error("Unexpected accepted frame ${shape.typeName}")
            }
            shape.path to source(shape.path, text)
        }
        assertTrue(exactDataClassShapeViolations(validSources, shapes).isEmpty())

        fun violationsFor(typeName: String, transform: (String) -> String): List<String> {
            val shape = shapes.single { candidate -> candidate.typeName == typeName }
            val original = validSources.getValue(shape.path)
            return exactDataClassShapeViolations(
                validSources + (shape.path to original.copy(text = transform(original.text))),
                shapes,
            )
        }

        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("data class GameplayAcceptedFrame", "data class `GameplayAcceptedFrame`")
            }.isEmpty(),
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("public data class GameplayAcceptedFrame", "data class GameplayAcceptedFrame")
            }.any { violation -> "must use direct same-line `public data class`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "public data class GameplayAcceptedFrame(",
                    "public data class GameplayAcceptedFrame public constructor(",
                )
            }.any { violation -> "must use direct canonical syntax" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("public data class GameplayAcceptedFrame", "private data class GameplayAcceptedFrame")
            }.any { violation -> "must use direct same-line `public data class`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("public data class GameplayAcceptedFrame", "internal data class GameplayAcceptedFrame")
            }.any { violation -> "must use direct same-line `public data class`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                "val private = 0\nval marker get() =\n    private\n\n$source"
            }.isEmpty(),
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("public data class GameplayAcceptedFrame", "private\ndata class GameplayAcceptedFrame")
            }.any { violation -> "must use direct same-line `public data class`" in violation },
        )
        listOf(
            "public\ndata class GameplayAcceptedFrame(",
            "public data\nclass GameplayAcceptedFrame(",
            "public data class\nGameplayAcceptedFrame(",
            "public data class GameplayAcceptedFrame\n(",
        ).forEach { splitDeclaration ->
            assertTrue(
                violationsFor("GameplayAcceptedFrame") { source ->
                    source.replace("public data class GameplayAcceptedFrame(", splitDeclaration)
                }.any { violation -> "must use direct same-line `public data class`" in violation },
            )
        }

        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "val nextState: GameplayState,",
                    "val nextState: GameplayState,\n    val renderSnapshot: GameplayRenderSnapshot,",
                )
            }.any { violation -> "renderSnapshot: GameplayRenderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("AppSessionAcceptedFrame") { source ->
                source.replace(
                    "val nextState: AppSessionState,",
                    "val nextState: AppSessionState,\n    val shellProjection: AppShellProjection,",
                )
            }.any { violation -> "shellProjection: AppShellProjection" in violation },
        )
        assertTrue(
            violationsFor("ProfileAcceptedFrame") { source ->
                source.replace("    val outputs: ImmutableList<ProfileOutput>,\n", "")
            }.any { violation -> "fields must be exactly" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "    val nextState: GameplayState,\n    val outputs: ImmutableList<GameplayOutput>,",
                    "    val outputs: ImmutableList<GameplayOutput>,\n    val nextState: GameplayState,",
                )
            }.any { violation -> "fields must be exactly" in violation },
        )
        assertTrue(
            violationsFor("AppSessionAcceptedFrame") { source ->
                source.replace(
                    "val outputs: ImmutableList<AppSessionOutput>,",
                    "val outputs: ImmutableList<AppSessionOutput>,\n    val acceptedRevision: Long,",
                )
            }.any { violation -> "acceptedRevision: Long" in violation },
        )
        assertTrue(
            violationsFor("ProfileAcceptedFrame") { source ->
                source.replace("val outputs: ImmutableList<ProfileOutput>", "var outputs: ImmutableList<ProfileOutput>")
            }.any { violation -> "var outputs: ImmutableList<ProfileOutput>" in violation },
        )
        assertTrue(
            violationsFor("ProfileAcceptedFrame") { source ->
                source.replace(
                    "val outputs: ImmutableList<ProfileOutput>,",
                    "val outputs: ImmutableList<ProfileOutput> = immutableListOf(),",
                )
            }.any { violation -> "val outputs: ImmutableList<ProfileOutput> = <default>" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "val outputs: ImmutableList<GameplayOutput>,",
                    "vararg val outputs: ImmutableList<GameplayOutput>,",
                )
            }.any { violation -> "vararg val outputs: ImmutableList<GameplayOutput>" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "val outputs: ImmutableList<GameplayOutput>,",
                    "val outputs: ImmutableList<GameplayOutput>,\n" +
                        "    val `renderSnapshot`: GameplayRenderSnapshot? = null,",
                )
            }.any { violation -> "renderSnapshot: GameplayRenderSnapshot?" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n) {",
                    "\n) {\n    val renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")",
                )
            }.any { violation -> "must not declare body properties" in violation && "renderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n) {",
                    "\n) {\n    val `renderSnapshot`: GameplayRenderSnapshot get() = error(\"forbidden\")",
                )
            }.any { violation ->
                "must not declare body properties" in violation && "`renderSnapshot`" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n) {",
                    "\n) {\n    fun `}`() = Unit\n" +
                        "    val renderSnapshot: GameplayRenderSnapshot? = null",
                )
            }.any { violation -> "must not declare body properties" in violation && "renderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n) {",
                    "\n) {\n    fun `\"`() = Unit\n" +
                        "    val renderSnapshot: GameplayRenderSnapshot? = null\n" +
                        "    fun `\"`(marker: Unit) = marker",
                )
            }.any { violation -> "must not declare body properties" in violation && "renderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace("\n) {", "\n) : Marker by marker({}) {")
            }.any { violation -> "must not declare supertypes, delegation, or type constraints" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n    }\n}",
                    "\n    }\n\n    operator fun component3(): GameplayRenderSnapshot = error(\"forbidden\")\n}",
                )
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun GameplayAcceptedFrame.component3(): GameplayRenderSnapshot = " +
                    "error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun Any.component3(): GameplayRenderSnapshot = error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun <T> T.component3(): GameplayRenderSnapshot = error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun <T : Any> T.component3(): GameplayRenderSnapshot = error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun <T : Any?> T.component3(): GameplayRenderSnapshot = error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source + "\nval component999999999999999999999999999999999999999999 = 0\n"
            }.any { violation -> "reserves component999999999999999999999999999999999999999999" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source + "\nval rendered = \"${'$'}{Triple(1, 2, 3).component3()}\"\n"
            }.any { violation -> "reserves component3" in violation },
        )
        val conventionalMainPath =
            "app/desktop/src/main/kotlin/kinetickk/app/desktop/ReservedComponent.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    conventionalMainPath to source(
                        conventionalMainPath,
                        "package kinetickk.app.desktop\noperator fun Any.component3(): Int = 0",
                    )
                    ),
                shapes,
            ).any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\noperator fun <U : Any, T : U> T.component3(): GameplayRenderSnapshot = " +
                    "error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                "import kotlin.Any as Universal\n" + source +
                    "\n" +
                    "operator fun Universal.component3(): GameplayRenderSnapshot = error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\ntypealias Universal = Any\n" +
                    "operator fun <T : Universal> T.component3(): GameplayRenderSnapshot = " +
                    "error(\"forbidden\")\n"
            }.any { violation -> "reserves component3" in violation },
        )
        val narrowComponentPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/NarrowComponentExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    narrowComponentPath to source(
                        narrowComponentPath,
                        "interface ComponentMarker\n" +
                            "operator fun <T : Comparable<T>> T.component3(): Int = 0\n" +
                            "operator fun <T> T.component4(): Int where T : Any, T : ComponentMarker = 0\n" +
                            "operator fun <T> T.component5(): Int " +
                            "where T : GameplayAcceptedFrame, T : ComponentMarker = 0",
                    )
                    ),
                shapes,
            ).any { violation -> "reserves component3" in violation },
        )
        assertTrue(
            violationsFor("AppSessionAcceptedFrame") { source ->
                source +
                    "\nval AppSessionAcceptedFrame.shellProjection: AppShellProjection " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val shellProjection" in violation
            },
        )
        assertTrue(
            violationsFor("AppSessionAcceptedFrame") { source ->
                source +
                    "\nval AppSessionAcceptedFrame?.`shellProjection`: AppShellProjection " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val `shellProjection`" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval GameplayAcceptedFrame ?.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval Any.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation &&
                    "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <T> T.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation &&
                    "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval `GameplayAcceptedFrame`.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval GameplayAcceptedFrame.`render=Snapshot`: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val `render=Snapshot`" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval GameplayAcceptedFrame.get: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val get" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval GameplayAcceptedFrame.get get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val get" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class by\n" +
                    "val @by GameplayAcceptedFrame.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class by\n" +
                    "val @kotlin.Suppress(\"marker\") @by GameplayAcceptedFrame.renderSnapshot: " +
                    "GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class Mark(val flag: Boolean)\n" +
                    "val @Mark(1 < 2) GameplayAcceptedFrame.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\ntypealias Frame = GameplayAcceptedFrame\n" +
                    "val Frame.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE_PARAMETER) annotation class AliasMark(val flag: Boolean)\n" +
                    "typealias Frame<@AliasMark(1 > 0) T> = GameplayAcceptedFrame\n"
            }.any { violation -> "must not expose typealias `Frame`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class Mark(val flag: Boolean)\n" +
                    "typealias Frame = @Mark(1 < 2) GameplayAcceptedFrame\n" +
                    "val Frame.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation -> "must not expose typealias `Frame`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\ntypealias `Frame` = (`GameplayAcceptedFrame`)\n" +
                    "val Frame.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation -> "must not expose typealias `Frame`" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\ntypealias Frame = (@Marker GameplayAcceptedFrame)\n" +
                    "val Frame.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation -> "must not expose typealias `Frame`" in violation },
        )
        val aliasPath = "app/shared/src/commonMain/kotlin/kinetickk/app/AcceptedFrameAlias.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    aliasPath to source(
                        aliasPath,
                        """
                            import kinetickk.flow.session.nucleus /* hidden */ . `AppSessionAcceptedFrame` as `Accepted Frame`

                            val `Accepted Frame`.shellProjection: AppShellProjection
                                get() = error("forbidden")
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val shellProjection" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <T : GameplayAcceptedFrame> T.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE_PARAMETER) annotation class Mark(val flag: Boolean)\n" +
                    "val <@Mark(1 < 2) T : GameplayAcceptedFrame> " +
                    "T.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <T : GameplayAcceptedFrame?> (T & Any).renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\ntypealias MyAny = kotlin.Any\n" +
                    "val <T : GameplayAcceptedFrame?> (T & MyAny).renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <U : GameplayAcceptedFrame, T : U> T.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <U : GameplayAcceptedFrame?, T : U&Any> " +
                    "T.renderSnapshot: GameplayRenderSnapshot get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <U, T> T.renderSnapshot: GameplayRenderSnapshot " +
                    "where U : GameplayAcceptedFrame, T : U get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <T> T.renderSnapshot: GameplayRenderSnapshot " +
                    "where T : GameplayAcceptedFrame get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <T> T.where: Int where T : GameplayAcceptedFrame " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val where" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class by\n" +
                    "val <T> T.renderSnapshot: @by Int where T : GameplayAcceptedFrame " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\n@Target(AnnotationTarget.TYPE) annotation class where\n" +
                    "val <T> T.renderSnapshot: Int where T : @where GameplayAcceptedFrame " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        val crossFileAliasPath =
            "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/FrameAlias.kt"
        val crossFileExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/CrossFileAcceptedFrameExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + mapOf(
                    crossFileAliasPath to source(
                        crossFileAliasPath,
                        """
                            typealias Frame = kinetickk.ball.gameplay.nucleus
                                .GameplayAcceptedFrame
                        """.trimIndent(),
                    ),
                    crossFileExtensionPath to source(
                        crossFileExtensionPath,
                        """
                            import kinetickk.ball.gameplay.nucleus.Frame

                            val Frame.renderSnapshot: GameplayRenderSnapshot
                                get() = error("forbidden")
                        """.trimIndent(),
                    ),
                ),
                shapes,
            ).any { violation -> "must not expose typealias `Frame`" in violation },
        )
        val wrapperExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/AcceptedFrameCollectionExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    wrapperExtensionPath to source(
                        wrapperExtensionPath,
                        """
                            private val List<GameplayAcceptedFrame>.lastAccepted: GameplayAcceptedFrame
                                get() = last()
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        val privateInlineExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/PrivateInlineExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "@Target(AnnotationTarget.PROPERTY) annotation class private\n" +
                            "private @private val String.softKeywordAnnotation: Int get() = length",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "private\ninline\nval String.multilineCachedLength: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation
            },
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "private\rval String.carriageReturnProjection: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation
            },
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "val private = 0\n" +
                            "val marker get() = private\n" +
                            "val String.answer: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val groupedReceiverAnnotations =
            "@Target(AnnotationTarget.VALUE_PARAMETER) annotation class ReceiverA\n" +
                "@Target(AnnotationTarget.VALUE_PARAMETER) annotation class ReceiverB\n"
        val groupedReceiverExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/GroupedReceiverExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    groupedReceiverExtensionPath to source(
                        groupedReceiverExtensionPath,
                        groupedReceiverAnnotations +
                            "val @receiver:[ReceiverA ReceiverB] String.projection: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "private inline val String.cachedLength: Int get() = length",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        "internal inline val String.cachedLength: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val delegatedPropertyPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/DelegatedProperty.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    delegatedPropertyPath to source(
                        delegatedPropertyPath,
                        "val answer by kotlin.lazy { 42 }",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    delegatedPropertyPath to source(
                        delegatedPropertyPath,
                        "val String.answer by kotlin.lazy { 42 }",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val annotatedGenericExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/AnnotatedGenericExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    annotatedGenericExtensionPath to source(
                        annotatedGenericExtensionPath,
                        "@Target(AnnotationTarget.TYPE) annotation class ReceiverMark\n" +
                            "val <T> @ReceiverMark T.projection: Int get() = 0",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val groupedTypeAnnotations =
            "@Target(AnnotationTarget.TYPE) annotation class TypeA\n" +
                "@Target(AnnotationTarget.TYPE) annotation class TypeB\n"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    annotatedGenericExtensionPath to source(
                        annotatedGenericExtensionPath,
                        groupedTypeAnnotations + "val @[TypeA TypeB] String.projection: Int get() = 0",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    annotatedGenericExtensionPath to source(
                        annotatedGenericExtensionPath,
                        groupedTypeAnnotations +
                            "private val @[TypeA TypeB] String.projection: Int get() = 0",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        val qualifiedGetterAnnotation =
            "object GetterMarks {\n" +
                "    @Target(AnnotationTarget.PROPERTY_GETTER) annotation class A\n" +
                "}\n"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        qualifiedGetterAnnotation + "val answer @GetterMarks.A get() = 42",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        qualifiedGetterAnnotation +
                            "val String.answer @GetterMarks.A get() = length",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val groupedAnnotationExtension =
            "@Target(AnnotationTarget.PROPERTY) annotation class A\n" +
                "@Target(AnnotationTarget.PROPERTY) annotation class B\n"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        groupedAnnotationExtension +
                            "private @property:[A B] inline val String.cachedLength: Int get() = length",
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    privateInlineExtensionPath to source(
                        privateInlineExtensionPath,
                        groupedAnnotationExtension +
                            "internal @property:[A B] inline val String.cachedLength: Int get() = length",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val shadowedReceiverPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/ShadowedAcceptedFrameReceiver.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    shadowedReceiverPath to source(
                        shadowedReceiverPath,
                        """
                            private val <GameplayAcceptedFrame> GameplayAcceptedFrame.allowed: Int
                                get() = 0
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source +
                    "\nval <GameplayAcceptedFrame : " +
                    "kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame> " +
                    "GameplayAcceptedFrame.renderSnapshot: GameplayRenderSnapshot " +
                    "get() = error(\"forbidden\")\n"
            }.any { violation ->
                "requires a direct same-line private modifier for production extension property" in violation && "val renderSnapshot" in violation
            },
        )
        val functionExtensionPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/AcceptedFrameFunctionExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    functionExtensionPath to source(
                        functionExtensionPath,
                        """
                            private val (() -> GameplayAcceptedFrame).latest: GameplayAcceptedFrame
                                get() = invoke()

                            private val (() -> GameplayAcceptedFrame)?.nullableLatest: GameplayAcceptedFrame?
                                get() = this?.invoke()

                            typealias Supplier = @Marker (() -> GameplayAcceptedFrame)

                            typealias SplitSupplier = (GameplayAcceptedFrame)
                                -> Unit

                            typealias SplitSuspendSupplier = suspend (GameplayAcceptedFrame)
                                -> Unit
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    functionExtensionPath to source(
                        functionExtensionPath,
                        "val (() -> Int).answer: Int get() = invoke()",
                    )
                    ),
                shapes,
            ).any { violation -> "requires a direct same-line private modifier for production extension property" in violation },
        )
        val annotationAliasPath =
            "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/AnnotatedFrameAlias.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    annotationAliasPath to source(
                        annotationAliasPath,
                        """
                            typealias AnnotatedFrame = @Marker
                                GameplayAcceptedFrame
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).any { violation -> "must not expose typealias `AnnotatedFrame`" in violation },
        )
        val escapedWherePath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/EscapedWhereExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    escapedWherePath to source(
                        escapedWherePath,
                        """
                            private val <T> T.`where T`: GameplayAcceptedFrame
                                get() = error("allowed")
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        val qualifiedWhereAnnotationPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/QualifiedWhereAnnotationExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    qualifiedWhereAnnotationPath to source(
                        qualifiedWhereAnnotationPath,
                        """
                            object Marks {
                                @Target(AnnotationTarget.TYPE)
                                annotation class where
                            }

                            private val <T> @Marks.where List<T>.allowed: GameplayAcceptedFrame
                                get() = error("allowed")
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        val useSiteWhereAnnotationPath =
            "app/shared/src/commonMain/kotlin/kinetickk/app/UseSiteWhereAnnotationExtension.kt"
        assertTrue(
            exactDataClassShapeViolations(
                validSources + (
                    useSiteWhereAnnotationPath to source(
                        useSiteWhereAnnotationPath,
                        """
                            @Target(AnnotationTarget.VALUE_PARAMETER)
                            annotation class where

                            private val <T> @receiver:where List<T>.allowed: GameplayAcceptedFrame
                                get() = error("allowed")
                        """.trimIndent(),
                    )
                    ),
                shapes,
            ).isEmpty(),
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                val drifted = source.replace(
                    "val nextState: GameplayState,",
                    "val nextState: GameplayState,\n    val renderSnapshot: GameplayRenderSnapshot,",
                )
                "/*\n$source\n*/\n$drifted"
            }.any { violation -> "renderSnapshot: GameplayRenderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                val drifted = source.replace(
                    "val nextState: GameplayState,",
                    "val nextState: GameplayState,\n    val renderSnapshot: GameplayRenderSnapshot,",
                )
                "object NestedDecoy {\n$source\n}\n$drifted"
            }.any { violation -> "renderSnapshot: GameplayRenderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                val drifted = source.replace(
                    "val nextState: GameplayState,",
                    "val nextState: GameplayState,\n    val renderSnapshot: GameplayRenderSnapshot,",
                )
                "object NestedDecoy {\n    fun `}`() = Unit\n$source\n}\n$drifted"
            }.any { violation -> "renderSnapshot: GameplayRenderSnapshot" in violation },
        )
        assertTrue(
            violationsFor("AppSessionAcceptedFrame") { source ->
                val drifted = source.replace(
                    "val nextState: AppSessionState,",
                    "val nextState: AppSessionState,\n    val shellProjection: AppShellProjection,",
                )
                "val decoy = \"\"\"\n$source\n\"\"\"\n$drifted"
            }.any { violation -> "shellProjection: AppShellProjection" in violation },
        )
        assertTrue(
            violationsFor("ProfileAcceptedFrame") { source ->
                source +
                    """

                        private fun helper() {
                            val local = 0
                            require(local == 0)
                        }
                    """.trimIndent()
            }.isEmpty(),
        )
        assertTrue(
            violationsFor("GameplayAcceptedFrame") { source ->
                source.replace(
                    "\n    }\n}",
                    "\n    }\n\n    fun `val helper`() = outputs.size\n}",
                )
            }.isEmpty(),
        )
    }

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

        val escapedDeclarationDecoy = invalid.copy(
            text = """
                fun `sealed interface ProfileIntent`() {
                    data class AdjustPreference(val value: Int) : ProfileIntent
                    data class PurchaseMetaUpgrade(val id: Int) : ProfileIntent
                    data class PurchaseOrEquipWeapon(val id: Int) : ProfileIntent
                }

                ${invalid.text}
            """.trimIndent(),
        )
        assertTrue(
            closedDirectSubtypeInventoryViolations(
                escapedDeclarationDecoy,
                "sealed interface ProfileIntent",
                "ProfileIntent",
                expected,
            ).any { "RetryLegacyPurge" in it },
        )
    }

    private fun source(path: String, text: String): SourceDocument = SourceDocument(path, text)
}
