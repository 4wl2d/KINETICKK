// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.KeynotePolicy
import kinetickk.ball.content.api.RunVarietyContent

/** The content with every run-variety system switched off: the run plays as it did before them. */
internal fun GameplayContentSnapshot.withoutRunVariety(): GameplayContentSnapshot = copy(variety = RunVarietyContent.DISABLED)

/** The content with the Keynote switched off: no featured tag or element, no Keynote floors and no Elite bias. */
internal fun GameplayContentSnapshot.withoutKeynote(): GameplayContentSnapshot = withKeynote(KeynotePolicy(enabled = false))

/** The content with [keynote] in place of the shipped Keynote policy. */
internal fun GameplayContentSnapshot.withKeynote(keynote: KeynotePolicy): GameplayContentSnapshot =
    copy(variety = variety.copy(keynote = keynote))
