// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget

/**
 * The running HUD's multi-pointer model: which of up to [MAX_POINTERS] pressed pointers steers the
 * singularity and which hold a control, so a thumb can steer while another taps Dash, a skill or
 * holds Brake. One pointer steers at a time (the first one down off the controls); later ones off
 * the controls wait as IDLE and the oldest takes over when the steering pointer lifts. A pointer that
 * went down on a control (or another HUD position) never steers, even after sliding off it. Pointers
 * keep their roles while the run is away from RUNNING (a choice, a pause), so a thumb held through it
 * steers and brakes again on return. Pure and pointer-thread confined: fixed arrays, no allocation.
 */
internal class RunningTouchTracker {
    private val ids = LongArray(MAX_POINTERS)
    private val roles = IntArray(MAX_POINTERS) // FREE, STEER, CONTROL, IDLE
    private val targets = IntArray(MAX_POINTERS) { NO_TARGET } // RunningControlTarget ordinal
    private val order = LongArray(MAX_POINTERS) // down sequence, for the oldest IDLE pointer
    private var sequence = 0L
    private var away = false // the run is not RUNNING: a held Brake does not apply

    /**
     * A pointer went down on [target] (null off the controls). [hudPosition] marks a press on a HUD
     * position that is not a control (it holds the pointer like one but has no target). Returns
     * [DOWN_STEER], [DOWN_CONTROL], [DOWN_IDLE], or [DOWN_FULL] when every slot is taken (the pointer
     * is ignored until it lifts).
     */
    fun down(id: Long, target: RunningControlTarget?, hudPosition: Boolean = false): Int {
        if (indexOf(id) >= 0) up(id)
        val slot = roles.indexOf(FREE)
        if (slot < 0) return DOWN_FULL
        ids[slot] = id
        order[slot] = sequence++
        targets[slot] = target?.ordinal ?: NO_TARGET
        roles[slot] = when {
            target != null || hudPosition -> CONTROL
            roles.indexOf(STEER) < 0 -> STEER
            else -> IDLE
        }
        return when (roles[slot]) {
            CONTROL -> DOWN_CONTROL
            STEER -> DOWN_STEER
            else -> DOWN_IDLE
        }
    }

    /** True when pointer [id] steers (its moves go to the singularity). */
    fun move(id: Long): Boolean {
        val slot = indexOf(id)
        return slot >= 0 && roles[slot] == STEER
    }

    /** The control pointer [id] holds, or null (steering, idle or unknown pointers). */
    fun target(id: Long): RunningControlTarget? {
        val slot = indexOf(id)
        if (slot < 0 || roles[slot] != CONTROL || targets[slot] == NO_TARGET) return null
        return RunningControlTarget.entries[targets[slot]]
    }

    /**
     * Pointer [id] lifted. Returns [UP_BRAKE_RELEASED] when it was the last one holding Brake while
     * the run is RUNNING, `UP_STEER_PROMOTED + slot` when it steered and the oldest IDLE pointer
     * (read its id with [idAt]) now steers, else [UP_NONE].
     */
    fun up(id: Long): Int {
        val slot = indexOf(id)
        if (slot < 0) return UP_NONE
        val role = roles[slot]
        val target = targets[slot]
        roles[slot] = FREE
        targets[slot] = NO_TARGET
        if (role == CONTROL && target == RunningControlTarget.BRAKE.ordinal) {
            return if (away || holds(RunningControlTarget.BRAKE)) UP_NONE else UP_BRAKE_RELEASED
        }
        if (role != STEER) return UP_NONE
        var oldest = -1
        for (index in 0 until MAX_POINTERS) {
            if (roles[index] == IDLE && (oldest < 0 || order[index] < order[oldest])) oldest = index
        }
        if (oldest < 0) return UP_NONE
        roles[oldest] = STEER
        return UP_STEER_PROMOTED + oldest
    }

    /** The pointer id in [slot] (after [UP_STEER_PROMOTED]). */
    fun idAt(slot: Int): Long = ids[slot]

    /** Whether any pointer holds [target]. */
    fun holds(target: RunningControlTarget): Boolean {
        for (index in 0 until MAX_POINTERS) {
            if (roles[index] == CONTROL && targets[index] == target.ordinal) return true
        }
        return false
    }

    /** Whether no pointer is tracked. */
    val isEmpty: Boolean get() = roles.all { it == FREE }

    /**
     * Whether the run is RUNNING at the current pointer event. Every pointer keeps its role either way.
     * True when this call changes whether a held Brake applies: leaving RUNNING releases it and
     * returning engages it again (dispatch `BrakeChanged(active = running)`).
     */
    fun running(running: Boolean): Boolean {
        if (away != running) return false
        away = !running
        return holds(RunningControlTarget.BRAKE)
    }

    private fun indexOf(id: Long): Int {
        for (index in 0 until MAX_POINTERS) {
            if (roles[index] != FREE && ids[index] == id) return index
        }
        return -1
    }

    companion object {
        const val MAX_POINTERS = 5

        const val DOWN_STEER = 0
        const val DOWN_CONTROL = 1
        const val DOWN_IDLE = 2
        const val DOWN_FULL = 3

        const val UP_NONE = -2
        const val UP_BRAKE_RELEASED = -1

        /** `UP_STEER_PROMOTED + slot`: the steering pointer lifted and the one in `slot` steers now. */
        const val UP_STEER_PROMOTED = 0

        private const val FREE = 0
        private const val STEER = 1
        private const val CONTROL = 2
        private const val IDLE = 3
        private const val NO_TARGET = -1
    }
}

/**
 * Presentation-only record of the running controls, written by the key handler and the touch
 * tracker in render time: when each control was last pressed (its 90 ms face flash), when an Apex
 * press found it cooling (the badge's refusal shake), whether key caps show ([keyHints]: set by
 * any gameplay key, cleared by a touch; a mouse leaves it) and whether the input is touch ([touchInput]:
 * set by a touch, cleared by any gameplay key; reward cards then name a Z/X/C slot by number). It never
 * feeds gameplay. One instance per gameplay host; no allocation.
 */
internal class RunningControlPressLog {
    private val pressedAt = FloatArray(RunningControlTarget.entries.size) { Float.NEGATIVE_INFINITY }

    var apexRefusedAt: Float = Float.NEGATIVE_INFINITY
        private set

    var keyHints: Boolean = false

    var touchInput: Boolean = false

    /** The Show FPS counter's value the PERFORMANCE slot draws (`GameplayFpsCounter.NO_VALUE` before one). */
    var fps: Int = -1

    fun press(target: RunningControlTarget, renderTime: Float) {
        pressedAt[target.ordinal] = renderTime
    }

    fun refuseApex(renderTime: Float) {
        apexRefusedAt = renderTime
    }

    /** 1 while [target]'s press flash shows ([PRESS_FLASH_SECONDS] after the press), else 0. */
    fun pressFlash(target: RunningControlTarget, renderTime: Float): Float {
        val age = renderTime - pressedAt[target.ordinal]
        return if (age >= 0f && age < PRESS_FLASH_SECONDS) 1f else 0f
    }

    companion object {
        const val PRESS_FLASH_SECONDS = 0.09f
    }
}
