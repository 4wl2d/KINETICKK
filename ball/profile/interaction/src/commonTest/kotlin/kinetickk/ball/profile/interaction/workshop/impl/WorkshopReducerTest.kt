// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HangarShapeProgress
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.hangar.impl.HangarAction
import kinetickk.ball.profile.interaction.hangar.impl.HangarReducer
import kinetickk.ball.profile.interaction.hangar.impl.HangarState
import kinetickk.ball.profile.interaction.hangar.impl.HangarUiEffect
import kinetickk.ball.profile.interaction.hangar.impl.toRenderModel
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkshopReducerTest {
    @Test
    fun aTabRequestAppliesOncePerSerial() {
        val start = WorkshopState(WorkshopTab.CHARACTERS)
        val first = WorkshopReducer.reduce(start, WorkshopAction.Request(WorkshopTabRequest(WorkshopTab.MATRIX, 1)))
        assertEquals(WorkshopTab.MATRIX, first.state.tab)
        assertEquals(1, first.state.appliedSerial)
        // A shell request is not a click: it announces the tab and moves focus to its inspected tile.
        assertEquals(listOf(WorkshopEffect.AnnounceTab(WorkshopTab.MATRIX), WorkshopEffect.FocusBody), first.effects)

        // The player moves on; the same request seen again (a recomposition, a reopening) changes nothing.
        val moved = WorkshopReducer.reduce(first.state, WorkshopAction.SelectTab(WorkshopTab.WEAPONS)).state
        val repeated = WorkshopReducer.reduce(moved, WorkshopAction.Request(WorkshopTabRequest(WorkshopTab.MATRIX, 1)))
        assertSame(moved, repeated.state)
        assertTrue(repeated.effects.isEmpty())

        // A new serial for the same tab applies again; for the shown tab it only refocuses the body.
        val again = WorkshopReducer.reduce(moved, WorkshopAction.Request(WorkshopTabRequest(WorkshopTab.MATRIX, 2)))
        assertEquals(WorkshopTab.MATRIX, again.state.tab)
        val sameTab = WorkshopReducer.reduce(again.state, WorkshopAction.Request(WorkshopTabRequest(WorkshopTab.MATRIX, 3)))
        assertEquals(WorkshopTab.MATRIX, sameTab.state.tab)
        assertEquals(3, sameTab.state.appliedSerial)
        assertEquals(listOf<WorkshopEffect>(WorkshopEffect.FocusBody), sameTab.effects)
    }

    @Test
    fun tabsAndSegmentsAreLocalPresentationEffects() {
        val start = WorkshopState()
        val press = WorkshopReducer.reduce(start, WorkshopAction.SelectTab(WorkshopTab.WEAPONS))
        assertEquals(WorkshopTab.WEAPONS, press.state.tab)
        assertEquals(listOf(WorkshopEffect.PlayAudio(ProfileAudioCue.UI_CLICK), WorkshopEffect.AnnounceTab(WorkshopTab.WEAPONS)), press.effects)
        // Pressing the shown tab does nothing; a digit also focuses the body.
        assertTrue(WorkshopReducer.reduce(press.state, WorkshopAction.SelectTab(WorkshopTab.WEAPONS)).effects.isEmpty())
        val digit = WorkshopReducer.reduce(press.state, WorkshopAction.SelectTab(WorkshopTab.MATRIX, focusBody = true))
        assertEquals(WorkshopEffect.FocusBody, digit.effects.last())

        // Left/Right on the strip wraps in strip order.
        assertEquals(WorkshopTab.MATRIX, WorkshopReducer.reduce(press.state, WorkshopAction.StepTab(forward = true)).state.tab)
        assertEquals(WorkshopTab.CHARACTERS, WorkshopReducer.reduce(press.state, WorkshopAction.StepTab(forward = false)).state.tab)
        val last = WorkshopState(WorkshopTab.MATRIX)
        assertEquals(WorkshopTab.CHARACTERS, WorkshopReducer.reduce(last, WorkshopAction.StepTab(forward = true)).state.tab)
        assertEquals(WorkshopTab.MATRIX, WorkshopReducer.reduce(start, WorkshopAction.StepTab(forward = false)).state.tab)

        // Upgrade is every tab's first segment; each tab keeps its own while the other changes.
        assertEquals(WorkshopSegment.UPGRADE, start.segment(WorkshopTab.CHARACTERS))
        assertEquals(WorkshopSegment.UPGRADE, start.segment(WorkshopTab.WEAPONS))
        assertEquals(null, start.segment(WorkshopTab.MATRIX))
        val details = WorkshopReducer.reduce(press.state, WorkshopAction.SelectSegment(WorkshopSegment.DETAILS))
        assertEquals(WorkshopSegment.DETAILS, details.state.weaponSegment)
        assertEquals(WorkshopSegment.UPGRADE, details.state.characterSegment)
        assertEquals(listOf<WorkshopEffect>(WorkshopEffect.PlayAudio(ProfileAudioCue.UI_CLICK)), details.effects)
        // A segment of another tab, or the shown one, changes nothing.
        assertSame(press.state, WorkshopReducer.reduce(press.state, WorkshopAction.SelectSegment(WorkshopSegment.TRAITS)).state)
        assertTrue(WorkshopReducer.reduce(details.state, WorkshopAction.SelectSegment(WorkshopSegment.DETAILS)).effects.isEmpty())
        // Left/Right inside the group wraps; the Core Matrix has none.
        assertEquals(WorkshopSegment.UPGRADE, WorkshopReducer.reduce(details.state, WorkshopAction.StepSegment(forward = true)).state.weaponSegment)
        assertEquals(WorkshopSegment.MASTERY, WorkshopReducer.reduce(details.state, WorkshopAction.StepSegment(forward = false)).state.weaponSegment)
        val traits = WorkshopReducer.reduce(start, WorkshopAction.StepSegment(forward = false)).state
        assertEquals(WorkshopSegment.TRAITS, traits.characterSegment)
        assertSame(last, WorkshopReducer.reduce(last, WorkshopAction.StepSegment(forward = true)).state)
        // Switching tabs keeps each tab's segment for this opening.
        val back = WorkshopReducer.reduce(details.state, WorkshopAction.SelectTab(WorkshopTab.CHARACTERS)).state
        assertEquals(WorkshopSegment.DETAILS, WorkshopReducer.reduce(back, WorkshopAction.SelectTab(WorkshopTab.WEAPONS)).state.weaponSegment)
    }

    @Test
    fun aStateRejectsASegmentOfAnotherTab() {
        assertFailsWith<IllegalArgumentException> { WorkshopState(characterSegment = WorkshopSegment.DETAILS) }
        assertFailsWith<IllegalArgumentException> { WorkshopState(weaponSegment = WorkshopSegment.TRAITS) }
        assertEquals(listOf(WorkshopSegment.UPGRADE, WorkshopSegment.TRAITS), WorkshopTab.CHARACTERS.segments)
        assertEquals(listOf(WorkshopSegment.UPGRADE, WorkshopSegment.MASTERY, WorkshopSegment.DETAILS), WorkshopTab.WEAPONS.segments)
    }

    @Test
    fun selectAsksForTheCharacterOnlyWhenSelectionIsAvailable() {
        val definitions = defaultCoreShapeDefinitions()
        val model = HangarProjection(
            LOCAL_PROFILE_INSTANCE_ID, ProfileRevision(1L),
            definitions.map { HangarShapeProgress(it.id, it.id != CoreShape.STAR, 0L, it.unlockTarget.toLong()) }.toImmutableList(),
            CoreShape.ORB,
        ).toRenderModel(definitions, 0L)
        val between = HangarState(model, CoreShape.PRISM, selectionAvailable = true)
        assertTrue(between.canSelect)
        assertEquals(listOf<HangarUiEffect>(HangarUiEffect.Select(CoreShape.PRISM)),
            HangarReducer.reduce(between, HangarAction.SelectInspected).effects)
        // Mid-run (Session withholds selection) neither Select nor a second press asks for anything.
        val inRun = between.copy(selectionAvailable = false)
        assertFalse(inRun.canSelect)
        assertTrue(HangarReducer.reduce(inRun, HangarAction.SelectInspected).effects.isEmpty())
        assertTrue(HangarReducer.reduce(inRun, HangarAction.Activate(CoreShape.PRISM)).effects.isEmpty())
        // A locked character or the selected one is never asked for, selection available or not.
        assertTrue(HangarReducer.reduce(between.copy(inspected = CoreShape.STAR), HangarAction.SelectInspected).effects.isEmpty())
        assertTrue(HangarReducer.reduce(between.copy(inspected = CoreShape.ORB), HangarAction.SelectInspected).effects.isEmpty())
    }
}
