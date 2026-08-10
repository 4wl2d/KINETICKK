// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.profile.api.ProfileStore
import kinetickk.ball.profile.resource.createPlatformProfileResource

fun createPlatformProfileStore(): ProfileStore =
    DefaultProfileStore(createPlatformProfileResource())
