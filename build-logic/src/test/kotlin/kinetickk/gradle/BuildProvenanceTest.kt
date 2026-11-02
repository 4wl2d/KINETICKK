// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildProvenanceTest {
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
}
