// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinSourceTextTest {
    @Test
    fun maskingKeepsLengthAndNewlinesAndBlanksCommentsStringsAndChars() {
        val source = listOf(
            "val a = 1 // trailing { comment",
            "/* outer /* nested { */ still } comment */ val b = 2",
            "val c = \"escaped \\\" quote { \"",
            "val d = \"\"\"raw { \" text",
            "} still raw\"\"\"",
            "val e = '\\''",
            "val f = '\"'",
            "val g = '{'",
        ).joinToString("\n", postfix = "\n")
        val masked = maskKotlinSource(source)

        assertEquals(source.length, masked.length)
        assertEquals(source.indices.filter { source[it] == '\n' }, masked.indices.filter { masked[it] == '\n' })
        assertTrue(bracesBalance(masked), masked)
        assertFalse(masked.contains("comment"), masked)
        assertFalse(masked.contains("quote"), masked)
        assertFalse(masked.contains("raw"), masked)
        listOf("val a = 1", "val b = 2", "val c =", "val d =", "val e =", "val f =", "val g =").forEach { code ->
            assertTrue(code in masked, "code was masked: $code")
        }
    }

    @Test
    fun stringTemplatesWithNestedQuotesAndBracesLeaveBalancedCode() {
        listOf(
            "val s = \"\${a ?: \"{\"}\"",
            "val s = \"\"\"\${\"}\"}\"\"\"",
            "val s = \"\${list.joinToString { \"x\" }}\"",
            "val s = \"\${'}'} and \${/* } */ b}\"",
        ).forEach { line ->
            val masked = maskKotlinSource("fun f() {\n$line\n}\n")
            assertTrue(bracesBalance(masked), "unbalanced after masking: $line")
            assertEquals("fun f() {", masked.lineSequence().first())
            assertTrue(masked.lines()[1].removePrefix("val s =").isBlank(), "template code survived: ${masked.lines()[1]}")
        }
    }

    @Test
    fun backtickNamesKeepBracesBalancedAndApostrophesInert() {
        val masked = maskKotlinSource("fun `it's {odd}`() {}\nval x = 1\n")
        assertTrue(bracesBalance(masked), masked)
        assertTrue(masked.startsWith("fun `it_s _odd_`() {}"), masked)
        assertTrue("val x = 1" in masked)
    }

    @Test
    fun anUnterminatedStringStopsAtTheLineEnd() {
        val masked = maskKotlinSource("val broken = \"no end {\nimport java.io.File\nfun f() {}\n")
        assertEquals(listOf(KotlinImport("java.io.File", 2)), kotlinImports(masked))
        assertTrue(bracesBalance(masked), masked)
    }

    @Test
    fun importsIncludeAliasesAndWildcardsWithLinesAndIgnoreCommentsAndStrings() {
        val source = """
            package a.b

            import kotlin.math.max as maximum
            // import java.io.File
            /* import java.net.URL */
            import kinetickk.ball.gameplay.nucleus.render.*
            val example = "import kotlinx.coroutines.launch"
              import kotlin.random.Random
        """.trimIndent()
        val masked = maskKotlinSource(source)
        assertEquals(
            listOf(
                KotlinImport("kotlin.math.max", 3),
                KotlinImport("kinetickk.ball.gameplay.nucleus.render.*", 6),
                KotlinImport("kotlin.random.Random", 8),
            ),
            kotlinImports(masked),
        )
        assertEquals("a.b", kotlinPackage(masked))
        assertNull(kotlinPackage(maskKotlinSource("// package a.b\nval x = \"package c\"\n")))
    }

    @Test
    fun physicalLineCountMatchesWcWithAnUnterminatedLastLine() {
        assertEquals(0, physicalLineCount(""))
        assertEquals(1, physicalLineCount("a"))
        assertEquals(1, physicalLineCount("a\n"))
        assertEquals(2, physicalLineCount("a\nb"))
        assertEquals(2, physicalLineCount("a\nb\n"))
    }

    @Test
    fun braceBalanceRejectsEarlyClosersAndLeftoverOpeners() {
        assertTrue(bracesBalance("{ { } }"))
        assertFalse(bracesBalance("} {"))
        assertFalse(bracesBalance("{ {"))
    }
}
