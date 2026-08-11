// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision

/** Ball-internal Nucleus-to-Interaction snapshot; not a Gameplay Application Surface. */
data class GameplayRenderSnapshot(
    val instanceId: GameplayInstanceId,
    val revision: GameplayRevision,
    val renderModel: GameplayRenderModel?,
)
