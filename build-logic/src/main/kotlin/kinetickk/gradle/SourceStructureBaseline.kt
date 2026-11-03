// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

internal const val SOURCE_STRUCTURE_BASELINE_PATH = "build-logic/source-structure-baseline.tsv"
internal const val UPDATE_SOURCE_STRUCTURE_BASELINE_TASK = "updateSourceStructureBaseline"
internal const val RESET_SOURCE_STRUCTURE_BASELINE_PROPERTY = "kinetickk.sourceStructure.resetBaseline"

/** One ratchet ceiling: [key] may measure at most [ceiling] for [rule]. */
internal data class BaselineRow(val rule: StructureRule, val key: String, val ceiling: Int)

/** A measurement that fails verification; [ceiling] is null when the item is not baselined. */
internal data class StructureViolation(
    val measurement: Measurement,
    val ceiling: Int? = null,
    val movedFrom: String? = null,
)

internal data class StructureEvaluation(
    val violations: List<StructureViolation>,
    val stale: List<BaselineRow>,
    val tightenable: List<BaselineRow>,
) {
    val failed: Boolean
        get() = violations.isNotEmpty() || stale.isNotEmpty()
}

/** The rows to write and what still fails after a tighten or reset. */
internal data class BaselineUpdate(
    val rows: List<BaselineRow>,
    val remaining: List<StructureViolation>,
    val lowered: Int = 0,
    val deleted: List<BaselineRow> = emptyList(),
    val carried: List<Pair<BaselineRow, BaselineRow>> = emptyList(),
)

internal class StructureBaselineException(message: String) : IllegalArgumentException(message)

/** Parses the canonical baseline, failing closed with the path and line number of the first defect. */
internal fun parseStructureBaseline(text: String, path: String = SOURCE_STRUCTURE_BASELINE_PATH): List<BaselineRow> {
    fun fail(line: Int, reason: String): Nothing = throw StructureBaselineException("$path:$line: $reason")
    if ('\r' in text) fail(text.substringBefore('\r').count { it == '\n' } + 1, "CR characters are forbidden; use LF line endings")
    if (!text.endsWith('\n')) fail(text.count { it == '\n' } + 1, "the file must end with a newline")
    val lines = text.dropLast(1).split('\n')
    var schemaIndex = 0
    while (schemaIndex < lines.size && lines[schemaIndex].isBaselineComment()) schemaIndex++
    if (schemaIndex >= lines.size || lines[schemaIndex] != BASELINE_SCHEMA_LINE) {
        fail(schemaIndex + 1, "expected the schema line '${BASELINE_SCHEMA_LINE.replace('\t', ' ')}'")
    }
    val rows = mutableListOf<BaselineRow>()
    for (index in schemaIndex + 1 until lines.size) {
        val lineNumber = index + 1
        val fields = lines[index].split('\t')
        if (fields.size != 3) fail(lineNumber, "a row has exactly 3 tab-separated fields: rule, key, ceiling")
        val (ruleId, key, ceilingText) = fields
        val rule = StructureRule.byId(ruleId) ?: fail(lineNumber, "unknown rule '$ruleId'")
        if (!rule.baselinable) fail(lineNumber, "rule '$ruleId' is a hard rule and cannot be baselined")
        if (key.isEmpty()) fail(lineNumber, "the key is empty")
        if (!CEILING.matches(ceilingText)) fail(lineNumber, "the ceiling '$ceilingText' is not a positive decimal integer")
        val ceiling = ceilingText.toIntOrNull() ?: fail(lineNumber, "the ceiling '$ceilingText' is too large")
        if (ceiling <= rule.limit) fail(lineNumber, "the ceiling $ceiling is within the limit ${rule.limit}; delete the row")
        val row = BaselineRow(rule, key, ceiling)
        rows.lastOrNull()?.let { previous ->
            val order = compareRows(previous, row)
            if (order == 0) fail(lineNumber, "duplicate row for $ruleId $key")
            if (order > 0) fail(lineNumber, "rows are not sorted by rule id and key")
        }
        rows += row
    }
    return rows
}

/** The canonical text; `renderStructureBaseline(parseStructureBaseline(text)) == text` for canonical input. */
internal fun renderStructureBaseline(rows: List<BaselineRow>): String = buildString {
    BASELINE_HEADER.forEach { append(it).append('\n') }
    append(BASELINE_SCHEMA_LINE).append('\n')
    rows.sortedWith(::compareRows).forEach { row ->
        append(row.rule.id).append('\t').append(row.key).append('\t').append(row.ceiling).append('\n')
    }
}

internal fun evaluateStructure(measurements: List<Measurement>, rows: List<BaselineRow>): StructureEvaluation {
    val rowsByKey = rows.associateBy { it.rule to it.key }
    val measuredKeys = measurements.filter { it.rule.baselinable }.map { it.rule to it.key }.toSet()
    val stale = rows.filter { (it.rule to it.key) !in measuredKeys }
    val moves = moveCandidates(measurements.filter { it.rule.baselinable && (it.rule to it.key) !in rowsByKey }, stale)
    val violations = mutableListOf<StructureViolation>()
    val tightenable = mutableListOf<BaselineRow>()
    measurements.forEach { measurement ->
        val row = rowsByKey[measurement.rule to measurement.key]
        when {
            !measurement.rule.baselinable -> violations += StructureViolation(measurement)
            row == null -> violations += StructureViolation(measurement, movedFrom = moves[measurement]?.key)
            measurement.value > row.ceiling -> violations += StructureViolation(measurement, ceiling = row.ceiling)
            measurement.value < row.ceiling -> tightenable += row
        }
    }
    return StructureEvaluation(violations, stale, tightenable)
}

/**
 * Tighten-only update: lowers ceilings to current values, carries a uniquely moved file or function
 * that did not grow, and deletes the other stale rows. It never adds or raises a row; everything it
 * cannot absorb is returned in [BaselineUpdate.remaining].
 */
internal fun tightenStructureBaseline(measurements: List<Measurement>, rows: List<BaselineRow>): BaselineUpdate {
    val evaluation = evaluateStructure(measurements, rows)
    val measuredValues = measurements.filter { it.rule.baselinable }.associate { (it.rule to it.key) to it.value }
    var lowered = 0
    val kept = rows.filter { (it.rule to it.key) in measuredValues }.map { row ->
        val value = measuredValues.getValue(row.rule to row.key)
        if (value < row.ceiling) {
            lowered++
            row.copy(ceiling = value)
        } else {
            row
        }
    }
    val rowsByKey = rows.associateBy { it.rule to it.key }
    val moves = moveCandidates(measurements.filter { it.rule.baselinable && (it.rule to it.key) !in rowsByKey }, evaluation.stale)
    val carried = moves.filter { (measurement, old) -> measurement.value <= old.ceiling }
        .map { (measurement, old) -> old to BaselineRow(measurement.rule, measurement.key, measurement.value) }
    val carriedOld = carried.map { it.first }.toSet()
    val carriedNew = carried.map { it.second.rule to it.second.key }.toSet()
    val remaining = evaluation.violations.filterNot { (it.measurement.rule to it.measurement.key) in carriedNew }
    return BaselineUpdate(
        rows = (kept + carried.map { it.second }).sortedWith(::compareRows),
        remaining = remaining,
        lowered = lowered,
        deleted = evaluation.stale.filterNot { it in carriedOld },
        carried = carried,
    )
}

/** Reset: every baselinable measurement becomes a row at its current value; hard violations remain. */
internal fun resetStructureBaseline(measurements: List<Measurement>): BaselineUpdate = BaselineUpdate(
    rows = measurements.filter { it.rule.baselinable }
        .map { BaselineRow(it.rule, it.key, it.value) }
        .sortedWith(::compareRows),
    remaining = measurements.filterNot { it.rule.baselinable }.map { StructureViolation(it) },
)

/**
 * A new measurement looks moved from a stale row when exactly one stale row and exactly one new
 * measurement of the same carryable rule share an identity (file basename, or `basename#name`).
 */
private fun moveCandidates(newMeasurements: List<Measurement>, stale: List<BaselineRow>): Map<Measurement, BaselineRow> {
    val staleByIdentity = stale.filter { it.rule.carryable }.groupBy { it.rule to carryIdentity(it.rule, it.key) }
    val newByIdentity = newMeasurements.filter { it.rule.carryable }.groupBy { it.rule to carryIdentity(it.rule, it.key) }
    return newByIdentity.mapNotNull { (identity, candidates) ->
        val old = staleByIdentity[identity]
        if (candidates.size == 1 && old != null && old.size == 1) candidates.single() to old.single() else null
    }.toMap()
}

private fun carryIdentity(rule: StructureRule, key: String): String =
    if (rule == StructureRule.FUNCTION_LINES) {
        key.substringBeforeLast('#').substringAfterLast('/') + "#" + key.substringAfterLast('#')
    } else {
        key.substringAfterLast('/')
    }

/** The failure text of verifySourceStructure; blocks sorted by rule id, entries by key. */
internal fun structureFailureMessage(
    violations: List<StructureViolation>,
    stale: List<BaselineRow>,
    reportPath: String? = null,
): String = buildString {
    append("Source structure verification failed with ${violations.size + stale.size} violation(s). ")
    append("Baseline: $SOURCE_STRUCTURE_BASELINE_PATH. ")
    append("Rules: $ARCHITECTURE_DOCUMENT_PATH (section \"Size limits and ratchets\").\n")
    violations.groupBy { it.measurement.rule }.toSortedMap(compareBy { it.id }).forEach { (rule, entries) ->
        append(rule.id).append(" (")
        if (rule.baselinable) append("limit ${rule.limit} ${rule.unit}; a baselined item may not grow") else append("hard rule, never baselined")
        append("):\n")
        entries.sortedWith(compareBy({ it.measurement.key }, { it.measurement.line })).forEach { appendViolation(it) }
        append("   Fix: ").append(rule.fix).append('\n')
    }
    if (stale.isNotEmpty()) {
        append("stale baseline rows:\n")
        stale.sortedWith(::compareRows).forEach { append(" - ${it.rule.id} ${it.key}: within the limit or gone\n") }
        append("   Fix: run ./gradlew $UPDATE_SOURCE_STRUCTURE_BASELINE_TASK ")
        append("(it only lowers ceilings, deletes stale rows and carries moved items).\n")
    }
    if (reportPath != null) append("Full report: ").append(reportPath).append('\n')
}.trimEnd('\n')

private fun StringBuilder.appendViolation(violation: StructureViolation) {
    val measurement = violation.measurement
    val rule = measurement.rule
    append(" - ").append(measurement.key)
    if (measurement.line > 0) append(" (line ${measurement.line})")
    if (rule.baselinable) {
        append(": ${measurement.value} ${rule.unit}, ")
        append(violation.ceiling?.let { "ceiling $it" } ?: "not baselined")
        violation.movedFrom?.let { append("; looks moved from ").append(it) }
    } else if (rule.unit.isNotEmpty()) {
        append(": ${measurement.value} ${rule.unit}")
    }
    append('\n')
    measurement.detail.lineSequence().filter { it.isNotEmpty() }.forEach { append("   ").append(it).append('\n') }
}

/** The report adds every baseline row with its current value and slack to the verification outcome. */
internal fun structureReport(outcome: String, rows: List<BaselineRow>, measurements: List<Measurement>): String = buildString {
    append(outcome.trimEnd('\n')).append("\n\n")
    val values = measurements.filter { it.rule.baselinable }.associate { (it.rule to it.key) to it.value }
    append("Baseline rows (${rows.size}):\n")
    rows.sortedWith(::compareRows).forEach { row ->
        val value = values[row.rule to row.key]
        append(" - ${row.rule.id} ${row.key}: ")
        if (value == null) append("within the limit or gone, ceiling ${row.ceiling}")
        else append("$value ${row.rule.unit}, ceiling ${row.ceiling}, slack ${row.ceiling - value}")
        append('\n')
    }
}

internal fun missingStructureBaselineMessage(): String =
    "Missing $SOURCE_STRUCTURE_BASELINE_PATH; create it with ./gradlew $UPDATE_SOURCE_STRUCTURE_BASELINE_TASK " +
        "-P$RESET_SOURCE_STRUCTURE_BASELINE_PROPERTY=true"

internal const val NOT_BASELINED_WITHOUT_RESET = "Not baselined without a reset; change the code instead."

private fun compareRows(a: BaselineRow, b: BaselineRow): Int =
    a.rule.id.compareTo(b.rule.id).takeIf { it != 0 } ?: a.key.compareTo(b.key)

private fun String.isBaselineComment(): Boolean = this == "#" || startsWith("# ")

private const val BASELINE_SCHEMA_LINE = "schema\tkinetickk-source-structure-baseline/v1"

private val BASELINE_HEADER = listOf(
    "# SPDX-FileCopyrightText: 2026 Vladislav Tomilov",
    "# SPDX-License-Identifier: GPL-3.0-or-later",
    "# Ceilings for verifySourceStructure (docs/project/ARCHITECTURE.md). Lower only: ./gradlew updateSourceStructureBaseline",
)

private val CEILING = Regex("[1-9][0-9]*")
