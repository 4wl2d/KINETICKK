// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.HudTrialPanelLayout
import kinetickk.ball.gameplay.interaction.canvas.activeTrial
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.containsInclusive
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max

/** A pointer result is either a live-run action or a request for the Session-owned host. */
sealed interface GameplayInput {
    data class Action(val action: GameplayInteractionPulse) : GameplayInput
    data object OpenSettings : GameplayInput
    data object OpenWorkshop : GameplayInput
    data object OpenRebirth : GameplayInput
    data object OpenCodex : GameplayInput
    data object ExitToHome : GameplayInput
    data object RestartRun : GameplayInput
    data object TogglePerformance : GameplayInput
}

/**
 * Interaction-ephemeral values required for deterministic canvas hit testing: [controlMask] places
 * the skill row and the Apex ([RunningControlMask]); [skillSlots] has bit n set while slot n holds a
 * skill (a press on an empty slot is a HUD position that dispatches nothing).
 */
internal data class GameplayHitTestState(
    val phase: GamePhase,
    val screenWidth: Float,
    val screenHeight: Float,
    val uiScale: Float,
    val choiceCount: Int,
    val choicesCanReroll: Boolean,
    val controlMask: Int = RunningControlMask.NONE,
    val skillSlots: Int = 0,
)

/** Bit n set while skill slot n holds a skill. */
internal fun GameplayRenderModel.filledSkillSlots(): Int {
    var bits = 0
    val slots = skillBar.slots
    for (index in 0 until slots.size) if (slots[index].skill >= 0) bits = bits or (1 shl index)
    return bits
}

/**
 * The gameplay surface in device pixels. The simulation viewport is dp; the HUD, the touch
 * controls and the Compose overlays are laid out in device pixels with [hudScale].
 */
internal val GameplayRenderModel.hudWidth: Float get() = screenWidth * viewportDensity
internal val GameplayRenderModel.hudHeight: Float get() = screenHeight * viewportDensity

/**
 * px per dp of the HUD, the running controls and the run's overlays: [GameplayRenderModel.uiScale] at the
 * applied interface scale [interfaceScale] (1 = 100 %). The world keeps the raw viewport density; drawing
 * and hit tests use this one value, so they always agree.
 */
internal fun GameplayRenderModel.hudScale(interfaceScale: Float): Float = hudScale(uiScale, viewportDensity, interfaceScale)

/** [GameplayRenderModel.hudScale] from its parts: the model's [uiScale] at 100 %, else the scaled viewport density. */
internal fun hudScale(uiScale: Float, viewportDensity: Float, interfaceScale: Float): Float =
    if (interfaceScale == 1f) uiScale else max(1f, viewportDensity * interfaceScale)

/**
 * Maps gameplay canvas coordinates (device pixels) without encoding any app destination in domain state;
 * [interfaceScale] is the applied interface scale the HUD is laid out at.
 */
fun GameplayRenderModel.resolveGameplayPress(x: Float, y: Float, interfaceScale: Float = 1f): GameplayInput? =
    resolveGameplayPress(
        phase = phase,
        screenWidth = hudWidth,
        screenHeight = hudHeight,
        uiScale = hudScale(interfaceScale),
        controlMask = RunningControlMask.of(this),
        skillSlots = filledSkillSlots(),
        x = x,
        y = y,
    )

fun GameplayRenderModel.isHudControlPosition(x: Float, y: Float, interfaceScale: Float = 1f): Boolean =
    isHudControlPosition(
        phase = phase,
        screenWidth = hudWidth,
        screenHeight = hudHeight,
        uiScale = hudScale(interfaceScale),
        controlMask = RunningControlMask.of(this),
        x = x,
        y = y,
    ) || isTrialInfoPosition(x, y, interfaceScale)

/** The running control at ([x], [y]) in canvas px, or null (live mask; any phase). */
internal fun GameplayRenderModel.runningControlTargetAt(x: Float, y: Float, interfaceScale: Float = 1f): RunningControlTarget? =
    runningControlTargetAt(hudWidth, hudHeight, hudScale(interfaceScale), RunningControlMask.of(this), x, y)

/**
 * Whether ([x], [y]) is on the active trial panel's (!) target. The pointer steers everywhere else;
 * a press here toggles the rules and never steers (the host keeps the open state).
 */
internal fun GameplayRenderModel.isTrialInfoPosition(x: Float, y: Float, interfaceScale: Float = 1f): Boolean {
    if (phase != GamePhase.RUNNING || activeTrial() == null) return false
    val scale = hudScale(interfaceScale)
    return TrialInfoHitLayout.update(hudWidth, hudHeight, scale, settings.textScale).infoTargetContains(x, y, scale)
}

/** Pointer-thread scratch layout for [isTrialInfoPosition] (never shared with drawing). */
private val TrialInfoHitLayout = HudTrialPanelLayout()

internal fun GameplayHitTestState.resolveGameplayPress(x: Float, y: Float): GameplayInput? =
    resolveGameplayPress(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        controlMask = controlMask,
        skillSlots = skillSlots,
        x = x,
        y = y,
    )

internal fun GameplayHitTestState.isHudControlPosition(x: Float, y: Float): Boolean =
    isHudControlPosition(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        controlMask = controlMask,
        x = x,
        y = y,
    )

private fun resolveGameplayPress(
    phase: GamePhase,
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    controlMask: Int,
    skillSlots: Int,
    x: Float,
    y: Float,
): GameplayInput? = when (phase) {
    GamePhase.RUNNING -> runningControlTargetAt(screenWidth, screenHeight, uiScale, controlMask, x, y)
        ?.let { runningControlInput(it, skillSlots) }
    GamePhase.PAUSED -> resolvePausePress(screenWidth, screenHeight, uiScale, x, y)
    GamePhase.CHOICE -> null // Compose activates on click, after scroll gestures resolve.
    GamePhase.GAME_OVER, GamePhase.VICTORY -> null // TerminalContent owns click and scroll handling.
}

private fun isHudControlPosition(
    phase: GamePhase,
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    controlMask: Int,
    x: Float,
    y: Float,
): Boolean = phase == GamePhase.RUNNING &&
    runningControlTargetAt(screenWidth, screenHeight, uiScale, controlMask, x, y) != null

private fun resolvePausePress(
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): GameplayInput? {
    val target = pauseLayoutGeometry(screenWidth, screenHeight, uiScale)
        .actions
        .firstOrNull { containsInclusive(it.bounds, x, y) }
        ?.target
        ?: return null
    return when (target) {
        PauseTarget.RESUME -> GameplayInput.Action(GameplayInteractionPulse.PauseToggled)
        PauseTarget.SETTINGS -> GameplayInput.OpenSettings
        PauseTarget.WORKSHOP -> GameplayInput.OpenWorkshop
        PauseTarget.CODEX -> GameplayInput.OpenCodex
        PauseTarget.EXIT -> GameplayInput.ExitToHome
    }
}

/**
 * What a press on [target] does: Dash, Brake, pause and performance as before; a skill slot casts
 * when it holds a skill ([skillSlots] bit), else nothing; the Apex control requests the Apex.
 */
internal fun runningControlInput(target: RunningControlTarget, skillSlots: Int): GameplayInput? = when (target) {
    RunningControlTarget.DASH -> GameplayInput.Action(GameplayInteractionPulse.DashRequested)
    RunningControlTarget.BRAKE -> GameplayInput.Action(
        GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = true),
    )
    RunningControlTarget.PERFORMANCE -> GameplayInput.TogglePerformance
    RunningControlTarget.PAUSE -> GameplayInput.Action(GameplayInteractionPulse.PauseToggled)
    RunningControlTarget.SKILL_1 -> skillInput(0, skillSlots)
    RunningControlTarget.SKILL_2 -> skillInput(1, skillSlots)
    RunningControlTarget.SKILL_3 -> skillInput(2, skillSlots)
    RunningControlTarget.APEX -> GameplayInput.Action(GameplayInteractionPulse.ApexRequested)
}

private fun skillInput(slot: Int, skillSlots: Int): GameplayInput? =
    if (skillSlots and (1 shl slot) == 0) null else GameplayInput.Action(GameplayInteractionPulse.SkillRequested.fromValidated(slot))

private fun runningControlTargetAt(
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    controlMask: Int,
    x: Float,
    y: Float,
): RunningControlTarget? {
    var matched: RunningControlTarget? = null
    forEachRunningControlBounds(
        screenWidth,
        screenHeight,
        uiScale,
        controlMask,
    ) { target, left, top, right, bottom ->
        if (matched != null) return@forEachRunningControlBounds
        val hit = x in left..right && y in top..bottom
        if (hit) matched = target
    }
    return matched
}
