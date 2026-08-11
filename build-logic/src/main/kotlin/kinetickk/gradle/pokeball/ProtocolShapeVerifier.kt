// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

internal data class CanonicalDataClassShape(
    val path: String,
    val typeName: String,
    val fields: List<CanonicalFieldShape>,
    val withinDeclaration: String? = null,
    val forbidFieldDefaults: Boolean = false,
    val forbidFieldModifiers: Boolean = false,
    val requireExplicitPublicVisibility: Boolean = false,
    val requireDirectPrimaryConstructorSyntax: Boolean = false,
    val forbidClassHeaderSuffix: Boolean = false,
    val forbidBodyProperties: Boolean = false,
    val reserveExtraComponentSpellings: Boolean = false,
    val requireDirectPrivateForExtensionProperties: Boolean = false,
    val forbidTypeAliases: Boolean = false,
)

internal data class CanonicalFieldShape(
    val name: String,
    val type: String,
    val mutable: Boolean = false,
    val hasDefault: Boolean = false,
    val modifierPrefix: String = "",
)

internal data class ForeignApplicationSurfacePolicy(
    val sourceRoot: String,
    val ownPackage: String,
    val allowedForeignImports: Set<String>,
)

internal data class CanonicalEnumInventory(
    val path: String,
    val typeName: String,
    val entries: List<String>,
)

internal data class ClosedDirectSubtypeInventory(
    val path: String,
    val declaration: String,
    val supertype: String,
    val expectedSubtypeNames: Set<String>,
)

internal fun exactDataClassShapeViolations(
    sources: Map<String, SourceDocument>,
    shapes: List<CanonicalDataClassShape>,
): List<String> = buildList {
    shapes.forEach { shape ->
        val source = sources[shape.path]
        if (source == null) {
            add("Canonical protocol shape ${shape.typeName} is missing source ${shape.path}")
            return@forEach
        }
        val scope = if (shape.withinDeclaration == null) {
            source.text
        } else {
            typeDeclarationBody(source.text, shape.withinDeclaration) ?: run {
                add(
                    "Canonical protocol shape ${shape.typeName} is missing enclosing declaration " +
                        "`${shape.withinDeclaration}` in ${shape.path}",
                )
                return@forEach
            }
        }
        val actualFields = primaryConstructorFields(
            scope,
            shape.typeName,
            includeDefaults = shape.forbidFieldDefaults,
            includeModifiers = shape.forbidFieldModifiers,
        )
        when {
            actualFields == null && shape.requireDirectPrimaryConstructorSyntax -> add(
                "Canonical protocol shape ${shape.typeName} must use direct canonical syntax " +
                    "`data class ${shape.typeName}(` in ${shape.path}",
            )
            actualFields == null -> add(
                "Canonical protocol shape ${shape.typeName} is not declared as a data class in ${shape.path}",
            )
            actualFields != shape.fields -> add(
                "Canonical protocol shape ${shape.typeName} fields must be exactly " +
                    "${shape.fields.joinToString(transform = CanonicalFieldShape::render)}; found " +
                    actualFields.joinToString(transform = CanonicalFieldShape::render),
            )
        }
        if (shape.requireExplicitPublicVisibility && !dataClassHasExplicitPublicVisibility(scope, shape.typeName)) {
            add(
                "Canonical protocol shape ${shape.typeName} must use direct same-line `public data class` " +
                    "visibility syntax in ${shape.path}",
            )
        }
        if (shape.forbidClassHeaderSuffix && dataClassHasHeaderSuffix(scope, shape.typeName)) {
            add(
                "Canonical protocol shape ${shape.typeName} must not declare supertypes, delegation, " +
                    "or type constraints in ${shape.path}",
            )
        }
        if (shape.forbidBodyProperties) {
            val bodyProperties = dataClassBodyProperties(scope, shape.typeName)
            if (bodyProperties == null) {
                add("Canonical protocol shape ${shape.typeName} is missing a readable class body in ${shape.path}")
            } else if (bodyProperties.isNotEmpty()) {
                add(
                    "Canonical protocol shape ${shape.typeName} must not declare body properties; found " +
                        bodyProperties.joinToString(),
                )
            }
        }
        if (shape.reserveExtraComponentSpellings) {
            sources.values
                .asSequence()
                .filter(SourceDocument::isProductionMainKotlinSource)
                .forEach { candidate ->
                    extraComponentIdentifiers(candidate.text, shape.fields.size)
                        .forEach { component ->
                            add(
                                "Canonical protocol shape ${shape.typeName} reserves $component; " +
                                    "custom component spellings beyond component${shape.fields.size} are " +
                                    "forbidden in production Main source ${candidate.relativePath}",
                            )
                        }
                }
        }
        if (shape.requireDirectPrivateForExtensionProperties) {
            sources.values
                .asSequence()
                .filter(SourceDocument::isProductionMainKotlinSource)
                .forEach { candidate ->
                    nonPrivateTopLevelExtensionProperties(candidate.text).forEach { property ->
                        add(
                            "Canonical protocol shape ${shape.typeName} requires a direct same-line private " +
                                "modifier for production extension property `$property` in " +
                                candidate.relativePath,
                        )
                    }
                }
        }
        if (shape.forbidTypeAliases) {
            sources.values
                .asSequence()
                .filter(SourceDocument::isProductionMainKotlinSource)
                .forEach { candidate ->
                    acceptedFrameDeclaredTypeAliases(candidate.text, shape.typeName).forEach { alias ->
                        add(
                            "Canonical protocol shape ${shape.typeName} must not expose typealias " +
                                "`$alias` in ${candidate.relativePath}",
                        )
                    }
                }
        }
    }
}.distinct().sorted()

internal fun exactEnumInventoryViolations(
    sources: Map<String, SourceDocument>,
    inventories: List<CanonicalEnumInventory>,
): List<String> = buildList {
    inventories.forEach { inventory ->
        val source = sources[inventory.path]
        if (source == null) {
            add("Canonical enum ${inventory.typeName} is missing source ${inventory.path}")
            return@forEach
        }
        val actual = enumEntries(source.text, inventory.typeName)
        when {
            actual == null -> add("Canonical enum ${inventory.typeName} is missing in ${inventory.path}")
            actual != inventory.entries -> add(
                "Canonical enum ${inventory.typeName} entries must be exactly " +
                    "${inventory.entries.joinToString()}; found ${actual.joinToString()}",
            )
        }
    }
}.distinct().sorted()

internal fun decisionContextBoundaryViolations(sources: List<SourceDocument>): List<String> = buildList {
    val forbiddenFieldTokens = setOf(
        "admission",
        "budget",
        "capacity",
        "causaldepth",
        "causalscope",
        "command",
        "fanout",
    )
    sources.asSequence()
        .filter { source ->
            source.relativePath.contains("/nucleus/src/") &&
                source.relativePath.contains("Main/") &&
                source.relativePath.endsWith(".kt")
        }
        .forEach { source ->
            dataClassNames(source.text)
                .filter { typeName -> typeName.endsWith("Context") }
                .forEach contextLoop@{ typeName ->
                    val constructor = primaryConstructor(source.text, typeName) ?: return@contextLoop
                    constructorFields(constructor).forEach { field ->
                        val normalized = (field.name + field.type)
                            .filter(Char::isLetterOrDigit)
                            .lowercase()
                        forbiddenFieldTokens.filter(normalized::contains).forEach { token ->
                            add(
                                "DecisionContext $typeName in ${source.relativePath} contains forbidden " +
                                    "runtime/command field `${field.name}` via `$token`",
                            )
                        }
                    }
                }
        }
}.distinct().sorted()

internal fun foreignApplicationSurfaceSignatureViolations(
    sources: List<SourceDocument>,
    policies: List<ForeignApplicationSurfacePolicy>,
): List<String> = buildList {
    policies.forEach { policy ->
        sources.asSequence()
            .filter { source ->
                source.relativePath.startsWith(policy.sourceRoot) &&
                    source.relativePath.contains("/src/") &&
                    source.relativePath.contains("Main/") &&
                    source.relativePath.endsWith(".kt")
            }
            .forEach { source ->
                referencedSurfaceTypes(source.text).forEach { referencedType ->
                    val importedPackage = referencedType.substringBeforeLast('.', missingDelimiterValue = "")
                    val isPokeballSurfaceImport = importedPackage.startsWith("kinetickk.ball.") ||
                        importedPackage.startsWith("kinetickk.flow.") ||
                        importedPackage.startsWith("kinetickk.resource.")
                    if (isPokeballSurfaceImport &&
                        importedPackage != policy.ownPackage &&
                        referencedType !in policy.allowedForeignImports
                    ) {
                        add(
                            "Application Surface ${source.relativePath} exposes unapproved foreign signature " +
                                "type `$referencedType`",
                        )
                    }
                }
            }
    }
}.distinct().sorted()

internal fun publicSourceCompletionWrapperViolations(
    sources: List<SourceDocument>,
    applicationSurfaceRoots: Set<String>,
): List<String> = buildList {
    val completionDeclaration = Regex(
        "\\b(?:(?:data|sealed)\\s+)?(?:class|object|interface)\\s+" +
            "([A-Za-z_][A-Za-z0-9_]*CommandCompleted)\\b",
    )
    sources.asSequence()
        .filter { source ->
            applicationSurfaceRoots.any(source.relativePath::startsWith) &&
                source.relativePath.contains("/src/") &&
                source.relativePath.contains("Main/") &&
                source.relativePath.endsWith(".kt")
        }
        .forEach { source ->
            completionDeclaration.findAll(source.text).forEach { match ->
                add(
                    "Caller Application Surface ${source.relativePath} exposes forbidden source completion " +
                        "wrapper `${match.groupValues[1]}`; receive the target ModuleResultDelivery only in caller Nucleus",
                )
            }
        }
}.distinct().sorted()

internal fun forbiddenProtocolSymbolViolations(
    sources: Map<String, SourceDocument>,
    forbiddenByPath: Map<String, Set<String>>,
): List<String> = buildList {
    forbiddenByPath.forEach { (path, symbols) ->
        val source = sources[path]
        if (source == null) {
            add("Protocol compatibility scan is missing source $path")
        } else {
            symbols.filter(source.text::contains).forEach { symbol ->
                add("Protocol source $path retains forbidden compatibility symbol `$symbol`")
            }
        }
    }
}.distinct().sorted()

internal fun requiredProtocolEvidenceViolations(
    sources: Map<String, SourceDocument>,
    anchors: List<BoundAnchor>,
): List<String> = buildList {
    anchors.forEach { anchor ->
        val source = sources[anchor.path]
        if (source == null) {
            add("Canonical protocol evidence is missing ${anchor.path}")
        } else {
            anchor.tokens.filterNot(source.text::contains).forEach { token ->
                add("Canonical protocol evidence ${anchor.path} is missing `$token`")
            }
        }
    }
}.distinct().sorted()

internal fun closedDirectSubtypeInventoryViolations(
    source: SourceDocument?,
    declaration: String,
    supertype: String,
    expectedSubtypeNames: Set<String>,
): List<String> = buildList {
    if (source == null) {
        add("Closed protocol inventory is missing source for $declaration")
        return@buildList
    }
    val body = typeDeclarationBody(source.text, declaration)
    if (body == null) {
        add("Closed protocol inventory ${source.relativePath} is missing `$declaration`")
        return@buildList
    }
    val actual = directSubtypeNames(body, supertype)
    if (actual != expectedSubtypeNames) {
        add(
            "Closed protocol inventory $declaration in ${source.relativePath} must contain exactly " +
                "${expectedSubtypeNames.sorted().joinToString()}; found ${actual.sorted().joinToString()}",
        )
    }
}

internal fun closedDirectSubtypeInventoryViolations(
    sources: Map<String, SourceDocument>,
    inventories: List<ClosedDirectSubtypeInventory>,
): List<String> = inventories.flatMap { inventory ->
    closedDirectSubtypeInventoryViolations(
        source = sources[inventory.path],
        declaration = inventory.declaration,
        supertype = inventory.supertype,
        expectedSubtypeNames = inventory.expectedSubtypeNames,
    )
}.distinct().sorted()

private fun CanonicalFieldShape.render(): String =
    "${modifierPrefix.takeIf(String::isNotEmpty)?.plus(" ").orEmpty()}" +
        "${if (mutable) "var" else "val"} $name: $type${if (hasDefault) " = <default>" else ""}"

private fun primaryConstructorFields(
    text: String,
    typeName: String,
    includeDefaults: Boolean,
    includeModifiers: Boolean,
): List<CanonicalFieldShape>? = primaryConstructor(text, typeName)?.let { constructor ->
    constructorFields(constructor, includeDefaults, includeModifiers)
}

private fun primaryConstructor(text: String, typeName: String): String? {
    val code = text.maskKotlinNonCode()
    val declaration = topLevelDataClassDeclaration(code, typeName) ?: return null
    val open = code.indexOf('(', declaration.range.first)
    val close = matchingDelimiter(code, open, '(', ')') ?: return null
    return code.substring(open + 1, close)
}

private fun constructorFields(
    constructor: String,
    includeDefaults: Boolean = false,
    includeModifiers: Boolean = false,
): List<CanonicalFieldShape> =
    splitAtTopLevel(constructor, ',').filter { parameter -> parameter.isNotBlank() }.map { parameter ->
        val matches = Regex(
            "\\b(val|var)\\s+($KOTLIN_IDENTIFIER_PATTERN)\\s*:\\s*(.+)",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(parameter)
        val match = matches.firstOrNull { candidate ->
            !parameter.isInsideBacktickIdentifier(candidate.range.first)
        } ?: return@map CanonicalFieldShape(
            name = "<unparsed>",
            type = parameter.trim().replace(Regex("\\s+"), " "),
            modifierPrefix = "<unparsed>",
        )
        CanonicalFieldShape(
            name = normalizeKotlinIdentifier(match.groupValues[2]),
            type = substringBeforeTopLevel(match.groupValues[3], '=').trim().replace(Regex("\\s+"), " "),
            mutable = match.groupValues[1] == "var",
            hasDefault = includeDefaults && hasTopLevelDelimiter(match.groupValues[3], '='),
            modifierPrefix = if (includeModifiers) {
                parameter.substring(0, match.range.first).trim().replace(Regex("\\s+"), " ")
            } else {
                ""
            },
        )
    }

private fun dataClassBodyProperties(text: String, typeName: String): List<String>? {
    val code = text.maskKotlinNonCode()
    val declaration = topLevelDataClassDeclaration(code, typeName) ?: return null
    val constructorOpen = code.indexOf('(', declaration.range.first)
    val constructorClose = matchingDelimiter(code, constructorOpen, '(', ')') ?: return null
    val bodyOpen = nextNonWhitespaceIndex(code, constructorClose + 1)
    if (bodyOpen == null || code[bodyOpen] != '{') return emptyList()
    val bodyClose = matchingDelimiter(code, bodyOpen, '{', '}') ?: return null
    val body = code.substring(bodyOpen + 1, bodyClose)
    val properties = mutableListOf<String>()
    var depth = 0
    var index = 0
    var backticked = false
    while (index < body.length) {
        val character = body[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '{' -> depth += 1
            character == '}' -> depth -= 1
            depth == 0 -> {
                val match = Regex("\\b(val|var)\\b").find(body, index)
                if (match != null && body.substring(index, match.range.first).none { it == '{' || it == '}' }) {
                    if (!body.isInsideBacktickIdentifier(match.range.first)) {
                        properties += "${match.groupValues[1]} ${propertyNameAfter(body, match.range.last + 1)}"
                        index = match.range.last
                    }
                }
            }
        }
        index += 1
    }
    return properties
}

private fun dataClassHasExplicitPublicVisibility(text: String, typeName: String): Boolean {
    val code = text.maskKotlinNonCode()
    val canonicalName = Regex.escape(typeName)
    val declaration = Regex(
        "^[ \\t]*public[ \\t]+data[ \\t]+class[ \\t]+" +
            "(?:$canonicalName|`$canonicalName`)[ \\t]*\\(",
        setOf(RegexOption.MULTILINE),
    )
    return declaration.findAll(code).count { match ->
        !code.isInsideBacktickIdentifier(match.range.first) && curlyDepthAt(code, match.range.first) == 0
    } == 1
}

private fun declarationModifiersBefore(code: String, declarationStart: Int): Set<String> {
    val declarationPrefix = code.substring(0, declarationStart)
    val tokens = kotlinTokens(declarationPrefix)
    var index = tokens.lastIndex
    var chainStart = tokens.size
    val modifiers = mutableSetOf<String>()
    while (index >= 0) {
        val token = tokens[index]
        val beforeAnnotation = annotationStartBefore(tokens, index)
        when {
            beforeAnnotation != null -> {
                chainStart = beforeAnnotation
                index = beforeAnnotation - 1
            }
            token.isPlainIdentifier && token.text in CLASS_DECLARATION_MODIFIERS -> {
                modifiers += token.text
                chainStart = index
                index -= 1
            }
            else -> break
        }
    }
    if (modifiers.isEmpty()) return emptySet()
    val hasDeclarationBoundary = index < 0 || tokens[index].text in setOf(";", "{", "}") ||
        declarationPrefix.substring(tokens[index].endIndex, tokens[chainStart].startIndex).contains('\n')
    return modifiers.takeIf { hasDeclarationBoundary }.orEmpty()
}

private data class KotlinToken(
    val text: String,
    val isPlainIdentifier: Boolean,
    val startIndex: Int,
    val endIndex: Int,
)

private val CLASS_DECLARATION_MODIFIERS = setOf(
    "public",
    "private",
    "internal",
    "protected",
    "expect",
    "actual",
    "final",
    "open",
    "abstract",
    "sealed",
    "inner",
    "value",
    "annotation",
    "enum",
    "inline",
    "external",
    "const",
    "lateinit",
    "override",
)

private fun kotlinTokens(text: String): List<KotlinToken> = buildList {
    var index = 0
    while (index < text.length) {
        val character = text[index]
        when {
            character.isWhitespace() -> index += 1
            character == '`' -> {
                val close = text.indexOf('`', index + 1)
                if (close < 0) {
                    add(KotlinToken(text.substring(index), isPlainIdentifier = false, index, text.length))
                    return@buildList
                }
                add(KotlinToken(text.substring(index, close + 1), isPlainIdentifier = false, index, close + 1))
                index = close + 1
            }
            character == '_' || character.isLetter() -> {
                val end = kotlinIdentifierEnd(text, index) ?: index + 1
                add(KotlinToken(text.substring(index, end), isPlainIdentifier = true, index, end))
                index = end
            }
            else -> {
                add(KotlinToken(character.toString(), isPlainIdentifier = false, index, index + 1))
                index += 1
            }
        }
    }
}

private fun annotationStartBefore(tokens: List<KotlinToken>, endIndex: Int): Int? {
    var index = endIndex
    if (index < 0) return null
    if (tokens[index].text == "]") {
        var depth = 1
        index -= 1
        while (index >= 0 && depth > 0) {
            when (tokens[index].text) {
                "]" -> depth += 1
                "[" -> depth -= 1
            }
            index -= 1
        }
        if (depth != 0) return null
        if (index >= 2 && tokens[index].text == ":" && tokens[index - 1].isPlainIdentifier &&
            tokens[index - 2].text == "@"
        ) {
            return index - 2
        }
        return index.takeIf { candidate -> candidate >= 0 && tokens[candidate].text == "@" }
    }
    if (tokens[index].text == ")") {
        var depth = 1
        index -= 1
        while (index >= 0 && depth > 0) {
            when (tokens[index].text) {
                ")" -> depth += 1
                "(" -> depth -= 1
            }
            index -= 1
        }
        if (depth != 0) return null
    }
    if (index < 0 || !tokens[index].isPlainIdentifier && !tokens[index].text.startsWith('`')) return null
    index -= 1
    while (
        index >= 1 && tokens[index].text == "." &&
        (tokens[index - 1].isPlainIdentifier || tokens[index - 1].text.startsWith('`'))
    ) {
        index -= 2
    }
    if (index >= 1 && tokens[index].text == ":" && tokens[index - 1].isPlainIdentifier) {
        index -= 2
    }
    return index.takeIf { candidate -> candidate >= 0 && tokens[candidate].text == "@" }
}

private fun dataClassHasHeaderSuffix(text: String, typeName: String): Boolean {
    val code = text.maskKotlinNonCode()
    val declaration = topLevelDataClassDeclaration(code, typeName) ?: return false
    val constructorOpen = code.indexOf('(', declaration.range.first)
    val constructorClose = matchingDelimiter(code, constructorOpen, '(', ')') ?: return false
    val next = nextNonWhitespaceIndex(code, constructorClose + 1) ?: return false
    return code[next] == ':' || code.hasKeywordAt(next, "where")
}

private fun nextNonWhitespaceIndex(text: String, startIndex: Int): Int? =
    (startIndex until text.length).firstOrNull { index -> !text[index].isWhitespace() }

private fun nonPrivateTopLevelExtensionProperties(text: String): List<String> {
    val code = text.maskKotlinNonCode()
    return extensionPropertyDeclarations(code)
        .filter { declaration -> curlyDepthAt(code, declaration.startIndex) == 0 }
        .filterNot { declaration ->
            hasDirectSameLinePrivateModifier(code, declaration.startIndex)
        }
        .map { declaration -> "${declaration.keyword} ${declaration.propertyName}" }
}

private fun hasDirectSameLinePrivateModifier(code: String, declarationStart: Int): Boolean {
    val lineBreak = maxOf(
        code.lastIndexOf('\n', startIndex = declarationStart - 1),
        code.lastIndexOf('\r', startIndex = declarationStart - 1),
    )
    val lineStart = lineBreak + 1
    val linePrefix = code.substring(lineStart, declarationStart)
    return "private" in declarationModifiersBefore(linePrefix, linePrefix.length)
}

private fun extraComponentIdentifiers(text: String, maximumOrdinal: Int): List<String> {
    return Regex(KOTLIN_IDENTIFIER_PATTERN).findAll(text).mapNotNull { match ->
        val identifier = normalizeKotlinIdentifier(match.value)
        val suffix = identifier.removePrefix("component")
        identifier.takeIf {
            identifier.startsWith("component") && suffix.isNotEmpty() && suffix.all { digit -> digit in '0'..'9' } &&
                decimalOrdinalExceeds(suffix, maximumOrdinal)
        }
    }.toList()
}

private fun SourceDocument.isProductionMainKotlinSource(): Boolean {
    if (!relativePath.endsWith(".kt")) return false
    val sourceSet = relativePath.substringAfter("/src/", missingDelimiterValue = "")
        .substringBefore('/')
    return sourceSet == "main" || sourceSet.endsWith("Main")
}

private fun decimalOrdinalExceeds(decimal: String, maximum: Int): Boolean {
    val normalized = decimal.dropWhile { digit -> digit == '0' }.ifEmpty { "0" }
    val maximumText = maximum.toString()
    return normalized.length > maximumText.length ||
        normalized.length == maximumText.length && normalized > maximumText
}

private data class ExtensionPropertyDeclaration(
    val startIndex: Int,
    val keyword: String,
    val propertyName: String,
)

private fun extensionPropertyDeclarations(code: String): List<ExtensionPropertyDeclaration> =
    Regex("\\b(val|var)\\b").findAll(code).filterNot { keyword ->
        code.isInsideBacktickIdentifier(keyword.range.first)
    }.mapNotNull { keyword ->
        val head = extensionPropertyHead(code, keyword.range.last + 1)
        val receiverDot = extensionReceiverDot(head)
        if (receiverDot < 0) return@mapNotNull null
        val propertyName = propertyNameAfter(head, receiverDot + 1)
        if (propertyName == "<property>") return@mapNotNull null
        ExtensionPropertyDeclaration(
            startIndex = keyword.range.first,
            keyword = keyword.groupValues[1],
            propertyName = propertyName,
        )
    }.toList()

private fun extensionReceiverDot(head: String): Int =
    topLevelDelimiterIndices(head, '.').firstOrNull { dot ->
        val propertyStart = nextNonWhitespaceIndex(head, dot + 1) ?: return@firstOrNull false
        val propertyEnd = kotlinIdentifierEnd(head, propertyStart) ?: return@firstOrNull false
        val next = nextNonWhitespaceIndex(head, propertyEnd)?.let(head::get)
        val receiverPrefix = head.substring(0, dot)
        val normalizedReceiver = normalizeExtensionReceiverTypeText(receiverPrefix)
        val functionReceiver = hasTopLevelArrow(normalizedReceiver)
        next != '.' && (functionReceiver || directReceiverTypeName(receiverPrefix) != null) &&
            (functionReceiver || !hasTopLevelCharacter(normalizedReceiver, '@'))
    } ?: -1

private fun normalizeExtensionReceiverTypeText(receiverPrefix: String): String {
    var receiver = receiverPrefix.trim()
    if (receiver.startsWith('<')) {
        val parametersEnd = matchingDelimiter(receiver, 0, '<', '>') ?: return receiver
        receiver = receiver.substring(parametersEnd + 1).trim()
    }
    return normalizeDirectTypeText(receiver)
}

private fun hasTopLevelCharacter(text: String, target: Char): Boolean =
    topLevelDelimiterIndices(text, target).isNotEmpty()

private fun extensionPropertyHead(code: String, startIndex: Int): String {
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var index = startIndex
    var backticked = false
    while (index < code.length) {
        val character = code[index]
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1 else return code.substring(startIndex, index)
            character == '<' && roundDepth == 0 && squareDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && code.getOrNull(index - 1) != '-' ->
                if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character == ':' -> if (
                roundDepth == 0 && angleDepth == 0 && squareDepth == 0 &&
                !code.substring(startIndex, index).trimEnd().endsWith("@receiver")
            ) {
                return code.substring(startIndex, index)
            }
            character == '=' || character == '{' || character == '}' || character == ';' -> if (
                roundDepth == 0 && angleDepth == 0 && squareDepth == 0
            ) {
                return code.substring(startIndex, index)
            }
        }
        if (
            !backticked &&
            roundDepth == 0 && angleDepth == 0 && squareDepth == 0 &&
            isPropertySuffixKeyword(code, startIndex, index)
        ) {
            return code.substring(startIndex, index)
        }
        index += 1
    }
    return code.substring(startIndex)
}

private fun isPropertySuffixKeyword(code: String, startIndex: Int, index: Int): Boolean {
    if (!(code.hasKeywordAt(index, "get") || code.hasKeywordAt(index, "set") || code.hasKeywordAt(index, "by"))) {
        return false
    }
    val prefix = code.substring(startIndex, index).trim()
    if (prefix.isEmpty()) return false
    return code.previousNonWhitespaceCharacter(index) !in setOf('@', '.', ':')
}

private fun String.previousNonWhitespaceCharacter(index: Int): Char? =
    (index - 1 downTo 0).firstOrNull { candidate -> !this[candidate].isWhitespace() }?.let(::get)

private fun directReceiverTypeName(receiverPrefix: String): String? {
    var receiver = receiverPrefix.trim()
    if (receiver.startsWith('<')) {
        val typeParametersEnd = matchingDelimiter(receiver, 0, '<', '>') ?: return null
        receiver = receiver.substring(typeParametersEnd + 1).trim()
    }
    receiver = receiver.replace(Regex("\\s+\\?"), "?")
    val directReceiver = lastTopLevelWhitespaceSegment(receiver).trim()
    return definitelyNonNullTypeParameterName(directReceiver) ?: directTypeName(directReceiver)
}

private fun definitelyNonNullTypeParameterName(type: String): String? {
    val normalized = normalizeDirectTypeText(type)
    val intersection = splitAtTopLevel(normalized, '&')
    if (intersection.size != 2 || directTypeName(intersection[1]) == null) return null
    return directTypeName(intersection[0])
}

private fun lastTopLevelWhitespaceSegment(text: String): String {
    var start = 0
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var backticked = false
    text.forEachIndexed { index, character ->
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && text.getOrNull(index - 1) != '-' ->
                if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character.isWhitespace() -> if (roundDepth == 0 && angleDepth == 0 && squareDepth == 0) {
                start = index + 1
            }
        }
    }
    return text.substring(start)
}

private fun acceptedFrameAliases(code: String, typeName: String): Set<String> {
    val normalizedTypeName = normalizeKotlinIdentifier(typeName)
    val aliases = mutableSetOf(normalizedTypeName)
    val canonicalTypePattern =
        "(?:${Regex.escape(typeName)}|`${Regex.escape(typeName)}`)"
    Regex(
        "\\bimport\\s+(?:$KOTLIN_IDENTIFIER_PATTERN\\s*\\.\\s*)*$canonicalTypePattern" +
            "\\s+as\\s+($KOTLIN_IDENTIFIER_PATTERN)",
    ).findAll(code).filterNot { match -> code.isInsideBacktickIdentifier(match.range.first) }.forEach { match ->
        aliases += normalizeKotlinIdentifier(match.groupValues[1])
    }

    val typeAliases = typeAliasDeclarations(code)
    var changed: Boolean
    do {
        changed = false
        typeAliases.forEach { (alias, target) ->
            if (alias !in aliases && directTypeName(target) in aliases) {
                aliases += alias
                changed = true
            }
        }
    } while (changed)
    return aliases
}

private fun acceptedFrameDeclaredTypeAliases(text: String, typeName: String): List<String> {
    val code = text.maskKotlinNonCode()
    val aliases = acceptedFrameAliases(code, typeName)
    return typeAliasDeclarations(code).map(Pair<String, String>::first).filter(aliases::contains)
}

private fun typeAliasDeclarations(code: String): List<Pair<String, String>> =
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

private fun directTypeName(type: String): String? {
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

private fun String.hasKeywordAt(index: Int, keyword: String): Boolean {
    if (!regionMatches(index, keyword, 0, keyword.length)) return false
    if (isInsideBacktickIdentifier(index)) return false
    val before = getOrNull(index - 1)
    val after = getOrNull(index + keyword.length)
    return (before == null || before != '_' && !before.isLetterOrDigit()) &&
        (after == null || after != '_' && !after.isLetterOrDigit())
}

private fun String.isInsideBacktickIdentifier(index: Int): Boolean {
    val lineStart = lastIndexOf('\n', startIndex = index - 1).let { newline -> newline + 1 }
    return substring(lineStart, index).count { character -> character == '`' } % 2 == 1
}

private fun topLevelDataClassDeclaration(code: String, typeName: String): MatchResult? {
    val canonicalName = Regex.escape(typeName)
    val declaration = Regex("\\bdata\\s+class\\s+(?:$canonicalName|`$canonicalName`)\\s*\\(")
    val matches = declaration.findAll(code).filter { match ->
        !code.isInsideBacktickIdentifier(match.range.first) && curlyDepthAt(code, match.range.first) == 0
    }.toList()
    return matches.singleOrNull()
}

private fun curlyDepthAt(text: String, endExclusive: Int): Int {
    var depth = 0
    var backticked = false
    for (index in 0 until endExclusive) {
        when (text[index]) {
            '`' -> backticked = !backticked
            '{' -> if (!backticked) depth += 1
            '}' -> if (!backticked) depth -= 1
        }
    }
    return depth
}

private fun propertyNameAfter(text: String, startIndex: Int): String {
    val start = (startIndex until text.length).firstOrNull { index -> !text[index].isWhitespace() }
        ?: return "<property>"
    if (text[start] == '`') {
        val end = text.indexOf('`', start + 1)
        return if (end >= 0) text.substring(start, end + 1) else "<property>"
    }
    val end = (start until text.length)
        .firstOrNull { index -> text[index] != '_' && !text[index].isLetterOrDigit() }
        ?: text.length
    return text.substring(start, end).ifEmpty { "<property>" }
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

private fun splitAtTopLevel(text: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var curlyDepth = 0
    var backticked = false
    text.forEachIndexed { index, character ->
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> roundDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 &&
                text.getOrNull(index - 1) != '-' -> if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> squareDepth -= 1
            character == '{' -> curlyDepth += 1
            character == '}' -> curlyDepth -= 1
            character == delimiter -> if (
                roundDepth == 0 && angleDepth == 0 && squareDepth == 0 && curlyDepth == 0
            ) {
                parts += text.substring(start, index)
                start = index + 1
            }
        }
    }
    parts += text.substring(start)
    return parts
}

private fun substringBeforeTopLevel(text: String, delimiter: Char): String =
    splitAtTopLevel(text, delimiter).first()

private fun hasTopLevelDelimiter(text: String, delimiter: Char): Boolean =
    splitAtTopLevel(text, delimiter).size > 1

private fun lastTopLevelDelimiter(text: String, delimiter: Char): Int {
    var result = -1
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var curlyDepth = 0
    var backticked = false
    text.forEachIndexed { index, character ->
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> roundDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 &&
                text.getOrNull(index - 1) != '-' -> if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> squareDepth -= 1
            character == '{' -> curlyDepth += 1
            character == '}' -> curlyDepth -= 1
            character == delimiter -> if (
                roundDepth == 0 && angleDepth == 0 && squareDepth == 0 && curlyDepth == 0
            ) {
                result = index
            }
        }
    }
    return result
}

private fun topLevelDelimiterIndices(text: String, delimiter: Char): List<Int> = buildList {
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var curlyDepth = 0
    var backticked = false
    text.forEachIndexed { index, character ->
        when {
            character == '`' -> backticked = !backticked
            backticked -> Unit
            character == '(' -> roundDepth += 1
            character == ')' -> if (roundDepth > 0) roundDepth -= 1
            character == '<' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 -> angleDepth += 1
            character == '>' && roundDepth == 0 && squareDepth == 0 && curlyDepth == 0 &&
                text.getOrNull(index - 1) != '-' -> if (angleDepth > 0) angleDepth -= 1
            character == '[' -> squareDepth += 1
            character == ']' -> if (squareDepth > 0) squareDepth -= 1
            character == '{' -> curlyDepth += 1
            character == '}' -> if (curlyDepth > 0) curlyDepth -= 1
            character == delimiter &&
                roundDepth == 0 && angleDepth == 0 && squareDepth == 0 && curlyDepth == 0 -> add(index)
        }
    }
}

private fun dataClassNames(text: String): List<String> =
    Regex("\\bdata\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        .findAll(text.maskKotlinNonCode())
        .map { match -> match.groupValues[1] }
        .toList()

private fun enumEntries(text: String, typeName: String): List<String>? {
    val body = typeDeclarationBody(text, "enum class $typeName") ?: return null
    return splitAtTopLevel(body.substringBefore(';'), ',')
        .map { entry -> entry.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "") }
        .map { entry -> entry.replace(Regex("//[^\\n]*"), "").trim() }
        .filter(String::isNotEmpty)
        .mapNotNull { entry -> Regex("^([A-Z][A-Z0-9_]*)\\b").find(entry)?.groupValues?.get(1) }
}

private fun importedTypes(text: String): Set<String> =
    Regex("(?m)^import\\s+([A-Za-z_][A-Za-z0-9_.]*[A-Za-z0-9_])(?:\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*)?\\s*$")
        .findAll(text)
        .map { match -> match.groupValues[1] }
        .toSet()

private fun referencedSurfaceTypes(text: String): Set<String> = importedTypes(text) +
    Regex(
        "\\bkinetickk\\.(?:ball|flow|resource)\\.[a-z][A-Za-z0-9_.]*\\.[A-Z][A-Za-z0-9_]*\\b",
    ).findAll(text).map { match -> match.value }.toSet()

private fun typeDeclarationBody(text: String, declaration: String): String? {
    val code = text.maskKotlinNonCode()
    val declarationIndices = generateSequence(code.indexOf(declaration).takeIf { it >= 0 }) { previous ->
        code.indexOf(declaration, previous + declaration.length).takeIf { it >= 0 }
    }.filter { index ->
        !code.isInsideBacktickIdentifier(index) && curlyDepthAt(code, index) == 0
    }.toList()
    val declarationIndex = declarationIndices.singleOrNull() ?: return null
    val open = (declarationIndex + declaration.length until code.length).firstOrNull { index ->
        code[index] == '{' && !code.isInsideBacktickIdentifier(index)
    } ?: return null
    val close = matchingDelimiter(code, open, '{', '}') ?: return null
    return code.substring(open + 1, close)
}

private fun String.maskKotlinNonCode(): String {
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

private fun directSubtypeNames(body: String, supertype: String): Set<String> {
    val declarations = Regex(
        "(?m)^\\s*(?:(?:public|internal|private|protected)\\s+)?" +
            "(?:(?:data|value|enum|sealed)\\s+)?(?:class|object|interface)\\s+([A-Za-z0-9_]+)\\b",
    ).findAll(body).toList()
    return declarations.mapIndexedNotNull { index, match ->
        val end = declarations.getOrNull(index + 1)?.range?.first ?: body.length
        val segment = body.substring(match.range.first, end)
        match.groupValues[1].takeIf {
            Regex(":\\s*${Regex.escape(supertype)}\\b").containsMatchIn(segment)
        }
    }.toSet()
}
