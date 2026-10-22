// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest

/** The detail segments behind the Characters and Weapons tabs; [key] names their test tags. */
internal enum class WorkshopSegment(val key: String) {
    UPGRADE("upgrade"),
    TRAITS("traits"),
    MASTERY("mastery"),
    DETAILS("details"),
}

private val CharacterSegments = listOf(WorkshopSegment.UPGRADE, WorkshopSegment.TRAITS)
private val WeaponSegments = listOf(WorkshopSegment.UPGRADE, WorkshopSegment.MASTERY, WorkshopSegment.DETAILS)

/** The segments of [this] tab in strip order; the Core Matrix has none. UPGRADE is the default. */
internal val WorkshopTab.segments: List<WorkshopSegment>
    get() = when (this) {
        WorkshopTab.CHARACTERS -> CharacterSegments
        WorkshopTab.WEAPONS -> WeaponSegments
        WorkshopTab.MATRIX -> emptyList()
    }

/**
 * Workshop presentation state: the shown [tab], the serial of the last shell request applied, and
 * the segment each tab shows. Nothing here reaches Session or Profile.
 */
internal data class WorkshopState(
    val tab: WorkshopTab = WorkshopTab.CHARACTERS,
    val appliedSerial: Int? = null,
    val characterSegment: WorkshopSegment = WorkshopSegment.UPGRADE,
    val weaponSegment: WorkshopSegment = WorkshopSegment.UPGRADE,
) {
    init {
        require(characterSegment in WorkshopTab.CHARACTERS.segments) { "Not a Characters segment: $characterSegment" }
        require(weaponSegment in WorkshopTab.WEAPONS.segments) { "Not a Weapons segment: $weaponSegment" }
    }

    /** The segment [tab] shows, or null for a tab without segments. */
    fun segment(tab: WorkshopTab): WorkshopSegment? = when (tab) {
        WorkshopTab.CHARACTERS -> characterSegment
        WorkshopTab.WEAPONS -> weaponSegment
        WorkshopTab.MATRIX -> null
    }
}

internal sealed interface WorkshopAction {
    /** A shell request (Home row, alias key): applies once per serial and focuses the tab's body. */
    data class Request(val request: WorkshopTabRequest) : WorkshopAction

    /** A tab press, or a digit key ([focusBody]: focus moves to the tab's inspected tile). */
    data class SelectTab(val tab: WorkshopTab, val focusBody: Boolean = false) : WorkshopAction

    /** Left/Right on a focused tab: the neighbour, wrapping; focus stays on the strip. */
    data class StepTab(val forward: Boolean) : WorkshopAction

    data class SelectSegment(val segment: WorkshopSegment) : WorkshopAction

    /** Left/Right inside the focused segment group, wrapping. */
    data class StepSegment(val forward: Boolean) : WorkshopAction

    /** The upgrade block's buy action for [target], whose track the block shows as [track]. */
    data class BuyUpgrade(val target: UpgradeTarget, val track: UpgradeTrackRenderModel) : WorkshopAction
}

internal sealed interface WorkshopEffect {
    data class PlayAudio(val cue: ProfileAudioCue) : WorkshopEffect

    /** Announce the newly shown [tab] (its name and its owned count) in the polite live region. */
    data class AnnounceTab(val tab: WorkshopTab) : WorkshopEffect

    /** Move keyboard focus to the shown tab's inspected tile so arrows act at once. */
    data object FocusBody : WorkshopEffect

    /** Ask the Profile for the next Refit or Honing level of [target] (its typed purchase pulse). */
    data class PurchaseUpgrade(val target: UpgradeTarget) : WorkshopEffect
}

internal data class WorkshopReduction(
    val state: WorkshopState,
    val effects: List<WorkshopEffect> = emptyList(),
)

/**
 * Tabs and segments are local presentation: every decision here is pure. The only Profile intent is
 * an upgrade purchase, asked only for an owned track below its maximum that the balance covers; the
 * Profile Nucleus still decides.
 */
internal object WorkshopReducer {
    fun reduce(state: WorkshopState, action: WorkshopAction): WorkshopReduction = when (action) {
        is WorkshopAction.Request -> if (action.request.serial == state.appliedSerial) {
            WorkshopReduction(state)
        } else {
            val shown = show(state.copy(appliedSerial = action.request.serial), action.request.tab, click = false)
            shown.copy(effects = shown.effects + WorkshopEffect.FocusBody)
        }
        is WorkshopAction.SelectTab -> {
            val shown = show(state, action.tab, click = true)
            if (action.focusBody) shown.copy(effects = shown.effects + WorkshopEffect.FocusBody) else shown
        }
        is WorkshopAction.StepTab -> {
            val tabs = WorkshopTab.entries
            val step = if (action.forward) 1 else tabs.size - 1
            show(state, tabs[(state.tab.ordinal + step) % tabs.size], click = true)
        }
        is WorkshopAction.SelectSegment -> segment(state, action.segment)
        is WorkshopAction.StepSegment -> {
            val segments = state.tab.segments
            val current = state.segment(state.tab)
            if (segments.isEmpty() || current == null) {
                WorkshopReduction(state)
            } else {
                val step = if (action.forward) 1 else segments.size - 1
                segment(state, segments[(segments.indexOf(current) + step) % segments.size])
            }
        }
        is WorkshopAction.BuyUpgrade -> WorkshopReduction(
            state,
            if (action.track.canBuy) listOf(WorkshopEffect.PurchaseUpgrade(action.target)) else emptyList(),
        )
    }

    private fun show(state: WorkshopState, tab: WorkshopTab, click: Boolean): WorkshopReduction = if (state.tab == tab) {
        WorkshopReduction(state)
    } else {
        WorkshopReduction(
            state.copy(tab = tab),
            listOfNotNull(if (click) WorkshopEffect.PlayAudio(ProfileAudioCue.UI_CLICK) else null, WorkshopEffect.AnnounceTab(tab)),
        )
    }

    private fun segment(state: WorkshopState, segment: WorkshopSegment): WorkshopReduction {
        if (segment !in state.tab.segments || state.segment(state.tab) == segment) return WorkshopReduction(state)
        val next = when (state.tab) {
            WorkshopTab.CHARACTERS -> state.copy(characterSegment = segment)
            WorkshopTab.WEAPONS -> state.copy(weaponSegment = segment)
            WorkshopTab.MATRIX -> state
        }
        return WorkshopReduction(next, listOf(WorkshopEffect.PlayAudio(ProfileAudioCue.UI_CLICK)))
    }
}
