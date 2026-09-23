// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.drawBehind
import kinetickk.foundation.common.localization.text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.common.localization.text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.resource.audio.api.AudioService

class DefaultSettingsFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
) : SettingsFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Long,
        onOutput: (SettingsOutput) -> Unit,
    ) {
        var renderModelValue by remember(profilePort, routeToken) {
            mutableStateOf(
                profilePort.query(ProfileQuery.GetPreferences).preferences.toRenderModel(),
            )
        }
        val focusRequester = remember { FocusRequester() }
        val localDensity = LocalDensity.current
        var viewportValue by remember { mutableStateOf(IntSize.Zero) }
        var pageValue by rememberSaveable(routeToken) { mutableIntStateOf(0) }
        var groupIndexValue by rememberSaveable(routeToken) { mutableIntStateOf(SettingsGroup.GAME.ordinal) }
        val group = SettingsGroup.entries[groupIndexValue]
        LaunchedEffect(pageValue) { focusRequester.requestFocus() }
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val textMeasurer = CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            typography = kinetickk.foundation.design.rememberInterfaceTypography(),
            language = LocalAppLanguage.current,
            scale = renderModelValue.preferences.textScale,
        )

        fun dispatch(action: SettingsAction) {
            val reduction = SettingsReducer.reduce(
                state = SettingsState(renderModelValue, pageValue, group),
                action = action,
            )
            renderModelValue = reduction.state.model
            pageValue = reduction.state.page
            groupIndexValue = reduction.state.group.ordinal
            reduction.effects.forEach { effect ->
                when (effect) {
                    is SettingsEffect.AdjustPreference -> {
                        profilePort.accept(ProfilePulse.AdjustPreference(effect.adjustment))
                        renderModelValue = profilePort
                            .query(ProfileQuery.GetPreferences)
                            .preferences
                            .toRenderModel()
                        audioExecutor.updatePreferences(renderModelValue.preferences)
                        onOutput(SettingsOutput.LanguageChanged(renderModelValue.preferences.language))
                    }
                    is SettingsEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is SettingsEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        Box(Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onSizeChanged { viewportValue = it }) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(routeToken, renderModelValue, pageValue, group, onOutput) {
                        detectTapGestures { position ->
                            resolveSettingsPress(
                                screenWidth = size.width.toFloat(),
                                screenHeight = size.height.toFloat(),
                                density = density,
                                page = pageValue,
                                group = group,
                                x = position.x,
                                y = position.y,
                            )?.let(::dispatch)
                        }
                    },
            ) {
                drawSettings(
                    model = renderModelValue,
                    page = pageValue,
                    group = group,
                    textMeasurer = textMeasurer,
                )
            }
            if (viewportValue.width > 0 && viewportValue.height > 0) {
                val layout = settingsLayout(
                    viewportValue.width.toFloat(), viewportValue.height.toFloat(),
                    localDensity.density, group, pageValue,
                )
                layout.visibleRows.forEachIndexed { index, row ->
                    if (row != SettingsRow.LANGUAGE && row != SettingsRow.MASTER_VOLUME) {
                        val top = layout.startY + layout.spacing * index
                        listOf(-1, 1).forEach { direction ->
                            val left = layout.bounds.right - (if (direction < 0) 190f else 62f) * localDensity.density
                            SettingsSemanticButton(
                                androidx.compose.ui.geometry.Rect(left, top, left + 42f * localDensity.density, top + layout.spacing - 4f * localDensity.density),
                                localDensity, "kinetickk.settings.${row.name.lowercase()}.${if (direction < 0) "decrease" else "increase"}",
                                (if (direction < 0) "− " else "+ ") + settingValue(renderModelValue.preferences, row, textMeasurer.language),
                            ) { dispatch(SettingsAction.Adjust(row, direction)) }
                        }
                    }
                }
                val footerTop = layout.bounds.bottom - 55f * localDensity.density
                val backRight = if (layout.maxPage > 0) layout.bounds.left + layout.bounds.width * 0.45f else layout.bounds.right
                SettingsSemanticButton(androidx.compose.ui.geometry.Rect(layout.bounds.left, footerTop, backRight, layout.bounds.bottom),
                    localDensity, "kinetickk.settings.back", textMeasurer.language.text(kinetickk.ball.profile.interaction.localization.ProfileText.Back)) { dispatch(SettingsAction.Back) }
                if (layout.maxPage > 0) {
                    val nextLeft = layout.bounds.right - 85f * localDensity.density
                    SettingsSemanticButton(androidx.compose.ui.geometry.Rect(backRight, footerTop, nextLeft, layout.bounds.bottom),
                        localDensity, "kinetickk.settings.previous", "←") { dispatch(SettingsAction.PageSelected((layout.page - 1).coerceAtLeast(0))) }
                    SettingsSemanticButton(androidx.compose.ui.geometry.Rect(nextLeft, footerTop, layout.bounds.right, layout.bounds.bottom),
                        localDensity, "kinetickk.settings.next", "→") { dispatch(SettingsAction.PageSelected((layout.page + 1).coerceAtMost(layout.maxPage))) }
                }
                layout.volumeBounds(localDensity.density)?.let { bounds ->
                    SettingsVolumeControl(
                        percent = (renderModelValue.preferences.masterVolume * 100f).roundToInt(),
                        routeToken = routeToken,
                        textScale = renderModelValue.preferences.textScale,
                        onPercentChange = { dispatch(SettingsAction.SetMasterVolume(it)) },
                        onEditingFinished = { focusRequester.requestFocus() },
                        modifier = Modifier
                            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                            .requiredSize(with(localDensity) { bounds.width.toDp() }, with(localDensity) { bounds.height.toDp() }),
                    )
                }
                Box(Modifier.fillMaxSize().selectableGroup()) {
                    layout.tabs.forEach { tab ->
                        Box(
                            Modifier
                                .offset { IntOffset(tab.bounds.left.roundToInt(), tab.bounds.top.roundToInt()) }
                                .requiredSize(
                                    with(localDensity) { tab.bounds.width.toDp() },
                                    with(localDensity) { tab.bounds.height.toDp() },
                                )
                                .testTag("kinetickk.settings.group.${tab.group.name.lowercase()}")
                                .semantics { contentDescription = textMeasurer.language.text(tab.group.label) }
                                .selectable(
                                    selected = group == tab.group,
                                    role = Role.Tab,
                                    onClick = { dispatch(SettingsAction.SelectGroup(tab.group)) },
                                ),
                        )
                    }
                }
            }
            settingsLanguageOptions(
                viewportValue.width.toFloat(), viewportValue.height.toFloat(),
                localDensity.density, pageValue, group,
            ).forEach { option ->
                Box(
                    Modifier
                        .offset { IntOffset(option.bounds.left.roundToInt(), option.bounds.top.roundToInt()) }
                        .requiredSize(
                            with(localDensity) { option.bounds.width.toDp() },
                            with(localDensity) { option.bounds.height.toDp() },
                        )
                        .testTag("kinetickk.settings.language.${option.language.code}")
                        .semantics { contentDescription = option.language.nativeName }
                        .selectable(
                            selected = renderModelValue.preferences.language == option.language,
                            role = Role.RadioButton,
                            onClick = { dispatch(SettingsAction.SelectLanguage(option.language)) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun SettingsSemanticButton(bounds: androidx.compose.ui.geometry.Rect, density: androidx.compose.ui.unit.Density,
    tag: String, description: String, onClick: () -> Unit) {
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
        .testTag(tag).semantics { contentDescription = description }
        .drawBehind {
            if (hovered || focused) drawRect(kinetickk.foundation.design.White.copy(alpha = 0.10f))
            if (focused) drawRect(kinetickk.foundation.design.KineticAccent, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        .hoverable(interactions)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick))
}
