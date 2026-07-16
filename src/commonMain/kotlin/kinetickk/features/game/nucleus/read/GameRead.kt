// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.read

/**
 * The exact committed authority snapshot supplied to a local read.
 *
 * A read may derive a payload from [state], but it cannot advance [commitRevision] or mutate the
 * retained state. The stamp returned to the caller is copied from this snapshot.
 */
data class CommittedStateSnapshot<out State>(
    val ballInstanceId: String,
    val commitRevision: ULong,
    val stateSchemaVersion: Int,
    val state: State,
)

/**
 * Marker for an authenticated actor supplied by a stronger read boundary.
 *
 * KINETICKK's selected local Standard profile has no authenticated read actor, so its Assembly
 * always supplies `null`. Keeping the canonical optional field prevents an ambient actor lookup.
 */
sealed interface AuthenticatedActorContext

data class ReadContext(
    val protocolVersion: String,
    val actorContext: AuthenticatedActorContext? = null,
)

data class ConsistencyStamp(
    val ballInstanceId: String,
    val commitRevision: ULong,
    val stateSchemaVersion: Int,
)

data class ReadResult<out Payload>(
    val payload: Payload,
    val consistencyStamp: ConsistencyStamp,
)
