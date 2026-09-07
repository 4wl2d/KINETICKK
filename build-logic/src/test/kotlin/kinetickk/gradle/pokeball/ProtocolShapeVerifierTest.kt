// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class ProtocolShapeVerifierTest {
    @Test
    fun nucleusAllowsSemanticCapacityButRejectsDispatchMechanismDependencies() {
        val path = "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/Decision.kt"
        val allowed = SourceDocument(path, """
            package kinetickk.ball.gameplay.nucleus

            data class GameplayContext(val rewardCapacity: Int, val commandName: String)
        """.trimIndent())
        assertTrue(buildList { addPackageAndImportViolations(listOf(allowed)) }.isEmpty())

        val forbiddenUses = listOf(
            "import kinetickk.foundation.dispatch.InlineReply",
            "val reply: kinetickk.foundation.dispatch.InlineReply<Result, Refusal>? = null",
            "val observed = \"${'$'}{kinetickk.foundation.dispatch.InlineAcceptance<Unit>()}\"",
        )
        forbiddenUses.forEach { use ->
            val forbidden = allowed.copy(text = allowed.text + "\n" + use)
            val violations = buildList { addPackageAndImportViolations(listOf(forbidden)) }
            assertTrue(violations.any { "kinetickk.foundation.dispatch" in it }, violations.joinToString("\n"))
        }
    }

    @Test
    fun foreignPublicDtoSignaturesFollowDeclaredApiDependencies() {
        val roots = listOf("ball/content/api", "ball/profile/api", "ball/gameplay/api", "flow/session/api", "resource/audio/api")
        val surfaces = roots.map { root ->
            val packageName = "kinetickk." + root.replace('/', '.')
            SourceDocument("$root/src/commonMain/kotlin/${packageName.replace('.', '/')}/Surface.kt", "package $packageName\n")
        }
        val consumer = surfaces.single { it.relativePath.startsWith("ball/gameplay/api/") }.let { source ->
            source.copy(text = source.text + """

                import kinetickk.ball.profile.api.CollectionProjection
                data class PublishedCollection(val projection: CollectionProjection)
            """.trimIndent())
        }
        val sources = surfaces.filterNot { it.relativePath == consumer.relativePath } + consumer
        val edges = setOf(ProjectEdge(":ball:gameplay:api", "commonMainApi", ":ball:profile:api"))
        assertTrue(applicationSurfaceViolations(sources).isEmpty())
        assertTrue(resolvedSemanticDirectControl(edges, sources).violations.isEmpty())
        assertTrue(resolvedSemanticDirectControl(emptySet(), sources).violations.any { "lacks a production dependency path" in it })

        val foreignState = consumer.copy(text = consumer.text + "\nimport kinetickk.ball.profile.nucleus.ProfileState\n")
        val violations = applicationSurfaceViolations(surfaces + foreignState)
        assertTrue(violations.any { "forbidden dependency token `.nucleus.`" in it })
        assertTrue(violations.any { "foreign sovereign State `ProfileState`" in it })
    }
}
