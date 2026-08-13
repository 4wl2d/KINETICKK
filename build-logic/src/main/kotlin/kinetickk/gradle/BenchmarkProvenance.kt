// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import java.io.File
import java.security.MessageDigest
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

private const val HARNESS_PATH =
    "tools/performance/harness/src/main/kotlin/kinetickk/performance/BenchmarkHarness.kt"
private const val COMPARATOR_PATH = "tools/performance/compare_results.py"
private const val PROVENANCE_EMITTER_PATH =
    "build-logic/src/main/kotlin/kinetickk/gradle/BenchmarkProvenance.kt"

fun JavaExec.benchmarkSourceContract(
    repositoryDirectory: File,
    adapterPath: String,
    runnerPath: String,
) {
    workingDir(repositoryDirectory)
    jvmArgumentProviders.add(
        BenchmarkSourceContractArguments(
            adapterPath = adapterPath,
            adapterFile = repositoryDirectory.resolve(adapterPath),
            harnessPath = HARNESS_PATH,
            harnessFile = repositoryDirectory.resolve(HARNESS_PATH),
            runnerPath = runnerPath,
            runnerFile = repositoryDirectory.resolve(runnerPath),
            comparatorPath = COMPARATOR_PATH,
            comparatorFile = repositoryDirectory.resolve(COMPARATOR_PATH),
            provenanceEmitterPath = PROVENANCE_EMITTER_PATH,
            provenanceEmitterFile = repositoryDirectory.resolve(PROVENANCE_EMITTER_PATH),
        ),
    )
}

private class BenchmarkSourceContractArguments(
    @get:Input
    val adapterPath: String,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val adapterFile: File,
    @get:Input
    val harnessPath: String,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val harnessFile: File,
    @get:Input
    val runnerPath: String,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runnerFile: File,
    @get:Input
    val comparatorPath: String,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val comparatorFile: File,
    @get:Input
    val provenanceEmitterPath: String,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val provenanceEmitterFile: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = buildList {
        addSource("adapter", adapterPath, adapterFile)
        addSource("harness", harnessPath, harnessFile)
        addSource("runner", runnerPath, runnerFile)
        addSource("comparator", comparatorPath, comparatorFile)
        addSource("provenanceEmitter", provenanceEmitterPath, provenanceEmitterFile)
    }

    private fun MutableList<String>.addSource(role: String, path: String, file: File) {
        add("-Dkinetickk.benchmark.provenance.${role}Path=$path")
        add("-Dkinetickk.benchmark.provenance.${role}Sha256=${file.sha256()}")
    }
}

private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(readBytes())
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
