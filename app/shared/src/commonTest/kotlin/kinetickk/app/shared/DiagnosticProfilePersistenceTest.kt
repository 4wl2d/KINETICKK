// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticProfilePersistenceTest {
    @Test fun recordsExactSaveWithoutPromotingUnknownWriteToSuccess() {
        val context = mutableMapOf<String, String>()
        val diagnostics = object : CrashDiagnostics {
            override fun context(key: String, describe: () -> String) { context[key] = describe() }
            override fun event(category: String, message: String, highFrequency: Boolean) = Unit
        }
        diagnostics.recordProfileRead(ProfilePersistenceReadResult.Observed("old save"))
        assertEquals("old save", context["profile.loaded.json"])
        diagnostics.recordProfileWriteAttempt("new save")
        diagnostics.recordProfileWriteResult("new save", ProfilePersistenceMutationResult.POSSIBLE_EXECUTION)
        assertEquals("new save", context["profile.write-attempt.json"])
        assertEquals("POSSIBLE_EXECUTION", context["profile.write-result"])
        assertFalse("profile.saved.json" in context)
        diagnostics.recordProfileWriteResult("confirmed save", ProfilePersistenceMutationResult.COMPLETED)
        assertEquals("confirmed save", context["profile.saved.json"])
        diagnostics.recordProfileWriteAttempt("pending save")
        assertEquals("IN_PROGRESS (no provider result observed yet)", context["profile.write-result"])
        assertEquals("confirmed save", context["profile.saved.json"])
    }
}
