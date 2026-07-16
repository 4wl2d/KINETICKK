// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.runtime

/** Immutable state snapshot supplied to a Ball-owned query reader. */
data class CommittedStateSnapshot<out State>(
    val ballInstanceId: String,
    val commitRevision: ULong,
    val stateSchemaVersion: Int,
    val state: State,
)

/** Marker for a boundary-authenticated actor; local anonymous reads use `null`. */
sealed interface AuthenticatedActorContext

data class ReadContext(
    val protocolVersion: String,
    val actorContext: AuthenticatedActorContext? = null,
)

/** Identifies the exact authority snapshot used to produce one query result. */
data class ConsistencyStamp(
    val ballInstanceId: String,
    val commitRevision: ULong,
    val stateSchemaVersion: Int,
)

data class ReadResult<out Payload>(
    val payload: Payload,
    val consistencyStamp: ConsistencyStamp,
)
