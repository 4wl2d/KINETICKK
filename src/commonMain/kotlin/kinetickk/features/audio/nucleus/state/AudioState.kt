// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio.nucleus.state

internal data class AudioState(
    val musicClockSeconds: Float = 0f,
    val musicStep: ULong = 0uL,
    val closed: Boolean = false,
    val transitionSteps: Int = 0,
)
