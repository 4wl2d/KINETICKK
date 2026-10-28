// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

/** The leaf modules `verifyArchitecture` expects; adding or removing a module updates this list. */
internal val expectedLeafProjects: Set<String> = sortedSetOf(
    ":app:android",
    ":app:desktop",
    ":app:shared",
    ":app:web",
    ":ball:content:api",
    ":ball:content:impl",
    ":ball:gameplay:api",
    ":ball:gameplay:impl",
    ":ball:gameplay:interaction",
    ":ball:gameplay:nucleus",
    ":ball:profile:api",
    ":ball:profile:impl",
    ":ball:profile:interaction",
    ":ball:profile:nucleus",
    ":ball:profile:resource",
    ":flow:session:api",
    ":flow:session:impl",
    ":flow:session:interaction",
    ":flow:session:nucleus",
    ":foundation:common",
    ":foundation:design",
    ":resource:audio:api",
    ":resource:audio:impl",
)

/** One declared `ProjectDependency`: [source] depends on [target] through [configuration]. */
internal data class ProjectEdge(
    val source: String,
    val configuration: String,
    val target: String,
) {
    val isTest: Boolean
        get() = configuration.contains("test", ignoreCase = true)

    companion object {
        fun decode(encoded: String): ProjectEdge {
            val parts = encoded.split('\t', limit = 3)
            require(parts.size == 3) { "Malformed project dependency edge: $encoded" }
            return ProjectEdge(parts[0], parts[1], parts[2])
        }
    }
}
