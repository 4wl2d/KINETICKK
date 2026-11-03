// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.label
import kotlin.math.roundToInt

/**
 * The Settings live region (`kinetickk.settings.announce`), always present and empty between
 * announcements: a key bound or moved and a reset are polite; a key that cannot be bound is assertive.
 */
@Composable
internal fun SettingsAnnouncer(announcement: SettingsAnnouncement?, language: AppLanguage) {
    val text = announcement?.let { settingsAnnouncementText(it, language) }
    val assertive = announcement == SettingsAnnouncement.KeyUnavailable
    Box(Modifier.testTag("kinetickk.settings.announce").semantics {
        liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
        if (text != null) contentDescription = text
    })
}

/** The words of an announcement: "Dash set to F" (plus "Active 1 moved to Space" after a swap). */
internal fun settingsAnnouncementText(announcement: SettingsAnnouncement, language: AppLanguage): String = when (announcement) {
    is SettingsAnnouncement.KeyBound -> {
        val set = language.text(SettingsRedesignText.KeySetTo, settingsActionName(announcement.action, language), announcement.key.label(language))
        val moved = announcement.moved
        val movedTo = announcement.movedTo
        if (moved == null || movedTo == null) set
        else "$set. " + language.text(SettingsRedesignText.KeyMovedTo, settingsActionName(moved, language), movedTo.label(language))
    }
    SettingsAnnouncement.KeyUnavailable -> language.text(SettingsRedesignText.KeyUnavailable)
    SettingsAnnouncement.KeysReset -> language.text(SettingsRedesignText.KeysReset)
}

/**
 * The open (!) explanation, composed last so it sits above the canvas and the Compose volume
 * strip. When it [takesPresses], every press inside the slip lands on it: a tap meant to dismiss
 * it closes it and never reaches the control it covers.
 */
@Composable
internal fun SettingsInfoSlipLayer(slip: SettingsInfoSlip, takesPresses: Boolean, onPress: () -> Unit) {
    val press by rememberUpdatedState(onPress)
    // PlacedBox puts the layer at the rounded top-left; the slip is drawn relative to it.
    val origin = Offset(slip.bounds.left.roundToInt().toFloat(), slip.bounds.top.roundToInt().toFloat())
    // Without pointer input the layer is no hit target, so presses reach the controls below.
    val presses = if (takesPresses) Modifier.pointerInput(slip) { detectTapGestures { press() } } else Modifier
    PlacedBox(slip.bounds, Modifier
        .testTag("kinetickk.settings.${slip.row.tagId}.slip")
        .then(presses)
        .drawBehind { drawSettingsInfoSlip(slip, origin) })
}

/**
 * Focusable semantic nodes over the canvas targets, in reading order. Pointer input stays with the
 * canvas and its resolver (these nodes add none); keyboard and accessibility actions reach the same
 * targets through [onActivate].
 */
@Composable
internal fun SettingsAccessibleControls(
    layout: SettingsLayout,
    model: kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel,
    language: AppLanguage,
    capture: GameAction?,
    resetArmed: Boolean,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onActivate: (SettingsTarget) -> Unit,
) {
    val preferences = model.preferences
    val platform = layout.platform
    val density = layout.density
    SettingsSemanticTarget(layout.back, "kinetickk.settings.back", language.text(SettingsRedesignText.Back), Role.Button,
        SettingsTarget.Back, onFocusChanged, onActivate)
    Box(Modifier.fillMaxSize().selectableGroup()) {
        layout.tabs.forEach { tab ->
            SettingsSemanticTarget(
                tab.bounds, "kinetickk.settings.group.${tab.group.name.lowercase()}", language.text(tab.group.label), Role.Tab,
                SettingsTarget.Tab(tab.group), onFocusChanged, onActivate, selected = layout.group == tab.group,
            )
        }
    }
    layout.rows.forEach { row ->
        val label = row.row.label(language)
        val tag = "kinetickk.settings.${row.row.tagId}"
        when (row.control) {
            SettingsControl.SEGMENTED -> Box(Modifier.fillMaxSize().selectableGroup()) {
                val selected = row.row.selectedOption(preferences, platform)
                row.options.forEachIndexed { option, cell ->
                    val optionLabel = row.row.optionLabel(option, language, platform)
                    SettingsSemanticTarget(
                        cell, "$tag.${row.row.optionId(option, platform)}",
                        optionLabel, Role.RadioButton, SettingsTarget.Option(row.row, option), onFocusChanged, onActivate,
                        selected = option == selected,
                    )
                }
            }
            SettingsControl.TOGGLE -> row.toggle?.let { toggle ->
                SettingsSemanticTarget(
                    toggle, "$tag.toggle", label, Role.Switch,
                    SettingsTarget.Toggle(row.row), onFocusChanged, onActivate, toggled = row.row.isOn(preferences, platform),
                )
            }
            SettingsControl.SLIDER -> if (row.row != SettingsRow.MASTER_VOLUME) {
                val value = settingValue(preferences, row.row, language, platform)
                val enabled = row.row.isEnabled(preferences)
                row.decrease?.let {
                    SettingsSemanticTarget(it, "$tag.decrease", "$label −", Role.Button,
                        SettingsTarget.Step(row.row, -1), onFocusChanged, onActivate, state = value, enabled = enabled)
                }
                row.increase?.let {
                    SettingsSemanticTarget(it, "$tag.increase", "$label +", Role.Button,
                        SettingsTarget.Step(row.row, 1), onFocusChanged, onActivate, state = value, enabled = enabled)
                }
            }
            SettingsControl.KEYBIND -> {
                val action = checkNotNull(row.row.boundAction)
                row.key?.let { cell ->
                    val key = preferences.keyBindings.key(action).label(language)
                    SettingsSemanticTarget(
                        cell, "$tag.key", language.text(SettingsRedesignText.KeyCell, label, key), Role.Button,
                        SettingsTarget.Key(action), onFocusChanged, onActivate,
                        state = if (capture == action) language.text(SettingsRedesignText.WaitingForKey) else null,
                        actionLabel = row.row.about(language, platform),
                    )
                }
            }
            // A readout has no control; its (!) below carries the value as state.
            SettingsControl.READOUT -> Unit
            SettingsControl.ACTION -> row.action?.let { button ->
                SettingsSemanticTarget(
                    button, "$tag.action",
                    language.text(if (resetArmed) SettingsRedesignText.ResetBindingsConfirm else SettingsRedesignText.ResetBindingsButton),
                    Role.Button, SettingsTarget.Reset, onFocusChanged, onActivate,
                    enabled = settingsResetAvailable(preferences, platform),
                )
            }
        }
        if (row.hasInfo) {
            SettingsSemanticTarget(
                row.info.settingsTouch(density, 32f), "$tag.info", row.row.about(language, platform), Role.Button,
                SettingsTarget.Info(row.row), onFocusChanged, onActivate,
                state = if (row.control == SettingsControl.READOUT) settingValue(preferences, row.row, language, platform) else null,
            )
        }
    }
    layout.pages.forEachIndexed { index, pip ->
        SettingsSemanticTarget(
            pip.settingsTouch(density, 32f), "kinetickk.settings.page.$index",
            language.text(SettingsRedesignText.Page, index + 1, layout.pages.size), Role.Tab,
            SettingsTarget.Page(index), onFocusChanged, onActivate, selected = index == layout.page,
        )
    }
}

@Composable
private fun SettingsSemanticTarget(
    bounds: Rect,
    tag: String,
    description: String,
    role: Role,
    target: SettingsTarget,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onActivate: (SettingsTarget) -> Unit,
    selected: Boolean? = null,
    toggled: Boolean? = null,
    state: String? = null,
    enabled: Boolean = true,
    actionLabel: String? = null,
) {
    PlacedBox(bounds, Modifier
        .testTag(tag)
        .semantics {
            contentDescription = description
            this.role = role
            if (selected != null) this.selected = selected
            if (toggled != null) toggleableState = ToggleableState(toggled)
            if (state != null) stateDescription = state
            if (!enabled) disabled()
            onClick(label = actionLabel) { onActivate(target); true }
        }
        .onFocusChanged { onFocusChanged(target, it.isFocused) }
        .onKeyEvent { event ->
            val activates = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
            if (activates && event.type == KeyEventType.KeyDown) onActivate(target)
            activates
        }
        .focusable())
}
