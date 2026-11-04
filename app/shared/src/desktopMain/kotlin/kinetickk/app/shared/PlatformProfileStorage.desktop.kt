// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.foundation.diagnostics.CrashDiagnostics

internal actual fun createPlatformProfilePersistenceCapability(diagnostics: CrashDiagnostics): ProfilePersistenceCapability =
    DesktopProfilePersistenceCapability(
        diagnostics = diagnostics,
        profileNode = {
            Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_PROFILE_NODE)
        },
    )

private class DesktopProfilePersistenceCapability(
    private val profileNode: () -> Preferences,
    private val diagnostics: CrashDiagnostics,
) : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult {
        val node = try {
            profileNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceReadResult.Failed.also { diagnostics.recordProfileRead(it) }
        } catch (_: IllegalStateException) {
            return ProfilePersistenceReadResult.Failed.also { diagnostics.recordProfileRead(it) }
        }
        return desktopProfileReadCall(
            exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT,
            loadKeyNames = node::keys,
            loadExactValue = {
                node.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT, null)
            },
        ).also { diagnostics.recordProfileRead(it) }
    }

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        desktopProfilePayloadAdmission(payload.length)?.let { return it }
        diagnostics.recordProfileWriteAttempt(payload)
        val node = try {
            profileNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION.also { diagnostics.recordProfileWriteResult(payload, it) }
        } catch (_: IllegalStateException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION.also { diagnostics.recordProfileWriteResult(payload, it) }
        }
        return desktopProfileMutationCall(
            mutate = {
                node.put(ProfilePersistenceContract.DESKTOP_SNAPSHOT, payload)
            },
            flush = node::flush,
        ).also { diagnostics.recordProfileWriteResult(payload, it) }
    }
}

internal fun desktopProfileReadCall(
    exactKey: String,
    loadKeyNames: () -> Array<String>,
    loadExactValue: () -> String?,
): ProfilePersistenceReadResult {
    val storedKeys = try {
        loadKeyNames()
    } catch (_: BackingStoreException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    desktopPreferenceKeyCountAdmission(storedKeys.size)?.let { return it }
    val keyIsPresent = storedKeys.any { storedKey -> storedKey == exactKey }
    if (!keyIsPresent) {
        return ProfilePersistenceReadResult.Observed(null)
    }
    val payload = try {
        loadExactValue()
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    return if (payload == null) {
        ProfilePersistenceReadResult.Failed
    } else {
        ProfilePersistenceReadResult.Observed(payload)
    }
}

internal fun desktopProfileMutationCall(
    mutate: () -> Unit,
    flush: () -> Unit,
): ProfilePersistenceMutationResult {
    try {
        mutate()
    } catch (_: IllegalStateException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalArgumentException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    }
    return try {
        flush()
        ProfilePersistenceMutationResult.COMPLETED
    } catch (_: BackingStoreException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    }
}

internal const val MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE: Int = 64

internal fun desktopPreferenceKeyCountAdmission(keyCount: Int): ProfilePersistenceReadResult? {
    require(keyCount >= 0) { "Desktop preference key count must be non-negative" }
    return if (keyCount <= MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE) {
        null
    } else {
        ProfilePersistenceReadResult.Failed
    }
}

/** Pure pre-provider admission for the Preferences value-size boundary. */
internal fun desktopProfilePayloadAdmission(valueLength: Int): ProfilePersistenceMutationResult? {
    require(valueLength >= 0) { "Profile persistence payload length must be non-negative" }
    return if (valueLength <= Preferences.MAX_VALUE_LENGTH) {
        null
    } else {
        ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    }
}
