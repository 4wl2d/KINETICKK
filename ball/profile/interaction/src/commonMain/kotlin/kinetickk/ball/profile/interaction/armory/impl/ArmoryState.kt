// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.foundation.collections.ImmutableList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the Armory offers for a weapon, from the Profile loadout projection. */
internal enum class ArmoryWeaponStatus {
    STARTER,
    OWNED,
    AFFORDABLE,
    SHORT,
    ;

    val locked: Boolean get() = this == AFFORDABLE || this == SHORT
}

internal fun ArmoryRenderModel.status(definition: WeaponDefinition): ArmoryWeaponStatus = when {
    definition.id == selectedWeapon -> ArmoryWeaponStatus.STARTER
    definition.id in unlockedWeapons -> ArmoryWeaponStatus.OWNED
    totalMatter >= definition.permanentUnlockCost -> ArmoryWeaponStatus.AFFORDABLE
    else -> ArmoryWeaponStatus.SHORT
}

/** The primary action is available for an owned non-starter weapon or an affordable unlock. */
internal val ArmoryWeaponStatus.actionEnabled: Boolean
    get() = this == ArmoryWeaponStatus.OWNED || this == ArmoryWeaponStatus.AFFORDABLE

/** Screen-local selection: the weapon whose details and action the panel shows. */
internal data class ArmoryViewState(
    val inspected: WeaponId,
)

internal sealed interface ArmoryAction {
    data object Back : ArmoryAction
    data object PreviousPage : ArmoryAction
    data object NextPage : ArmoryAction

    /** Hover-free selection (focus, first tap): show this weapon in the detail panel. */
    data class Inspect(val id: WeaponId) : ArmoryAction

    /** Tile activation: the first press inspects, a press on the inspected tile confirms. */
    data class Activate(val id: WeaponId) : ArmoryAction

    /** The detail panel's primary button for [id]. */
    data class Apply(val id: WeaponId) : ArmoryAction
}

internal data class ArmoryReduction(
    val state: ArmoryViewState,
    val effects: List<ArmoryEffect> = emptyList(),
)

internal sealed interface ArmoryEffect {
    data class PurchaseOrEquipWeapon(val id: WeaponId) : ArmoryEffect
    data class PlayAudio(val cue: ProfileAudioCue) : ArmoryEffect
    data class Emit(val output: ArmoryOutput) : ArmoryEffect
    data class ScrollGrid(val forward: Boolean) : ArmoryEffect
}

internal class ArmoryReducer(
    private val weapons: ImmutableList<WeaponDefinition>,
) {
    fun renderModel(
        snapshot: LoadoutProfileSnapshot,
        activeRunWeapon: WeaponId?,
    ): ArmoryRenderModel = ArmoryRenderModel(
        totalMatter = snapshot.economy.matter,
        selectedWeapon = snapshot.loadout.selectedWeapon,
        unlockedWeapons = snapshot.loadout.unlockedWeapons,
        activeRunWeapon = activeRunWeapon,
    )

    fun reduce(state: ArmoryViewState, model: ArmoryRenderModel, action: ArmoryAction): ArmoryReduction = when (action) {
        ArmoryAction.Back -> ArmoryReduction(
            state,
            effects = listOf(
                ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                ArmoryEffect.Emit(ArmoryOutput.Back),
            ),
        )
        ArmoryAction.PreviousPage -> ArmoryReduction(
            state,
            effects = listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK), ArmoryEffect.ScrollGrid(forward = false)),
        )
        ArmoryAction.NextPage -> ArmoryReduction(
            state,
            effects = listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK), ArmoryEffect.ScrollGrid(forward = true)),
        )
        is ArmoryAction.Inspect -> inspect(state, action.id)
        is ArmoryAction.Activate -> if (state.inspected == action.id) apply(state, model, action.id) else inspect(state, action.id)
        is ArmoryAction.Apply -> apply(state, model, action.id)
    }

    private fun inspect(state: ArmoryViewState, id: WeaponId): ArmoryReduction =
        if (state.inspected == id || weapons.none { it.id == id }) {
            ArmoryReduction(state)
        } else {
            ArmoryReduction(ArmoryViewState(id), listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK)))
        }

    // Affordability is only presentation gating (the old disabled button); the Profile Nucleus
    // remains the authority that accepts or rejects the purchase or equip.
    private fun apply(state: ArmoryViewState, model: ArmoryRenderModel, id: WeaponId): ArmoryReduction {
        val definition = weapons.firstOrNull { it.id == id } ?: return ArmoryReduction(state)
        if (!model.status(definition).actionEnabled) return ArmoryReduction(ArmoryViewState(id))
        return ArmoryReduction(ArmoryViewState(id), listOf(ArmoryEffect.PurchaseOrEquipWeapon(id)))
    }
}

/**
 * One geometry for drawing, Compose placement and pointer mapping. Tiles are in grid content
 * coordinates (add [gridViewport] top-left, subtract the grid scroll); every detail rect is in
 * screen coordinates for a detail scroll of 0.
 */
internal class ArmoryLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val gridViewport: Rect,
    val tiles: List<Rect>,
    val gridContentHeight: Float,
    val rowPitch: Float,
    val columns: Int,
    val detailViewport: Rect,
    val detailContentHeight: Float,
    val plate: Rect,
    val name: Rect,
    val description: Rect,
    val tags: Rect,
    val mastery: Rect,
    val ladder: Rect,
    val action: Rect,
    val need: Rect,
) {
    val gridScrollMax: Float get() = max(0f, gridContentHeight - gridViewport.height)
    val detailScrollMax: Float get() = max(0f, detailContentHeight - detailViewport.height)
}

/** Board type sizes per layout mode (design px before the frame scale and the text-size setting). */
internal class ArmoryType(
    val status: Float,
    val tileIcon: Float,
    val tileName: Float,
    val tileTags: Float,
    val name: Float,
    val body: Float,
    val tag: Float,
    val label: Float,
    val ladderMono: Float,
    val ladderName: Float,
    val action: Float,
)

internal fun armoryType(mode: ProfileLayoutMode): ArmoryType = when (mode) {
    ProfileLayoutMode.REGULAR -> ArmoryType(11f, 48f, 24f, 8.5f, 34f, 18f, 13f, 15f, 11f, 13f, 34f)
    else -> ArmoryType(9.5f, 30f, 17f, 7.5f, 21f, 14f, 11f, 12f, 9.5f, 11f, 22f)
}

internal fun armoryLayout(
    frame: ProfileFrame,
    weaponCount: Int,
    textScale: Float,
    backWidth: Float,
    actionWidth: Float,
): ArmoryLayout {
    fun d(value: Float) = frame.d(value)
    val grow = 1f + (textScale.coerceIn(0.75f, 2f) - 1f) * 0.55f
    val back = profileHeaderBackRect(frame, backWidth)
    // Room for the selected lift (−8) and echo (+7) inside the scroll viewport.
    val pad = d(12f)
    val gridLeft: Float
    val gridRight: Float
    val gridTop: Float
    val gridBottom: Float
    val detailLeft: Float
    val detailRight: Float
    val detailTop: Float
    val detailBottom: Float
    val columns: Int
    val gap: Float
    val tileHeight: (width: Float, available: Float) -> Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            gridLeft = frame.x(56f)
            gridRight = frame.x(886f)
            gridTop = frame.headerHeight + d(24f)
            gridBottom = frame.height - d(16f)
            detailLeft = frame.x(950f)
            detailRight = frame.right
            detailTop = d(108f)
            detailBottom = frame.height - d(16f)
            columns = 4
            gap = d(12f)
            tileHeight = { _, _ -> d(172f) * grow }
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val detailWidth = (frame.width * 0.38f).coerceIn(d(250f), d(340f))
            detailRight = frame.right
            detailLeft = detailRight - detailWidth
            gridLeft = frame.left
            gridRight = detailLeft - d(28f)
            gridTop = frame.headerHeight + d(4f)
            gridBottom = frame.height - d(4f)
            detailTop = frame.headerHeight + d(8f)
            detailBottom = frame.height - d(8f)
            columns = if (gridRight - gridLeft >= d(430f)) 4 else 3
            gap = d(8f)
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, width * 0.95f * grow) }
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val dock = d(300f) * (1f + (grow - 1f) * 0.6f)
            gridLeft = frame.left
            gridRight = frame.right
            gridTop = frame.headerHeight
            gridBottom = frame.height - dock
            detailLeft = frame.left
            detailRight = frame.right
            detailTop = gridBottom + d(12f)
            detailBottom = frame.height - d(10f)
            columns = 3
            gap = d(8f)
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, width * 0.95f * grow) }
        }
    }
    val tileWidth = ((gridRight - gridLeft) - gap * (columns - 1)) / columns
    val rows = if (weaponCount <= 0) 0 else (weaponCount + columns - 1) / columns
    // Compact grids size their rows to fit the viewport when a row stays at least 84 px tall.
    val fitHeight = if (rows == 0) 0f else (gridBottom - gridTop - pad - gap * (rows - 1)) / rows
    val tileH = tileHeight(tileWidth, fitHeight)
    val tiles = List(weaponCount) { index ->
        val column = index % columns
        val row = index / columns
        val left = pad + column * (tileWidth + gap)
        val top = pad + row * (tileH + gap)
        Rect(left, top, left + tileWidth, top + tileH)
    }
    val gridViewport = Rect(gridLeft - pad, gridTop - pad, gridRight + pad, max(gridTop, gridBottom))
    val gridContentHeight = if (rows == 0) 0f else pad * 2f + rows * tileH + (rows - 1) * gap

    val type = armoryType(frame.mode)
    val t = textScale.coerceIn(0.75f, 2f)
    val regular = frame.regular
    val plateSize = d(if (regular) 92f else 44f)
    val plate = Rect(detailLeft, detailTop, detailLeft + plateSize, detailTop + plateSize)
    val nameLeft = plate.right + d(if (regular) 18f else 12f)
    val name = Rect(nameLeft, plate.top, detailRight, plate.bottom)
    val descriptionTop = plate.bottom + d(if (regular) 18f else 10f)
    // Larger text wraps into more lines: the slot grows with the text size (whole lines).
    val descriptionLines = kotlin.math.ceil((if (regular) 3f else 2f) * max(1f, t)).toFloat()
    val description = Rect(detailLeft, descriptionTop, detailRight,
        descriptionTop + d(type.body) * 1.4f * descriptionLines * t)
    val tagsTop = description.bottom + d(if (regular) 12f else 8f)
    val tags = Rect(detailLeft, tagsTop, detailRight, tagsTop + d(if (regular) 22f else 20f) * max(1f, t * 0.9f))
    val masteryTop = tags.bottom + d(if (regular) 26f else 10f)
    val mastery = Rect(detailLeft, masteryTop, detailRight, masteryTop + max(frame.density * 24f, d(type.label) * t))
    val ladderTop = mastery.bottom + d(if (regular) 16f else 6f)
    val ladder = Rect(detailLeft, ladderTop, detailRight, ladderTop + d(if (regular) 94f else 60f) * t)
    val actionTop = ladder.bottom + d(if (regular) 26f else 10f)
    val actionHeight = d(if (regular) 72f else 52f)
    val clampedAction = min(actionWidth, detailRight - detailLeft)
    val action = Rect(detailLeft, actionTop, detailLeft + clampedAction, actionTop + actionHeight)
    val need = Rect(action.right + d(16f), action.top, detailRight, action.bottom)
    val detailViewport = Rect(detailLeft - pad, detailTop - pad, detailRight + pad, max(detailTop, detailBottom))
    val detailContentHeight = action.bottom + pad - detailViewport.top
    return ArmoryLayout(frame, back, gridViewport, tiles, gridContentHeight, tileH + gap, columns, detailViewport,
        detailContentHeight, plate, name, description, tags, mastery, ladder, action, need)
}

/**
 * The scroll value one page away: whole rows that fit the viewport (at least one row), clamped
 * to 0..[maxValue].
 */
internal fun armoryGridScrollTarget(value: Float, maxValue: Float, viewportHeight: Float, rowPitch: Float, forward: Boolean): Float {
    if (maxValue <= 0f || rowPitch <= 0f) return 0f
    val step = max(1f, floor(viewportHeight / rowPitch)) * rowPitch
    val target = if (forward) value + step else value - step
    return target.coerceIn(0f, maxValue)
}

/** Page count and the current page (1-based) for the grid scroll. */
internal fun armoryGridPages(value: Float, maxValue: Float, viewportHeight: Float, rowPitch: Float): Pair<Int, Int> {
    if (maxValue <= 0f || rowPitch <= 0f) return 1 to 1
    val step = max(1f, floor(viewportHeight / rowPitch)) * rowPitch
    val pages = ceil(maxValue / step).toInt() + 1
    val page = if (value >= maxValue - 0.5f) pages else (floor(value / step + 0.5f).toInt() + 1).coerceIn(1, pages)
    return pages to page
}

/**
 * Maps a press to the Armory action it hits: the header Back, a weapon tile (activation) or the
 * detail panel's primary action. [gridScroll]/[detailScroll] are the scroll offsets in px.
 */
internal fun resolveArmoryPress(
    layout: ArmoryLayout,
    weapons: List<WeaponDefinition>,
    inspected: WeaponId,
    gridScroll: Float,
    detailScroll: Float,
    x: Float,
    y: Float,
): ArmoryAction? {
    if (layout.back.contains(androidx.compose.ui.geometry.Offset(x, y))) return ArmoryAction.Back
    val grid = layout.gridViewport
    if (x in grid.left..grid.right && y in grid.top..grid.bottom) {
        val contentX = x - grid.left
        val contentY = y - grid.top + gridScroll.coerceIn(0f, layout.gridScrollMax)
        layout.tiles.forEachIndexed { index, tile ->
            if (contentX in tile.left..tile.right && contentY in tile.top..tile.bottom) {
                return weapons.getOrNull(index)?.id?.let(ArmoryAction::Activate)
            }
        }
        return null
    }
    val detail = layout.detailViewport
    if (x in detail.left..detail.right && y in detail.top..detail.bottom) {
        val detailY = y + detailScroll.coerceIn(0f, layout.detailScrollMax)
        if (x in layout.action.left..layout.action.right && detailY in layout.action.top..layout.action.bottom) {
            return ArmoryAction.Apply(inspected)
        }
    }
    return null
}
