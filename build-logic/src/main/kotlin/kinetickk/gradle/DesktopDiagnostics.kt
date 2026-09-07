// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

@DisableCachingByDefault(because = "Records provenance of the local build")
abstract class WriteDesktopBuildInfoTask : DefaultTask() {
    @get:Input abstract val gameVersion: Property<String>
    @get:Input abstract val kotlinVersion: Property<String>
    @get:Input abstract val composeVersion: Property<String>
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
            digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray())
            digest.update(0.toByte())
            digest.update(file.readBytes())
            digest.update(0.toByte())
        }
        val properties = Properties().apply {
            setProperty("game.version", gameVersion.get())
            setProperty("kotlin.version", kotlinVersion.get())
            setProperty("compose.version", composeVersion.get())
            setProperty("git.revision", gitRevision.get().trim().ifBlank { "unknown" })
            setProperty("git.dirty", gitStatus.get().isNotBlank().toString())
            setProperty("git.status", gitStatus.get().take(64 * 1024))
            setProperty("source.sha256", digest.digest().joinToString("") { "%02x".format(it) })
            setProperty("built.at", Instant.now().toString())
        }
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        directory.resolve("kinetickk-build.properties").outputStream().use { properties.store(it, "KINETICKK build provenance") }
    }
}

/** Serializable Gradle action: no execution-time Project access, compatible with configuration cache. */
class IsolateDesktopRuntimeAction(private val isolatedClasspath: ConfigurableFileCollection) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        task as JavaExec
        val originals = task.classpath.files.toList()
        val directory = Files.createTempDirectory("kinetickk-runtime-")
        val copies = originals.mapIndexed { index, source ->
            val target = directory.resolve("$index-${source.name}").toFile()
            if (source.isDirectory) source.copyRecursively(target, overwrite = false)
            else source.copyTo(target, overwrite = false)
            target
        }
        isolatedClasspath.setFrom(copies)
        task.classpath = isolatedClasspath
        task.systemProperty("kinetickk.originalClasspath", originals.joinToString(java.io.File.pathSeparator))
        task.systemProperty("kinetickk.runtimeDir", directory.toString())
        val root = Path.of(task.systemProperties["kinetickk.crashDir"]?.toString()
            ?: Path.of(System.getProperty("user.home"), ".kinetickk", "crashes").toString())
        val crashRoot = if (task.args.orEmpty().any { it.startsWith("--crash-test=") }) root.resolve("self-tests") else root
        val nativeDirectory = crashRoot.resolve("native")
        try {
            Files.createDirectories(nativeDirectory)
            task.jvmArgs("-XX:ErrorFile=${nativeDirectory.resolve("hs_err_pid%p.log")}")
        } catch (error: Exception) {
            task.logger.warn("Cannot configure native crash directory; JVM default location will be used: {}", error.toString())
        }
        task.logger.lifecycle("KINETICKK isolated runtime: {}", directory)
    }
}

class DeleteSuccessfulDesktopRuntimeAction : Action<Task>, Serializable {
    override fun execute(task: Task) {
        task as JavaExec
        val directory = task.systemProperties["kinetickk.runtimeDir"]?.toString()?.let(Path::of) ?: return
        if (directory.parent != Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize() ||
            !directory.fileName.toString().startsWith("kinetickk-runtime-")) return
        Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
