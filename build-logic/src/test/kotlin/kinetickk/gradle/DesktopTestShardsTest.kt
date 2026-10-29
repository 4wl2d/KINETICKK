// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopTestShardsTest {
    @Test
    fun shardPropertyAcceptsOnlyTheShardIds() {
        DesktopTestShard.entries.forEach { shard -> assertEquals(shard, DesktopTestShard.parse(shard.id)) }

        listOf("", "REST", "Rest", " rest", "gates", "all").forEach { value ->
            assertFailsWith<GradleException>(value) { DesktopTestShard.parse(value) }
        }
    }

    @Test
    fun shardListsNameDisjointExactClasses() {
        val listed = DesktopTestShard.entries.flatMap(DesktopTestShard::testClasses)
        assertEquals(listed.distinct(), listed, "a class is listed twice")
        listed.forEach { name ->
            // Gradle matches a pattern without wildcards as the exact class name.
            assertTrue(Regex("""[a-z][\w.]*\.[A-Z]\w*""").matches(name), "'$name' is not a top-level class name")
        }
        assertEquals(emptyList(), DesktopTestShard.REST.testClasses)
        assertEquals(null, DesktopTestShard.REST.projectPath)
        DesktopTestShard.entries.filter { it != DesktopTestShard.REST }.forEach { shard ->
            assertTrue(shard.testClasses.isNotEmpty(), "${shard.id} lists no class")
            assertTrue(shard.projectPath?.startsWith(":") == true, "${shard.id} names no project")
        }
    }

    @Test
    fun everyListedClassIsADesktopTestClassOfItsShardsProject() {
        // A renamed or deleted gate would leave its shard empty and move its time to the rest; a class of another
        // project would run nowhere, since a gate shard's job runs only its project's desktopTest.
        DesktopTestShard.entries.filter { it != DesktopTestShard.REST }.forEach { shard ->
            val projectDirectory = shard.projectPath!!.removePrefix(":").replace(':', '/') + "/src/"
            shard.testClasses.forEach { testClass ->
                val source = desktopTestSources[testClass]
                assertTrue(source != null, "${shard.id} lists $testClass, which is no desktop test class of the tree")
                assertTrue(source.startsWith(projectDirectory), "${shard.id} lists $testClass from $source")
            }
        }
    }

    @Test
    fun ciRunsEveryShardWithItsTasks() {
        val workflow = repository.resolve(".github/workflows/ci.yml").readLines()
        val jobs = workflow.indices.mapNotNull { index ->
            val shard = Regex("""^\s+- shard: ([\w-]+)$""").find(workflow[index])?.groupValues?.get(1)
            val tasks = workflow.getOrNull(index + 1)?.let { Regex("""^\s+tasks: "(.+)"$""").find(it) }
            shard?.let { it to tasks?.groupValues?.get(1)?.split(' ') }
        }.toMap()

        assertEquals(DesktopTestShard.entries.map(DesktopTestShard::id).toSet(), jobs.keys, "ci.yml's shards")
        DesktopTestShard.entries.forEach { shard ->
            val expected = shard.projectPath?.let { "$it:desktopTest" } ?: "desktopTest"
            assertEquals(true, jobs[shard.id]?.contains(expected), "ci.yml runs ${shard.id} without $expected")
        }
        assertEquals(true, jobs[DesktopTestShard.REST.id]?.containsAll(listOf(":build-logic:test", "verifyArchitecture")))
    }

    @Test
    fun everyDesktopTestClassRunsInExactlyOneShard() {
        assertTrue(desktopTestClasses.isNotEmpty(), "no desktop test class under ${repository.path}")
        val notExactlyOnce = desktopTestClasses.associateWith { testClass ->
            DesktopTestShard.entries.filter { it.selects(testClass) }.map(DesktopTestShard::id)
        }.filterValues { it.size != 1 }
        assertEquals(emptyMap(), notExactlyOnce, "classes not run by exactly one shard")

        val union = DesktopTestShard.entries.flatMap { shard -> desktopTestClasses.filter(shard::selects) }
        assertEquals(desktopTestClasses.size, union.size)
        assertEquals(desktopTestClasses, union.toSet())
        desktopTestClasses.forEach { testClass -> assertTrue(DesktopTestShard.of(testClass).selects(testClass)) }
    }

    @Test
    fun scannerFindsTopLevelClassesThatDeclareTests() {
        val source = """
            |package a.b
            |
            |import kotlin.test.Test
            |
            |/** A KDoc line. */
            |class FirstTest {
            |    @Test
            |    fun one() {}
            |}
            |
            |private class Helper(val x: Int) {
            |    fun notATest() {}
            |}
            |
            |internal fun helper() = Unit
            |
            |internal class SecondTest(private val value: Int = 1) {
            |    @Test fun two() {}
            |}
        """.trimMargin()

        assertEquals(setOf("a.b.FirstTest", "a.b.SecondTest"), testClassesOf(source))
    }

    private companion object {
        /** build-logic's tests run in build-logic/, one level below the repository. */
        val repository: File = File("..").canonicalFile

        /**
         * Every top-level class with a test in a desktopTest or commonTest source set (what `desktopTest` runs), with
         * its source file relative to the repository.
         */
        val desktopTestSources: Map<String, String> by lazy {
            repository.walkTopDown()
                .onEnter { it.name !in SKIPPED_DIRECTORIES }
                .filter { file ->
                    file.isFile && file.extension == "kt" &&
                        TEST_SOURCE_ROOTS.any { root -> root in file.invariantSeparatorsPath }
                }
                .flatMap { file ->
                    val path = file.relativeTo(repository).invariantSeparatorsPath
                    testClassesOf(file.readText()).asSequence().map { it to path }
                }
                .toMap()
        }

        val desktopTestClasses: Set<String> by lazy { desktopTestSources.keys }

        val TEST_SOURCE_ROOTS = listOf("/src/commonTest/kotlin/", "/src/desktopTest/kotlin/")
        val SKIPPED_DIRECTORIES = setOf(".git", ".gradle", ".claude", ".kotlin", "build", "build-logic", "node_modules")

        private val packageLine = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE)
        private val topLevelClass = Regex("""^(?:(?:public|internal|private|abstract|open|data)\s+)*class\s+(\w+)""")
        private val testAnnotation = Regex("""^\s+@Test\b""")

        /**
         * The top-level classes of [source] that declare a `@Test` method. The tree has no test base classes, suites
         * or nested test classes, and Kotlin's official style starts every top-level declaration in column 0.
         */
        fun testClassesOf(source: String): Set<String> {
            val packageName = packageLine.find(source)?.groupValues?.get(1) ?: return emptySet()
            val classes = mutableSetOf<String>()
            var current: String? = null
            for (line in source.lineSequence()) {
                val declaration = topLevelClass.find(line)
                if (declaration != null) {
                    current = declaration.groupValues[1]
                } else if (line.isNotEmpty() && line[0].isLetter()) {
                    current = null
                }
                if (current != null && testAnnotation.containsMatchIn(line)) classes += "$packageName.$current"
            }
            return classes
        }
    }
}
