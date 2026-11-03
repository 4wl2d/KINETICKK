// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kinetickk.ball.gameplay.interaction.layout.hudHeight
import kinetickk.ball.gameplay.interaction.layout.hudWidth
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

/**
 * What the composed gameplay host depends on. A running frame changes positions every frame but
 * these facts rarely, so the host recomposes only when one of them changes.
 */
internal data class GameplayCompositionFacts(
    val phase: GamePhase,
    val hudWidth: Float,
    val hudHeight: Float,
    val uiScale: Float,
    /** The viewport's raw density (the HUD scale at another interface scale, [hudScale]). */
    val viewportDensity: Float,
    val textScale: Float,
    val choiceCount: Int,
    val choicesCanReroll: Boolean,
    val braking: Boolean,
    val overheated: Boolean,
    val dashReady: Boolean,
    /** [RunningControlMask]: whether the skill row and the Apex control are placed. */
    val controlMask: Int,
    val coreShape: CoreShape,
    /** Per skill slot: the skill ordinal (−1 empty), ready, active. */
    val skill0: Int,
    val skill0Ready: Boolean,
    val skill0Active: Boolean,
    val skill1: Int,
    val skill1Ready: Boolean,
    val skill1Active: Boolean,
    val skill2: Int,
    val skill2Ready: Boolean,
    val skill2Active: Boolean,
    val apexReady: Boolean,
    val apexActive: Boolean,
) {
    fun skill(slot: Int): Int = when (slot) {
        0 -> skill0
        1 -> skill1
        else -> skill2
    }

    fun skillReady(slot: Int): Boolean = when (slot) {
        0 -> skill0Ready
        1 -> skill1Ready
        else -> skill2Ready
    }

    fun skillActive(slot: Int): Boolean = when (slot) {
        0 -> skill0Active
        1 -> skill1Active
        else -> skill2Active
    }
}

internal fun GameplayRenderModel.compositionFacts(): GameplayCompositionFacts {
    val slots = skillBar.slots
    return GameplayCompositionFacts(
        phase = phase,
        hudWidth = hudWidth,
        hudHeight = hudHeight,
        uiScale = uiScale,
        viewportDensity = viewportDensity,
        textScale = settings.textScale,
        choiceCount = choices.size,
        choicesCanReroll = choicesCanReroll,
        braking = braking,
        overheated = overheated,
        dashReady = dashReady,
        controlMask = RunningControlMask.of(this),
        coreShape = coreShape,
        skill0 = slots[0].skill,
        skill0Ready = slots[0].ready,
        skill0Active = slots[0].active01 > 0f,
        skill1 = slots[1].skill,
        skill1Ready = slots[1].ready,
        skill1Active = slots[1].active01 > 0f,
        skill2 = slots[2].skill,
        skill2Ready = slots[2].ready,
        skill2Active = slots[2].active01 > 0f,
        apexReady = apex.ready,
        apexActive = apex.active01 > 0f,
    )
}
