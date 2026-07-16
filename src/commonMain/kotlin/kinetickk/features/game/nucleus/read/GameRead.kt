// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.read

/** Compatibility aliases while shared read mechanics live in the runtime package. */
typealias CommittedStateSnapshot<State> = kinetickk.application.runtime.CommittedStateSnapshot<State>
typealias AuthenticatedActorContext = kinetickk.application.runtime.AuthenticatedActorContext
typealias ReadContext = kinetickk.application.runtime.ReadContext
typealias ConsistencyStamp = kinetickk.application.runtime.ConsistencyStamp
typealias ReadResult<Payload> = kinetickk.application.runtime.ReadResult<Payload>
