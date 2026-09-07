// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehaviorEvidenceTest {
    private val evidence = BehaviorEvidence(":ball:example:impl", "example.AcceptanceTest", setOf("acceptsSix"))
    private val reportName = "TEST-${evidence.className}.xml"

    @Test
    fun onlyAnExecutedPassingCaseSatisfiesTheBehaviorGate() {
        assertEquals(emptyList(), verify(case()))
        listOf("failure", "error", "skipped").forEach { result ->
            assertTrue(verify(case("<$result/>")).single().contains("did not pass"))
        }
        assertTrue(verify(case().replace("acceptsSix", "someOtherTest")).single().contains("did not pass"))
        assertTrue(verify(case().replace("example.AcceptanceTest", "unrelated.AcceptanceTest"))
            .single().contains("did not pass"))
    }

    @Test
    fun aCommentOrUnexecutedTestDeclarationCannotSupplyEvidence() {
        assertTrue(verify("<testsuite><!-- ${case()} --></testsuite>").single().contains("did not pass"))
        assertTrue(behaviorEvidenceViolations(emptyMap(), listOf(evidence)).single().contains("Missing"))
    }

    @Test
    fun anUnrelatedPassingCaseCannotReplaceAFailedBoundaryProperty() {
        val failed = case("<failure message=\"seventh target was changed\"/>")
        val unrelated = case().replace("acceptsSix", "someOtherPassingBehavior")
        assertTrue(verify("<testsuites>$failed$unrelated</testsuites>").single().contains("did not pass"))
    }

    @Test
    fun executedEvidenceCoversBoundsIsolationAndTheSharedAcceptanceMechanism() {
        val byClass = runtimeBehaviorEvidence.associateBy(BehaviorEvidence::className)
        assertEquals(runtimeBehaviorEvidence.size, byClass.size)
        assertTrue(byClass.getValue("kinetickk.ball.gameplay.nucleus.simulation.GameplayCollectionBoundsTest")
            .methods.contains("arcCoilTargetsSixNearestAndLeavesSeventhUntouched"))
        assertTrue(byClass.getValue("kinetickk.ball.gameplay.nucleus.simulation.GameplayReductionIsolationTest")
            .methods.isNotEmpty())
        assertTrue(byClass.getValue("kinetickk.foundation.dispatch.InlineReplyTest")
            .methods.contains("inputAdaptationFaultRetainsAcceptedReplyAndBlocksNewRootsWithoutARefusal"))
        assertTrue(runtimeBehaviorEvidence.all { it.methods.isNotEmpty() })
    }

    @Test
    fun malformedReportsAndExternalEntitiesFailClosed() {
        assertTrue(verify("<testsuite>").single().contains("Invalid"))
        assertTrue(verify("<!DOCTYPE testsuite SYSTEM 'file:///not-a-test'><testsuite/>")
            .single().contains("Invalid"))
    }

    private fun case(result: String = "") =
        "<testsuite><testcase name=\"acceptsSix[desktop]\" classname=\"example.AcceptanceTest\">" +
            "$result</testcase></testsuite>"

    private fun verify(xml: String) = behaviorEvidenceViolations(mapOf(reportName to xml), listOf(evidence))
}
