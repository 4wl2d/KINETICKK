// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal fun isShippedLicenseEntry(name: String): Boolean {
    val path = name.replace('\\', '/')
    if (path.isEmpty() || path.endsWith("/") || path.startsWith("/") || path.split('/').any { it == ".." || it == "." }) {
        return false
    }
    val file = path.substringAfterLast('/').lowercase()
    val inLicenseDirectory = path.startsWith("license/") ||
        path.startsWith("licenses/") ||
        path.startsWith("META-INF/license/") ||
        path.startsWith("META-INF/licenses/")
    return file == "license" ||
        file.startsWith("license.") ||
        file == "notice" ||
        file.startsWith("notice.") ||
        file == "al2.0" ||
        file == "lgpl2.1" ||
        file.endsWith("-license.txt") ||
        file.endsWith("-notice.txt") ||
        inLicenseDirectory ||
        (path.startsWith("META-INF/") && (file.contains("license") || file.contains("notice")))
}

internal data class PomLicense(val name: String, val url: String)

internal fun mavenPomUrls(coordinates: String): List<String> {
    val parts = coordinates.split(':')
    if (parts.size != 3 || parts.any { it.isBlank() || it.contains("..") || !it.matches(Regex("[A-Za-z0-9._+-]+")) }) {
        return emptyList()
    }
    val (group, name, version) = parts
    val prefix = "${group.replace('.', '/')}/$name/$version/$name-$version.pom"
    return listOf(
        "https://repo1.maven.org/maven2/$prefix",
        "https://dl.google.com/dl/android/maven2/$prefix",
    )
}

internal fun pomLicenses(xml: String): List<PomLicense> =
    Regex("(?s)<license>(.*?)</license>").findAll(xml).map { match ->
        val block = match.groupValues[1]
        PomLicense(
            name = Regex("(?s)<name>(.*?)</name>").find(block)?.groupValues?.get(1)?.trim().orEmpty(),
            url = Regex("(?s)<url>(.*?)</url>").find(block)?.groupValues?.get(1)?.trim().orEmpty(),
        )
    }.filter { it.name.isNotEmpty() || it.url.isNotEmpty() }.toList()

internal fun readPom(url: String): String? {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", "KINETICKK-license-inventory")
    }
    try {
        return when (connection.responseCode) {
            HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
            HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw GradleException("POM request $url returned ${connection.responseCode}.")
        }
    } finally {
        connection.disconnect()
    }
}

internal fun standardLicenseId(license: PomLicense): String? {
    val url = license.url.trim().removeSuffix("/").lowercase()
    val name = license.name.trim().lowercase()
    val apache = url == "http://www.apache.org/licenses/license-2.0.txt" ||
        url == "https://www.apache.org/licenses/license-2.0.txt" ||
        name == "the apache software license, version 2.0" ||
        name == "the apache license, version 2.0" ||
        name == "apache license, version 2.0" ||
        name == "apache license 2.0" ||
        name == "apache-2.0" ||
        url == "http://www.apache.org/licenses/license-2.0" ||
        url == "https://www.apache.org/licenses/license-2.0"
    return if (apache) "Apache-2.0" else null
}

internal fun licenseDirectoryName(coordinates: String): String =
    coordinates.map { char ->
        if (char.isLetterOrDigit() || char == '.' || char == '_' || char == '-') char else '_'
    }.joinToString("").trim('_').ifEmpty { "unnamed-component" }

internal data class ShippedLicenseEntry(val name: String, val bytes: ByteArray)

internal fun licenseEntries(zip: ZipFile): List<ShippedLicenseEntry> =
    zip.entries().asSequence()
        .filter { entry -> !entry.isDirectory && isShippedLicenseEntry(entry.name) }
        .sortedBy { it.name.replace('\\', '/') }
        .map { entry ->
            if (entry.size > 1_048_576) {
                throw GradleException("License entry ${entry.name} in ${zip.name} is larger than 1 MiB.")
            }
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw GradleException("License entry ${entry.name} in ${zip.name} is empty.")
            }
            ShippedLicenseEntry(entry.name.replace('\\', '/'), bytes)
        }
        .toList()

private val requiredJdkLegalPaths = listOf(
    "legal/java.base",
    "legal/java.desktop",
    "legal/java.prefs",
    "legal/java.datatransfer",
    "legal/jdk.unsupported",
)

@DisableCachingByDefault(because = "Reads the local dependency cache and packaging JDK")
abstract class CollectDesktopLicensesTask : DefaultTask() {
    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jdkHome: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectLegalFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val standardLicenseTexts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        val output = outputDirectory.get().asFile
        if (output.exists()) output.deleteRecursively()
        output.mkdirs()
        val index = StringBuilder()
        index.append("# Desktop license inventory\n\n")
        index.append("Generated from the resolved desktop runtime classpath and the packaging JDK.\n\n")

        val missing = mutableListOf<String>()
        artifacts.get().forEach { record ->
            val parts = record.split('\t')
            if (parts.size != 3) throw GradleException("Bad license artifact record '$record'.")
            val (kind, coordinates, path) = parts
            val file = File(path)
            if (!file.isFile) throw GradleException("Runtime artifact $coordinates is missing: $path")
            val directory = output.resolve("components").resolve(licenseDirectoryName(coordinates))
            directory.mkdirs()
            if (kind == "project") {
                projectLegalFiles.files.sortedBy { it.name }.forEach { legal ->
                    legal.copyTo(directory.resolve(legal.name), overwrite = true)
                }
                index.append("- $coordinates: project material, GPL-3.0-or-later plus the packaged project notices\n")
                return@forEach
            }
            val entries = try {
                ZipFile(file).use(::licenseEntries)
            } catch (error: ZipException) {
                throw GradleException("Runtime artifact $coordinates is not a zip: ${file.name}", error)
            }
            if (entries.isNotEmpty()) {
                entries.forEach { entry ->
                    val target = directory.resolve(entry.name.substringAfterLast('/'))
                    if (target.exists()) {
                        throw GradleException("Duplicate license file name ${target.name} in $coordinates.")
                    }
                    target.writeBytes(entry.bytes)
                }
                copySkiaLicense(coordinates, directory)
                index.append("- $coordinates: ${entries.joinToString(", ") { it.name }}\n")
                return@forEach
            }
            val packaged = packagePomLicense(coordinates, directory)
            if (packaged == null) {
                missing += "$coordinates (${file.name})"
            } else {
                index.append("- $coordinates: POM ${packaged.first} names ${packaged.second}; packaged ${packaged.third}\n")
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "No license text for:\n" + missing.joinToString("\n") +
                    "\nA desktop package cannot ship until each artifact's license text is packaged.",
            )
        }

        val jdk = jdkHome.get().asFile
        val jdkOut = output.resolve("jdk")
        val rootNotices = listOf("LICENSE", "NOTICE").map { jdk.resolve(it) }.filter { it.isFile }
        if (rootNotices.isEmpty()) {
            throw GradleException("Packaging JDK ${jdk.absolutePath} has neither LICENSE nor NOTICE.")
        }
        rootNotices.forEach { notice -> notice.copyTo(jdkOut.resolve(notice.name), overwrite = true) }
        requiredJdkLegalPaths.forEach { relative ->
            val source = jdk.resolve(relative)
            if (!source.exists()) {
                throw GradleException("Packaging JDK ${jdk.absolutePath} has no $relative.")
            }
            val target = jdkOut.resolve(relative)
            target.parentFile.mkdirs()
            source.copyRecursively(target, overwrite = true)
        }
        index.append(
            "- packaging JDK ${jdk.absolutePath}: " +
                "${rootNotices.joinToString(", ") { it.name }}, ${requiredJdkLegalPaths.joinToString(", ")}\n",
        )
        output.resolve("INDEX.md").writeText(index.toString())
    }

    private fun packagePomLicense(coordinates: String, directory: File): Triple<String, String, String>? {
        val urls = mavenPomUrls(coordinates)
        if (urls.isEmpty()) return null
        val fetched = urls.firstNotNullOfOrNull { url -> readPom(url)?.let { url to it } } ?: return null
        val (url, xml) = fetched
        val licenses = pomLicenses(xml)
        if (licenses.isEmpty()) return null
        val ids = licenses.map { license ->
            standardLicenseId(license) ?: return null
        }
        ids.distinct().forEach { id ->
            val text = standardLicenseTexts.files.singleOrNull { it.name == "$id.txt" }
                ?: throw GradleException("Missing vendored license text $id.txt.")
            text.copyTo(directory.resolve("$id.txt"), overwrite = true)
        }
        directory.resolve("POM-LICENSE.txt").writeText(
            licenses.joinToString("\n") { "${it.name} <${it.url}>" } + "\nPOM: $url\n",
        )
        val skia = if (coordinates.startsWith("org.jetbrains.skiko:skiko-awt-runtime-")) ", Skia-LICENSE.txt" else ""
        copySkiaLicense(coordinates, directory)
        return Triple(url, licenses.joinToString("; ") { it.name }, ids.distinct().joinToString(", ") + skia)
    }

    private fun copySkiaLicense(coordinates: String, directory: File) {
        if (!coordinates.startsWith("org.jetbrains.skiko:skiko-awt-runtime-")) return
        val text = standardLicenseTexts.files.singleOrNull { it.name == "Skia-LICENSE.txt" }
            ?: throw GradleException("Missing vendored Skia-LICENSE.txt for $coordinates.")
        text.copyTo(directory.resolve("Skia-LICENSE.txt"), overwrite = true)
    }
}

@DisableCachingByDefault(because = "Packages a local app image and the exact Git commit")
abstract class PackageSteamAppImageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licensesDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distributableRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val gameVersion: Property<String>

    @get:Input
    abstract val gitRevision: Property<String>

    @get:OutputFile
    abstract val zipFile: RegularFileProperty

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun packageImage() {
        val repository = repositoryDirectory.get().asFile
        val status = git(repository, "status", "--porcelain=v1", "--untracked-files=normal")
        if (status.isNotBlank()) {
            throw GradleException("Steam package requires a clean Git tree. Uncommitted files:\n$status")
        }
        val revision = git(repository, "rev-parse", "HEAD").trim()
        if (revision != gitRevision.get().trim()) {
            throw GradleException("Git HEAD $revision does not match the configured revision ${gitRevision.get().trim()}.")
        }
        val image = findAppImage(distributableRoot.get().asFile)
        val licenses = image.resolve("LICENSES")
        if (licenses.exists()) licenses.deleteRecursively()
        licensesDirectory.get().asFile.copyRecursively(licenses, overwrite = true)
        val source = image.resolve("corresponding-source.tar.gz")
        val archived = gitBytes(repository, "archive", "--format=tar.gz", "HEAD")
        source.writeBytes(archived)
        image.resolve("PACKAGE.txt").writeText(
            """
            KINETICKK $revision
            Game version: ${gameVersion.get()}
            This is a Steam candidate app image, not a signed release tag.
            The Steamworks SDK is not linked into this binary.
            Corresponding source for this exact commit is corresponding-source.tar.gz.
            Third-party license texts are in LICENSES/.
            """.trimIndent() + "\n",
        )
        val zip = zipFile.get().asFile
        zip.parentFile.mkdirs()
        if (zip.exists()) zip.delete()
        ZipOutputStream(zip.outputStream().buffered()).use { output ->
            image.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(image).path.replace(File.separatorChar, '/')
                val entry = ZipEntry(name)
                output.putNextEntry(entry)
                file.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
        }
        checksumFile.get().asFile.writeText(sha256(zip.toPath()) + "  ${zip.name}\n")
    }
}

internal fun findAppImage(root: File): File {
    if (!root.isDirectory) throw GradleException("Desktop distributable root does not exist: ${root.absolutePath}")
    val images = root.walkTopDown().filter { directory ->
        directory.isDirectory && (directory.resolve("KINETICKK.exe").isFile || directory.resolve("KINETICKK").isFile)
    }.toList()
    if (images.size != 1) {
        throw GradleException(
            "Expected one KINETICKK app image under ${root.absolutePath}, found ${images.size}: " +
                images.joinToString(", ") { it.absolutePath },
        )
    }
    return images.single()
}

private fun git(directory: File, vararg args: String): String =
    gitBytes(directory, *args).decodeToString()

private fun gitBytes(directory: File, vararg args: String): ByteArray {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(directory)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readBytes()
    val exit = process.waitFor()
    if (exit != 0) {
        throw GradleException("git ${args.joinToString(" ")} failed ($exit): ${output.decodeToString()}")
    }
    return output
}

internal fun sha256(path: java.nio.file.Path): String =
    MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
