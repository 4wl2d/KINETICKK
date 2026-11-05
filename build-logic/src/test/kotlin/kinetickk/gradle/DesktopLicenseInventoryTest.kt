// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLicenseInventoryTest {
    @Test
    fun licenseEntryNamesAcceptPackagedNoticesAndRejectTraversal() {
        assertTrue(isShippedLicenseEntry("META-INF/LICENSE"))
        assertTrue(isShippedLicenseEntry("license/LICENSE.txt"))
        assertTrue(isShippedLicenseEntry("META-INF/NOTICE.txt"))
        assertTrue(isShippedLicenseEntry("META-INF/AL2.0"))
        assertFalse(isShippedLicenseEntry("../LICENSE"))
        assertFalse(isShippedLicenseEntry("META-INF/MANIFEST.MF"))
        assertFalse(isShippedLicenseEntry("META-INF/LICENSE/"))
    }

    @Test
    fun pomLicenseMappingAcceptsOnlyTheNamedApacheLicense() {
        val xml = """
            <licenses><license><name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses>
        """.trimIndent()
        val licenses = pomLicenses(xml)
        assertEquals("Apache-2.0", standardLicenseId(licenses.single()))
        assertEquals(
            listOf(
                "https://repo1.maven.org/maven2/androidx/arch/core/core-common/2.2.0/core-common-2.2.0.pom",
                "https://dl.google.com/dl/android/maven2/androidx/arch/core/core-common/2.2.0/core-common-2.2.0.pom",
            ),
            mavenPomUrls("androidx.arch.core:core-common:2.2.0"),
        )
        assertEquals(emptyList(), mavenPomUrls("../secret:core:1"))
        assertEquals(null, standardLicenseId(PomLicense("GPL-3.0", "https://www.gnu.org/licenses/gpl-3.0.txt")))
        assertEquals(
            "Apache-2.0",
            standardLicenseId(PomLicense("The Apache License, Version 2.0", "https://www.apache.org/licenses/LICENSE-2.0")),
        )
    }

    fun licenseDirectoryNameKeepsCoordinatesReadable() {
        assertEquals(
            "org.jetbrains.skiko_skiko-awt-runtime-windows-x64_0.150.1",
            licenseDirectoryName("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1"),
        )
    }

    @Test
    fun jarLicenseExtractionReadsOnlyLicenseEntries() {
        val jar = Files.createTempFile("license-inventory", ".jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/LICENSE"))
            zip.write("Apache License".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("game/Main.class"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        val entries = ZipFile(jar.toFile()).use(::licenseEntries)
        assertEquals(listOf("META-INF/LICENSE"), entries.map { it.name })
        assertEquals("Apache License", entries.single().bytes.decodeToString())
    }
}
