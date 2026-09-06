// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.localization

/** An exhaustive pair of translations. Language is supplied by the caller, never global state. */
interface TextResource {
    val english: String
    val russian: String
}

/** Substitutes positional arguments once, so inserted user text is never interpreted as a template. */
fun AppLanguage.text(resource: TextResource, vararg arguments: Any): String {
    val template = when (this) {
        AppLanguage.English -> resource.english
        AppLanguage.Russian -> resource.russian
    }
    if (arguments.isEmpty()) return template
    return TextArgument.replace(template) { match ->
        arguments.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
    }
}

private val TextArgument = Regex("\\{([0-9]+)\\}")
