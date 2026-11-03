// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

/**
 * One named function with a body. [lines] runs from the line of the `fun` keyword to the line that
 * ends the body; [isDataTable] marks an expression body that is a brace-free table builder call or
 * a `when` without lambdas or block branches.
 */
internal data class ScannedFunction(val name: String, val line: Int, val lines: Int, val isDataTable: Boolean)

/** Raised when masked text does not let the scanner close a function body. */
internal class FunctionScanException(message: String) : RuntimeException(message)

/**
 * Function-length heuristic over masked Kotlin text (see [maskKotlinSource]); the caller checks
 * [bracesBalance] first. It is not a parser. Known limits, which review has to cover:
 * - every physical line inside the body counts, blank lines and comments included;
 * - same-named functions in one file are indistinguishable to callers that key by name;
 * - local functions are measured on their own and inside their parent;
 * - property accessors, `init` blocks, secondary constructors, lambdas stored in properties,
 *   property initializers, anonymous functions and extensions on parenthesized function types
 *   are not measured;
 * - an expression body ends at a newline unless that line ends, or the next line starts, with a
 *   continuation token, which follows Kotlin's own newline rules; a line that happens to end with
 *   such a token (`… in`) can over-count.
 */
internal fun scanKotlinFunctions(masked: String): List<ScannedFunction> = KotlinFunctionScanner(masked).scan()

private class KotlinFunctionScanner(private val code: String) {
    private val n = code.length
    private val lines = KotlinLineIndex(code)

    fun scan(): List<ScannedFunction> = FUN.findAll(code).mapNotNull { scanAt(it.range.first, it.range.last + 1) }.toList()

    private fun scanAt(keyword: Int, afterKeyword: Int): ScannedFunction? {
        var j = afterKeyword
        while (j < n && code[j] in " \t\r\n") j++
        // `fun interface`, an anonymous function or `fun (T).ext()` (a documented blind spot).
        if (isFunInterface(j) || (j < n && code[j] == '(')) return null
        val parameterStart = headerEnd(j) ?: return null
        val name = functionName(code.substring(j, parameterStart).trim())
        val parameterEnd = matchingParenthesis(parameterStart) ?: return null
        val (kind, bodyStart) = findBody(parameterEnd + 1) ?: return null
        return if (kind == BodyKind.BLOCK) {
            val end = matchingBrace(bodyStart)
            ScannedFunction(name, lines.lineOf(keyword), lines.lineOf(end) - lines.lineOf(keyword) + 1, false)
        } else {
            expressionFunction(name, keyword, bodyStart)
        }
    }

    private fun isFunInterface(j: Int): Boolean =
        code.startsWith("interface", j) && (j + 9 >= n || !code[j + 9].isIdentifierPart())

    /** Offset of the parameter list's `(` at angle depth 0, or null when `{`, `}`, `=` or `;` comes first. */
    private fun headerEnd(start: Int): Int? {
        var angle = 0
        var k = start
        while (k < n) {
            when (code[k]) {
                '<' -> angle++
                '>' -> angle--
                '(' -> if (angle == 0) return k
                '{', '}', '=', ';' -> return null
            }
            k++
        }
        return null
    }

    private fun functionName(header: String): String {
        var head = header
        if (head.startsWith("<")) {
            var depth = 0
            for ((q, c) in head.withIndex()) {
                depth += angleStep(c)
                if (depth == 0) {
                    head = head.substring(q + 1)
                    break
                }
            }
        }
        var depth = 0
        var last = -1
        for ((q, c) in head.withIndex()) {
            depth += angleStep(c)
            if (c == '.' && depth == 0) last = q
        }
        return head.substring(last + 1).trim().trim('`').trim().ifEmpty { "?" }
    }

    private fun angleStep(c: Char): Int = when (c) {
        '<' -> 1
        '>' -> -1
        else -> 0
    }

    private fun matchingParenthesis(open: Int): Int? {
        var depth = 0
        var q = open
        while (q < n) {
            if (code[q] == '(') depth++ else if (code[q] == ')') depth--
            if (depth == 0) return q
            q++
        }
        return null
    }

    /** Finds `{` (block body) or a lone `=` (expression body) after the parameter list; null for bodiless members. */
    private fun findBody(start: Int): Pair<BodyKind, Int>? {
        var r = start
        var depth = 0
        while (r < n) {
            val c = code[r]
            when {
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> depth--
                depth != 0 -> Unit
                c == '{' -> return BodyKind.BLOCK to r
                c == '=' && isAssignment(r) -> return BodyKind.EXPRESSION to r
                c == ';' || c == '}' -> return null
                c == '\n' -> {
                    val next = nextLine(r)
                    if (next.isBlank() || STOP_LINE.containsMatchIn(next)) return null
                }
            }
            r++
        }
        return null
    }

    private fun isAssignment(r: Int): Boolean =
        (r + 1 >= n || code[r + 1] != '=') && code[r - 1] !in "!<>="

    private fun matchingBrace(open: Int): Int {
        var depth = 0
        var e = open
        while (e < n) {
            if (code[e] == '{') depth++ else if (code[e] == '}') depth--
            if (depth == 0) return e
            e++
        }
        throw FunctionScanException("unterminated function body at line ${lines.lineOf(open)}")
    }

    private fun expressionFunction(name: String, keyword: Int, equals: Int): ScannedFunction {
        var depth = 0
        var e = equals + 1
        var end = -1
        while (e < n) {
            val c = code[e]
            if (c == '(' || c == '[' || c == '{') {
                depth++
            } else if (c == ')' || c == ']' || c == '}') {
                depth--
                if (depth < 0) {
                    end = e - 1
                    break
                }
            } else if (c == '\n' && depth == 0 && endsExpression(e)) {
                end = e - 1
                break
            }
            e++
        }
        if (end < 0) end = n - 1
        while (end > equals && code[end] in " \t\r\n") end--
        val expression = code.substring(equals + 1, end + 1).trimStart()
        val table = (TABLE_CALL.containsMatchIn(expression) && '{' !in expression) ||
            (WHEN_START.containsMatchIn(expression) && expression.count { it == '{' } == 1)
        return ScannedFunction(name, lines.lineOf(keyword), lines.lineOf(end) - lines.lineOf(keyword) + 1, table)
    }

    /** At a depth-0 newline: the expression ends unless this line or the next one continues it. */
    private fun endsExpression(newline: Int): Boolean {
        val current = code.substring(code.lastIndexOf('\n', newline - 1) + 1, newline)
        if (current.isNotBlank() && CONT_END.containsMatchIn(current)) return false
        val next = nextLine(newline)
        return next.isBlank() || !CONT_START.containsMatchIn(next)
    }

    private fun nextLine(newline: Int): String {
        val end = code.indexOf('\n', newline + 1)
        return code.substring(newline + 1, if (end < 0) n else end)
    }

    private fun Char.isIdentifierPart(): Boolean = this == '_' || this == '`' || this == '$' || isLetterOrDigit()

    private enum class BodyKind { BLOCK, EXPRESSION }

    private companion object {
        val FUN = Regex("""(?<![\w`$])fun(?![\w`$])""")
        val STOP_LINE = Regex(
            "^\\s*(?:@|(?:fun|val|var|class|object|interface|typealias|init|constructor|companion|enum|sealed|data|" +
                "private|internal|public|protected|override|abstract|open|suspend|inline|operator|infix|tailrec|" +
                "external|actual|expect|const|lateinit|value|annotation|inner)\\b)",
        )
        val CONT_START = Regex("""^\s*(?:\.|\?\.|\?:|&&|\|\||as\b|as\?|else\b|->|\)|\]|\})""")
        val CONT_END = Regex("""(?:[+\-*/%=,(\[{.]|&&|\|\||\?:|->|\b(?:in|is|as|else))\s*$""")
        val TABLE_CALL = Regex(
            "^(?:listOf|mutableListOf|arrayOf|mapOf|mutableMapOf|setOf|immutableListOf|immutableSetOf|" +
                "persistentListOf|intArrayOf|floatArrayOf|longArrayOf|doubleArrayOf)\\s*(?:<[^>{}]*>)?\\s*\\(",
        )
        val WHEN_START = Regex("^when\\b")
    }
}
