// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Legendary is a power spike in presentation too (decisions D4): its first pick plays the Legendary chime, marks its
 * build toast as a Legendary (a typed flag, no message parsing) and rings two gold shockwaves with a gold burst at the
 * Core; any other pick keeps the plain level-up sound and toast.
 */
class LegendaryAnnouncementTest {
    @Test
    fun aLegendaryPickIsAnnouncedAndAPlainPickIsNot() {
        val state = buildTestState(rebirth = 10).apply {
            level = 30
            lifetimeMatter = 4_000L
        }
        state.openItemOfferWithFloor(ItemRarity.LEGENDARY)
        val card = state.choices.indexOfFirst { state.content.items[requireNotNull(it.itemId)].rarity == ItemRarity.LEGENDARY }
        assertTrue(card >= 0)
        state.takeSoundCues()
        state.takeVisualFxCues()
        state.choose(card)
        val sounds = state.takeSoundCues()
        val visuals = state.takeVisualFxCues()
        assertTrue(GameplayAudioCue.LEGENDARY_ACQUIRED in sounds && GameplayAudioCue.LEVEL_UP !in sounds, "$sounds")
        assertTrue(visuals.filterIsInstance<VisualFxCue.BuildChanged>().single().legendary)
        assertEquals(2, visuals.count { it is VisualFxCue.ShockwaveAdded && it.colorIndex == FxColorCode.APEX_GOLD })
        assertTrue(visuals.any { it is VisualFxCue.Burst && it.colorIndex == FxColorCode.APEX_GOLD })

        state.openItemOfferWithFloor(ItemRarity.COMMON)
        val plain = state.choices.indexOfFirst { state.content.items[requireNotNull(it.itemId)].rarity != ItemRarity.LEGENDARY }
        state.choose(plain)
        val plainSounds = state.takeSoundCues()
        assertTrue(GameplayAudioCue.LEVEL_UP in plainSounds && GameplayAudioCue.LEGENDARY_ACQUIRED !in plainSounds, "$plainSounds")
        assertFalse(state.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>().single().legendary)
    }
}
