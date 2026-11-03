// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinFunctionScannerTest {
    private fun scan(vararg lines: String): List<ScannedFunction> =
        scanKotlinFunctions(maskKotlinSource(lines.joinToString("\n", postfix = "\n")))

    private fun lengths(vararg lines: String): Map<String, Int> = scan(*lines).associate { it.name to it.lines }

    private fun filler(count: Int): Array<String> = Array(count) { "        value$it," }

    @Test
    fun blockFunctionsCountFromTheKeywordLineAndIncludeMultiLineSignatures() {
        val functions = scan(
            "/**",
            " * KDoc is not counted.",
            " */",
            "@Suppress(\"Unused\")",
            "@JvmName(\"other\")",
            "fun five(): Int {",
            "    val a = 1",
            "    val b = 2",
            "    return a + b",
            "}",
            "fun signature(",
            "    a: Int,",
            "    b: Int,",
            "): Int {",
            "    return a",
            "}",
        )
        assertEquals(listOf(ScannedFunction("five", 6, 5, false), ScannedFunction("signature", 11, 6, false)), functions)
    }

    @Test
    fun expressionBodiesFollowContinuationLinesAndStopBeforeTheNextDeclaration() {
        assertEquals(
            mapOf("chain" to 6, "next" to 1, "last" to 3),
            lengths(
                "class Holder {",
                "    fun chain(items: List<Int>) = items",
                "        .map { it + 1 }",
                "        ?.filter { it > 2 }",
                "        ?: emptyList<Int>() +",
                "        listOf(4)",
                "        .sorted()",
                "    fun next() = 1",
                "    fun last() =",
                "        when (2) { else -> 3 }",
                "            .toString()",
                "}",
            ),
        )
    }

    @Test
    fun bodilessAbstractInterfaceAndExpectMembersAreNotMeasured() {
        assertEquals(
            mapOf("b" to 2),
            lengths(
                "abstract class A {",
                "    abstract fun a(): Int",
                "    fun b() {",
                "    }",
                "}",
                "interface I {",
                "    fun member(): Int",
                "    val x: Int",
                "}",
                "expect fun platform(): String",
            ),
        )
    }

    @Test
    fun funInterfacesAndAnonymousFunctionsAreNotMeasured() {
        assertEquals(
            emptyMap(),
            lengths(
                "fun interface Runner { fun run() }",
                "val f = fun(x: Int): Int { return x }",
            ),
        )
    }

    @Test
    fun namesSkipTypeParametersReceiversAndBackticks() {
        // Masking turns punctuation inside backticks into underscores, so the apostrophe reads as `_`.
        assertEquals(
            listOf("maxOrZero", "step", "it_s odd", "entries"),
            scan(
                "fun <T : Comparable<T>> List<T>.maxOrZero(): T where T : Any {",
                "    return first()",
                "}",
                "fun MutableGameState.step() {}",
                "fun `it's odd`() {}",
                "fun <K, V> Map<K, V>.entries(): Int = size",
            ).map { it.name },
        )
    }

    @Test
    fun bracesInStringsAndCommentsInsideTheBodyDoNotChangeTheLength() {
        assertEquals(
            mapOf("text" to 6),
            lengths(
                "fun text(): String {",
                "    // } closing brace in a comment",
                "    val a = \"}}}\"",
                "    val b = \"\${a.let { \"{\" }}\"",
                "    return a + b + '}'",
                "}",
                "val after = 1",
            ),
        )
    }

    @Test
    fun braceFreeTableBuildersAndPlainWhenTablesAreExempt() {
        val table = scan("fun table() = listOf(", *filler(118), ")").single()
        assertEquals(120, table.lines)
        assertTrue(table.isDataTable)

        val typed = scan("fun typed() = mapOf<String, Int>(", *filler(3), ")").single()
        assertTrue(typed.isDataTable)

        val withLambda = scan("fun withLambda() = listOf(", "        run { 1 },", *filler(117), ")").single()
        assertEquals(120, withLambda.lines)
        assertEquals(false, withLambda.isDataTable)

        assertTrue(scan("fun pick(k: Int) = when (k) {", "    1 -> \"a\"", "    else -> \"b\"", "}").single().isDataTable)
        assertEquals(
            false,
            scan("fun branch(k: Int) = when (k) {", "    1 -> { \"a\" }", "    else -> \"b\"", "}").single().isDataTable,
        )
        assertEquals(false, scan("fun block(): List<Int> {", "    return listOf(1, 2)", "}").single().isDataTable)
    }
}
