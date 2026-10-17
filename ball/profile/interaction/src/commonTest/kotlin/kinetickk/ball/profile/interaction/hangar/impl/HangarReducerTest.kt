// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HangarShapeProgress
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun hangarProjection(
    unlocked: Set<CoreShape> = setOf(CoreShape.ORB, CoreShape.PRISM),
    selected: CoreShape = CoreShape.ORB,
): HangarProjection = HangarProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(3L),
    shapes = defaultCoreShapeDefinitions().map { definition ->
        val target = definition.unlockTarget.toLong()
        HangarShapeProgress(definition.id, definition.id in unlocked, if (definition.id in unlocked) target else target / 2, target)
    }.toImmutableList(),
    selected = selected,
)

class HangarReducerTest {
    private val definitions = defaultCoreShapeDefinitions()

    @Test
    fun theRosterFollowsTheProfileAndNormalizesTraitBarsOverTheRoster() {
        val model = hangarProjection().toRenderModel(definitions, matter = 42L)
        assertEquals(CoreShape.entries.toList(), model.shapes.map { it.shape })
        assertEquals(2, model.unlockedCount)
        assertEquals(42L, model.matter)
        val star = model.shape(CoreShape.STAR)!!
        assertFalse(star.unlocked)
        assertEquals(750L to 1_500L, star.progress to star.target)
        val prism = model.shape(CoreShape.PRISM)!!
        // The Prism is the heaviest and toughest form: its mass and integrity bars are full.
        assertEquals(1f, prism.massBar)
        assertEquals(1f, prism.integrityBar)
        // Its extra drag makes it the clumsiest; the Spiral's low drag handles best.
        assertEquals(HANGAR_BAR_FLOOR, prism.handlingBar)
        assertEquals(HANGAR_BAR_FLOOR, model.shape(CoreShape.ORB)!!.massBar, "neutral mass is the roster's lightest")
        assertEquals(1f, model.shape(CoreShape.SPIRAL)!!.handlingBar)
        assertEquals(1f, model.shape(CoreShape.SHARD)!!.heatBar)
        assertTrue(model.shapes.all { it.massBar in 0f..1f && it.integrityBar in 0f..1f && it.handlingBar in 0f..1f && it.heatBar in 0f..1f })
    }

    @Test
    fun charactersThatClearedTheFinalTierAreAscendant() {
        assertTrue(hangarProjection().toRenderModel(definitions, 0L).shapes.none { it.ascendant })
        val crowned = hangarProjection().copy(ascendedCharacters = kinetickk.foundation.collections.immutableSetOf(CoreShape.PRISM))
            .toRenderModel(definitions, 0L)
        assertEquals(listOf(CoreShape.PRISM), crowned.shapes.filter { it.ascendant }.map { it.shape })
        assertFalse(crowned.ascendant)
        assertTrue(hangarProjection().copy(ascendant = true).toRenderModel(definitions, 0L).ascendant)
    }

    @Test
    fun aPressInspectsThenSelectsAndOnlyUnlockedCharactersCanBeChosen() {
        val state = HangarState(hangarProjection().toRenderModel(definitions, 0L))
        assertEquals(CoreShape.ORB, state.inspected, "the Hangar opens on the selected character")
        assertFalse(state.canSelect, "the selected character is already chosen")

        val inspect = HangarReducer.reduce(state, HangarAction.Activate(CoreShape.PRISM))
        assertEquals(CoreShape.PRISM, inspect.state.inspected)
        assertTrue(inspect.effects.isEmpty(), "the first press only inspects")
        val select = HangarReducer.reduce(inspect.state, HangarAction.Activate(CoreShape.PRISM))
        assertEquals(listOf<HangarUiEffect>(HangarUiEffect.Select(CoreShape.PRISM)), select.effects)

        val locked = HangarReducer.reduce(state, HangarAction.Inspect(CoreShape.HEXAGON)).state
        assertFalse(locked.canSelect)
        assertTrue(HangarReducer.reduce(locked, HangarAction.SelectInspected).effects.isEmpty())
        assertTrue(HangarReducer.reduce(locked, HangarAction.Activate(CoreShape.HEXAGON)).effects.isEmpty())

        val back = HangarReducer.reduce(state, HangarAction.Back).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<HangarUiEffect.PlayAudio>(back[0]).cue)
        assertEquals(HangarOutput.Back, assertIs<HangarUiEffect.Emit>(back[1]).output)

        val refreshed = HangarReducer.selected(select.state, hangarProjection(selected = CoreShape.PRISM).toRenderModel(definitions, 0L))
        assertEquals(CoreShape.PRISM, refreshed.model.selected)
        assertEquals(CoreShape.PRISM, refreshed.inspected)
        assertFalse(refreshed.canSelect)
    }

    @Test
    fun everyLayoutKeepsTwelveTouchSizedTilesAndTheSelectButtonOnScreen() {
        val viewports = listOf(
            Triple(1_440f, 810f, 1f), Triple(1_000f, 700f, 1f), Triple(900f, 560f, 1f), Triple(2_880f, 1_620f, 2f),
            Triple(844f, 390f, 1f), Triple(800f, 360f, 1f), Triple(2_400f, 1_080f, 3f),
            Triple(390f, 844f, 1f), Triple(360f, 640f, 1f), Triple(1_080f, 2_400f, 3f),
        )
        val modes = mutableSetOf<ProfileLayoutMode>()
        viewports.forEach { (width, height, density) ->
            val frame = profileFrame(width, height, density)
            modes += frame.mode
            listOf(1f, 1.4f).forEach { textScale ->
                val layout = hangarLayout(frame, textScale, backWidth = 90f * density)
                val where = "${width}x$height @$density x$textScale"
                assertEquals(HANGAR_COLUMNS * HANGAR_ROWS, layout.tiles.size)
                (layout.tiles + layout.select).forEach { rect ->
                    assertTrue(rect.width / density >= 48f && rect.height / density >= 48f, "$rect is under 48 dp at $where")
                    assertTrue(rect.left >= 0f && rect.top >= 0f && rect.right <= width + 0.5f && rect.bottom <= height + 0.5f,
                        "$rect leaves the screen at $where")
                }
                layout.tiles.forEachIndexed { index, tile ->
                    layout.tiles.drop(index + 1).forEach { other -> assertFalse(tile.overlaps(other), "tiles overlap at $where") }
                    assertFalse(tile.overlaps(layout.select), "a tile covers Select at $where")
                    assertFalse(tile.overlaps(layout.detailViewport), "a tile covers the details at $where")
                    assertTrue(tile.top >= frame.headerHeight - 0.5f, "a tile runs under the header at $where")
                }
                assertTrue(layout.detailViewport.height > 0f, "the details have room at $where")
                val state = HangarState(hangarProjection().toRenderModel(definitions, 0L))
                CoreShape.entries.forEachIndexed { index, shape ->
                    val center = layout.tiles[index].center
                    assertEquals(HangarAction.Activate(shape), resolveHangarPress(layout, state, center.x, center.y))
                }
                assertEquals(HangarAction.SelectInspected, resolveHangarPress(layout, state, layout.select.center.x, layout.select.center.y))
                assertEquals(HangarAction.Back, resolveHangarPress(layout, state, layout.back.center.x, layout.back.center.y))
            }
        }
        assertEquals(ProfileLayoutMode.entries.toSet(), modes, "regular, landscape and portrait are all covered")
    }
}
