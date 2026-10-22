// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.ball.profile.interaction.drawProfileSidePanel
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.hangar.api.HangarRenderModel
import kinetickk.ball.profile.interaction.hangar.api.HangarShapeRenderModel
import kinetickk.ball.profile.interaction.hangar.api.HangarWeaponRenderModel
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.armory.impl.armoryWeaponIcon
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.ball.profile.interaction.profileLoopTime
import kinetickk.ball.profile.interaction.profileScrollCue
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopAction
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegmentStrip
import kinetickk.ball.profile.interaction.workshop.impl.segments
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

/** Latest geometry for the background pass (written during composition). */
internal class HangarLayoutHolder {
    var layout: HangarLayout? = null
}

/** The form index of a character: [KkFormGeometry] and [KkIcon.Forms] follow the character order. */
internal fun CoreShape.hangarFormIndex(): Int = ordinal

/** Seconds per turn of the animated forms (tiles and the preview). */
private const val HANGAR_SPIN_SECONDS = 24f

/**
 * The Workshop's Characters body inside the panel's [frame]: the roster, the detail panel with its
 * [segment] and the pinned Select button. [textScale] is the player's text-size setting (1.25 = the
 * boards' size); [focusSerial] moves keyboard focus to the inspected tile when it changes.
 */
@Composable
internal fun HangarBody(
    frame: ProfileFrame,
    state: HangarState,
    segment: WorkshopSegment,
    textScale: Float,
    detailScroll: ScrollState,
    holder: HangarLayoutHolder = remember { HangarLayoutHolder() },
    focusSerial: Int = 0,
    onSegment: (WorkshopAction) -> Unit = {},
    onAction: (HangarAction) -> Unit,
) {
    val scale = profileTextScale(textScale)
    val language = LocalAppLanguage.current
    val model = state.model
    val time = profileLoopTime(HANGAR_SPIN_SECONDS)
    // Enter selects the inspected character even while focus rests on the panel itself (after
    // opening, or when hover inspected a tile): a focused tile or button consumes Enter first, and
    // an unconsumed Enter must not reach the Session, which would close the Workshop.
    Box(
        Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown) onAction(HangarAction.SelectInspected)
            true
        },
    ) {
        val measurer = rememberKkCanvasMeasurer(scale)
        val type = hangarType(frame.mode)
        val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
        val inspected = state.inspectedShape
        val signature = inspected?.let { model.signatureWeapons(it.shape) }.orEmpty()
        val layout = hangarLayout(frame, scale, backWidth, signatureHarmony = signature.map { it.harmonizing.isNotEmpty() },
            segment = segment, locked = inspected?.unlocked == false) { width ->
            if (inspected == null) 0f else hangarDescriptionLayout(measurer, frame, type, inspected, language, width).kkBoxHeight
        }
        SideEffect { holder.layout = layout }
        val inspectedFocus = remember { FocusRequester() }
        LaunchedEffect(focusSerial) { if (focusSerial > 0) inspectedFocus.requestFocus() }
        Box(
            Modifier.semantics { contentDescription = language.text(ProfileScreensRedesignText.HangarRoster) }
                .testTag("profile-hangar-roster"),
        ) {
            model.shapes.forEachIndexed { index, shape ->
                val tile = layout.tiles.getOrNull(index) ?: return@forEachIndexed
                HangarTile(frame, type, tile, shape, shape.shape == state.inspected, shape.shape == model.selected, time, scale,
                    language, onAction, focusRequester = if (shape.shape == inspected?.shape) inspectedFocus else null)
            }
        }
        if (inspected != null) {
            val detail = layout.detailViewport
            LaunchedEffect(inspected.shape) { detailScroll.scrollTo(0) }
            Box(
                Modifier.offset { IntOffset(detail.left.roundToInt(), detail.top.roundToInt()) }
                    .size(frame.dp(detail.width), frame.dp(detail.height))
                    .profileScrollCue(detailScroll, Kk.Ink1, frame.d(24f))
                    .verticalScroll(detailScroll)
                    .testTag("profile-hangar-detail"),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(frame.dp(layout.detailContentHeight))
                        .semantics {
                            contentDescription = hangarDetailDescription(inspected, model, language, traits = segment == WorkshopSegment.TRAITS)
                        }
                        .drawBehind { drawHangarDetail(measurer, frame, type, layout, inspected, model, signature, time, language) },
                ) {
                    WorkshopSegmentStrip(frame, layout.segments, WorkshopTab.CHARACTERS.segments, segment, scale, onSegment)
                    // Each signature weapon reads as its own node: its name, state and harmony.
                    if (layout.signature.height > 0f) {
                        signature.forEachIndexed { index, weapon ->
                            val row = layout.signatureRows.getOrNull(index) ?: return@forEachIndexed
                            Box(
                                Modifier.offset { IntOffset(row.left.roundToInt(), row.top.roundToInt()) }
                                    .size(frame.dp(row.width), frame.dp(row.height))
                                    .testTag("profile-hangar-signature-${weapon.definition.id}")
                                    .semantics {
                                        contentDescription = weapon.definition.name.localizedContent(language)
                                        stateDescription = listOfNotNull(hangarWeaponStatus(weapon, language), hangarHarmonyText(weapon, language))
                                            .joinToString(", ")
                                    },
                            )
                        }
                    }
                }
            }
            val select = layout.select
            val selected = inspected.shape == model.selected
            val label = when {
                selected -> language.text(ProfileScreensRedesignText.HangarSelected)
                !inspected.unlocked -> language.text(ProfileScreensRedesignText.HangarLocked)
                !state.selectionAvailable -> language.text(ProfileScreensRedesignText.SelectInRun)
                else -> language.text(ProfileScreensRedesignText.HangarSelect)
            }
            ProfileSlabButton(
                label = label,
                onClick = { onAction(HangarAction.SelectInspected) },
                frame = frame,
                tag = "profile-hangar-select",
                size = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD,
                fontSize = type.select,
                enabled = state.canSelect,
                locked = !inspected.unlocked,
                textScale = scale,
                contentDescription = label + " " + inspected.definition.displayName.localizedContent(language),
                stateDescription = hangarUnlockText(inspected, language),
                modifier = Modifier.offset { IntOffset(select.left.roundToInt(), select.top.roundToInt()) }
                    .size(frame.dp(select.width), frame.dp(select.height)),
            )
        }
    }
}

@Composable
private fun HangarTile(
    frame: ProfileFrame,
    type: HangarType,
    tile: Rect,
    shape: HangarShapeRenderModel,
    inspected: Boolean,
    selected: Boolean,
    time: Float,
    textScale: Float,
    language: AppLanguage,
    onAction: (HangarAction) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var pointerPress by remember { mutableStateOf(false) }
    val selection = if (inspected) 1f else 0f
    // Hover only highlights: the inspected character (which Select acts on) changes on a press, a tap or keyboard
    // focus, so crossing other tiles on the way to the detail panel leaves it alone.
    val measurer = rememberKkCanvasMeasurer(textScale)
    val name = shape.definition.displayName.localizedContent(language)
    val status = hangarTileStatus(shape, selected, language)
    Box(
        Modifier.offset { IntOffset(tile.left.roundToInt(), tile.top.roundToInt()) }
            .size(frame.dp(tile.width), frame.dp(tile.height))
            .testTag("profile-hangar-tile-${shape.shape}")
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics {
                this.selected = selected
                contentDescription = name
                stateDescription = status
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerPress = true
                    }
                }
            }
            // Keyboard focus (arrows, Tab) inspects; focus that comes with a press does not, so the first
            // press only inspects and a press (or Enter) on the inspected tile selects it.
            .onFocusChanged {
                if (it.isFocused && !pointerPress) onAction(HangarAction.Inspect(shape.shape))
                if (!it.isFocused) pointerPress = false
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { onAction(HangarAction.Activate(shape.shape)) }
            .drawBehind { drawHangarTile(measurer, frame, type, shape, name, status, selection, hovered, focused, selected, time) },
    )
}

/** The status line of a tile: Selected, the unlock progress, or nothing for an unlocked character. */
internal fun hangarTileStatus(shape: HangarShapeRenderModel, selected: Boolean, language: AppLanguage): String = when {
    selected -> language.text(ProfileScreensRedesignText.HangarSelected)
    shape.unlocked -> language.text(ProfileScreensRedesignText.HangarAvailable)
    else -> language.text(ProfileScreensRedesignText.CountOf, shape.progress, shape.target)
}

/** The unlock line of the detail panel: the requirement and, while locked, its progress. */
internal fun hangarUnlockText(shape: HangarShapeRenderModel, language: AppLanguage): String {
    val requirement = shape.definition.unlockDescription.localizedContent(language)
    return if (shape.unlocked) {
        language.text(ProfileScreensRedesignText.HangarUnlockedBy, requirement)
    } else {
        language.text(ProfileScreensRedesignText.HangarProgress, requirement, shape.progress, shape.target)
    }
}

/** Everything the detail panel shows, as one accessible sentence list ([traits]: the Traits segment is shown). */
internal fun hangarDetailDescription(
    shape: HangarShapeRenderModel,
    model: HangarRenderModel,
    language: AppLanguage,
    traits: Boolean = true,
): String {
    val definition = shape.definition
    return listOf(
        definition.displayName.localizedContent(language),
        definition.mechanicName.localizedContent(language) + ", " + definition.triggerKind.displayLabel.localizedContent(language),
        definition.mechanicDescription.localizedContent(language),
        if (traits) {
            language.text(ProfileScreensRedesignText.HangarDifficulty) + " " +
                language.text(ProfileScreensRedesignText.CountOf, definition.difficulty, 3)
        } else "",
        hangarUnlockText(shape, language),
        if (shape.shape == model.selected) language.text(ProfileScreensRedesignText.HangarSelected) else "",
    ).filter { it.isNotBlank() }.joinToString(". ")
}

/** A signature weapon's state: the starter, owned, its Rebirth gate, or its Matter price. */
internal fun hangarWeaponStatus(weapon: HangarWeaponRenderModel, language: AppLanguage): String = when {
    weapon.starter -> language.text(ProfileScreensRedesignText.HangarWeaponStarter)
    weapon.owned -> language.text(ProfileScreensRedesignText.HangarWeaponOwned)
    weapon.rebirthLocked -> language.text(ProfileScreensRedesignText.RebornLock, weapon.definition.minimumRebirth)
    else -> language.text(ProfileScreensRedesignText.CostMatter, formatMatter(weapon.definition.permanentUnlockCost.toLong(), language))
}

/** The heirloom candidates that would harmonize with [weapon], or null when none carries its key. */
internal fun hangarHarmonyText(weapon: HangarWeaponRenderModel, language: AppLanguage): String? =
    weapon.harmonizing.takeIf { it.isNotEmpty() }?.let { relics ->
        language.text(ProfileScreensRedesignText.HangarHarmony, relics.joinToString(", ") { it.name.localizedContent(language) })
    }

/** The weapon icon of the signature row (the Armory's cached `weapons.<id>` icon). */
private fun hangarWeaponIcon(weapon: HangarWeaponRenderModel): KkIcon? = armoryWeaponIcon(weapon.definition.id)

internal fun DrawScope.drawHangarForm(index: Int, center: Offset, radius: Float, fill: Color, rim: Color, spin: Float) {
    val path = KkFormPaths.of(index, radius)
    rotate(spin, center) {
        translate(center.x, center.y) {
            if (KkFormGeometry.isStroked(index)) {
                val width = KkFormGeometry.strokeWidth(index, radius)
                drawPath(path, rim, style = kkStroke(width + radius * 0.18f))
                drawPath(path, fill, style = kkStroke(width))
            } else {
                drawPath(path, rim, style = kkStroke(radius * 0.18f))
                drawPath(path, fill)
            }
        }
    }
}

private fun DrawScope.drawHangarTile(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    shape: HangarShapeRenderModel,
    name: String,
    status: String,
    selection: Float,
    hovered: Boolean,
    focused: Boolean,
    selected: Boolean,
    time: Float,
) {
    val k = frame.k
    val roles = measurer.roles
    val on = selection > 0.5f
    val bounds = Rect(Offset.Zero, size)
    drawKkTile(measurer, bounds, selected = selection, hovered = hovered, locked = !shape.unlocked,
        cutDp = (if (frame.regular) 12f else 8f) * k, focused = focused)
    val fg = when {
        !shape.unlocked && on -> Kk.Bone
        !shape.unlocked -> Kk.Mute
        on -> Kk.Ink
        else -> Kk.Bone
    }
    val pad = frame.d(if (frame.regular) 10f else 5f)
    val statusLayout = fitKkText(measurer, status, type.tileStatus * k, size.width - pad * 2f, minFactor = 0.5f) {
        measurer.typography.monoStyle(it)
    }
    val nameLayout = fitKkText(measurer, name, type.tileName * k, size.width - pad * 2f, minFactor = 0.5f) {
        measurer.typography.condStyle(it, lineHeightEm = 1f)
    }
    val textHeight = statusLayout.kkBoxHeight + nameLayout.kkBoxHeight + frame.d(2f)
    val formRoom = (size.height - pad * 2f - textHeight).coerceAtLeast(frame.d(10f))
    val radius = (minOf(formRoom, size.width - pad * 2f) * 0.36f).coerceAtLeast(frame.d(6f))
    val center = Offset(size.width * 0.5f, pad + formRoom * 0.5f)
    val index = shape.shape.hangarFormIndex()
    val spin = if (shape.unlocked) (time / HANGAR_SPIN_SECONDS * 360f) else 0f
    drawHangarForm(index, center, radius, if (shape.unlocked) fg else Kk.Ink3, if (on) Kk.Bone else Kk.Ink, spin)
    if (!shape.unlocked) drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.9f, fg)
    val nameTop = size.height - pad - textHeight
    drawProfileText(nameLayout, size.width * 0.5f, nameTop, fg, "hangar.tile.name", shape.shape, align = KkAlign.CENTER)
    // A locked tile keeps its dark face when inspected, so its unlock progress reads in light ink then.
    val statusColor = when {
        !shape.unlocked -> if (on) Kk.Bone2 else Kk.Mute
        selected -> if (on) Kk.Ink else roles.you
        else -> if (on) Kk.Ink else Kk.Mute
    }
    drawProfileText(statusLayout, size.width * 0.5f, size.height - pad - statusLayout.kkBoxHeight, statusColor, "hangar.tile.status",
        shape.shape, align = KkAlign.CENTER)
    if (selected) {
        val gem = frame.d(if (frame.regular) 9f else 7f)
        drawKkGem(Offset(size.width - pad - gem * 0.5f, pad + gem * 0.6f), if (on) Kk.Ink else roles.you, gem / density)
    }
    if (shape.ascendant) {
        // Ascendant: the gold crown in the tile's top-left corner.
        val crown = frame.d(if (frame.regular) 8f else 6f)
        translate(pad + crown, pad + crown) { drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, crown), Kk.Gold) }
    }
}

/** The inspected character's description wrapped to [width] (every line; the details scroll). */
internal fun hangarDescriptionLayout(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    shape: HangarShapeRenderModel,
    language: AppLanguage,
    width: Float,
) = measureKkText(measurer, shape.definition.mechanicDescription.localizedContent(language),
    measurer.typography.bodyStyle(type.body * frame.k), maxWidth = width, maxLines = PROFILE_DESCRIPTION_MAX_LINES)

private fun DrawScope.drawHangarDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    layout: HangarLayout,
    shape: HangarShapeRenderModel,
    model: HangarRenderModel,
    signatureWeapons: List<HangarWeaponRenderModel>,
    time: Float,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    val definition = shape.definition
    // Large animated preview: the form spinning in a faint ring.
    val preview = layout.preview
    val radius = preview.height * 0.36f
    val center = Offset(preview.left + radius * 1.4f, preview.center.y)
    drawCircle(roles.you.copy(alpha = 0.12f), radius * 1.35f, center, style = kkStroke(frame.d(2f)))
    drawHangarForm(definition.id.hangarFormIndex(), center, radius, if (shape.unlocked) Kk.Bone else Kk.Ink3, Kk.Ink,
        time / HANGAR_SPIN_SECONDS * 360f)
    if (!shape.unlocked) drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.8f, Kk.Mute)
    if (shape.ascendant) {
        // Ascendant: the gold crown over the preview ring.
        val crown = radius * 0.32f
        translate(center.x, center.y - radius * 1.35f - crown * 0.4f) {
            drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, crown), Kk.Gold)
        }
    }
    val tagText = definition.mechanicName.localizedContent(language) + " / " + definition.triggerKind.displayLabel.localizedContent(language)
    val tag = fitKkText(measurer, tagText, type.mono * k, layout.tag.width, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(tag, layout.tag.left, layout.tag.top, roles.you, "hangar.detail.tag")
    val name = fitKkText(measurer, definition.displayName.localizedContent(language), type.name * k, layout.name.width, minFactor = 0.4f) {
        measurer.typography.wideStyle(it, lineHeightEm = 1.05f)
    }
    drawProfileText(name, layout.name.left, layout.name.center.y, if (shape.unlocked) Kk.Bone else Kk.Mute, "hangar.detail.name",
        valign = KkVAlign.CENTER)
    drawProfileText(hangarDescriptionLayout(measurer, frame, type, shape, language, layout.description.width),
        layout.description.left, layout.description.top, Kk.Bone, "hangar.detail.description")
    if (layout.unlock.height > 0f) {
        val unlock = fitKkText(measurer, hangarUnlockText(shape, language), type.mono * k, layout.unlock.width, maxLines = 2,
            minFactor = 0.5f) { measurer.typography.monoStyle(it) }
        drawProfileText(unlock, layout.unlock.left, layout.unlock.top, if (shape.unlocked) Kk.Bone2 else roles.threat, "hangar.detail.unlock")
    }
    if (shape.shape == model.selected) {
        drawKkGem(Offset(layout.name.right - frame.d(10f), layout.tag.center.y), roles.you, frame.d(9f) / density)
    }
    // Traits, difficulty and the signature weapons show on the Traits segment only.
    if (layout.signature.height <= 0f) return
    val labels = listOf(
        ProfileScreensRedesignText.HangarMass to shape.massBar,
        ProfileScreensRedesignText.HangarIntegrity to shape.integrityBar,
        ProfileScreensRedesignText.HangarHandling to shape.handlingBar,
        ProfileScreensRedesignText.HangarHeat to shape.heatBar,
    )
    val labelWidth = layout.traits.first().width * 0.36f
    labels.forEachIndexed { index, (label, value) ->
        val row = layout.traits[index]
        val text = fitKkText(measurer, language.text(label), type.mono * k, labelWidth - frame.d(8f), minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawProfileText(text, row.left, row.center.y, Kk.Mute, "hangar.detail.trait", index, valign = KkVAlign.CENTER)
        val barLeft = row.left + labelWidth
        val barHeight = row.height * 0.36f
        val bar = Rect(barLeft, row.center.y - barHeight * 0.5f, row.right, row.center.y + barHeight * 0.5f)
        drawRect(Kk.Line2, bar.topLeft, bar.size)
        drawRect(roles.you, bar.topLeft, Size(bar.width * value.coerceIn(0f, 1f), bar.height))
    }
    val difficultyLabel = fitKkText(measurer, language.text(ProfileScreensRedesignText.HangarDifficulty), type.mono * k,
        labelWidth - frame.d(8f), minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    val difficulty = layout.difficulty
    drawProfileText(difficultyLabel, difficulty.left, difficulty.center.y, Kk.Mute, "hangar.detail.difficulty", valign = KkVAlign.CENTER)
    val pip = difficulty.height * 0.5f
    drawKkPips(Offset(difficulty.left + labelWidth, difficulty.center.y - pip * 0.5f), 3, definition.difficulty, roles.threat,
        sizeDp = pip / density, widthDp = pip * 1.6f / density, emptyOutlined = true)
    // The signature weapons: the two weapons whose signature character this is, each with its
    // icon, name and state (owned, starter, Rebirth gate or price) and the heirlooms it harmonizes with.
    val signature = layout.signature
    val firstRow = layout.signatureRows.firstOrNull() ?: signature
    val signatureLabel = fitKkText(measurer, language.text(ProfileScreensRedesignText.HangarSignature), type.mono * k,
        labelWidth - frame.d(8f), minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(signatureLabel, signature.left, firstRow.top + layout.signatureLine * 0.5f, Kk.Mute, "hangar.detail.signature",
        valign = KkVAlign.CENTER)
    signatureWeapons.forEachIndexed { index, weapon ->
        val row = layout.signatureRows.getOrNull(index) ?: return@forEachIndexed
        drawHangarSignatureWeapon(measurer, frame, type, Rect(row.left + labelWidth, row.top, row.right, row.bottom), layout.signatureLine,
            weapon, language)
    }
}

internal fun DrawScope.drawHangarBackground(frame: ProfileFrame, layout: HangarLayout?) {
    drawRect(Kk.Ink)
    drawProfileGrid(frame)
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> drawProfileSidePanel(frame.x(846f))
        ProfileLayoutMode.COMPACT_LANDSCAPE -> if (layout != null) {
            drawProfileSidePanel(layout.detailViewport.left - size.height * KkShape.ShearRatio * 0.5f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> if (layout != null) {
            val top = layout.detailViewport.top - frame.d(10f)
            drawRect(Kk.Ink1, Offset(0f, top), Size(size.width, size.height - top))
            drawLine(Kk.Line, Offset(0f, top), Offset(size.width, top), density)
        }
    }
}

/**
 * One signature weapon in [row]: its icon (muted while not owned) and name, its state at the right
 * edge (gold for a Rebirth gate) and, when heirloom candidates carry its key, the harmony line under them.
 */
private fun DrawScope.drawHangarSignatureWeapon(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    row: Rect,
    lineHeight: Float,
    weapon: HangarWeaponRenderModel,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    val harmonyLayout = hangarHarmonyText(weapon, language)?.let {
        fitKkText(measurer, it, type.mono * k, row.width, minFactor = 0.5f, uppercase = false) { size -> measurer.typography.monoStyle(size) }
    }
    val centerY = row.top + lineHeight * 0.5f
    val owned = weapon.owned || weapon.starter
    val icon = lineHeight * 0.72f
    hangarWeaponIcon(weapon)?.let { drawKkIcon(it, Offset(row.left + icon * 0.5f, centerY), icon, if (owned) roles.you else Kk.Mute) }
    val status = fitKkText(measurer, hangarWeaponStatus(weapon, language), type.mono * k, row.width * 0.4f, minFactor = 0.5f) {
        measurer.typography.monoStyle(it)
    }
    val statusColor = when {
        weapon.rebirthLocked -> roles.gold.base
        owned -> roles.you
        else -> Kk.Mute
    }
    drawProfileText(status, row.right, centerY, statusColor, "hangar.detail.signature.state", weapon.definition.id, align = KkAlign.END,
        valign = KkVAlign.CENTER)
    val nameLeft = row.left + icon + frame.d(8f)
    val name = fitKkText(measurer, weapon.definition.name.localizedContent(language), type.mono * 1.45f * k,
        (row.right - status.size.width - frame.d(10f) - nameLeft).coerceAtLeast(frame.d(30f)), minFactor = 0.5f) {
        measurer.typography.condStyle(it, lineHeightEm = 1f)
    }
    drawProfileText(name, nameLeft, centerY, if (owned) Kk.Bone else Kk.Bone2, "hangar.detail.signature.value", weapon.definition.id,
        valign = KkVAlign.CENTER)
    if (harmonyLayout != null) {
        drawProfileText(harmonyLayout, row.left, row.top + lineHeight, roles.you, "hangar.detail.signature.harmony", weapon.definition.id)
    }
}
