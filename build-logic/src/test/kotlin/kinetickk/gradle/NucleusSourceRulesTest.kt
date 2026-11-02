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
    fun similarlyNamedPurePackagesDoNotMatchForbiddenPackagePrefixes() {
        assertTrue(nucleusImportViolations("""
            import example.java.io.FileData
            import kotlin.time.ClockReading
            import kinetickk.foundation.dispatchData.Command
        """.trimIndent()).isEmpty())
    }
}
