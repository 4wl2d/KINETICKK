// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

/** All ability charge derives from accepted physical movement and combat. */
internal data class CharacterRuntime(
    val charge: Float = 0f,
    val barrier: Float = 0f,
    val barrierTime: Float = 0f,
    val previousSpeed: Float = 0f,
    val previousHeading: Float = 0f,
    val turnArc: Float = 0f,
    val turnDistance: Float = 0f,
    val wasBraking: Boolean = false,
    val parryWindow: Float = 0f,
    val parryCooldown: Float = 0f,
    val ringRadius: Float = 55f,
    val dashSequence: Int = 0,
    val dashOrigin: WorldPoint? = null,
    val dashRecorded: Boolean = false,
    val ramPower: Float = 0f,
    val lattice: List<WorldPoint> = emptyList(),
    val latticeTime: Float = 0f,
)
