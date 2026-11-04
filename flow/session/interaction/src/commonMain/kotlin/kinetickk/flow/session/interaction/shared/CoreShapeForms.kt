// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.shared

import kinetickk.ball.content.api.CoreShape

/** The form index of a character: [KkFormGeometry] and [KkIcon.Forms] follow the character order. */
internal fun CoreShape.formIndex(): Int = ordinal
