// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.testing

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.impl.createContentCatalog

internal val canonicalGameplayContent: GameplayContentSnapshot =
    createContentCatalog().gameplayContent()
