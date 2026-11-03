// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun createPlatformProfilePersistenceCapability(diagnostics: CrashDiagnostics): ProfilePersistenceCapability =
    WebProfilePersistenceCapability()

private class WebProfilePersistenceCapability : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult = readWebProfileSnapshot()

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult =
        writeWebProfileSnapshot(payload)
}

@OptIn(ExperimentalWasmJsInterop::class)
private external interface WebStorageReadCall : JsAny {
    val status: String
    val payload: String?
}

private fun readWebProfileSnapshot(): ProfilePersistenceReadResult =
    webStorageRead(ProfilePersistenceContract.WEB_SNAPSHOT).toPersistenceResult()

private fun writeWebProfileSnapshot(payload: String): ProfilePersistenceMutationResult =
    webStorageWrite(ProfilePersistenceContract.WEB_SNAPSHOT, payload).toPersistenceMutationResult()

private fun WebStorageReadCall.toPersistenceResult(): ProfilePersistenceReadResult = when (status) {
    WEB_STORAGE_OBSERVED -> ProfilePersistenceReadResult.Observed(payload)
    WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceReadResult.Failed
    else -> error("Web Storage read returned an unknown provider status")
}

private fun String.toPersistenceMutationResult(): ProfilePersistenceMutationResult = when (this) {
    WEB_STORAGE_COMPLETED -> ProfilePersistenceMutationResult.COMPLETED
    WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    else -> error("Web Storage mutation returned an unknown provider status")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun webStorageRead(key: String): WebStorageReadCall = js(
    """{
        try {
            const exactStorage = globalThis.localStorage;
            return { status: 'observed', payload: exactStorage.getItem(key) };
        } catch (failure) {
            if (
                typeof DOMException !== 'undefined' &&
                failure instanceof DOMException &&
                failure.name === 'SecurityError'
            ) {
                return { status: 'failed-before-execution', payload: null };
            }
            throw failure;
        }
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun webStorageWrite(key: String, payload: String): String = js(
    """{
        try {
            const exactStorage = globalThis.localStorage;
            exactStorage.setItem(key, payload);
            return 'completed';
        } catch (failure) {
            if (
                typeof DOMException !== 'undefined' &&
                failure instanceof DOMException &&
                (failure.name === 'SecurityError' || failure.name === 'QuotaExceededError')
            ) {
                return 'failed-before-execution';
            }
            throw failure;
        }
    }""",
)

private const val WEB_STORAGE_OBSERVED: String = "observed"
private const val WEB_STORAGE_COMPLETED: String = "completed"
private const val WEB_STORAGE_FAILED_BEFORE_EXECUTION: String = "failed-before-execution"
