// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

internal fun <T, K> requireUniqueKeys(
    label: String,
    values: List<T>,
    key: (T) -> K,
) {
    val duplicates = values.groupingBy(key).eachCount().filterValues { count -> count > 1 }.keys
    require(duplicates.isEmpty()) { "$label contains duplicate keys: ${duplicates.joinToString()}" }
}

internal fun <K, V> uniqueLinkedMap(
    label: String,
    entries: List<Pair<K, V>>,
): LinkedHashMap<K, V> {
    requireUniqueKeys(label, entries, Pair<K, V>::first)
    return LinkedHashMap<K, V>().apply { entries.forEach { (key, value) -> put(key, value) } }
}

/**
 * The closed physical ownership inventory. Leaf, ownership and internal-package checks all
 * project this declaration so adding a module cannot omit one of those boundaries.
 */
internal val authorityModules = uniqueLinkedMap("authorityModules", listOf(
    "AppAssembly" to listOf(":app:android", ":app:desktop", ":app:shared", ":app:web"),
    "AppSession" to listOf(
        ":flow:session:api",
        ":flow:session:nucleus",
        ":flow:session:interaction",
        ":flow:session:impl",
    ),
    "GameplayRun" to listOf(
        ":ball:gameplay:api",
        ":ball:gameplay:nucleus",
        ":ball:gameplay:interaction",
        ":ball:gameplay:impl",
    ),
    "Profile" to listOf(
        ":ball:profile:api",
        ":ball:profile:nucleus",
        ":ball:profile:resource",
        ":ball:profile:interaction",
        ":ball:profile:impl",
    ),
    "ContentCatalog" to listOf(":ball:content:api", ":ball:content:impl"),
    "AudioResource" to listOf(":resource:audio:api", ":resource:audio:impl"),
    "Foundation" to listOf(":foundation:common", ":foundation:design"),
))

internal fun projectAuthorities(modulesByAuthority: Map<String, List<String>>): Map<String, String> =
    uniqueLinkedMap(
        "project authorities",
        modulesByAuthority.flatMap { (authority, modules) ->
            require(authority.isNotBlank()) { "Project authority must not be blank" }
            require(modules.isNotEmpty()) { "Authority $authority must own at least one module" }
            modules.map { module ->
                require(Regex(":[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)+").matches(module)) {
                    "Invalid leaf module path: $module"
                }
                module to authority
            }
        },
    )

private val authoritiesByProject = projectAuthorities(authorityModules)

internal val expectedLeafProjects = authoritiesByProject.keys.toSortedSet()

internal val applicationSurfaces = uniqueLinkedMap(
    "applicationSurfaces",
    listOf("ContentCatalog", "Profile", "GameplayRun", "AppSession").map { authority ->
        authority to authorityModules.getValue(authority).single { module -> module.endsWith(":api") }
    },
)

internal val internalProjectPackages = expectedLeafProjects
    .filter { module ->
        module.startsWith(":ball:") || module.startsWith(":flow:") || module.startsWith(":resource:")
    }
    .filterNot { module -> module.endsWith(":api") }
    .associateWith { module -> "kinetickk" + module.replace(':', '.') }

internal fun authorityFor(projectPath: String): String =
    authoritiesByProject[projectPath] ?: error("Unknown project authority: $projectPath")
