// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot

internal fun testSnapshot(
    profile: PlayerProfile = PlayerProfile(),
    revision: Long = 0L,
): ProfileSnapshot = ProfileSnapshot(
    revision = ProfileRevision(revision),
    profile = profile,
)

internal fun requireEncoded(snapshot: ProfileSnapshot): String =
    (ProfileCodec.encode(snapshot) as ProfileEncodeResult.Encoded).payload
