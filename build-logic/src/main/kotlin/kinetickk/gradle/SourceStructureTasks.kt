// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads the leaf modules' Kotlin sources and measures them with [scanSourceStructure]. Everything
 * that decides lives in pure functions; the task only reads files and relativizes paths. The
 * baseline and the architecture document are read in the action, so a missing file produces this
 * build's own message instead of a Gradle validation error.
 */
abstract class SourceStructureTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val leafProjectPaths: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val architectureDocument: RegularFileProperty

    @get:Internal
    abstract val baselineFile: RegularFileProperty

    internal fun scanRepository(): StructureScan {
        val root = repositoryDirectory.get().asFile
        val sources = (mainSourceFiles.files + testSourceFiles.files)
            .filter { it.isFile }
            .map { file -> RepositorySource(file.relativeTo(root).invariantSeparatorsPath, readStrictUtf8(file, root)) }
            .sortedBy { it.path }
        val leafDirectories = leafProjectPaths.get().map { it.removePrefix(":").replace(':', '/') }.toSet()
        val document = architectureDocument.get().asFile.takeIf { it.isFile }?.let { readStrictUtf8(it, root) }
        return scanSourceStructure(sources, leafDirectories, document, { path -> File(root, path).exists() })
    }

    /** The parsed baseline, or null when the file does not exist. */
    internal fun readBaseline(): List<BaselineRow>? {
        val file = baselineFile.get().asFile
        if (!file.isFile) return null
        val root = repositoryDirectory.get().asFile
        val path = file.relativeTo(root).invariantSeparatorsPath
        return try {
            parseStructureBaseline(readStrictUtf8(file, root), path)
        } catch (failure: StructureBaselineException) {
            throw GradleException(failure.message.orEmpty(), failure)
        }
    }

    internal fun relativePath(file: File): String = file.relativeTo(repositoryDirectory.get().asFile).invariantSeparatorsPath
}

abstract class VerifySourceStructureTask : SourceStructureTask() {
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val started = System.nanoTime()
        val scan = scanRepository()
        val report = reportFile.get().asFile
        val reportPath = relativePath(report)
        val rows = readBaseline()
        if (rows == null) {
            val message = missingStructureBaselineMessage()
            writeReport(report, structureReport(message, emptyList(), scan.measurements))
            throw GradleException(message)
        }
        val evaluation = evaluateStructure(scan.measurements, rows)
        if (evaluation.failed) {
            val message = structureFailureMessage(evaluation.violations, evaluation.stale, reportPath)
            writeReport(report, structureReport(message, rows, scan.measurements))
            throw GradleException(message)
        }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        val summary = buildString {
            append("Source structure verified: ${scan.files} files, ${scan.functions} functions, ${rows.size} baselined item(s)")
            if (evaluation.tightenable.isNotEmpty()) {
                append(" (${evaluation.tightenable.size} ceiling(s) can be lowered: ./gradlew $UPDATE_SOURCE_STRUCTURE_BASELINE_TASK)")
            }
            append(" in $elapsedMillis ms.")
        }
        writeReport(report, structureReport(summary, rows, scan.measurements))
        logger.lifecycle(summary)
    }
}

abstract class UpdateSourceStructureBaselineTask : SourceStructureTask() {
    @get:Input
    abstract val resetBaseline: Property<Boolean>

    init {
        resetBaseline.convention(false)
    }

    @TaskAction
    fun update() {
        val scan = scanRepository()
        val baseline = baselineFile.get().asFile
        val update = if (resetBaseline.get()) {
            resetStructureBaseline(scan.measurements)
        } else {
            tightenStructureBaseline(scan.measurements, readBaseline() ?: throw GradleException(missingStructureBaselineMessage()))
        }
        baseline.parentFile.mkdirs()
        baseline.writeText(renderStructureBaseline(update.rows), StandardCharsets.UTF_8)
        logger.lifecycle(
            "Wrote ${relativePath(baseline)}: ${update.rows.size} row(s); lowered ${update.lowered}, " +
                "deleted ${update.deleted.size}, carried ${update.carried.size}.",
        )
        update.carried.forEach { (old, new) -> logger.lifecycle("Carried ${old.rule.id} ${old.key} -> ${new.key} (${new.ceiling})") }
        if (update.remaining.isNotEmpty()) {
            val message = structureFailureMessage(update.remaining, emptyList())
            throw GradleException(if (resetBaseline.get()) message else "$message\n$NOT_BASELINED_WITHOUT_RESET")
        }
    }
}

private fun writeReport(report: File, text: String) {
    report.parentFile.mkdirs()
    report.writeText(text.trimEnd('\n') + "\n", StandardCharsets.UTF_8)
}

/** Strict UTF-8, like the architecture edge reports: malformed input fails instead of being replaced. */
private fun readStrictUtf8(file: File, root: File): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(file.readBytes()))
        .toString()
} catch (failure: CharacterCodingException) {
    throw GradleException("${file.relativeTo(root).invariantSeparatorsPath} is not valid UTF-8", failure)
}
