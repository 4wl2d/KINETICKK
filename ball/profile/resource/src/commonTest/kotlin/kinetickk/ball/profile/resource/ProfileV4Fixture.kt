// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileV4Snapshot

internal val TestContentVersion: ContentVersion = ContentVersion("test-content")

internal fun testV4Snapshot(
    profile: PlayerProfile = PlayerProfile(),
    revision: Long = 0L,
    legacyResetConfirmed: Boolean = false,
    contentVersion: ContentVersion = TestContentVersion,
): ProfileV4Snapshot = ProfileV4Snapshot(
    contentVersion = contentVersion,
    revision = ProfileRevision(revision),
    legacyResetConfirmed = legacyResetConfirmed,
    profile = profile,
)

internal fun requireEncoded(snapshot: ProfileV4Snapshot): String =
    (ProfileV4Codec.encode(snapshot) as ProfileV4EncodeResult.Encoded).payload
