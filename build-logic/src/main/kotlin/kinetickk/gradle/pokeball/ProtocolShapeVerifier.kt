// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

private fun nextNonWhitespaceIndex(text: String, startIndex: Int): Int? =
    (startIndex until text.length).firstOrNull { index -> !text[index].isWhitespace() }

internal fun typeAliasDeclarations(code: String): List<Pair<String, String>> =
    Regex("\\btypealias\\s+($KOTLIN_IDENTIFIER_PATTERN)").findAll(code).filterNot { declaration ->
        code.isInsideBacktickIdentifier(declaration.range.first)
    }.mapNotNull { declaration ->
        var next = nextNonWhitespaceIndex(code, declaration.range.last + 1) ?: return@mapNotNull null
        if (code[next] == '<') {
            next = matchingDelimiter(code, next, '<', '>')?.plus(1) ?: return@mapNotNull null
            next = nextNonWhitespaceIndex(code, next) ?: return@mapNotNull null
        }
        if (code[next] != '=') return@mapNotNull null
        val target = typeAliasTarget(code, next + 1) ?: return@mapNotNull null
        normalizeKotlinIdentifier(declaration.groupValues[1]) to target
    }.toList()

private fun typeAliasTarget(code: String, startIndex: Int): String? {
    val start = nextNonWhitespaceIndex(code, startIndex) ?: return null
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var index = start
    var backticked = false
    while (index < code.length) {
        val character = code[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1 else return code.substring(start, index).trim()
            character == '<' && roundDepth == 0 && squareDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && code.getOrNull(index - 1) != '-' ->
                if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character == ';' -> if (roundDepth == 0 && angleDepth == 0 && squareDepth == 0) {
                return code.substring(start, index).trim()
            }
            character == '\n' -> if (roundDepth == 0 && angleDepth == 0 && squareDepth == 0) {
                val candidate = code.substring(start, index).trim()
                val nextIndex = nextNonWhitespaceIndex(code, index + 1)
                val next = nextIndex?.let(code::get)
                val nextStartsArrow = nextIndex != null && code.startsWith("->", nextIndex)
                val completeType = directTypeName(candidate) != null || hasTopLevelArrow(candidate)
                if (
                    candidate.isNotEmpty() && completeType && candidate != "suspend" &&
                    !candidate.endsWith('.') && !candidate.endsWith("->") && next != '.' && !nextStartsArrow
                ) {
                    return candidate
                }
            }
        }
        index += 1
    }
    return code.substring(start).trim().ifEmpty { null }
}

internal fun directTypeName(type: String): String? {
    val trimmed = normalizeDirectTypeText(type)
    if (hasTopLevelArrow(trimmed)) return null
    val rawType = trimmed.substring(0, firstGenericOpen(trimmed)).trim()
    val identifiers = Regex(KOTLIN_IDENTIFIER_PATTERN).findAll(rawType).map { match -> match.value }.toList()
    return identifiers.lastOrNull()?.let(::normalizeKotlinIdentifier)
}

private fun normalizeDirectTypeText(type: String): String {
    var trimmed = type.trim()
    var changed: Boolean
    do {
        val before = trimmed
        trimmed = stripLeadingTypeAnnotations(trimmed).trim().removeSuffix("?").trim()
        if (trimmed.startsWith('(')) {
            val close = matchingDelimiter(trimmed, 0, '(', ')')
            if (close == trimmed.lastIndex) trimmed = trimmed.substring(1, close).trim()
        }
        changed = trimmed != before
    } while (changed)
    return trimmed
}

private fun hasTopLevelArrow(type: String): Boolean {
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var backticked = false
    for (index in 0 until type.lastIndex) {
        val character = type[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && type.getOrNull(index - 1) != '-' ->
                if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character == '-' -> if (
                type[index + 1] == '>' && roundDepth == 0 && angleDepth == 0 && squareDepth == 0
            ) {
                return true
            }
        }
    }
    return false
}

private fun stripLeadingTypeAnnotations(type: String): String {
    var remaining = type.trim()
    while (remaining.startsWith('@')) {
        var index = nextNonWhitespaceIndex(remaining, 1) ?: break
        if (remaining[index] == '[') {
            val groupEnd = matchingDelimiter(remaining, index, '[', ']') ?: break
            remaining = remaining.substring(groupEnd + 1).trim()
            continue
        }
        var identifierEnd = kotlinIdentifierEnd(remaining, index) ?: break
        var afterIdentifier = nextNonWhitespaceIndex(remaining, identifierEnd) ?: remaining.length
        if (afterIdentifier < remaining.length && remaining[afterIdentifier] == ':') {
            index = nextNonWhitespaceIndex(remaining, afterIdentifier + 1) ?: break
            if (remaining[index] == '[') {
                val groupEnd = matchingDelimiter(remaining, index, '[', ']') ?: break
                remaining = remaining.substring(groupEnd + 1).trim()
                continue
            }
            identifierEnd = kotlinIdentifierEnd(remaining, index) ?: break
            afterIdentifier = nextNonWhitespaceIndex(remaining, identifierEnd) ?: remaining.length
        }
        while (afterIdentifier < remaining.length && remaining[afterIdentifier] == '.') {
            index = nextNonWhitespaceIndex(remaining, afterIdentifier + 1) ?: break
            identifierEnd = kotlinIdentifierEnd(remaining, index) ?: break
            afterIdentifier = nextNonWhitespaceIndex(remaining, identifierEnd) ?: remaining.length
        }
        if (afterIdentifier < remaining.length && remaining[afterIdentifier] == '(') {
            afterIdentifier = matchingDelimiter(remaining, afterIdentifier, '(', ')')?.plus(1) ?: break
        }
        remaining = remaining.substring(afterIdentifier).trim()
    }
    return remaining
}

private fun kotlinIdentifierEnd(text: String, start: Int): Int? {
    if (start !in text.indices) return null
    if (text[start] == '`') {
        val close = text.indexOf('`', start + 1)
        return close.takeIf { it > start + 1 }?.plus(1)
    }
    if (text[start] != '_' && !text[start].isLetter()) return null
    var index = start + 1
    while (index < text.length && (text[index] == '_' || text[index].isLetterOrDigit())) index += 1
    return index
}

private fun normalizeKotlinIdentifier(identifier: String): String =
    identifier.removeSurrounding("`")

private fun firstGenericOpen(type: String): Int {
    var backticked = false
    type.forEachIndexed { index, character ->
        when {
            character == '`' -> backticked = !backticked
            character == '<' && !backticked -> return index
        }
    }
    return type.length
}

private const val KOTLIN_IDENTIFIER_PATTERN =
    "(?:`[^`\\r\\n]+`|[\\p{L}_][\\p{L}\\p{N}_]*)"

private fun String.isInsideBacktickIdentifier(index: Int): Boolean {
    val lineStart = lastIndexOf('\n', startIndex = index - 1).let { newline -> newline + 1 }
    return substring(lineStart, index).count { character -> character == '`' } % 2 == 1
}

private fun matchingDelimiter(
    text: String,
    openIndex: Int,
    open: Char,
    close: Char,
): Int? {
    if (openIndex !in text.indices || text[openIndex] != open) return null
    if (open == '<' && close == '>') return matchingAngleDelimiter(text, openIndex)
    var depth = 0
    var backticked = false
    for (index in openIndex until text.length) {
        val character = text[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == open -> depth += 1
            character == close && !(open == '<' && close == '>' && text.getOrNull(index - 1) == '-') -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return null
}

private fun matchingAngleDelimiter(text: String, openIndex: Int): Int? {
    var angleDepth = 0
    var roundDepth = 0
    var squareDepth = 0
    var curlyDepth = 0
    var backticked = false
    for (index in openIndex until text.length) {
        val character = text[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character == '{' -> curlyDepth += 1
            character == '}' -> if (curlyDepth > 0) curlyDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 &&
                text.getOrNull(index - 1) != '-' -> {
                angleDepth -= 1
                if (angleDepth == 0) return index
            }
        }
    }
    return null
}

internal fun String.maskKotlinNonCode(): String {
    val output = StringBuilder(length)
    var index = 0
    var blockCommentDepth = 0
    var lineComment = false
    var stringQuote: Char? = null
    var tripleQuoted = false
    var escaped = false
    var backtickIdentifier = false

    fun appendMasked(character: Char) {
        output.append(if (character == '\n') '\n' else ' ')
    }

    while (index < length) {
        val character = this[index]
        val next = getOrNull(index + 1)
        when {
            lineComment -> {
                appendMasked(character)
                if (character == '\n') lineComment = false
            }
            blockCommentDepth > 0 -> when {
                character == '/' && next == '*' -> {
                    output.append("  ")
                    blockCommentDepth += 1
                    index += 1
                }
                character == '*' && next == '/' -> {
                    output.append("  ")
                    blockCommentDepth -= 1
                    index += 1
                }
                else -> appendMasked(character)
            }
            stringQuote != null -> when {
                tripleQuoted && substring(index, minOf(length, index + 3)) == "\"\"\"" -> {
                    output.append("   ")
                    index += 2
                    stringQuote = null
                    tripleQuoted = false
                }
                !tripleQuoted && escaped -> {
                    appendMasked(character)
                    escaped = false
                }
                !tripleQuoted && character == '\\' -> {
                    appendMasked(character)
                    escaped = true
                }
                !tripleQuoted && character == stringQuote -> {
                    appendMasked(character)
                    stringQuote = null
                }
                else -> appendMasked(character)
            }
            backtickIdentifier -> {
                output.append(character)
                if (character == '`') backtickIdentifier = false
            }
            character == '`' -> {
                output.append(character)
                backtickIdentifier = true
            }
            character == '/' && next == '/' -> {
                output.append("  ")
                lineComment = true
                index += 1
            }
            character == '/' && next == '*' -> {
                output.append("  ")
                blockCommentDepth = 1
                index += 1
            }
            substring(index, minOf(length, index + 3)) == "\"\"\"" -> {
                output.append("   ")
                stringQuote = '"'
                tripleQuoted = true
                index += 2
            }
            character == '"' || character == '\'' -> {
                appendMasked(character)
                stringQuote = character
                tripleQuoted = false
                escaped = false
            }
            else -> output.append(character)
        }
        index += 1
    }
    return output.toString()
}
