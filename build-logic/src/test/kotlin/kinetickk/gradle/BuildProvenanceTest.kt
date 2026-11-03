// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class BuildProvenanceTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun macPackagingVersionKeepsGameVersionOrderWithoutZeroMajor() {
        assertEquals("1.6.0", macOsPackageVersion("0.6.0"))
        assertEquals("2.0.0", macOsPackageVersion("1.0.0"))
        assertFailsWith<IllegalArgumentException> { macOsPackageVersion("0.6.0-beta") }
    }

    @Test
    fun propertyEncodingIsOrderedAndRoundTripsNonAsciiAndNewlines() {
        val first = Properties().apply {
            setProperty("z", "Путь\\с пробелом\nsecond line: =")
            setProperty(" a", " leading space")
        }
        val reversed = Properties().apply {
            setProperty(" a", " leading space")
            setProperty("z", "Путь\\с пробелом\nsecond line: =")
        }
        val bytes = deterministicProperties(first)
        assertContentEquals(bytes, deterministicProperties(reversed))
        assertEquals(first, Properties().apply { bytes.inputStream().use(::load) })
    }

    @Test
    fun sourceFingerprintDependsOnRelativePathsAndContentOnly() {
        fun fingerprint(checkout: String, content: String, reversed: Boolean = false): String {
            val root = temporaryDirectory.resolve(checkout)
            val files = listOf("a/src/A.kt" to content, "b/src/B.kt" to "class B").map { (path, text) ->
                root.resolve(path).apply { parentFile.mkdirs(); writeText(text) }
            }
            return sourceSha256(root, if (reversed) files.reversed() else files)
        }

        val original = fingerprint("first", "class A")
        assertEquals(original, fingerprint("second/nested", "class A", reversed = true))
        assertNotEquals(original, fingerprint("third", "class A2"))
    }
}
