// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.ball.gameplay.interaction.layout.HudTrialPanelLayout
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.layout.hudHeight
import kinetickk.ball.gameplay.interaction.layout.hudScale
import kinetickk.ball.gameplay.interaction.layout.hudWidth
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.skillSlot
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.math.roundToInt

@Composable
internal fun GameplaySemanticControls(
    facts: GameplayCompositionFacts,
    content: GameplayContentSnapshot,
    trialDescription: String?,
    density: Density,
    hudScale: Float,
    performanceEnabled: Boolean,
    fps: IntState,
    pauseLayout: PauseLayoutGeometry?,
    onTrialInfoFocusChanged: (Boolean) -> Unit,
    onTrialInfoToggle: () -> Unit,
    onInput: (GameplayInput) -> Unit,
    onControl: (RunningControlTarget) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
    if (facts.phase == GamePhase.RUNNING && trialDescription != null) {
        TrialInfoSemanticNode(
            facts = facts,
            density = density,
            hudScale = hudScale,
            description = trialDescription,
            onFocusChanged = onTrialInfoFocusChanged,
            onToggle = onTrialInfoToggle,
        )
    }
    when (facts.phase) {
        GamePhase.RUNNING -> forEachRunningControlBounds(
            width = facts.hudWidth,
            height = facts.hudHeight,
            scale = hudScale,
            mask = facts.controlMask,
        ) { target, left, top, right, bottom ->
            val bounds = Rect(left, top, right, bottom)
            when (target) {
                RunningControlTarget.BRAKE -> BrakeSemanticControl(
                    bounds = bounds,
                    density = density,
                    active = facts.braking,
                    onPressedChange = onBrakeChanged,
                )
                RunningControlTarget.DASH -> GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.dash",
                    description = language.text(GameplayText.DashDescription),
                    state = when {
                        facts.overheated -> language.text(GameplayText.OfflineState)
                        facts.dashReady -> language.text(GameplayText.ReadyState)
                        else -> language.text(GameplayText.CoolingState)
                    },
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.DashRequested))
                    },
                )
                RunningControlTarget.PERFORMANCE -> PerformanceSemanticAction(
                    bounds = bounds,
                    density = density,
                    enabled = performanceEnabled,
                    fps = fps,
                    onClick = { onInput(GameplayInput.TogglePerformance) },
                )
                RunningControlTarget.PAUSE -> GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.pause",
                    description = language.text(GameplayText.PauseDescription),
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
                    },
                )
                RunningControlTarget.SKILL_1,
                RunningControlTarget.SKILL_2,
                RunningControlTarget.SKILL_3,
                -> {
                    val slot = target.skillSlot
                    val skill = facts.skill(slot)
                    val name = SkillId.entries.getOrNull(skill)?.let { content.skill(it).name.localizedContent(language) }
                    GameplaySemanticAction(
                        bounds = bounds,
                        density = density,
                        tag = "kinetickk.gameplay.skill.$slot",
                        description = if (name == null) {
                            language.text(GameplayText.SkillSlotEmpty, slot + 1)
                        } else {
                            language.text(GameplayText.SkillSlotDescription, slot + 1, name)
                        },
                        state = if (name == null) null else actionState(facts.skillActive(slot), facts.skillReady(slot), language),
                        onClick = { onControl(target) },
                    )
                }
                RunningControlTarget.APEX -> GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.apex",
                    description = language.text(
                        GameplayText.ApexDescription,
                        content.coreShape(facts.coreShape).apex.name.localizedContent(language),
                    ),
                    state = actionState(facts.apexActive, facts.apexReady, language),
                    onClick = { onControl(target) },
                )
            }
        }
        GamePhase.PAUSED -> requireNotNull(pauseLayout).actions.forEach { action ->
            when (action.target) {
                PauseTarget.RESUME -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.resume",
                    description = language.text(GameplayText.ResumeDescription),
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
                    },
                )
                PauseTarget.SETTINGS -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.settings",
                    description = language.text(GameplayText.SettingsDescription),
                    onClick = { onInput(GameplayInput.OpenSettings) },
                )
                PauseTarget.WORKSHOP -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.workshop",
                    description = language.text(GameplayText.WorkshopDescription),
                    onClick = { onInput(GameplayInput.OpenWorkshop) },
                )
                PauseTarget.CODEX -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.codex",
                    description = language.text(OverlayRedesignText.Codex),
                    onClick = { onInput(GameplayInput.OpenCodex) },
                )
                PauseTarget.EXIT -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.exit",
                    description = language.text(GameplayText.ExitDescription),
                    onClick = { onInput(GameplayInput.ExitToHome) },
                )
            }
        }
        GamePhase.CHOICE -> Unit // RewardContent owns the visible Compose actions.
        GamePhase.GAME_OVER, GamePhase.VICTORY -> Unit // TerminalContent owns native buttons.

    }
}

/**
 * The trial panel's (!) is drawn on the Canvas, where the pointer steers the singularity; its rules
 * are exposed as a focusable node whose description is the rule text. Focus shows the tooltip.
 */
@Composable
private fun TrialInfoSemanticNode(
    facts: GameplayCompositionFacts,
    density: Density,
    hudScale: Float,
    description: String,
    onFocusChanged: (Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    val bounds = remember(facts.hudWidth, facts.hudHeight, hudScale, facts.textScale) {
        HudTrialPanelLayout()
            .update(facts.hudWidth, facts.hudHeight, hudScale, facts.textScale)
            .infoTarget(hudScale)
    }
    val currentOnFocusChanged = rememberUpdatedState(onFocusChanged)
    DisposableEffect(Unit) {
        onDispose { currentOnFocusChanged.value(false) }
    }
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag(GAMEPLAY_TRIAL_INFO_TAG)
            .onFocusChanged { currentOnFocusChanged.value(it.isFocused) }
            .onKeyEvent { event -> activateSemanticButtonFromKey(event.key, event.type, onToggle) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onToggle()
                    true
                }
            }
            .focusable(),
    )
}

/** A skill's or the Apex's state: active while it runs, ready, else recharging. */
private fun actionState(active: Boolean, ready: Boolean, language: AppLanguage): String = language.text(
    when {
        active -> GameplayText.ActiveState
        ready -> GameplayText.ReadyState
        else -> GameplayText.RechargingState
    },
)

@Composable
@NonRestartableComposable
private fun PerformanceSemanticAction(
    bounds: Rect,
    density: Density,
    enabled: Boolean,
    fps: IntState,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = language.text(GameplayText.PerformanceDescription)
    Box(
        Modifier.placeInGameplayBounds(bounds, density)
            .testTag("kinetickk.gameplay.performance")
            .then(if (DIAGNOSTICS_ENABLED) Modifier.onKeyEvent { event ->
                activateSemanticButtonFromKey(event.key, event.type, onClick)
            }.focusable() else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                // Observe the half-second readout in semantics; changing it need not recompose the host.
                text = fpsReadoutText(language, fps.intValue)
                if (DIAGNOSTICS_ENABLED) {
                    role = Role.Button
                    stateDescription = if (enabled) language.text(GameplayText.OnState) else language.text(GameplayText.OffState)
                    onClick(label = description) { onClick(); true }
                }
            },
    )
}

@Composable
private fun GameplaySemanticAction(
    bounds: Rect,
    density: Density,
    tag: String,
    description: String,
    state: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag(tag)
            .onKeyEvent { event ->
                activateSemanticButtonFromKey(event.key, event.type, onClick)
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                state?.let { stateDescription = it }
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .focusable(),
    )
}

@Composable
private fun BrakeSemanticControl(
    bounds: Rect,
    density: Density,
    active: Boolean,
    onPressedChange: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag("kinetickk.gameplay.brake")
            .onKeyEvent { event ->
                activateSemanticButtonFromKey(event.key, event.type) {
                    onPressedChange(gameplayBrakeSemanticToggleState(active))
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = language.text(GameplayText.BrakeDescription)
                stateDescription = gameplayBrakeStateDescription(active, language)
                onClick(label = language.text(GameplayText.BrakeAction)) {
                    onPressedChange(gameplayBrakeSemanticToggleState(active))
                    true
                }
            }
            .focusable(),
    )
}

internal fun Modifier.placeInGameplayBounds(bounds: Rect, density: Density): Modifier =
    this
        .offset {
            IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
        }
        .requiredSize(
            width = with(density) { bounds.width.toDp() },
            height = with(density) { bounds.height.toDp() },
        )

internal const val GAMEPLAY_TRIAL_INFO_TAG = "kinetickk.gameplay.trial-info"
internal val GAMEPLAY_BRAKE_DESCRIPTION = GameplayText.BrakeDescription.english
internal val GAMEPLAY_BRAKE_SEMANTIC_ACTION_LABEL = GameplayText.BrakeAction.english

internal fun gameplayBrakeStateDescription(active: Boolean, language: AppLanguage = AppLanguage.English): String =
    if (active) language.text(GameplayText.PressedState) else language.text(GameplayText.ReleasedState)

/** Accessibility activation is a latch: only another explicit action releases it. */
internal fun gameplayBrakeSemanticToggleState(active: Boolean): Boolean = !active

private inline fun activateSemanticButtonFromKey(
    key: Key,
    type: KeyEventType,
    onClick: () -> Unit,
): Boolean {
    if (key != Key.Enter && key != Key.NumPadEnter && key != Key.Spacebar) return false
    return when (type) {
        KeyEventType.KeyDown -> true
        KeyEventType.KeyUp -> {
            onClick()
            true
        }
        else -> false
    }
}
