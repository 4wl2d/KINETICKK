// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.workshopSegmentHeight
import kinetickk.ball.profile.interaction.workshop.impl.workshopUpgradeBlockHeight
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
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
    /** Not owned and gated above the profile's Rebirth rank, whatever the Matter balance. */
    REBIRTH_LOCKED,
    ;

    val locked: Boolean get() = this == AFFORDABLE || this == SHORT || this == REBIRTH_LOCKED

    /** A locked weapon the player cannot unlock yet: the panel says why beside the disabled action. */
    val blocked: Boolean get() = this == SHORT || this == REBIRTH_LOCKED
}

// Same order as the Profile's purchase decision: the Rebirth gate before the Matter price.
internal fun ArmoryRenderModel.status(definition: WeaponDefinition): ArmoryWeaponStatus = when {
    definition.id == selectedWeapon -> ArmoryWeaponStatus.STARTER
    definition.id in unlockedWeapons -> ArmoryWeaponStatus.OWNED
    rebirthRank < definition.minimumRebirth -> ArmoryWeaponStatus.REBIRTH_LOCKED
    totalMatter >= definition.permanentUnlockCost -> ArmoryWeaponStatus.AFFORDABLE
    else -> ArmoryWeaponStatus.SHORT
}

/** The primary action is available for an owned non-starter weapon or an affordable unlock. */
internal val ArmoryWeaponStatus.actionEnabled: Boolean
    get() = this == ArmoryWeaponStatus.OWNED || this == ArmoryWeaponStatus.AFFORDABLE

/** Why a weapon could not be unlocked: the panel names it beside the action instead of ignoring the press. */
internal enum class ArmoryRefusal {
    INSUFFICIENT_MATTER,
    REBIRTH_LOCKED,
    /** The Profile refused for another reason (its state moved on since the screen last read it). */
    REFUSED,
}

/** The last refused unlock of [weapon], shown until another weapon is inspected or an unlock succeeds. */
internal data class ArmoryNotice(val weapon: WeaponId, val refusal: ArmoryRefusal)

/** The refusal a press on a [ArmoryWeaponStatus.blocked] weapon reports; null for every other status. */
internal fun ArmoryWeaponStatus.refusal(): ArmoryRefusal? = when (this) {
    ArmoryWeaponStatus.SHORT -> ArmoryRefusal.INSUFFICIENT_MATTER
    ArmoryWeaponStatus.REBIRTH_LOCKED -> ArmoryRefusal.REBIRTH_LOCKED
    else -> null
}

/** The refusal shown for the Profile's [ProfileRejection] of a purchase or equip; null when nothing changed. */
internal fun ProfileRejection.armoryRefusal(): ArmoryRefusal? = when (this) {
    ProfileRejection.NoChange -> null
    ProfileRejection.InsufficientMatter -> ArmoryRefusal.INSUFFICIENT_MATTER
    ProfileRejection.WeaponRebirthLocked -> ArmoryRefusal.REBIRTH_LOCKED
    else -> ArmoryRefusal.REFUSED
}

/** Screen-local selection: the weapon whose details and action the panel shows, and its last refusal. */
internal data class ArmoryViewState(
    val inspected: WeaponId,
    val notice: ArmoryNotice? = null,
) {
    /** The refusal to show for the inspected weapon, if its last unlock was refused. */
    val inspectedNotice: ArmoryNotice? get() = notice?.takeIf { it.weapon == inspected }
}

/**
 * A weapon's live synergy read from content and the Profile alone: its [signature] character
 * (active when the next run's character is that one) and the heirloom candidates that carry its
 * Relic key ([harmonizing], in heirloom slot order).
 */
internal class ArmorySynergy(
    val signature: CoreShapeDefinition?,
    val signatureActive: Boolean,
    val harmonizing: List<RelicDefinition>,
)

internal fun ArmoryRenderModel.synergy(
    weapon: WeaponDefinition,
    coreShapes: List<CoreShapeDefinition>,
    relics: List<RelicDefinition>,
): ArmorySynergy = ArmorySynergy(
    signature = coreShapes.firstOrNull { it.id == weapon.signature },
    signatureActive = character == weapon.signature,
    harmonizing = heirloomRelics.distinct().mapNotNull { id -> relics.firstOrNull { it.id == id } }.filter { weapon.key in it.keys },
)

/** Weapon levels of the three mastery forms: the mastery milestones after the base one (3, 6 and 10). */
internal fun armoryFormLevels(masteries: List<WeaponMastery>): List<Int> =
    masteries.map { it.minimumLevel }.filter { it > 1 }.sorted().take(WEAPON_FORM_COUNT)
        .takeIf { it.size == WEAPON_FORM_COUNT } ?: DefaultFormLevels

private const val WEAPON_FORM_COUNT = 3
private val DefaultFormLevels = listOf(3, 6, 10)

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

/** Heirloom candidate ids the render model reads when the Profile holds none. */
internal val NoHeirlooms: ImmutableList<RelicId> = immutableListOf()

internal class ArmoryReducer(
    private val weapons: ImmutableList<WeaponDefinition>,
) {
    fun renderModel(
        snapshot: LoadoutProfileSnapshot,
        activeRunWeapon: WeaponId?,
        heirloomRelics: ImmutableList<RelicId> = NoHeirlooms,
    ): ArmoryRenderModel = ArmoryRenderModel(
        totalMatter = snapshot.economy.matter,
        selectedWeapon = snapshot.loadout.selectedWeapon,
        unlockedWeapons = snapshot.loadout.unlockedWeapons,
        activeRunWeapon = activeRunWeapon,
        rebirthRank = snapshot.rebirthRank,
        character = snapshot.loadout.coreShape,
        heirloomRelics = heirloomRelics,
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

    // Affordability and the Rebirth gate are only presentation gating (a locked button that names its
    // reason); the Profile Nucleus remains the authority that accepts or rejects the purchase or equip.
    private fun apply(state: ArmoryViewState, model: ArmoryRenderModel, id: WeaponId): ArmoryReduction {
        val definition = weapons.firstOrNull { it.id == id } ?: return ArmoryReduction(state)
        val status = model.status(definition)
        status.refusal()?.let { refusal ->
            return ArmoryReduction(ArmoryViewState(id, ArmoryNotice(id, refusal)), listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.REFUSED)))
        }
        if (!status.actionEnabled) return ArmoryReduction(ArmoryViewState(id))
        return ArmoryReduction(ArmoryViewState(id), listOf(ArmoryEffect.PurchaseOrEquipWeapon(id)))
    }

    /**
     * The Profile's answer to the purchase or equip of [id]: a [rejection] is named beside the
     * action (a stale screen can still be refused), an acceptance clears the last refusal.
     */
    fun answered(state: ArmoryViewState, id: WeaponId, rejection: ProfileRejection?): ArmoryViewState = when (rejection) {
        null -> state.copy(notice = null)
        else -> state.copy(notice = rejection.armoryRefusal()?.let { ArmoryNotice(id, it) })
    }
}

/**
 * The grid scroll (px) that shows tile [index] whole, moving as little as possible from [scroll]:
 * a tile above the viewport aligns its top (with the grid's padding for the selection lift), one
 * below aligns its bottom, and a tile taller than the viewport shows its top. Clamped to the
 * scroll range.
 */
internal fun armoryRevealScroll(layout: ArmoryLayout, index: Int, scroll: Float): Float {
    val max = layout.gridScrollMax
    val tile = layout.tiles.getOrNull(index) ?: return scroll.coerceIn(0f, max)
    val margin = layout.tiles.first().top
    val viewport = layout.gridViewport.height
    val target = when {
        tile.top - margin < scroll || tile.height + margin * 2f > viewport -> tile.top - margin
        tile.bottom + margin > scroll + viewport -> tile.bottom + margin - viewport
        else -> scroll
    }
    return target.coerceIn(0f, max)
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
    /** True when the details overflow and [action] is pinned under the scrolling details. */
    val actionPinned: Boolean = false,
    /** Size (px) of the weapon icon every tile draws, and its centre (tile y): one size for the whole grid. */
    val tileIcon: Float = 0f,
    val tileIconY: Float = 0f,
    /** True when the mastery milestones stack one per line under the level cells. */
    val ladderStacked: Boolean = false,
    /** The three mastery forms by level, under the ladder. */
    val forms: Rect = Rect.Zero,
    /** The live synergy: signature character and twist, heirloom harmony, tag resonance. */
    val synergy: Rect = Rect.Zero,
    /** The weapon's typed facts: family, element, Relic key, scaling input and cadence. */
    val facts: Rect = Rect.Zero,
    /** The segmented switch under the tags (Upgrade, Mastery, Details); its cells start at the left. */
    val segments: Rect = Rect.Zero,
    /** The shown segment: the blocks of the other segments are 0 px tall at the segment body's top. */
    val segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    /** The upgrade block's slot (the Upgrade segment). */
    val upgrade: Rect = Rect.Zero,
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

/**
 * Measured heights (px) of the inspected weapon's name block, description and tag rows, and
 * whether the mastery milestones fit side by side with their bonus groups clearly apart.
 */
internal class ArmoryDetailMetrics(
    val nameHeight: Float,
    val descriptionHeight: Float,
    val tagsHeight: Float,
    val masterySideBySide: Boolean = true,
    /** Heights (px) of the mastery forms, the synergy block and the facts as drawn; null keeps an estimate. */
    val formsHeight: Float? = null,
    val synergyHeight: Float? = null,
    val factsHeight: Float? = null,
)

/** A weapon tile's content padding in px (the board's `padding: 14px 22px 14px 24px`; tighter on phones). */
internal class ArmoryTileInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun armoryTileInsets(frame: ProfileFrame): ArmoryTileInsets = if (frame.regular) {
    ArmoryTileInsets(frame.d(24f), frame.d(14f), frame.d(22f), frame.d(14f))
} else {
    ArmoryTileInsets(frame.d(14f), frame.d(10f), frame.d(12f), frame.d(10f))
}

/**
 * The tallest status line and the tallest name block (the name, plus the tag line under it on
 * the regular tile) of all tiles in the grid, in px.
 */
internal class ArmoryTileMetrics(val statusHeight: Float, val nameBlockHeight: Float)

/** Gap (dp at the board scale) between a tile's icon and the status line above / the name below. */
internal const val ARMORY_TILE_ICON_GAP = 4f

/** The smallest tile icon, as a share of the board's icon: tiles grow in height rather than draw less. */
internal const val ARMORY_TILE_ICON_MIN = 0.6f

/**
 * [textScale] is the board-relative text multiplier ([kinetickk.ball.profile.interaction.profileTextScale]).
 * [detail] measures the inspected weapon's texts for a name width and the panel width (the
 * renderer's layouts), so the panel stacks what is actually drawn; without it the slots keep
 * the board's line counts. [tileText] measures the grid's tile texts for the tiles' inner width,
 * so every tile draws its icon at one size between them; without it the icon keeps the board's size.
 */
internal fun armoryLayout(
    frame: ProfileFrame,
    weaponCount: Int,
    textScale: Float,
    backWidth: Float,
    actionWidth: Float,
    needRoom: Boolean = false,
    masteryCount: Int = 4,
    /** The detail segment shown under the tags; the others take no room. */
    segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    tileText: ((innerWidth: Float) -> ArmoryTileMetrics)? = null,
    detail: ((nameWidth: Float, width: Float) -> ArmoryDetailMetrics)? = null,
): ArmoryLayout {
    fun d(value: Float) = frame.d(value)
    val grow = profileLayoutGrow(textScale, 0.55f)
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
            // Rows stay at least 84 px tall (the press target), even when tiles are narrower than that.
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, max(d(84f) * grow, width * 0.95f * grow)) }
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            // The detail dock grows with the text so the ladder starts above the pinned action.
            val dock = d(300f) * grow
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
            // Rows stay at least 84 px tall (the press target), even when tiles are narrower than that.
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, max(d(84f) * grow, width * 0.95f * grow)) }
        }
    }
    val tileWidth = ((gridRight - gridLeft) - gap * (columns - 1)) / columns
    val rows = if (weaponCount <= 0) 0 else (weaponCount + columns - 1) / columns
    // Compact grids size their rows to fit the viewport when a row stays at least 84 px tall.
    val fitHeight = if (rows == 0) 0f else (gridBottom - gridTop - pad - gap * (rows - 1)) / rows
    val type = armoryType(frame.mode)
    // Every tile draws its icon at one size, between the tallest status line and the tallest
    // name block of the grid, so a name that wraps never shrinks its own icon; when large text
    // leaves less than the smallest icon, the tiles grow (the grid scrolls) instead.
    val insets = armoryTileInsets(frame)
    val tileMetrics = tileText?.invoke(tileWidth - insets.left - insets.right)
    val iconMax = d(type.tileIcon)
    val iconMin = iconMax * ARMORY_TILE_ICON_MIN
    val iconGap = d(ARMORY_TILE_ICON_GAP)
    val iconBandTop = if (tileMetrics == null) 0f else insets.top + tileMetrics.statusHeight + iconGap
    val iconBandBelow = if (tileMetrics == null) 0f else insets.bottom + tileMetrics.nameBlockHeight + iconGap
    val tileH = max(tileHeight(tileWidth, fitHeight), if (tileMetrics == null) 0f else iconBandTop + iconMin + iconBandBelow)
    val tileIcon = if (tileMetrics == null) iconMax else (tileH - iconBandTop - iconBandBelow).coerceIn(iconMin, iconMax)
    val tileIconY = if (tileMetrics == null) tileH * 0.5f else (iconBandTop + tileH - iconBandBelow) * 0.5f
    val tiles = List(weaponCount) { index ->
        val column = index % columns
        val row = index / columns
        val left = pad + column * (tileWidth + gap)
        val top = pad + row * (tileH + gap)
        Rect(left, top, left + tileWidth, top + tileH)
    }
    val gridViewport = Rect(gridLeft - pad, gridTop - pad, gridRight + pad, max(gridTop, gridBottom))
    val gridContentHeight = if (rows == 0) 0f else pad * 2f + rows * tileH + (rows - 1) * gap

    val t = textScale.coerceIn(0.75f, 2f)
    val regular = frame.regular
    val plateSize = d(if (regular) 92f else 44f)
    val plate = Rect(detailLeft, detailTop, detailLeft + plateSize, detailTop + plateSize)
    val nameLeft = plate.right + d(if (regular) 18f else 12f)
    val metrics = detail?.invoke(detailRight - nameLeft, detailRight - detailLeft)
    // A name that fits the plate is centred on it; a taller one (two lines of large text) starts
    // at the plate's top and pushes the description down, so it never rises out of the panel.
    val nameHeight = metrics?.nameHeight ?: plateSize
    val name = if (nameHeight <= plateSize) {
        Rect(nameLeft, plate.center.y - nameHeight * 0.5f, detailRight, plate.center.y + nameHeight * 0.5f)
    } else {
        Rect(nameLeft, plate.top, detailRight, plate.top + nameHeight)
    }
    val descriptionTop = max(plate.bottom, name.bottom) + d(if (regular) 18f else 10f)
    // The description slot is as tall as the wrapped description (the details scroll), and at
    // least the board's slot (3 lines, 2 on phones, at the board size) so short texts keep the
    // board's spacing.
    val boardDescription = d(type.body) * 1.4f * (if (regular) 3f else 2f)
    val description = Rect(detailLeft, descriptionTop, detailRight,
        descriptionTop + max(boardDescription, metrics?.descriptionHeight ?: (boardDescription * ceil((if (regular) 3f else 2f) * max(1f, t)) *
            t / (if (regular) 3f else 2f))))
    val tagsTop = description.bottom + d(if (regular) 12f else 8f)
    val tags = Rect(detailLeft, tagsTop, detailRight, tagsTop + (metrics?.tagsHeight ?: armoryTagHeight(frame, t)))
    // The segmented switch under the tags; only the shown segment's blocks take room below it.
    val segmentsTop = tags.bottom + d(if (regular) 20f else 10f)
    val segments = Rect(detailLeft, segmentsTop, detailRight, segmentsTop + workshopSegmentHeight(frame))
    val bodyTop = segments.bottom + d(if (regular) 16f else 8f)
    fun hidden() = Rect(detailLeft, bodyTop, detailRight, bodyTop)
    val upgrade = if (segment == WorkshopSegment.UPGRADE) {
        Rect(detailLeft, bodyTop, detailRight, bodyTop + workshopUpgradeBlockHeight(frame))
    } else hidden()
    val showMastery = segment == WorkshopSegment.MASTERY
    val mastery = if (showMastery) Rect(detailLeft, bodyTop, detailRight, bodyTop + max(frame.density * 24f, d(type.label) * t)) else hidden()
    val ladderTop = mastery.bottom + if (showMastery) d(if (regular) 16f else 6f) else 0f
    // Large text, or a panel too narrow to keep the milestones' bonus groups clearly apart,
    // stacks the milestones (one line each) instead of squeezing them side by side.
    val ladderStacked = armoryMasteryStacked(t) || metrics?.masterySideBySide == false
    val ladderHeight = if (ladderStacked) {
        armoryLadderCellsTop(frame, stacked = true, textScale = t) + d(if (regular) 12f else 10f) + d(10f) +
            masteryCount * d(type.ladderName) * ARMORY_STACKED_LINE * t
    } else {
        d(if (regular) 94f else 60f) * t
    }
    val ladder = Rect(detailLeft, ladderTop, detailRight, ladderTop + if (showMastery) ladderHeight else 0f)
    // The weapon sheet: the mastery forms under the ladder; the live synergy and the typed facts on Details.
    val sectionGap = d(if (regular) 24f else 14f)
    val bodyLine = d(type.body) * 1.4f * t
    val labelLine = d(type.label) * 1.5f * t
    val forms = if (showMastery) {
        val formsTop = ladder.bottom + sectionGap
        Rect(detailLeft, formsTop, detailRight, formsTop + (metrics?.formsHeight ?: (labelLine + bodyLine * 3f + d(18f))))
    } else hidden()
    val showDetails = segment == WorkshopSegment.DETAILS
    val synergy = if (showDetails) {
        Rect(detailLeft, bodyTop, detailRight, bodyTop + (metrics?.synergyHeight ?: (labelLine * 4f + bodyLine * 5f)))
    } else hidden()
    val facts = if (showDetails) {
        val factsTop = synergy.bottom + sectionGap
        Rect(detailLeft, factsTop, detailRight, factsTop + (metrics?.factsHeight ?: (labelLine * 5f + d(16f))))
    } else hidden()
    val visibleBottom = maxOf(upgrade.bottom, forms.bottom, facts.bottom)
    val actionHeight = d(if (regular) 72f else 52f)
    // A locked weapon keeps room beside the button for "Need N more matter".
    val actionRoom = (detailRight - detailLeft) * if (needRoom) 0.64f else 1f
    val clampedAction = min(actionWidth, actionRoom)
    val limit = max(detailTop + actionHeight, detailBottom)
    val naturalTop = visibleBottom + d(if (regular) 26f else 10f)
    // When the details run past the fold (large text, phones) the primary action is pinned to
    // the bottom of the panel and the details above it scroll.
    val actionPinned = naturalTop + actionHeight > limit
    val actionTop = if (actionPinned) limit - actionHeight else naturalTop
    val action = Rect(detailLeft, actionTop, detailLeft + clampedAction, actionTop + actionHeight)
    val need = Rect(action.right + d(16f), action.top, detailRight, action.bottom)
    val detailViewport = Rect(detailLeft - pad, detailTop - pad, detailRight + pad,
        if (actionPinned) action.top - d(8f) else limit)
    val detailContentHeight = visibleBottom + pad - detailViewport.top
    return ArmoryLayout(frame, back, gridViewport, tiles, gridContentHeight, tileH + gap, columns, detailViewport,
        detailContentHeight, plate, name, description, tags, mastery, ladder, action, need, actionPinned,
        tileIcon, tileIconY, ladderStacked, forms, synergy, facts, segments, segment, upgrade)
}

/** Height in px of one detail tag chip at the board-relative text multiplier [textScale]. */
internal fun armoryTagHeight(frame: ProfileFrame, textScale: Float): Float =
    frame.d(if (frame.regular) 22f else 20f) * max(1f, textScale.coerceIn(0.75f, 2f) * 0.9f)

/**
 * Offset of the mastery level cells below the ladder's top: room for the "Lvl N" labels above
 * them, or a small gap when the milestones are [stacked] under the cells (their levels are in the list).
 */
internal fun armoryLadderCellsTop(frame: ProfileFrame, stacked: Boolean, textScale: Float): Float = when {
    stacked -> frame.d(4f)
    else -> frame.d(if (frame.regular) 30f else 22f) * textScale.coerceIn(0.75f, 2f)
}

/** From this text size (board-relative) the mastery milestones stack one per line under the level cells. */
internal fun armoryMasteryStacked(textScale: Float): Boolean = textScale >= 1.3f

/** Line pitch of a stacked milestone, in multiples of its name size. */
internal const val ARMORY_STACKED_LINE = 1.5f

/**
 * Side-by-side milestones need this many gaps of a bonus group's own inner gap between two
 * neighbouring groups, so each pair of values reads as one milestone's.
 */
internal const val ARMORY_BONUS_SEPARATION = 3f

/**
 * Whether the milestones fit side by side on a ladder [width] px wide: each bonus group of
 * [groupWidths] (px, in milestone order) fits before the next milestone's cell, the last one
 * ends at the right edge, and neighbouring groups stay [ARMORY_BONUS_SEPARATION] × [innerGap] apart.
 */
internal fun armoryMasteryFitsSideBySide(
    frame: ProfileFrame,
    levels: List<Int>,
    groupWidths: List<Float>,
    innerGap: Float,
    width: Float,
): Boolean {
    if (levels.isEmpty()) return true
    val cells = levels.max().coerceIn(1, 40)
    val cellGap = frame.d(4f)
    val pitch = (width - cellGap * (cells - 1)) / cells + cellGap
    var previousRight = Float.NEGATIVE_INFINITY
    levels.forEachIndexed { index, level ->
        val groupWidth = groupWidths[index]
        val last = index == levels.lastIndex
        val left = if (last) width - groupWidth else (level - 1).coerceAtLeast(0) * pitch
        if (!last && groupWidth > (levels[index + 1] - 1) * pitch - left - frame.d(6f)) return false
        if (left - previousRight < innerGap * ARMORY_BONUS_SEPARATION) return false
        previousRight = left + groupWidth
    }
    return true
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
 * detail panel's primary action. [gridScroll] is the grid scroll offset in px (the detail
 * scroll moves only non-interactive details).
 */
internal fun resolveArmoryPress(
    layout: ArmoryLayout,
    weapons: List<WeaponDefinition>,
    inspected: WeaponId,
    gridScroll: Float,
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
    // The primary action never scrolls: it follows the details when they fit and is pinned
    // under them when they do not.
    if (x in layout.action.left..layout.action.right && y in layout.action.top..layout.action.bottom) {
        return ArmoryAction.Apply(inspected)
    }
    return null
}
