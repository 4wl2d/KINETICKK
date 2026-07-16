// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.read

/** Profile-owned names for the shared mechanical read contract. */
typealias ProfileConsistencyStamp = kinetickk.application.runtime.ConsistencyStamp
typealias ProfileReadResult<Payload> = kinetickk.application.runtime.ReadResult<Payload>
internal typealias ProfileCommittedStateSnapshot<State> =
    kinetickk.application.runtime.CommittedStateSnapshot<State>
internal typealias ProfileReadContext = kinetickk.application.runtime.ReadContext
