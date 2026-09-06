// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DamageNumberLocalizationTest {
    @Test
    fun localizedCompactDamageKeepsRoundingPromotionAndAllSupportedMagnitudes() {
        val cases = listOf(
            Triple(999L, "999", "999"),
            Triple(1_000L, "1K", "1 тыс."),
            Triple(1_250L, "1.3K", "1,3 тыс."),
            Triple(999_949L, "999.9K", "999,9 тыс."),
            Triple(999_950L, "1M", "1 млн"),
            Triple(1_250_000_000L, "1.3B", "1,3 млрд"),
            Triple(1_250_000_000_000L, "1.3T", "1,3 трлн"),
            Triple(1_250_000_000_000_000L, "1.3Qa", "1,3 квадрлн"),
            Triple(Long.MAX_VALUE, "9.2Qi", "9,2 квинтлн"),
            Triple(-1_200L, "-1.2K", "-1,2 тыс."),
        )
        cases.forEach { (amount, english, russian) ->
            val number = DamageNumberProjection(0f, 0f, amount, false, 0.65f)
            assertEquals(english, number.formattedAmount(DamageNumberFormat.COMPACT))
            assertEquals(russian, number.formattedAmount(DamageNumberFormat.COMPACT, AppLanguage.Russian))
            AppLanguage.entries.forEach { language ->
                assertEquals(amount.toString(), number.formattedAmount(DamageNumberFormat.FULL, language))
            }
        }
    }

    @Test
    fun advancingAndRebasingEffectsRetainsBothCachedTranslations() {
        val reducer = InteractionFxReducer(seed = 12)
        reducer.apply(listOf(VisualFxCue.DamageNumberAdded(100f, 200f, 1_250L, true)))
        val original = reducer.snapshot().damageNumbers.single()
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(0.1f), VisualFxCue.WorldRebased(40f, 50f)))
        val snapshot = reducer.snapshot()
        val advanced = snapshot.damageNumbers.single()
        assertEquals(60f, advanced.x)
        assertEquals(146.6f, advanced.y, 0.001f)
        assertEquals(0.55f, advanced.life, 0.001f)
        assertSame(original.compactAmount, advanced.compactAmount)
        assertSame(original.russianCompactAmount, advanced.russianCompactAmount)
        assertSame(original.fullAmount, advanced.fullAmount)
        assertSame(advanced.russianCompactAmount, advanced.formattedAmount(DamageNumberFormat.COMPACT, AppLanguage.Russian))
        assertEquals("1,3 тыс.", advanced.russianCompactAmount)
        assertSame(snapshot, reducer.snapshot())
    }
}
