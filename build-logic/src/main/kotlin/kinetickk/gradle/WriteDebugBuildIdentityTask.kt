// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

/** Portable source identity; deliberately excludes the clock and machine-specific paths. */
@CacheableTask
abstract class WriteDebugBuildIdentityTask : DefaultTask() {
    @get:Input abstract val gameVersion: Property<String>
    @get:Input abstract val gitRevision: Property<String>
    @get:Input abstract val gitStatus: Property<String>
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildSources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun write() {
        val root = repositoryDirectory.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        buildSources.files.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach { file ->
            digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(file.readBytes())
            digest.update(0.toByte())
        }
        val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
        val description = "KINETICKK ${gameVersion.get()} | variant=debug" +
            " | revision=${gitRevision.get().trim().ifBlank { "unknown" }}" +
            " | dirty=${gitStatus.get().isNotBlank()} | source.sha256=$fingerprint"
        val escaped = description.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r")
        val file = outputDirectory.file("kinetickk/app/shared/DebugBuildIdentity.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "// SPDX-FileCopyrightText: 2026 Vladislav Tomilov\n" +
                "// SPDX-License-Identifier: GPL-3.0-or-later\n\n" +
                "package kinetickk.app.shared\n\n" +
                "internal object DebugBuildIdentity {\n" +
                "    const val description: String = \"$escaped\"\n" +
                "}\n",
        )
    }
}
