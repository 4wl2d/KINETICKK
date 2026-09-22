// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

private val LocalPanelFocus = staticCompositionLocalOf<FocusRequester?> { null }
private val LocalPanelAccent = staticCompositionLocalOf { KineticAccent }

/** Shared layout only; actions and profile decisions remain with each feature. */
@Composable
internal fun ProfilePanel(
    title: String,
    subtitle: String,
    scale: Float,
    tag: String,
    onBack: () -> Unit,
    accent: Color = KineticAccent,
    contentKey: Any = Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(contentKey) { panelFocus.requestFocus() }
    CompositionLocalProvider(LocalPanelFocus provides panelFocus, LocalPanelAccent provides accent) {
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind { drawSectionAtmosphere(accent) }.padding(16.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth >= (640f * scale.coerceAtMost(1.5f)).dp
        val compact = maxHeight < 480.dp
        Column(Modifier.widthIn(max = 1160.dp).fillMaxWidth().heightIn(max = 880.dp).fillMaxHeight()
            .background(SpaceBlack.copy(alpha = 0.92f)).testTag(tag).focusRequester(panelFocus).focusable()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileLabel(title.uppercase(), scale, size = if (compact) 25f else 38f, bold = true)
                ProfileLabel(subtitle, scale, Muted, size = 11f)
            }
            Box(Modifier.padding(horizontal = 24.dp).width(72.dp).height(4.dp).background(accent))
            key(contentKey) {
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("$tag-scroll")
                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { content(wide) }
                    if (scroll.maxValue > 0) Canvas(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight()) {
                        val thumb = (size.height * scroll.viewportSize / (scroll.viewportSize + scroll.maxValue)).coerceAtLeast(20.dp.toPx()).coerceAtMost(size.height)
                        val top = (size.height - thumb) * scroll.value / scroll.maxValue
                        drawRect(Muted.copy(alpha = 0.6f), Offset(0f, top), Size(size.width, thumb))
                    }
                }
            }
            ProfileDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileButton(LocalAppLanguage.current.text(ProfileText.Back), scale, "$tag-back", Modifier.weight(1f), onClick = onBack)
                footer?.invoke(this)
            }
        }
    }
    }
}

@Composable
internal fun ProfileLabel(text: String, scale: Float, color: Color = White, size: Float = 14f, bold: Boolean = false, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = interfaceTextStyle(size.coerceAtLeast(14f) * scale, color,
        if (bold) FontWeight.Bold else FontWeight.Normal, display = bold && size >= 18f))
}

@Composable
internal fun ProfileDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(DarkLine.copy(alpha = 0.7f))) }

@Composable
internal fun ProfileButton(text: String, scale: Float, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    accent: Color = White, description: String = text, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val panelFocus = LocalPanelFocus.current
    val panelAccent = LocalPanelAccent.current
    val highlighted = enabled && (focused || hovered)
    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 52.dp).drawBehind {
        drawKineticRibbon(Rect(Offset.Zero, size), if (highlighted) panelAccent else OverlayPanel, 8.dp.toPx())
    }
        .border(1.dp, if (focused) White else Color.Transparent).testTag(tag)
        .semantics { contentDescription = description }
        .hoverable(interactions, enabled).clickable(enabled = enabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = {
            // A purchase or last-page click can disable its own button. Keep keyboard
            // routing on the panel instead of leaving focus on a removed target.
            panelFocus?.requestFocus()
            onClick()
        })
        .padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        ProfileLabel(text, scale, if (highlighted) SpaceBlack else if (enabled) accent else Muted, size = 13f, bold = true)
    }
}
