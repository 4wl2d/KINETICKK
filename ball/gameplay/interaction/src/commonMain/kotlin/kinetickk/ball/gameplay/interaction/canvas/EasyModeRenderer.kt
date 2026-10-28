// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix

/** Pastel lilac of Baby mode accents. */
internal fun easyPastelLilac(roles: KkRolePalette): Color = kkMix(roles.pol, Kk.Bone, 0.5f)

/** Pastel volt of the Baby mode ribbon: still "you", never the cursor's soft green. */
internal fun easyPastelYou(roles: KkRolePalette): Color = kkMix(roles.you, Kk.Bone, 0.35f)
