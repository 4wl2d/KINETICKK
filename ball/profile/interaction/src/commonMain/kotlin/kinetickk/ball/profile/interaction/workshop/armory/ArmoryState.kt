// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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
        upgrades = snapshot.upgrades,
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
