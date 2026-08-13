// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.performance

import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

/** Primitive-returning operation avoids a generic Function1<Long> box in allocation measurements. */
fun interface BenchmarkOperation {
    operator fun invoke(validation: BenchmarkValidationContext): Long
}

fun interface BenchmarkProbePreparation {
    operator fun invoke()
}

/**
 * Semantic evidence that is evaluated once before calibration and never inside a timed batch.
 * [expectedTimedResult] independently binds the primitive result returned by the measured path.
 * [expectedOutcomeWitness] binds a canonical state/output witness published by that same path.
 */
data class BenchmarkValidation(
    val expectedTimedResult: Long,
    val expectedOutcomeWitness: Long,
    val prepareProbe: BenchmarkProbePreparation = BenchmarkProbePreparation {},
)

/**
 * The operation publishes its canonical result through [observeOutcome]. The lambda is inlined and
 * is not evaluated in timed batches, so canonical fingerprinting cannot pollute measurements.
 */
class BenchmarkValidationContext @PublishedApi internal constructor(
    @PublishedApi internal val recorder: BenchmarkOutcomeRecorder?,
) {
    inline fun observeOutcome(witness: () -> Long) {
        recorder?.record(witness())
    }
}

@PublishedApi
internal class BenchmarkOutcomeRecorder {
    private var count: Int = 0
    private var witness: Long = 0L

    @PublishedApi
    internal fun record(value: Long) {
        count += 1
        witness = value
    }

    fun singleWitness(scenarioName: String): Long {
        require(count == 1) {
            "Benchmark scenario $scenarioName must publish exactly one outcome witness; observed $count"
        }
        return witness
    }
}

/** One deterministic operation whose result is consumed to keep the measured work observable. */
data class BenchmarkScenario(
    val name: String,
    val category: String,
    val description: String,
    val metadata: Map<String, String> = emptyMap(),
    val minimumOperations: Int = 1,
    val maximumOperations: Int = 1_000_000,
    val validation: BenchmarkValidation? = null,
    val operation: BenchmarkOperation,
) {
    init {
        require(name.matches(Regex("[a-z0-9_]+"))) { "Invalid benchmark name: $name" }
        require(category.isNotBlank())
        require(minimumOperations > 0)
        require(maximumOperations >= minimumOperations)
    }
}

data class BenchmarkSuiteIdentity(
    val suiteVersion: String,
    val adapter: String,
    val label: String,
    val revision: String,
    val dirty: Boolean,
)

private data class BenchmarkSource(
    val path: String,
    val sha256: String,
)

private data class BenchmarkSourceContract(
    val adapter: BenchmarkSource,
    val harness: BenchmarkSource,
    val runner: BenchmarkSource,
    val comparator: BenchmarkSource,
    val provenanceEmitter: BenchmarkSource,
)

private data class BenchmarkProfile(
    val name: String,
    val warmupIterations: Int,
    val measurementIterations: Int,
    val targetIterationNanos: Long,
) {
    companion object {
        fun load(): BenchmarkProfile {
            val preset = when (System.getProperty(PROFILE_PROPERTY, "standard").lowercase()) {
                "smoke" -> BenchmarkProfile("smoke", 2, 3, 40_000_000L)
                "standard" -> BenchmarkProfile("standard", 5, 10, 180_000_000L)
                "deep" -> BenchmarkProfile("deep", 10, 20, 450_000_000L)
                else -> error("$PROFILE_PROPERTY must be smoke, standard, or deep")
            }
            return preset.copy(
                warmupIterations = positiveOverride(WARMUPS_PROPERTY, preset.warmupIterations),
                measurementIterations = positiveOverride(MEASUREMENTS_PROPERTY, preset.measurementIterations),
                targetIterationNanos = positiveOverride(
                    ITERATION_MILLIS_PROPERTY,
                    preset.targetIterationNanos / 1_000_000L,
                ) * 1_000_000L,
            )
        }

        private fun positiveOverride(name: String, default: Int): Int =
            System.getProperty(name)?.toIntOrNull()?.also { require(it > 0) } ?: default

        private fun positiveOverride(name: String, default: Long): Long =
            System.getProperty(name)?.toLongOrNull()?.also { require(it > 0L) } ?: default
    }
}

private data class RuntimeCounters(
    val threadCpuNanos: Long?,
    val threadAllocatedBytes: Long?,
    val gcCollections: Long,
    val gcMillis: Long,
)

private data class BenchmarkSample(
    val operations: Int,
    val wallNanosPerOperation: Double,
    val cpuNanosPerOperation: Double?,
    val allocatedBytesPerOperation: Double?,
    val gcCollectionsPerOperation: Double,
    val gcNanosPerOperation: Double,
)

private data class MetricSummary(
    val mean: Double,
    val median: Double,
    val p95: Double,
    val minimum: Double,
    val maximum: Double,
    val coefficientOfVariation: Double,
)

private data class ScenarioResult(
    val scenario: BenchmarkScenario,
    val validation: BenchmarkValidationEvidence,
    val operationsPerIteration: Int,
    val samples: List<BenchmarkSample>,
)

data class BenchmarkValidationEvidence(
    val expectedTimedResult: Long,
    val actualTimedResult: Long,
    val expectedOutcomeWitness: Long,
    val actualOutcomeWitness: Long,
)

private const val PROFILE_PROPERTY = "kinetickk.benchmark.profile"
private const val OUTPUT_PROPERTY = "kinetickk.benchmark.output"
private const val SCENARIOS_PROPERTY = "kinetickk.benchmark.scenarios"
private const val WARMUPS_PROPERTY = "kinetickk.benchmark.warmups"
private const val MEASUREMENTS_PROPERTY = "kinetickk.benchmark.measurements"
private const val ITERATION_MILLIS_PROPERTY = "kinetickk.benchmark.iterationMillis"
private const val FORK_PROPERTY = "kinetickk.benchmark.fork"
private const val PROVENANCE_PROPERTY_PREFIX = "kinetickk.benchmark.provenance"
private const val SCHEMA_VERSION = 2
private const val SOURCE_CONTRACT_VERSION = 2
private const val VALIDATION_CONTRACT_VERSION = 1
private const val SPDX_COPYRIGHT = "2026 Vladislav Tomilov"
private const val SPDX_LICENSE = "GPL-3.0-or-later"

private val disabledValidationContext = BenchmarkValidationContext(recorder = null)

@Volatile
private var benchmarkBlackhole: Long = 0L

/**
 * Runs every selected scenario in one fresh JVM fork and writes the complete raw samples as JSON.
 * Multi-fork aggregation and branch comparison intentionally happen outside this process.
 */
fun runBenchmarkSuite(
    identity: BenchmarkSuiteIdentity,
    scenarios: List<BenchmarkScenario>,
) {
    require(scenarios.isNotEmpty()) { "At least one benchmark scenario is required" }
    require(scenarios.map(BenchmarkScenario::name).distinct().size == scenarios.size) {
        "Benchmark scenario names must be unique"
    }
    require(identity.suiteVersion.matches(Regex("[a-z0-9-]+-v(?:[2-9]|[1-9][0-9]+)"))) {
        "suiteVersion must be bumped for raw schema v$SCHEMA_VERSION: ${identity.suiteVersion}"
    }
    Locale.setDefault(Locale.ROOT)

    val profile = BenchmarkProfile.load()
    val sourceContract = loadSourceContract()
    val requestedNames = System.getProperty(SCENARIOS_PROPERTY)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        .orEmpty()
    val selected = if (requestedNames.isEmpty()) {
        scenarios
    } else {
        val unknown = requestedNames - scenarios.map(BenchmarkScenario::name).toSet()
        require(unknown.isEmpty()) { "Unknown benchmark scenarios: ${unknown.sorted()}" }
        scenarios.filter { it.name in requestedNames }
    }

    val runtimeMetrics = RuntimeMetrics()
    println(
        "KINETICKK benchmark suite=${identity.suiteVersion} adapter=${identity.adapter} " +
            "profile=${profile.name} fork=${System.getProperty(FORK_PROPERTY, "1")}",
    )
    val results = selected.mapIndexed { index, scenario ->
        println("[${index + 1}/${selected.size}] ${scenario.category}/${scenario.name}: validating")
        val validation = validateBenchmarkScenario(scenario)
        println("  semantic preflight passed; calibrating")
        val operations = calibrate(scenario, profile.targetIterationNanos)
        repeat(profile.warmupIterations) { warmup ->
            runBatch(scenario, operations)
            println("  warmup ${warmup + 1}/${profile.warmupIterations}")
        }
        val samples = List(profile.measurementIterations) { measurement ->
            val before = runtimeMetrics.read()
            val started = System.nanoTime()
            runBatch(scenario, operations)
            val wallNanos = System.nanoTime() - started
            val after = runtimeMetrics.read()
            val sample = BenchmarkSample(
                operations = operations,
                wallNanosPerOperation = wallNanos.toDouble() / operations,
                cpuNanosPerOperation = delta(after.threadCpuNanos, before.threadCpuNanos)
                    ?.toDouble()
                    ?.div(operations),
                allocatedBytesPerOperation = delta(
                    after.threadAllocatedBytes,
                    before.threadAllocatedBytes,
                )?.toDouble()?.div(operations),
                gcCollectionsPerOperation = max(0L, after.gcCollections - before.gcCollections)
                    .toDouble() / operations,
                gcNanosPerOperation = max(0L, after.gcMillis - before.gcMillis)
                    .toDouble() * 1_000_000.0 / operations,
            )
            println(
                "  measure ${measurement + 1}/${profile.measurementIterations}: " +
                    "${formatNanos(sample.wallNanosPerOperation)} ns/op, " +
                    "${sample.allocatedBytesPerOperation?.let(::formatNanos) ?: "n/a"} B/op",
            )
            sample
        }
        ScenarioResult(scenario, validation, operations, samples)
    }

    val output = Path.of(System.getProperty(OUTPUT_PROPERTY, "build/performance/result.json"))
        .toAbsolutePath()
        .normalize()
    output.parent?.let(Files::createDirectories)
    Files.writeString(
        output,
        renderJson(identity, sourceContract, profile, results, runtimeMetrics),
    )
    println("Wrote benchmark result: $output")
    printSummary(results)
}

/** Public for focused mutation tests; benchmark execution uses this exact preflight. */
fun validateBenchmarkScenario(scenario: BenchmarkScenario): BenchmarkValidationEvidence {
    val validation = requireNotNull(scenario.validation) {
        "Benchmark scenario ${scenario.name} is missing an explicit validation contract"
    }
    val metadataWitness = scenario.metadata["outcomeFingerprint"]?.toLongOrNull()
    require(metadataWitness == validation.expectedOutcomeWitness) {
        "Benchmark scenario ${scenario.name} metadata outcomeFingerprint " +
            "${scenario.metadata["outcomeFingerprint"] ?: "<missing>"} does not match expected witness " +
            validation.expectedOutcomeWitness
    }
    validation.prepareProbe()
    val recorder = BenchmarkOutcomeRecorder()
    val actualTimedResult = scenario.operation(BenchmarkValidationContext(recorder))
    val actualOutcomeWitness = recorder.singleWitness(scenario.name)
    require(actualTimedResult == validation.expectedTimedResult) {
        "Benchmark scenario ${scenario.name} returned $actualTimedResult; expected " +
            validation.expectedTimedResult
    }
    require(actualOutcomeWitness == validation.expectedOutcomeWitness) {
        "Benchmark scenario ${scenario.name} published outcome witness $actualOutcomeWitness; " +
            "expected ${validation.expectedOutcomeWitness}"
    }
    return BenchmarkValidationEvidence(
        expectedTimedResult = validation.expectedTimedResult,
        actualTimedResult = actualTimedResult,
        expectedOutcomeWitness = validation.expectedOutcomeWitness,
        actualOutcomeWitness = actualOutcomeWitness,
    )
}

private fun calibrate(scenario: BenchmarkScenario, targetNanos: Long): Int {
    var operations = scenario.minimumOperations
    repeat(12) {
        val elapsed = measureNanoTime { runBatch(scenario, operations) }.coerceAtLeast(1L)
        if (elapsed >= targetNanos / 2L || operations >= scenario.maximumOperations) {
            return operations
        }
        val estimated = ceil(operations.toDouble() * targetNanos / elapsed).toLong()
        val boundedGrowth = min(operations.toLong() * 8L, max(operations.toLong() + 1L, estimated))
        operations = min(scenario.maximumOperations.toLong(), boundedGrowth).toInt()
    }
    return operations
}

private fun runBatch(scenario: BenchmarkScenario, operations: Int) {
    var observed = benchmarkBlackhole
    repeat(operations) { index ->
        observed = java.lang.Long.rotateLeft(
            observed xor scenario.operation(disabledValidationContext),
            index and 63,
        )
    }
    benchmarkBlackhole = observed
}

private fun delta(after: Long?, before: Long?): Long? =
    if (after == null || before == null) null else max(0L, after - before)

private class RuntimeMetrics {
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val allocationBean = (threadBean as? com.sun.management.ThreadMXBean)?.also { bean ->
        if (bean.isThreadAllocatedMemorySupported && !bean.isThreadAllocatedMemoryEnabled) {
            runCatching { bean.isThreadAllocatedMemoryEnabled = true }
        }
    }
    private val garbageCollectors: List<GarbageCollectorMXBean> =
        ManagementFactory.getGarbageCollectorMXBeans()

    val allocationSupported: Boolean
        get() = allocationBean?.isThreadAllocatedMemoryEnabled == true

    val cpuTimeSupported: Boolean
        get() = threadBean.isCurrentThreadCpuTimeSupported

    fun read(): RuntimeCounters = RuntimeCounters(
        threadCpuNanos = if (cpuTimeSupported) threadBean.currentThreadCpuTime else null,
        threadAllocatedBytes = if (allocationSupported) {
            allocationBean?.getThreadAllocatedBytes(Thread.currentThread().id)
        } else {
            null
        },
        gcCollections = garbageCollectors.sumOf { max(0L, it.collectionCount) },
        gcMillis = garbageCollectors.sumOf { max(0L, it.collectionTime) },
    )

    fun collectorNames(): List<String> = garbageCollectors.map(GarbageCollectorMXBean::getName)
}

private fun printSummary(results: List<ScenarioResult>) {
    println("\nScenario summary (median wall time / median allocation):")
    results.forEach { result ->
        val wall = summarize(result.samples.map(BenchmarkSample::wallNanosPerOperation))
        val allocation = summarizeNullable(result.samples.map(BenchmarkSample::allocatedBytesPerOperation))
        println(
            "  ${result.scenario.name.padEnd(34)} " +
                "${formatNanos(wall.median).padStart(12)} ns/op  " +
                "${allocation?.let { formatNanos(it.median) }?.padStart(12) ?: "         n/a"} B/op",
        )
    }
}

private fun summarizeNullable(values: List<Double?>): MetricSummary? =
    values.filterNotNull().takeIf(List<Double>::isNotEmpty)?.let(::summarize)

private fun summarize(values: List<Double>): MetricSummary {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val mean = values.average()
    val variance = if (values.size > 1) {
        values.sumOf { value -> (value - mean) * (value - mean) } / (values.size - 1)
    } else {
        0.0
    }
    return MetricSummary(
        mean = mean,
        median = percentile(sorted, 0.5),
        p95 = percentile(sorted, 0.95),
        minimum = sorted.first(),
        maximum = sorted.last(),
        coefficientOfVariation = if (mean == 0.0) 0.0 else sqrt(variance) / mean,
    )
}

private fun percentile(sorted: List<Double>, percentile: Double): Double {
    if (sorted.size == 1) return sorted.single()
    val position = percentile.coerceIn(0.0, 1.0) * (sorted.lastIndex)
    val lower = position.toInt()
    val upper = min(sorted.lastIndex, lower + 1)
    val fraction = position - lower
    return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
}

private fun loadSourceContract(): BenchmarkSourceContract = BenchmarkSourceContract(
    adapter = loadSource("adapter"),
    harness = loadSource("harness"),
    runner = loadSource("runner"),
    comparator = loadSource("comparator"),
    provenanceEmitter = loadSource("provenanceEmitter"),
)

private fun loadSource(role: String): BenchmarkSource {
    val pathProperty = "$PROVENANCE_PROPERTY_PREFIX.${role}Path"
    val digestProperty = "$PROVENANCE_PROPERTY_PREFIX.${role}Sha256"
    val logicalPath = requireNotNull(System.getProperty(pathProperty)) {
        "Missing required benchmark source property $pathProperty"
    }
    require(logicalPath.isNotBlank() && '\\' !in logicalPath) {
        "$pathProperty must be a non-empty repository-relative POSIX path"
    }
    val relativePath = Path.of(logicalPath)
    require(!relativePath.isAbsolute && relativePath.normalize().toString() == logicalPath) {
        "$pathProperty must be a normalized repository-relative path: $logicalPath"
    }
    val repositoryRoot = Path.of(System.getProperty("user.dir")).toRealPath()
    val unresolvedSourcePath = repositoryRoot.resolve(relativePath).normalize()
    require(unresolvedSourcePath.startsWith(repositoryRoot) && Files.isRegularFile(unresolvedSourcePath)) {
        "$pathProperty does not resolve to a regular file inside the repository: $logicalPath"
    }
    val sourcePath = unresolvedSourcePath.toRealPath()
    require(sourcePath.startsWith(repositoryRoot)) {
        "$pathProperty resolves through a symlink outside the repository: $logicalPath"
    }
    val expectedDigest = requireNotNull(System.getProperty(digestProperty)) {
        "Missing required benchmark source property $digestProperty"
    }
    require(expectedDigest.matches(Regex("[0-9a-f]{64}"))) {
        "$digestProperty must be a lowercase SHA-256 digest"
    }
    val actualDigest = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(sourcePath))
        .joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }
    require(actualDigest == expectedDigest) {
        "$role source digest mismatch for $logicalPath: expected $expectedDigest, actual $actualDigest"
    }
    return BenchmarkSource(path = logicalPath, sha256 = actualDigest)
}

private fun renderJson(
    identity: BenchmarkSuiteIdentity,
    sourceContract: BenchmarkSourceContract,
    profile: BenchmarkProfile,
    results: List<ScenarioResult>,
    runtimeMetrics: RuntimeMetrics,
): String = buildString {
    val runtime = ManagementFactory.getRuntimeMXBean()
    val memory = ManagementFactory.getMemoryMXBean()
    append("{\n")
    append("  \"schemaVersion\": $SCHEMA_VERSION,\n")
    append("  \"spdxFileCopyrightText\": \"$SPDX_COPYRIGHT\",\n")
    append("  \"spdxLicenseIdentifier\": \"$SPDX_LICENSE\",\n")
    append("  \"suiteVersion\": ${identity.suiteVersion.json()},\n")
    append("  \"adapter\": ${identity.adapter.json()},\n")
    append("  \"label\": ${identity.label.json()},\n")
    append("  \"revision\": ${identity.revision.json()},\n")
    append("  \"dirty\": ${identity.dirty},\n")
    append("  \"fork\": ${System.getProperty(FORK_PROPERTY, "1").json()},\n")
    append("  \"generatedAt\": ${Instant.now().toString().json()},\n")
    append("  \"sourceContract\": {\n")
    append("    \"contractVersion\": $SOURCE_CONTRACT_VERSION,\n")
    append("    \"algorithm\": \"SHA-256\",\n")
    append("    \"adapter\": ${sourceContract.adapter.json()},\n")
    append("    \"harness\": ${sourceContract.harness.json()},\n")
    append("    \"runner\": ${sourceContract.runner.json()},\n")
    append("    \"comparator\": ${sourceContract.comparator.json()},\n")
    append("    \"provenanceEmitter\": ${sourceContract.provenanceEmitter.json()}\n")
    append("  },\n")
    append("  \"profile\": {\n")
    append("    \"name\": ${profile.name.json()},\n")
    append("    \"warmupIterations\": ${profile.warmupIterations},\n")
    append("    \"measurementIterations\": ${profile.measurementIterations},\n")
    append("    \"targetIterationMillis\": ${profile.targetIterationNanos / 1_000_000L}\n")
    append("  },\n")
    append("  \"environment\": {\n")
    append("    \"osName\": ${System.getProperty("os.name").json()},\n")
    append("    \"osVersion\": ${System.getProperty("os.version").json()},\n")
    append("    \"architecture\": ${System.getProperty("os.arch").json()},\n")
    append("    \"javaVersion\": ${System.getProperty("java.version").json()},\n")
    append("    \"javaVendor\": ${System.getProperty("java.vendor").json()},\n")
    append("    \"vmName\": ${System.getProperty("java.vm.name").json()},\n")
    append("    \"availableProcessors\": ${Runtime.getRuntime().availableProcessors()},\n")
    append("    \"maxHeapBytes\": ${memory.heapMemoryUsage.max},\n")
    append("    \"cpuTimeSupported\": ${runtimeMetrics.cpuTimeSupported},\n")
    append("    \"threadAllocationSupported\": ${runtimeMetrics.allocationSupported},\n")
    append("    \"garbageCollectors\": ${runtimeMetrics.collectorNames().jsonArray()},\n")
    append(
        "    \"jvmArguments\": ${runtime.inputArguments.filterNot { argument ->
            argument.startsWith("-Dkinetickk.benchmark.")
        }.jsonArray()}\n",
    )
    append("  },\n")
    append("  \"scenarios\": [\n")
    results.forEachIndexed { resultIndex, result ->
        val samples = result.samples
        append("    {\n")
        append("      \"name\": ${result.scenario.name.json()},\n")
        append("      \"category\": ${result.scenario.category.json()},\n")
        append("      \"description\": ${result.scenario.description.json()},\n")
        append("      \"operationsPerIteration\": ${result.operationsPerIteration},\n")
        append("      \"metadata\": ${result.scenario.metadata.jsonObject()},\n")
        append("      \"validation\": {\n")
        append("        \"contractVersion\": $VALIDATION_CONTRACT_VERSION,\n")
        append("        \"expectedTimedResult\": ${result.validation.expectedTimedResult.toString().json()},\n")
        append("        \"actualTimedResult\": ${result.validation.actualTimedResult.toString().json()},\n")
        append("        \"expectedOutcomeWitness\": ${result.validation.expectedOutcomeWitness.toString().json()},\n")
        append("        \"actualOutcomeWitness\": ${result.validation.actualOutcomeWitness.toString().json()}\n")
        append("      },\n")
        append("      \"summary\": {\n")
        append("        \"wallNanosPerOperation\": ${summarize(samples.map(BenchmarkSample::wallNanosPerOperation)).json()},\n")
        append("        \"cpuNanosPerOperation\": ${summarizeNullable(samples.map(BenchmarkSample::cpuNanosPerOperation)).jsonOrNull()},\n")
        append("        \"allocatedBytesPerOperation\": ${summarizeNullable(samples.map(BenchmarkSample::allocatedBytesPerOperation)).jsonOrNull()},\n")
        append("        \"gcCollectionsPerOperation\": ${summarize(samples.map(BenchmarkSample::gcCollectionsPerOperation)).json()},\n")
        append("        \"gcNanosPerOperation\": ${summarize(samples.map(BenchmarkSample::gcNanosPerOperation)).json()}\n")
        append("      },\n")
        append("      \"samples\": [\n")
        samples.forEachIndexed { sampleIndex, sample ->
            append("        {\n")
            append("          \"operations\": ${sample.operations},\n")
            append("          \"wallNanosPerOperation\": ${sample.wallNanosPerOperation.number()},\n")
            append("          \"cpuNanosPerOperation\": ${sample.cpuNanosPerOperation.numberOrNull()},\n")
            append("          \"allocatedBytesPerOperation\": ${sample.allocatedBytesPerOperation.numberOrNull()},\n")
            append("          \"gcCollectionsPerOperation\": ${sample.gcCollectionsPerOperation.number()},\n")
            append("          \"gcNanosPerOperation\": ${sample.gcNanosPerOperation.number()}\n")
            append("        }${if (sampleIndex == samples.lastIndex) "" else ","}\n")
        }
        append("      ]\n")
        append("    }${if (resultIndex == results.lastIndex) "" else ","}\n")
    }
    append("  ]\n")
    append("}\n")
}

private fun MetricSummary.json(): String = buildString {
    append('{')
    append("\"mean\":${mean.number()},")
    append("\"median\":${median.number()},")
    append("\"p95\":${p95.number()},")
    append("\"min\":${minimum.number()},")
    append("\"max\":${maximum.number()},")
    append("\"coefficientOfVariation\":${coefficientOfVariation.number()}")
    append('}')
}

private fun MetricSummary?.jsonOrNull(): String = this?.json() ?: "null"

private fun BenchmarkSource.json(): String =
    "{\"path\":${path.json()},\"sha256\":${sha256.json()}}"

private fun Map<String, String>.jsonObject(): String = entries
    .sortedBy(Map.Entry<String, String>::key)
    .joinToString(prefix = "{", postfix = "}") { (key, value) -> "${key.json()}:${value.json()}" }

private fun List<String>.jsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.json() }

private fun String.json(): String = buildString(length + 2) {
    append('"')
    this@json.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private fun Double.number(): String = when {
    isNaN() || isInfinite() -> "null"
    else -> String.format(Locale.ROOT, "%.9f", this)
}

private fun Double?.numberOrNull(): String = this?.number() ?: "null"

private fun formatNanos(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
