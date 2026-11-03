// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_SMALLEST_TEXT_SCALE
import kinetickk.ball.profile.interaction.PROFILE_WRAPPED_LINE_HEIGHT
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.workshop.hangar.drawHangarForm
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.workshop.impl.workshopTileLevel
import kinetickk.ball.profile.interaction.workshop.impl.workshopTileLevelColor
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a weapon tile writes: its name, status (or unlock cost) line and whether it has tags. */
internal class ArmoryTileText(val name: String, val status: String, val hasTags: Boolean)

internal fun armoryTileTexts(weapons: List<WeaponDefinition>, model: ArmoryRenderModel, language: AppLanguage): List<ArmoryTileText> =
    weapons.map { definition ->
        val status = model.status(definition)
        val active = model.activeRunWeapon == definition.id && status != ArmoryWeaponStatus.STARTER
        ArmoryTileText(
            name = definition.name.localizedContent(language),
            status = when {
                status == ArmoryWeaponStatus.REBIRTH_LOCKED -> armoryStatusText(status, active, definition, language)
                status.locked -> formatMatter(definition.permanentUnlockCost.toLong(), language)
                else -> armoryStatusText(status, active, definition, language)
            },
            hasTags = definition.tags.isNotEmpty(),
        )
    }

/**
 * The grid's tallest status line and name block at their full size (a tile may shrink its own),
 * for tiles whose content is [innerWidth] px wide.
 */
internal fun armoryTileMetrics(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    tiles: List<ArmoryTileText>,
    innerWidth: Float,
): ArmoryTileMetrics {
    val statusStyle = measurer.typography.monoStyle(type.status * frame.k)
    val tagLine = measureKkText(measurer, "0", measurer.typography.monoStyle(type.tileTags * frame.k), uppercase = true).kkBoxHeight
    var status = 0f
    var nameBlock = 0f
    for (tile in tiles) {
        status = max(status, measureKkText(measurer, tile.status, statusStyle, uppercase = true).kkBoxHeight)
        var block = armoryTileNameLayout(measurer, frame, type, tile.name, innerWidth).kkBoxHeight
        // The regular tile puts its tag line under the name, 5 dp below it.
        if (frame.regular) block += frame.d(5f) + if (tile.hasTags) tagLine else 0f
        nameBlock = max(nameBlock, block)
    }
    return ArmoryTileMetrics(status, nameBlock)
}

@Composable
internal fun ArmoryTile(
    frame: ProfileFrame,
    type: ArmoryType,
    tile: Rect,
    iconSize: Float,
    iconY: Float,
    definition: WeaponDefinition,
    model: ArmoryRenderModel,
    inspected: Boolean,
    textScale: Float,
    language: AppLanguage,
    onAction: (ArmoryAction) -> Unit,
    signatureForm: Int,
    focusRequester: FocusRequester? = null,
    maxLevel: Int = UpgradePolicy().maxLevel,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var pointerPress by remember { mutableStateOf(false) }
    val selection = if (inspected) 1f else 0f
    // The selected character's signature weapons carry its form in the tile's corner.
    val signature = model.character == definition.signature
    val measurer = rememberKkCanvasMeasurer(textScale)
    val status = model.status(definition)
    val active = model.activeRunWeapon == definition.id && status != ArmoryWeaponStatus.STARTER
    val name = definition.name.localizedContent(language)
    val statusText = armoryStatusText(status, active, definition, language)
    val costText = formatMatter(definition.permanentUnlockCost.toLong(), language)
    val upgradeLevel = model.upgrades.weaponLevel(definition.id)
    val level = workshopTileLevel(upgradeLevel, !status.locked, maxLevel, language)
    val tags = remember(definition, language) { definition.tags.map { it.displayLabel.localizedContent(language) } }
    Box(
        Modifier.offset { IntOffset(tile.left.roundToInt(), tile.top.roundToInt()) }
            .size(frame.dp(tile.width), frame.dp(tile.height))
            .testTag("profile-armory-equip-${definition.id}")
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics {
                selected = inspected
                contentDescription = name
                stateDescription = listOfNotNull(statusText, level,
                    if (signature) language.text(ProfileScreensRedesignText.ArmorySignatureMark) else null).joinToString(", ")
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerPress = true
                    }
                }
            }
            // Keyboard focus moves the selection; focus that comes with a press does not, so the
            // first press only inspects and a press on the inspected tile confirms.
            .onFocusChanged {
                if (it.isFocused && !pointerPress) onAction(ArmoryAction.Inspect(definition.id))
                if (!it.isFocused) pointerPress = false
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                onAction(ArmoryAction.Activate(definition.id))
            }
            .drawBehind {
                drawArmoryTile(measurer, frame, type, name, tags, statusText, costText, status, active,
                    armoryWeaponIcon(definition.id), iconSize, iconY, selection, hovered, focused,
                    tint = armoryElementColor(measurer, definition), signatureForm = if (signature) signatureForm else -1,
                    level = level, maxed = upgradeLevel >= maxLevel)
            },
    )
}

internal fun armoryStatusText(status: ArmoryWeaponStatus, active: Boolean, definition: WeaponDefinition, language: AppLanguage): String = when {
    active -> language.text(ProfileScreensRedesignText.ActiveRun)
    status == ArmoryWeaponStatus.STARTER -> language.text(ProfileScreensRedesignText.Starter)
    status == ArmoryWeaponStatus.OWNED -> language.text(ProfileScreensRedesignText.Owned)
    status == ArmoryWeaponStatus.REBIRTH_LOCKED -> language.text(ProfileScreensRedesignText.RebornLock, definition.minimumRebirth)
    else -> language.text(ProfileScreensRedesignText.CostMatter, formatMatter(definition.permanentUnlockCost.toLong(), language))
}

private fun DrawScope.drawArmoryTile(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    name: String,
    tags: List<String>,
    statusText: String,
    costText: String,
    status: ArmoryWeaponStatus,
    active: Boolean,
    icon: KkIcon?,
    iconSize: Float,
    iconY: Float,
    selection: Float,
    hovered: Boolean,
    focused: Boolean,
    tint: Color,
    signatureForm: Int,
    level: String? = null,
    maxed: Boolean = false,
) {
    val k = frame.k
    val bounds = Rect(Offset.Zero, size)
    val locked = status.locked
    val roles = measurer.roles
    // The board's selected tile keeps its row (the entrance animation holds `transform: none`),
    // so the foundation's 8 dp selection lift is cancelled here; the echo still slides out.
    translate(0f, d(8f) * selection.coerceIn(0f, 1.2f)) {
        drawKkTile(measurer, bounds, selected = selection, hovered = hovered, locked = locked,
            cutDp = (if (frame.regular) ARMORY_TILE_CUT else 12f) * k, focused = focused)
    }
    val on = selection > 0.5f
    val fg = when {
        locked && on -> Kk.Bone
        locked -> Kk.Mute
        on -> Kk.Ink
        else -> Kk.Bone
    }
    val compact = !frame.regular
    val insets = armoryTileInsets(frame)
    val padLeft = insets.left
    val padRight = insets.right
    val padTop = insets.top
    val padBottom = insets.bottom
    val innerWidth = size.width - padLeft - padRight
    // The signature mark: the selected character's form in the top-right corner, beside the status line.
    val markRadius = frame.d(if (compact) 6f else 8f)
    val markRoom = if (signatureForm >= 0) markRadius * 2f + frame.d(6f) else 0f
    if (signatureForm >= 0) {
        drawHangarForm(signatureForm, Offset(size.width - padRight - markRadius, padTop + markRadius), markRadius,
            if (on) Kk.Ink else roles.you, if (on) Kk.Bone else Kk.Ink, 0f)
    }
    val statusWidth = innerWidth - markRoom
    if (status == ArmoryWeaponStatus.REBIRTH_LOCKED) {
        // A Rebirth-gated weapon names its rank on the tile: the lock and "Reborn N" in gold.
        val gold = roles.gold.base
        val lock = frame.d(if (compact) 9f else 11f)
        val rank = fitKkText(measurer, statusText, type.status * k, statusWidth - lock - frame.d(5f), minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(padLeft + lock * 0.5f, padTop + rank.kkBoxHeight * 0.5f), lock, if (on) Kk.Bone else gold)
        drawProfileText(rank, padLeft + lock + frame.d(5f), padTop, if (on) Kk.Bone else gold, "armory.tile.status", name)
    } else if (locked) {
        val gem = frame.d(if (compact) 7f else 9f)
        val costColor = when {
            status == ArmoryWeaponStatus.AFFORDABLE -> roles.you
            else -> Kk.Mute
        }
        val cost = fitKkText(measurer, costText, type.status * k, statusWidth - gem - frame.d(5f), minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawKkGem(Offset(padLeft + gem * 0.5f, padTop + cost.kkBoxHeight * 0.5f), costColor, gem / density)
        drawProfileText(cost, padLeft + gem + frame.d(5f), padTop, costColor, "armory.tile.status", name)
    } else {
        val layout = fitKkText(measurer, statusText, type.status * k, statusWidth, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
        drawProfileText(layout, padLeft, padTop, if (active && !on) roles.you else fg, "armory.tile.status", name,
            alpha = if (active) 1f else 0.7f)
    }

    // Phones keep the tags in the detail panel; the regular tile lists them as words on one line,
    // shrinking them together (never below their size at the smallest text setting).
    var tagsTop = size.height - padBottom
    if (!compact && tags.isNotEmpty()) {
        val tagGap = frame.d(8f)
        val right = armoryTileTagRight(frame, size, padLeft, padRight, bottom = size.height - padBottom)
        val room = right - padLeft
        fun lineWidth(font: Float) = tags.sumOf {
            measureKkText(measurer, it, measurer.typography.monoStyle(font), uppercase = true).size.width.toDouble()
        }.toFloat() + tagGap * (tags.size - 1)
        var font = type.tileTags * k
        val smallest = font * (PROFILE_SMALLEST_TEXT_SCALE / measurer.scale).coerceAtMost(1f)
        while (font > smallest && lineWidth(font) > room) font = max(smallest, font * 0.94f)
        val tagStyle = measurer.typography.monoStyle(font)
        tagsTop -= measureKkText(measurer, tags.first(), tagStyle, uppercase = true).kkBoxHeight
        var x = padLeft
        for (tag in tags) {
            val layout = measureKkText(measurer, tag, tagStyle, uppercase = true)
            // A tag that would pass the line's right edge is left to the detail panel's tag rows.
            if (x + layout.size.width > right) break
            drawProfileText(layout, x, tagsTop, fg, "armory.tile.tag", name, alpha = 0.75f)
            x += layout.size.width + tagGap
        }
    }
    val nameLayout = armoryTileNameLayout(measurer, frame, type, name, innerWidth)
    val nameTop = tagsTop - (if (compact) 0f else frame.d(5f)) - nameLayout.kkBoxHeight
    drawProfileText(nameLayout, padLeft, nameTop, fg, "armory.tile.name", name)

    // The icon has the grid's one size and height (the layout keeps it between the tallest status
    // line and the tallest name block), so a wrapped name never shrinks or hides it.
    // The weapon icon takes its element's hue (Kinetic weapons the preset's volt).
    val iconColor = when {
        locked -> Kk.Mute
        on -> Kk.Ink
        else -> tint
    }
    if (icon != null && iconSize > 0f) {
        val center = Offset(size.width * 0.5f, iconY)
        drawKkIcon(icon, center, iconSize, iconColor)
        if (ProfileTextProbe.sink != null) ProfileTextProbe.record("armory.tile.icon", name, null,
            Rect(center.x - iconSize * 0.5f, center.y - iconSize * 0.5f, center.x + iconSize * 0.5f, center.y + iconSize * 0.5f))
    }
    if (level != null) {
        // The Honing level sits at the bottom-right of the icon band, above the name (gold at the maximum).
        val mark = fitKkText(measurer, level, type.status * k, innerWidth * 0.4f, minFactor = 0.6f) { measurer.typography.monoStyle(it) }
        drawProfileText(mark, size.width - padRight, iconY + iconSize * 0.5f, workshopTileLevelColor(maxed, on), "armory.tile.level", name,
            align = KkAlign.END, valign = KkVAlign.BASELINE)
    }
}

/**
 * The x (tile px) the regular tile's tag line may reach, for tags ending at [bottom]: the board's
 * padded content edge (`padding-right`), and never closer to the slab's sheared right edge at
 * that height (it leans in by the cut towards the tile's bottom) than the status line sits from
 * the leaning left edge at the top (`padding-left` − cut).
 */
private fun armoryTileTagRight(frame: ProfileFrame, size: Size, padLeft: Float, padRight: Float, bottom: Float): Float {
    val cut = frame.d(ARMORY_TILE_CUT)
    val slabRight = size.width - cut * (bottom / size.height).coerceIn(0f, 1f)
    return min(size.width - padRight, slabRight - (padLeft - cut))
}

/** The regular tile's slab cut (dp at the board scale). */
private const val ARMORY_TILE_CUT = 16f

/** A tile's weapon name: two lines at most, shrunk to fit (never below its size at the smallest text setting). */
internal fun armoryTileNameLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: ArmoryType, name: String, width: Float) =
    // A full line height keeps Cyrillic diacritics (Ё, Й) clear of the line above.
    fitKkText(measurer, name, type.tileName * frame.k, width, maxLines = 2, minFactor = 0.6f) {
        measurer.typography.condStyle(it, lineHeightEm = PROFILE_WRAPPED_LINE_HEIGHT)
    }
