// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BenchmarkHarnessIntegrityTest {
    @Test
    fun validOperationBindsTimedResultAndIndependentOutcomeWitness() {
        var state = 0L
        val scenario = BenchmarkScenario(
            name = "valid_mutation",
            category = "harness",
            description = "Focused validation fixture.",
            metadata = mapOf("outcomeFingerprint" to "1"),
            validation = BenchmarkValidation(
                expectedTimedResult = 7L,
                expectedOutcomeWitness = 1L,
                prepareProbe = { state = 0L },
            ),
        ) { validation ->
            state += 1L
            validation.observeOutcome { state }
            7L
        }

        val evidence = validateBenchmarkScenario(scenario)

        assertEquals(7L, evidence.actualTimedResult)
        assertEquals(1L, evidence.actualOutcomeWitness)
    }

    @Test
    fun constantExpectedReturnCannotMaskNoOpMutation() {
        var state = 0L
        val noOpMutation = BenchmarkScenario(
            name = "constant_no_op_mutation",
            category = "harness",
            description = "Mutation returning the old expected result without doing the work.",
            metadata = mapOf("outcomeFingerprint" to "1"),
            validation = BenchmarkValidation(
                expectedTimedResult = 7L,
                expectedOutcomeWitness = 1L,
                prepareProbe = { state = 0L },
            ),
        ) { validation ->
            validation.observeOutcome { state }
            7L
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            validateBenchmarkScenario(noOpMutation)
        }

        assertTrue(failure.message.orEmpty().contains("published outcome witness 0; expected 1"))
    }
}
