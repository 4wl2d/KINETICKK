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
    fun damageNumbersReadThreeSignificantDigitsWithLatinSuffixesInBothLanguages() {
        val cases = listOf(
            Triple(999L, "999", "999"),
            Triple(1_000L, "1K", "1K"),
            Triple(1_250L, "1.25K", "1,25K"),
            Triple(999_499L, "999K", "999K"),
            Triple(999_500L, "1M", "1M"),
            Triple(1_250_000_000L, "1.25B", "1,25B"),
            Triple(1_250_000_000_000L, "1.25T", "1,25T"),
            Triple(1_250_000_000_000_000L, "1.25Qa", "1,25Qa"),
            Triple(Long.MAX_VALUE, "9.22Qi", "9,22Qi"),
            Triple(-1_200L, "−1.2K", "−1,2K"),
        )
        cases.forEach { (amount, english, russian) ->
            val number = DamageNumberProjection(0f, 0f, amount, false, 0.6f)
            assertEquals(english, number.formattedAmount(DamageNumberFormat.COMPACT))
            assertEquals(russian, number.formattedAmount(DamageNumberFormat.COMPACT, AppLanguage.Russian))
        }
        val big = DamageNumberProjection(0f, 0f, 2_468_013L, false, 0.6f)
        assertEquals("2,468,013", big.formattedAmount(DamageNumberFormat.FULL, AppLanguage.English))
        assertEquals("2\u202F468\u202F013", big.formattedAmount(DamageNumberFormat.FULL, AppLanguage.Russian))
    }

    @Test
    fun aSmallNumberBuildsOneStringForEveryFormAndLanguage() {
        val number = DamageNumberProjection(0f, 0f, 840L, false, 0.6f)
        assertEquals("840", number.compactAmount)
        DamageNumberFormat.entries.forEach { format ->
            AppLanguage.entries.forEach { language -> assertSame(number.compactAmount, number.formattedAmount(format, language)) }
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
        // The anchor only moves with the world; the renderer eases the drift from the life.
        assertEquals(60f, advanced.x)
        assertEquals(150f, advanced.y, 0.001f)
        assertEquals(InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS - 0.1f, advanced.life, 0.001f)
        assertSame(original.compactAmount, advanced.compactAmount)
        assertSame(original.russianCompactAmount, advanced.russianCompactAmount)
        assertSame(original.fullAmount, advanced.fullAmount)
        assertSame(original.russianFullAmount, advanced.russianFullAmount)
        assertSame(advanced.russianCompactAmount, advanced.formattedAmount(DamageNumberFormat.COMPACT, AppLanguage.Russian))
        assertEquals("1,25K", advanced.russianCompactAmount)
        assertEquals("1\u202F250", advanced.russianFullAmount)
        assertSame(snapshot, reducer.snapshot())
    }
}
