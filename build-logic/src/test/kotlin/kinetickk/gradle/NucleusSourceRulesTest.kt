// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NucleusSourceRulesTest {
    @Test
    fun effectImportsAreRejectedIncludingAliasesAndParentWildcards() {
        val forbidden = listOf(
            "androidx.compose.runtime.mutableStateOf",
            "java.io.File as LocalFile",
            "java.net.URL",
            "kotlin.io.println",
            "kotlinx.coroutines.launch",
            "kotlin.reflect.full.memberProperties",
            "kotlin.time.Clock",
            "kotlin.time.*",
            "kotlinx.datetime.Clock as WallTime",
            "kotlin.random.Random.Default",
            "kotlin.random.*",
            "kinetickk.foundation.dispatch.InlineAcceptance",
            "kinetickk.foundation.diagnostics.CrashDiagnostics",
        )
        for (target in forbidden) {
            val violations = nucleusImportViolations("package example\nimport $target\nfun decide() = Unit")
            assertEquals(1, violations.size, "Import escaped the sensor: $target")
            assertTrue(violations.single().startsWith("2:"), violations.single())
        }
    }

    @Test
    fun deterministicInputsSeededRandomTypesAndOrdinaryValuesRemainAllowed() {
        assertTrue(nucleusImportViolations("""
            package example
            import kotlin.math.*
            import kotlin.jvm.JvmInline
            import kotlin.random.Random
            import kotlin.time.Duration
            import kinetickk.foundation.random.CloneableXorWowRandom
            import kinetickk.foundation.collections.ImmutableList
            import kinetickk.ball.profile.api.ProfileRevision
        """.trimIndent()).isEmpty())
    }

    @Test
    fun commentsAndStringExamplesDoNotCreateFalseImportsOrHideLaterRealImports() {
        val source = """
            // import java.io.File
            /* outer
                /* nested comment */
                import kotlinx.coroutines.launch
            */
            import kotlin.time.Clock as AmbientTime
        """.trimIndent() + "\nval example = \"\"\"\nimport java.net.URL\n\"\"\"\n" +
            "val escaped = \"a \\\"quote\\\"\"\nval character = '\\\''\n"
        assertEquals(
            listOf("6: forbidden nucleus import 'kotlin.time.Clock'; keep effects in the owner/adapters"),
            nucleusImportViolations(source),
        )
    }

    @Test
    fun ambientRandomnessCallsAreRejectedOnePerCall() {
        val calls = listOf(
            "Random.nextFloat()",
            "Random.Default.nextInt()",
            "list.random()",
            "xs.shuffled()",
            "items.shuffle()",
            "Random\n    .nextInt(3)",
        )
        for (call in calls) {
            val violations = ambientRandomnessViolations("package example\nfun decide() {\n    val x = $call\n}\n")
            assertEquals(1, violations.size, "Ambient randomness escaped the sensor: $call -> $violations")
            assertTrue(violations.single().startsWith("3: ambient randomness '"), violations.single())
            assertTrue(violations.single().endsWith("; draw from a seeded run stream or worldHash"), violations.single())
        }
    }

    @Test
    fun seededRandomnessAndRandomnessInStringsOrCommentsRemainAllowed() {
        assertTrue(ambientRandomnessViolations("""
            package example
            import kotlin.random.Random
            fun decide(random: Random, rng: Random, list: List<Int>) {
                random.nextFloat()
                rng.nextInt(3)
                list.random(rng)
                list.shuffled(rng)
                CloneableXorWowRandom(seed)
                val label = "Random.nextFloat()"
                // list.random()
            }
        """.trimIndent()).isEmpty())
    }

    @Test
    fun apiSourcesRejectPlatformAndEffectImportsButMayUseDispatchReplies() {
        for (target in listOf(
            "androidx.compose.runtime.Stable",
            "kinetickk.foundation.diagnostics.X",
            "kotlinx.coroutines.flow.StateFlow",
            "java.io.File",
            "kinetickk.foundation.design.KkColors",
        )) {
            val violations = apiImportViolations("package example\nimport $target\n")
            assertEquals(1, violations.size, "Import escaped the api sensor: $target")
            assertTrue(violations.single().startsWith("2: forbidden api import '$target'"), violations.single())
        }
        assertTrue(apiImportViolations("""
            package example
            import kinetickk.foundation.dispatch.InlineReply
            import kinetickk.foundation.collections.ImmutableList
            import kotlin.random.Random
        """.trimIndent()).isEmpty())
    }

    @Test
    fun similarlyNamedPurePackagesDoNotMatchForbiddenPackagePrefixes() {
        assertTrue(nucleusImportViolations("""
            import example.java.io.FileData
            import kotlin.time.ClockReading
            import kinetickk.foundation.dispatchData.Command
        """.trimIndent()).isEmpty())
    }
}
