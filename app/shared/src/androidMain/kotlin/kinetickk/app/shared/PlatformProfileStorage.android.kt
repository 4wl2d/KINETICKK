// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.foundation.diagnostics.CrashDiagnostics
import android.content.Context
import android.content.SharedPreferences
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult

private const val ANDROID_PROFILE_PREFERENCES = "kinetickk.profile.v2"
private const val ANDROID_SNAPSHOT = "snapshot"

internal actual fun createPlatformProfilePersistenceCapability(diagnostics: CrashDiagnostics): ProfilePersistenceCapability {
    val context = AndroidApplicationContext.requireContext()
    return AndroidProfilePersistenceCapability(
        profile = context.getSharedPreferences(ANDROID_PROFILE_PREFERENCES, Context.MODE_PRIVATE),
    )
}

private class AndroidProfilePersistenceCapability(
    private val profile: SharedPreferences,
) : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult =
        androidProfileReadCall(profile, ANDROID_SNAPSHOT)

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult =
        androidProfileMutationCall {
            profile.edit().putString(ANDROID_SNAPSHOT, payload)
        }
}

private fun androidProfileReadCall(
    preferences: SharedPreferences,
    exactKey: String,
): ProfilePersistenceReadResult {
    val keyIsPresent = try {
        preferences.contains(exactKey)
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    if (!keyIsPresent) return ProfilePersistenceReadResult.Observed(null)

    val payload = try {
        preferences.getString(exactKey, null)
    } catch (_: ClassCastException) {
        return ProfilePersistenceReadResult.Failed
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

private fun androidProfileMutationCall(
    prepare: () -> SharedPreferences.Editor,
): ProfilePersistenceMutationResult {
    val editor = try {
        prepare()
    } catch (_: SecurityException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalArgumentException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalStateException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    }
    return try {
        if (editor.commit()) {
            ProfilePersistenceMutationResult.COMPLETED
        } else {
            ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
        }
    } catch (_: SecurityException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    } catch (_: IllegalStateException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    }
}
