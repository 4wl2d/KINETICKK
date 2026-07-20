// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.impl

import androidx.compose.runtime.Composable
import kinetickk.feature.game.api.GameFeature

class DefaultGameFeature : GameFeature {
    @Composable
    override fun Content() {
        GameContent()
    }
}
