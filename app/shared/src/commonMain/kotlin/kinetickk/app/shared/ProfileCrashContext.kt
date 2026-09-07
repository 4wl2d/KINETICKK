// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.foundation.diagnostics.CrashDiagnostics

/** Format only values already observed by the platform broker; retain no persistence authority. */
internal fun CrashDiagnostics.recordProfileRead(result: ProfilePersistenceReadResult) {
    if (result is ProfilePersistenceReadResult.Observed) {
        val payload = result.payload
        context("profile.loaded.json") { payload ?: "null" }
    }
    event("profile.read", if (result is ProfilePersistenceReadResult.Observed) "Observed" else "Failed")
}

internal fun CrashDiagnostics.recordProfileWriteAttempt(payload: String) {
    context("profile.write-attempt.json") { payload }
    context("profile.write-result") { "IN_PROGRESS (no provider result observed yet)" }
    event("profile.write", "started (${payload.length} characters)")
}

internal fun CrashDiagnostics.recordProfileWriteResult(payload: String, result: ProfilePersistenceMutationResult) {
    context("profile.write-result") { result.name }
    if (result == ProfilePersistenceMutationResult.COMPLETED) {
        context("profile.saved.json") { payload }
    }
    event("profile.write", result.name)
}
