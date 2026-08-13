// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files

const val ARCHITECTURE_EDGE_EXPORT_TASK_NAME = "exportArchitectureEdges"
const val ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME =
    "architectureEdgeReportElements"
const val ARCHITECTURE_EDGE_REPORTS_CONFIGURATION_NAME =
    "architectureEdgeReports"

@CacheableTask
abstract class ExportArchitectureEdgesTask : DefaultTask() {
    @get:Input
    abstract val sourceProjectPath: Property<String>

    @get:Input
    abstract val declaredProjectDependencies: SetProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        declaredProjectDependencies.convention(emptySet())
    }

    @TaskAction
    fun export() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            architectureEdgeReportText(
                sourceProjectPath = sourceProjectPath.get(),
                encodedEdges = declaredProjectDependencies.get(),
            ),
            StandardCharsets.UTF_8,
        )
    }
}

internal fun architectureEdgeReportText(
    sourceProjectPath: String,
    encodedEdges: Collection<String>,
): String {
    validateProjectPath(sourceProjectPath, "source project")
    val edges = encodedEdges.map(::decodeArchitectureEdge).toSortedSet()
    edges.forEach { edge ->
        require(edge.source == sourceProjectPath) {
            "Architecture edge source ${edge.source} does not match report source $sourceProjectPath"
        }
    }
    return buildString {
        appendLine(ARCHITECTURE_EDGE_REPORT_SCHEMA_LINE)
        appendLine("source\t$sourceProjectPath")
        edges.forEach { appendLine("edge\t$it") }
    }
}

internal fun loadArchitectureEdges(
    reportFiles: Collection<File>,
    expectedSourceProjectPaths: Set<String>,
): Set<String> {
    val reportsBySource = linkedMapOf<String, ArchitectureEdgeReport>()
    reportFiles.sortedBy(File::getAbsolutePath).forEach { file ->
        val report = runCatching { parseArchitectureEdgeReport(file) }
            .getOrElse { failure ->
                throw GradleException(
                    "Invalid architecture edge report ${file.invariantSeparatorsPath}: " +
                        failure.message,
                    failure,
                )
            }
        val previous = reportsBySource.put(report.sourceProjectPath, report)
        if (previous != null) {
            throw GradleException(
                "Multiple architecture edge reports were resolved for ${report.sourceProjectPath}",
            )
        }
    }

    val actualSources = reportsBySource.keys
    val missing = expectedSourceProjectPaths - actualSources
    val unexpected = actualSources - expectedSourceProjectPaths
    if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
        throw GradleException(
            buildString {
                append("Architecture edge report set does not match the leaf-project set.")
                if (missing.isNotEmpty()) {
                    append(" Missing reports: ${missing.sorted().joinToString()}.")
                }
                if (unexpected.isNotEmpty()) {
                    append(" Unexpected reports: ${unexpected.sorted().joinToString()}.")
                }
            },
        )
    }

    return reportsBySource.values
        .asSequence()
        .flatMap { it.encodedEdges.asSequence() }
        .toSortedSet()
}

private fun parseArchitectureEdgeReport(file: File): ArchitectureEdgeReport {
    val text = decodeArchitectureReportUtf8(Files.readAllBytes(file.toPath()))
    require('\r' !in text) { "CR characters are forbidden; reports must use canonical LF endings" }
    require(text.endsWith('\n')) { "report must end with a newline" }
    val lines = text.dropLast(1).split('\n')
    require(lines.size >= 2) { "report must contain schema and source lines" }
    require(lines[0] == ARCHITECTURE_EDGE_REPORT_SCHEMA_LINE) {
        "unsupported schema line: ${lines[0]}"
    }

    val sourceFields = lines[1].split('\t')
    require(sourceFields.size == 2 && sourceFields[0] == "source") {
        "malformed source line: ${lines[1]}"
    }
    val source = sourceFields[1]
    validateProjectPath(source, "report source")

    val edges = lines.drop(2).mapIndexed { index, line ->
        val fields = line.split('\t')
        require(fields.size == 4 && fields[0] == "edge") {
            "malformed edge line ${index + 3}: $line"
        }
        decodeArchitectureEdge(fields.drop(1).joinToString("\t")).also { edge ->
            require(edge.source == source) {
                "edge line ${index + 3} belongs to ${edge.source}, not $source"
            }
        }.encoded
    }
    require(edges.size == edges.toSet().size) { "report contains duplicate edge rows" }
    require(edges == edges.sorted()) { "edge rows are not sorted canonically" }
    return ArchitectureEdgeReport(source, edges.toSortedSet())
}

private fun decodeArchitectureReportUtf8(bytes: ByteArray): String =
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

private fun decodeArchitectureEdge(encoded: String): ArchitectureEdge {
    val fields = encoded.split('\t')
    require(fields.size == 3) { "Malformed architecture dependency edge: $encoded" }
    val source = fields[0]
    val configuration = fields[1]
    val target = fields[2]
    validateProjectPath(source, "edge source")
    require(configuration.isNotBlank()) { "Architecture edge configuration must not be blank" }
    require(configuration.none { it == '\n' || it == '\r' || it == '\t' }) {
        "Architecture edge configuration contains a control separator: $configuration"
    }
    validateProjectPath(target, "edge target")
    return ArchitectureEdge(source, configuration, target)
}

private fun validateProjectPath(path: String, label: String) {
    require(path.startsWith(':') && path.length > 1) { "$label is not a leaf project path: $path" }
    require(path.none { it == '\n' || it == '\r' || it == '\t' }) {
        "$label contains a control separator: $path"
    }
}

private data class ArchitectureEdge(
    val source: String,
    val configuration: String,
    val target: String,
) : Comparable<ArchitectureEdge> {
    val encoded: String
        get() = "$source\t$configuration\t$target"

    override fun compareTo(other: ArchitectureEdge): Int = encoded.compareTo(other.encoded)

    override fun toString(): String = encoded
}

private data class ArchitectureEdgeReport(
    val sourceProjectPath: String,
    val encodedEdges: Set<String>,
)

private const val ARCHITECTURE_EDGE_REPORT_SCHEMA_LINE =
    "schema\tkinetickk-architecture-edges/v1"
