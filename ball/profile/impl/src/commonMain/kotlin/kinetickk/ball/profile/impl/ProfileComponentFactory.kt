// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.resource.createPlatformProfileResource

fun createPlatformProfileComponent(policy: ProfilePolicySnapshot): ProfilePort =
    DefaultProfileComponent(
        resource = createPlatformProfileResource(),
        policy = policy,
    )
