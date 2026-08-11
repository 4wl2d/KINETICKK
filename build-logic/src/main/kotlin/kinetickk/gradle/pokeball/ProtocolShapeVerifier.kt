// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

internal data class CanonicalDataClassShape(
    val path: String,
    val typeName: String,
    val fields: List<CanonicalFieldShape>,
    val withinDeclaration: String? = null,
)

internal data class CanonicalFieldShape(
    val name: String,
    val type: String,
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
        val actualFields = primaryConstructorFields(scope, shape.typeName)
        when {
            actualFields == null -> add(
                "Canonical protocol shape ${shape.typeName} is not declared as a data class in ${shape.path}",
            )
            actualFields != shape.fields -> add(
                "Canonical protocol shape ${shape.typeName} fields must be exactly " +
                    "${shape.fields.joinToString(transform = CanonicalFieldShape::render)}; found " +
                    actualFields.joinToString(transform = CanonicalFieldShape::render),
            )
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

private fun CanonicalFieldShape.render(): String = "$name: $type"

private fun primaryConstructorFields(text: String, typeName: String): List<CanonicalFieldShape>? =
    primaryConstructor(text, typeName)?.let(::constructorFields)

private fun primaryConstructor(text: String, typeName: String): String? {
    val declaration = Regex("\\bdata\\s+class\\s+${Regex.escape(typeName)}\\s*\\(").find(text) ?: return null
    val open = text.indexOf('(', declaration.range.first)
    var depth = 0
    var index = open
    while (index < text.length) {
        when (text[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return text.substring(open + 1, index)
            }
        }
        index += 1
    }
    return null
}

private fun constructorFields(constructor: String): List<CanonicalFieldShape> =
    splitAtTopLevel(constructor, ',').mapNotNull { parameter ->
        val match = Regex("\\b(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
            .find(parameter)
            ?: return@mapNotNull null
        CanonicalFieldShape(
            name = match.groupValues[1],
            type = substringBeforeTopLevel(match.groupValues[2], '=').trim().replace(Regex("\\s+"), " "),
        )
    }

private fun splitAtTopLevel(text: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    var roundDepth = 0
    var angleDepth = 0
    var squareDepth = 0
    var curlyDepth = 0
    text.forEachIndexed { index, character ->
        when (character) {
            '(' -> roundDepth += 1
            ')' -> roundDepth -= 1
            '<' -> angleDepth += 1
            '>' -> if (angleDepth > 0) angleDepth -= 1
            '[' -> squareDepth += 1
            ']' -> squareDepth -= 1
            '{' -> curlyDepth += 1
            '}' -> curlyDepth -= 1
            delimiter -> if (roundDepth == 0 && angleDepth == 0 && squareDepth == 0 && curlyDepth == 0) {
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

private fun dataClassNames(text: String): List<String> =
    Regex("\\bdata\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        .findAll(text)
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
    val declarationIndex = text.indexOf(declaration)
    if (declarationIndex < 0) return null
    val open = text.indexOf('{', declarationIndex + declaration.length)
    if (open < 0) return null
    var depth = 1
    for (index in open + 1 until text.length) {
        when (text[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return text.substring(open + 1, index)
            }
        }
    }
    return null
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
